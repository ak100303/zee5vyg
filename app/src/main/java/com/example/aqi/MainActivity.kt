package com.example.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.aqi.data.database.AqiDatabase
import com.example.aqi.data.database.AqiEntity
import com.example.aqi.data.database.HourlyAqiEntity
import com.example.aqi.ui.components.*
import com.example.aqi.ui.theme.AQITheme
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val MIN_REFRESH_INTERVAL = 5 * 60 * 1000 

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    FirebaseAuth.getInstance().signInAnonymously().await()
                }
                setupBackgroundRecording()
            } catch (e: Exception) {}
        }

        setContent {
            AQITheme {
                var isLoading by remember { mutableStateOf(true) }
                var aqiData by remember { mutableStateOf<AqiData?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var isOffline by remember { mutableStateOf(false) }
                var showSearchDialog by remember { mutableStateOf(false) }
                var customQuery by remember { mutableStateOf<String?>(null) }
                var showBackupDisclaimer by remember { mutableStateOf(false) }
                var hasShownDisclaimerThisSession by remember { mutableStateOf(false) }

                val context = LocalContext.current
                val apiService = remember { AqiApiService.create() }
                val gson = remember { Gson() }
                val scope = rememberCoroutineScope()
                val sharedPrefs = remember { context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE) }
                val db = remember { AqiDatabase.getDatabase(context) }
                val firestore = remember { FirebaseFirestore.getInstance() }
                val auth = remember { FirebaseAuth.getInstance() }

                val checkInternet = {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                val fetchData = { force: Boolean, query: String? ->
                    scope.launch {
                        isLoading = true
                        val hasInternet = checkInternet()
                        
                        try {
                            if (!hasInternet) {
                                if (aqiData == null) {
                                    sharedPrefs.getString("cached_nearby_aqi", null)?.let { aqiData = gson.fromJson(it, AqiData::class.java) }
                                }
                                isOffline = true; isLoading = false; return@launch
                            }

                            val resultPair = withContext(Dispatchers.IO) {
                                try {
                                    val owKey = BuildConfig.OPEN_WEATHER_KEY
                                    var finalRes: AqiResponse? = null
                                    var finalSource = "waqi"

                                    val fused = LocationServices.getFusedLocationProviderClient(context)
                                    val loc = fused.lastLocation.await() ?: fused.getCurrentLocation(CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).build(), null).await()

                                    if (loc != null || query != null) {
                                        // 1. TIER 1: ALWAYS TRY WAQI FIRST ON REFRESH
                                        val waqiRes = if (query != null) {
                                            try { apiService.getAqiByCityName(query, BuildConfig.API_KEY) } catch (e: Exception) { null }
                                        } else {
                                            try { apiService.getHyperlocalAqi(loc!!.latitude, loc.longitude, BuildConfig.API_KEY) } catch (e: Exception) { null }
                                        }

                                        // Stagnancy check against local cache
                                        val lastRecords = db.aqiDao().getLastTwoRecords()
                                        val isStagnant = waqiRes != null && waqiRes.status == "ok" && 
                                            lastRecords.isNotEmpty() && gson.fromJson(waqiRes.data, AqiData::class.java).aqi == lastRecords[0].aqi

                                        if (waqiRes != null && waqiRes.status == "ok" && !isStagnant) {
                                            finalRes = waqiRes
                                            finalSource = "waqi"
                                        } else if (owKey.isNotEmpty()) {
                                            // 2. TIER 2: FAILOVER TO OPENWEATHER
                                            val targetLat = loc?.latitude ?: 0.0
                                            val targetLon = loc?.longitude ?: 0.0
                                            
                                            val owPollution = try { apiService.getOpenWeatherAqi(targetLat, targetLon, owKey) } catch (e: Exception) { null }
                                            val owWeather = try { apiService.getOpenWeatherForecast(targetLat, targetLon, "metric", owKey) } catch (e: Exception) { null }
                                            
                                            if (owPollution != null && owPollution.list.isNotEmpty() && owWeather != null) {
                                                val pm25 = owPollution.list[0].components.pm2_5
                                                val aqi = calculateUsAqi(pm25)
                                                val w = owWeather.list[0]
                                                val name = if (query != null) query else (lastRecords.firstOrNull()?.cityName ?: "Local Area")
                                                
                                                val json = """{"status":"ok","data":{"aqi":$aqi,"idx":9999,"city":{"name":"$name (Backup: OWM)"},"iaqi":{"pm25":{"v":$pm25},"t":{"v":${w.main.temp}},"h":{"v":${w.main.humidity}},"w":{"v":${w.wind.speed}}},"forecast":{"daily":{"pm25":[{"avg":$aqi,"day":"${SimpleDateFormat("yyyy-MM-dd").format(Date())}","max":$aqi,"min":$aqi}]}}}}"""
                                                finalRes = gson.fromJson(json, AqiResponse::class.java)
                                                finalSource = "openweather"
                                            }
                                        }
                                    }
                                    if (finalRes != null) Pair(finalRes, finalSource) else null
                                } catch (e: Exception) { null }
                            }

                            if (resultPair != null) {
                                val data = gson.fromJson(resultPair.first.data, AqiData::class.java)
                                aqiData = data; isOffline = false; errorMessage = null
                                sharedPrefs.edit().putString("cached_nearby_aqi", gson.toJson(data)).putLong("last_fetch_time", System.currentTimeMillis()).apply()
                                
                                if (resultPair.second == "openweather" && !hasShownDisclaimerThisSession) {
                                    showBackupDisclaimer = true; hasShownDisclaimerThisSession = true
                                }

                                withContext(Dispatchers.IO) {
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                    db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = data.city.name, aqi = data.aqi, dataSource = resultPair.second))
                                }
                            } else {
                                isOffline = true
                            }
                        } catch (e: Exception) { isOffline = true } finally { isLoading = false }
                    }
                }

                val permsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { fetchData(false, null) }
                LaunchedEffect(Unit) { permsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

                if (showSearchDialog) {
                    LocationSearchDialog(onDismiss = { showSearchDialog = false }, onSearch = { q -> customQuery = q; fetchData(true, q); showSearchDialog = false }, onReset = { customQuery = null; fetchData(true, null); showSearchDialog = false })
                }

                if (showBackupDisclaimer) {
                    AlertDialog(onDismissRequest = { showBackupDisclaimer = false }, title = { Text("Using Regional Backup") }, text = { Text("WAQI sensor is stagnant or offline. Showing grid-based regional average (OWM).") }, confirmButton = { TextButton(onClick = { showBackupDisclaimer = false }) { Text("OK") } })
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(isLoading = isLoading, isOffline = isOffline, errorMessage = errorMessage, aqiData = aqiData, onRefresh = { fetchData(true, customQuery) }, onStationLongPress = { showSearchDialog = true })
                }
            }
        }
    }

    private fun setupBackgroundRecording() {
        val workRequest = PeriodicWorkRequestBuilder<AqiBackgroundWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("AqiBackgroundRecorder", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}

fun calculateUsAqi(pm25: Float): Int {
    return when {
        pm25 <= 12.0f -> linearInterpolate(pm25, 0f, 12.0f, 0, 50)
        pm25 <= 35.4f -> linearInterpolate(pm25, 12.1f, 35.4f, 51, 100)
        pm25 <= 55.4f -> linearInterpolate(pm25, 35.5f, 55.4f, 101, 150)
        pm25 <= 150.4f -> linearInterpolate(pm25, 55.5f, 150.4f, 151, 200)
        pm25 <= 250.4f -> linearInterpolate(pm25, 150.5f, 250.4f, 201, 300)
        else -> linearInterpolate(pm25, 250.5f, 500.4f, 301, 500)
    }.coerceIn(0, 500)
}

private fun linearInterpolate(aqi: Float, concLo: Float, concHi: Float, aqiLo: Int, aqiHi: Int): Int {
    return (((aqiHi - aqiLo) / (concHi - concLo)) * (aqi - concLo) + aqiLo).toInt()
}

@Composable
fun MainScreen(isLoading: Boolean, isOffline: Boolean, errorMessage: String?, aqiData: AqiData?, onRefresh: () -> Unit, onStationLongPress: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && aqiData == null) { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
        else if (aqiData != null) {
            val pagerState = rememberPagerState { 4 }
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedBackground(aqi = aqiData.aqi)
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> CurrentAqiScreen(aqiData, onRefresh, onStationLongPress, isOffline)
                        1 -> ForecastScreen(aqiData = aqiData)
                        2 -> ExerciseScreen(aqi = aqiData.aqi)
                        3 -> CalendarScreen(cityName = aqiData.city.name, forecasts = aqiData.forecast?.daily)
                    }
                }
                if (isOffline) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).clip(RoundedCornerShape(12.dp))) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Using Cached Data", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.height(50.dp).fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
                    repeat(4) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                        Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentAqiScreen(aqiData: AqiData, onRefresh: () -> Unit, onStationLongPress: () -> Unit, isOffline: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { onStationLongPress() }) }) {
                Text("LOCATION:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                Text(aqiData.city.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Air Quality Index", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        AqiGauge(aqi = aqiData.aqi) 
        Row(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricBadge("PM2.5", aqiData.iaqi.pm25?.value?.toInt()?.toString() ?: "--")
            MetricBadge("PM10", aqiData.iaqi.pm10?.value?.toInt()?.toString() ?: "--")
        }
        Spacer(modifier = Modifier.height(8.dp))
        HourlyPredictionCard(aqiData = aqiData)
        Spacer(modifier = Modifier.height(8.dp))
        aqiData.forecast?.daily?.let { PredictionCard(forecastDetails = it, liveAqi = aqiData.aqi) }
        Spacer(modifier = Modifier.height(8.dp))
        PrecautionsCard(aqi = aqiData.aqi)
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun MetricBadge(label: String, value: String) {
    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("$label: ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
fun LocationSearchDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit, onReset: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Location") },
        text = { Column { Text("Enter a city name or Station ID."); TextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { if (query.isNotBlank()) onSearch(query) }) { Text("Search") } },
        dismissButton = { TextButton(onClick = onReset) { Text("Use My Location") } }
    )
}
