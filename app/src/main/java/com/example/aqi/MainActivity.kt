package com.example.aqi

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aqi.di.ViewModelFactory
import com.example.aqi.ui.theme.AQITheme
import com.example.aqi.viewmodel.AqiUiState
import com.example.aqi.viewmodel.HyperlocalAqiViewModel
import com.google.android.gms.location.LocationServices
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: HyperlocalAqiViewModel by viewModels { ViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AQITheme {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                        fetchLocationAndData()
                    }
                }

                LaunchedEffect(Unit) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsState()
                    MainScreen(uiState)
                }
            }
        }
    }

    private fun fetchLocationAndData() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.fetchAqiData(location.latitude, location.longitude, "4984482351fff856dae78e96fbbc69acfc414b35")
                }
            }
        } catch (e: SecurityException) {
            // Handle security exception if permissions are revoked.
        }
    }
}

@Composable
fun MainScreen(uiState: AqiUiState) {
    when (uiState) {
        is AqiUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AqiUiState.Success -> {
            val aqiData = uiState.data.data
            val liveAqi = aqiData.aqi
            val cityName = aqiData.city.name.split(",").first().trim()

            val (topColor, bottomColor) = when {
                liveAqi <= 50 -> Color(0xFF1E3C72) to Color(0xFF2A5298) // Calm Blue
                liveAqi <= 100 -> Color(0xFF3A3841) to Color(0xFF534741) // Muted Orange/Grey
                liveAqi <= 150 -> Color(0xFF5D4037) to Color(0xFF4E342E) // Dark Orange
                else -> Color(0xFF4A2321) to Color(0xFF212121) // Deep Red/Black
            }

            val animatedTopColor by animateColorAsState(targetValue = topColor, animationSpec = tween(2000, easing = FastOutSlowInEasing), label = "")
            val animatedBottomColor by animateColorAsState(targetValue = bottomColor, animationSpec = tween(2000, easing = FastOutSlowInEasing), label = "")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(animatedTopColor, animatedBottomColor)))
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 48.dp)) {
                        AqiGauge(aqi = liveAqi)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                aqiData.forecast?.daily?.pm25?.firstOrNull()?.let {
                    PredictionCard(forecast = it)
                }

                Spacer(modifier = Modifier.height(24.dp))
                PrecautionsCard(aqi = liveAqi)

                Spacer(modifier = Modifier.height(40.dp))

            }
        }
        is AqiUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.message, color = Color.Red)
            }
        }
    }
}

@Composable
fun AqiGauge(aqi: Int) {
    var animationTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(aqi) {
        animationTarget = aqi.toFloat()
    }

    val animatedAqi by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
        label = "AQIValue"
    )

    val aqiColor = when {
        animatedAqi <= 50 -> Color(0xFF4CAF50)
        animatedAqi <= 100 -> Color(0xFFFFEB3B)
        animatedAqi <= 150 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    val gaugeBackgroundColor = Color.White.copy(alpha = 0.3f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.minDimension / 2) - 20f
            val strokeWidth = 35f

            drawArc(
                color = gaugeBackgroundColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = aqiColor,
                startAngle = 135f,
                sweepAngle = (animatedAqi / 200f * 270f).coerceIn(0f, 270f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedAqi.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = aqiColor
            )
            Text(
                text = "AQI",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PredictionCard(forecast: ForecastDay?) {
    if (forecast == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "🗓️ FORECAST - ${forecast.day.substringAfter("-")}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Avg AQI: ${forecast.avg}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Min: ${forecast.min} - Max: ${forecast.max}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun PrecautionsCard(aqi: Int) {
    val (generalAdvice, sensitiveAdvice) = when {
        aqi <= 50 -> "It's a great day to be active outside." to "Enjoy the fresh air!"
        aqi <= 100 -> "Air quality is acceptable." to "Sensitive groups should consider reducing prolonged or heavy exertion outdoors."
        aqi <= 150 -> "Everyone may begin to experience some adverse health effects." to "Sensitive groups may experience more serious health effects. Reduce outdoor activity."
        aqi <= 200 -> "Health alert: the risk of health effects is increased for everyone." to "Avoid prolonged outdoor exertion; sensitive groups should remain indoors."
        else -> "Hazardous: This is an emergency condition." to "Everyone should avoid all outdoor exertion."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "⚕️ HEALTH RECOMMENDATIONS",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("General Public:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(generalAdvice, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))

            Spacer(modifier = Modifier.height(12.dp))

            Text("Sensitive Groups:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sensitiveAdvice, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
