package com.tuna.finlife.game

import com.tuna.finlife.simulation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CareerScenariosTest {
    /** Resolve unrelated rent-rise choices and stop at the first professional job offer. */
    private fun reachOffer(seed: Long = 19L): SimulationSession {
        var session = AdventureRun.start(seed = seed, playerName = "Alex")
        repeat(100) {
            val pending = SimulationSessions.pendingDecisions(session)
            if (pending.any { it.event is LifeEvent.EpflJobOffer }) return session
            session = if (pending.isNotEmpty()) {
                val choice = pending.first()
                AdventureRun.choose(session, choice.event.id, requireNotNull(AdventureRun.defaultAnswer(choice.event)))
            } else AdventureRun.advance(session)
        }
        error("No job offer was scheduled after graduation")
    }

    private fun finishOtherDecisions(current: SimulationSession): SimulationSession {
        var session = current
        while (SimulationSessions.pendingDecisions(session).isNotEmpty()) {
            val decision = SimulationSessions.pendingDecisions(session).first()
            session = AdventureRun.choose(session, decision.event.id, requireNotNull(AdventureRun.defaultAnswer(decision.event)))
        }
        return session
    }

    @Test
    fun careerBreakIsUnavailableDuringStudiesAndStopsExactlySixProfessionalSalaries() {
        val student = AdventureRun.start(seed = 19L)
        assertEquals(WhatIfState.Unavailable, AdventureRun.whatIfStates(student)[WhatIf.CareerBreak])
        val offered = reachOffer()
        val offer = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        var session = finishOtherDecisions(AdventureRun.choose(offered, offer.event.id, "epfl"))
        assertEquals(EmployerType.EPFL, session.currentState.employerType)
        assertEquals(WhatIfState.Available, AdventureRun.whatIfStates(session)[WhatIf.CareerBreak])

        session = WhatIfDraft(session).toggle(WhatIf.CareerBreak, true).session
        for (i in 1..6) {
            session = finishOtherDecisions(AdventureRun.advance(session))
            assertEquals(Money.ZERO, session.history.last().flow.professionalIncome, "Break month $i")
        }
        session = finishOtherDecisions(AdventureRun.advance(session))
        assertEquals(Money.chf(4_500), session.history.last().flow.professionalIncome)
        assertFalse(session.currentState.jobLost)
    }

    @Test
    fun epflChoicePaysTheOfferedSalaryAndHasNoCompanyClosureRisk() {
        val offered = reachOffer()
        val event = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        assertEquals(0.30, (event.event as LifeEvent.EpflJobOffer).privateCompanyFailureProbability)
        val accepted = finishOtherDecisions(AdventureRun.choose(offered, event.event.id, "epfl"))
        assertEquals(EmployerType.EPFL, accepted.currentState.employerType)
        assertEquals(Money.chf(4_500), accepted.history.last().flow.professionalIncome)
        assertTrue(accepted.calendar.none { it is LifeEvent.CompanyClosure })
        assertTrue(accepted.currentState.cash >= Money.ZERO)
    }

    @Test
    fun privateJobRiskIsDeterministicAndNeverDeletesSavings() {
        val offered = reachOffer(seed = 22L)
        val event = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        val kept = AdventureRun.choose(offered, event.event.id, "stay")
        val saved = JourneySave.encode(StartingData.Default, kept, null, false, "English", "System", "Alex")
        val restored = JourneySave.decode(saved)
        assertEquals(kept.calendar, restored.session.calendar)
        assertEquals(kept.currentState, restored.session.currentState)

        // Risk draw is a separate event in a future month, never an immediate deduction from cash.
        assertTrue(kept.calendar.filterIsInstance<LifeEvent.CompanyClosure>().all { it.month in (offered.currentMonth + 1)..(offered.currentMonth + 12) })
        assertEquals(EmployerType.PRIVATE, kept.currentState.employerType)
        assertFalse(kept.currentState.jobLost)
    }

    @Test
    fun closureZeroesFutureWagesButKeepsCashUntilActualBillsArePaid() {
        val employed = reachOffer()
        val event = SimulationSessions.pendingDecisions(employed).single { it.event is LifeEvent.EpflJobOffer }
        var session = AdventureRun.choose(employed, event.event.id, "stay")
        // Explicit scenario independently tests failure, regardless of the seeded 30% draw.
        if (session.currentMonth == employed.currentMonth) session = finishOtherDecisions(session)
        val month = session.currentMonth
        session = SimulationSessions.schedule(session, LifeEvent.CompanyClosure("forced-closure", month, event.event.id))
        val before = session.currentState.cash
        session = finishOtherDecisions(AdventureRun.advance(session))
        assertTrue(session.currentState.jobLost)
        assertEquals(Money.ZERO, session.history.last().flow.professionalIncome)
        assertTrue(session.currentState.cash <= before)
        assertTrue(session.currentState.cash >= Money.ZERO)
    }

    @Test
    fun counterfactualOfEpflChoicePreservesOtherCareerOption() {
        val offered = reachOffer()
        val event = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        val selected = AdventureRun.choose(offered, event.event.id, "epfl")
        val comparison = Counterfactual.afterChoice(offered, event.event.id, "epfl", selected)
        assertTrue(comparison != null)
        assertTrue(comparison.alternative.calendar.any { it is LifeEvent.EpflJobOffer })
        assertEquals(EmployerType.PRIVATE, comparison.alternative.currentState.employerType)
    }
}
