package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.ui.theme.DaylightAmber
import com.example.ui.theme.DaylightBorder
import com.example.ui.theme.DaylightCanvas
import com.example.ui.theme.DaylightCyan
import com.example.ui.theme.ObsidianAmethyst
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianGold
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TronAbyss
import com.example.ui.theme.TronCluOrange
import com.example.ui.theme.TronNeonCyan
import com.example.ui.theme.TronOutline
import java.util.Locale

private data class AccentColorPreset(
    val name: String,
    val argb: Long
)

private val CUSTOM_ACCENT_PRESETS = listOf(
    AccentColorPreset("Azul Neon", 0xFF00C8FFL),
    AccentColorPreset("Ciano Tron", 0xFF00F0FFL),
    AccentColorPreset("Laranja Clu", 0xFFFF7A00L),
    AccentColorPreset("Carmesim Laser", 0xFFFF2E63L),
    AccentColorPreset("Verde Matrix", 0xFF00E676L),
    AccentColorPreset("Ultravioleta", 0xFFA855F7L),
    AccentColorPreset("Rosa Plasma", 0xFFEC4899L),
    AccentColorPreset("Ouro Solar", 0xFFFACC15L),
    AccentColorPreset("Azul Cobalto", 0xFF3B82F6L),
    AccentColorPreset("Prata Gelo", 0xFFE2E8F0L)
)

@Composable
fun ThemeSelectorCard(
    selectedTheme: AppThemeMode,
    customAccentColor: Long,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectCustomAccentColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (selectedTheme == AppThemeMode.TRON || selectedTheme == AppThemeMode.CUSTOM) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Aparência & Temas",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Temas fixos (Tron Legacy, Obsidian Pro, Claro) ou tema Custom com sua cor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    ThemeOptionRow(
                        mode = mode,
                        customAccentColor = customAccentColor,
                        isSelected = mode == selectedTheme,
                        onClick = { onSelectTheme(mode) }
                    )
                }
            }

            AnimatedVisibility(visible = selectedTheme == AppThemeMode.CUSTOM) {
                CustomAccentColorEditor(
                    accentArgb = customAccentColor,
                    onAccentChange = onSelectCustomAccentColor,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: AppThemeMode,
    customAccentColor: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "theme_option_border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        label = "theme_option_bg"
    )

    val testTagName = when (mode) {
        AppThemeMode.TRON -> "theme_option_tron"
        AppThemeMode.OBSIDIAN -> "theme_option_obsidian"
        AppThemeMode.DAYLIGHT -> "theme_option_daylight"
        AppThemeMode.CUSTOM -> "theme_option_custom"
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeMiniSwatch(mode = mode, customAccentColor = customAccentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "ATIVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Tema ${mode.label} selecionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomAccentColorEditor(
    accentArgb: Long,
    onAccentChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(accentArgb.toInt())
    val hsv = remember(accentArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(accentArgb.toInt(), it) }
    }
    val hexLabel = remember(accentArgb) {
        String.format(Locale.US, "#%06X", accentArgb.toInt() and 0xFFFFFF)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_theme_color_panel")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ColorLens,
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Cor do Tema Custom",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = activeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                        Text(
                            text = hexLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CUSTOM_ACCENT_PRESETS.forEach { preset ->
                    val swatchColor = Color(preset.argb.toInt())
                    val isPicked = (preset.argb and 0xFFFFFFL) == (accentArgb and 0xFFFFFFL)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isPicked) 2.5.dp else 1.dp,
                                color = if (isPicked) Color.White else Color.White.copy(alpha = 0.22f),
                                shape = CircleShape
                            )
                            .semantics {
                                contentDescription = "Cor ${preset.name}"
                                selected = isPicked
                            }
                            .clickable(role = Role.RadioButton) {
                                onAccentChange(preset.argb)
                            }
                            .testTag("custom_color_preset_${preset.argb.toString(16)}")
                    ) {
                        if (isPicked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (swatchColor.luminance() > 0.45f) Color(0xFF07090D) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hue slider with live rainbow preview bar
            Text(
                text = "Matiz (${hsv[0].toInt()}°)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF0000),
                                Color(0xFFFFFF00),
                                Color(0xFF00FF00),
                                Color(0xFF00FFFF),
                                Color(0xFF0000FF),
                                Color(0xFFFF00FF),
                                Color(0xFFFF0000)
                            )
                        )
                    )
            )
            Slider(
                value = hsv[0],
                onValueChange = { newHue ->
                    val sat = hsv[1].coerceAtLeast(0.25f)
                    val value = hsv[2].coerceAtLeast(0.65f)
                    val newArgb = AndroidColor.HSVToColor(floatArrayOf(newHue, sat, value)).toLong() and 0xFFFFFFFFL
                    onAccentChange(newArgb)
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor
                ),
                modifier = Modifier.testTag("custom_color_hue_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saturação (${(hsv[1] * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hsv[1],
                        onValueChange = { newSat ->
                            val newArgb = AndroidColor.HSVToColor(
                                floatArrayOf(hsv[0], newSat, hsv[2].coerceAtLeast(0.4f))
                            ).toLong() and 0xFFFFFFFFL
                            onAccentChange(newArgb)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeColor,
                            activeTrackColor = activeColor
                        ),
                        modifier = Modifier.testTag("custom_color_saturation_slider")
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Brilho (${(hsv[2] * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hsv[2],
                        onValueChange = { newVal ->
                            val newArgb = AndroidColor.HSVToColor(
                                floatArrayOf(hsv[0], hsv[1], newVal)
                            ).toLong() and 0xFFFFFFFFL
                            onAccentChange(newArgb)
                        },
                        valueRange = 0.45f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeColor,
                            activeTrackColor = activeColor
                        ),
                        modifier = Modifier.testTag("custom_color_brightness_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeMiniSwatch(
    mode: AppThemeMode,
    customAccentColor: Long
) {
    val customAccent = Color(customAccentColor.toInt())
    val bgColor = when (mode) {
        AppThemeMode.TRON -> TronAbyss
        AppThemeMode.OBSIDIAN -> ObsidianVoid
        AppThemeMode.DAYLIGHT -> DaylightCanvas
        AppThemeMode.CUSTOM -> Color(0xFF07090D)
    }
    val primaryAccent = when (mode) {
        AppThemeMode.TRON -> TronNeonCyan
        AppThemeMode.OBSIDIAN -> ObsidianAmethyst
        AppThemeMode.DAYLIGHT -> DaylightCyan
        AppThemeMode.CUSTOM -> customAccent
    }
    val secondaryAccent = when (mode) {
        AppThemeMode.TRON -> TronCluOrange
        AppThemeMode.OBSIDIAN -> ObsidianGold
        AppThemeMode.DAYLIGHT -> DaylightAmber
        AppThemeMode.CUSTOM -> customAccent.copy(alpha = 0.55f)
    }
    val frameColor = when (mode) {
        AppThemeMode.TRON -> TronOutline
        AppThemeMode.OBSIDIAN -> ObsidianBorder
        AppThemeMode.DAYLIGHT -> DaylightBorder
        AppThemeMode.CUSTOM -> customAccent.copy(alpha = 0.7f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, frameColor),
        modifier = Modifier.size(46.dp)
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            when (mode) {
                AppThemeMode.TRON -> {
                    drawLine(
                        color = primaryAccent.copy(alpha = 0.22f),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = primaryAccent.copy(alpha = 0.22f),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        strokeWidth = 1f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryAccent.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.46f
                        ),
                        radius = size.minDimension * 0.46f,
                        center = center
                    )
                    drawCircle(
                        color = primaryAccent,
                        radius = size.minDimension * 0.34f,
                        center = center,
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                    drawCircle(
                        color = secondaryAccent,
                        radius = size.minDimension * 0.14f,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                AppThemeMode.OBSIDIAN -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryAccent.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width * 0.68f, size.height * 0.34f),
                            radius = size.minDimension * 0.48f
                        )
                    )
                    drawCircle(
                        color = primaryAccent,
                        radius = size.minDimension * 0.22f,
                        center = Offset(size.width * 0.40f, size.height * 0.46f)
                    )
                    drawCircle(
                        color = secondaryAccent,
                        radius = size.minDimension * 0.14f,
                        center = Offset(size.width * 0.66f, size.height * 0.64f)
                    )
                }

                AppThemeMode.CUSTOM -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryAccent.copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.48f
                        ),
                        radius = size.minDimension * 0.48f,
                        center = center
                    )
                    drawCircle(
                        color = primaryAccent,
                        radius = size.minDimension * 0.28f,
                        center = center
                    )
                }

                AppThemeMode.DAYLIGHT -> {
                    drawCircle(
                        color = primaryAccent,
                        radius = size.minDimension * 0.25f,
                        center = Offset(size.width * 0.42f, size.height * 0.46f)
                    )
                    drawCircle(
                        color = secondaryAccent,
                        radius = size.minDimension * 0.14f,
                        center = Offset(size.width * 0.68f, size.height * 0.64f)
                    )
                }
            }
        }
    }
}
