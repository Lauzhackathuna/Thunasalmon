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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { System, Light, Dark }

internal val FinLifeContentWidth = 480.dp

internal object FinLifeSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val xLarge = 32.dp
    val touchTarget = 48.dp
}

/** Quiet Motion: warm paper, slate ink, restrained glacier blue and a soft copper accent. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF087E6D),
    onPrimary = Color.White,
    secondary = Color(0xFFD47A5A),
    onSecondary = Color.White,
    tertiary = Color(0xFF447E83),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF1A2928),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2928),
    surfaceVariant = Color(0xFFF0F5F3),
    onSurfaceVariant = Color(0xFF5B6D68),
    outline = Color(0xFF81958D),
    outlineVariant = Color(0xFFE1EBE6),
    primaryContainer = Color(0xFFDDF3E9),
    onPrimaryContainer = Color(0xFF084A41),
    secondaryContainer = Color(0xFFF0E8DF),
    onSecondaryContainer = Color(0xFF654D3A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5AE3B3),
    onPrimary = Color(0xFF08261F),
    secondary = Color(0xFFF1A58E),
    onSecondary = Color(0xFF35251D),
    tertiary = Color(0xFF8DCBCB),
    background = Color(0xFF0D1217),
    onBackground = Color(0xFFF2F7F4),
    surface = Color(0xFF182128),
    onSurface = Color(0xFFF2F7F4),
    surfaceVariant = Color(0xFF203039),
    onSurfaceVariant = Color(0xFFA8BDB6),
    outline = Color(0xFF779388),
    outlineVariant = Color(0xFF30433D),
    primaryContainer = Color(0xFF164C3E),
    onPrimaryContainer = Color(0xFFD6F8E7),
    secondaryContainer = Color(0xFF493E3B),
    onSecondaryContainer = Color(0xFFF1D9C5),
)

internal data class DeltaColors(val gain: Color, val loss: Color, val neutral: Color) {
    fun colorFor(direction: DeltaDirection): Color = when (direction) {
        DeltaDirection.Up -> gain
        DeltaDirection.Down -> loss
        DeltaDirection.Unchanged -> neutral
    }
}

private val LightDeltaColors = DeltaColors(Color(0xFF087E6D), Color(0xFFC34A56), LightColors.onSurfaceVariant)
private val DarkDeltaColors = DeltaColors(Color(0xFF5AE3B3), Color(0xFFFF9EA5), DarkColors.onSurfaceVariant)
internal val LocalDeltaColors = staticCompositionLocalOf { LightDeltaColors }
private val LightBackdrop = Brush.verticalGradient(listOf(Color(0xFFF9FCFA), LightColors.background))
private val DarkBackdrop = Brush.verticalGradient(listOf(Color(0xFF111C20), DarkColors.background, Color(0xFF090E12)))
internal val LocalGameBackdrop = staticCompositionLocalOf { LightBackdrop }
/** Background of outlined buttons: white in light mode, the dark surface in dark mode. */
internal val LocalButtonContainer = staticCompositionLocalOf { Color.White }

private val FinLifeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

private val FinLifeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 43.sp,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
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
    CompositionLocalProvider(
        LocalDeltaColors provides if (dark) DarkDeltaColors else LightDeltaColors,
        LocalGameBackdrop provides if (dark) DarkBackdrop else LightBackdrop,
        LocalButtonContainer provides if (dark) DarkColors.surface else Color.White,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = FinLifeShapes,
            typography = FinLifeTypography,
            content = content,
        )
    }
}
