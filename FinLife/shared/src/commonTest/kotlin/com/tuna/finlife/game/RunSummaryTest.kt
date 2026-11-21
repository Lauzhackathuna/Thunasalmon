package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RunSummaryTest {
    @Test
    fun runIsNotLimitedToTwelveMonths() {
        val session = (1..24).fold(AdventureRun.start(seed = 1)) { run, _ -> AdventureRun.advance(run) }
        assertEquals(24, session.currentMonth)
        assertNotEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun openingSummaryHasNoMonthsAndOpeningBalances() {
        val start = AdventureRun.start(seed = 1)
        val summary = RunSummary.of(start)
        assertEquals(0, summary.monthsSimulated)
        assertEquals(start.currentState.cash, summary.cash)
        assertEquals(Money.ZERO, summary.totalIncome)
        assertEquals(Money.ZERO, summary.totalExpenses)
    }

    @Test
    fun summaryTotalsOnlySimulatedMonthsAndReconcilesCash() {
        val waiting = (1..12).fold(AdventureRun.start(seed = 1)) { run, _ -> AdventureRun.advance(run) }
        val summary = RunSummary.of(waiting)
        assertEquals(waiting.history.size, summary.monthsSimulated)
        // No investments, borrowing or debt in the demo run: cash moves by income minus expenses.
        assertEquals(waiting.initialState.cash + summary.totalIncome - summary.totalExpenses, summary.cash)
        assertEquals(summary.cash, summary.netWorth)
    }
}
