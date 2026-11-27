package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.EngineType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrbVisualizer(
    isActive: Boolean,
    rmsDb: Float,
    accentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    centerContent: @Composable () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )

    val normalizedRms = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    val animatedRms by animateFloatAsState(
        targetValue = if (isActive) normalizedRms.coerceAtLeast(0.25f) else 0f,
        animationSpec = spring(stiffness = 280f),
        label = "rms_level"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val maxRadius = this.size.minDimension / 2f
            if (maxRadius <= 1f) return@Canvas
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isActive) 0.34f else 0.14f),
                        secondaryColor.copy(alpha = if (isActive) 0.12f else 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (maxRadius * (0.85f + animatedRms * 0.18f)).coerceAtLeast(1f)
                ),
                radius = maxRadius,
                center = center
            )

            // Outer concentric acoustic rings
            val ringCount = 3
            for (i in 0 until ringCount) {
                val factor = 0.52f + i * 0.16f + (if (isActive) animatedRms * 0.08f else 0f)
                val wave = if (isActive) sin(phase + i * 1.1f) * 4.dp.toPx() else (idleBreath - 1f) * 6.dp.toPx()
                val ringRadius = (maxRadius * factor + wave).coerceIn(1f, (maxRadius - 2.dp.toPx()).coerceAtLeast(1f))
                val alpha = if (isActive) (0.72f - i * 0.2f) else (0.30f - i * 0.08f)

                drawCircle(
                    color = if (i % 2 == 0) accentColor.copy(alpha = alpha) else secondaryColor.copy(alpha = alpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = if (i == 0) 2.5.dp.toPx() else 1.5.dp.toPx())
                )
            }

            // Orbital telemetry nodes
            if (isActive) {
                for (dot in 0 until 6) {
                    val angle = phase + dot * (Math.PI.toFloat() / 3f)
                    val orbitR = maxRadius * (0.74f + animatedRms * 0.08f)
                    val dotPos = Offset(
                        x = center.x + cos(angle) * orbitR,
                        y = center.y + sin(angle) * orbitR
                    )
                    drawCircle(
                        color = if (dot % 2 == 0) accentColor else secondaryColor,
                        radius = 3.dp.toPx(),
                        center = dotPos
                    )
                }
            }

            // Inner core disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isActive) 0.45f else 0.22f),
                        accentColor.copy(alpha = 0.08f)
                    ),
                    center = center,
                    radius = (maxRadius * 0.46f).coerceAtLeast(1f)
                ),
                radius = (maxRadius * 0.44f * idleBreath).coerceAtLeast(1f),
                center = center
            )
        }

        centerContent()
    }
}

@Composable
fun EngineBadge(
    engineName: String,
    modelName: String = "",
    modifier: Modifier = Modifier
) {
    val engine = runCatching { EngineType.valueOf(engineName) }.getOrNull()
    val (bg, fg, icon, label) = when (engine) {
        EngineType.OLLAMA -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.Computer,
            "Ollama LAN"
        )

        EngineType.OFFLINE -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Rounded.OfflineBolt,
            "Offline"
        )

        EngineType.GEMINI_LIVE -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.GraphicEq,
            "Gemini Live"
        )

        else -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.AutoAwesome,
            "Gemini"
        )
    }

    Surface(
        modifier = modifier,
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (modelName.isNotBlank()) "$label · $modelName" else label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
