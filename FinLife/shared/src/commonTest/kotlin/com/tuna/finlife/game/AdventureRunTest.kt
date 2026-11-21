package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdventureRunTest {
    @Test
    fun housingChoiceBlocksMonthFourAndBranchesFromTheSameBalance() {
        val waiting = SimulationSessions.runUntilBlocked(AdventureRun.start())
        assertEquals(SessionStatus.AWAITING_DECISION, waiting.status)
        assertEquals(3, waiting.currentMonth)
        assertEquals(Money.chf(6_360), waiting.currentState.cash)
        assertEquals(1, SimulationSessions.pendingDecisions(waiting).size)

        val stay = AdventureRun.preview(waiting, HousingDecision.Stay)
        val move = AdventureRun.preview(waiting, HousingDecision.Move)
        assertEquals(stay.before, move.before)
        assertEquals(Money.chf(756), stay.flow.rent)
        assertEquals(Money.chf(6_024), stay.after.cash)
        assertEquals(Money.chf(500), move.flow.rent)
        assertEquals(Money.chf(500), move.flow.decisionCosts)
        assertEquals(Money.chf(5_780), move.after.cash)
        assertTrue(waiting.selections.isEmpty())
    }

    @Test
    fun movingCostAndChoiceAreAppliedOnce() {
        val waiting = SimulationSessions.runUntilBlocked(AdventureRun.start())
        val moved = AdventureRun.choose(waiting, HousingDecision.Move)
        assertSame(moved, AdventureRun.choose(moved, HousingDecision.Stay))
        val finished = SimulationSessions.runUntilBlocked(moved)
        assertEquals(SessionStatus.COMPLETED, finished.status)
        assertEquals(1, finished.selections.size)
        assertEquals(1, finished.history.count { it.flow.decisionCosts != Money.ZERO })
        assertTrue(finished.history.drop(3).all { it.flow.rent == Money.chf(500) })
    }
}
