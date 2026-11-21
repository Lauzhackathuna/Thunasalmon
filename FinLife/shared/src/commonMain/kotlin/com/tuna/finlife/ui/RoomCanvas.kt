package com.tuna.finlife.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.room_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomCanvas() {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(Res.string.room_description)
    Canvas(
        modifier = Modifier.fillMaxWidth()
            .aspectRatio(400f / 204f)
            .clip(MaterialTheme.shapes.large)
            .semantics { contentDescription = description },
    ) {
        scale(size.width / 400f, size.height / 204f, pivot = Offset.Zero) {
            drawRect(colors.surface, size = Size(400f, 204f))
            drawRect(colors.onSurface.copy(alpha = 0.07f), Offset(0f, 130f), Size(400f, 74f))
            drawLine(colors.outlineVariant, Offset(0f, 130f), Offset(400f, 130f), strokeWidth = 2f)

            val frame = Offset(36f, 24f)
            val frameSize = Size(80f, 70f)
            drawRoundRect(colors.secondaryContainer, frame, frameSize, CornerRadius(6f))
            drawRoundRect(colors.outline, frame, frameSize, CornerRadius(6f), style = Stroke(2f))
            drawLine(colors.outline, Offset(76f, 25f), Offset(76f, 93f), strokeWidth = 2f)
            drawLine(colors.outline, Offset(37f, 59f), Offset(115f, 59f), strokeWidth = 2f)

            drawOval(colors.onSurface.copy(alpha = 0.12f), Offset(171f, 138f), Size(62f, 12f))
            drawRoundRect(colors.primary, Offset(174f, 77f), Size(56f, 66f), CornerRadius(26f))
            drawCircle(colors.onPrimary, radius = 2.8f, center = Offset(192f, 103f))
            drawCircle(colors.onPrimary, radius = 2.8f, center = Offset(212f, 103f))
            drawArc(
                color = colors.onPrimary,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(197f, 108f),
                size = Size(10f, 8f),
                style = Stroke(1.8f),
            )

            val slotStroke = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            for (x in listOf(26f, 151f, 276f)) {
                drawRoundRect(
                    colors.outline,
                    topLeft = Offset(x, 159f),
                    size = Size(98f, 29f),
                    cornerRadius = CornerRadius(8f),
                    style = slotStroke,
                )
            }
        }
    }
}
