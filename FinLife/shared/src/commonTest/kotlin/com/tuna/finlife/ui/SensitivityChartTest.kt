package com.tuna.finlife.ui

import com.tuna.finlife.game.AdventureOutlook
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.BalanceMetric
import com.tuna.finlife.game.Sensitivity
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.answerDefaults
import com.tuna.finlife.game.playDefaults
import com.tuna.finlife.simulation.Money
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitivityChartTest {
    private val fresh = answerDefaults(AdventureRun.start(seed = 5))
    private val played = playDefaults(fresh, 3)

    /** The two series exactly as the panel builds them, for a run and the slider values. */
    private fun series(draft: WhatIfDraft, sensitivity: Sensitivity): SensitivitySeries {
        val unchanged = AdventureOutlook.calculate(draft.base, draft.internshipSalary)
        val adjusted = AdventureOutlook.calculate(draft.base, draft.internshipSalary, sensitivity)
        return sensitivitySeries(unchanged.selected(draft.selected), adjusted.selected(draft.selected))
    }

    private val drafts = listOf(WhatIfDraft(fresh), WhatIfDraft(played))

    @Test
    fun neutralSlidersGiveIdenticalSeries() {
        for (draft in drafts) {
            val s = series(draft, Sensitivity())
            assertEquals(s.current, s.tested)
        }
    }

    @Test
    fun thirteenPointsStartingFromTheSameValue() {
        for (draft in drafts) {
            val s = series(draft, Sensitivity(rentPercent = 20, incomePercent = -10))
            assertEquals(13, s.current.size)
            assertEquals(13, s.tested!!.size)
            assertEquals(s.current[0], s.tested[0])
        }
    }

    @Test
    fun tooltipShowsTheEngineAmountsAtThreeSixAndTwelveMonths() {
        for (draft in drafts) {
            val sensitivity = Sensitivity(rentPercent = 15, incomePercent = 10)
            val option = AdventureOutlook.calculate(draft.base, draft.internshipSalary, sensitivity).selected(draft.selected)
            val reference = AdventureOutlook.calculate(draft.base, draft.internshipSalary).selected(draft.selected)
            val s = series(draft, sensitivity)
            // Same values the panel used to list: net worth at +3, +6, +12 and the difference.
            for (offset in listOf(3, 6, 12)) {
                val reading = s.readingAt(offset)
                val b = BalanceMetric.NetWorth.value(option.at(offset))
                val a = BalanceMetric.NetWorth.value(reference.at(offset))
                assertEquals(b, reading.tested)
                assertEquals(a, reading.current)
                assertEquals(b - a, reading.gap)
            }
        }
    }

    @Test
    fun tooltipGapIsZeroNow() {
        for (draft in drafts) {
            val reading = series(draft, Sensitivity(rentPercent = 50, incomePercent = -50)).readingAt(0)
            assertEquals(Money.ZERO, reading.gap)
        }
    }

    @Test
    fun tooltipAlwaysStaysInsideTheChart() {
        val chart = IntSize(330, 140)
        val tooltip = IntSize(200, 100)
        for (pointX in 0..330 step 10) {
            for (pointY in listOf(0, 70, 140)) {
                val at = sensitivityTooltipOffset(chart, pointX, listOf(pointY), gap = 16, tooltip)
                assertTrue(at.x >= 0 && at.x + tooltip.width <= chart.width, "x for $pointX")
                assertTrue(at.y >= 0 && at.y + tooltip.height <= chart.height, "y for $pointX")
            }
        }
        // Beside the point when there is room: never under the pointer.
        val beside = sensitivityTooltipOffset(IntSize(600, 140), 50, listOf(70), 16, tooltip)
        assertEquals(66, beside.x)
        // Otherwise, away from the highlighted point: point near the top → tooltip at the bottom.
        assertEquals(40, sensitivityTooltipOffset(chart, 165, listOf(10), 16, tooltip).y)
    }

    @Test
    fun higherRentIsNeverAboveTheCurrentValues() {
        for (draft in drafts) {
            val s = series(draft, Sensitivity(rentPercent = 20))
            for (month in 0..12) assertTrue(s.tested!![month] <= s.current[month], "month $month")
        }
    }

    @Test
    fun higherIncomeIsNeverBelowTheCurrentValues() {
        for (draft in drafts) {
            val s = series(draft, Sensitivity(incomePercent = 20))
            for (month in 0..12) assertTrue(s.tested!![month] >= s.current[month], "month $month")
        }
    }

    @Test
    fun colouredZoneChangesExactlyWhereTheCurvesCross() {
        // A goes 0 → 100, B goes 100 → 0: they cross halfway, at month 0.5.
        val pieces = gapPieces(listOf(0L, 100L), listOf(100L, 0L))
        assertEquals(listOf(GapPiece(0.0, 0.5, gain = true), GapPiece(0.5, 1.0, gain = false)), pieces)
        assertEquals(valueAt(listOf(0L, 100L), 0.5), valueAt(listOf(100L, 0L), 0.5))
        // d goes from −10 to +30: crossing at t = −10 / (−10 − 30) = 0.25.
        assertEquals(1.25, gapPieces(listOf(0L, 10L, 0L), listOf(0L, 0L, 30L))[1].to)
        // No gap, no piece.
        assertTrue(gapPieces(listOf(5L, 6L, 7L), listOf(5L, 6L, 7L)).isEmpty())
    }

    @Test
    fun axisKeepsAMarginAndRoundTicksWithoutForcingZero() {
        val axis = sensitivityAxis(listOf(800_000L, 600_000L, 500_000L)) // 8 000 → 5 000 CHF
        assertTrue(axis.min > 0, "0 is not forced into the range")
        assertTrue(axis.min <= 470_000L && axis.max >= 830_000L, "about 10 % margin on each side")
        assertTrue(axis.ticks.size in 2..3)
        assertTrue(axis.ticks.all { it in axis.min..axis.max && it % 100 == 0L })
        // A flat line still gets a band and ticks.
        val flat = sensitivityAxis(listOf(123_400L, 123_400L))
        assertTrue(flat.max > flat.min && flat.ticks.size in 2..3)
    }
}
