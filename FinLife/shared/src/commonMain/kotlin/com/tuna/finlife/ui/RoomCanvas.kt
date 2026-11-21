package com.tuna.finlife.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tuna.finlife.game.RoomItem
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.furnished_room_description
import finlife.shared.generated.resources.room_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomCanvas(ownedItems: Set<RoomItem>) {
    val colors = MaterialTheme.colorScheme
    val description = if (ownedItems.isEmpty()) {
        stringResource(Res.string.room_description)
    } else {
        val names = RoomItem.entries.filter { it in ownedItems }.map { stringResource(it.label) }
        stringResource(Res.string.furnished_room_description, names.joinToString())
    }
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
            for (item in RoomItem.entries) {
                val x = 26f + 125f * item.slot.ordinal
                if (item in ownedItems) {
                    drawFurniture(item, x + 49f, colors)
                } else {
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
}

private fun DrawScope.drawFurniture(item: RoomItem, x: Float, colors: ColorScheme) {
    when (item) {
        RoomItem.ReadingLamp -> {
            drawLine(colors.onSurfaceVariant, Offset(x, 141f), Offset(x, 180f), strokeWidth = 4f)
            drawRoundRect(colors.primary, Offset(x - 17f, 178f), Size(34f, 6f), CornerRadius(3f))
            drawRoundRect(colors.primary, Offset(x - 18f, 123f), Size(36f, 22f), CornerRadius(6f))
            drawLine(colors.secondaryContainer, Offset(x - 12f, 141f), Offset(x + 12f, 141f), strokeWidth = 2f)
        }
        RoomItem.Chair -> {
            drawLine(colors.onSurfaceVariant, Offset(x - 17f, 169f), Offset(x - 20f, 184f), strokeWidth = 4f)
            drawLine(colors.onSurfaceVariant, Offset(x + 17f, 169f), Offset(x + 20f, 184f), strokeWidth = 4f)
            drawRoundRect(colors.primary, Offset(x - 18f, 145f), Size(36f, 22f), CornerRadius(5f))
            drawRoundRect(colors.secondaryContainer, Offset(x - 22f, 163f), Size(44f, 10f), CornerRadius(4f))
        }
        RoomItem.Plant -> {
            drawLine(colors.onSurfaceVariant, Offset(x, 145f), Offset(x, 166f), strokeWidth = 3f)
            drawOval(colors.primary, Offset(x - 18f, 141f), Size(20f, 12f))
            drawOval(colors.primary, Offset(x - 1f, 132f), Size(19f, 13f))
            drawRoundRect(colors.secondaryContainer, Offset(x - 14f, 162f), Size(28f, 22f), CornerRadius(5f))
            drawRoundRect(colors.outline, Offset(x - 14f, 162f), Size(28f, 22f), CornerRadius(5f), style = Stroke(1.5f))
        }
    }
}
