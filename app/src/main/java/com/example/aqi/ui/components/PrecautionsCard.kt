package com.example.aqi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
