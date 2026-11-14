package com.mindtrace.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Sage = Color(0xFF6B8F7B)
val SageDark = Color(0xFFA9C9B4)
val Clay = Color(0xFFB8835E)
val ClayDark = Color(0xFFE2BE9F)
val DustyBlue = Color(0xFF7389A6)
val DustyBlueDark = Color(0xFFAFC1DA)
val WarmPaper = Color(0xFFF6F1E8)
val WarmSurface = Color(0xFFFFFCF7)
val Ink = Color(0xFF26302A)
val NightBackground = Color(0xFF14110F)
val NightSurface = Color(0xFF1E1A17)

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDEADF),
    onPrimaryContainer = Color(0xFF1C2B22),
    inversePrimary = Color(0xFFB9D8C1),
    secondary = Clay,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E2D4),
    onSecondaryContainer = Color(0xFF3D2A1D),
    tertiary = DustyBlue,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE6F1),
    onTertiaryContainer = Color(0xFF1D2938),
    background = WarmPaper,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E1D5),
    onSurfaceVariant = Color(0xFF4F5751),
    outline = Color(0xFF7C847D),
    outlineVariant = Color(0xFFD9D1C5),
    surfaceDim = Color(0xFFE4DDD2),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7F1),
    surfaceContainer = Color(0xFFF4EEE5),
    surfaceContainerHigh = Color(0xFFEDE6DB),
    surfaceContainerHighest = Color(0xFFE6DED2),
    inverseSurface = Color(0xFF2E3530),
    inverseOnSurface = Color(0xFFF3F1EA),
    scrim = Color(0xFF000000),
    error = Color(0xFFA9443E),
    errorContainer = Color(0xFFF7DDDA),
    onErrorContainer = Color(0xFF4B1512)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6CDBA),
    onPrimary = Color(0xFF1E3023),
    primaryContainer = Color(0xFF33483A),
    onPrimaryContainer = Color(0xFFDCE9DE),
    inversePrimary = Color(0xFF526B58),
    secondary = Color(0xFFE3C0A0),
    onSecondary = Color(0xFF3E2C1F),
    secondaryContainer = Color(0xFF55402E),
    onSecondaryContainer = Color(0xFFF5E7D8),
    tertiary = Color(0xFFB3C4D9),
    onTertiary = Color(0xFF1D2C3D),
    tertiaryContainer = Color(0xFF35485C),
    onTertiaryContainer = Color(0xFFE0E9F4),
    background = NightBackground,
    onBackground = Color(0xFFF0EAE1),
    surface = NightSurface,
    onSurface = Color(0xFFF0EAE1),
    surfaceVariant = Color(0xFF2A2521),
    onSurfaceVariant = Color(0xFFC7BEB2),
    outline = Color(0xFF93897D),
    outlineVariant = Color(0xFF3E3832),
    surfaceDim = Color(0xFF14110F),
    surfaceBright = Color(0xFF3B342E),
    surfaceContainerLowest = Color(0xFF0E0C0B),
    surfaceContainerLow = Color(0xFF181513),
    surfaceContainer = Color(0xFF201C19),
    surfaceContainerHigh = Color(0xFF2A2521),
    surfaceContainerHighest = Color(0xFF342E29),
    inverseSurface = Color(0xFFF0EAE1),
    inverseOnSurface = Color(0xFF23201D),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF5F1A17),
    errorContainer = Color(0xFF5B2320),
    onErrorContainer = Color(0xFFF7DDDA)
)

/** 主题色系：只替换主色/辅色，背景与中性色保持暖纸/夜间基调。 */
private data class AccentPalette(
    val lightPrimary: Long, val lightPrimaryContainer: Long, val lightOnPrimaryContainer: Long,
    val lightSecondary: Long, val lightSecondaryContainer: Long,
    val lightTertiary: Long, val lightTertiaryContainer: Long,
    val darkPrimary: Long, val darkPrimaryContainer: Long, val darkOnPrimaryContainer: Long,
    val darkSecondary: Long, val darkSecondaryContainer: Long,
    val darkTertiary: Long, val darkTertiaryContainer: Long,
    val lightBackground: Long = 0L, val lightSurface: Long = 0L,
    val darkBackground: Long = 0L, val darkSurface: Long = 0L
)

private fun Long.color() = Color(this)

private val PaletteSage = AccentPalette(
    0xFF6B8F7B, 0xFFDDEADF, 0xFF1C2B22, 0xFFB8835E, 0xFFF3E2D4, 0xFF7389A6, 0xFFDEE6F1,
    0xFFB6CDBA, 0xFF33483A, 0xFFDCE9DE, 0xFFE3C0A0, 0xFF55402E, 0xFFB3C4D9, 0xFF35485C,
    0xFFF6F1E8, 0xFFFFFCF7, 0xFF14110F, 0xFF1E1A17
)
private val PaletteOcean = AccentPalette(
    0xFF4F7FA8, 0xFFD7E6F4, 0xFF102A3E, 0xFF5E8A8C, 0xFFD8E9EA, 0xFF7A82B0, 0xFFE1E2F4,
    0xFFA9C8E6, 0xFF2C4A63, 0xFFD7E8F8, 0xFFB7D3D4, 0xFF355556, 0xFFC3C7E8, 0xFF3F4468,
    0xFFEEF3F8, 0xFFF7FBFE, 0xFF0D141B, 0xFF16212B
)
private val PalettePlum = AccentPalette(
    0xFF8A5F9E, 0xFFEEDFF5, 0xFF301C3A, 0xFFA86B85, 0xFFF5E0E8, 0xFF8E7DA8, 0xFFE7E0F0,
    0xFFD5B8E5, 0xFF4C3560, 0xFFEFE1F8, 0xFFE3BDCD, 0xFF583846, 0xFFC9BEE0, 0xFF473C5C,
    0xFFF7F0FA, 0xFFFDF8FF, 0xFF150F1B, 0xFF201827
)
private val PaletteAmber = AccentPalette(
    0xFF9A6B2F, 0xFFF6E6CA, 0xFF3A2810, 0xFFA9703F, 0xFFF3E2D4, 0xFF8A8250, 0xFFEFEAD3,
    0xFFE8C48A, 0xFF5C4323, 0xFFF8E6C8, 0xFFDDB58A, 0xFF5A4028, 0xFFD4CC9A, 0xFF4E4A2C,
    0xFFF8F0E2, 0xFFFFFAF0, 0xFF161008, 0xFF221A0F
)
private val PaletteRose = AccentPalette(
    0xFFA85A6E, 0xFFF7DDE4, 0xFF3D1620, 0xFFB07A5E, 0xFFF4E2D7, 0xFF97709A, 0xFFEEDFEF,
    0xFFE8B4C1, 0xFF63323E, 0xFFFBE2E8, 0xFFE0BBA2, 0xFF5A3D2D, 0xFFD6B6D8, 0xFF513A52,
    0xFFFAF0F2, 0xFFFFF8FA, 0xFF170F12, 0xFF241A1E
)

private fun paletteFor(name: String): AccentPalette? = when (name) {
    "ocean" -> PaletteOcean
    "plum" -> PalettePlum
    "amber" -> PaletteAmber
    "rose" -> PaletteRose
    "sage" -> PaletteSage
    else -> null
}

private fun applyPalette(base: ColorScheme, palette: AccentPalette, dark: Boolean): ColorScheme =
    if (dark) base.copy(
        primary = palette.darkPrimary.color(),
        primaryContainer = palette.darkPrimaryContainer.color(),
        onPrimaryContainer = palette.darkOnPrimaryContainer.color(),
        secondary = palette.darkSecondary.color(),
        secondaryContainer = palette.darkSecondaryContainer.color(),
        tertiary = palette.darkTertiary.color(),
        tertiaryContainer = palette.darkTertiaryContainer.color(),
        background = if (palette.darkBackground != 0L) palette.darkBackground.color() else base.background,
        surface = if (palette.darkSurface != 0L) palette.darkSurface.color() else base.surface
    ) else base.copy(
        primary = palette.lightPrimary.color(),
        primaryContainer = palette.lightPrimaryContainer.color(),
        onPrimaryContainer = palette.lightOnPrimaryContainer.color(),
        secondary = palette.lightSecondary.color(),
        secondaryContainer = palette.lightSecondaryContainer.color(),
        tertiary = palette.lightTertiary.color(),
        tertiaryContainer = palette.lightTertiaryContainer.color(),
        background = if (palette.lightBackground != 0L) palette.lightBackground.color() else base.background,
        surface = if (palette.lightSurface != 0L) palette.lightSurface.color() else base.surface
    )

private val MindTraceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
)

private val MindTraceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MindTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: String = "sage",
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColors else LightColors
    val palette = paletteFor(colorTheme)
    val colors = if (palette == null) base else applyPalette(base, palette, darkTheme)
    MaterialTheme(
        colorScheme = colors,
        typography = MindTraceTypography,
        shapes = MindTraceShapes
    ) {
        // 全局渐变背景：让半透明毛玻璃卡片有可透出的层次与光影。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colors.background, colors.surfaceContainerLow)
                    )
                )
        ) {
            // 统一默认文字颜色。部分自定义 Box 卡片没有 Surface 的 contentColor，
            // 不显式设色时会退回系统默认黑色；在深色模式下会出现黑字。
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                content = content
            )
        }
    }
}

val MoodColors = listOf(
    Color(0xFF8D9AA8),
    Color(0xFF9AA7B0),
    Color(0xFFB3A28A),
    Color(0xFF8FAF94),
    Color(0xFF6B9A7B)
)

val MoodLabels = listOf("很低落", "有点低", "一般", "还不错", "很好")
