package com.tuna.finlife.simulation

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthlyFinancialTest {
    private fun config(months: Int = 12) = MonthlyConfiguration(horizonMonths = months, annualInflation = 0.0,
        randomEvents = RandomEventSettings(enabled = false))
    private fun simulate(months: Int = 12, profile: MonthlyProfile = MonthlyProfile.alexia(),
        configuration: MonthlyConfiguration = config(months), events: List<LifeEvent> = emptyList()): SimulationSession =
        SimulationSessions.runUntilBlocked(SimulationSessions.start(profile, configuration, events))

    @Test fun initialConditionsAndRequiredCashExamplesAreExact() {
        val session = simulate()
        assertEquals(13, session.states.size)
        assertEquals(12, session.history.size)
        assertEquals(Money.chf(8_000), session.states[0].cash)
        assertEquals(Money.chf(6_600), session.states[1].cash)
        assertEquals(Money.chf(6_000), session.states[2].cash)
        assertEquals(Money.chf(3_600), session.states[6].cash)
        assertEquals(Money.chf(2_200), session.states[7].cash)
        assertEquals(Money.ZERO, session.states[12].cash)
        assertEquals(listOf(0, 6), session.history.filter { it.flow.tuition > Money.ZERO }.map { it.month })
        session.history.forEach {
            assertEquals(Money.chf(1_200), it.flow.studentIncome)
            assertEquals(Money.chf(1_800), it.flow.recurringExpenses)
        }
        assertEquals(HousingType.ALONE, session.profile.housing.type)
    }

    @Test fun horizonsHaveNTransitionsAndNPlusOneStates() {
        for (months in listOf(1, 6, 12, 24, 120, 121)) {
            val session = simulate(months)
            assertEquals(months, session.history.size)
            assertEquals(months + 1, session.states.size)
            assertEquals(SessionStatus.COMPLETED, session.status)
        }
        assertFailsWith<IllegalArgumentException> { config(0) }
        assertFailsWith<IllegalArgumentException> { config(1_201) }
    }

    @Test fun studyCompletionAndSalaryAnniversariesAreNominal() {
        val session = simulate(38, configuration = config(38).copy(annualInflation = 0.1))
        assertEquals(LifeStatus.STUDENT, session.states[23].status)
        assertEquals(LifeStatus.EMPLOYED, session.states[24].status)
        assertEquals(24, session.states[24].careerStartMonth)
        assertEquals(Money.chf(6_000), session.history[24].flow.professionalIncome)
        assertEquals(Money.chf(6_000), session.history[35].flow.professionalIncome)
        assertEquals(Money.chf(6_120), session.history[36].flow.professionalIncome)
        assertEquals(listOf(0, 6, 12, 18), session.history.filter { it.flow.tuition > Money.ZERO }.map { it.month })
        assertTrue(session.history.drop(24).all { it.flow.studentIncome == Money.ZERO && it.flow.tuition == Money.ZERO })
    }

    @Test fun inflationUsesBaseCostsAndRealWealthUsesStateDate() {
        val session = simulate(13, configuration = config(13).copy(annualInflation = 0.02))
        val month12 = session.history[12].flow
        assertEquals(Money.chf(1020), month12.rent)
        assertEquals(Money.chf(255), month12.healthInsurance)
        assertEquals(Money.chf(255), month12.groceries)
        assertEquals(Money.chf(306), month12.otherExpenses)
        assertEquals(Money.chf(816), month12.tuition)
        assertEquals(Money.chf(1_200), month12.studentIncome)
        assertEquals(session.states[12].netWorth.scaled(1.0 / 1.02), session.states[12].realNetWorth)
        assertEquals(Money.chf(1000).scaled(1.02.pow(1.0 / 12)), session.history[1].flow.rent)
    }

    @Test fun effectiveMonthlyReturnsAndCentRoundingAreCorrect() {
        for (profile in InvestmentProfile.entries) {
            val rate = FinancialSimulation.effectiveMonthlyRate(profile.annualReturn)
            assertEquals(1.0 + profile.annualReturn, (1.0 + rate).pow(12), 1e-12)
        }
        val profile = MonthlyProfile.alexia().copy(investments = Money.chf(1_000))
        val session = simulate(1, profile = profile)
        assertEquals(Money(247), session.history[0].flow.investmentReturn)
        assertEquals(Money(100_247), session.currentState.investments)
        assertEquals(Money(1), Money(1).scaled(0.5))
        assertEquals(Money(-1), Money(-1).scaled(0.5))
        assertEquals(Money(101), Money(100).scaled(1.005))
        assertEquals("-0.01 CHF", Money(-1).toString())
    }

    @Test fun deficitNeverFundsInvestmentsAndBorrowingCannotBypassTheRule() {
        assertFailsWith<IllegalArgumentException> { MonthlyProfile.alexia().copy(cash = Money.chf(-5000)) }
        val profile = MonthlyProfile.alexia().copy(cash = Money.ZERO)
        val session = simulate(1, profile, config(1).copy(investmentShare = 1.0),
            listOf(LifeEvent.Financing("loan", 0, Money.chf(1000))))
        assertEquals(Money.ZERO, session.history.single().flow.investmentContribution)
        assertEquals(Money.ZERO, session.currentState.cash)
        assertEquals(Money.chf(400), session.currentState.accountsPayable)
        assertEquals(Money.chf(1000), session.currentState.debt)
    }

    @Test fun ledgerReconcilesExactlyIncludingLoansRepaymentsAndReturns() {
        val profile = MonthlyProfile.alexia().copy(studyMonths = 0, investments = Money.chf(1_000), debt = Money.chf(1_000))
        val session = simulate(24, profile, config(24).copy(annualInflation = 0.02, investmentShare = 0.35,
            annualDebtInterest = 0.06), listOf(LifeEvent.Financing("borrow", 0, Money.chf(500)),
            LifeEvent.Financing("repay", 1, principalRepayment = Money.chf(1_500)),
            LifeEvent.Maintenance("S040", 4, PaymentResponsibility.CHARACTER)))
        for (record in session.history) {
            val f = record.flow
            assertEquals(record.before.cash + f.netCashFlow - f.investmentContribution, record.after.cash)
            assertEquals(record.before.investments + f.investmentReturn + f.investmentContribution, record.after.investments)
            assertEquals(record.before.debt + f.newBorrowing - f.principalRepayment, record.after.debt)
            assertEquals(record.after.cash + record.after.investments - record.after.debt, record.after.netWorth)
            assertEquals(f.income - f.expenses + f.investmentReturn, record.netWorthChange)
            assertEquals(f.netWorthChange, record.netWorthChange)
        }
        assertEquals(Money.chf(1_500), session.history[1].flow.principalRepayment)
        assertEquals(Money.ZERO, session.currentState.debt)
        assertTrue(session.history[2].flow.interest == Money.ZERO)
    }

    @Test fun standaloneMonthIsIdenticalToSessionTransition() {
        val session = simulate(12)
        val step = MonthlySimulation.calculateMonth(session.profile, session.configuration, session.states[6]) as MonthStep.Calculated
        assertEquals(session.states[7], step.record.after)
        assertEquals(session.history[6].flow, step.record.flow)
        assertTrue(step.record.explanation().any { it.contains("2200.00 CHF") })
    }

    @Test fun goalDateIsIndependentAndAnnualOutputsAreAggregates() {
        val session = simulate(6)
        assertEquals(120, session.goal.goal.dueMonth)
        assertFalse(session.goal.dueDateSimulated)
        assertTrue(session.unsimulatedConsequences().any { it.contains("échéance non encore simulée") })
        val year = MonthlyComparisons.annualSummaries(session).single()
        assertFalse(year.isFullYear)
        assertEquals(Money.chf(7_200), year.income)
        assertEquals(Money.chf(11_600), year.expenses)
        assertEquals(session.currentState, year.closing)
        val longer = simulate(13)
        val summaries = MonthlyComparisons.annualSummaries(longer)
        assertTrue(summaries[0].isFullYear)
        assertFalse(summaries[1].isFullYear)
        assertEquals(longer.history.map { it.flow.expenses }.total(), summaries.map { it.expenses }.total())
    }

    @Test fun invalidAmountsRatesDatesAndOverRepaymentAreRejected() {
        assertFailsWith<IllegalArgumentException> { Money(Long.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { Money.chf(Long.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { config().copy(annualInflation = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { config().copy(annualInflation = -1.0) }
        assertFailsWith<IllegalArgumentException> { config().copy(investmentShare = 1.1) }
        assertFailsWith<IllegalArgumentException> { simulate(events = listOf(LifeEvent.Financing("x", 0, principalRepayment = Money.chf(1)))) }
        assertFailsWith<IllegalArgumentException> { simulate(events = listOf(MicroEvents.plant("x", 12))) }
        assertFailsWith<IllegalArgumentException> { simulate(events = listOf(MicroEvents.plant("x", 0), MicroEvents.plant("x", 1))) }
        assertFailsWith<IllegalArgumentException> { LifeEvent.Maintenance("x", 0, PaymentResponsibility.CHARACTER, Money(-1)) }
    }
}
