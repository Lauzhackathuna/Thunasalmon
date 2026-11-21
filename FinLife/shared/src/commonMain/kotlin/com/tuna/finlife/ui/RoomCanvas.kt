package com.tuna.finlife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tuna.finlife.game.CharacterMood
import com.tuna.finlife.game.ItemCondition
import com.tuna.finlife.game.RoomItem
import com.tuna.finlife.game.RoomProgress
import com.tuna.finlife.game.RoomSlot
import com.tuna.finlife.game.RoomTier
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.furnished_room_description
import finlife.shared.generated.resources.room_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomCanvas(room: RoomProgress, mood: CharacterMood) {
    val colors = MaterialTheme.colorScheme
    val tier = stringResource(room.tier.label)
    val description = if (room.displayed.isEmpty()) {
        stringResource(Res.string.room_description, tier)
    } else {
        val names = RoomSlot.entries.mapNotNull { room.displayed[it] }.map { stringResource(it.label) }
        stringResource(Res.string.furnished_room_description, tier, names.joinToString())
    }
    val smile by animateFloatAsState(
        targetValue = when (mood) {
            CharacterMood.Thriving -> 9f
            CharacterMood.Okay -> 3f
            CharacterMood.Stressed -> -6f
        },
        label = "character expression",
    )
    val bodyColor by animateColorAsState(
        targetValue = if (mood == CharacterMood.Stressed) colors.outline else colors.primary,
        label = "character color",
    )
    val faceColor by animateColorAsState(
        targetValue = if (mood == CharacterMood.Stressed) colors.surface else colors.onPrimary,
        label = "character face color",
    )
    val frameWidth = when (room.tier) {
        RoomTier.SharedRoom -> 80f
        RoomTier.SmallStudio -> 112f
        RoomTier.Flat -> 144f
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
            val frameSize = Size(frameWidth, 70f)
            drawRoundRect(colors.secondaryContainer, frame, frameSize, CornerRadius(6f))
            drawRoundRect(colors.outline, frame, frameSize, CornerRadius(6f), style = Stroke(2f))
            val divider = frame.x + frameWidth / 2
            drawLine(colors.outline, Offset(divider, 25f), Offset(divider, 93f), strokeWidth = 2f)
            drawLine(colors.outline, Offset(37f, 59f), Offset(frame.x + frameWidth - 1, 59f), strokeWidth = 2f)

            drawOval(colors.onSurface.copy(alpha = 0.12f), Offset(171f, 138f), Size(62f, 12f))
            drawRoundRect(bodyColor, Offset(174f, 77f), Size(56f, 66f), CornerRadius(26f))
            drawCircle(faceColor, radius = 2.8f, center = Offset(192f, 103f))
            drawCircle(faceColor, radius = 2.8f, center = Offset(212f, 103f))
            val mouth = Path().apply {
                moveTo(196f, 113f)
                cubicTo(199f, 113f + smile, 205f, 113f + smile, 208f, 113f)
            }
            drawPath(mouth, faceColor, style = Stroke(1.8f))

            val slotStroke = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            for (slot in RoomSlot.entries) {
                val x = 26f + 125f * slot.ordinal
                val item = room.displayed[slot]
                if (item != null) {
                    drawFurniture(item, room.inventory.getValue(item), x + 49f, colors)
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

private fun DrawScope.drawFurniture(item: RoomItem, condition: ItemCondition, x: Float, colors: ColorScheme) {
    val opacity = if (condition == ItemCondition.Broken) 0.55f else 1f
    val tilt = if (condition == ItemCondition.Broken) 12f else 0f
    rotate(tilt, pivot = Offset(x, 175f)) {
        drawFurnitureShape(item, x, colors, opacity)
        if (condition != ItemCondition.Intact) {
            val crack = Path().apply {
                moveTo(x - 3f, 158f)
                lineTo(x + 3f, 166f)
                lineTo(x - 2f, 171f)
                lineTo(x + 4f, 179f)
            }
            drawPath(crack, colors.surface, style = Stroke(3.5f))
            drawPath(crack, colors.onSurface, style = Stroke(1.3f))
        }
    }
}

private fun DrawScope.drawFurnitureShape(item: RoomItem, x: Float, colors: ColorScheme, opacity: Float) {
    val primary = colors.primary.copy(alpha = opacity)
    val secondary = colors.secondaryContainer.copy(alpha = opacity)
    val support = colors.onSurfaceVariant.copy(alpha = opacity)
    when (item) {
        RoomItem.ReadingLamp -> {
            drawLine(support, Offset(x, 141f), Offset(x, 180f), strokeWidth = 4f)
            drawRoundRect(primary, Offset(x - 17f, 178f), Size(34f, 6f), CornerRadius(3f))
            drawRoundRect(primary, Offset(x - 18f, 123f), Size(36f, 22f), CornerRadius(6f))
            drawLine(secondary, Offset(x - 12f, 141f), Offset(x + 12f, 141f), strokeWidth = 2f)
        }
        RoomItem.Chair -> {
            drawLine(support, Offset(x - 17f, 169f), Offset(x - 20f, 184f), strokeWidth = 4f)
            drawLine(support, Offset(x + 17f, 169f), Offset(x + 20f, 184f), strokeWidth = 4f)
            drawRoundRect(primary, Offset(x - 18f, 145f), Size(36f, 22f), CornerRadius(5f))
            drawRoundRect(secondary, Offset(x - 22f, 163f), Size(44f, 10f), CornerRadius(4f))
        }
        RoomItem.Plant -> {
            drawLine(support, Offset(x, 145f), Offset(x, 166f), strokeWidth = 3f)
            drawOval(primary, Offset(x - 18f, 141f), Size(20f, 12f))
            drawOval(primary, Offset(x - 1f, 132f), Size(19f, 13f))
            drawRoundRect(secondary, Offset(x - 14f, 162f), Size(28f, 22f), CornerRadius(5f))
            drawRoundRect(support, Offset(x - 14f, 162f), Size(28f, 22f), CornerRadius(5f), style = Stroke(1.5f))
        }
        RoomItem.TableLamp -> {
            drawLine(support, Offset(x, 159f), Offset(x, 181f), strokeWidth = 4f)
            drawRoundRect(primary, Offset(x - 14f, 178f), Size(28f, 6f), CornerRadius(3f))
            val shade = Path().apply {
                moveTo(x - 10f, 140f)
                lineTo(x + 10f, 140f)
                lineTo(x + 18f, 160f)
                lineTo(x - 18f, 160f)
                close()
            }
            drawPath(shade, secondary)
            drawPath(shade, support, style = Stroke(1.5f))
        }
        RoomItem.Armchair -> {
            drawLine(support, Offset(x - 18f, 174f), Offset(x - 20f, 184f), strokeWidth = 4f)
            drawLine(support, Offset(x + 18f, 174f), Offset(x + 20f, 184f), strokeWidth = 4f)
            drawRoundRect(primary, Offset(x - 20f, 143f), Size(40f, 34f), CornerRadius(9f))
            drawRoundRect(secondary, Offset(x - 23f, 160f), Size(46f, 16f), CornerRadius(5f))
            drawRoundRect(primary, Offset(x - 28f, 155f), Size(11f, 22f), CornerRadius(5f))
            drawRoundRect(primary, Offset(x + 17f, 155f), Size(11f, 22f), CornerRadius(5f))
        }
    }
}
