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
