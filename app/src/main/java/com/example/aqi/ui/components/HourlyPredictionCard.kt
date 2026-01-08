package com.example.aqi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aqi.AqiData
import com.example.aqi.ai.AqiPredictor
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HourlyPredictionCard(aqiData: AqiData) {
    val predictor = remember { AqiPredictor() }
    val hourlyPredictions = predictor.predictNext3Hours(aqiData)

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
                "🧠 3-HOUR AI FORECAST",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                hourlyPredictions.forEachIndexed { index, aqi ->
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.HOUR, index + 1)
                    val hour = SimpleDateFormat("h a", Locale.getDefault()).format(calendar.time)
                    PredictionItem(hour = hour, aqi = aqi)
                }
            }
        }
    }
}

@Composable
private fun PredictionItem(hour: String, aqi: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = hour, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = aqi.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                aqi <= 50 -> Color(0xFF4CAF50)
                aqi <= 100 -> Color(0xFFFFEB3B)
                aqi <= 150 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        )
    }
}
