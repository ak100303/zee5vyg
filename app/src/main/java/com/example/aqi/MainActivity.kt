package com.example.aqi

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.aqi.ui.components.AnimatedBackground
import com.example.aqi.ui.components.AqiGauge
import com.example.aqi.ui.components.HourlyPredictionCard
import com.example.aqi.ui.components.PrecautionsCard
import com.example.aqi.ui.components.PredictionCard
import com.example.aqi.ui.theme.AQITheme
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {
            AQITheme {
                var isLoading by remember { mutableStateOf(true) }
                var aqiData by remember { mutableStateOf<AqiData?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                val apiService = remember { AqiApiService.create() }
                val gson = remember { Gson() }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val locationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
                        ) {
                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val response = apiService.getHyperlocalAqi(location.latitude, location.longitude, BuildConfig.API_KEY)
                                            if (response.status == "ok" && response.data.isJsonObject) {
                                                val data = gson.fromJson(response.data, AqiData::class.java)
                                                aqiData = data
                                                errorMessage = null
                                            } else {
                                                errorMessage = response.data.toString()
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Network error: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        } else {
                            errorMessage = "Location permission is required to fetch AQI data."
                            isLoading = false
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(isLoading, errorMessage, aqiData)
                }
            }
        }
    }
}

@Composable
fun MainScreen(isLoading: Boolean, errorMessage: String?, aqiData: AqiData?) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            }
            aqiData != null -> {
                val (topColor, bottomColor) = when {
                    aqiData.aqi <= 50 -> Color(0xFF1E3C72) to Color(0xFF2A5298) // Calm Blue
                    aqiData.aqi <= 100 -> Color(0xFF3A3841) to Color(0xFF534741) // Muted Orange/Grey
                    aqiData.aqi <= 150 -> Color(0xFF5D4037) to Color(0xFF4E342E) // Dark Orange
                    else -> Color(0xFF4A2321) to Color(0xFF212121) // Deep Red/Black
                }

                val animatedTopColor by animateColorAsState(targetValue = topColor, animationSpec = tween(2000), label = "")
                val animatedBottomColor by animateColorAsState(targetValue = bottomColor, animationSpec = tween(2000), label = "")

                val pagerState = rememberPagerState { 3 }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(animatedTopColor, animatedBottomColor)))
                ) {
                    AnimatedBackground()
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> CurrentAqiScreen(aqiData)
                            1 -> ForecastScreen(aqiData = aqiData)
                            2 -> ExerciseScreen(aqi = aqiData.aqi)
                        }
                    }

                    Row(
                        Modifier
                            .height(50.dp)
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(3) { iteration ->
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
fun CurrentAqiScreen(aqiData: AqiData) {
    val liveAqi = aqiData.aqi
    val cityName = aqiData.city.name.split(",").first().trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = cityName.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            "Air Quality Index",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(32.dp))

        AqiGauge(aqi = liveAqi)

        Spacer(modifier = Modifier.height(24.dp))
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
