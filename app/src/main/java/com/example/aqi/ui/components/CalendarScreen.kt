package com.example.aqi.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aqi.ForecastDetails
import com.example.aqi.data.database.AqiDatabase
import com.example.aqi.data.database.AqiEntity
import com.example.aqi.data.database.HourlyAqiEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(cityName: String, forecasts: ForecastDetails?) {
    val context = LocalContext.current
    val db = remember { AqiDatabase.getDatabase(context) }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    
    var viewDate by remember { mutableStateOf(Calendar.getInstance()) }
    val today = remember { Calendar.getInstance() }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today.time) }
    var selectedDayOfMonth by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    
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

    // CLOUD SYNC: Now triggers on day selection too!
    LaunchedEffect(viewDate, cityName, selectedDayOfMonth) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        val cal = viewDate.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
        val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(viewDate.time)
        
        scope.launch {
            try {
                // 1. Sync all daily peaks for the month
                val snapshot = firestore.collection("users").document(userId)
                    .collection("history")
                    .whereGreaterThanOrEqualTo("date", "$monthStr-01")
                    .whereLessThanOrEqualTo("date", "$monthStr-31")
                    .get().await()
                
                snapshot.documents.forEach { doc ->
                    val date = doc.getString("date") ?: ""
                    val aqi = doc.getLong("aqi")?.toInt() ?: 0
                    val city = doc.getString("cityName") ?: cityName
                    db.aqiDao().insertAqiRecord(AqiEntity(date, city, aqi))
                }

                // 2. Sync hourly details for the currently SELECTED day
                val hourlySnapshot = firestore.collection("users").document(userId)
                    .collection("history").document(selectedDateStr)
                    .collection("hourly").get().await()
                
                hourlySnapshot.documents.forEach { hDoc ->
                    val hour = hDoc.id.toIntOrNull() ?: 0
                    val hAqi = hDoc.getLong("aqi")?.toInt() ?: 0
                    db.aqiDao().insertHourlyRecord(HourlyAqiEntity(0, selectedDateStr, hour, cityName, hAqi))
                }
            } catch (e: Exception) {}
        }
    }

    // Local DB Observers
    val monthQuery = remember(viewDate) { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(viewDate.time) + "%" }
    val historyRecords by db.aqiDao().getAqiRecordsForMonthAndCity(monthQuery, cityName).collectAsState(initial = emptyList())

    val selectedFullDateStr = remember(viewDate, selectedDayOfMonth) {
        val cal = viewDate.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }
    val hourlyRecords by db.aqiDao().getHourlyRecordsForDay(selectedFullDateStr, cityName).collectAsState(initial = emptyList())

    val aqiMap = remember(historyRecords, forecasts, viewDate) {
        val map = mutableMapOf<Int, Int>()
        historyRecords.forEach { entity ->
            val day = entity.date.substringAfterLast("-").toIntOrNull()
            if (day != null) map[day] = entity.aqi
        }
        if (isCurrentMonth && forecasts != null) {
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val forecastDays = (forecasts.pm25?.map { it.day } ?: emptyList()) + (forecasts.pm10?.map { it.day } ?: emptyList()) + (forecasts.o3?.map { it.day } ?: emptyList())
            forecastDays.distinct().forEach { dayStr ->
                if (dayStr <= todayStr) {
                    try {
                        val date = df.parse(dayStr)
                        val cal = Calendar.getInstance().apply { time = date!! }
                        if (cal.get(Calendar.MONTH) == viewDate.get(Calendar.MONTH) && cal.get(Calendar.YEAR) == viewDate.get(Calendar.YEAR)) {
                            val dayInt = cal.get(Calendar.DAY_OF_MONTH)
                            if (!map.containsKey(dayInt)) {
                                val pm25 = forecasts.pm25?.find { it.day == dayStr }?.avg
                                val pm10 = forecasts.pm10?.find { it.day == dayStr }?.avg
                                val o3 = forecasts.o3?.find { it.day == dayStr }?.avg
                                val maxAvg = listOfNotNull(pm25, pm10, o3).maxOrNull()
                                if (maxAvg != null) map[dayInt] = maxAvg
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        map
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(text = "Historical Record", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
        Text(text = "Live Cloud History Enabled", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00E676))
        
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewDate = (viewDate.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
            }
            Text(text = currentMonthName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
            IconButton(onClick = { viewDate = (viewDate.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
            Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)).blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp))
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                        Text(text = day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.height(220.dp)) {
                    LazyVerticalGrid(columns = GridCells.Fixed(7), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                        items(firstDayOfWeek) { Box(modifier = Modifier.size(40.dp)) }
                        items(daysInMonth) { index ->
                            val dayOfMonth = index + 1
                            val isFuture = if (isCurrentMonth) dayOfMonth > today.get(Calendar.DAY_OF_MONTH) else viewDate.after(today)
                            CalendarDayCell(day = dayOfMonth, aqi = aqiMap[dayOfMonth], isFuture = isFuture, isSelected = selectedDayOfMonth == dayOfMonth, onClick = { if (!isFuture) selectedDayOfMonth = dayOfMonth })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Timely Trends: Day $selectedDayOfMonth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        DailyHourlyChart(hourlyRecords = hourlyRecords)
        Spacer(modifier = Modifier.height(24.dp))
        CalendarLegend()
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun DailyHourlyChart(hourlyRecords: List<HourlyAqiEntity>) {
    var selectedBarIndex by remember { mutableIntStateOf(-1) }
    Box(modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp))) {
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)).blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp))
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                for (hour in 0..23) {
                    val record = hourlyRecords.find { it.hour == hour }
                    val aqi = record?.aqi
                    val isSelected = selectedBarIndex == hour
                    val barColor = when {
                        aqi == null -> Color.White.copy(alpha = 0.05f)
                        aqi <= 50 -> Color(0xFF00E676)
                        aqi <= 100 -> Color(0xFFFFEA00)
                        aqi <= 150 -> Color(0xFFFF9100)
                        aqi <= 200 -> Color(0xFFFF5252)
                        else -> Color(0xFFD500F9)
                    }
                    val heightPercent = if (aqi != null) (aqi / 300f).coerceIn(0.05f, 1f) else 0.05f
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(heightPercent).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(if (isSelected) Color.White else barColor).clickable { selectedBarIndex = if (selectedBarIndex == hour) -1 else hour })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 4, 8, 12, 16, 20, 23).forEach { h ->
                    val label = when { h == 0 -> "12 AM"; h < 12 -> "$h AM"; h == 12 -> "12 PM"; else -> "${h-12} PM" }
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
            }
            if (selectedBarIndex != -1) {
                val record = hourlyRecords.find { it.hour == selectedBarIndex }
                val timeLabel = when { selectedBarIndex == 0 -> "12 AM"; selectedBarIndex < 12 -> "$selectedBarIndex AM"; selectedBarIndex == 12 -> "12 PM"; else -> "${selectedBarIndex-12} PM" }
                Text(text = "Time: $timeLabel | AQI: ${record?.aqi ?: "No Record"}", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun CalendarDayCell(day: Int, aqi: Int?, isFuture: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = when {
        isFuture -> Color.White.copy(alpha = 0.05f)
        aqi == null -> Color.White.copy(alpha = 0.1f)
        aqi <= 50 -> Color(0xFF00E676)
        aqi <= 100 -> Color(0xFFFFEA00)
        aqi <= 150 -> Color(0xFFFF9100)
        aqi <= 200 -> Color(0xFFFF5252)
        else -> Color(0xFFD500F9)
    }
    val contentColor = if (aqi != null && aqi in 51..100) Color.Black else Color.White
    Box(modifier = Modifier.aspectRatio(1f).background(backgroundColor, RoundedCornerShape(12.dp)).border(width = 2.dp, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = day.toString(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = contentColor.copy(alpha = 0.8f))
            if (aqi != null) {
                Text(text = aqi.toString(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = contentColor)
            }
        }
    }
}

@Composable
fun CalendarLegend() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LegendItem("Good", Color(0xFF00E676)); LegendItem("Mod", Color(0xFFFFEA00)); LegendItem("Unhealthy", Color(0xFFFF9100)); LegendItem("Bad", Color(0xFFFF5252))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
