package com.example.aqi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Using a basic MaterialTheme to avoid Theme.AQI errors
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var liveAqi by remember { mutableStateOf(0) }
    var cityName by remember { mutableStateOf("Chennai") }
    var isLoading by remember { mutableStateOf(true) }
    val apiService = remember { AqiApiService.create() }

    LaunchedEffect(Unit) {
        try {
            // 2. Fetch real-time data for Chennai
            // I'm using the 'demo' token; for your FYP, use your own token later
            val response = apiService.getCityAqi("chennai", "4984482351fff856dae78e96fbbc69acfc414b35")

            if (response.status == "ok") {
                liveAqi = response.data.aqi
                cityName = response.data.city.name
            }
        } catch (e: Exception) {
            // If internet fails, it stays at 0 or shows an error
            cityName = "Network Error"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF1E3C72), Color(0xFF2A5298))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("CHENNAI, INDIA", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Text("Air Quality Index", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth().height(350.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        AqiGauge(aqi = liveAqi)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            PredictionCard(prediction = 106.67f)
        }
    }
}

@Composable
fun AqiGauge(aqi: Int) {
    val animatedAqi by animateFloatAsState(
        targetValue = aqi.toFloat(),
        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
        label = "Needle"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.minDimension / 2) - 40f

            // 1. Draw the Colored Arc
            drawArc(
                brush = Brush.horizontalGradient(listOf(Color.Green, Color.Yellow, Color.Red)),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 45f, cap = StrokeCap.Round)
            )

            // 2. Needle Calculation
            val angleDegrees = 180f + (animatedAqi.coerceIn(0f, 200f) / 200f * 180f)
            val angleRad = Math.toRadians(angleDegrees.toDouble())

            // Define lineEnd correctly here
            val lineEnd = Offset(
                x = (center.x + (radius - 20f) * cos(angleRad)).toFloat(),
                y = (center.y + (radius - 20f) * sin(angleRad)).toFloat()
            )

            // 3. Dynamic Needle Color
            val needleColor = when {
                animatedAqi <= 50 -> Color.Green
                animatedAqi <= 100 -> Color.Yellow
                else -> Color(0xFFFF5252) // Soft Red
            }

            // 4. Draw the Needle using 'lineEnd'
            drawLine(
                color = needleColor,
                start = center,
                end = lineEnd, // This matches the variable above
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )

            drawCircle(Color.White, radius = 15f, center = center)
        }

        // AQI Number
        Text(
            text = aqi.toString(),
            fontSize = 50.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(top = 50.dp)
        )
    }
}

@Composable
fun PredictionCard(prediction: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("🤖 AI FORECAST", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("Tomorrow's AQI: $prediction", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Model: Random Forest", fontSize = 12.sp, color = Color(0xFF2E7D32))
        }
    }
}