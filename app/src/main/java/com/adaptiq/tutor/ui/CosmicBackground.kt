package com.adaptiq.tutor.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * CosmicBackground - A low-power, hardware-accelerated Canvas particle effect.
 *
 * Renders a subtle animated starfield using Compose Canvas. Designed to be
 * visually engaging for younger learners while remaining battery-efficient.
 *
 * Implementation uses a fixed particle pool (no allocations per frame)
 * and relies on Compose's hardware-accelerated Canvas rendering.
 */

private const val PARTICLE_COUNT = 60
private const val MAX_SPEED = 0.3f
private const val MAX_RADIUS = 2.5f
private const val MIN_RADIUS = 0.5f

private data class Particle(
    var x: Float,
    var y: Float,
    var radius: Float,
    var alpha: Float,
    var speedX: Float,
    var speedY: Float,
    var twinkleSpeed: Float,
    var twinklePhase: Float
)

@Composable
fun CosmicBackground(modifier: Modifier = Modifier) {
    // Pre-allocate particle pool — no GC pressure during animation
    val particles = remember {
        List(PARTICLE_COUNT) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * (MAX_RADIUS - MIN_RADIUS) + MIN_RADIUS,
                alpha = Random.nextFloat() * 0.5f + 0.2f,
                speedX = (Random.nextFloat() - 0.5f) * MAX_SPEED,
                speedY = (Random.nextFloat() - 0.5f) * MAX_SPEED,
                twinkleSpeed = Random.nextFloat() * 0.02f + 0.005f,
                twinklePhase = Random.nextFloat() * 6.28f
            )
        }.toMutableList()
    }

    var frameCount by remember { mutableFloatStateOf(0f) }

    // Animate frame counter to drive particle updates
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Update frame counter
    LaunchedEffect(animatedTime) {
        frameCount = animatedTime
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Deep space background gradient (drawn by the parent theme, this is transparent)
        drawRect(Color(0xFF0B0E1A))

        // Update and draw particles
        for (particle in particles) {
            // Update position with wrapping
            particle.x += particle.speedX / w
            particle.y += particle.speedY / h
            if (particle.x < 0) particle.x += 1f
            if (particle.x > 1) particle.x -= 1f
            if (particle.y < 0) particle.y += 1f
            if (particle.y > 1) particle.y -= 1f

            // Twinkle effect
            val twinkle = (sin(frameCount * particle.twinkleSpeed + particle.twinklePhase) + 1f) / 2f
            val alpha = particle.alpha * (0.4f + 0.6f * twinkle)

            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = particle.radius,
                center = Offset(particle.x * w, particle.y * h)
            )
        }

        // Draw a few subtle nebula blobs
        drawNebulaEffect(w, h, frameCount)
    }
}

private fun DrawScope.drawNebulaEffect(w: Float, h: Float, time: Float) {
    val nebulaPoints = listOf(
        Offset(w * 0.2f, h * 0.3f) to Color(0x0844A4E0),
        Offset(w * 0.7f, h * 0.6f) to Color(0x08A855F7),
        Offset(w * 0.5f, h * 0.8f) to Color(0x08F43F5E),
    )

    for ((center, color) in nebulaPoints) {
        val wobbleX = cos(time * 0.01f) * 20f
        val wobbleY = sin(time * 0.008f) * 15f
        drawCircle(
            color = color,
            radius = 180f,
            center = Offset(center.x + wobbleX, center.y + wobbleY)
        )
    }
}
