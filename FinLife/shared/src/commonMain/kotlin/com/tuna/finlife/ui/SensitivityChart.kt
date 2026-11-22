package com.tuna.finlife.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tuna.finlife.game.BalanceMetric
import com.tuna.finlife.game.ChartScale
import com.tuna.finlife.game.FutureOption
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.simulation.Money
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.money_decimal_separator
import finlife.shared.generated.resources.money_group_separator
import finlife.shared.generated.resources.sensitivity_chart_current
import finlife.shared.generated.resources.sensitivity_chart_description
import finlife.shared.generated.resources.sensitivity_chart_description_neutral
import finlife.shared.generated.resources.sensitivity_chart_hint
import finlife.shared.generated.resources.sensitivity_chart_now
import finlife.shared.generated.resources.sensitivity_chart_tested
import finlife.shared.generated.resources.sensitivity_tooltip_gap
import finlife.shared.generated.resources.sensitivity_tooltip_months
import finlife.shared.generated.resources.sensitivity_tooltip_one_month
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// Pure part: no drawing, tested in commonTest.
// ---------------------------------------------------------------------------------------------

/**
 * Net worth, month by month (index 0 = now), for the unchanged outlook ([current]) and for the
 * outlook with the sliders ([tested], null while both sliders are at 0 %).
 * Read straight from the same engine projections as the stability shown under the chart.
 */
internal data class SensitivitySeries(val current: List<Money>, val tested: List<Money>?) {
    /** What the tooltip shows for [month]; [gap] = tested − current, null without tested values. */
    fun readingAt(month: Int): SensitivityReading {
        require(month in current.indices)
        val b = tested?.get(month)
        return SensitivityReading(month, b, current[month], b?.let { it - current[month] })
    }
}

internal data class SensitivityReading(val month: Int, val tested: Money?, val current: Money, val gap: Money?)

internal fun sensitivitySeries(unchanged: FutureOption, adjusted: FutureOption?): SensitivitySeries =
    SensitivitySeries(
        unchanged.projection.states.map { BalanceMetric.NetWorth.value(it) },
        adjusted?.projection?.states?.map { BalanceMetric.NetWorth.value(it) },
    )

/** Vertical scale in cents: [min]..[max] is the drawn range, [ticks] the round values labelled. */
internal data class SensitivityAxis(val min: Long, val max: Long, val ticks: List<Long>)

/** Smallest margin above and below the curves: 50 CHF, so that a flat line still gets a band. */
private const val MIN_MARGIN_CENTS = 5_000L

/**
 * Range = lowest and highest value of the curves, widened by about 10 % of their spread on each side
 * (0 is not forced in). Ticks: round multiples (1, 2, 2.5 or 5 × 10ⁿ CHF) inside that range, three
 * if possible, otherwise two.
 */
internal fun sensitivityAxis(values: List<Long>): SensitivityAxis {
    require(values.isNotEmpty())
    val low = values.min()
    val high = values.max()
    val margin = maxOf((high - low) / 10, MIN_MARGIN_CENTS)
    val min = low - margin
    val max = high + margin
    val candidates = niceStepsCents().map { ticksIn(min, max, it) }
    val ticks = candidates.firstOrNull { it.size == 3 }
        ?: candidates.firstOrNull { it.size == 2 }
        ?: candidates.first { it.isNotEmpty() && it.size <= 4 }
    return SensitivityAxis(min, max, ticks)
}

/** 1, 2, 2.5, 5, 10, 20 … CHF, in cents, increasing. */
private fun niceStepsCents(): List<Long> {
    val result = mutableListOf<Long>()
    var power = 100L // 1 CHF
    while (power <= 1_000_000_000_000L) {
        result += listOf(power, power * 2, power * 5 / 2, power * 5)
        power *= 10
    }
    return result
}

/** Every multiple of [step] between [min] and [max] included. */
private fun ticksIn(min: Long, max: Long, step: Long): List<Long> {
    val first = -((-min).floorDiv(step)) * step // smallest multiple ≥ min
    val result = mutableListOf<Long>()
    var tick = first
    while (tick <= max && result.size <= 4) {
        result += tick
        tick += step
    }
    return result
}

/** One piece of the coloured zone between the curves, from month [from] to month [to] (fractional). */
internal data class GapPiece(val from: Double, val to: Double, val gain: Boolean)

/**
 * Splits the zone between [current] (A) and [tested] (B) into pieces of one sign each.
 * Between months i and i+1 both lines are straight, so d = B − A varies linearly from dᵢ to dᵢ₊₁.
 * If the sign changes, the lines cross where d = 0, i.e. at i + t with t = dᵢ / (dᵢ − dᵢ₊₁).
 */
internal fun gapPieces(current: List<Long>, tested: List<Long>): List<GapPiece> {
    require(current.size == tested.size)
    val pieces = mutableListOf<GapPiece>()
    for (i in 0 until current.lastIndex) {
        val d0 = tested[i] - current[i]
        val d1 = tested[i + 1] - current[i + 1]
        when {
            d0 == 0L && d1 == 0L -> Unit
            (d0 > 0 && d1 < 0) || (d0 < 0 && d1 > 0) -> {
                val crossing = i + d0.toDouble() / (d0 - d1).toDouble()
                pieces += GapPiece(i.toDouble(), crossing, gain = d0 > 0)
                pieces += GapPiece(crossing, (i + 1).toDouble(), gain = d1 > 0)
            }
            else -> pieces += GapPiece(i.toDouble(), (i + 1).toDouble(), gain = d0 + d1 > 0)
        }
    }
    return pieces
}

/** Value of a polyline at fractional month [x] (straight line between two months). */
internal fun valueAt(series: List<Long>, x: Double): Double {
    val i = x.toInt().coerceIn(0, maxOf(0, series.lastIndex - 1))
    if (series.size == 1) return series[0].toDouble()
    return series[i] + (series[i + 1] - series[i]) * (x - i)
}

/**
 * Where the tooltip goes, always entirely inside the chart (all values in px, chart coordinates).
 * Beside the selected month if it fits (right, else left), at least [gap] from it, so the pointer
 * is never under it. Otherwise centred on the month, at the top or the bottom of the chart,
 * whichever is further from the highlighted points [pointYs].
 */
internal fun sensitivityTooltipOffset(chart: IntSize, pointX: Int, pointYs: List<Int>, gap: Int, tooltip: IntSize): IntOffset {
    val maxX = maxOf(0, chart.width - tooltip.width)
    val maxY = maxOf(0, chart.height - tooltip.height)
    if (pointX + gap + tooltip.width <= chart.width) return IntOffset(pointX + gap, 0)
    if (pointX - gap - tooltip.width >= 0) return IntOffset(pointX - gap - tooltip.width, 0)
    val x = (pointX - tooltip.width / 2).coerceIn(0, maxX)
    // Distance from each candidate band to the nearest highlighted point; keep the larger.
    fun clearance(top: Int) = pointYs.minOfOrNull { y ->
        when {
            y < top -> top - y
            y > top + tooltip.height -> y - top - tooltip.height
            else -> -minOf(y - top, top + tooltip.height - y)
        }
    } ?: 0
    val y = if (clearance(0) >= clearance(maxY)) 0 else maxY
    return IntOffset(x, y)
}

// ---------------------------------------------------------------------------------------------
// Drawing.
// ---------------------------------------------------------------------------------------------

private val SensitivityChartHeight = 140.dp
private val SensitivityTooltipWidth = 200.dp
private val LabelledMonths = listOf(0, 3, 6, 12)
private val MarkedMonths = listOf(3, 6, 12)

/** A dotted grey line: the unchanged outlook. */
private fun currentStroke(scope: DrawScope) = with(scope) {
    Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
}

/** A solid emerald line: the outlook with the sliders. */
private fun testedStroke(scope: DrawScope) = with(scope) {
    Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/**
 * Mini chart of the "Test the assumptions" panel: net worth over the next months, unchanged values
 * (dotted grey) against the slider values (solid emerald), the gap filled green or red.
 * Hover (mouse), press/drag (touch) or arrow keys (focus) select the nearest month, as on the main
 * chart: a hairline, highlighted points and a tooltip kept inside the chart.
 */
@Composable
internal fun SensitivityChart(series: SensitivitySeries, modifier: Modifier = Modifier) {
    val current = remember(series) { series.current.map { it.cents } }
    val tested = remember(series) { series.tested?.map { it.cents } }
    val axis = remember(current, tested) { sensitivityAxis(current + tested.orEmpty()) }
    val group = stringResource(Res.string.money_group_separator)
    val decimal = stringResource(Res.string.money_decimal_separator)
    val yLabels = remember(axis, group, decimal) { ChartScale.tickLabels(axis.ticks.map { it / 100 }, group, decimal) }
    val now = stringResource(Res.string.sensitivity_chart_now)
    val lastMonth = current.lastIndex
    val count = current.size
    val xLabels = LabelledMonths.filter { it <= lastMonth }.map { it to if (it == 0) now else "+$it" }

    val colors = MaterialTheme.colorScheme
    val delta = LocalDeltaColors.current
    val neutralLine = colors.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val description = if (series.tested == null) {
        stringResource(Res.string.sensitivity_chart_description_neutral, lastMonth, money(series.current.last()))
    } else {
        stringResource(Res.string.sensitivity_chart_description, lastMonth, money(series.tested.last()), money(series.current.last()))
    }
    val reading = selected?.let { series.readingAt(it) }
    val selectedLabel = reading?.let { tooltipSummary(it) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
            SensitivityLegendItem(stringResource(Res.string.sensitivity_chart_current), neutralLine, dotted = true)
            if (tested != null) SensitivityLegendItem(stringResource(Res.string.sensitivity_chart_tested), colors.primary, dotted = false)
        }
        // Y-axis title: what is plotted and its unit, as on the main chart.
        Text(metricLabel(BalanceMetric.NetWorth) + " · " + LocalCurrencyCode.current, style = labelStyle)
        BoxWithConstraints(Modifier.fillMaxWidth().height(SensitivityChartHeight)) {
            // Geometry, also needed outside the Canvas to map the pointer to a month.
            val leftPx = with(density) { yLabels.maxOf { measurer.measure(it, labelStyle).size.width } + 8.dp.toPx() }
            val rightPx = with(density) { 8.dp.toPx() }
            val topPx = with(density) { 6.dp.toPx() }
            val bottomPx = with(density) { measurer.measure("0", labelStyle).size.height + 6.dp.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val plotWidth = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
            val plotBottom = heightPx - bottomPx
            val plotHeight = (plotBottom - topPx).coerceAtLeast(1f)
            val span = (axis.max - axis.min).toDouble()
            fun xOf(month: Double) = leftPx + if (lastMonth <= 0) 0f else (plotWidth * month / lastMonth).toFloat()
            fun yOf(cents: Double) = topPx + (plotHeight * (axis.max - cents) / span).toFloat()
            val geometry by rememberUpdatedState(Triple(leftPx, plotWidth, count))

            Canvas(
                Modifier.matchParentSize()
                    // Same gestures as the main chart.
                    .pointerInput(series) {
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
                        val at = selected ?: (count - 1)
                        when (event.key) {
                            Key.DirectionLeft -> { selected = (at - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { selected = (at + 1).coerceAtMost(count - 1); true }
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
                val hairline = 1.dp.toPx()

                // Light grid at the round values, with their labels.
                for ((tick, label) in axis.ticks.zip(yLabels)) {
                    val y = yOf(tick.toDouble())
                    drawLine(colors.outlineVariant, Offset(leftPx, y), Offset(leftPx + plotWidth, y), hairline)
                    val text = measurer.measure(label, labelStyle)
                    drawText(text, topLeft = Offset(leftPx - 6.dp.toPx() - text.size.width, y - text.size.height / 2f))
                }
                // The zero line, only when 0 lies inside the drawn range.
                if (axis.min < 0 && axis.max > 0) {
                    val y = yOf(0.0)
                    drawLine(colors.outline, Offset(leftPx, y), Offset(leftPx + plotWidth, y), hairline)
                }
                // Month labels: Now, +3, +6, +12, kept inside the canvas.
                for ((month, label) in xLabels) {
                    val text = measurer.measure(label, labelStyle)
                    val x = (xOf(month.toDouble()) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                    drawText(text, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
                }

                // Coloured zone between the curves, cut exactly where they cross.
                if (tested != null) {
                    for (piece in gapPieces(current, tested)) {
                        val zone = Path().apply {
                            moveTo(xOf(piece.from), yOf(valueAt(current, piece.from)))
                            lineTo(xOf(piece.to), yOf(valueAt(current, piece.to)))
                            lineTo(xOf(piece.to), yOf(valueAt(tested, piece.to)))
                            lineTo(xOf(piece.from), yOf(valueAt(tested, piece.from)))
                            close()
                        }
                        drawPath(zone, (if (piece.gain) delta.gain else delta.loss).copy(alpha = 0.2f))
                    }
                }

                fun line(values: List<Long>) = Path().apply {
                    for ((month, value) in values.withIndex()) {
                        val x = xOf(month.toDouble())
                        val y = yOf(value.toDouble())
                        if (month == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                // Hairline on the selected month, under the curves (as on the main chart).
                val pick = selected
                if (pick != null) {
                    val x = xOf(pick.toDouble())
                    drawLine(colors.onSurfaceVariant, Offset(x, topPx), Offset(x, plotBottom), hairline)
                }
                drawPath(line(current), neutralLine, style = currentStroke(this))
                if (tested != null) {
                    drawPath(line(tested), colors.primary, style = testedStroke(this))
                    // Small dots at +3, +6, +12.
                    for (month in MarkedMonths.filter { it <= tested.lastIndex }) {
                        val point = Offset(xOf(month.toDouble()), yOf(tested[month].toDouble()))
                        drawCircle(colors.surface, 4.5.dp.toPx(), point)
                        drawCircle(colors.primary, 3.dp.toPx(), point)
                    }
                }
                // Highlighted points of the selected month: hollow on A, filled on B (main chart's markers).
                if (pick != null) {
                    val a = Offset(xOf(pick.toDouble()), yOf(current[pick].toDouble()))
                    drawCircle(colors.surface, 6.dp.toPx(), a)
                    drawCircle(neutralLine, 4.dp.toPx(), a, style = Stroke(2.dp.toPx()))
                    if (tested != null) {
                        val b = Offset(xOf(pick.toDouble()), yOf(tested[pick].toDouble()))
                        drawCircle(colors.surface, 6.dp.toPx(), b)
                        drawCircle(colors.primary, 4.dp.toPx(), b)
                    }
                }
            }

            // Tooltip: drawn above the canvas, inside the chart. It has no pointer input, so the
            // pointer keeps reaching the canvas even over the tooltip (as with the main chart's host).
            if (reading != null) {
                val pointX = xOf(reading.month.toDouble()).roundToInt()
                val pointYs = listOfNotNull(current[reading.month], tested?.get(reading.month)).map { yOf(it.toDouble()).roundToInt() }
                // The pointer is at most half a month-step from the snapped point; keep the tooltip further away.
                val halfStep = if (count <= 1) 0f else plotWidth / (count - 1) / 2f
                val gap = with(density) { maxOf(16.dp.toPx(), halfStep + 8.dp.toPx()) }.roundToInt()
                Layout(content = { SensitivityTooltip(reading, maxWidth) }, modifier = Modifier.matchParentSize()) { measurables, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val placeable = measurables.first().measure(Constraints(maxWidth = width))
                    layout(width, height) {
                        placeable.place(sensitivityTooltipOffset(IntSize(width, height), pointX, pointYs, gap,
                            IntSize(placeable.width, placeable.height)))
                    }
                }
            }
        }
        if (tested == null) {
            Text(stringResource(Res.string.sensitivity_chart_hint), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun tooltipTitle(month: Int): String = when (month) {
    0 -> stringResource(Res.string.sensitivity_chart_now)
    1 -> stringResource(Res.string.sensitivity_tooltip_one_month)
    else -> stringResource(Res.string.sensitivity_tooltip_months, month)
}

/** "▲ +CHF 300" in the gain colour, "▼ −CHF 300" in the loss colour, "CHF 0" neutral. */
@Composable
private fun gapText(gap: Money): Pair<String, DeltaDirection> {
    val direction = DeltaDirection.fromAmount(gap.cents.toDouble())
    return money(if (gap < Money.ZERO) -gap else gap) to direction
}

/** Same text, read by screen readers when a month is selected. */
@Composable
private fun tooltipSummary(reading: SensitivityReading): String = buildList {
    add(tooltipTitle(reading.month))
    reading.tested?.let { add(stringResource(Res.string.sensitivity_chart_tested) + ": " + money(it)) }
    add(stringResource(Res.string.sensitivity_chart_current) + ": " + money(reading.current))
    reading.gap?.let { val (amount, direction) = gapText(it); add(stringResource(Res.string.sensitivity_tooltip_gap) + ": " + formatDelta(amount, direction)) }
}.joinToString(", ")

/** Tooltip body: same background, border, padding, fonts and rows as the main chart's tooltip. */
@Composable
private fun SensitivityTooltip(reading: SensitivityReading, chartWidth: androidx.compose.ui.unit.Dp) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.width(minOf(SensitivityTooltipWidth, chartWidth))
            .background(colors.surface, MaterialTheme.shapes.small)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .padding(FinLifeSpacing.small),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // Text on the tooltip's surface, whatever container the chart sits in (light and dark themes).
      CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Text(tooltipTitle(reading.month), style = MaterialTheme.typography.labelLarge)
        reading.tested?.let { TooltipLine(stringResource(Res.string.sensitivity_chart_tested), money(it)) }
        TooltipLine(stringResource(Res.string.sensitivity_chart_current), money(reading.current))
        reading.gap?.let { gap ->
            val (amount, direction) = gapText(gap)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                Text(
                    stringResource(Res.string.sensitivity_tooltip_gap),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                DeltaIndicator(amount, direction)
            }
        }
      }
    }
}

@Composable
private fun SensitivityLegendItem(label: String, color: Color, dotted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            val y = size.height / 2
            drawLine(color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
