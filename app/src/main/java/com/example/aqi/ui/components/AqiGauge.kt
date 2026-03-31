package com.example.aqi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AqiGauge(aqi: Int) {
    // Ensure we don't pass negative or zero values during init
    val safeAqi = aqi.coerceAtLeast(0).toFloat()
    
    var animationTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(safeAqi) {
        animationTarget = safeAqi
    }

    val animatedAqi by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
        label = "AQIValue"
    )

    // Scientific AQI Color Scale
    val aqiColor = when {
        animatedAqi <= 50 -> Color(0xFF00E676)  // Good
        animatedAqi <= 100 -> Color(0xFFFFEA00) // Moderate
        animatedAqi <= 150 -> Color(0xFFFF9100) // Sensitive
        animatedAqi <= 200 -> Color(0xFFFF5252) // Unhealthy
        animatedAqi <= 300 -> Color(0xFFD500F9) // Very Unhealthy
        else -> Color(0xFFB71C1C)               // Hazardous
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 35f

            // Background Track
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress Arc - With NaN Safety
            val sweepAngle = (animatedAqi / 500f * 270f)
            val finalSweep = if (sweepAngle.isNaN()) 0f else sweepAngle.coerceIn(0f, 270f)

            drawArc(
                color = aqiColor,
                startAngle = 135f,
                sweepAngle = finalSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedAqi.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = aqiColor
            )
            Text(
                text = "AQI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
