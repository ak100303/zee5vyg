package com.example.aqi.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await

@Composable
fun DiscoveryMapScreen() {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    
    var discoveredAreas by remember { mutableStateOf<List<MapDiscovery>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch all unlocked areas from Cloud
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("discoveries").get().await()
            
            discoveredAreas = snapshot.documents.map { doc ->
                MapDiscovery(
                    name = doc.getString("name") ?: "Unknown",
                    lat = doc.getDouble("lat") ?: 0.0,
                    lon = doc.getDouble("lon") ?: 0.0,
                    aqi = doc.getLong("aqi")?.toInt() ?: 0
                )
            }
        } catch (e: Exception) {} finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val chennai = LatLng(13.0827, 80.2707) // Default center
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(chennai, 11f)
        }

        // 1. THE GAME MAP
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapStyleOptions = MapStyleOptions(MapStyle.DARK_JSON), // Sleek dark mode
                isMyLocationEnabled = true
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            discoveredAreas.forEach { area ->
                val color = when {
                    area.aqi <= 50 -> Color(0xFF00E676)
                    area.aqi <= 100 -> Color(0xFFFFEA00)
                    area.aqi <= 150 -> Color(0xFFFF9100)
                    else -> Color(0xFFFF5252)
                }
                
                Marker(
                    state = MarkerState(position = LatLng(area.lat, area.lon)),
                    title = area.name,
                    snippet = "AQI: ${area.aqi} Unlocked!",
                    alpha = 0.9f
                )
            }
        }

        // 2. QUEST DASHBOARD (Glassmorphism)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp, start = 20.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 30.dp else 0.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "NEIGHBORHOOD QUEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            "Level ${discoveredAreas.size / 5 + 1} Explorer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.Cyan
                        )
                    }
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${discoveredAreas.size} Unlocked",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

data class MapDiscovery(
    val name: String,
    val lat: Double,
    val lon: Double,
    val aqi: Int
)

object MapStyle {
    const val DARK_JSON = """
    [
      { "elementType": "geometry", "stylers": [ { "color": "#212121" } ] },
      { "elementType": "labels.text.fill", "stylers": [ { "color": "#757575" } ] },
      { "elementType": "labels.text.stroke", "stylers": [ { "color": "#212121" } ] },
      { "featureType": "administrative", "elementType": "geometry", "stylers": [ { "color": "#757575" } ] },
      { "featureType": "poi", "elementType": "geometry", "stylers": [ { "color": "#181818" } ] },
      { "featureType": "road", "elementType": "geometry.fill", "stylers": [ { "color": "#2c2c2c" } ] },
      { "featureType": "water", "elementType": "geometry", "stylers": [ { "color": "#000000" } ] }
    ]
    """
}
