package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RunSummaryTest {
    @Test
    fun runIsNotLimitedToTwelveMonths() {
        var session = AdventureRun.choose(SimulationSessions.runUntilBlocked(AdventureRun.start()), HousingDecision.Stay)
        session = SimulationSessions.runUntilBlocked(session, maxMonths = 20)
        assertEquals(24, session.currentMonth)
        assertNotEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun openingSummaryHasNoMonthsAndOpeningBalances() {
        val start = AdventureRun.start()
        val summary = RunSummary.of(start)
        assertEquals(0, summary.monthsSimulated)
        assertEquals(start.currentState.cash, summary.cash)
        assertEquals(Money.ZERO, summary.totalIncome)
        assertEquals(Money.ZERO, summary.totalExpenses)
    }

    @Test
    fun summaryTotalsOnlySimulatedMonthsAndReconcilesCash() {
        val waiting = SimulationSessions.runUntilBlocked(AdventureRun.start())
        val summary = RunSummary.of(waiting)
        assertEquals(waiting.history.size, summary.monthsSimulated)
        // No investments, borrowing or debt in the demo run: cash moves by income minus expenses.
        assertEquals(waiting.initialState.cash + summary.totalIncome - summary.totalExpenses, summary.cash)
        assertEquals(summary.cash, summary.netWorth)
    }
}
