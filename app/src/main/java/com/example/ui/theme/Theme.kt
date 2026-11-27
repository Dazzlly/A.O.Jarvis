package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OpenJarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = OnArcCyan,
    primaryContainer = ArcCyanContainer,
    onPrimaryContainer = ArcCyanBright,
    secondary = TelemetryAmber,
    onSecondary = OnTelemetryAmber,
    secondaryContainer = TelemetryAmberContainer,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = SignalEmerald,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = SignalEmeraldContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = PulseCrimson,
    onError = Color.White,
    errorContainer = PulseCrimsonContainer,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianVoid,
    onBackground = InkLightPrimary,
    surface = ObsidianSurface,
    onSurface = InkLightPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = InkLightSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1D293B)
)

private val OpenJarvisLightColorScheme = lightColorScheme(
    primary = DaylightCyan,
    onPrimary = Color.White,
    primaryContainer = DaylightCyanContainer,
    onPrimaryContainer = Color(0xFF082F49),
    secondary = DaylightAmber,
    onSecondary = Color.White,
    secondaryContainer = DaylightAmberContainer,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = DaylightEmerald,
    onTertiary = Color.White,
    tertiaryContainer = DaylightEmeraldContainer,
    onTertiaryContainer = Color(0xFF022C22),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = DaylightCanvas,
    onBackground = InkDarkPrimary,
    surface = DaylightSurface,
    onSurface = InkDarkPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = InkDarkSecondary,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightCanvas,
    surfaceContainer = DaylightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFD7E5F2),
    outline = DaylightBorder,
    outlineVariant = Color(0xFFDCE6F0)
)

val OpenJarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OpenJarvisTheme(
    darkTheme: Boolean = true,
    followSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) OpenJarvisDarkColorScheme else OpenJarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenJarvisTypography,
        shapes = OpenJarvisShapes,
        content = content
    )
}
