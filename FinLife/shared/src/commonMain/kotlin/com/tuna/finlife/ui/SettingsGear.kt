package com.tuna.finlife.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/** Eight-tooth gear with a round hole, drawn after the reference glyph. Tinted by the caller. */
internal val SettingsGear: ImageVector by lazy {
    val center = 12f
    val outer = 10.5f
    val root = 8f
    val hole = 3.4f
    val teeth = 8
    fun PathBuilder.to(radius: Float, degrees: Double, move: Boolean = false) {
        val angle = degrees * PI / 180.0
        val x = center + radius * cos(angle).toFloat()
        val y = center + radius * sin(angle).toFloat()
        if (move) moveTo(x, y) else lineTo(x, y)
    }
    ImageVector.Builder("SettingsGear", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            val step = 360.0 / teeth
            for (i in 0 until teeth) {
                val mid = i * step - 90.0
                // Tooth flanks: narrower at the tip than at the root.
                to(root, mid - 13.0, move = i == 0)
                to(outer, mid - 9.5)
                to(outer, mid + 9.5)
                to(root, mid + 13.0)
                // Root between teeth, as a few short segments.
                for (k in 1..3) to(root, mid + 13.0 + k * (step - 26.0) / 4)
            }
            close()
            // Hole: a polygon fine enough to read as a circle at icon sizes.
            for (k in 0..36) to(hole, k * 10.0, move = k == 0)
            close()
        }
    }.build()
}

/** Settings button: a blue gear with no outline or background. */
@Composable
internal fun SettingsGearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(FinLifeSpacing.touchTarget),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(SettingsGear, contentDescription = stringResource(Res.string.settings), modifier = Modifier.size(28.dp))
    }
}

@Preview
@Composable
private fun SettingsGearButtonPreview() {
    FinLifeTheme(ThemeMode.Light) { SettingsGearButton(onClick = {}) }
}
