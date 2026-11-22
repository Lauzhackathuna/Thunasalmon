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
            session = SimulationSessions.advance(session)
            assertEquals(Money.ZERO, session.history.last().flow.professionalIncome, "Break month $i")
        }
        session = SimulationSessions.advance(session)
        assertEquals(Money.chf(4_500), session.history.last().flow.professionalIncome)
        assertFalse(session.currentState.jobLost)
    }

    @Test
    fun epflChoicePaysTheOfferedSalaryAndHasNoCompanyClosureRisk() {
        val offered = reachOffer()
        val event = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        assertEquals(0.10, (event.event as LifeEvent.EpflJobOffer).monthlyFailureProbability)
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

        // Nothing is pre-drawn twelve months in advance; only a failed CURRENT month appears.
        assertTrue(kept.calendar.filterIsInstance<LifeEvent.CompanyClosure>()
            .all { it.month <= kept.currentMonth })
        assertEquals(EmployerType.PRIVATE, kept.currentState.employerType)
        assertFalse(kept.currentState.jobLost)
    }

    @Test
    fun closureZeroesFutureWagesButKeepsCashUntilActualBillsArePaid() {
        val employed = reachOffer()
        val event = SimulationSessions.pendingDecisions(employed).single { it.event is LifeEvent.EpflJobOffer }
        var session = AdventureRun.choose(employed, event.event.id, "stay")
        // Explicit scenario independently tests failure, regardless of the monthly 10% draw.
        if (session.currentMonth == employed.currentMonth) session = finishOtherDecisions(session)
        val month = session.currentMonth
        session = SimulationSessions.schedule(session, LifeEvent.CompanyClosure("forced-closure", month, event.event.id))
        val before = session.currentState.cash
        session = SimulationSessions.advance(session)
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
        val other = requireNotNull(comparison)
        assertTrue(other.alternative.calendar.any { it is LifeEvent.EpflJobOffer })
        assertEquals(EmployerType.PRIVATE, other.alternative.currentState.employerType)
    }

    @Test
    fun monthlyCompanyFailureRollsAreReproducibleAndCloseToTenPercent() {
        val seed = 2026L
        val count = (1..10_000).count { month -> RandomTroubles.privateCompanyFails(seed, month) }
        // Wide statistical tolerance: detects accidental annual-only, 30%, or no-risk logic.
        assertTrue(count in 850..1150, "Expected about 10% failures, found $count/10000")
        for (month in 1..1_200) {
            assertEquals(RandomTroubles.privateCompanyFails(seed, month),
                RandomTroubles.privateCompanyFails(seed, month))
        }
        assertTrue((30..100).any { RandomTroubles.privateCompanyFails(seed, it) })
    }

    @Test
    fun keepingPrivateJobRollsEveryMonthUntilClosureWithoutPredrawingFutureEvents() {
        val seed = 19L
        val offered = reachOffer(seed)
        val offer = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        var session = AdventureRun.choose(offered, offer.event.id, "stay")
        // Resolve any other decisions at the offer month, to enter the FIRST month at risk.
        while (session.currentMonth == offered.currentMonth) {
            val pending = SimulationSessions.pendingDecisions(session).firstOrNull()
            session = if (pending != null)
                AdventureRun.choose(session, pending.event.id, requireNotNull(AdventureRun.defaultAnswer(pending.event)))
            else AdventureRun.advance(session)
        }
        var monthsChecked = 0
        while (!session.currentState.jobLost && monthsChecked < 120) {
            val month = session.currentMonth
            val closedThisMonth = session.calendar.any { it is LifeEvent.CompanyClosure && it.month == month }
            assertEquals(RandomTroubles.privateCompanyFails(seed, month), closedThisMonth,
                "Month $month must independently evaluate the 10% hazard")
            assertTrue(session.calendar.filterIsInstance<LifeEvent.CompanyClosure>().none { it.month > month },
                "Future company closures must not be revealed or scheduled")
            while (session.currentMonth == month) {
                val pending = SimulationSessions.pendingDecisions(session).firstOrNull()
                session = if (pending != null)
                    AdventureRun.choose(session, pending.event.id, requireNotNull(AdventureRun.defaultAnswer(pending.event)))
                else AdventureRun.advance(session)
            }
            monthsChecked++
        }
        assertTrue(session.currentState.jobLost, "Company failure should eventually occur in this seeded run")
        assertEquals(Money.ZERO, session.history.last().flow.professionalIncome)
        assertTrue(session.currentState.cash >= Money.ZERO)
    }

    @Test
    fun epflJobIsNotSubjectToMonthlyPrivateCompanyHazard() {
        val offered = reachOffer(19L)
        val offer = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        var session = AdventureRun.choose(offered, offer.event.id, "epfl")
        while (session.currentMonth < offered.currentMonth + 20) {
            val pending = SimulationSessions.pendingDecisions(session).firstOrNull()
            session = if (pending != null)
                AdventureRun.choose(session, pending.event.id, requireNotNull(AdventureRun.defaultAnswer(pending.event)))
            else AdventureRun.advance(session)
        }
        assertEquals(EmployerType.EPFL, session.currentState.employerType)
        assertFalse(session.currentState.jobLost)
        assertTrue(session.calendar.none { it is LifeEvent.CompanyClosure })
    }

    @Test
    fun legacyPredrawnCompanyClosureIsDiscardedWhenRestoringOldSave() {
        val offered = reachOffer(19L)
        val offer = SimulationSessions.pendingDecisions(offered).single { it.event is LifeEvent.EpflJobOffer }
        var session = AdventureRun.choose(offered, offer.event.id, "stay")
        val original = JourneySave.encode(StartingData.Default, session, null, false, "English", "System", "Alex")
        val lines = original.substringBeforeLast("\nHASH|").lines().map { line ->
            when {
                line == "FINLIFE|3" -> "FINLIFE|2"
                line.contains("|epfl|") && line.startsWith("E|") -> line + "|0.3|12"
                else -> line
            }
        }.toMutableList()
        fun hex(value: String) = value.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
        lines += "E|${hex("legacy-future-closure")}|${session.currentMonth + 5}|closure|${hex(offer.event.id)}"
        val body = lines.joinToString("\n")
        var hash = 2166136261u
        body.forEach { hash = (hash xor it.code.toUInt()) * 16777619u }
        val restored = JourneySave.decode(body + "\nHASH|" + hash.toString(16))
        assertEquals(0.10, restored.session.calendar.filterIsInstance<LifeEvent.EpflJobOffer>()
            .single().monthlyFailureProbability)
        assertTrue(restored.session.calendar.none { it.id == "legacy-future-closure" })
        assertEquals(session.currentState, restored.session.currentState)
    }


}
