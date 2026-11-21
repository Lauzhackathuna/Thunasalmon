package com.tuna.finlife.game

import com.tuna.finlife.simulation.EventApplication
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeStatus
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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdventureRunTest {
    @Test
    fun expensesIndexAtTwoPercentAndSalariesStayFlat() {
        val run = playDefaults(AdventureRun.start(seed = 42), 37)
        assertEquals(Money.chf(StartingData.Default.groceries).scaled(1.02), run.history[12].flow.groceries)
        assertEquals(run.history[24].flow.professionalIncome, run.history[36].flow.professionalIncome)
        assertEquals(Money.chf(StartingData.Default.salaryAfterStudies), run.history[36].flow.professionalIncome)
    }

    @Test
    fun editingReplaysTheSameMonthsAndChoicesWithRevisedAmounts() {
        val start = AdventureRun.start(seed = 3)
        val original = playDefaults(AdventureRun.applyWhatIf(start, WhatIf.Internship), 4)
        val data = StartingData.Default.copy(internshipSalary = 1_700, groceries = 200)
        val revised = assertNotNull(AdventureRun.recalculate(original, data))

        assertEquals(original.currentMonth, revised.currentMonth)
        assertEquals(original.selections.toSet(), revised.selections.toSet())
        assertEquals(original.calendar.map { it.id }, revised.calendar.map { it.id })
        assertEquals(Money.chf(1_700), revised.history.first().flow.internshipIncome)
        assertEquals(Money.chf(200), revised.history.first().flow.groceries)
        assertEquals(Money.chf(1_500), original.history.first().flow.internshipIncome)
    }

    private fun advance(session: SimulationSession, months: Int): SimulationSession = playDefaults(session, months)

    private fun randomEvents(session: SimulationSession) = session.calendar.filter(RandomTroubles::isRandom)

    @Test
    fun rentAndMaintenanceUseSeparateMonthlyThresholds() {
        val hit = RandomTroubles.decide(0.1999, 1.0 / 12.0 - 0.0001, 5, "home", emptyList())
        assertEquals(2, hit.size)
        assertTrue(hit[0] is LifeEvent.RentRise && hit[1] is LifeEvent.Maintenance)
        assertTrue(RandomTroubles.decide(0.2, 1.0 / 12.0, 5, "home", emptyList()).isEmpty())
    }

    @Test
    fun drawnProblemsUseTheCurrentHomeAndTheDemoRepairCost() {
        val (rent, maintenance) = RandomTroubles.decide(0.0, 0.0, 7, "flat", emptyList())
        rent as LifeEvent.RentRise
        maintenance as LifeEvent.Maintenance
        assertEquals(MonthlyRentRule("flat", 7), rent.rule)
        assertEquals(0.03, rent.rule.annualExtraRate)
        assertEquals(Money.chf(400), maintenance.nominalAmount)
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
    fun runIsOpenEndedAndOnlyRandomRentRisesAskForADecision() {
        val run = advance(AdventureRun.start(seed = 7), 30)
        assertEquals(30, run.currentMonth)
        assertTrue(run.calendar.filter { it.interactive }.all { it is LifeEvent.RentRise && RandomTroubles.isRandom(it) })
    }

    @Test
    fun aDrawnRentRiseIsAskedBeforeTheMonthIsPlayed() {
        val calendar = RandomTroubles.decide(0.0, 0.99, 0, "maya-home", emptyList())
        assertTrue(calendar.single() is LifeEvent.RentRise)
        // Find a run whose first month draws a rent rise, then check the app stops on it.
        val run = (1L..200L).map { AdventureRun.start(seed = it) }.first { s -> s.calendar.any { it is LifeEvent.RentRise } }
        assertEquals(SessionStatus.AWAITING_DECISION, run.status)
        assertEquals(listOf("stay", "move"), SimulationSessions.pendingDecisions(run).single().options.map { it.id })
        assertEquals(WhatIfState.Unavailable, AdventureRun.whatIfStates(run)[WhatIf.SharedFlat])
    }

    @Test
    fun sharedFlatToggleMovesMayaWithoutADecisionScreen() {
        val start = AdventureRun.start(seed = 3)
        assertEquals(WhatIf.entries.associateWith { WhatIfState.Available }, AdventureRun.whatIfStates(start))
        val switchedOn = AdventureRun.applyWhatIf(start, WhatIf.SharedFlat)
        assertEquals(SessionStatus.READY, switchedOn.status)
        assertTrue(SimulationSessions.pendingDecisions(switchedOn).isEmpty())
        assertEquals(WhatIfState.Active, AdventureRun.whatIfStates(switchedOn)[WhatIf.SharedFlat])
        assertSame(switchedOn, AdventureRun.applyWhatIf(switchedOn, WhatIf.SharedFlat))

        val moved = AdventureRun.advance(switchedOn)
        assertEquals(1, moved.currentMonth)
        assertEquals(Money.chf(StartingData.Default.sharedFlatRent), moved.history.last().flow.rent)
        assertEquals(Money.chf(StartingData.Default.sharedFlatMovingCost), moved.history.last().flow.decisionCosts)
        assertEquals(HousingType.SHARED, moved.currentState.housing.type)
        // Still on in later months: the engine has no way back.
        assertEquals(WhatIfState.Active, AdventureRun.whatIfStates(advance(moved, 5))[WhatIf.SharedFlat])
    }

    @Test
    fun internshipIsTheEnginesThreeMonthOfferAlongsideStudies() {
        val start = AdventureRun.start(seed = 3)
        val month1 = AdventureRun.advance(AdventureRun.applyWhatIf(start, WhatIf.Internship))
        // The demo salary is added to student income; studies continue.
        assertEquals(LifeStatus.STUDENT, month1.currentState.status)
        assertEquals(Money.chf(1_500), month1.history.last().flow.internshipIncome)
        assertEquals(Money.chf(StartingData.Default.parentIncome), month1.history.last().flow.studentIncome)
        assertEquals(WhatIfState.Active, AdventureRun.whatIfStates(month1)[WhatIf.Internship])
        // Exactly three paid months, then the switch can be used again.
        val month3 = advance(month1, 2)
        assertEquals(listOf(1_500L, 1_500L, 1_500L), month3.history.map { it.flow.internshipIncome.cents / 100 })
        assertEquals(Money.ZERO, advance(month3, 1).history.last().flow.internshipIncome)
        assertEquals(WhatIfState.Available, AdventureRun.whatIfStates(answerDefaults(advance(month3, 1)))[WhatIf.Internship])

        val graduated = advance(start, 24)
        assertEquals(WhatIfState.Unavailable, AdventureRun.whatIfStates(graduated)[WhatIf.Internship])
        assertSame(graduated, AdventureRun.applyWhatIf(graduated, WhatIf.Internship))
    }

    @Test
    fun genericPreviewMatchesTheChosenMonthAndChangesNothing() {
        val waiting = SimulationSessions.advance(
            SimulationSessions.start(
                configuration = com.tuna.finlife.simulation.MonthlyConfiguration(
                    horizonMonths = 12,
                    randomEvents = com.tuna.finlife.simulation.RandomEventSettings(enabled = false),
                ),
                events = listOf(LifeEvent.Internship("offer", 0)),
            ),
        )
        val preview = AdventureRun.preview(waiting, "offer", "accept")
        val chosen = AdventureRun.choose(waiting, "offer", "accept")
        assertEquals(preview?.after, chosen.history.last().after)
        assertTrue(waiting.selections.isEmpty())
        assertSame(chosen, AdventureRun.choose(chosen, "offer", "decline"))
    }

    @Test
    fun switchesCanBeTurnedOffUntilTheMonthIsAdvanced() {
        val base = AdventureRun.start(seed = 3)
        val on = WhatIfDraft(base).toggle(WhatIf.SharedFlat, true)
        assertTrue(on.isOn(WhatIf.SharedFlat))
        assertTrue(on.canSwitch(WhatIf.SharedFlat))
        val off = on.toggle(WhatIf.SharedFlat, false)
        assertFalse(off.isOn(WhatIf.SharedFlat))
        assertSame(base, off.session)
        assertEquals(Money.chf(StartingData.Default.rent), AdventureRun.advance(off.session).history.last().flow.rent)
    }

    @Test
    fun bothWhatIfsTogetherKeepTheMoveAnswer() {
        val both = WhatIfDraft(AdventureRun.start(seed = 3))
            .toggle(WhatIf.SharedFlat, true)
            .toggle(WhatIf.Internship, true)
        assertTrue(SimulationSessions.pendingDecisions(both.session).isEmpty())
        val next = AdventureRun.advance(both.session)
        assertEquals(1, next.currentMonth)
        assertEquals(Money.chf(1_500), next.history.last().flow.internshipIncome)
        assertEquals(HousingType.SHARED, next.currentState.housing.type)
    }

    @Test
    fun aWhatIfFromAnEarlierMonthIsLockedOn() {
        val moved = AdventureRun.advance(WhatIfDraft(AdventureRun.start(seed = 3)).toggle(WhatIf.SharedFlat, true).session)
        val draft = WhatIfDraft(moved)
        assertTrue(draft.isOn(WhatIf.SharedFlat))
        assertFalse(draft.canSwitch(WhatIf.SharedFlat))
        assertEquals(draft, draft.toggle(WhatIf.SharedFlat, true))
    }
}
