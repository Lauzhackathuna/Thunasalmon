package com.tuna.finlife.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthlyScenarioTest {
    private fun config(months: Int) = MonthlyConfiguration(horizonMonths = months, annualInflation = 0.0,
        randomEvents = RandomEventSettings(enabled = false))
    private fun run(months: Int, events: List<LifeEvent>, configuration: MonthlyConfiguration = config(months)) =
        runAcceptingRent(SimulationSessions.start(configuration = configuration, events = events))

    private fun runAcceptingRent(input: SimulationSession): SimulationSession {
        var session = SimulationSessions.runUntilBlocked(input)
        while (SimulationSessions.pendingDecisions(session).any { it.event is LifeEvent.RentRise }) {
            val pending = SimulationSessions.pendingDecisions(session).first { it.event is LifeEvent.RentRise }
            session = SimulationSessions.runUntilBlocked(SimulationSessions.submit(session, pending.event.id, "stay"))
        }
        return session
    }

    @Test fun internshipAtMonthThreeSuspendsAndResumesStudiesWithoutDoubleBilling() {
        val session = run(49, listOf(LifeEvent.Internship("S005", 3, interactive = false)))
        assertEquals(3, session.states[3].studyMonthsCompleted)
        assertEquals(21, session.states[3].studyMonthsRemaining)
        for (month in 3..14) {
            val record = session.history[month]
            assertEquals(Money.chf(2_500), record.flow.internshipIncome)
            assertEquals(Money.ZERO, record.flow.studentIncome)
            assertEquals(Money.ZERO, record.flow.professionalIncome)
            assertEquals(Money.ZERO, record.flow.tuition)
            assertEquals(3, record.after.studyMonthsCompleted)
        }
        assertEquals(LifeStatus.STUDENT, session.states[15].status)
        assertEquals(21, session.states[15].studyMonthsRemaining)
        assertEquals(listOf(0, 18, 24, 30), session.history.filter { it.flow.tuition > Money.ZERO }.map { it.month })
        assertEquals(Money.chf(3_200), session.history.map { it.flow.tuition }.total())
        assertEquals(36, session.states[36].careerStartMonth)
        assertEquals(Money.chf(6_000), session.history[36].flow.professionalIncome)
        assertEquals(Money.chf(6_120), session.history[48].flow.professionalIncome)
        assertEquals(Money.chf(800), session.history[0].flow.tuition)
    }

    @Test fun internshipAtTuitionDeadlineDefersUnpaidBillAndShortHorizonDoesNotForecast() {
        val session = run(20, listOf(LifeEvent.Internship("S005", 6, interactive = false)))
        assertEquals(Money.ZERO, session.history[6].flow.tuition)
        assertEquals(Money.chf(800), session.history[18].flow.tuition)
        val short = run(6, listOf(LifeEvent.Internship("S005", 3, interactive = false)))
        assertEquals(9, short.currentState.internshipMonthsRemaining)
        assertEquals(21, short.currentState.studyMonthsRemaining)
        assertEquals(7, short.states.size)
        assertTrue(short.unsimulatedConsequences().any { it.contains("9 mois de stage") })
    }

    @Test fun internshipIsUnavailableWhenEmployedOrAlreadyInterning() {
        val events = listOf(LifeEvent.Internship("first", 0, interactive = false),
            LifeEvent.Internship("overlap", 3), LifeEvent.Internship("too-late", 37))
        val session = run(39, events)
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(EventApplication.NOT_APPLICABLE, session.history[3].events.single().application)
        assertEquals(EventApplication.NOT_APPLICABLE, session.history[37].events.single().application)
    }

    @Test fun rentRisesOnlyAtStartAndAnnualAnniversary() {
        val session = run(28, listOf(LifeEvent.RentRise("S037", 2, MonthlyRentRule("initial-home", 2))))
        assertEquals(Money.chf(1000), session.history[1].flow.rent)
        assertEquals(Money.chf(1030), session.history[2].flow.rent)
        assertEquals(Money.chf(1030), session.history[13].flow.rent)
        assertEquals(Money(106_090), session.history[14].flow.rent)
        assertEquals(Money(106_090), session.history[25].flow.rent)
        assertEquals(Money(109_273), session.history[26].flow.rent)
        val inflated = run(15, listOf(LifeEvent.RentRise("S037", 2, MonthlyRentRule("initial-home", 2))),
            config(15).copy(annualInflation = 0.02))
        assertEquals(Money.chf(1000).scaled(1.02 * 1.03), inflated.history[12].flow.rent)
    }

    @Test fun repairIsUniqueNominalAndRequiresExplicitPaymentResponsibility() {
        val configuration = config(6).copy(annualInflation = 0.1)
        val reference = run(6, emptyList(), configuration)
        val repair = run(6, listOf(LifeEvent.Maintenance("S040", 4, PaymentResponsibility.CHARACTER)), configuration)
        // Known future repairs change earmarking before they change cash or wealth.
        assertEquals(reference.states.take(5).map { it.cash }, repair.states.take(5).map { it.cash })
        assertEquals(reference.states.take(5).map { it.netWorth }, repair.states.take(5).map { it.netWorth })
        assertEquals(Money.chf(6_000), repair.history[4].flow.exceptionalExpenses)
        assertEquals(1, repair.history.count { it.flow.exceptionalExpenses > Money.ZERO })
        assertEquals(Money.chf(-6_000), repair.currentState.netWorth - reference.currentState.netWorth)
        assertTrue(repair.states.all { it.debt == Money.ZERO })
        assertTrue(repair.history.all { it.flow.newBorrowing == Money.ZERO })
        val otherParty = run(6, listOf(LifeEvent.Maintenance("S040", 4, PaymentResponsibility.OTHER_PARTY)), configuration)
        assertEquals(reference.states, otherParty.states)
        assertEquals(EventApplication.NOT_APPLICABLE, otherParty.history[4].events.single().application)
    }

    @Test fun housingDecisionPersistsButDoesNotInheritOldContractRentRise() {
        val events = listOf(LifeEvent.RentRise("S037", 0, MonthlyRentRule("initial-home", 0)),
            LifeEvent.HousingChoice("move", 2, HousingContract("new-home", Money.chf(500), 2),
                Money.chf(100), PaymentResponsibility.CHARACTER),
            LifeEvent.RentRise("old-contract", 3, MonthlyRentRule("initial-home", 3)))
        var session = run(6, events)
        assertEquals(2, session.currentMonth)
        session = SimulationSessions.submit(session, "move", "move")
        session = SimulationSessions.runUntilBlocked(session)
        assertEquals(Money.chf(1030), session.history[1].flow.rent)
        assertEquals(Money.chf(500), session.history[2].flow.rent)
        assertEquals(Money.chf(100), session.history[2].flow.decisionCosts)
        assertEquals(Money.chf(500), session.history[5].flow.rent)
        assertEquals(EventApplication.NOT_APPLICABLE, session.history[3].events.single().application)
        assertEquals(null, session.currentState.rentRule)
    }

    @Test fun newContractCanHaveItsOwnExplicitRentRule() {
        val event = LifeEvent.HousingChoice("move", 1, HousingContract("new", Money.chf(500), 1), Money.ZERO,
            PaymentResponsibility.CHARACTER, MonthlyRentRule("new", 2, 0.1))
        var session = run(4, listOf(event))
        session = SimulationSessions.runUntilBlocked(SimulationSessions.submit(session, "move", "move"))
        assertEquals(Money.chf(500), session.history[1].flow.rent)
        assertEquals(Money.chf(550), session.history[2].flow.rent)
    }

    @Test fun allScenarioCombinationsReconcileAndInteractionsAreNotSummed() {
        val configuration = config(48).copy(investmentShare = 0.5, annualInflation = 0.02)
        val results = (0..7).map { mask ->
            val events = buildList {
                if (mask and 1 != 0) add(LifeEvent.Internship("S005", 3, interactive = false))
                if (mask and 2 != 0) add(LifeEvent.RentRise("S037", 2, MonthlyRentRule("initial-home", 2)))
                if (mask and 4 != 0) add(LifeEvent.Maintenance("S040", 4, PaymentResponsibility.CHARACTER))
            }
            run(48, events, configuration)
        }
        results.forEach { result ->
            assertEquals(48, result.history.size)
            result.history.forEach { assertEquals(it.flow.netWorthChange, it.after.netWorth - it.before.netWorth) }
        }
        val joint = results[7].currentState.netWorth - results[0].currentState.netWorth
        val summed = listOf(1, 2, 4).map { results[it].currentState.netWorth - results[0].currentState.netWorth }.total()
        assertFalse(joint == summed)
    }
}
