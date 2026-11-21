package com.tuna.finlife.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartScaleTest {
    @Test
    fun valueAxisUsesCleanStepsThatCoverTheData() {
        val axis = ChartScale.valueAxis(-3_250, 8_000)
        assertEquals(listOf(-5_000L, 0, 5_000, 10_000), axis.ticks)
        assertTrue(axis.min <= -3_250 && axis.max >= 8_000)
    }

    @Test
    fun flatSeriesStillGetsABand() {
        val axis = ChartScale.valueAxis(8_000, 8_000)
        assertTrue(axis.min < 8_000 && axis.max > 8_000)
        assertTrue(axis.ticks.size >= 2)
    }

    @Test
    fun monthTicksStayFewAndCalendarLike() {
        assertEquals(listOf(0, 1, 2, 3), ChartScale.monthTicks(3))
        assertEquals(listOf(0, 3, 6, 9, 12, 15), ChartScale.monthTicks(15))
        assertEquals(listOf(0, 12, 24, 36, 48, 60), ChartScale.monthTicks(70))
        assertTrue(ChartScale.monthTicks(1_200).size <= 7)
    }

    @Test
    fun nearestIndexSnapsAndClamps() {
        assertEquals(0, ChartScale.nearestIndex(-5f, 100f, 11))
        assertEquals(3, ChartScale.nearestIndex(31f, 100f, 11))
        assertEquals(10, ChartScale.nearestIndex(500f, 100f, 11))
        assertEquals(0, ChartScale.nearestIndex(50f, 100f, 1))
    }

    @Test
    fun tickLabelsAreFullBelowTenThousandAndCompactAbove() {
        assertEquals("0", ChartScale.tickLabel(0, ",", "."))
        assertEquals("2,500", ChartScale.tickLabel(2_500, ",", "."))
        assertEquals("−2,500", ChartScale.tickLabel(-2_500, ",", "."))
        assertEquals("10K", ChartScale.tickLabel(10_000, ",", "."))
        assertEquals("12,5K", ChartScale.tickLabel(12_500, " ", ","))
        assertEquals("1,250K", ChartScale.tickLabel(1_250_000, ",", "."))
    }
}
