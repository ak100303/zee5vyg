package com.example.aqi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.max
import kotlin.math.min

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
            "Daily Forecast ($dateRange)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
        ) {
            if (forecasts.isNotEmpty()) {
                AqiForecastGraph(
                    forecasts = forecasts,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(250.dp)
                )
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

        // --- Dynamic Y-Axis Scaling ---
        val minAqiValue = forecasts.minOfOrNull { it.min } ?: 0
        val maxAqiValue = forecasts.maxOfOrNull { it.max } ?: 200
        val range = (maxAqiValue - minAqiValue).toFloat().coerceAtLeast(1f)
        val paddedMin = (minAqiValue - range * 0.2f).coerceAtLeast(0f)
        val paddedMax = (maxAqiValue + range * 0.2f)
        val dynamicRange = (paddedMax - paddedMin).coerceAtLeast(1f)

        val xStep = size.width / (forecasts.size - 1).coerceAtLeast(1)

        // --- Draw Grid and X-Axis Labels ---
        forecasts.forEachIndexed { index, forecast -> // X-axis day labels
            val x = index * xStep
            val dayText = SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.day)!!)

            drawText(
                textMeasurer = textMeasurer,
                text = dayText,
                topLeft = Offset(x - (textMeasurer.measure(AnnotatedString(dayText)).size.width / 2), graphHeight + 5.dp.toPx()),
                style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            )
        }

        // --- Map data to canvas points using dynamic scale ---
        val scaleY = { value: Int -> graphHeight - ((value - paddedMin) / dynamicRange * graphHeight) }
        val avgPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.avg)) }
        val minPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.min)) }
        val maxPoints = forecasts.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.max)) }

        // --- Draw Min-Max Range Area ---
        val rangePath = Path().apply {
            moveTo(minPoints.first().x, minPoints.first().y)
            minPoints.forEach { lineTo(it.x, it.y) }
            maxPoints.reversed().forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(rangePath, color = Color.White.copy(alpha = 0.1f))

        // --- Draw Average Line ---
        avgPoints.zipWithNext().forEach { (start, end) ->
            drawLine(color = Color(0xFFFFEB3B), start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }

        // --- Draw Interactive Scrubber and Labels ---
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

            val avgTextLayout = textMeasurer.measure(AnnotatedString(avgText), style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
            val minTextLayout = textMeasurer.measure(AnnotatedString(minText), style = TextStyle(color = Color.White.copy(alpha = 0.8f)))
            val maxTextLayout = textMeasurer.measure(AnnotatedString(maxText), style = TextStyle(color = Color.White.copy(alpha = 0.8f)))

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
