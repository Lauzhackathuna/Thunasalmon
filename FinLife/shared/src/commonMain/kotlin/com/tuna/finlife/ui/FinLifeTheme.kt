package com.tuna.finlife.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { System, Light, Dark }

internal val FinLifeContentWidth = 480.dp

internal object FinLifeSpacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val touchTarget = 48.dp
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF365D80),
    onPrimary = Color.White,
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF25282D),
    surface = Color(0xFFFFFDFA),
    onSurface = Color(0xFF25282D),
    onSurfaceVariant = Color(0xFF515862),
    outline = Color(0xFF737B84),
    outlineVariant = Color(0xFFD9DDE2),
    secondaryContainer = Color(0xFFDCEAF7),
    onSecondaryContainer = Color(0xFF223F58),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CEF0),
    onPrimary = Color(0xFF17354E),
    background = Color(0xFF1B1D21),
    onBackground = Color(0xFFF4F1EB),
    surface = Color(0xFF25282D),
    onSurface = Color(0xFFF4F1EB),
    onSurfaceVariant = Color(0xFFC5CCD5),
    outline = Color(0xFF9CA5B0),
    outlineVariant = Color(0xFF454C56),
    secondaryContainer = Color(0xFF294962),
    onSecondaryContainer = Color(0xFFDBEAF7),
)

internal data class DeltaColors(val gain: Color, val loss: Color, val neutral: Color) {
    fun colorFor(direction: DeltaDirection): Color = when (direction) {
        DeltaDirection.Up -> gain
        DeltaDirection.Down -> loss
        DeltaDirection.Unchanged -> neutral
    }
}

private val LightDeltaColors = DeltaColors(Color(0xFF236342), Color(0xFFA5343C), LightColors.onSurfaceVariant)
private val DarkDeltaColors = DeltaColors(Color(0xFF94D8AE), Color(0xFFFFABB1), DarkColors.onSurfaceVariant)
internal val LocalDeltaColors = staticCompositionLocalOf { LightDeltaColors }

private val FinLifeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

private val FinLifeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun FinLifeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    SystemBarAppearance(dark)
    CompositionLocalProvider(LocalDeltaColors provides if (dark) DarkDeltaColors else LightDeltaColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = FinLifeShapes,
            typography = FinLifeTypography,
            content = content,
        )
    }
}
