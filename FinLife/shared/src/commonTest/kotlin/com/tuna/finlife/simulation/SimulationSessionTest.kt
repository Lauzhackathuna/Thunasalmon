package com.tuna.finlife.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulationSessionTest {
    private fun configuration(months: Int = 6) = MonthlyConfiguration(horizonMonths = months, annualInflation = 0.0,
        randomEvents = RandomEventSettings(enabled = false))
    private fun start(events: List<LifeEvent> = emptyList()) = SimulationSessions.start(configuration = configuration(), events = events)

    @Test fun monthsWithoutEventsStillAdvanceAndProduceFullLedger() {
        val start = start()
        val result = SimulationSessions.runUntilBlocked(start)
        assertEquals(0, start.currentMonth)
        assertEquals(6, result.currentMonth)
        assertTrue(result.history.all { it.events.isEmpty() })
        assertEquals(Money.chf(6_000), result.currentState.cash)
    }

    @Test fun decisionBlocksWithoutApplyingAnyPartOfTheMonth() {
        val initial = start(listOf(MicroEvents.plant("plant", 2),
            LifeEvent.Maintenance("repair", 2, PaymentResponsibility.CHARACTER)))
        val blocked = SimulationSessions.runUntilBlocked(initial)
        assertEquals(2, blocked.currentMonth)
        assertEquals(2, blocked.history.size)
        assertEquals(SessionStatus.AWAITING_DECISION, blocked.status)
        assertEquals(blocked.states, SimulationSessions.advance(blocked).states)
        assertEquals(listOf("first", "second"), SimulationSessions.pendingDecisions(blocked).single().options.map { it.id })
        assertFailsWith<IllegalArgumentException> { SimulationSessions.submit(blocked, "plant", "invalid") }
        val answered = SimulationSessions.submit(blocked, "plant", "first")
        assertEquals(2, answered.currentMonth)
        val next = SimulationSessions.advance(answered)
        assertEquals(3, next.currentMonth)
        assertEquals(Money.chf(35), next.history.last().flow.decisionCosts)
        assertEquals(Money.chf(6_000), next.history.last().flow.exceptionalExpenses)
        assertEquals(Money.chf(565), next.currentState.cash)
    }

    @Test fun twoInteractiveMomentsAreAllowedButAThirdIsRejected() {
        var session = start(listOf(MicroEvents.plant("plant", 1), MicroEvents.oven("oven", 4, PaymentResponsibility.CHARACTER)))
        session = SimulationSessions.runUntilBlocked(session)
        assertEquals(1, session.currentMonth)
        session = SimulationSessions.runUntilBlocked(SimulationSessions.submit(session, "plant", "second"))
        assertEquals(4, session.currentMonth)
        session = SimulationSessions.runUntilBlocked(SimulationSessions.submit(session, "oven", "first"))
        assertEquals(6, session.currentMonth)
        assertEquals(Money.chf(5_550), session.currentState.cash)
        assertFailsWith<IllegalArgumentException> {
            start(listOf(MicroEvents.plant("a", 0), MicroEvents.plant("b", 2), MicroEvents.plant("c", 4)))
        }
    }

    @Test fun forkPreservesPrefixOriginalBranchAndExternalEvents() {
        var buy = SimulationSessions.runUntilBlocked(start(listOf(MicroEvents.plant("plant", 2))))
        buy = SimulationSessions.runUntilBlocked(SimulationSessions.submit(buy, "plant", "first"))
        val savedStates = buy.states
        val inspected = SimulationSessions.viewState(buy, 2)
        assertEquals(buy.states[2], inspected)
        assertEquals(savedStates, buy.states)
        var skip = SimulationSessions.fork(buy, 2, "skip-plant")
        assertEquals(buy.calendar, skip.calendar)
        assertEquals(buy.states.take(3), skip.states)
        skip = SimulationSessions.runUntilBlocked(SimulationSessions.submit(skip, "plant", "second"))
        assertEquals(savedStates, buy.states)
        assertEquals(buy.states.take(3), skip.states.take(3))
        assertEquals(Money.chf(5_965), buy.currentState.cash)
        assertEquals(Money.chf(6_000), skip.currentState.cash)
        val delta = MonthlyComparisons.compare(buy, skip, 6)
        assertEquals(Money.chf(-35), delta.cash)
        assertEquals(Money.chf(35), delta.expenses)
        assertEquals(Money.ZERO, delta.income)
        assertEquals(Money.chf(-35), delta.nominalNetWorth)
        assertEquals(Money.chf(-35), delta.realNetWorth)
        assertEquals("first", buy.selections.single().optionId)
        assertEquals("second", skip.selections.single().optionId)
    }

    @Test fun branchCanAddCompatibleFutureInterventionWithoutRetroactiveChanges() {
        val reference = SimulationSessions.runUntilBlocked(start())
        val branch = SimulationSessions.fork(reference, 3, "internship", listOf(LifeEvent.Internship("stage", 3, interactive = false)))
        val result = SimulationSessions.runUntilBlocked(branch)
        assertEquals(reference.states.take(4), result.states.take(4))
        assertEquals(Money.chf(9_900), result.currentState.cash)
        assertEquals(Money.chf(6_000), reference.currentState.cash)
        assertFailsWith<IllegalArgumentException> { SimulationSessions.fork(reference, 3, "invalid", listOf(MicroEvents.plant("past", 1))) }
    }

    @Test fun changingPastDecisionCanMakeFutureEventInapplicableWithoutRedraw() {
        val configuration = configuration(30)
        val events = listOf(LifeEvent.Internship("first", 0), LifeEvent.Internship("second", 3))
        var original = SimulationSessions.start(configuration = configuration, events = events)
        original = SimulationSessions.runUntilBlocked(SimulationSessions.submit(original, "first", "accept"))
        assertEquals(30, original.currentMonth)
        assertEquals(EventApplication.NOT_APPLICABLE, original.history[3].events.single().application)
        var alternative = SimulationSessions.fork(original, 0, "decline-first")
        alternative = SimulationSessions.runUntilBlocked(SimulationSessions.submit(alternative, "first", "decline"))
        assertEquals(3, alternative.currentMonth)
        assertEquals(original.calendar, alternative.calendar)
        alternative = SimulationSessions.runUntilBlocked(SimulationSessions.submit(alternative, "second", "accept"))
        assertEquals(EventApplication.APPLIED, alternative.history[3].events.single().application)
    }

    @Test fun sameSeedProducesSameCalendarAndForkDoesNotGenerateAgain() {
        val config = configuration().copy(randomSeed = 93, randomEvents = RandomEventSettings(enabled = true, opportunityProbability = 1.0))
        val a = SimulationSessions.start(configuration = config)
        val b = SimulationSessions.start(configuration = config)
        assertEquals(a.calendar, b.calendar)
        assertTrue(a.calendar.isNotEmpty())
        assertTrue(a.calendar.map { it.month }.distinct().size <= 2)
        val fork = SimulationSessions.fork(a, 0, "fork")
        assertEquals(a.calendar, fork.calendar)
        assertEquals(a.calendar, SimulationSessions.runUntilBlocked(a).calendar)
        assertTrue((0..100).map { seed -> EventCalendar.generate(MonthlyProfile.alexia(), config.copy(randomSeed = seed.toLong())) }.distinct().size > 1)
    }

    @Test fun generatedCalendarsAllowNoEventsAndNeverExceedTwoMomentsInSixMonths() {
        val zero = configuration().copy(randomEvents = RandomEventSettings(opportunityProbability = 0.0))
        assertTrue(SimulationSessions.start(configuration = zero).calendar.isEmpty())
        for (seed in 0..100) {
            val config = configuration().copy(randomSeed = seed.toLong(), randomEvents = RandomEventSettings(opportunityProbability = 1.0))
            val events = SimulationSessions.start(configuration = config).calendar
            assertTrue(events.all { it.month in 0..5 })
            assertTrue(events.filter { it.interactive }.map { it.month }.distinct().size <= 2)
            assertTrue(events.size < 6)
        }
    }

    @Test fun collectionMutationCannotChangeSessionOrAnotherBranch() {
        val external = mutableListOf<LifeEvent>(MicroEvents.plant("plant", 2), MicroEvents.plant("other", 4))
        val session = start(external)
        external.clear()
        assertEquals(2, session.calendar.size)
        val calendarCopy = session.calendar as MutableList<LifeEvent>
        calendarCopy.clear()
        val historyCopy = SimulationSessions.runUntilBlocked(session).history as MutableList<MonthlyRecord>
        historyCopy.clear()
        val branch = SimulationSessions.fork(session, 0, "branch")
        assertEquals(session.calendar, branch.calendar)
        assertEquals(2, branch.calendar.size)
        assertEquals(0, session.history.size)
    }

    @Test fun foreignResponsibilitySkipsDecisionAndMicroExpensesAreNotUbsScenarios() {
        val event = MicroEvents.oven("oven", 1, PaymentResponsibility.OTHER_PARTY)
        val result = SimulationSessions.runUntilBlocked(start(listOf(event)))
        assertEquals(6, result.currentMonth)
        assertEquals(Money.chf(6_000), result.currentState.cash)
        assertEquals(EventApplication.NOT_APPLICABLE, result.history[1].events.single().application)
        assertEquals(null, event.scenarioId)
        assertEquals(DataProvenance.TEAM_ASSUMPTION, event.definitionProvenance)
        assertEquals(DataProvenance.CHALLENGE_SCENARIO_DEFINITION,
            LifeEvent.Maintenance("S040", 0, PaymentResponsibility.CHARACTER).definitionProvenance)
    }

    @Test fun repeatedRunsAndExplanationsUseAppliedLedger() {
        val input = start(listOf(MicroEvents.transport("transport", 3, Money.chf(20), PaymentResponsibility.CHARACTER)))
        val a = SimulationSessions.runUntilBlocked(input)
        val b = SimulationSessions.runUntilBlocked(input)
        assertEquals(a.states, b.states)
        assertEquals(a.history.map { it.flow }, b.history.map { it.flow })
        assertEquals(a.history.map { it.explanation() }, b.history.map { it.explanation() })
        assertEquals(Money.chf(20), a.history[3].events.single().chargedCost)
        assertEquals(Money.chf(20), a.history[3].flow.exceptionalExpenses)
        assertFalse(a.history[3].needsFinancing)
    }
    @Test fun allDecisionsInSameMonthMustBeResolvedBeforeAnyCharge() {
        val session = start(listOf(MicroEvents.plant("a", 0), MicroEvents.plant("b", 0)))
        val oneAnswer = SimulationSessions.submit(session, "a", "first")
        assertEquals(SessionStatus.AWAITING_DECISION, oneAnswer.status)
        assertEquals(0, SimulationSessions.advance(oneAnswer).currentMonth)
        val ready = SimulationSessions.submit(oneAnswer, "b", "second")
        val result = SimulationSessions.advance(ready)
        assertEquals(Money.chf(6_965), result.currentState.cash)
        assertEquals(Money.chf(35), result.history.single().flow.decisionCosts)
        assertEquals(2, result.history.single().decisions.size)
    }

}
