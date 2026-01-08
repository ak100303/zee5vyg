package com.example.aqi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aqi.ForecastDetails
import java.text.SimpleDateFormat
import java.util.*

data class DominantPollutantForecast(
    val pollutantName: String,
    val forecastDay: com.example.aqi.ForecastDay
)

@Composable
fun PredictionCard(forecastDetails: ForecastDetails, liveAqi: Int) {
    val dailyForecasts = remember(forecastDetails) {
        val allForecasts = mutableListOf<DominantPollutantForecast>()
        val days = forecastDetails.pm25?.map { it.day } ?: emptyList()

        for (day in days) {
            val pm25 = forecastDetails.pm25?.find { it.day == day }
            val pm10 = forecastDetails.pm10?.find { it.day == day }
            val o3 = forecastDetails.o3?.find { it.day == day }

            val dominant = listOfNotNull(pm25, pm10, o3).maxByOrNull { it.avg }

            if (dominant != null) {
                val pollutantName = when (dominant) {
                    pm25 -> "PM2.5"
                    pm10 -> "PM10"
                    o3 -> "Ozone (O₃)"
                    else -> "AQI"
                }
                allForecasts.add(DominantPollutantForecast(pollutantName, dominant))
            }
        }
        allForecasts
    }

    if (dailyForecasts.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { dailyForecasts.size })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.2f)))
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            HorizontalPager(state = pagerState) { page ->
                val forecast = dailyForecasts[page]
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.forecastDay.day)
                val dayText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date!!)

                Column {
                    Text(
                        if (page == 0) "✨ TODAY'S OVERVIEW" else "🗓️ FORECAST - $dayText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (page == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Live AQI", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                                Text(liveAqi.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Predicted Avg", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                                Text(forecast.forecastDay.avg.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            "${forecast.pollutantName} Avg: ${forecast.forecastDay.avg}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (forecast.forecastDay.min != forecast.forecastDay.avg || forecast.forecastDay.max != forecast.forecastDay.avg) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Min: ${forecast.forecastDay.min} - Max: ${forecast.forecastDay.max}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(8.dp)
                    )
                }
            }
        }
    }
}
