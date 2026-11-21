package com.tuna.finlife.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinLifeV3Test {
    private val profile = MonthlyProfile.alexia()
    private val config = MonthlyConfiguration(annualInflation = 0.0, randomEvents = RandomEventSettings(enabled = false))
    private fun initial(p: MonthlyProfile = profile, c: MonthlyConfiguration = config) = MonthlySimulation.initialState(p, c)
    private fun project(p: MonthlyProfile = profile, c: MonthlyConfiguration = config) = FinancialProjections.nothingChanges(p, c, initial(p, c))
    private fun bill(category: Envelope, amount: Long) = FinancialObligation("bill", 0, category, Money.chf(amount))

    @Test fun referenceAllocationAndCustomAllocationConserveCash() {
        val state = initial()
        assertEquals(Money.chf(8000), state.envelopes.total)
        assertEquals(Money.chf(1350), state.envelopes.reserve)
        assertEquals(state.cash, state.netWorth)
        assertEquals(LifeStatus.STUDENT, state.status)
        assertEquals(HousingType.ALONE, state.housing.type)
        val p = profile.copy(cash = Money.chf(10), initialEnvelopes = EnvelopeBalances(other = Money.chf(10)))
        assertEquals(p.initialEnvelopes, initial(p).envelopes)
        assertFailsWith<IllegalArgumentException> { profile.copy(initialEnvelopes = EnvelopeBalances()) }
    }

    @Test fun allocationsFillNeedsInPriorityOrderAndThenReserve() {
        val target = EnvelopeBalances(Money.chf(20), Money.chf(30), Money.chf(40), Money.chf(10), Money.chf(50))
        val ledger = BudgetEnvelopes.settle(EnvelopeBalances(), Money.chf(160), Money.ZERO, target, emptyList())
        assertEquals(Envelope.entries.toList(), ledger.allocations.map { it.category })
        assertEquals(listOf(10L, 20L, 30L, 40L, 50L, 10L).map { Money.chf(it) }, ledger.allocations.map { it.amount })
        val next = BudgetEnvelopes.settle(ledger.after, Money.chf(20), Money.ZERO, target, emptyList())
        assertEquals(listOf(IncomeAllocation(Envelope.RESERVE, Money.chf(20))), next.allocations)
        assertEquals(Money.chf(180), next.after.total)
    }

    @Test fun rentShortfallUsesOtherThenReserveWithoutDoubleSpending() {
        val before = EnvelopeBalances(housing = Money.chf(850), other = Money.chf(100), reserve = Money.chf(1350))
        val ledger = BudgetEnvelopes.settle(before, Money.ZERO, Money.ZERO, EnvelopeBalances(), listOf(bill(Envelope.HOUSING, 1000)))
        assertEquals(listOf(Envelope.OTHER, Envelope.RESERVE), ledger.transfers.map { it.from })
        assertEquals(listOf(Money.chf(100), Money.chf(50)), ledger.transfers.map { it.amount })
        assertEquals(before.total - Money.chf(1000), ledger.after.total)
        assertEquals(Money.chf(1000), ledger.paidExpenses)
        assertTrue(ledger.explanation().any { it.contains("100.00 CHF") })
    }

    @Test fun unexpectedExpenseUsesOtherAndReserveAndPreservesFutureRiskTrace() {
        val before = EnvelopeBalances(other = Money.chf(300), reserve = Money.chf(1350))
        val result = BudgetEnvelopes.settle(before, Money.ZERO, Money.ZERO, EnvelopeBalances(), listOf(bill(Envelope.OTHER, 500)))
        assertEquals(Money.chf(200), result.transfers.single().amount)
        assertEquals(Money.chf(1150), result.after.total)
        val scarce = EnvelopeBalances(Money.chf(10), Money.chf(10), Money.chf(10), Money.chf(10), Money.chf(10), Money.chf(10))
        val emergency = BudgetEnvelopes.settle(scarce, Money.ZERO, Money.ZERO, scarce, listOf(bill(Envelope.OTHER, 100)))
        assertEquals(listOf(Envelope.RESERVE, Envelope.GROCERIES, Envelope.INSURANCE, Envelope.HOUSING, Envelope.STUDIES), emergency.transfers.map { it.from })
        assertEquals(Money.chf(40), emergency.outstanding.single().missing)
        assertEquals(Money.ZERO, emergency.after.total)
        assertTrue(emergency.transfers.all { it.futureFundingAtRisk == Money.chf(10) })
    }

    @Test fun tuitionIsAccruedOnlyAtDeadlinesAndUnpaidBillRemainsExplicit() {
        val result = project()
        assertEquals(listOf(0, 6), result.records.filter { it.flow.tuition > Money.ZERO }.map { it.month })
        assertEquals(Money.chf(1600), result.records.map { it.flow.tuition }.total())
        assertEquals(Money.chf(1600), result.records.flatMap { it.budget.payments }.filter { it.obligation.category == Envelope.STUDIES }.map { it.amount }.total())
        val poor = profile.copy(cash = Money.ZERO, studentMonthlyIncome = Money.ZERO)
        val step = MonthlySimulation.calculateMonth(poor, config, initial(poor)) as MonthStep.Calculated
        assertEquals(Money.chf(800), step.record.after.unpaid.items.single { it.category == Envelope.STUDIES }.missing)
        assertEquals(Money.ZERO, step.record.paidExpenses)
        val next = MonthlySimulation.calculateMonth(poor, config, step.record.after) as MonthStep.Calculated
        assertEquals(1, next.record.after.unpaid.items.count { it.category == Envelope.STUDIES })
        assertEquals(Money.ZERO, next.record.flow.tuition)
    }

    @Test fun twelveMonthsReconcileAccrualsPaymentsAndTheoreticalDeficit() {
        val result = project()
        assertEquals(12, result.records.size); assertEquals(13, result.states.size)
        assertEquals(Money.chf(14400), result.records.map { it.flow.income }.total())
        assertEquals(Money.chf(23200), result.records.map { it.flow.expenses }.total())
        assertEquals(Money.chf(22400), result.records.map { it.paidExpenses }.total())
        assertEquals(Money.chf(-800), result.finalState.theoreticalCash)
        assertEquals(Money.chf(800), result.finalState.accountsPayable)
        assertEquals(Money.ZERO, result.finalState.cash)
        for (r in result.records) {
            assertEquals(r.before.cash + r.flow.income + r.flow.newBorrowing - r.paidExpenses - r.flow.principalRepayment - r.flow.investmentContribution, r.after.cash)
            assertEquals(r.flow.netWorthChange, r.netWorthChange)
            assertEquals(r.after.cash + r.after.investments - r.after.debt - r.after.accountsPayable, r.after.netWorth)
            assertEquals(r.after.cash, r.after.envelopes.total)
            assertEquals(Money.ZERO, r.after.debt)
            assertTrue(Envelope.entries.all { r.after.envelopes[it] >= Money.ZERO })
            assertEquals(r.after.netWorth, r.after.realNetWorth)
        }
    }

    @Test fun arrearsArePaidLaterWithoutRecognizingExpenseTwice() {
        val p = profile.copy(cash = Money.ZERO, studentMonthlyIncome = Money.ZERO)
        val first = (MonthlySimulation.calculateMonth(p, config, initial(p)) as MonthStep.Calculated).record
        val next = (MonthlySimulation.calculateMonth(p, config, first.after,
            listOf(LifeEvent.Financing("explicit", 1, borrowing = Money.chf(5000)))) as MonthStep.Calculated).record
        assertEquals(Money.chf(1800), next.flow.expenses)
        assertEquals(Money.chf(4400), next.paidExpenses)
        assertEquals(Money.chf(600), next.after.cash)
        assertEquals(Money.ZERO, next.after.accountsPayable)
        assertEquals(Money.chf(5000), next.after.debt)
        assertEquals(next.flow.netWorthChange, next.netWorthChange)
    }

    @Test fun unpaidPrincipalStaysInBankDebtWithoutCreatingDuplicateLiability() {
        val p = profile.copy(cash = Money.ZERO, studentMonthlyIncome = Money.ZERO, debt = Money.chf(100))
        val first = (MonthlySimulation.calculateMonth(p, config, initial(p),
            listOf(LifeEvent.Financing("repay", 0, principalRepayment = Money.chf(100)))) as MonthStep.Calculated).record
        assertEquals(Money.chf(100), first.after.debt)
        assertEquals(Money.chf(2600), first.after.accountsPayable)
        assertEquals(Money.chf(2700), first.financingNeed)
        assertEquals(Money.ZERO, first.flow.principalRepayment)
        val next = (MonthlySimulation.calculateMonth(p, config, first.after,
            listOf(LifeEvent.Financing("loan", 1, borrowing = Money.chf(5000)))) as MonthStep.Calculated).record
        assertEquals(Money.chf(100), next.flow.principalRepayment)
        assertEquals(Money.chf(5000), next.after.debt)
    }

    @Test fun voluntarySharingIsAvailableImmediatelyAndPersists() {
        val session = SimulationSessions.start(profile, config)
        val offered = SimulationSessions.proposeSharedHousing(session)
        val event = SimulationSessions.pendingDecisions(offered).single().event
        val moved = SimulationSessions.runUntilBlocked(SimulationSessions.submit(offered, event.id, "move"), 12)
        assertEquals(0, session.currentMonth)
        assertEquals(Money.chf(1600), moved.currentState.cash)
        assertTrue(moved.history.all { it.flow.rent == Money.chf(800) && it.flow.decisionCosts == Money.ZERO })
        assertEquals(0, moved.currentState.housingDecision?.month)
        assertFailsWith<IllegalArgumentException> { SimulationSessions.proposeSharedHousing(moved) }
    }

    @Test fun rentRiseBlocksAndAlternativesDoNotShareOldContractRule() {
        val event = LifeEvent.RentRise("S037", 0, MonthlyRentRule("initial-home", 0))
        val input = SimulationSessions.start(profile, config, listOf(event))
        assertEquals(SessionStatus.AWAITING_DECISION, SimulationSessions.advance(input).status)
        val alone = SimulationSessions.advance(SimulationSessions.submit(input, event.id, "stay"))
        val shared = SimulationSessions.advance(SimulationSessions.submit(input, event.id, "move"))
        assertEquals(Money.chf(1030), alone.history.single().flow.rent)
        assertEquals(Money.chf(800), shared.history.single().flow.rent)
        assertEquals(null, shared.currentState.rentRule)
        assertEquals(Money.chf(230), shared.currentState.cash - alone.currentState.cash)
        val oldRise = LifeEvent.RentRise("old", 1, MonthlyRentRule("initial-home", 1))
        val ignored = SimulationSessions.advance(SimulationSessions.schedule(shared, oldRise))
        assertEquals(EventApplication.NOT_APPLICABLE, ignored.history.last().events.single().application)
    }

    @Test fun sharedContractIndexationAndGeneralInflationDoNotPrompt() {
        val c = config.copy(annualInflation = 0.02)
        val session = SimulationSessions.runUntilBlocked(SimulationSessions.start(profile, c), 12)
        assertEquals(12, session.currentMonth)
        val moved = SimulationSessions.proposeSharedHousing(session)
        val next = SimulationSessions.advance(SimulationSessions.submit(moved, "FINLIFE_SHARED_12", "move"))
        assertEquals(Money.chf(816), next.history.last().flow.rent)
        assertEquals(Money.ZERO, next.history.last().flow.decisionCosts)
        assertEquals(Money.chf(816).scaled(c.priceIndex(1)), SimulationSessions.advance(next).history.last().flow.rent)
    }

    @Test fun shortInternshipIsExactlyThreeAdditiveMonthsAndKeepsStudies() {
        val offer = LifeEvent.ShortInternshipOffer(month = 1, startMonth = 4)
        val accepted = FinancialProjections.project("accept", profile, config, initial(), listOf(offer), listOf(DecisionSelection(offer.id, "accept")))
        val declined = FinancialProjections.nothingChanges(profile, config, initial(), listOf(offer))
        assertEquals(listOf(4, 5, 6), accepted.records.filter { it.flow.internshipIncome > Money.ZERO }.map { it.month })
        assertEquals(Money.chf(900), accepted.records.map { it.flow.internshipIncome }.total())
        assertTrue(accepted.records.all { it.flow.studentIncome == Money.chf(1200) })
        assertEquals(Money.chf(1500), accepted.records[6].flow.income)
        assertEquals(Money.chf(1200), accepted.records[7].flow.income)
        assertEquals(listOf(0, 6), accepted.records.filter { it.flow.tuition > Money.ZERO }.map { it.month })
        assertEquals(12, accepted.finalState.studyMonthsCompleted)
        assertEquals(Money.chf(900), accepted.finalState.netWorth - declined.finalState.netWorth)
        assertEquals(null, offer.scenarioId)
        assertEquals(ScenarioId.S005, LifeEvent.Internship("S005", 0).scenarioId)
    }

    @Test fun annualAndShortInternshipsCannotOverlapOrOverrideCommitment() {
        val offer = LifeEvent.ShortInternshipOffer(month = 0, startMonth = 4)
        val annual = LifeEvent.Internship("S005", 1)
        val result = FinancialProjections.project("accepted", profile, config, initial(), listOf(offer, annual), listOf(DecisionSelection(offer.id, "accept")))
        assertEquals(EventApplication.NOT_APPLICABLE, result.records[1].events.single().application)
        val reverse = FinancialProjections.project("annual", profile, config, initial(),
            listOf(annual.copy(month = 0, interactive = false), offer.copy(month = 1)), listOf(DecisionSelection(offer.id, "accept")))
        assertEquals(EventApplication.NOT_APPLICABLE, reverse.records[1].events.single().application)
    }

    @Test fun projectionFromCurrentStatePreservesActiveCommitmentsAndHasTwelveTransitions() {
        val offer = LifeEvent.ShortInternshipOffer(month = 0)
        val input = SimulationSessions.start(profile, config.copy(horizonMonths = 6), listOf(offer))
        val active = SimulationSessions.advance(SimulationSessions.submit(input, offer.id, "accept"))
        val future = FinancialProjections.nothingChanges(active)
        assertEquals(active.currentState, future.initialState)
        assertEquals(13, future.finalState.month)
        assertEquals(Money.chf(600), future.records.map { it.flow.internshipIncome }.total())
        assertEquals(1, active.currentMonth)
        assertEquals(13, future.states.size)
    }

    @Test fun knownEventsAreProvisionedAndRandomOrDiscretionaryChangesAreNotInvented() {
        val known = LifeEvent.OneOffExpense("bill", 4, "Known bill", Money.chf(500), PaymentResponsibility.CHARACTER)
        val empty = MonthlyFlow(rent = Money.chf(1000), healthInsurance = Money.chf(250), groceries = Money.chf(250), otherExpenses = Money.chf(300))
        val target = BudgetEnvelopes.targets(profile, config, initial(), empty, listOf(known))
        assertEquals(Money.chf(800), target.other)
        val future = FinancialProjections.nothingChanges(profile, config, initial(), listOf(known, MicroEvents.plant("plant", 2)))
        assertEquals(Money.chf(500), future.records[4].flow.exceptionalExpenses)
        assertEquals(Money.ZERO, future.records[2].flow.decisionCosts)
        assertTrue(future.records.flatMap { it.events }.none { it.eventId.startsWith("random-") })
    }

    @Test fun comparisonHasExplicitOptionsCommonStateCalendarAndActualCurves() {
        val event = LifeEvent.RentRise("rise", 4, MonthlyRentRule("initial-home", 4))
        val offer = LifeEvent.ShortInternshipOffer(month = 0)
        val comparison = FinancialProjections.compareDecisions(profile, config, initial(), listOf(event), offer)
        assertEquals(4, comparison.scenarios.size)
        assertTrue(comparison.scenarios.all { it.initialState == initial() && it.states.size == 13 })
        assertTrue(comparison.scenarios.all { it.records[4].events.any { outcome -> outcome.eventId == "rise" } })
        assertEquals(3, comparison.curves.size)
        assertEquals(comparison.scenarios.maxOf { it.finalState.cash }, comparison.best.finalState.cash)
        assertEquals(comparison.scenarios.minOf { it.finalState.cash }, comparison.worst.finalState.cash)
        assertEquals((1..12).map { comparison.best.states[it].cash - comparison.worst.states[it].cash }.total().scaled(1.0 / 12), comparison.averageSpread)
        assertEquals(comparison.best.states.map { it.cash }, comparison.curves[1].points.map { it.cash })
    }

    @Test fun dispersionIsNotStabilityAndEssentialCoverageExcludesDiscretionaryCosts() {
        val baseline = project()
        val same = ProjectionComparison(baseline, listOf(baseline, baseline))
        val result = FinancialStabilityAnalysis.evaluate(profile, config, baseline, same)
        assertEquals(Money.ZERO, result.averageSpread)
        assertEquals(FinancialStability.CRITICAL, result.category)
        assertEquals(Money(163333), result.coverage.first().essentialExpenses)
        assertEquals(Money.chf(1350), result.coverage.first().reserve)
        assertEquals(Money.chf(8000), result.coverage.first().mobilizableCash)
        assertEquals(BudgetBalance.STRUCTURAL_DEFICIT, result.coverage.first().balance)
        assertEquals(2, result.deficitMonths)
        assertEquals(Money.chf(800), result.maximumFinancingNeed)
        val essentialOther = FinancialStabilityAnalysis.evaluate(profile, config, baseline,
            settings = StabilitySettings(essentialOtherShare = 1.0))
        assertEquals(Money(193333), essentialOther.coverage.first().essentialExpenses)
    }

    @Test fun stageNeverImprovesPermanentBudgetAndSharingRecalculatesCoverage() {
        val comparison = FinancialProjections.compareDecisions(profile, config, initial(), offer = LifeEvent.ShortInternshipOffer(month = 0))
        val assessment = FinancialStabilityAnalysis.evaluate(profile, config, comparison.best, comparison)
        assertEquals(Money(143333), assessment.coverage.first().essentialExpenses)
        assertEquals(Money(-53333), assessment.coverage.first().structuralBalance)
        assertFalse(assessment.category == FinancialStability.STABLE)
        val wealthy = profile.copy(cash = Money.chf(50000), studentMonthlyIncome = Money.chf(2500))
        assertEquals(FinancialStability.STABLE, FinancialStabilityAnalysis.evaluate(wealthy, config, project(wealthy)).category)
    }

    @Test fun deterministicInflatedAccountingAndExampleOutputs() {
        val c = config.copy(annualInflation = 0.02)
        val a = project(c = c); val b = project(c = c)
        assertEquals(a.states, b.states)
        assertEquals(a.records.map { it.explanation() }, b.records.map { it.explanation() })
        a.states.forEach { assertEquals(it.netWorth.scaled(1.0 / c.priceIndex(it.month)), it.realNetWorth) }
        val comparisons = FinancialProjections.compareDecisions(profile, config, initial(), offer = LifeEvent.ShortInternshipOffer(month = 0))
        assertEquals(Money.chf(2500), comparisons.best.finalState.cash)
        assertEquals(Money(-100528), a.finalState.theoreticalCash)
        assertEquals(Money(100528), a.finalState.accountsPayable)
        assertEquals(Money(-98557), a.finalState.realNetWorth)
        assertEquals(Money(204167), comparisons.averageSpread)
    }
    @Test fun committedAnnualStageTakesPriorityOverNewShortOffer() {
        val offer = LifeEvent.ShortInternshipOffer(month = 0, startMonth = 4)
        val annual = LifeEvent.Internship("committed-S005", 2, interactive = false)
        val result = FinancialProjections.project("commitment", profile, config, initial(), listOf(offer, annual),
            listOf(DecisionSelection(offer.id, "accept")))
        assertEquals(EventApplication.NOT_APPLICABLE, result.records[0].events.single().application)
        assertEquals(EventApplication.APPLIED, result.records[2].events.single().application)
        assertEquals(Money.chf(2500), result.records[4].flow.internshipIncome)
        assertEquals(2, result.finalState.studyMonthsCompleted)
    }

    @Test fun sharedRentRiseNeedsNoRepeatedHousingChoiceAndZeroRiseDoesNotBlock() {
        val shared = profile.copy(housing = HousingContract("shared", Money.chf(800), type = HousingType.SHARED))
        val step = MonthlySimulation.calculateMonth(shared, config, initial(shared),
            listOf(LifeEvent.RentRise("new-contract-rise", 0, MonthlyRentRule("shared", 0)))) as MonthStep.Calculated
        assertEquals(Money.chf(824), step.record.flow.rent)
        val unchanged = MonthlySimulation.calculateMonth(profile, config, initial(),
            listOf(LifeEvent.RentRise("zero", 0, MonthlyRentRule("initial-home", 0, 0.0)))) as MonthStep.Calculated
        assertEquals(Money.chf(1000), unchanged.record.flow.rent)
    }

    @Test fun configurableMovingCostIsShownAndActuallyAccruedOnce() {
        val c = config.copy(sharedMovingCost = Money.chf(175))
        val event = LifeEvent.RentRise("S037", 0, MonthlyRentRule("initial-home", 0))
        val session = SimulationSessions.start(profile, c, listOf(event))
        assertEquals(Money.chf(175), SimulationSessions.pendingDecisions(session).single().options.single { it.id == "move" }.immediateCost)
        val moved = SimulationSessions.runUntilBlocked(SimulationSessions.submit(session, event.id, "move"), 12)
        assertEquals(Money.chf(175), moved.history.map { it.flow.decisionCosts }.total())
        assertEquals(Money.chf(1425), moved.currentState.cash)
    }

    @Test fun accountingHoldsAcrossScarcityInflationDebtAndInvestmentCases() {
        for (cash in listOf(0L, 500L, 8000L)) for (inflation in listOf(-0.01, 0.0, 0.02)) {
            val p = profile.copy(cash = Money.chf(cash), investments = Money.chf(100), debt = Money.chf(250))
            val c = config.copy(annualInflation = inflation, annualDebtInterest = 0.06, investmentShare = 0.5)
            val result = FinancialProjections.project("accounting", p, c, initial(p, c), listOf(
                LifeEvent.Financing("repay", 2, principalRepayment = Money.chf(250)),
                LifeEvent.Maintenance("stress", 5, PaymentResponsibility.CHARACTER)))
            result.records.forEach { r ->
                assertEquals(r.before.cash + r.flow.netCashFlow - r.flow.investmentContribution, r.after.cash)
                assertEquals(r.flow.netWorthChange, r.netWorthChange)
                assertEquals(r.before.debt + r.flow.newBorrowing - r.flow.principalRepayment, r.after.debt)
                assertEquals(r.after.envelopes.total, r.after.cash)
                assertTrue(Envelope.entries.all { r.after.envelopes[it] >= Money.ZERO })
                assertEquals(r.after.netWorth.scaled(1.0 / c.priceIndex(r.after.month)), r.after.realNetWorth)
            }
        }
    }

    @Test fun uiReadmeExampleUsesThePublicApi() {
        val config = MonthlyConfiguration(randomEvents = RandomEventSettings(enabled = false))
        var session = SimulationSessions.start(configuration = config)
        session = SimulationSessions.proposeSharedHousing(session)
        val housing = SimulationSessions.pendingDecisions(session).single()
        session = SimulationSessions.submit(session, housing.event.id, "move")
        session = SimulationSessions.schedule(session, LifeEvent.ShortInternshipOffer(month = 0))
        session = SimulationSessions.submit(session, "FINLIFE_INTERNSHIP_OFFER", "accept")
        session = SimulationSessions.advance(session)
        val projection = FinancialProjections.nothingChanges(session)
        val comparison = FinancialProjections.compareDecisions(session.profile, session.configuration, session.currentState)
        val stability = FinancialStabilityAnalysis.evaluate(session.profile, session.configuration, comparison.reference, comparison)
        assertEquals(13, projection.states.size)
        assertEquals(3, comparison.curves.size)
        assertEquals(13, stability.coverage.size)
        assertEquals(Money.chf(300), projection.records.first().flow.internshipIncome)
        assertTrue(session.history.last().explanation().isNotEmpty())
    }

}
