package com.example.aqi

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aqi.ui.components.WeatherDetailsCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ForecastScreen(aqiData: AqiData) {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val forecasts = aqiData.forecast?.daily?.pm25?.filter {
        val forecastDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.day)
        forecastDate != null && !forecastDate.before(today)
    } ?: emptyList()

    val startDate = forecasts.firstOrNull()?.day?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) }
    val endDate = forecasts.lastOrNull()?.day?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) }
    val dateRange = if (startDate != null && endDate != null) {
        "${SimpleDateFormat("MMM d", Locale.getDefault()).format(startDate)} - ${SimpleDateFormat("MMM d", Locale.getDefault()).format(endDate)}"
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "PM2.5 Forecast",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black, // Bold
            color = Color.White
        )
        
        Text(
            text = "Source: ${aqiData.city.name}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, // Bold
            color = Color.White.copy(alpha = 0.6f)
        )
        
        Text(
            text = dateRange,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, // Bold
            color = Color.White.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // --- UPGRADED FROSTED GLASS CONTAINER ---
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.1f))))
            ) {
                if (forecasts.isNotEmpty()) {
                    AqiForecastGraph(
                        forecasts = forecasts,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        WeatherDetailsCard(metrics = aqiData.iaqi)
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun AqiForecastGraph(forecasts: List<ForecastDay>, modifier: Modifier = Modifier) {
    if (forecasts.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val xStep = size.width / (forecasts.size - 1).coerceAtLeast(1)
                val index = (it.x / xStep).toInt().coerceIn(0, forecasts.size - 1)
                selectedIndex = if (selectedIndex == index) null else index
            }
        )
    }) {
        val bottomPadding = 40.dp.toPx()
        val graphHeight = size.height - bottomPadding

        val minAqiValue = forecasts.minOfOrNull { it.min } ?: 0
        val maxAqiValue = forecasts.maxOfOrNull { it.max } ?: 200
        val range = (maxAqiValue - minAqiValue).toFloat().coerceAtLeast(1f)
        val paddedMin = (minAqiValue - range * 0.2f).coerceAtLeast(0f)
        val paddedMax = (maxAqiValue + range * 0.2f)
        val dynamicRange = (paddedMax - paddedMin).coerceAtLeast(1f)

        val xStep = size.width / (forecasts.size - 1).coerceAtLeast(1)

        forecasts.forEachIndexed { index, forecast ->
            val x = index * xStep
            val dayText = SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.day)!!)

            drawText(
                textMeasurer = textMeasurer,
                text = dayText,
                topLeft = Offset(x - (textMeasurer.measure(AnnotatedString(dayText)).size.width / 2), graphHeight + 10.dp.toPx()),
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }

        val scaleY = { value: Int -> graphHeight - ((value - paddedMin) / dynamicRange * graphHeight) }
        val avgPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.avg)) }
        val minPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.min)) }
        val maxPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.max)) }

        val rangePath = Path().apply {
            moveTo(minPoints.first().x, minPoints.first().y)
            minPoints.forEach { lineTo(it.x, it.y) }
            maxPoints.reversed().forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(rangePath, color = Color.White.copy(alpha = 0.1f))

        avgPoints.zipWithNext().forEach { (start, end) ->
            drawLine(color = Color(0xFFFFEB3B), start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }

        selectedIndex?.let { index ->
            val selectedAvg = avgPoints[index]
            val forecast = forecasts[index]

            drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(selectedAvg.x, 0f), end = Offset(selectedAvg.x, graphHeight), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, radius = 8f, center = selectedAvg)
            drawCircle(Color(0xFFFFEB3B), radius = 6f, center = selectedAvg)

            val anchorOffset = 12.dp.toPx()
            val isNearEnd = selectedAvg.x > size.width * 0.75
            val labelAnchorX = if (isNearEnd) selectedAvg.x - anchorOffset else selectedAvg.x + anchorOffset

            val avgText = "Avg: ${forecast.avg}"
            val minText = "Min: ${forecast.min}"
            val maxText = "Max: ${forecast.max}"

            val avgTextLayout = textMeasurer.measure(AnnotatedString(avgText), style = TextStyle(color = Color.White, fontWeight = FontWeight.Black))
            val minTextLayout = textMeasurer.measure(AnnotatedString(minText), style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold))
            val maxTextLayout = textMeasurer.measure(AnnotatedString(maxText), style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold))

            val baseOffsetX = if (isNearEnd) -(avgTextLayout.size.width.toFloat()) else 0f

            drawText(textLayoutResult = avgTextLayout, topLeft = Offset(labelAnchorX + baseOffsetX, selectedAvg.y - (avgTextLayout.size.height / 2)))
            if (forecast.min != forecast.avg) {
                drawText(textLayoutResult = minTextLayout, topLeft = Offset(labelAnchorX + baseOffsetX, selectedAvg.y + avgTextLayout.size.height * 0.75f))
            }
            if (forecast.max != forecast.avg) {
                drawText(textLayoutResult = maxTextLayout, topLeft = Offset(labelAnchorX + baseOffsetX, selectedAvg.y - maxTextLayout.size.height * 1.75f))
            }
        }
    }
}
