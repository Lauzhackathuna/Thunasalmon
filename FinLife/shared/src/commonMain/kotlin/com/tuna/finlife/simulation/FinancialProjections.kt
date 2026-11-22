package com.tuna.finlife.simulation

import kotlin.math.pow

/** Thirteen states and twelve real engine transitions, starting at the supplied opening state. */
class TwelveMonthProjection internal constructor(val label: String, val initialState: MonthlyState,
    records: List<MonthlyRecord>, assumptions: List<String>) {
    private val data = records.toList()
    private val assumptionData = assumptions.toList()
    val records get() = data.toList()
    val states get() = listOf(initialState) + data.map { it.after }
    val assumptions get() = assumptionData.toList()
    val finalState get() = data.last().after
    val deficitMonths get() = data.filter { it.needsFinancing }.map { it.month }
}

data class ProjectionPoint(val month: Int, val cash: Money, val theoreticalCash: Money,
    val nominalNetWorth: Money, val realNetWorth: Money, val unpaid: Money, val rent: Money?)
enum class CurveRole { REFERENCE, BEST_STUDIED, WORST_STUDIED }
class ProjectionCurve(val role: CurveRole, val label: String, val colorHex: String, val projection: TwelveMonthProjection) {
    val points get() = projection.states.mapIndexed { index, state -> ProjectionPoint(state.month, state.cash,
        state.theoreticalCash, state.netWorth, state.realNetWorth, state.unpaid.items.map { it.missing }.total(),
        if (index == 0) null else projection.records[index - 1].flow.rent) }
}
class ProjectionComparison internal constructor(val reference: TwelveMonthProjection, scenarios: List<TwelveMonthProjection>) {
    private val data = scenarios.toList()
    val scenarios get() = data.toList()
    // Cash is primary; unpaid obligations break ties, then net worth. Stable ordering resolves exact ties.
    private val ranking = compareBy<TwelveMonthProjection> { it.finalState.cash }
        .thenByDescending { it.finalState.unpaid.items.map { bill -> bill.missing }.total() }
        .thenBy { it.finalState.netWorth }
    val best = data.maxWith(ranking)
    val worst = data.minWith(ranking)
    /** Signed difference between the two selected whole trajectories, not a pointwise envelope. */
    val averageSpread: Money = (1..12).map { best.states[it].cash - worst.states[it].cash }.total().scaled(1.0 / 12)
    val curves get() = listOf(ProjectionCurve(CurveRole.REFERENCE, "Si rien ne change", "#707070", reference),
        ProjectionCurve(CurveRole.BEST_STUDIED, "Meilleur cas étudié : ${best.label}", "#238443", best),
        ProjectionCurve(CurveRole.WORST_STUDIED, "Pire cas étudié : ${worst.label}", "#CB181D", worst))
}

object FinancialProjections {
    /** No calendar generation. Pass only already-known future events; unspecified choices keep baseline behavior.
     * Explicit selections represent commitments already made and are respected, including an accepted offer. */
    fun nothingChanges(profile: MonthlyProfile, configuration: MonthlyConfiguration, state: MonthlyState,
        knownEvents: List<LifeEvent> = emptyList(), commitments: List<DecisionSelection> = emptyList()): TwelveMonthProjection =
        project("Si rien ne change", profile, configuration, state, knownEvents, commitments)

    fun nothingChanges(session: SimulationSession, knownEvents: List<LifeEvent> = session.calendar.filter {
        it.month >= session.currentMonth }): TwelveMonthProjection =
        nothingChanges(session.profile, session.configuration, session.currentState, knownEvents,
            session.selections.filter { choice -> knownEvents.any { it.id == choice.eventId } })

    /** Public scenario runner also supports separately labelled stress projections. */
    fun project(label: String, profile: MonthlyProfile, configuration: MonthlyConfiguration, state: MonthlyState,
        knownEvents: List<LifeEvent> = emptyList(), choices: List<DecisionSelection> = emptyList()): TwelveMonthProjection {
        require(state.month + 12 <= MonthlyConfiguration.MAX_MONTHS)
        require(knownEvents.all { it.month >= state.month })
        require(choices.map { it.eventId }.distinct().size == choices.size)
        require(choices.all { choice -> knownEvents.any { it.id == choice.eventId && it.interactive } })
        val config = configuration.copy(horizonMonths = maxOf(configuration.horizonMonths, state.month + 12),
            randomEvents = configuration.randomEvents.copy(enabled = false))
        val events = knownEvents.filter { it.month < state.month + 12 }.filter { event ->
            event !is LifeEvent.PurchaseChoice || choices.any { it.eventId == event.id }
        }
        EventCalendar.validate(events, config.horizonMonths)
        var current = state
        val records = mutableListOf<MonthlyRecord>()
        repeat(12) {
            val thisMonth = events.filter { it.month == current.month }
            val selected = choices.filter { choice -> thisMonth.any { it.id == choice.eventId } }.toMutableList()
            while (true) {
                when (val step = MonthlySimulation.calculateMonth(profile, config, current, thisMonth, selected, events.filter { it.month > current.month })) {
                    is MonthStep.Calculated -> { records += step.record; current = step.record.after; break }
                    is MonthStep.AwaitingDecision -> step.decisions.forEach { pending ->
                        val option = when (pending.event) {
                            is LifeEvent.Internship, is LifeEvent.ShortInternshipOffer -> "decline"
                            is LifeEvent.RentRise, is LifeEvent.HousingChoice, is LifeEvent.EpflJobOffer -> "stay"
                            else -> error("Provide an explicit choice for ${pending.event.id}")
                        }
                        selected += DecisionSelection(pending.event.id, option)
                    }
                }
            }
        }
        return TwelveMonthProjection(label, state, records, listOf(
            "12 transitions ; inflation annuelle ${config.annualInflation}, indexation mensuelle effective.",
            "Aucun tirage aléatoire ; seuls les événements fournis sont connus : ${events.map { it.id }}.",
            "Sans choix explicite : logement conservé, nouvelles offres refusées, achats discrétionnaires exclus.",
            "Engagements actifs, échéances académiques et impayés conservés ; aucun prêt automatique.",
            "Dépenses exigibles et paiements effectifs séparés ; cash théorique = cash disponible − obligations impayées."))
    }

    /** Explicit finite set: stay/move now × accept/decline supplied short offer. External events are identical.
     * Other interactive external events keep baseline responses; no stress event is added implicitly. */
    fun compareDecisions(profile: MonthlyProfile, configuration: MonthlyConfiguration, state: MonthlyState,
        knownEvents: List<LifeEvent> = emptyList(), offer: LifeEvent.ShortInternshipOffer? = null): ProjectionComparison {
        require(knownEvents.none { it is LifeEvent.HousingChoice || it is LifeEvent.ShortInternshipOffer || it is LifeEvent.PurchaseChoice })
        require(offer == null || (offer.month in state.month until state.month + 12 &&
            state.status == LifeStatus.STUDENT && state.shortInternship?.let { it.startMonth + 3 > state.month } != true))
        val calendar = knownEvents + listOfNotNull(offer)
        val reference = nothingChanges(profile, configuration, state, calendar)
        val possibilities = mutableListOf<TwelveMonthProjection>()
        for (move in if (state.housing.type == HousingType.SHARED) listOf(false) else listOf(false, true)) {
            for (accept in if (offer == null) listOf(false) else listOf(false, true)) {
                val housing = if (move) MonthlySimulation.sharedHousingChoice(state.month, configuration) else null
                val choices = listOfNotNull(housing?.let { DecisionSelection(it.id, "move") },
                    offer?.let { DecisionSelection(it.id, if (accept) "accept" else "decline") })
                val result = project("${if (move || state.housing.type == HousingType.SHARED) "Colocation" else "Logement actuel"}, ${if (accept) "stage accepté" else "sans nouveau stage"}",
                    profile, configuration, state, calendar + listOfNotNull(housing), choices)
                // Exclude alternatives whose intended intervention is incompatible at its actual date.
                if (accept && result.records.flatMap { it.events }.none { it.eventId == offer?.id && it.application == EventApplication.APPLIED }) continue
                possibilities += result
            }
        }
        return ProjectionComparison(reference, possibilities)
    }
}

enum class BudgetBalance { SURPLUS, BALANCED, STRUCTURAL_DEFICIT }
enum class FinancialStability { STABLE, WATCH, FRAGILE, CRITICAL }
data class StabilitySettings(val essentialOtherShare: Double = 0.0, val unexpectedExpense: Money = Money.chf(500),
    val balanceTolerance: Money = Money.chf(10), val significantUnpaid: Money = Money.chf(100)) {
    init { require(essentialOtherShare.isFinite() && essentialOtherShare in 0.0..1.0)
        requireCost(unexpectedExpense); requireCost(balanceTolerance); requireCost(significantUnpaid) }
}
data class MonthlyCoverage(val month: Int, val essentialExpenses: Money, val reserve: Money,
    val mobilizableCash: Money, val monthsCovered: Double?, val threeMonths: Money, val sixMonths: Money,
    val structuralBalance: Money, val balance: BudgetBalance,
    val absorbsUnexpectedExpense: Boolean, val uncommittedCash: Money)
class StabilityAssessment internal constructor(val category: FinancialStability, coverage: List<MonthlyCoverage>,
    val averageSpread: Money?, val relativeSpread: Double?, val deficitMonths: Int,
    val maximumFinancingNeed: Money, val finalUnpaid: Money, val minimumCash: Money,
    val reserveExhausted: Boolean, reasons: List<String>) {
    private val coverageData = coverage.toList()
    private val reasonData = reasons.toList()
    val coverage get() = coverageData.toList()
    val reasons get() = reasonData.toList()
}

object FinancialStabilityAnalysis {
    fun evaluate(profile: MonthlyProfile, configuration: MonthlyConfiguration, projection: TwelveMonthProjection,
        comparison: ProjectionComparison? = null, settings: StabilitySettings = StabilitySettings()): StabilityAssessment {
        require(comparison == null || comparison.reference.initialState == projection.initialState)
        val terminal = projection.finalState
        val index = configuration.priceIndex(terminal.month)
        val terminalSalary = MonthlySimulation.professionalIncome(profile, configuration, terminal)
        val terminalFlow = MonthlyFlow(
            professionalIncome = terminalSalary, taxes = terminalSalary.scaled(configuration.incomeTaxRate),
            rent = MonthlySimulation.rentAt(terminal, configuration), healthInsurance = profile.healthInsurance.scaled(index),
            groceries = profile.groceries.scaled(index), otherExpenses = profile.otherExpenses.scaled(index),
            interest = terminal.debt.scaled(FinancialSimulation.effectiveMonthlyRate(configuration.annualDebtInterest)))
        val coverage = (projection.records.map { it.before to it.flow } + (terminal to terminalFlow)).map { (state, flow) ->
            val tuition = if (state.studyMonthsRemaining > 0)
                profile.tuitionPayment.scaled(configuration.priceIndex(state.month) / profile.tuitionEveryStudyMonths) else Money.ZERO
            val essential = flow.rent + flow.healthInsurance + flow.groceries + tuition +
                flow.otherExpenses.scaled(settings.essentialOtherShare)
            // S005 and the short internship are temporary; neither finances permanent commitments.
            val permanentIncome = if (state.studyMonthsRemaining > 0) profile.studentMonthlyIncome else flow.professionalIncome
            val balance = permanentIncome - flow.recurringExpenses - tuition - flow.interest - flow.taxes
            val uncommitted = (state.cash - state.accountsPayable -
                (state.envelopes.total - state.envelopes.reserve)).nonNegative()
            MonthlyCoverage(state.month, essential, state.envelopes.reserve, state.cash,
                if (essential == Money.ZERO) null else state.cash.cents.toDouble() / essential.cents,
                essential.scaled(3.0), essential.scaled(6.0), balance,
                when { balance < -settings.balanceTolerance -> BudgetBalance.STRUCTURAL_DEFICIT
                    balance > settings.balanceTolerance -> BudgetBalance.SURPLUS; else -> BudgetBalance.BALANCED },
                state.cash >= settings.unexpectedExpense + state.unpaid.items.map { it.missing }.total(), uncommitted)
        }
        val deficitMonths = projection.deficitMonths.size
        val maximum = projection.records.maxOf { it.financingNeed }
        val finalUnpaid = projection.records.last().financingNeed
        val minimumCash = projection.states.minOf { it.cash }
        val structural = coverage.any { it.balance == BudgetBalance.STRUCTURAL_DEFICIT }
        val lowCoverage = coverage.any { it.monthsCovered != null && it.monthsCovered < 3.0 }
        val category = when {
            maximum > settings.significantUnpaid || deficitMonths >= 2 -> FinancialStability.CRITICAL
            deficitMonths > 0 || structural || coverage.any { it.monthsCovered != null && it.monthsCovered < 1.0 } -> FinancialStability.FRAGILE
            lowCoverage || coverage.any { !it.absorbsUnexpectedExpense } -> FinancialStability.WATCH
            else -> FinancialStability.STABLE
        }
        val spread = comparison?.averageSpread
        val essentialAverage = coverage.take(12).map { it.essentialExpenses }.total().scaled(1.0 / 12)
        return StabilityAssessment(category, coverage, spread,
            if (spread != null && essentialAverage > Money.ZERO) spread.cents.toDouble() / essentialAverage.cents else null,
            deficitMonths, maximum, finalUnpaid, minimumCash,
            projection.states.any { it.envelopes.reserve == Money.ZERO }, buildList {
                add("$deficitMonths mois avec obligations impayées ; besoin maximal $maximum ; minimum disponible $minimumCash.")
                if (structural) add("Budget structurellement déficitaire : les revenus temporaires ne sont pas permanents.")
                if (lowCoverage) add("Couverture inférieure à trois mois à au moins une date ; repère prudent, pas seuil universel.")
                add("La réserve affectée est distincte du cash mobilisable ; utiliser les autres enveloppes expose les factures futures.")
                add("La dispersion mesure la sensibilité aux seuls scénarios étudiés, jamais la stabilité ni une probabilité.")
                add("Indicateur pédagogique FinLife, ni notation officielle UBS ni conseil financier personnalisé.")
            })
    }
}
