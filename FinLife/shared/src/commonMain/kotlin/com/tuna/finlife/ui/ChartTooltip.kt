package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Pure placement of a chart tooltip inside a container (all values in px, container coordinates). */
internal object TooltipPlacement {
    /**
     * Right of the point if it fits, else left, else below the chart, else above it; always inside the
     * container. Beside the point it keeps at least [gap] px horizontally from [pointX]; above/below it
     * stays outside [anchor]. Callers pass a gap larger than half a data step, so the pointer (which is
     * within half a step of the snapped point) is never under the tooltip.
     */
    fun place(anchor: IntRect, pointX: Int, gap: Int, margin: Int, container: IntSize, tooltip: IntSize): IntOffset {
        val maxX = maxOf(0, container.width - tooltip.width)
        val maxY = maxOf(0, container.height - tooltip.height)
        val sideY = anchor.top.coerceIn(0, maxY)
        if (pointX + gap + tooltip.width <= container.width) return IntOffset(pointX + gap, sideY)
        if (pointX - gap - tooltip.width >= 0) return IntOffset(pointX - gap - tooltip.width, sideY)
        val x = (pointX - tooltip.width / 2).coerceIn(0, maxX)
        val below = anchor.bottom + margin
        if (below + tooltip.height <= container.height) return IntOffset(x, below)
        val above = anchor.top - margin - tooltip.height
        if (above >= 0) return IntOffset(x, above)
        return IntOffset(x, maxY)
    }
}

/** What a chart wants shown: [anchor] and [pointX] are in root coordinates. */
internal class TooltipRequest(
    val owner: Any,
    val anchor: IntRect,
    val pointX: Int,
    val gap: Int,
    val margin: Int,
    val content: @Composable () -> Unit,
)

internal class TooltipHostState {
    var request by mutableStateOf<TooltipRequest?>(null)
        private set

    fun show(request: TooltipRequest) { this.request = request }

    fun hide(owner: Any) { if (request?.owner === owner) request = null }
}

internal val LocalTooltipHost = staticCompositionLocalOf<TooltipHostState?> { null }

/**
 * Draws chart tooltips above the whole screen, in the same layer as the content. The overlay has no
 * pointer input, so mouse and touch events always reach the chart underneath, even over the tooltip:
 * the tooltip can never hide itself or steal the hover. Nothing clips it.
 */
@Composable
internal fun TooltipHost(content: @Composable () -> Unit) {
    val state = remember { TooltipHostState() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalTooltipHost provides state) { content() }
        val request = state.request
        if (request != null) {
            Layout(content = request.content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = width, maxHeight = height))
                layout(width, height) {
                    if (placeable != null) {
                        val dx = origin.x.roundToInt()
                        val dy = origin.y.roundToInt()
                        val anchor = request.anchor.translate(-dx, -dy)
                        placeable.place(
                            TooltipPlacement.place(
                                anchor, request.pointX - dx, request.gap, request.margin,
                                IntSize(width, height), IntSize(placeable.width, placeable.height),
                            ),
                        )
                    }
                }
            }
        }
    }
}
