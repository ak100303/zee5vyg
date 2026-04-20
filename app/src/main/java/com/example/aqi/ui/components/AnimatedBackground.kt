package com.example.aqi.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.aqi.R
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.random.Random
import java.util.Calendar

private data class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var angle: Float,
    var size: Float,
    var alpha: Float
)

@Composable
fun AnimatedBackground(aqi: Int, localHour: Int? = null) {
    val isPolluted = aqi >= 81

    val currentHour = localHour ?: remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    val bgRes = when (currentHour) {
        in 4..6 -> if (isPolluted) R.drawable.pollutant_dawn else R.drawable.dawn
        in 7..9 -> if (isPolluted) R.drawable.pollutant_morning else R.drawable.morning
        in 10..15 -> if (isPolluted) R.drawable.bg_polluted else R.drawable.bg_clear
        in 16..18 -> if (isPolluted) R.drawable.pollutant_evening else R.drawable.evening
        else -> if (isPolluted) R.drawable.pollutant_night else R.drawable.night
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Image Layer with smooth Crossfade transition
        Crossfade(targetState = bgRes, animationSpec = tween(3000), label = "BgTransition") { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 2. Readability Scrim: Darkens top and bottom to make text pop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f), // Top area
                            Color.Transparent,              // Center (keep art clear)
                            Color.Black.copy(alpha = 0.6f)   // Bottom area (cards)
                        )
                    )
                )
        )

        // 3. Animated Particle Overlay
        val particleColor = if (!isPolluted) Color.White.copy(alpha = 0.4f) else Color(0xFF5D4037).copy(alpha = 0.3f)
        val particles = remember { mutableStateListOf<Particle>() }
        val particleCount = 35

        LaunchedEffect(Unit) {
            repeat(particleCount) {
                particles.add(
                    Particle(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.00005f + 0.00001f,
                        angle = Random.nextFloat() * 360,
                        size = Random.nextFloat() * 3f + 1f,
                        alpha = Random.nextFloat() * 0.4f + 0.1f
                    )
                )
            }
        }

        val time by produceState(0L) {
            while (isActive) {
                value = withFrameNanos { it }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val currentSpeed = if (!isPolluted) particle.speed else particle.speed * 0.4f
                val currentSize = if (!isPolluted) particle.size else particle.size * 2f

                particle.y -= currentSpeed
                particle.x += cos(particle.angle) * currentSpeed * 0.3f

                if (particle.y < 0) {
                    particle.y = 1f
                    particle.x = Random.nextFloat()
                }
                
                drawCircle(
                    color = particleColor,
                    center = Offset(particle.x * size.width, particle.y * size.height),
                    radius = currentSize,
                    alpha = particle.alpha
                )
            }
        }
    }
}
