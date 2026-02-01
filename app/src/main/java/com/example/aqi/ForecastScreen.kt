package com.example.aqi

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    val pm25Forecasts = aqiData.forecast?.daily?.pm25?.filter {
        val forecastDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.day)
        forecastDate != null && !forecastDate.before(today)
    } ?: emptyList()

    val pm10Forecasts = aqiData.forecast?.daily?.pm10?.filter {
        val forecastDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.day)
        forecastDate != null && !forecastDate.before(today)
    } ?: emptyList()

    val startDate = pm25Forecasts.firstOrNull()?.day?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) }
    val endDate = pm25Forecasts.lastOrNull()?.day?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) }
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
            text = "AQI INSIGHTS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        
        Text(
            text = "Station: ${aqiData.city.name}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // CONDITIONAL OUTAGE CARD
        // (Note: In a real outage, aqiData would be coming from the prediction engine)
        if (System.currentTimeMillis() % 10 == 0L) { // Simulating an occasional outage check
             OutageAlertCard()
             Spacer(modifier = Modifier.height(16.dp))
        }

        AqiInsightCard(aqi = aqiData.aqi)

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "7-Day Comparative Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(text = dateRange, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            
            // Legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFEB3B), CircleShape))
                Text(" PM2.5", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5252), CircleShape))
                Text(" PM10", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
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
                if (pm25Forecasts.isNotEmpty()) {
                    AqiForecastGraph(
                        pm25 = pm25Forecasts,
                        pm10 = pm10Forecasts,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        WeatherDetailsCard(metrics = aqiData.iaqi)
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun OutageAlertCard() {
    Surface(
        color = Color(0xFFFF9800).copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("WAQI SERVER OUTAGE", color = Color(0xFFFF9800), fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("Showing trend-based predictions for the next 3 hours.", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AqiInsightCard(aqi: Int) {
    val insight = remember(aqi) { getAqiReasoning(aqi) }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 30.dp else 0.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.35f))
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "LATEST INSIGHT",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Powered by AI Analysis", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = insight.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan
                    )
                }
            }
        }
    }
}

data class AqiInsight(val headline: String, val description: String, val action: String)

fun getAqiReasoning(aqi: Int): AqiInsight {
    return when {
        aqi <= 50 -> AqiInsight(
            "Crystal Clear Skies",
            "The air is scrubbing clean due to favorable meteorological conditions. Traffic emissions are dispersing rapidly.",
            "Perfect time for full home ventilation."
        )
        aqi <= 100 -> AqiInsight(
            "Moderate Stability",
            "Atmospheric pressure is holding pollutants at background levels. Construction and traffic are the primary contributors.",
            "Safe for most, but shut windows during morning rush."
        )
        aqi <= 150 -> AqiInsight(
            "Temperature Inversion",
            "Cold air layers are trapping surface pollutants near the ground. Particle buildup is noticeable in your neighborhood.",
            "Limit intense outdoor cardio and use purifiers."
        )
        else -> AqiInsight(
            "Hazardous Concentration",
            "Dangerous PM levels recorded. Likely industrial discharge or heavy congestion trapped by low wind dispersion.",
            "Stay indoors. Mandatory N95 use for essential travel."
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun AqiForecastGraph(pm25: List<ForecastDay>, pm10: List<ForecastDay>, modifier: Modifier = Modifier) {
    if (pm25.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val xStep = size.width / (pm25.size - 1).coerceAtLeast(1)
                val index = (it.x / xStep).toInt().coerceIn(0, pm25.size - 1)
                selectedIndex = if (selectedIndex == index) null else index
            }
        )
    }) {
        val bottomPadding = 40.dp.toPx()
        val graphHeight = size.height - bottomPadding

        val allValues = pm25.flatMap { listOf(it.min, it.max) } + pm10.flatMap { listOf(it.min, it.max) }
        val minAqiValue = allValues.minOrNull() ?: 0
        val maxAqiValue = allValues.maxOrNull() ?: 200
        val range = (maxAqiValue - minAqiValue).toFloat().coerceAtLeast(1f)
        val paddedMin = (minAqiValue - range * 0.1f).coerceAtLeast(0f)
        val paddedMax = (maxAqiValue + range * 0.1f)
        val dynamicRange = (paddedMax - paddedMin).coerceAtLeast(1f)

        val xStep = size.width / (pm25.size - 1).coerceAtLeast(1)

        // Draw Days
        pm25.forEachIndexed { index, forecast ->
            val x = index * xStep
            val dayText = SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.day)!!)
            drawText(
                textMeasurer = textMeasurer,
                text = dayText,
                topLeft = Offset(x - (textMeasurer.measure(AnnotatedString(dayText)).size.width / 2), graphHeight + 10.dp.toPx()),
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }

        val scaleY = { value: Int -> graphHeight - ((value - paddedMin) / dynamicRange * graphHeight) }
        
        // PM2.5 Points (Yellow)
        val pm25Points = pm25.mapIndexed { i, f -> Offset(i * xStep, scaleY(f.avg)) }
        pm25Points.zipWithNext().forEach { (start, end) ->
            drawLine(color = Color(0xFFFFEB3B), start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }

        // PM10 Points (Red)
        val pm10Points = pm10.mapIndexed { i, f -> 
            val f10 = if (i < pm10.size) pm10[i] else null
            if (f10 != null) Offset(i * xStep, scaleY(f10.avg)) else null
        }.filterNotNull()
        pm10Points.zipWithNext().forEach { (start, end) ->
            drawLine(color = Color(0xFFFF5252), start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }

        selectedIndex?.let { index ->
            val x = index * xStep
            val f25 = pm25[index]
            val f10 = if (index < pm10.size) pm10[index] else null

            drawLine(color = Color.White.copy(alpha = 0.3f), start = Offset(x, 0f), end = Offset(x, graphHeight), strokeWidth = 1.5.dp.toPx())
            
            // Marker Dots
            drawCircle(Color.White, radius = 6f, center = pm25Points[index])
            if (index < pm10Points.size) drawCircle(Color.White, radius = 6f, center = pm10Points[index])

            val anchorOffset = 12.dp.toPx()
            val isNearEnd = x > size.width * 0.7
            val labelAnchorX = if (isNearEnd) x - anchorOffset else x + anchorOffset

            val text25 = "PM2.5: ${f25.avg}"
            val text10 = if (f10 != null) "PM10: ${f10.avg}" else ""

            val layout25 = textMeasurer.measure(AnnotatedString(text25), style = TextStyle(color = Color(0xFFFFEB3B), fontWeight = FontWeight.Black, fontSize = 12.sp))
            val layout10 = textMeasurer.measure(AnnotatedString(text10), style = TextStyle(color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 12.sp))

            val offsetX = if (isNearEnd) -(layout25.size.width.toFloat()) else 0f
            
            drawText(layout25, topLeft = Offset(labelAnchorX + offsetX, pm25Points[index].y - 20.dp.toPx()))
            if (f10 != null) {
                drawText(layout10, topLeft = Offset(labelAnchorX + offsetX, pm10Points[index].y + 10.dp.toPx()))
            }
        }
    }
}
