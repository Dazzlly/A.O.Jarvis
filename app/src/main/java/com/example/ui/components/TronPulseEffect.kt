package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TronPulseVisualState(
    val borderAlpha: Float,
    val glowAlpha: Float,
    val ringExpandFraction: Float
)

/**
 * Produces a subtle, power-conscious Tron neon breathing pulse whenever the assistant
 * is actively listening (foreground STT, Gemini Live, or active background listening).
 * Smoothly settles to calm static values when idle.
 */
@Composable
fun rememberTronPulseState(isListening: Boolean): TronPulseVisualState {
    val activeFactor by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "tron_pulse_active_factor"
    )

    if (activeFactor <= 0.005f) {
        return TronPulseVisualState(
            borderAlpha = 0.30f,
            glowAlpha = 0f,
            ringExpandFraction = 0f
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tron_listening_pulse")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tron_breath"
    )
    val ripple by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tron_ripple"
    )

    val borderAlpha = 0.30f + (0.28f + 0.38f * breath) * activeFactor
    val glowAlpha = (0.14f + 0.24f * breath) * activeFactor

    return TronPulseVisualState(
        borderAlpha = borderAlpha.coerceIn(0.25f, 0.96f),
        glowAlpha = glowAlpha.coerceIn(0f, 0.45f),
        ringExpandFraction = ripple * activeFactor
    )
}

/**
 * Subtle concentric Tron neon pulse halo around circular listening affordances
 * (such as the Tron Data Disc emblem or the active microphone button).
 */
@Composable
fun TronPulseHaloBox(
    isListening: Boolean,
    haloSize: Dp,
    modifier: Modifier = Modifier,
    pulseColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    content: @Composable BoxScope.() -> Unit
) {
    val pulse = rememberTronPulseState(isListening = isListening)

    Box(
        modifier = modifier.size(haloSize),
        contentAlignment = Alignment.Center
    ) {
        if (pulse.glowAlpha > 0.01f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val maxR = size.minDimension / 2f
                if (maxR <= 1f) return@Canvas
                val center = Offset(size.width / 2f, size.height / 2f)

                // Soft inner radial Tron glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            pulseColor.copy(alpha = pulse.glowAlpha),
                            secondaryColor.copy(alpha = pulse.glowAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR.coerceAtLeast(1f)
                    ),
                    radius = maxR,
                    center = center
                )

                // Expanding subtle neon pulse ring
                val ringRadius = (maxR * (0.64f + 0.32f * pulse.ringExpandFraction)).coerceAtLeast(1f)
                val ringAlpha = ((1f - pulse.ringExpandFraction) * 0.65f).coerceIn(0f, 0.65f)
                drawCircle(
                    color = pulseColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
        }
        content()
    }
}
