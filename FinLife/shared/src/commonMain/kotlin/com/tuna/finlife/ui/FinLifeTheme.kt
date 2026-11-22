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
    primary = Color(0xFF315C80),
    onPrimary = Color.White,
    secondary = Color(0xFF8E735D),
    onSecondary = Color.White,
    tertiary = Color(0xFF607C7A),
    background = Color(0xFFF6F6F3),
    onBackground = Color(0xFF202B36),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202B36),
    surfaceVariant = Color(0xFFF0F3F5),
    onSurfaceVariant = Color(0xFF566371),
    outline = Color(0xFF7D8B98),
    outlineVariant = Color(0xFFE1E6EA),
    primaryContainer = Color(0xFFDCEBF5),
    onPrimaryContainer = Color(0xFF203D54),
    secondaryContainer = Color(0xFFF0E8DF),
    onSecondaryContainer = Color(0xFF654D3A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D2F1),
    onPrimary = Color(0xFF132C40),
    secondary = Color(0xFFD4B196),
    onSecondary = Color(0xFF372B22),
    tertiary = Color(0xFF9CCAC3),
    background = Color(0xFF171B23),
    onBackground = Color(0xFFF2F4F5),
    surface = Color(0xFF222936),
    onSurface = Color(0xFFF2F4F5),
    surfaceVariant = Color(0xFF2A3543),
    onSurfaceVariant = Color(0xFFC0CDD9),
    outline = Color(0xFF8EA0B0),
    outlineVariant = Color(0xFF3A4655),
    primaryContainer = Color(0xFF25445E),
    onPrimaryContainer = Color(0xFFE1F2FF),
    secondaryContainer = Color(0xFF493F38),
    onSecondaryContainer = Color(0xFFF1D9C5),
)

internal data class DeltaColors(val gain: Color, val loss: Color, val neutral: Color) {
    fun colorFor(direction: DeltaDirection): Color = when (direction) {
        DeltaDirection.Up -> gain
        DeltaDirection.Down -> loss
        DeltaDirection.Unchanged -> neutral
    }
}

private val LightDeltaColors = DeltaColors(Color(0xFF277453), Color(0xFFAA4550), LightColors.onSurfaceVariant)
private val DarkDeltaColors = DeltaColors(Color(0xFF9DE0BC), Color(0xFFFFB1B5), DarkColors.onSurfaceVariant)
internal val LocalDeltaColors = staticCompositionLocalOf { LightDeltaColors }
private val LightBackdrop = Brush.verticalGradient(listOf(Color(0xFFFBFAF8), LightColors.background))
private val DarkBackdrop = Brush.verticalGradient(listOf(Color(0xFF242D3B), DarkColors.background, Color(0xFF151921)))
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
