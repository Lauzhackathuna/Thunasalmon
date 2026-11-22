package com.tuna.finlife.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.tuna.finlife.game.BalanceMetric
import com.tuna.finlife.i18n.formatCents
import com.tuna.finlife.simulation.TwelveMonthProjection
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import finlife.shared.generated.resources.*
import com.tuna.finlife.game.AlternativeChoice
import com.tuna.finlife.game.ChartScale
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.kind
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.MonthlyState
import com.tuna.finlife.simulation.SimulationSession
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.cash_history
import finlife.shared.generated.resources.chart_axis_months
import finlife.shared.generated.resources.chart_axis_net_worth
import finlife.shared.generated.resources.chart_difference
import finlife.shared.generated.resources.chart_other_answer
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
private val TooltipWidth = 240.dp

/** Whole CHF of the plotted value: the engine's net worth (cash + investments − debt − unpaid bills). */
private fun MonthlyState.plotted(metric: BalanceMetric): Long = metric.value(this).cents

/**
 * Net worth over the simulated months: one 2dp line, hairline grid, labelled axes, and a dotted line for
 * the alternative to the latest choice. Hover (mouse), press/drag (touch) or arrow keys (focus) show a
 * crosshair snapped to the nearest month and a tooltip with that month's details. The tooltip is drawn
 * by [TooltipHost] and never receives pointer input, so it cannot interrupt the hover.
 */
@Composable
internal fun CashChart(session: SimulationSession, counterfactual: Counterfactual?, modifier: Modifier = Modifier,
    projection: TwelveMonthProjection? = null, baseline: TwelveMonthProjection? = null,
    combineHistory: Boolean = false,
    selectedMetric: BalanceMetric? = null,
    onSelectMetric: ((BalanceMetric) -> Unit)? = null,
) {
    var localMetric by remember { mutableStateOf(BalanceMetric.Cash) }
    val metric = selectedMetric ?: localMetric
    var window by remember { mutableStateOf<Int?>(12) }
    val combined = projection != null && combineHistory
    val allStates = remember(session, projection, combined) {
        when {
            combined -> session.states.dropLast(1) + requireNotNull(projection).states
            projection != null -> projection.states
            else -> session.states
        }
    }
    if (allStates.size == 1) {
        Text(stringResource(Res.string.history_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val states = remember(allStates, window, combined, session.currentMonth) {
        if (combined) {
            // In forecast mode, 3/6/12 means upcoming months, plus at most 3 past months for context.
            val start = if (window == null) 0 else (session.currentMonth - 3).coerceAtLeast(0)
            val end = session.currentMonth + (window ?: 12)
            allStates.filter { it.month in start..end }
        } else allStates.slice(ChartScale.visibleRange(allStates.size, window, projection != null))
    }
    val firstMonth = states.first().month
    val projectionStart = if (combined) session.currentMonth - firstMonth else -1
    val history = remember(session, projection, combined) {
        (if (combined) session.history + requireNotNull(projection).records
         else projection?.records ?: session.history).associateBy { it.after.month }
    }
    val events = remember(session) { session.calendar.associateBy { it.id } }
    val values = remember(states, metric) { states.map { it.plotted(metric) } }
    val count = states.size
    val altStates = remember(session, counterfactual, baseline, firstMonth, combined) {
        val all = when {
            combined && baseline != null -> session.states.dropLast(1) + baseline.states
            baseline != null -> baseline.states
            else -> counterfactual?.alternative?.states.orEmpty()
        }
        all.filter { it.month >= firstMonth }
    }
    val altFrom = when {
        combined && baseline != null -> projectionStart
        baseline != null -> 0
        else -> ((counterfactual?.month ?: firstMonth) - firstMonth).coerceAtLeast(0)
    }
    val altValues = remember(altStates, metric) { altStates.map { it.plotted(metric) } }
    val altLast = minOf(altStates.lastIndex, values.lastIndex)
    val altRange = if (altLast > altFrom) altFrom..altLast else IntRange.EMPTY

    val axis = remember(values, altValues, altRange) {
        val all = values + altRange.map { altValues[it] }
        ChartScale.valueAxis(all.min(), all.max())
    }
    val group = stringResource(Res.string.money_group_separator)
    val decimal = stringResource(Res.string.money_decimal_separator)
    val yLabels = remember(axis, group, decimal) {
        if (axis.max - axis.min < 10000) axis.ticks.map { formatCents(it, group, decimal) }
        else ChartScale.tickLabels(axis.ticks.map { it / 100 }, group, decimal)
    }
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var selected by remember(session, projection, window, metric) { mutableStateOf<Int?>(null) }
    val crosshair = remember { Animatable(0f) }
    var crosshairShown by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        val target = selected ?: run { crosshairShown = false; return@LaunchedEffect }
        // Glide between months once shown; jump on first appearance so it never sweeps from month 0.
        if (crosshairShown) crosshair.animateTo(target.toFloat(), tween(120)) else crosshair.snapTo(target.toFloat())
        crosshairShown = true
    }

    val altLabel = if (baseline != null) stringResource(Res.string.outlook_baseline) else counterfactual?.let {
        stringResource(Res.string.historical_alternative, alternativeLabel(it.choice))
    }
    val description = metricLabel(metric) + ", " + stringResource(if (projection == null) Res.string.cash_history else Res.string.future_path)
    val selectedLabel = selected?.let { monthLabel(states[it].month) + ": " + money(metric.value(states[it])) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.chart_metric), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BalanceMetric.entries.forEach { value ->
                FilterChip(selected = metric == value, onClick = {
                    if (selectedMetric == null) localMetric = value else onSelectMetric?.invoke(value)
                }, label = { Text(metricLabel(value)) })
            }
        }
        Text(stringResource(if (combined) Res.string.chart_future_range else Res.string.chart_range), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf<Int?>(3, 6, 12, null).forEach { value ->
                FilterChip(selected = window == value, onClick = { window = value },
                    label = { Text(value?.toString() ?: stringResource(Res.string.chart_all)) })
            }
        }
        if (combined) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
                LegendItem(stringResource(Res.string.chart_your_path), dotted = false)
                LegendItem(stringResource(Res.string.future_path), dotted = true)
                if (!altRange.isEmpty() && altLabel != null) LegendItem(altLabel, dotted = true, secondary = true)
            }
        } else if (!altRange.isEmpty() && altLabel != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
                LegendItem(stringResource(if (projection == null) Res.string.chart_your_path else Res.string.future_path), dotted = false)
                LegendItem(altLabel, dotted = true, secondary = true)
            }
        }
        // Y-axis title: what is plotted and its unit.
        Text(metricLabel(metric) + " · " + LocalCurrencyCode.current, style = labelStyle)

        var bounds by remember { mutableStateOf<Rect?>(null) }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(ChartHeight).onGloballyPositioned { bounds = it.boundsInRoot() },
        ) {
            val leftPx = with(density) { yLabels.maxOf { measurer.measure(it, labelStyle).size.width } + 8.dp.toPx() }
            val rightPx = with(density) { 8.dp.toPx() }
            val topPx = with(density) { 8.dp.toPx() }
            val bottomPx = with(density) { measurer.measure("0", labelStyle).size.height + 8.dp.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val plotWidth = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
            val monthTicks = ChartScale.monthTicks(count - 1, (maxWidth.value / 85).toInt().coerceIn(2, 6))
            val geometry by rememberUpdatedState(Triple(leftPx, plotWidth, count))
            fun xOf(index: Float) = leftPx + if (count <= 1) 0f else plotWidth * index / (count - 1)

            Canvas(
                Modifier.matchParentSize()
                    .pointerInput(session, projection, window) {
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
                val span = (axis.max - axis.min).toDouble()
                fun yOf(cents: Long) = topPx + (plotHeight * (axis.max - cents) / span).toFloat()
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
                    val text = measurer.measure((firstMonth + month).toString(), labelStyle)
                    val x = (xOf(month.toFloat()) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                    drawText(text, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
                }
                // Historical path stays solid; future projection is distinguished by a dashed line.
                val line = Path()
                for ((index, value) in values.withIndex()) {
                    if (combined && index > projectionStart) break
                    val point = Offset(xOf(index.toFloat()), yOf(value))
                    if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
                }
                drawPath(line, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (combined && projectionStart >= 0 && projectionStart < values.lastIndex) {
                    val forecast = Path()
                    for (index in projectionStart..values.lastIndex) {
                        val point = Offset(xOf(index.toFloat()), yOf(values[index]))
                        if (index == projectionStart) forecast.moveTo(point.x, point.y) else forecast.lineTo(point.x, point.y)
                    }
                    drawPath(forecast, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
                }
                // The alternative, dotted, from the month of the choice.
                if (!altRange.isEmpty()) {
                    val dotted = Path()
                    for (index in altRange) {
                        val point = Offset(xOf(index.toFloat()), yOf(altValues[index]))
                        if (index == altRange.first) dotted.moveTo(point.x, point.y) else dotted.lineTo(point.x, point.y)
                    }
                    drawPath(dotted, colors.secondary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots(this)))
                }

                // Crosshair and markers (8dp dot with a 2dp surface ring), else an end-dot on the latest month.
                val markerIndex = selected ?: values.lastIndex
                if (selected != null) {
                    val x = xOf(crosshair.value)
                    drawLine(colors.onSurfaceVariant, Offset(x, topPx), Offset(x, plotBottom), hairline)
                    if (markerIndex in altRange && markerIndex > altFrom) {
                        // Hollow marker on the alternative so it differs from the solid path's dot.
                        val altMarker = Offset(xOf(markerIndex.toFloat()), yOf(altValues[markerIndex]))
                        drawCircle(colors.surface, 6.dp.toPx(), altMarker)
                        drawCircle(colors.primary, 4.dp.toPx(), altMarker, style = Stroke(2.dp.toPx()))
                    }
                }
                val marker = Offset(xOf(markerIndex.toFloat()), yOf(values[markerIndex]))
                drawCircle(colors.surface, 6.dp.toPx(), marker)
                drawCircle(colors.primary, 4.dp.toPx(), marker)
            }

            // Tooltip, shown through the screen-level host.
            val host = LocalTooltipHost.current
            val owner = remember { Any() }
            DisposableEffect(host) { onDispose { host?.hide(owner) } }
            val index = selected
            val box = bounds
            val tooltip = if (index != null && box != null) {
                // The pointer is at most half a month-step from the snapped point; keep the tooltip further away.
                val halfStep = if (count <= 1) 0f else plotWidth / (count - 1) / 2f
                val gap = with(density) { maxOf(16.dp.toPx(), halfStep + 8.dp.toPx()) }.roundToInt()
                val margin = with(density) { 8.dp.toPx() }.roundToInt()
                val alternative = if (index in altRange && index > altFrom && altLabel != null) {
                    AlternativeReading(altLabel, altStates[index])
                } else {
                    null
                }
                val record = history[states[index].month]
                TooltipRequest(
                    owner = owner,
                    anchor = IntRect(box.left.roundToInt(), box.top.roundToInt(), box.right.roundToInt(), box.bottom.roundToInt()),
                    pointX = (box.left + xOf(index.toFloat())).roundToInt(),
                    gap = gap,
                    margin = margin,
                ) { MonthTooltip(states[index].month, states[index], record, alternative, events, metric,
                    projected = combined && states[index].month > session.currentMonth) }
            } else {
                null
            }
            SideEffect { if (tooltip != null) host?.show(tooltip) else host?.hide(owner) }
        }
        // X-axis title.
        Text(stringResource(Res.string.chart_axis_months), style = labelStyle, modifier = Modifier.align(Alignment.End))
    }
}

private class AlternativeReading(val label: String, val state: MonthlyState)

/** Tooltip body. A plain Column (not a Surface): it must not take part in pointer handling. */
@Composable
private fun MonthTooltip(
    index: Int,
    state: MonthlyState,
    record: MonthlyRecord?,
    alternative: AlternativeReading?,
    events: Map<String, LifeEvent>,
    metric: BalanceMetric,
    projected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.width(TooltipWidth)
            .background(colors.surface, MaterialTheme.shapes.small)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .padding(FinLifeSpacing.small),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(monthLabel(index), style = MaterialTheme.typography.labelLarge)
        if (projected) Text(stringResource(Res.string.future_path), style = MaterialTheme.typography.labelSmall)
        TooltipLine(metricLabel(metric), money(metric.value(state)))
        if (alternative != null) {
            TooltipLine(alternative.label, money(metric.value(alternative.state)))
            val difference = (metric.value(state) - metric.value(alternative.state)).cents / 100.0
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
        TooltipLine(stringResource(Res.string.actual_cash), money(state.cash))
        val unpaid = state.unpaid.items.fold(Money.ZERO) { total, bill -> total + bill.missing }
        if (unpaid > Money.ZERO) TooltipLine(stringResource(Res.string.unpaid_bills), money(unpaid))
        if (record != null) {
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
}

/** Round-capped, near-zero-length dashes read as dots. */
private fun dots(scope: DrawScope): PathEffect = with(scope) {
    PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
}

@Composable
private fun LegendItem(label: String, dotted: Boolean, secondary: Boolean = false) {
    val color = if (secondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            val y = size.height / 2
            drawLine(
                color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Legend/tooltip name of the dotted path. */
@Composable
private fun alternativeLabel(choice: AlternativeChoice): String = when (choice) {
    is AlternativeChoice.WithoutWhatIfs -> stringResource(
        when {
            WhatIf.Internship in choice.whatIfs && WhatIf.SharedFlat in choice.whatIfs -> Res.string.chart_without_both
            WhatIf.Internship in choice.whatIfs -> Res.string.chart_without_internship
            else -> Res.string.chart_without_move
        },
    )
    is AlternativeChoice.OtherAnswer ->
        stringResource(Res.string.chart_other_answer, optionLabel(choice.event.kind(), choice.option))
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

/** State m closes month m (shown 1-based like the month cards); state 0 is the opening balance. */
@Composable
private fun monthLabel(index: Int): String =
    if (index == 0) stringResource(Res.string.opening_balance) else stringResource(Res.string.month_number, index)

@Composable
internal fun metricLabel(metric: BalanceMetric): String = stringResource(when (metric) {
    BalanceMetric.Cash -> Res.string.metric_cash
    BalanceMetric.NetWorth -> Res.string.net_worth
    BalanceMetric.Theoretical -> Res.string.metric_theoretical
})
