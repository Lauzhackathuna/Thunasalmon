package com.tuna.finlife.game

import com.tuna.finlife.simulation.EventApplication
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.PaymentResponsibility
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdventureRunTest {
    private fun advance(session: SimulationSession, months: Int): SimulationSession =
        (1..months).fold(session) { run, _ -> AdventureRun.advance(run) }

    private fun randomEvents(session: SimulationSession) = session.calendar.filter(RandomTroubles::isRandom)

    @Test
    fun aHitNeedsARollBelowOneFifth() {
        val hit = RandomTroubles.decide(0.1999, 0.1999, 5, "home", emptyList())
        assertEquals(2, hit.size)
        assertTrue(hit[0] is LifeEvent.RentRise && hit[1] is LifeEvent.Maintenance)
        assertTrue(RandomTroubles.decide(0.2, 0.2, 5, "home", emptyList()).isEmpty())
    }

    @Test
    fun drawnProblemsUseEngineDefaultsAndTheCurrentHome() {
        val (rent, maintenance) = RandomTroubles.decide(0.0, 0.0, 7, "flat", emptyList())
        rent as LifeEvent.RentRise
        maintenance as LifeEvent.Maintenance
        assertEquals(MonthlyRentRule("flat", 7), rent.rule)
        assertEquals(0.03, rent.rule.annualExtraRate)
        assertEquals(Money.chf(6_000), maintenance.nominalAmount)
        assertEquals(PaymentResponsibility.CHARACTER, maintenance.responsibility)
    }

    @Test
    fun capsApplyPerTwelveMonthBlock() {
        var calendar = emptyList<LifeEvent>()
        for (month in 0 until 24) calendar = calendar + RandomTroubles.decide(0.0, 0.0, month, "home", calendar)
        for (block in 0..1) {
            val inBlock = calendar.filter { it.month / 12 == block }
            assertEquals(1, inBlock.count { it is LifeEvent.RentRise })
            assertEquals(2, inBlock.count { it is LifeEvent.Maintenance })
        }
        // The first month of a new block can draw again.
        assertTrue(calendar.any { it is LifeEvent.RentRise && it.month == 12 })
    }

    @Test
    fun sameSeedGivesTheSameRunAndCapsHoldOverTenYears() {
        val first = advance(AdventureRun.start(seed = 42), 120)
        val second = advance(AdventureRun.start(seed = 42), 120)
        assertEquals(randomEvents(first), randomEvents(second))
        assertEquals(first.currentState, second.currentState)
        for ((_, inBlock) in randomEvents(first).groupBy { it.month / 12 }) {
            assertTrue(inBlock.count { it is LifeEvent.RentRise } <= 1)
            assertTrue(inBlock.count { it is LifeEvent.Maintenance } <= 2)
        }
    }

    @Test
    fun onlyTheFirstRentRisePerHomeTakesEffect() {
        val run = advance(AdventureRun.start(seed = 42), 120)
        val outcomes = run.history.flatMap { it.events }.associateBy { it.eventId }
        val rises = run.calendar.filterIsInstance<LifeEvent.RentRise>()
        assertTrue(rises.size >= 2, "seed 42 should draw several rent rises in ten years")
        assertEquals(EventApplication.APPLIED, outcomes.getValue(rises.first().id).application)
        assertTrue(rises.drop(1).all { outcomes.getValue(it.id).application == EventApplication.NOT_APPLICABLE })
    }

    @Test
    fun runIsOpenEndedAndHasNoScheduledDecision() {
        val run = advance(AdventureRun.start(seed = 7), 30)
        assertEquals(30, run.currentMonth)
        assertEquals(SessionStatus.READY, run.status)
        assertTrue(run.calendar.none { it.interactive })
    }

    @Test
    fun whatIfAddsADecisionInTheCurrentMonth() {
        val start = AdventureRun.start(seed = 3)
        assertEquals(WhatIf.entries, AdventureRun.availableWhatIfs(start))
        val waiting = AdventureRun.addWhatIf(start, WhatIf.SharedFlat)
        assertEquals(SessionStatus.AWAITING_DECISION, waiting.status)
        assertEquals(0, waiting.currentMonth)
        assertTrue(AdventureRun.availableWhatIfs(waiting).isEmpty())
        val pending = SimulationSessions.pendingDecisions(waiting).single()
        assertEquals(listOf("stay", "move"), pending.options.map { it.id })

        val moved = AdventureRun.choose(waiting, pending.event.id, "move")
        assertEquals(1, moved.currentMonth)
        assertEquals(Money.chf(500), moved.history.last().flow.rent)
        assertEquals(Money.chf(500), moved.history.last().flow.decisionCosts)
        assertEquals(HousingType.SHARED, moved.currentState.housing.type)
        assertFalse(WhatIf.SharedFlat in AdventureRun.availableWhatIfs(moved))
    }

    @Test
    fun previewMatchesTheChosenMonthAndChangesNothing() {
        val waiting = AdventureRun.addWhatIf(AdventureRun.start(seed = 3), WhatIf.Oven)
        val id = SimulationSessions.pendingDecisions(waiting).single().event.id
        val preview = AdventureRun.preview(waiting, id, "second")
        val chosen = AdventureRun.choose(waiting, id, "second")
        assertEquals(preview?.after, chosen.history.last().after)
        assertEquals(Money.chf(800), chosen.history.last().flow.decisionCosts)
        assertTrue(waiting.selections.isEmpty())
        assertSame(chosen, AdventureRun.choose(chosen, id, "first"))
    }

    @Test
    fun internshipWhatIfIsOnlyOfferedWhileStudying() {
        val start = AdventureRun.start(seed = 3)
        val waiting = AdventureRun.addWhatIf(start, WhatIf.Internship)
        val id = SimulationSessions.pendingDecisions(waiting).single().event.id
        val interning = AdventureRun.choose(waiting, id, "accept")
        assertFalse(WhatIf.Internship in AdventureRun.availableWhatIfs(interning))
        val graduated = advance(start, 24)
        assertFalse(WhatIf.Internship in AdventureRun.availableWhatIfs(graduated))
    }
}
