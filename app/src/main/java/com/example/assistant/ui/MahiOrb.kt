package com.example.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.assistant.SessionState
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet

@Composable
fun MahiOrb(
    sessionState: SessionState,
    inputAmplitude: Float,
    outputAmplitude: Float,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (sessionState) {
                    SessionState.CONNECTING -> 1200
                    SessionState.SPEAKING -> 2400
                    SessionState.LISTENING -> 4000
                    SessionState.DISCONNECTED -> 12000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val currentAmplitude = when (sessionState) {
        SessionState.SPEAKING -> outputAmplitude
        SessionState.LISTENING -> inputAmplitude
        else -> 0f
    }

    val animatedAmp by animateFloatAsState(
        targetValue = currentAmplitude,
        animationSpec = tween(80),
        label = "amp"
    )

    val orbColorPrimary by animateColorAsState(
        targetValue = when (sessionState) {
            SessionState.SPEAKING -> NeonPink
            SessionState.LISTENING -> NeonCyan
            SessionState.CONNECTING -> NeonViolet
            SessionState.DISCONNECTED -> Color(0xFF64748B)
        },
        label = "primary_color"
    )

    val orbColorSecondary by animateColorAsState(
        targetValue = when (sessionState) {
            SessionState.SPEAKING -> NeonViolet
            SessionState.LISTENING -> NeonPink
            SessionState.CONNECTING -> NeonCyan
            SessionState.DISCONNECTED -> Color(0xFF1E293B)
        },
        label = "secondary_color"
    )

    val dynamicScale = (1.0f + animatedAmp * 0.35f) * if (sessionState != SessionState.DISCONNECTED) breathingScale else 1.0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .testTag("mahi_orb_container")
    ) {
        // Multi-layered Canvas for glowing futuristic rings and animated arcs
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.38f

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColorPrimary.copy(alpha = if (sessionState == SessionState.DISCONNECTED) 0.08f else 0.25f + animatedAmp * 0.3f),
                        orbColorSecondary.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.6f * dynamicScale
                ),
                radius = baseRadius * 1.6f * dynamicScale,
                center = center
            )

            // Dynamic concentric ripple rings
            if (sessionState != SessionState.DISCONNECTED) {
                val ringCount = 3
                for (i in 1..ringCount) {
                    val ringRadius = baseRadius * (1f + (i * 0.18f) * (0.8f + animatedAmp * 0.6f))
                    drawCircle(
                        color = orbColorPrimary.copy(alpha = (0.22f / i) + (animatedAmp * 0.15f)),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = (2.dp.toPx() - (i * 0.4f)))
                    )
                }
            }

            // Orbital rotating arcs
            val arcStyle = Stroke(width = 3.dp.toPx())
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        orbColorPrimary.copy(alpha = 0.7f),
                        orbColorSecondary,
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = rotationAngle,
                sweepAngle = 140f,
                useCenter = false,
                style = arcStyle,
                topLeft = Offset(center.x - baseRadius * 1.15f, center.y - baseRadius * 1.15f),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2.3f, baseRadius * 2.3f)
            )

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        orbColorSecondary.copy(alpha = 0.8f),
                        orbColorPrimary,
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = -rotationAngle * 1.3f,
                sweepAngle = 100f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
                topLeft = Offset(center.x - baseRadius * 1.25f, center.y - baseRadius * 1.25f),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2.5f, baseRadius * 2.5f)
            )

            // Inner core gradient sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColorPrimary.copy(alpha = 0.95f),
                        orbColorSecondary.copy(alpha = 0.85f),
                        Color(0xFF0F172A)
                    ),
                    center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                    radius = baseRadius * 0.9f
                ),
                radius = baseRadius * 0.82f * (0.95f + animatedAmp * 0.12f),
                center = center
            )
        }

        // Center clickable action button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .scale(dynamicScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 55.dp),
                    onClick = onOrbClick
                )
                .testTag("mahi_orb_button")
        ) {
            val icon = when (sessionState) {
                SessionState.SPEAKING -> Icons.Default.Stop
                SessionState.LISTENING -> Icons.Default.GraphicEq
                SessionState.CONNECTING -> Icons.Default.Mic
                SessionState.DISCONNECTED -> Icons.Default.PowerSettingsNew
            }

            Icon(
                imageVector = icon,
                contentDescription = when (sessionState) {
                    SessionState.SPEAKING -> "Tap to interrupt Mahi"
                    SessionState.LISTENING -> "Mahi is listening"
                    SessionState.CONNECTING -> "Connecting to Mahi"
                    SessionState.DISCONNECTED -> "Tap to start voice chat"
                },
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
