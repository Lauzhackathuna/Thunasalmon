package com.tuna.finlife.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tuna.finlife.game.ChartScale
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.kind
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SimulationSession
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.cash_history
import finlife.shared.generated.resources.chart_axis_months
import finlife.shared.generated.resources.chart_difference
import finlife.shared.generated.resources.chart_without_both
import finlife.shared.generated.resources.chart_without_internship
import finlife.shared.generated.resources.chart_without_move
import finlife.shared.generated.resources.chart_your_path
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.money_decimal_separator
import finlife.shared.generated.resources.money_group_separator
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.opening_balance
import finlife.shared.generated.resources.unpaid_bills
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private val ChartHeight = 200.dp
private val TooltipWidth = 230.dp

/**
 * Cash balance over the simulated months: one 2dp line, hairline grid, labelled axes.
 * Hover (mouse), press/drag (touch) or arrow keys (focus) show a crosshair snapped to the
 * nearest month and a tooltip with that month's details.
 */
@Composable
internal fun CashChart(session: SimulationSession, counterfactual: Counterfactual?, modifier: Modifier = Modifier) {
    val states = remember(session) { session.states }
    val history = remember(session) { session.history }
    val events = remember(session) { session.calendar.associateBy { it.id } }
    val cash = remember(states) { states.map { it.theoreticalCash.cents / 100 } }
    // Alternative path (dotted): from the month it branched off to the last month it reached.
    val altStates = remember(counterfactual) { counterfactual?.alternative?.states.orEmpty() }
    val altFrom = counterfactual?.month ?: 0
    val altCash = remember(altStates) { altStates.map { it.theoreticalCash.cents / 100 } }
    val altLast = minOf(altStates.lastIndex, cash.lastIndex)
    val altRange = if (altLast > altFrom) altFrom..altLast else IntRange.EMPTY
    val axis = remember(cash, altCash, altRange) {
        val all = cash + altRange.map { altCash[it] }
        ChartScale.valueAxis(all.min(), all.max())
    }
    val monthTicks = remember(states.size) { ChartScale.monthTicks(states.lastIndex) }
    val count = states.size

    val group = stringResource(Res.string.money_group_separator)
    val decimal = stringResource(Res.string.money_decimal_separator)
    val yLabels = remember(axis, group, decimal) { ChartScale.tickLabels(axis.ticks, group, decimal) }
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var selected by remember(session) { mutableStateOf<Int?>(null) }
    val crosshair = remember { Animatable(0f) }
    var crosshairShown by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        val target = selected ?: run { crosshairShown = false; return@LaunchedEffect }
        // Glide between months once shown; jump on first appearance so it never sweeps from month 0.
        if (crosshairShown) crosshair.animateTo(target.toFloat(), tween(120)) else crosshair.snapTo(target.toFloat())
        crosshairShown = true
    }

    val description = stringResource(Res.string.cash_history)
    val selectedLabel = selected?.let { monthLabel(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!altRange.isEmpty() && counterfactual != null) {
            // Two series: the legend is always present; identity by line style, not colour alone.
            Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
                LegendItem(stringResource(Res.string.chart_your_path), dotted = false)
                LegendItem(withoutLabel(counterfactual.removed), dotted = true)
            }
        }
        // Y-axis title: the unit of the values.
        Text(LocalCurrencyCode.current, style = labelStyle)
        BoxWithConstraints(Modifier.fillMaxWidth().height(ChartHeight)) {
            val leftPx = with(density) {
                (yLabels.maxOf { measurer.measure(it, labelStyle).size.width }).toFloat() + 8.dp.toPx()
            }
            val rightPx = with(density) { 8.dp.toPx() }
            val topPx = with(density) { 8.dp.toPx() }
            val bottomPx = with(density) { measurer.measure("0", labelStyle).size.height + 8.dp.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val plotWidth = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
            val geometry by rememberUpdatedState(Triple(leftPx, plotWidth, count))
            fun xOf(index: Float) = leftPx + if (count <= 1) 0f else plotWidth * index / (count - 1)

            Canvas(
                Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val (left, width, points) = geometry
                                when (event.type) {
                                    PointerEventType.Exit -> if (change.type == PointerType.Mouse) selected = null
                                    PointerEventType.Enter, PointerEventType.Move, PointerEventType.Press ->
                                        selected = ChartScale.nearestIndex(change.position.x - left, width, points)
                                    // Touch keeps the last reading visible after the finger lifts.
                                    else -> Unit
                                }
                            }
                        }
                    }
                    .onFocusChanged { if (it.isFocused && selected == null) selected = count - 1 }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val current = selected ?: (count - 1)
                        when (event.key) {
                            Key.DirectionLeft -> { selected = (current - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { selected = (current + 1).coerceAtMost(count - 1); true }
                            Key.Escape -> { selected = null; true }
                            else -> false
                        }
                    }
                    .focusable()
                    .semantics {
                        contentDescription = description
                        if (selectedLabel != null) stateDescription = selectedLabel
                    },
            ) {
                val plotBottom = size.height - bottomPx
                val plotHeight = (plotBottom - topPx).coerceAtLeast(1f)
                val span = (axis.max - axis.min).toFloat()
                fun yOf(chf: Long) = topPx + plotHeight * (axis.max - chf) / span
                val hairline = 1.dp.toPx()

                // Grid and y labels; the zero line is one step stronger when it is in range.
                for ((tick, label) in axis.ticks.zip(yLabels)) {
                    val y = yOf(tick)
                    drawLine(
                        if (tick == 0L) colors.outline else colors.outlineVariant,
                        Offset(leftPx, y), Offset(leftPx + plotWidth, y), hairline,
                    )
                    val text = measurer.measure(label, labelStyle)
                    drawText(text, topLeft = Offset(leftPx - 6.dp.toPx() - text.size.width, y - text.size.height / 2f))
                }
                // X labels: month numbers, kept inside the canvas.
                for (month in monthTicks) {
                    val text = measurer.measure(month.toString(), labelStyle)
                    val x = (xOf(month.toFloat()) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                    drawText(text, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
                }
                // The series.
                val line = Path()
                for ((index, value) in cash.withIndex()) {
                    val point = Offset(xOf(index.toFloat()), yOf(value))
                    if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
                }
                drawPath(line, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (!altRange.isEmpty()) {
                    val dotted = Path()
                    for (index in altRange) {
                        val point = Offset(xOf(index.toFloat()), yOf(altCash[index]))
                        if (index == altRange.first) dotted.moveTo(point.x, point.y) else dotted.lineTo(point.x, point.y)
                    }
                    drawPath(dotted, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots(this)))
                }

                // Crosshair and marker (8dp dot with a 2dp surface ring), else an end-dot on the latest month.
                val markerIndex = selected ?: cash.lastIndex
                if (selected != null) {
                    val x = xOf(crosshair.value)
                    drawLine(colors.onSurfaceVariant, Offset(x, topPx), Offset(x, plotBottom), hairline)
                }
                if (selected != null && markerIndex in altRange && markerIndex > altFrom) {
                    // Hollow marker on the alternative so it differs from the solid path's dot.
                    val altMarker = Offset(xOf(markerIndex.toFloat()), yOf(altCash[markerIndex]))
                    drawCircle(colors.surface, 6.dp.toPx(), altMarker)
                    drawCircle(colors.primary, 4.dp.toPx(), altMarker, style = Stroke(2.dp.toPx()))
                }
                val marker = Offset(xOf(markerIndex.toFloat()), yOf(cash[markerIndex]))
                drawCircle(colors.surface, 6.dp.toPx(), marker)
                drawCircle(colors.primary, 4.dp.toPx(), marker)
            }

            val index = selected
            if (index != null) {
                // The pointer is at most half a month-step from the snapped point: keep the tooltip further
                // away than that, so the pointer never lands on it (that used to hide and re-show it).
                val halfStep = if (count <= 1) 0f else plotWidth / (count - 1) / 2f
                val gap = with(density) { maxOf(16.dp.toPx(), halfStep + 8.dp.toPx()) }.roundToInt()
                val margin = with(density) { 8.dp.toPx() }.roundToInt()
                val pointX = xOf(index.toFloat()).roundToInt()
                val placement = remember(pointX, gap, margin) { TooltipPlacement(pointX, gap, margin) }
                // A popup is drawn above the page and placed within the whole window, never cut off.
                Popup(
                    popupPositionProvider = placement,
                    properties = PopupProperties(focusable = false),
                ) { Surface(
                    modifier = Modifier.width(TooltipWidth),
                    shape = MaterialTheme.shapes.small,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        Modifier.padding(FinLifeSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val state = states[index]
                        Text(monthLabel(index), style = MaterialTheme.typography.labelLarge)
                        TooltipLine(stringResource(Res.string.cash_balance), money(state.theoreticalCash))
                        TooltipLine(stringResource(Res.string.net_worth), money(state.netWorth))
                        val unpaid = state.unpaid.items.fold(Money.ZERO) { total, bill -> total + bill.missing }
                        if (unpaid > Money.ZERO) TooltipLine(stringResource(Res.string.unpaid_bills), money(unpaid))
                        if (counterfactual != null && index in altRange && index > altFrom) {
                            val other = altStates[index]
                            TooltipLine(withoutLabel(counterfactual.removed), money(other.theoreticalCash))
                            val difference = (state.theoreticalCash - other.theoreticalCash).cents / 100.0
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                                Text(
                                    stringResource(Res.string.chart_difference),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                                DeltaIndicator(displayMoney(abs(difference)), DeltaDirection.fromAmount(difference))
                            }
                        }
                        if (index > 0) {
                            val record = history[index - 1]
                            TooltipLine(stringResource(Res.string.income), money(record.flow.income))
                            TooltipLine(stringResource(Res.string.expenses), money(record.flow.expenses))
                            for (outcome in record.events) {
                                val event = events[outcome.eventId] ?: continue
                                outcomeText(event.kind(), outcome, record)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } }
            }
        }
        // X-axis title.
        Text(stringResource(Res.string.chart_axis_months), style = labelStyle, modifier = Modifier.align(Alignment.End))
    }
}

/**
 * Places the tooltip in window coordinates: right of the point, else left, else above or below the
 * chart. Always inside the window and at least [gap] px horizontally from the point (beside), or
 * outside the chart (above/below), so the pointer is never over it.
 */
private class TooltipPlacement(private val pointX: Int, private val gap: Int, private val margin: Int) :
    PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val (width, height) = popupContentSize
        val x = anchorBounds.left + pointX
        fun clampY(y: Int) = y.coerceIn(0, maxOf(0, windowSize.height - height))
        if (x + gap + width <= windowSize.width) return IntOffset(x + gap, clampY(anchorBounds.top))
        if (x - gap - width >= 0) return IntOffset(x - gap - width, clampY(anchorBounds.top))
        val left = (x - width / 2).coerceIn(0, maxOf(0, windowSize.width - width))
        val below = anchorBounds.bottom + margin
        return if (below + height <= windowSize.height) IntOffset(left, below)
        else IntOffset(left, maxOf(0, anchorBounds.top - margin - height))
    }
}

/** Tooltip row: the value leads; the label is small and muted. */
@Composable
private fun TooltipLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        Text(
            label,
            Modifier.weight(1f).alignByBaseline(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.alignByBaseline(), style = MaterialTheme.typography.labelLarge)
    }
}

/** Round-capped, near-zero-length dashes read as dots. */
private fun dots(scope: DrawScope): PathEffect = with(scope) {
    PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
}

@Composable
private fun LegendItem(label: String, dotted: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            val y = size.height / 2
            drawLine(
                color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dotted) dots(this) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun withoutLabel(removed: Set<WhatIf>): String = stringResource(
    when {
        WhatIf.Internship in removed && WhatIf.SharedFlat in removed -> Res.string.chart_without_both
        WhatIf.Internship in removed -> Res.string.chart_without_internship
        else -> Res.string.chart_without_move
    },
)

/** State m closes month m (shown 1-based like the month cards); state 0 is the opening balance. */
@Composable
private fun monthLabel(index: Int): String =
    if (index == 0) stringResource(Res.string.opening_balance) else stringResource(Res.string.month_number, index)
