package com.example.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
                            val lastFetch = sharedPrefs.getLong("last_fetch_time", 0L)
                            val currentTime = System.currentTimeMillis()

                            if (aqiData == null) {
                                sharedPrefs.getString("cached_nearby_aqi", null)?.let {
                                    aqiData = gson.fromJson(it, AqiData::class.java)
                                }
                            }

                            if (!hasInternet) {
                                isOffline = true
                                isLoading = false
                                return@launch
                            }

                            if (!force && query == null && currentTime - lastFetch < MIN_REFRESH_INTERVAL) {
                                isLoading = false
                                return@launch
                            }

                            val response = withContext(Dispatchers.IO) {
                                try {
                                    if (query != null) {
                                        apiService.getAqiByCityName(query, BuildConfig.API_KEY)
                                    } else {
                                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                        val location = fusedLocationClient.lastLocation.await() ?:
                                            fusedLocationClient.getCurrentLocation(CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).build(), null).await()

                                        if (location != null) {
                                            // Beacon Update
                                            val userId = auth.currentUser?.uid
                                            if (userId != null) {
                                                firestore.collection("users").document(userId).set(hashMapOf("lastLocation" to hashMapOf("lat" to location.latitude, "lon" to location.longitude)), SetOptions.merge())
                                            }

                                            // Multi-Tier Fetch
                                            var source = "waqi"
                                            var waqi = try { apiService.getHyperlocalAqi(location.latitude, location.longitude, BuildConfig.API_KEY) } catch (e: Exception) { null }
                                            
                                            if (waqi == null || waqi.status != "ok") {
                                                val owKey = BuildConfig.OPEN_WEATHER_KEY
                                                if (owKey.isNotEmpty()) {
                                                    val owData = try { apiService.getOpenWeatherAqi(location.latitude, location.longitude, owKey) } catch (e: Exception) { null }
                                                    if (owData != null && owData.list.isNotEmpty()) {
                                                        val pm25 = owData.list[0].components.pm2_5
                                                        val aqi = calculateUsAqi(pm25)
                                                        source = "openweather"
                                                        val json = """{"status":"ok","data":{"aqi":$aqi,"idx":9999,"city":{"name":"OpenWeather Backup","geo":[${location.latitude},${location.longitude}]},"iaqi":{"pm25":{"v":$pm25}}}}"""
                                                        waqi = gson.fromJson(json, AqiResponse::class.java)
                                                    }
                                                }
                                            }
                                            Pair(waqi, source)
                                        } else { null }
                                    }
                                } catch (e: Exception) { null }
                            }

                            if (response is Pair<*, *> && (response.first as? AqiResponse)?.status == "ok") {
                                val aqiRes = response.first as AqiResponse
                                val source = response.second as String
                                val data = gson.fromJson(aqiRes.data, AqiData::class.java)
                                aqiData = data
                                isOffline = false
                                sharedPrefs.edit().putString("cached_nearby_aqi", gson.toJson(data)).putLong("last_fetch_time", System.currentTimeMillis()).apply()
                                
                                val now = Calendar.getInstance()
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
                                val hour = now.get(Calendar.HOUR_OF_DAY)
                                withContext(Dispatchers.IO) {
                                    db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = data.city.name, aqi = data.aqi, dataSource = source))
                                    if (auth.currentUser?.uid != null) {
                                        val record = hashMapOf("aqi" to data.aqi, "cityName" to data.city.name, "dataSource" to source, "timestamp" to com.google.firebase.Timestamp.now())
                                        firestore.collection("users").document(auth.currentUser!!.uid).collection("history").document(dateStr).collection("hourly").document(hour.toString()).set(record)
                                    }
                                }
                            } else {
                                // TIER 3: Prediction
                                withContext(Dispatchers.IO) {
                                    val lastRecords = db.aqiDao().getLastTwoRecords()
                                    if (lastRecords.size >= 2) {
                                        val prediction = (lastRecords[0].aqi + (lastRecords[0].aqi - lastRecords[1].aqi).coerceIn(-25, 25)).coerceIn(0, 500)
                                        val now = Calendar.getInstance()
                                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
                                        val hour = now.get(Calendar.HOUR_OF_DAY)
                                        db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = lastRecords[0].cityName, aqi = prediction, dataSource = "trend_prediction"))
                                        if (aqiData != null) {
                                            aqiData = aqiData!!.copy(aqi = prediction)
                                        }
                                    }
                                }
                                isOffline = true
                            }
                        } catch (e: Exception) { isOffline = true } finally { isLoading = false }
                    }
                }

                val permissionsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = { fetchData(false, null); setupBackgroundRecording() })
                LaunchedEffect(Unit) { permissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

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
        pm25 <= 12.0 -> ((50 - 0) / (12.0 - 0) * (pm25 - 0) + 0).toInt()
        pm25 <= 35.4 -> ((100 - 51) / (35.4 - 12.1) * (pm25 - 12.1) + 51).toInt()
        pm25 <= 55.4 -> ((150 - 101) / (55.4 - 35.5) * (pm25 - 35.5) + 101).toInt()
        pm25 <= 150.4 -> ((200 - 151) / (150.4 - 55.5) * (pm25 - 55.5) + 151).toInt()
        pm25 <= 250.4 -> ((300 - 201) / (250.4 - 150.5) * (pm25 - 150.5) + 201).toInt()
        else -> ((500 - 301) / (500.4 - 250.5) * (pm25 - 250.5) + 301).toInt()
    }.coerceIn(0, 500)
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
                            Text("Outage Backup Active", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
