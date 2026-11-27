package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Real-time reactive audio waveform visualizer rendered with Jetpack Compose Canvas.
 * Combines symmetric rounded acoustic frequency bars fed by live SpeechRecognizer RMS samples
 * with a glowing Tron-inspired harmonic carrier wave that responds immediately to microphone input.
 */
@Composable
fun VoiceWaveformVisualizer(
    waveformSamples: List<Float>,
    rmsDb: Float,
    isListening: Boolean,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_waveform_phase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessing) 900 else 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_carrier_phase"
    )

    val normalizedRms = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    val smoothEnergy by animateFloatAsState(
        targetValue = when {
            isProcessing -> 0.55f
            isListening -> normalizedRms.coerceAtLeast(0.16f)
            else -> 0.05f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "waveform_energy"
    )

    val description = when {
        isProcessing -> "Processando comando de voz"
        isListening -> "Visualizador de forma de onda reagindo ao microfone"
        else -> "Visualizador de áudio em espera"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("voice_waveform_visualizer")
            .semantics { contentDescription = description }
    ) {
        val width = size.width
        val canvasHeight = size.height
        if (width <= 0f || canvasHeight <= 0f) return@Canvas

        val centerY = canvasHeight / 2f
        val barCount = waveformSamples.size.coerceIn(16, 40)
        val slotWidth = width / barCount.toFloat()
        val barWidth = (slotWidth * 0.54f).coerceIn(2.dp.toPx(), 6.dp.toPx())
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val maxHalfHeight = (canvasHeight * 0.44f).coerceAtLeast(4.dp.toPx())

        // Subtle horizontal Tron baseline rail
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryColor.copy(alpha = if (isListening || isProcessing) 0.42f else 0.16f),
                    secondaryColor.copy(alpha = if (isListening || isProcessing) 0.42f else 0.16f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        val activeGradient = Brush.verticalGradient(
            colors = listOf(
                primaryColor,
                secondaryColor,
                primaryColor
            ),
            startY = centerY - maxHalfHeight,
            endY = centerY + maxHalfHeight
        )

        // Mirrored reactive acoustic bars driven by real microphone RMS history
        for (i in 0 until barCount) {
            val sample = waveformSamples.getOrElse(i) { 0.08f }.coerceIn(0.05f, 1f)
            val positionRatio = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            // Bell-curve envelope keeps edges sleek while amplifying center vocal energy
            val envelope = 0.45f + 0.55f * sin(positionRatio * PI).toFloat()
            val harmonicRipple = when {
                isProcessing -> 0.35f + 0.45f * abs(sin(wavePhase + i * 0.42f))
                isListening -> {
                    val liveBoost = (sample * 0.78f + smoothEnergy * 0.32f)
                    val subtleMotion = 0.10f * abs(sin(wavePhase + i * 0.55f))
                    (liveBoost + subtleMotion).coerceIn(0.08f, 1f)
                }
                else -> 0.10f + 0.04f * abs(sin(wavePhase * 0.5f + i * 0.3f))
            }

            val barHalfHeight = (maxHalfHeight * harmonicRipple * envelope).coerceAtLeast(2.dp.toPx())
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val alpha = when {
                isProcessing -> 0.72f
                isListening -> (0.45f + 0.55f * harmonicRipple).coerceIn(0.45f, 1f)
                else -> 0.28f
            }

            drawRoundRect(
                brush = activeGradient,
                topLeft = Offset(x, centerY - barHalfHeight),
                size = Size(barWidth, barHalfHeight * 2f),
                cornerRadius = cornerRadius,
                alpha = alpha
            )
        }

        // Glowing harmonic sine overlay reacting to instantaneous microphone RMS
        if (isListening || isProcessing) {
            val wavePath = Path()
            val steps = 48
            val amplitude = maxHalfHeight * (0.28f + smoothEnergy * 0.62f)
            for (step in 0..steps) {
                val t = step.toFloat() / steps.toFloat()
                val x = t * width
                val edgeFade = sin(t * PI).toFloat()
                val sampleIdx = (t * (waveformSamples.size - 1)).toInt().coerceIn(0, (waveformSamples.size - 1).coerceAtLeast(0))
                val localSample = waveformSamples.getOrElse(sampleIdx) { smoothEnergy }
                val yOffset = sin(t * PI * 4.0 + wavePhase).toFloat() * amplitude * edgeFade * (0.55f + localSample * 0.65f)
                val y = centerY + yOffset
                if (step == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }

            // Soft outer neon glow stroke
            drawPath(
                path = wavePath,
                color = tertiaryColor.copy(alpha = 0.24f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            // Crisp inner neon filament
            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.2f),
                        tertiaryColor.copy(alpha = 0.92f),
                        primaryColor.copy(alpha = 0.92f),
                        secondaryColor.copy(alpha = 0.2f)
                    )
                ),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
