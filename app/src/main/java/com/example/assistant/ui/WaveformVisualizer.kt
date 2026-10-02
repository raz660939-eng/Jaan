package com.example.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.assistant.SessionState
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    sessionState: SessionState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barCount = 36
    val primaryColor = when (sessionState) {
        SessionState.SPEAKING -> NeonPink
        SessionState.LISTENING -> NeonCyan
        SessionState.CONNECTING -> NeonViolet
        SessionState.DISCONNECTED -> Color(0xFF334155)
    }

    val secondaryColor = when (sessionState) {
        SessionState.SPEAKING -> NeonViolet
        SessionState.LISTENING -> NeonPink
        SessionState.CONNECTING -> NeonCyan
        SessionState.DISCONNECTED -> Color(0xFF1E293B)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val step = width / (barCount + 1)

        val brush = Brush.horizontalGradient(
            colors = listOf(secondaryColor, primaryColor, secondaryColor)
        )

        for (i in 0 until barCount) {
            val x = step * (i + 1)
            val normalizedX = i.toFloat() / barCount.toFloat()

            val baseSine = sin(normalizedX * 4 * Math.PI + phase).toFloat()
            val ampMultiplier = if (sessionState == SessionState.DISCONNECTED) {
                0.05f
            } else {
                (amplitude * 1.5f).coerceIn(0.12f, 1.0f)
            }

            // Calculate height with bell curve falloff near edges
            val bellFalloff = sin(normalizedX * Math.PI).toFloat()
            val barHeight = ((height * 0.45f) * ampMultiplier * (0.4f + 0.6f * baseSine.coerceAtLeast(0.1f)) * bellFalloff)
                .coerceAtLeast(4.dp.toPx())

            drawLine(
                brush = brush,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
