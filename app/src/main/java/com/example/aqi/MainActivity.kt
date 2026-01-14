package com.example.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
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
import com.example.aqi.data.database.AqiDatabase
import com.example.aqi.data.database.AqiEntity
import com.example.aqi.ui.components.*
import com.example.aqi.ui.theme.AQITheme
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val MIN_REFRESH_INTERVAL = 5 * 60 * 1000 // 5 minutes

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {
            AQITheme {
                var isLoading by remember { mutableStateOf(true) }
                var aqiData by remember { mutableStateOf<AqiData?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var showSearchDialog by remember { mutableStateOf(false) }
                var customQuery by remember { mutableStateOf<String?>(null) }

                val apiService = remember { AqiApiService.create() }
                val gson = remember { Gson() }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val sharedPrefs = remember { context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE) }
                val db = remember { AqiDatabase.getDatabase(context) }

                val fetchData = { force: Boolean, query: String? ->
                    scope.launch {
                        isLoading = true
                        try {
                            val lastFetch = sharedPrefs.getLong("last_fetch_time", 0L)
                            val currentTime = System.currentTimeMillis()

                            if (!force && query == null && aqiData == null) {
                                val cachedJson = sharedPrefs.getString("cached_nearby_aqi", null)
                                if (cachedJson != null) {
                                    aqiData = gson.fromJson(cachedJson, AqiData::class.java)
                                    if (currentTime - lastFetch < MIN_REFRESH_INTERVAL) {
                                        isLoading = false
                                        return@launch
                                    }
                                }
                            }

                            val response = if (query != null) {
                                if (query.all { it.isDigit() }) {
                                    apiService.getAqiByStationId(query, BuildConfig.API_KEY)
                                } else {
                                    apiService.getAqiByCityName(query, BuildConfig.API_KEY)
                                }
                            } else {
                                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                
                                // Improved Location Fetch: Try lastLocation then request fresh update
                                var location = try {
                                    fusedLocationClient.lastLocation.await()
                                } catch (e: Exception) { null }

                                if (location == null) {
                                    // Force a single high-accuracy update
                                    val locationRequest = CurrentLocationRequest.Builder()
                                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                                        .build()
                                    location = try {
                                        fusedLocationClient.getCurrentLocation(locationRequest, null).await()
                                    } catch (e: Exception) { null }
                                }

                                if (location != null) {
                                    apiService.getHyperlocalAqi(location.latitude, location.longitude, BuildConfig.API_KEY)
                                } else {
                                    null
                                }
                            }

                            if (response != null) {
                                if (response.status == "ok" && response.data.isJsonObject) {
                                    val data = gson.fromJson(response.data, AqiData::class.java)
                                    aqiData = data
                                    errorMessage = null
                                    
                                    if (query == null) {
                                        sharedPrefs.edit()
                                            .putString("cached_nearby_aqi", gson.toJson(data))
                                            .putLong("last_fetch_time", System.currentTimeMillis())
                                            .apply()
                                            
                                        db.aqiDao().insertAqiRecord(
                                            AqiEntity(
                                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                                aqi = data.aqi,
                                                cityName = data.city.name
                                            )
                                        )
                                    }
                                } else {
                                    errorMessage = "Location not found: ${response.data.asString}"
                                }
                            } else {
                                errorMessage = "Could not determine location. Please ensure GPS is enabled and you have a signal."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Connection error: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                }

                val locationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
                        ) {
                            fetchData(false, null)
                        } else {
                            errorMessage = "Location permission is required."
                            isLoading = false
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                if (showSearchDialog) {
                    LocationSearchDialog(
                        onDismiss = { showSearchDialog = false },
                        onSearch = { query ->
                            customQuery = query
                            fetchData(true, query)
                            showSearchDialog = false
                        },
                        onReset = {
                            customQuery = null
                            fetchData(true, null)
                            showSearchDialog = false
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        aqiData = aqiData,
                        onRefresh = { fetchData(true, customQuery) },
                        onStationLongPress = { showSearchDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    isLoading: Boolean,
    errorMessage: String?,
    aqiData: AqiData?,
    onRefresh: () -> Unit,
    onStationLongPress: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && aqiData == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMessage != null && aqiData == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            }
            aqiData != null -> {
                val (topColor, bottomColor) = when {
                    aqiData.aqi <= 50 -> Color(0xFF1E3C72) to Color(0xFF2A5298)
                    aqiData.aqi <= 100 -> Color(0xFF3A3841) to Color(0xFF534741)
                    aqiData.aqi <= 150 -> Color(0xFF5D4037) to Color(0xFF4E342E)
                    else -> Color(0xFF4A2321) to Color(0xFF212121)
                }

                val animatedTopColor by animateColorAsState(targetValue = topColor, animationSpec = tween(2000), label = "")
                val animatedBottomColor by animateColorAsState(targetValue = bottomColor, animationSpec = tween(2000), label = "")

                val pagerState = rememberPagerState { 4 }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(animatedTopColor, animatedBottomColor)))
                ) {
                    AnimatedBackground()
                    
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> CurrentAqiScreen(aqiData, onRefresh, onStationLongPress)
                            1 -> ForecastScreen(aqiData = aqiData)
                            2 -> ExerciseScreen(aqi = aqiData.aqi)
                            3 -> CalendarScreen(forecasts = aqiData.forecast?.daily?.pm25 ?: emptyList())
                        }
                    }

                    Row(
                        Modifier
                            .height(50.dp)
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(4) { iteration ->
                            val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentAqiScreen(aqiData: AqiData, onRefresh: () -> Unit, onStationLongPress: () -> Unit) {
    val liveAqi = aqiData.aqi
    val stationName = aqiData.city.name

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onStationLongPress() })
                    }
            ) {
                Text(
                    text = "LOCATION (Long-press to change):",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = stationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Air Quality Index",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(32.dp))

        AqiGauge(aqi = liveAqi)

        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricBadge("PM2.5", aqiData.iaqi.pm25?.value?.toInt()?.toString() ?: "--")
            MetricBadge("PM10", aqiData.iaqi.pm10?.value?.toInt()?.toString() ?: "--")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HourlyPredictionCard(aqiData = aqiData)

        Spacer(modifier = Modifier.height(24.dp))
        aqiData.forecast?.daily?.let {
            PredictionCard(forecastDetails = it, liveAqi = liveAqi)
        }

        Spacer(modifier = Modifier.height(24.dp))
        PrecautionsCard(aqi = liveAqi)

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun MetricBadge(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(text = "$label: ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun LocationSearchDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit, onReset: () -> Unit) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Location") },
        text = {
            Column {
                Text("Enter a city name (e.g. \"London\") or a specific Station ID.")
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (query.isNotBlank()) onSearch(query) }) {
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Use My Location")
            }
        }
    )
}
