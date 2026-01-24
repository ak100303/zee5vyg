package com.example.aqi.ui.components

import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)) // Correctly clip the container
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.1f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                HorizontalPager(state = pagerState) { page ->
                    val forecast = dailyForecasts[page]
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.forecastDay.day)
                    val dayText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date!!)

                    Column {
                        Text(
                            text = if (page == 0) "✨ TODAY'S OVERVIEW" else "🗓️ FORECAST - $dayText",
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f)
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (page == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Live AQI", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                    Text(liveAqi.toString(), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Predicted Avg", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                    Text(forecast.forecastDay.avg.toString(), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
                                }
                            }
                        } else {
                            val aqi = forecast.forecastDay.avg
                            val aqiColor = when {
                                aqi <= 50 -> Color(0xFF00E676) // Emerald Green
                                aqi <= 100 -> Color(0xFFFFEA00) // Yellow
                                aqi <= 150 -> Color(0xFFFF9100) // Orange
                                else -> Color(0xFFFF5252) // Red
                            }

                            Text(
                                text = "${forecast.pollutantName} Avg: ${forecast.forecastDay.avg}",
                                style = MaterialTheme.typography.titleLarge,
                                color = aqiColor,
                                fontWeight = FontWeight.Black
                            )
                            if (forecast.forecastDay.min != forecast.forecastDay.avg || forecast.forecastDay.max != forecast.forecastDay.avg) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Min: ${forecast.forecastDay.min} - Max: ${forecast.forecastDay.max}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
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
}
