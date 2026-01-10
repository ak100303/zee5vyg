package com.example.aqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aqi.ForecastDay
import com.example.aqi.data.database.AqiDatabase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(forecasts: List<ForecastDay>) {
    val context = LocalContext.current
    val db = remember { AqiDatabase.getDatabase(context) }
    
    var viewDate by remember { mutableStateOf(Calendar.getInstance()) }
    
    // Optimization: Use derivedStateOf for values that change only when viewDate changes
    val today = remember { Calendar.getInstance() }
    val isCurrentMonth by remember(viewDate) {
        derivedStateOf {
            viewDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) && 
            viewDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
    }

    val currentMonthName = remember(viewDate) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(viewDate.time)
    }
    
    val daysInMonth = remember(viewDate) { viewDate.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val firstDayOfWeek = remember(viewDate) {
        val helper = (viewDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        helper.get(Calendar.DAY_OF_WEEK) - 1
    }

    // Load history
    val monthQuery = remember(viewDate) { 
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(viewDate.time) + "%" 
    }
    val historyRecords by db.aqiDao().getAqiRecordsForMonth(monthQuery).collectAsState(initial = emptyList())

    // Optimization: Index data into a Map for O(1) lookup during grid rendering
    val aqiMap = remember(historyRecords, forecasts, viewDate) {
        val map = mutableMapOf<Int, Int>()
        
        // 1. Add historical data from DB
        historyRecords.forEach { entity ->
            val day = entity.date.substringAfterLast("-").toIntOrNull()
            if (day != null) map[day] = entity.aqi
        }
        
        // 2. Add/Override with API forecast data (more recent for today/tomorrow)
        val curMonth = viewDate.get(Calendar.MONTH)
        val curYear = viewDate.get(Calendar.YEAR)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        forecasts.forEach { f ->
            try {
                val date = df.parse(f.day)
                val cal = Calendar.getInstance().apply { time = date!! }
                if (cal.get(Calendar.MONTH) == curMonth && cal.get(Calendar.YEAR) == curYear) {
                    map[cal.get(Calendar.DAY_OF_MONTH)] = f.avg
                }
            } catch (e: Exception) {}
        }
        map
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Historical Record",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewDate = (viewDate.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
            }

            Text(
                text = currentMonthName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(onClick = {
                viewDate = (viewDate.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(firstDayOfWeek) { Box(modifier = Modifier.size(45.dp)) }

            items(daysInMonth) { index ->
                val dayOfMonth = index + 1
                val isFuture = if (isCurrentMonth) dayOfMonth > today.get(Calendar.DAY_OF_MONTH) else viewDate.after(today)
                val aqiValue = if (!isFuture) aqiMap[dayOfMonth] else null
                
                CalendarDayCell(day = dayOfMonth, aqi = aqiValue, isFuture = isFuture)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        CalendarLegend()
    }
}

@Composable
fun CalendarDayCell(day: Int, aqi: Int?, isFuture: Boolean) {
    val backgroundColor = remember(aqi, isFuture) {
        when {
            isFuture -> Color.White.copy(alpha = 0.02f)
            aqi == null -> Color.White.copy(alpha = 0.05f)
            aqi <= 50 -> Color(0xFF4CAF50)
            aqi <= 100 -> Color(0xFFFFEB3B)
            aqi <= 150 -> Color(0xFFFF9800)
            aqi <= 200 -> Color(0xFFF44336)
            else -> Color(0xFF9C27B0)
        }
    }

    val contentColor = remember(aqi, isFuture) {
        when {
            isFuture -> Color.White.copy(alpha = 0.2f)
            aqi != null && aqi in 51..100 -> Color.Black 
            else -> Color.White
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (isFuture) Color.Transparent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = 12.sp,
                fontWeight = if (isFuture) FontWeight.Normal else FontWeight.Bold,
                color = contentColor.copy(alpha = 0.8f)
            )
            if (aqi != null && !isFuture) {
                Text(
                    text = aqi.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem("Good", Color(0xFF4CAF50))
        LegendItem("Mod", Color(0xFFFFEB3B))
        LegendItem("Unhealthy", Color(0xFFFF9800))
        LegendItem("Bad", Color(0xFFF44336))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}
