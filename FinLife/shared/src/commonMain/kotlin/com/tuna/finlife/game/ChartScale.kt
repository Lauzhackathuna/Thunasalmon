package com.tuna.finlife.game

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure axis maths for the cash chart. Amounts are whole CHF. */
internal object ChartScale {
    data class Axis(val min: Long, val max: Long, val ticks: List<Long>)

    /** Clean ticks (1, 2, 2.5 or 5 × a power of ten) covering [low]..[high], about [target] of them. */
    fun valueAxis(low: Long, high: Long, target: Int = 5): Axis {
        require(target >= 2 && low <= high)
        // A flat line still gets a readable band around it.
        val span = maxOf(high - low, 100L).toDouble()
        val raw = span / (target - 1)
        val magnitude = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
            .let { maxOf(1L, it.toLong()) }
        val centre = (low + high) / 2.0
        val from = if (high - low >= 100) low.toDouble() else centre - span / 2
        val to = if (high - low >= 100) high.toDouble() else centre + span / 2
        val min = floor(from / step).toLong() * step
        val max = ceil(to / step).toLong() * step
        return Axis(min, max, (min..max step step).toList())
    }

    private val monthSteps = listOf(1, 2, 3, 6, 12, 24, 60, 120, 240, 600)

    /** Month ticks from 0 to [lastMonth], at most [maxTicks] intervals, on calendar-like steps. */
    fun monthTicks(lastMonth: Int, maxTicks: Int = 6): List<Int> {
        require(lastMonth >= 0 && maxTicks >= 1)
        val step = monthSteps.firstOrNull { lastMonth / it <= maxTicks } ?: monthSteps.last()
        return (0..lastMonth step step).toList()
    }

    /** Index of the point nearest to [x] when [count] points are spread evenly over [width]. */
    fun nearestIndex(x: Float, width: Float, count: Int): Int {
        require(count >= 1)
        if (count == 1 || width <= 0f) return 0
        return (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    /** Full number under 10,000 (with [groupSeparator]); compact "10K" / "12.5K" style from 10,000 on. */
    fun tickLabel(chf: Long, groupSeparator: String, decimalSeparator: String): String {
        val sign = if (chf < 0) "−" else ""
        val magnitude = abs(chf)
        if (magnitude >= 10_000) {
            val tenths = (magnitude / 100.0).roundToInt()
            val whole = group((tenths / 10).toLong(), groupSeparator)
            val text = if (tenths % 10 == 0) whole else "$whole$decimalSeparator${tenths % 10}"
            return "$sign${text}K"
        }
        return sign + group(magnitude, groupSeparator)
    }

    private fun group(value: Long, separator: String): String =
        value.toString().reversed().chunked(3).map { it.reversed() }.asReversed().joinToString(separator)
}
