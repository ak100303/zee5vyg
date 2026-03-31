package com.example.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.app.NotificationCompat
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
    private val PERSONAL_ALERT_ID = 999

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        startPersonalAqiService()

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
                            if (!hasInternet) {
                                if (aqiData == null) {
                                    val cached = sharedPrefs.getString("cached_nearby_aqi", null)
                                    if (cached != null) aqiData = gson.fromJson(cached, AqiData::class.java)
                                }
                                if (aqiData == null) errorMessage = "No internet and no cached data."
                                isOffline = true; isLoading = false; return@launch
                            }

                            val resultPair = withContext(Dispatchers.IO) {
                                try {
                                    val govKey = BuildConfig.GOV_INDIA_KEY
                                    val waqiKey = BuildConfig.API_KEY
                                    val owKey = BuildConfig.OPEN_WEATHER_KEY
                                    var finalRes: AqiResponse? = null
                                    var finalSource = "waqi"

                                    val targetCoords = if (query != null) {
                                        val geocoder = Geocoder(context, Locale.getDefault())
                                        val addr = try { geocoder.getFromLocationName(query, 1) } catch (e: Exception) { null }
                                        if (!addr.isNullOrEmpty()) Pair(addr[0].latitude, addr[0].longitude) else null
                                    } else {
                                        val fused = LocationServices.getFusedLocationProviderClient(context)
                                        val loc = try { fused.lastLocation.await() ?: fused.getCurrentLocation(CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).build(), null).await() } catch(e:Exception) { null }
                                        if (loc != null) Pair(loc.latitude, loc.longitude) else Pair(28.6139, 77.2090) // Default to New Delhi if location fails
                                    }

                                    var localLat = 0.0
                                    var localLon = 0.0
                                    if (targetCoords != null) {
                                        localLat = targetCoords.first
                                        localLon = targetCoords.second
                                        if (query == null && auth.currentUser?.uid != null) {
                                            firestore.collection("users").document(auth.currentUser!!.uid).set(hashMapOf("lastLocation" to hashMapOf("lat" to localLat, "lon" to localLon)), SetOptions.merge())
                                        }

                                        // 1. WAQI
                                        var res = try { apiService.getHyperlocalAqi(localLat, localLon, waqiKey) } catch (e: Exception) { null }
                                        
                                        // Stale Detection Logic (Offline Sensors)
                                        if (res != null && res.status == "ok") {
                                            val parsedData = try { gson.fromJson(res.data, AqiData::class.java) } catch(e:Exception){null}
                                            if (parsedData?.time != null) {
                                                // WAQI 'time.v' is commonly a Unix Epoch Timestamp in seconds
                                                val updateTimeMillis = parsedData.time.v * 1000L
                                                val now = System.currentTimeMillis()
                                                val differenceHours = (now - updateTimeMillis) / (1000 * 60 * 60)
                                                if (differenceHours > 24) {
                                                    Log.e("AQI_DEBUG", "WAQI sensor is heavily stale (${differenceHours}hrs old). Forcing failover.")
                                                    res = null // Veto this response and trigger downstream fallbacks
                                                }
                                            }
                                        }

                                        val lastRecords = db.aqiDao().getLastTwoRecords()
                                        val isStagnant = query == null && lastRecords.size >= 2 && res != null && res.status == "ok" && 
                                            (try { gson.fromJson(res.data, AqiData::class.java).aqi == lastRecords[0].aqi } catch(e:Exception){false})

                                        if (res != null && res.status == "ok" && !isStagnant) {
                                            finalRes = res; finalSource = "waqi"
                                        } else {
                                            // 2. GOVT
                                            if (govKey.isNotEmpty()) {
                                                val cityName = query ?: try { Geocoder(context).getFromLocation(localLat, localLon, 1)?.get(0)?.locality } catch(e:Exception) { null }
                                                if (cityName != null) {
                                                    val govRes = try { apiService.getGovIndiaAqi(govKey, city = cityName) } catch (e: Exception) { null }
                                                    if (govRes != null && govRes.records.isNotEmpty()) {
                                                        val pmRecord = govRes.records.find { it.pollutantId == "PM2.5" } ?: govRes.records[0]
                                                        val aqiVal = pmRecord.pollutantValue.toIntOrNull() ?: 0
                                                        val forecastJson = buildOwmForecastJson(localLat, localLon, owKey, apiService)
                                                        val json = """{"status":"ok","data":{"aqi":$aqiVal,"idx":7777,"city":{"name":"${pmRecord.station} (Govt)"},"iaqi":{"pm25":{"v":$aqiVal}},"forecast":$forecastJson}}"""
                                                        finalRes = gson.fromJson(json, AqiResponse::class.java); finalSource = "gov_india"
                                                    }
                                                }
                                            }
                                            // 3. OWM
                                            if (finalRes == null && owKey.isNotEmpty()) {
                                                val owPollution = try { apiService.getOpenWeatherAqi(localLat, localLon, owKey) } catch (e: Exception) { null }
                                                if (owPollution != null && owPollution.list.isNotEmpty()) {
                                                    val aqiVal = calculateUsAqi(owPollution.list[0].components.pm2_5)
                                                    val forecastJson = buildOwmForecastJson(localLat, localLon, owKey, apiService)
                                                    val json = """{"status":"ok","data":{"aqi":$aqiVal,"idx":9999,"city":{"name":"${query ?: "Local Area"} (Backup)"},"iaqi":{"pm25":{"v":${owPollution.list[0].components.pm2_5}}},"forecast":$forecastJson}}"""
                                                    finalRes = gson.fromJson(json, AqiResponse::class.java); finalSource = "openweather"
                                                }
                                            }
                                        }
                                    }
                                    if (finalRes != null) {
                                        // REAL-TIME WEATHER INJECTION OVERRIDE
                                        if (owKey.isNotEmpty()) {
                                            try {
                                                val cw = apiService.getCurrentWeather(localLat, localLon, "metric", owKey)
                                                // Deconstruct the JSON, inject exactly the new Temperature, Humidity, Wind, and reconstruct it.
                                                val dataObj = gson.fromJson(finalRes!!.data, AqiData::class.java)
                                                val newIaqi = IaqiMetrics(
                                                    pm25 = dataObj.iaqi.pm25, pm10 = dataObj.iaqi.pm10, o3 = dataObj.iaqi.o3,
                                                    temperature = Value(cw.main.temp), humidity = Value(cw.main.humidity), 
                                                    wind = Value(cw.wind.speed), pressure = Value(cw.main.pressure)
                                                )
                                                val newDataObj = AqiData(dataObj.aqi, dataObj.idx, dataObj.city, newIaqi, dataObj.forecast, dataObj.time)
                                                finalRes = AqiResponse("ok", gson.toJsonTree(newDataObj))
                                            } catch (e: Exception) { Log.e("AQI_DEBUG", "Failed to inject real-time weather: ${e.message}") }
                                        }
                                        Pair(finalRes!!, finalSource) 
                                    } else null
                                } catch (e: Exception) { null }
                            }

                            if (resultPair != null) {
                                val data = gson.fromJson(resultPair.first.data, AqiData::class.java)
                                aqiData = data; isOffline = false; errorMessage = null
                                sharedPrefs.edit().putString("cached_nearby_aqi", gson.toJson(data)).putLong("last_fetch_time", System.currentTimeMillis()).apply()
                                withContext(Dispatchers.IO) {
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                    db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = data.city?.name ?: "Unknown", aqi = data.aqi, dataSource = resultPair.second))
                                }
                            } else {
                                if (query != null) errorMessage = "Location not found."
                                else isOffline = true
                            }
                        } catch (e: Exception) { isOffline = true } finally { isLoading = false }
                    }
                }

                val permsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), onResult = { perms -> if (perms.values.any { it }) { fetchData(false, null) } })
                LaunchedEffect(Unit) { permsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

                if (showSearchDialog) {
                    LocationSearchDialog(onDismiss = { showSearchDialog = false }, onSearch = { q -> customQuery = q; fetchData(true, q); showSearchDialog = false }, onReset = { customQuery = null; fetchData(true, null); showSearchDialog = false })
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    MainScreen(isLoading = isLoading, isOffline = isOffline, errorMessage = errorMessage, aqiData = aqiData, onRefresh = { fetchData(true, customQuery) }, onStationLongPress = { showSearchDialog = true })
                }
            }
        }
    }

    private fun setupBackgroundRecording() {
        val workRequest = PeriodicWorkRequestBuilder<AqiBackgroundWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("AqiBackgroundRecorder", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun startPersonalAqiService() {
        val intent = Intent(this, PersonalAqiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { startForegroundService(intent) } else { startService(intent) }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(PERSONAL_ALERT_ID)
    }
}

fun calculateUsAqi(pm25: Float): Int {
    return when {
        pm25 <= 12.0f -> linearInterpolate(pm25, 0f, 12.0f, 0, 50)
        pm25 <= 35.4f -> linearInterpolate(pm25, 12.1f, 35.4f, 51, 100)
        pm25 <= 55.4f -> linearInterpolate(pm25, 35.5f, 55.4f, 101, 150)
        pm25 <= 150.4f -> linearInterpolate(pm25, 55.5f, 150.4f, 151, 200)
        pm25 <= 250.4f -> linearInterpolate(pm25, 150.5f, 250.4f, 201, 300)
        else -> linearInterpolate(pm25, 301f, 500f, 301, 500)
    }.coerceIn(0, 500)
}

private fun linearInterpolate(aqi: Float, concLo: Float, concHi: Float, aqiLo: Int, aqiHi: Int): Int {
    return (((aqiHi - aqiLo) / (concHi - concLo)) * (aqi - concLo) + aqiLo).toInt()
}

suspend fun buildOwmForecastJson(lat: Double, lon: Double, owKey: String, apiService: AqiApiService): String {
    if (owKey.isEmpty()) return "null"
    val owForecast = try { apiService.getOpenWeatherAqiForecast(lat, lon, owKey) } catch(e:Exception){null}
    if (owForecast != null && owForecast.list.isNotEmpty()) {
        val dailyMap = mutableMapOf<String, Pair<Int, Int>>()
        for (item in owForecast.list) {
            val date = Date(item.dt * 1000L)
            val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            val fAqi = calculateUsAqi(item.components.pm2_5)
            val currentMax = dailyMap[dayStr]?.first ?: 0
            val currentMin = dailyMap[dayStr]?.second ?: 500
            dailyMap[dayStr] = Pair(maxOf(currentMax, fAqi), minOf(currentMin, fAqi))
        }
        val dailyStr = dailyMap.map { (day, bounds) ->
            """{"avg":${(bounds.first + bounds.second)/2}, "day":"$day", "max":${bounds.first}, "min":${bounds.second}}"""
        }.joinToString(",")
        return """{"daily":{"pm25":[$dailyStr]}}"""
    }
    return "null"
}

@Composable
fun MainScreen(isLoading: Boolean, isOffline: Boolean, errorMessage: String?, aqiData: AqiData?, onRefresh: () -> Unit, onStationLongPress: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && aqiData == null) { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
        else if (aqiData != null) {
            val pagerState = rememberPagerState { 5 }
            var personalAqi by remember { mutableIntStateOf(0) }
            val currentAqi = if (pagerState.currentPage == 4) personalAqi else aqiData.aqi

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedBackground(aqi = currentAqi)
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> CurrentAqiScreen(aqiData, onRefresh, onStationLongPress, isOffline)
                        1 -> ForecastScreen(aqiData = aqiData)
                        2 -> ExerciseScreen(aqi = aqiData.aqi)
                        3 -> CalendarScreen(cityName = aqiData.city?.name ?: "Local Area", forecasts = aqiData.forecast?.daily)
                        4 -> PersonalSensorScreen(onAqiChanged = { personalAqi = it })
                    }
                }
                if (isOffline) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).clip(RoundedCornerShape(12.dp))) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Service Outage", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.height(50.dp).fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
                    repeat(5) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                        Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(10.dp))
                    }
                }
            }
        }
        else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMessage ?: "No data available. Please check your connection or try another location.", color = Color.White, modifier = Modifier.padding(16.dp))
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
                Text(aqiData.city?.name ?: "Unknown", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
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
