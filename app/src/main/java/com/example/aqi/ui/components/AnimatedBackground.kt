package com.example.aqi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
fun AnimatedBackground() {
    val particles = remember { mutableStateListOf<Particle>() }
    val particleCount = 30 // Number of particles

    LaunchedEffect(Unit) {
        repeat(particleCount) {
            particles.add(
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = Random.nextFloat() * 0.00005f + 0.00001f,
                    angle = Random.nextFloat() * 360,
                    size = Random.nextFloat() * 4f + 2f,
                    alpha = Random.nextFloat() * 0.5f + 0.1f
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
            // Update particle position
            particle.y -= particle.speed
            particle.x += cos(particle.angle) * particle.speed * 0.5f

            // Reset particle when it goes off-screen
            if (particle.y < 0) {
                particle.y = 1f
                particle.x = Random.nextFloat()
            }
            if (particle.x < 0 || particle.x > 1) {
                particle.angle += 180
            }

            drawParticle(particle)
        }
    }
}

private fun DrawScope.drawParticle(particle: Particle) {
    drawCircle(
        color = Color.White,
        center = Offset(particle.x * size.width, particle.y * size.height),
        radius = particle.size,
        alpha = particle.alpha
    )
}
