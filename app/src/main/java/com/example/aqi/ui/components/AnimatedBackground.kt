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

private data class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var angle: Float,
    var size: Float,
    var alpha: Float
)

@Composable
fun AnimatedBackground(aqi: Int) {
    val isPolluted = aqi >= 81

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Image Layer with smooth Crossfade transition
        Crossfade(targetState = isPolluted, animationSpec = tween(3000), label = "BgTransition") { polluted ->
            Image(
                painter = painterResource(id = if (polluted) R.drawable.bg_polluted else R.drawable.bg_clear),
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
