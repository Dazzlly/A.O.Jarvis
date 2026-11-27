package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.AppThemeMode
import com.example.data.DEFAULT_CUSTOM_ACCENT_COLOR

val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.TRON }

private val TronLegacyColorScheme = darkColorScheme(
    primary = TronNeonCyan,
    onPrimary = OnTronNeonCyan,
    primaryContainer = TronNeonCyanContainer,
    onPrimaryContainer = TronNeonCyanBright,
    secondary = TronCluOrange,
    onSecondary = OnTronCluOrange,
    secondaryContainer = TronCluOrangeContainer,
    onSecondaryContainer = TronCluOrangeBright,
    tertiary = TronGridMint,
    onTertiary = OnTronGridMint,
    tertiaryContainer = TronGridMintContainer,
    onTertiaryContainer = TronGridMintBright,
    error = TronRecognizerRed,
    onError = Color.White,
    errorContainer = TronRecognizerRedContainer,
    onErrorContainer = TronRecognizerRedBright,
    background = TronAbyss,
    onBackground = TronInkPrimary,
    surface = TronPanel,
    onSurface = TronInkPrimary,
    surfaceVariant = TronPanelHigh,
    onSurfaceVariant = TronInkSecondary,
    surfaceContainerLowest = TronAbyss,
    surfaceContainerLow = TronPanel,
    surfaceContainer = TronPanelHigh,
    surfaceContainerHigh = TronPanelHighest,
    outline = TronOutline,
    outlineVariant = TronOutlineSubtle
)

private val ObsidianProColorScheme = darkColorScheme(
    primary = ObsidianAmethyst,
    onPrimary = OnObsidianAmethyst,
    primaryContainer = ObsidianAmethystContainer,
    onPrimaryContainer = ObsidianAmethystBright,
    secondary = ObsidianGold,
    onSecondary = OnObsidianGold,
    secondaryContainer = ObsidianGoldContainer,
    onSecondaryContainer = ObsidianGoldBright,
    tertiary = ObsidianEmerald,
    onTertiary = OnObsidianEmerald,
    tertiaryContainer = ObsidianEmeraldContainer,
    onTertiaryContainer = ObsidianEmeraldBright,
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
    outlineVariant = ObsidianBorderSubtle
)

private val DaylightColorScheme = lightColorScheme(
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

/**
 * Tema Custom: base escura fixa de alto contraste, personalizando exclusivamente
 * a cor de destaque (primary, containers, bordas e brilho ambiente) a partir da cor escolhida.
 */
fun buildCustomColorScheme(accentArgb: Long): ColorScheme {
    val accent = Color(accentArgb.toInt())
    val baseVoid = Color(0xFF07090D)
    val baseSurface = Color(0xFF10131A)
    val baseSurfaceHigh = Color(0xFF171B24)
    val baseSurfaceHighest = Color(0xFF202532)
    val onAccent = if (accent.luminance() > 0.42f) Color(0xFF060A10) else Color.White
    val accentContainer = lerp(baseSurface, accent, 0.24f)
    val onAccentContainer = lerp(Color.White, accent, 0.30f)
    val secondaryAccent = lerp(accent, Color.White, 0.18f)
    val secondaryContainer = lerp(baseSurface, accent, 0.16f)
    val outline = lerp(Color(0xFF2C3240), accent, 0.45f)
    val outlineVariant = lerp(Color(0xFF1C202B), accent, 0.18f)

    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = secondaryAccent,
        onSecondary = onAccent,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onAccentContainer,
        tertiary = ObsidianEmerald,
        onTertiary = OnObsidianEmerald,
        tertiaryContainer = ObsidianEmeraldContainer,
        onTertiaryContainer = ObsidianEmeraldBright,
        error = PulseCrimson,
        onError = Color.White,
        errorContainer = PulseCrimsonContainer,
        onErrorContainer = Color(0xFFFECDD3),
        background = baseVoid,
        onBackground = InkLightPrimary,
        surface = baseSurface,
        onSurface = InkLightPrimary,
        surfaceVariant = baseSurfaceHigh,
        onSurfaceVariant = InkLightSecondary,
        surfaceContainerLowest = baseVoid,
        surfaceContainerLow = baseSurface,
        surfaceContainer = baseSurfaceHigh,
        surfaceContainerHigh = baseSurfaceHighest,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

// Precision angular geometry for Tron Legacy
val TronShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// Sculpted obsidian-glass geometry for Obsidian Pro, Daylight & Custom
val OpenJarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun OpenJarvisTheme(
    themeMode: AppThemeMode = AppThemeMode.TRON,
    customAccentArgb: Long = DEFAULT_CUSTOM_ACCENT_COLOR,
    content: @Composable () -> Unit
) {
    val customColorScheme = remember(customAccentArgb) {
        buildCustomColorScheme(customAccentArgb)
    }
    val colorScheme = when (themeMode) {
        AppThemeMode.TRON -> TronLegacyColorScheme
        AppThemeMode.OBSIDIAN -> ObsidianProColorScheme
        AppThemeMode.DAYLIGHT -> DaylightColorScheme
        AppThemeMode.CUSTOM -> customColorScheme
    }
    val shapes = when (themeMode) {
        AppThemeMode.TRON -> TronShapes
        AppThemeMode.OBSIDIAN, AppThemeMode.DAYLIGHT, AppThemeMode.CUSTOM -> OpenJarvisShapes
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            val useLightSystemBars = !themeMode.isDark
            insetsController.isAppearanceLightStatusBars = useLightSystemBars
            insetsController.isAppearanceLightNavigationBars = useLightSystemBars
        }
    }

    CompositionLocalProvider(
        LocalAppThemeMode provides themeMode,
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OpenJarvisTypography,
            shapes = shapes
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}

/**
 * Atmospheric backdrop layered behind the app Scaffold:
 * - TRON: Deep abyss with electric cyan radial horizon bloom and subtle precision Tron Grid lines.
 * - OBSIDIAN: Volcanic OLED canvas with understated amethyst-and-gold glass sheen.
 * - DAYLIGHT: Clean luminous daylight wash.
 * - CUSTOM: Clean dark canvas with ambient radial aura tinted exclusively by the user's chosen color.
 */
@Composable
fun JarvisThemeBackdrop(
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val baseBg = MaterialTheme.colorScheme.background
    val primaryGlow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val maxDim = size.maxDimension.coerceAtLeast(1f)
            when (themeMode) {
                AppThemeMode.TRON -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.14f),
                                primaryGlow.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.06f),
                            radius = (maxDim * 0.72f).coerceAtLeast(1f)
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryGlow.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.92f, size.height * 0.88f),
                            radius = (maxDim * 0.45f).coerceAtLeast(1f)
                        )
                    )
                    val gridStep = 44.dp.toPx().coerceAtLeast(8f)
                    val lineColor = primaryGlow.copy(alpha = 0.055f)
                    val accentNodeColor = primaryGlow.copy(alpha = 0.12f)
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = lineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += gridStep
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += gridStep
                    }
                    var gx = gridStep
                    while (gx < size.width) {
                        var gy = gridStep
                        while (gy < size.height * 0.45f) {
                            drawCircle(
                                color = accentNodeColor,
                                radius = 1.6f,
                                center = Offset(gx, gy)
                            )
                            gy += gridStep * 2
                        }
                        gx += gridStep * 2
                    }
                }

                AppThemeMode.OBSIDIAN -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.11f),
                                primaryGlow.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.08f),
                            radius = (maxDim * 0.65f).coerceAtLeast(1f)
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryGlow.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.86f),
                            radius = (maxDim * 0.52f).coerceAtLeast(1f)
                        )
                    )
                }

                AppThemeMode.CUSTOM -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.13f),
                                primaryGlow.copy(alpha = 0.035f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.07f),
                            radius = (maxDim * 0.70f).coerceAtLeast(1f)
                        )
                    )
                }

                AppThemeMode.DAYLIGHT -> {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = (size.height * 0.35f).coerceAtLeast(1f)
                        )
                    )
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
