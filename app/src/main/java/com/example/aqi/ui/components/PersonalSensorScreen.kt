package com.example.aqi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun PersonalSensorScreen(onAqiChanged: (Int) -> Unit) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    
    var ppmValue by remember { mutableFloatStateOf(400f) }
    var tempValue by remember { mutableFloatStateOf(0f) }
    var humValue by remember { mutableFloatStateOf(0f) }
    var aqiValue by remember { mutableIntStateOf(0) }
    var lastUpdate by remember { mutableStateOf("Locating Station...") }

    // REAL-TIME LISTENER
    LaunchedEffect(Unit) {
        val docRef = firestore.collection("users").document("esp32_device")
            .collection("iot_devices").document("station_01")
            
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                lastUpdate = "Connection Error"
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                ppmValue = snapshot.getDouble("ppm")?.toFloat() ?: 400f
                tempValue = snapshot.getDouble("temp")?.toFloat() ?: 0f
                humValue = snapshot.getDouble("humidity")?.toFloat() ?: 0f
                
                val newAqi = snapshot.getLong("aqi")?.toInt() ?: calculateAqiFromPpm(ppmValue)
                aqiValue = newAqi
                
                // Alert logic is now handled by PersonalAqiMonitorService
                // ensuring it works even when the app is closed.
                
                onAqiChanged(newAqi)
                lastUpdate = "Live Lab-Grade Stream"
            }
        }
    }

    val animatedAqi by animateFloatAsState(
        targetValue = aqiValue.toFloat(),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "IotGauge"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text("Internal Air Station", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
        Surface(color = Color.Green.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
            Text(
                text = "● $lastUpdate", 
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, 
                color = Color.Green, 
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            AqiGauge(aqi = animatedAqi.toInt()) 
        }

        Spacer(modifier = Modifier.height(32.dp))

        SensorValueCard(label = "MQ135 SENSOR", value = String.format("%.1f", ppmValue), unit = "PPM", subValue = "Smoke, Alcohol, CO2")

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SensorValueCard(label = "TEMP", value = String.format("%.1f", tempValue), unit = "°C", subValue = "Indoor")
            }
            Box(modifier = Modifier.weight(1f)) {
                SensorValueCard(label = "HUMIDITY", value = String.format("%.1f", humValue), unit = "%", subValue = "Indoor")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = getPpmAdvice(ppmValue),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(20.dp),
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun SensorValueCard(label: String, value: String, unit: String, subValue: String) {
    Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(32.dp))) {
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)).blur(30.dp))
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.4f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(unit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp), color = Color.Cyan)
            }
            Text(subValue, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

fun calculateAqiFromPpm(ppm: Float): Int {
    return when {
        ppm <= 600 -> (ppm / 12).toInt() 
        ppm <= 1000 -> (51 + (ppm - 600) / 8).toInt()
        ppm <= 1500 -> (101 + (ppm - 1000) / 10).toInt()
        else -> (151 + (ppm - 1500) / 15).toInt().coerceAtMost(500)
    }
}

fun getPpmAdvice(ppm: Float): String {
    return when {
        ppm < 600 -> "Air is scientifically clean. Mental focus is at maximum."
        ppm < 1000 -> "Acceptable levels. No immediate health risks detected."
        ppm < 1500 -> "Stale air detected. Open a window for a few minutes."
        else -> "ALERT: High gas detected! Ventilate immediately!"
    }
}
