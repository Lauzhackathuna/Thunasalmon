package com.tuna.finlife.simulation

import kotlin.math.pow

sealed class MonthStep {
    class AwaitingDecision internal constructor(decisions: List<PendingDecision>, outcomes: List<EventOutcome>) : MonthStep() {
        private val decisionData = decisions.toList()
        private val outcomeData = outcomes.toList()
        val decisions: List<PendingDecision> get() = decisionData.toList()
        val outcomes: List<EventOutcome> get() = outcomeData.toList()
    }
    data class Calculated(val record: MonthlyRecord) : MonthStep()
}

/** Exact financial explanation: amounts are the ledger actually applied, not prose estimates. */
class MonthlyRecord internal constructor(
    val before: MonthlyState,
    val after: MonthlyState,
    val flow: MonthlyFlow,
    events: List<EventOutcome>,
    decisions: List<DecisionSelection>,
) {
    private val eventData = events.toList()
    private val decisionData = decisions.toList()
    val events: List<EventOutcome> get() = eventData.toList()
    val decisions: List<DecisionSelection> get() = decisionData.toList()
    val month: Int get() = before.month
    val needsFinancing: Boolean get() = after.cash < Money.ZERO
    val financingNeed: Money get() = (-after.cash).nonNegative()
    val netWorthChange: Money get() = after.netWorth - before.netWorth
    val provenance: DataProvenance get() = DataProvenance.SIMULATED_RESULT

    fun explanation(): List<String> = listOf(
        "Mois $month : cash ${before.cash} → ${after.cash}.",
        "Revenus : études ${flow.studentIncome}, stage ${flow.internshipIncome}, emploi ${flow.professionalIncome}.",
        "Dépenses : loyer ${flow.rent}, assurance maladie ${flow.healthInsurance}, courses ${flow.groceries}, autres ${flow.otherExpenses}, études ${flow.tuition}.",
        "Exceptionnel ${flow.exceptionalExpenses}, décisions ${flow.decisionCosts}, intérêts ${flow.interest}.",
        "Emprunts explicites ${flow.newBorrowing}, capital remboursé ${flow.principalRepayment}, versement investi ${flow.investmentContribution}, rendement ${flow.investmentReturn}.",
        "Variation de patrimoine = revenus − dépenses + rendement = $netWorthChange. Patrimoine nominal ${after.netWorth}, réel ${after.realNetWorth}.",
        if (needsFinancing) "Besoin de financement : $financingNeed. Aucun financement automatique."
        else "Pas de déficit de liquidités à la fin du mois.",
    ) + eventData.map { "${it.eventId} : ${it.application}, ${it.reason}, coût ${it.chargedCost}, choix ${it.optionId ?: "aucun"}." }
}

object MonthlySimulation {
    fun initialState(profile: MonthlyProfile, configuration: MonthlyConfiguration): MonthlyState {
        val netWorth = profile.cash + profile.investments - profile.debt
        return MonthlyState(0, profile.cash, profile.investments, profile.debt, netWorth,
            0, profile.studyMonths, -1, 0, Money.ZERO, if (profile.studyMonths == 0) 0 else null, profile.housing, null)
            .also { require(configuration.horizonMonths > 0) }
    }

    /** Computes exactly one transition; no RNG, mutation or implicit response to a decision. */
    fun calculateMonth(
        profile: MonthlyProfile,
        configuration: MonthlyConfiguration,
        state: MonthlyState,
        events: List<LifeEvent> = emptyList(),
        decisions: List<DecisionSelection> = emptyList(),
    ): MonthStep {
        val month = state.month
        require(month in 0 until configuration.horizonMonths)
        require(state.studyMonthsCompleted + state.studyMonthsRemaining == profile.studyMonths)
        require(state.housing.priceMonth <= month)
        require(state.rentRule == null || state.rentRule.contractId == state.housing.id)
        val expectedPaidProgress = if (state.studyMonthsCompleted == 0) -1 else
            (state.studyMonthsCompleted - 1) / profile.tuitionEveryStudyMonths * profile.tuitionEveryStudyMonths
        require(state.lastPaidTuitionProgress == expectedPaidProgress) { "Academic progress and paid tuition schedule disagree" }
        require(state.realNetWorth == state.netWorth.scaled(1.0 / configuration.priceIndex(month)))
        EventCalendar.validate(events, configuration.horizonMonths)
        require(events.all { it.month == month })
        require(decisions.map { it.eventId }.distinct().size == decisions.size)
        val choiceMap = decisions.associate { it.eventId to it.optionId }
        for (choice in decisions) {
            val event = events.singleOrNull { it.id == choice.eventId }
            require(event != null && event.interactive) { "Choice does not refer to an interactive event this month" }
            require(options(event).any { it.id == choice.optionId }) { "Unknown option" }
        }
        var working = state
        var exceptional = Money.ZERO
        var decisionCosts = Money.ZERO
        var borrowing = Money.ZERO
        var repayment = Money.ZERO
        val outcomes = mutableListOf<EventOutcome>()
        val pending = mutableListOf<PendingDecision>()
        for (event in events.sortedWith(EventCalendar.order)) {
            val reason = inapplicableReason(event, working)
            if (reason != null) {
                outcomes += EventOutcome(event.id, EventApplication.NOT_APPLICABLE, reason)
                continue
            }
            val choice = choiceMap[event.id]
            if (event.interactive && choice == null) {
                pending += PendingDecision(event, options(event))
                continue
            }
            var cost = Money.ZERO
            var declined = false
            when (event) {
                is LifeEvent.Internship -> {
                    declined = choice == "decline"
                    if (!declined) working = working.copy(internshipMonthsRemaining = event.durationMonths,
                        internshipMonthlyIncome = event.monthlyIncome)
                }
                is LifeEvent.RentRise -> working = working.copy(rentRule = event.rule)
                is LifeEvent.Maintenance -> { cost = event.nominalAmount; exceptional += cost }
                is LifeEvent.PurchaseChoice -> {
                    cost = if (choice == "first") event.firstCost else event.secondCost
                    decisionCosts += cost
                }
                is LifeEvent.HousingChoice -> {
                    declined = choice == "stay"
                    if (!declined) {
                        cost = if (event.responsibility == PaymentResponsibility.CHARACTER) event.movingCost else Money.ZERO
                        decisionCosts += cost
                        // A new contract never inherits the old contract's S037 rule.
                        working = working.copy(housing = event.newContract, rentRule = event.newRentRule)
                    }
                }
                is LifeEvent.OneOffExpense -> { cost = event.amount; exceptional += cost }
                is LifeEvent.Financing -> { borrowing += event.borrowing; repayment += event.principalRepayment }
            }
            outcomes += EventOutcome(event.id, if (declined) EventApplication.DECLINED else EventApplication.APPLIED,
                if (declined) "Option de référence conservée" else "Règle appliquée pendant le mois $month", cost, choice)
        }
        // Nothing is applied until every required response exists. Only report skipped events here.
        if (pending.isNotEmpty()) return MonthStep.AwaitingDecision(pending,
            outcomes.filter { it.application == EventApplication.NOT_APPLICABLE })

        fun indexed(amount: Money) = amount.scaled(configuration.priceIndex(month))
        val status = working.status
        val studentIncome = if (status == LifeStatus.STUDENT) profile.studentMonthlyIncome else Money.ZERO
        val internIncome = if (status == LifeStatus.INTERN) working.internshipMonthlyIncome else Money.ZERO
        val professionalIncome = if (status == LifeStatus.EMPLOYED) profile.startingMonthlySalary.scaled(
            (1.0 + configuration.annualSalaryGrowth).pow((month - requireNotNull(working.careerStartMonth)) / 12)) else Money.ZERO
        val studyProgress = working.studyMonthsCompleted
        val tuitionDue = status == LifeStatus.STUDENT && studyProgress % profile.tuitionEveryStudyMonths == 0 &&
            studyProgress > working.lastPaidTuitionProgress
        val tuition = if (tuitionDue) indexed(profile.tuitionPayment) else Money.ZERO
        val extraRent = working.rentRule?.let { rule ->
            if (month < rule.startMonth) 1.0 else (1.0 + rule.annualExtraRate).pow((month - rule.startMonth) / 12 + 1)
        } ?: 1.0
        val rent = working.housing.monthlyRent.scaled(
            configuration.priceIndex(month - working.housing.priceMonth) * extraRent)
        require(repayment <= state.debt + borrowing) { "Principal repayment exceeds explicit outstanding debt" }
        val interest = state.debt.scaled(FinancialSimulation.effectiveMonthlyRate(configuration.annualDebtInterest))
        var flow = MonthlyFlow(studentIncome, internIncome, professionalIncome, rent, indexed(profile.healthInsurance),
            indexed(profile.groceries), indexed(profile.otherExpenses), tuition, exceptional, decisionCosts,
            interest, borrowing, repayment)
        // Borrowing cannot finance investments; existing deficits must first be covered.
        val operatingSurplus = (flow.income - flow.expenses - repayment).nonNegative()
        val availableWithoutBorrowing = (state.cash + flow.netCashFlow - borrowing).nonNegative()
        val contribution = minOf(operatingSurplus.scaled(configuration.investmentShare), availableWithoutBorrowing)
        val investmentReturn = state.investments.scaled(FinancialSimulation.effectiveMonthlyRate(configuration.annualInvestmentReturn))
        flow = flow.copy(investmentContribution = contribution, investmentReturn = investmentReturn)
        val cash = state.cash + flow.netCashFlow - contribution
        val investments = state.investments + investmentReturn + contribution
        val debt = state.debt + borrowing - repayment
        val studyCompleted = studyProgress + if (status == LifeStatus.STUDENT) 1 else 0
        val studyRemaining = profile.studyMonths - studyCompleted
        val careerStart = working.careerStartMonth ?: if (studyRemaining == 0) month + 1 else null
        val after = working.copy(month = month + 1, cash = cash, investments = investments, debt = debt,
            realNetWorth = (cash + investments - debt).scaled(1.0 / configuration.priceIndex(month + 1)),
            studyMonthsCompleted = studyCompleted, studyMonthsRemaining = studyRemaining,
            lastPaidTuitionProgress = if (tuitionDue) studyProgress else working.lastPaidTuitionProgress,
            internshipMonthsRemaining = (working.internshipMonthsRemaining - 1).coerceAtLeast(0), careerStartMonth = careerStart)
        check(after.netWorth - state.netWorth == flow.netWorthChange)
        return MonthStep.Calculated(MonthlyRecord(state, after, flow, outcomes, decisions))
    }

    private fun inapplicableReason(event: LifeEvent, state: MonthlyState): String? = when (event) {
        is LifeEvent.Internship -> if (state.status != LifeStatus.STUDENT) "S005 nécessite des études restantes et un statut étudiant" else null
        is LifeEvent.RentRise -> when {
            event.rule.contractId != state.housing.id -> "S037 concerne un autre contrat de logement"
            state.rentRule != null -> "Une règle S037 est déjà active pour ce contrat"
            else -> null
        }
        is LifeEvent.HousingChoice -> if (event.newContract.id == state.housing.id) "Le nouveau contrat doit avoir un identifiant distinct" else null
        is LifeEvent.Maintenance -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Réparation à la charge d'un tiers" else null
        is LifeEvent.PurchaseChoice -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Coût à la charge d'un tiers" else null
        is LifeEvent.OneOffExpense -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Coût à la charge d'un tiers" else null
        is LifeEvent.Financing -> null
    }

    private fun options(event: LifeEvent): List<DecisionOption> = when (event) {
        is LifeEvent.Internship -> listOf(DecisionOption("accept", "Accepter le stage", Money.ZERO), DecisionOption("decline", "Continuer les études", Money.ZERO))
        is LifeEvent.PurchaseChoice -> listOf(DecisionOption("first", event.firstLabel, event.firstCost), DecisionOption("second", event.secondLabel, event.secondCost))
        is LifeEvent.HousingChoice -> listOf(DecisionOption("stay", "Conserver le logement", Money.ZERO),
            DecisionOption("move", "Changer de logement", if (event.responsibility == PaymentResponsibility.CHARACTER) event.movingCost else Money.ZERO))
        else -> emptyList()
    }
}
