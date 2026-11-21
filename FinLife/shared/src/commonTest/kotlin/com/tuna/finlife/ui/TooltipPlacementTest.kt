package com.tuna.finlife.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TooltipPlacementTest {
    private val window = IntSize(1200, 900)
    private val chart = IntRect(50, 400, 480, 650)
    private val tooltip = IntSize(290, 220)

    @Test
    fun goesRightOfThePointWhenItFits() {
        assertEquals(IntOffset(260, 400), TooltipPlacement.place(chart, 200, 60, 8, window, tooltip))
    }

    @Test
    fun goesLeftWhenTheRightSideIsFull() {
        val narrow = IntSize(800, 900)
        assertEquals(IntOffset(700 - 60 - 290, 400), TooltipPlacement.place(chart, 700, 60, 8, narrow, tooltip))
    }

    @Test
    fun goesBelowThenAboveTheChartWhenNeitherSideFits() {
        val narrow = IntSize(480, 900)
        assertEquals(IntOffset(95, 658), TooltipPlacement.place(chart, 240, 60, 8, narrow, tooltip))
        val lowChart = IntRect(0, 600, 480, 850)
        assertEquals(IntOffset(95, 600 - 8 - 220), TooltipPlacement.place(lowChart, 240, 60, 8, narrow, tooltip))
    }

    @Test
    fun staysInsideTheWindow() {
        val tall = IntSize(290, 1000)
        val placed = TooltipPlacement.place(chart, 200, 60, 8, window, tall)
        assertEquals(0, placed.y)
        val nearBottom = IntRect(50, 800, 480, 880)
        val beside = TooltipPlacement.place(nearBottom, 200, 60, 8, window, tooltip)
        assertTrue(beside.y + tooltip.height <= window.height)
    }

    /** The pointer is within half a step of the point; the tooltip must never cover any such position. */
    @Test
    fun neverCoversThePointerAcrossManyLayouts() {
        val halfStep = 30
        val gap = halfStep + 8
        for (width in listOf(480, 800, 1200, 1920)) {
            for (height in listOf(600, 900, 1200)) {
                for (top in listOf(0, 200, 450)) {
                    val anchor = IntRect(20, top, minOf(width, 460), top + 250)
                    val container = IntSize(width, height)
                    for (pointX in anchor.left..anchor.right step 10) {
                        val at = TooltipPlacement.place(anchor, pointX, gap, 8, container, tooltip)
                        val overlapsX = at.x < pointX + halfStep && at.x + tooltip.width > pointX - halfStep
                        val overlapsY = at.y < anchor.bottom && at.y + tooltip.height > anchor.top
                        // A non-covering spot exists beside the point or above/below the chart.
                        val fitsSomewhere = pointX + gap + tooltip.width <= width || pointX - gap - tooltip.width >= 0 ||
                            anchor.bottom + 8 + tooltip.height <= height || anchor.top - 8 - tooltip.height >= 0
                        if (fitsSomewhere) {
                            assertTrue(!(overlapsX && overlapsY), "covers pointer: w=$width h=$height top=$top x=$pointX at=$at")
                        }
                        assertTrue(at.x >= 0 && at.y >= 0, "outside window: $at")
                        assertTrue(at.x + tooltip.width <= maxOf(width, tooltip.width), "outside window: $at")
                    }
                }
            }
        }
    }
}
