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
    val budget: BudgetLedger,
) {
    private val eventData = events.toList()
    private val decisionData = decisions.toList()
    val events: List<EventOutcome> get() = eventData.toList()
    val decisions: List<DecisionSelection> get() = decisionData.toList()
    val month: Int get() = before.month
    val needsFinancing: Boolean get() = after.unpaid.items.isNotEmpty()
    val financingNeed: Money get() = after.unpaid.items.map { it.missing }.total()
    val paidExpenses: Money get() = budget.paidExpenses
    val netWorthChange: Money get() = after.netWorth - before.netWorth
    val provenance: DataProvenance get() = DataProvenance.SIMULATED_RESULT

    fun explanation(): List<String> = listOf(
        "Mois $month : cash ${before.cash} → ${after.cash}.",
        "Revenus : études ${flow.studentIncome}, stage ${flow.internshipIncome}, emploi ${flow.professionalIncome}.",
        "Dépenses : loyer ${flow.rent}, assurance maladie ${flow.healthInsurance}, courses ${flow.groceries}, autres ${flow.otherExpenses}, études ${flow.tuition}.",
        "Paiements effectifs ${flow.paidExpenses} ; passifs fournisseurs ${after.accountsPayable}. Exceptionnel ${flow.exceptionalExpenses}, décisions ${flow.decisionCosts}, intérêts ${flow.interest}.",
        "Emprunts explicites ${flow.newBorrowing}, capital remboursé ${flow.principalRepayment}, versement investi ${flow.investmentContribution}, rendement ${flow.investmentReturn}.",
        "Variation de patrimoine = revenus − dépenses + rendement = $netWorthChange. Patrimoine nominal ${after.netWorth}, réel ${after.realNetWorth}.",
        if (needsFinancing) "Besoin de financement : $financingNeed. Aucun financement automatique."
        else "Pas de déficit de liquidités à la fin du mois.",
    ) + budget.explanation() + eventData.map { "${it.eventId} : ${it.application}, ${it.reason}, coût ${it.chargedCost}, choix ${it.optionId ?: "aucun"}." }
}

object MonthlySimulation {
    fun initialState(profile: MonthlyProfile, configuration: MonthlyConfiguration): MonthlyState {
        val netWorth = profile.cash + profile.investments - profile.debt
        return MonthlyState(0, profile.cash, profile.investments, profile.debt, netWorth,
            0, profile.studyMonths, -1, 0, Money.ZERO, if (profile.studyMonths == 0) 0 else null, profile.housing, null,
                envelopes = BudgetEnvelopes.initial(profile))
            .also { require(configuration.horizonMonths > 0) }
    }

    /** Computes exactly one transition; no RNG, mutation or implicit response to a decision. */
    fun calculateMonth(
        profile: MonthlyProfile,
        configuration: MonthlyConfiguration,
        state: MonthlyState,
        events: List<LifeEvent> = emptyList(),
        decisions: List<DecisionSelection> = emptyList(),
        knownFutureEvents: List<LifeEvent> = emptyList(),
    ): MonthStep {
        val month = state.month
        require(month in 0 until configuration.horizonMonths)
        require(state.studyMonthsCompleted + state.studyMonthsRemaining == profile.studyMonths)
        require(state.housing.priceMonth <= month)
        require(state.rentRule == null || state.rentRule.contractId == state.housing.id)
        val expectedPaidProgress = if (state.studyMonthsCompleted == 0) -1 else
            (state.studyMonthsCompleted - 1) / profile.tuitionEveryStudyMonths * profile.tuitionEveryStudyMonths
        require(state.lastPaidTuitionProgress == expectedPaidProgress) { "Academic progress and assessed tuition schedule disagree" }
        require(state.realNetWorth == state.netWorth.scaled(1.0 / configuration.priceIndex(month)))
        EventCalendar.validate(events, configuration.horizonMonths)
        require(events.all { it.month == month })
        require(decisions.map { it.eventId }.distinct().size == decisions.size)
        val choiceMap = decisions.associate { it.eventId to it.optionId }
        for (choice in decisions) {
            val event = events.singleOrNull { it.id == choice.eventId }
            require(event != null && event.interactive) { "Choice does not refer to an interactive event this month" }
            require(options(event, configuration).any { it.id == choice.optionId }) { "Unknown option" }
        }
        var working = state
        var exceptional = Money.ZERO
        var decisionCosts = Money.ZERO
        var borrowing = Money.ZERO
        var repayment = Money.ZERO
        val outcomes = mutableListOf<EventOutcome>()
        val pending = mutableListOf<PendingDecision>()
        for (event in events.sortedWith(EventCalendar.order)) {
            val committedAnnualConflict = event is LifeEvent.ShortInternshipOffer &&
                (events + knownFutureEvents).filterIsInstance<LifeEvent.Internship>().any {
                    !it.interactive && it.month < event.startMonth + 3 && it.month + it.durationMonths > event.month
                }
            val reason = if (committedAnnualConflict) "Un stage S005 déjà engagé est incompatible avec cette offre" else inapplicableReason(event, working)
            if (reason != null) {
                outcomes += EventOutcome(event.id, EventApplication.NOT_APPLICABLE, reason)
                continue
            }
            val choice = choiceMap[event.id]
            if (event.interactive && choice == null && !(event is LifeEvent.RentRise && (working.housing.type == HousingType.SHARED || event.rule.annualExtraRate == 0.0))) {
                pending += PendingDecision(event, options(event, configuration))
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
                is LifeEvent.ShortInternshipOffer -> {
                    declined = choice == "decline"
                    if (!declined) working = working.copy(shortInternship = ShortInternship(event.month, event.startMonth, event.monthlyIncome))
                }
                is LifeEvent.RentRise -> {
                    if (choice == "move") {
                        cost = configuration.sharedMovingCostAt(month)
                        decisionCosts += cost
                        working = working.copy(housing = sharedContract(month, configuration), rentRule = null,
                            housingDecision = HousingDecision(month, "Hausse contractuelle S037", event.id))
                    } else working = working.copy(rentRule = event.rule,
                        housingDecision = HousingDecision(month, "Hausse contractuelle acceptée", event.id))
                }
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
                        working = working.copy(housing = event.newContract, rentRule = event.newRentRule,
                            housingDecision = HousingDecision(month, "Changement volontaire de logement", event.id))
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
        val internIncome = (if (status == LifeStatus.INTERN) working.internshipMonthlyIncome else Money.ZERO) +
            (working.shortInternship?.takeIf { it.active(month) }?.monthlyIncome ?: Money.ZERO)
        val professionalIncome = if (status == LifeStatus.EMPLOYED) profile.startingMonthlySalary.scaled(
            (1.0 + configuration.annualSalaryGrowth).pow((month - requireNotNull(working.careerStartMonth)) / 12)) else Money.ZERO
        val studyProgress = working.studyMonthsCompleted
        val tuitionDue = status == LifeStatus.STUDENT && studyProgress % profile.tuitionEveryStudyMonths == 0 &&
            studyProgress > working.lastPaidTuitionProgress
        val tuition = if (tuitionDue) indexed(profile.tuitionPayment) else Money.ZERO
        val rent = rentAt(working, configuration)
        require(repayment <= state.debt + borrowing) { "Principal repayment exceeds explicit outstanding debt" }
        val interest = state.debt.scaled(FinancialSimulation.effectiveMonthlyRate(configuration.annualDebtInterest))
        var flow = MonthlyFlow(studentIncome, internIncome, professionalIncome, rent, indexed(profile.healthInsurance),
            indexed(profile.groceries), indexed(profile.otherExpenses), tuition, exceptional, decisionCosts,
            interest, borrowing, repayment)
        val obligations = state.unpaid.items + listOf(
            Triple("studies", Envelope.STUDIES, tuition), Triple("rent", Envelope.HOUSING, rent),
            Triple("insurance", Envelope.INSURANCE, flow.healthInsurance), Triple("groceries", Envelope.GROCERIES, flow.groceries),
            Triple("other", Envelope.OTHER, flow.otherExpenses), Triple("interest", Envelope.OTHER, interest),
        ).filter { it.third > Money.ZERO }.map { (id, category, amount) ->
            FinancialObligation("$month-$id", month, category, amount)
        } + outcomes.filter { it.chargedCost > Money.ZERO }.map {
            FinancialObligation("$month-event-${it.eventId}", month, Envelope.OTHER, it.chargedCost)
        } + if (repayment > Money.ZERO) listOf(FinancialObligation("$month-principal", month, Envelope.OTHER, repayment, bankPrincipal = true)) else emptyList()
        require(state.unpaid.items.filter { it.bankPrincipal }.map { it.missing }.total() + repayment <= state.debt + borrowing)
        val operatingSurplus = (flow.income - flow.expenses - repayment - state.unpaid.items.map { it.missing }.total()).nonNegative()
        val availableWithoutBorrowing = (state.cash + flow.netCashFlow - borrowing - state.accountsPayable).nonNegative()
        val limit = minOf(operatingSurplus.scaled(configuration.investmentShare), availableWithoutBorrowing)
        val ledger = BudgetEnvelopes.settle(state.envelopes, flow.income, borrowing,
            BudgetEnvelopes.targets(profile, configuration, working, flow, knownFutureEvents), obligations, limit, month)
        val investmentReturn = state.investments.scaled(FinancialSimulation.effectiveMonthlyRate(configuration.annualInvestmentReturn))
        flow = flow.copy(investmentContribution = ledger.invested, investmentReturn = investmentReturn,
            principalRepayment = ledger.paidPrincipal, paidExpenses = ledger.paidExpenses)
        val cash = ledger.after.total
        val investments = state.investments + investmentReturn + ledger.invested
        val debt = state.debt + borrowing - ledger.paidPrincipal
        val payable = ledger.outstanding.filter { !it.bankPrincipal }.map { it.missing }.total()
        val studyCompleted = studyProgress + if (status == LifeStatus.STUDENT) 1 else 0
        val studyRemaining = profile.studyMonths - studyCompleted
        val careerStart = working.careerStartMonth ?: if (studyRemaining == 0) month + 1 else null
        val after = working.copy(month = month + 1, cash = cash, investments = investments, debt = debt,
            envelopes = ledger.after, unpaid = UnpaidObligations(ledger.outstanding),
            realNetWorth = (cash + investments - debt - payable).scaled(1.0 / configuration.priceIndex(month + 1)),
            studyMonthsCompleted = studyCompleted, studyMonthsRemaining = studyRemaining,
            lastPaidTuitionProgress = if (tuitionDue) studyProgress else working.lastPaidTuitionProgress,
            internshipMonthsRemaining = (working.internshipMonthsRemaining - 1).coerceAtLeast(0), careerStartMonth = careerStart)
        check(after.netWorth - state.netWorth == flow.netWorthChange)
        return MonthStep.Calculated(MonthlyRecord(state, after, flow, outcomes, decisions, ledger))
    }

    internal fun rentAt(state: MonthlyState, configuration: MonthlyConfiguration): Money {
        val rule = state.rentRule
        val extra = if (rule == null || state.month < rule.startMonth) 1.0 else
            (1.0 + rule.annualExtraRate).pow((state.month - rule.startMonth) / 12 + 1)
        return state.housing.monthlyRent.scaled(configuration.priceIndex(state.month - state.housing.priceMonth) * extra)
    }

    /** Reference 800 CHF is at month zero; indexed once when the new contract is signed. */
    fun sharedContract(month: Int, configuration: MonthlyConfiguration) = HousingContract(
        "shared-$month", configuration.sharedReferenceRent.scaled(configuration.priceIndex(month)), month, HousingType.SHARED)

    fun sharedHousingChoice(month: Int, configuration: MonthlyConfiguration, id: String = "FINLIFE_SHARED_$month") =
        LifeEvent.HousingChoice(id, month, sharedContract(month, configuration), configuration.sharedMovingCostAt(month), PaymentResponsibility.CHARACTER)

    private fun inapplicableReason(event: LifeEvent, state: MonthlyState): String? = when (event) {
        is LifeEvent.ShortInternshipOffer -> if (state.status != LifeStatus.STUDENT ||
            state.shortInternship?.let { it.startMonth + 3 > state.month } == true) "Mini-stage incompatible avec le statut ou un stage déjà engagé" else null
        is LifeEvent.Internship -> if (state.status != LifeStatus.STUDENT ||
            state.shortInternship?.let { it.startMonth + 3 > state.month } == true) "S005 nécessite des études restantes et un statut étudiant" else null
        is LifeEvent.RentRise -> when {
            event.rule.contractId != state.housing.id -> "S037 concerne un autre contrat de logement"
            state.rentRule != null -> "Une règle S037 est déjà active pour ce contrat"
            else -> null
        }
        is LifeEvent.HousingChoice -> if (event.newContract.id == state.housing.id ||
            (event.newContract.type == HousingType.SHARED && state.housing.type == HousingType.SHARED)) "Le nouveau contrat doit avoir un identifiant distinct" else null
        is LifeEvent.Maintenance -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Réparation à la charge d'un tiers" else null
        is LifeEvent.PurchaseChoice -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Coût à la charge d'un tiers" else null
        is LifeEvent.OneOffExpense -> if (event.responsibility != PaymentResponsibility.CHARACTER) "Coût à la charge d'un tiers" else null
        is LifeEvent.Financing -> null
    }

    private fun options(event: LifeEvent, configuration: MonthlyConfiguration): List<DecisionOption> = when (event) {
        is LifeEvent.RentRise -> listOf(DecisionOption("stay", "Accepter le nouveau loyer", Money.ZERO),
            DecisionOption("move", "Passer en colocation", configuration.sharedMovingCostAt(event.month)))
        is LifeEvent.ShortInternshipOffer -> listOf(DecisionOption("accept", "Accepter le stage de trois mois", Money.ZERO),
            DecisionOption("decline", "Refuser le stage", Money.ZERO))
        is LifeEvent.Internship -> listOf(DecisionOption("accept", "Accepter le stage", Money.ZERO), DecisionOption("decline", "Continuer les études", Money.ZERO))
        is LifeEvent.PurchaseChoice -> listOf(DecisionOption("first", event.firstLabel, event.firstCost), DecisionOption("second", event.secondLabel, event.secondCost))
        is LifeEvent.HousingChoice -> listOf(DecisionOption("stay", "Conserver le logement", Money.ZERO),
            DecisionOption("move", "Changer de logement", if (event.responsibility == PaymentResponsibility.CHARACTER) event.movingCost else Money.ZERO))
        else -> emptyList()
    }
}
