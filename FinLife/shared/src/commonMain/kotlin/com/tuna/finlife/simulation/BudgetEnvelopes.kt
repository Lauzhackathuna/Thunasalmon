package com.tuna.finlife.simulation

enum class Envelope { STUDIES, HOUSING, INSURANCE, GROCERIES, OTHER, RESERVE }

/** Value object: no mutable map can leak across branches. */
data class EnvelopeBalances(
    val housing: Money = Money.ZERO, val insurance: Money = Money.ZERO,
    val groceries: Money = Money.ZERO, val studies: Money = Money.ZERO,
    val other: Money = Money.ZERO, val reserve: Money = Money.ZERO,
) {
    init { listOf(housing, insurance, groceries, studies, other, reserve).forEach(::requireCost) }
    operator fun get(category: Envelope): Money = when (category) {
        Envelope.HOUSING -> housing; Envelope.INSURANCE -> insurance; Envelope.GROCERIES -> groceries
        Envelope.STUDIES -> studies; Envelope.OTHER -> other; Envelope.RESERVE -> reserve
    }
    val total: Money get() = Envelope.entries.map { get(it) }.total()
    internal fun with(category: Envelope, value: Money): EnvelopeBalances = when (category) {
        Envelope.HOUSING -> copy(housing = value); Envelope.INSURANCE -> copy(insurance = value)
        Envelope.GROCERIES -> copy(groceries = value); Envelope.STUDIES -> copy(studies = value)
        Envelope.OTHER -> copy(other = value); Envelope.RESERVE -> copy(reserve = value)
    }
    companion object {
        fun alexia() = EnvelopeBalances(Money.chf(3000), Money.chf(1000), Money.chf(750),
            Money.chf(1600), Money.chf(300), Money.chf(1350))
    }
}

data class IncomeAllocation(val category: Envelope, val amount: Money)
data class EnvelopeTransfer(val from: Envelope, val to: Envelope, val amount: Money,
    val obligationId: String, val reason: String, val futureFundingAtRisk: Money)
data class FinancialObligation(val id: String, val dueMonth: Int, val category: Envelope,
    val amount: Money, val paid: Money = Money.ZERO, val bankPrincipal: Boolean = false) {
    init { require(id.isNotBlank() && dueMonth >= 0); requireCost(amount); requireCost(paid); require(paid <= amount) }
    val missing: Money get() = amount - paid
}
data class ObligationPayment(val obligation: FinancialObligation, val amount: Money)

class BudgetLedger internal constructor(
    val before: EnvelopeBalances, val after: EnvelopeBalances,
    allocations: List<IncomeAllocation>, transfers: List<EnvelopeTransfer>,
    payments: List<ObligationPayment>, outstanding: List<FinancialObligation>,
    val borrowing: Money, val invested: Money,
) {
    private val allocationData = allocations.toList()
    private val transferData = transfers.toList()
    private val paymentData = payments.toList()
    private val outstandingData = outstanding.toList()
    val allocations get() = allocationData.toList()
    val transfers get() = transferData.toList()
    val payments get() = paymentData.toList()
    val outstanding get() = outstandingData.toList()
    val paidExpenses get() = paymentData.filter { !it.obligation.bankPrincipal }.map { it.amount }.total()
    val paidPrincipal get() = paymentData.filter { it.obligation.bankPrincipal }.map { it.amount }.total()
    fun explanation(): List<String> = allocationData.map { "Revenu affecté à ${it.category} : ${it.amount}." } +
        transferData.map { "${it.reason} : transfert de ${it.amount} depuis ${it.from} vers ${it.to} pour ${it.obligationId}. Provision future exposée : ${it.futureFundingAtRisk}." } +
        paymentData.map { "${it.obligation.id} : exigible ${it.obligation.missing}, payé ${it.amount}." } +
        outstandingData.map { "${it.id} reste impayée à hauteur de ${it.missing}." }
}

object BudgetEnvelopes {
    val fundingPriority get() = Envelope.entries.toList()
    val withdrawalPriority get() = listOf(Envelope.OTHER, Envelope.RESERVE, Envelope.GROCERIES,
        Envelope.INSURANCE, Envelope.HOUSING, Envelope.STUDIES)

    /** Targets cover current recurring bills and the next two academic deadlines (including today).
     * Known S005 suspension shifts deadlines; inflation is evaluated at each expected payment date.
     * Targets are recomputed every month, after applying decisions and contractual changes. */
    internal fun targets(profile: MonthlyProfile, config: MonthlyConfiguration, state: MonthlyState,
        flow: MonthlyFlow, knownFutureEvents: List<LifeEvent> = emptyList()): EnvelopeBalances {
        var studies = Money.ZERO
        var progress = state.studyMonthsCompleted
        var month = state.month + state.internshipMonthsRemaining
        var deadlines = 0
        val committedStages = knownFutureEvents.filterIsInstance<LifeEvent.Internship>().filter { !it.interactive }.associateBy { it.month }
        while (progress < profile.studyMonths && deadlines < 2) {
            committedStages[month]?.let { month += it.durationMonths }
            if (progress % profile.tuitionEveryStudyMonths == 0 && progress > state.lastPaidTuitionProgress) {
                studies += profile.tuitionPayment.scaled(config.priceIndex(month))
                deadlines++
            }
            progress++; month++
        }
        // Explicit known exceptional bills are earmarked in OTHER over the next twelve months.
        // Unanswered optional purchases/stages/housing choices are not assumed accepted.
        val futureBills = knownFutureEvents.filter { it.month > state.month && it.month < state.month + 12 }.map {
            when (it) {
                is LifeEvent.OneOffExpense -> if (it.responsibility == PaymentResponsibility.CHARACTER) it.amount else Money.ZERO
                is LifeEvent.Maintenance -> if (it.responsibility == PaymentResponsibility.CHARACTER) it.nominalAmount else Money.ZERO
                else -> Money.ZERO
            }
        }.total()
        return EnvelopeBalances(flow.rent, flow.healthInsurance, flow.groceries, studies, flow.otherExpenses + futureBills)
    }

    fun settle(before: EnvelopeBalances, income: Money, borrowing: Money, targets: EnvelopeBalances,
        obligations: List<FinancialObligation>, investmentLimit: Money = Money.ZERO, currentMonth: Int? = null): BudgetLedger {
        listOf(income, borrowing, investmentLimit).forEach(::requireCost)
        require(obligations.map { it.id }.distinct().size == obligations.size)
        var balances = before
        var remaining = income
        val allocations = mutableListOf<IncomeAllocation>()
        for (category in fundingPriority) {
            val due = obligations.filter { it.category == category }.map { it.missing }.total()
            val arrears = obligations.filter { it.category == category && currentMonth != null && it.dueMonth < currentMonth }
                .map { it.missing }.total()
            val need = (maxOf(targets[category] + arrears, due) - balances[category]).nonNegative()
            val amount = if (category == Envelope.RESERVE) remaining else minOf(remaining, need)
            if (amount > Money.ZERO) {
                balances = balances.with(category, balances[category] + amount)
                allocations += IncomeAllocation(category, amount)
                remaining -= amount
            }
        }
        balances = balances.copy(reserve = balances.reserve + borrowing)
        var remainingTargets = targets
        val transfers = mutableListOf<EnvelopeTransfer>()
        val payments = mutableListOf<ObligationPayment>()
        val outstanding = mutableListOf<FinancialObligation>()
        for (bill in obligations.sortedWith(compareBy({ it.dueMonth }, { it.category.ordinal }, { it.id }))) {
            var missing = (bill.missing - balances[bill.category]).nonNegative()
            for (source in withdrawalPriority.filter { it != bill.category }) {
                val amount = minOf(missing, balances[source])
                if (amount == Money.ZERO) continue
                val exposed = minOf(amount, (remainingTargets[source] - (balances[source] - amount)).nonNegative())
                balances = balances.with(source, balances[source] - amount)
                    .with(bill.category, balances[bill.category] + amount)
                transfers += EnvelopeTransfer(source, bill.category, amount, bill.id,
                    "Enveloppe ${bill.category} insuffisante pour la facture", exposed)
                missing -= amount
            }
            val paid = minOf(bill.missing, balances[bill.category])
            balances = balances.with(bill.category, balances[bill.category] - paid)
            payments += ObligationPayment(bill, paid)
            if (currentMonth == null || bill.dueMonth == currentMonth) {
                remainingTargets = remainingTargets.with(bill.category, (remainingTargets[bill.category] - bill.amount).nonNegative())
            }
            if (paid < bill.missing) outstanding += bill.copy(paid = bill.paid + paid)
        }
        // Only uncommitted reserve is invested; earmarked future bills remain funded.
        val invested = if (outstanding.isEmpty()) minOf(investmentLimit, balances.reserve) else Money.ZERO
        balances = balances.copy(reserve = balances.reserve - invested)
        check(balances.total == before.total + income + borrowing - payments.map { it.amount }.total() - invested)
        return BudgetLedger(before, balances, allocations, transfers, payments, outstanding, borrowing, invested)
    }
}

class UnpaidObligations(items: List<FinancialObligation> = emptyList()) {
    private val data = items.toList()
    val items get() = data.toList()
    init { require(data.all { it.missing > Money.ZERO }); require(data.map { it.id }.distinct().size == data.size) }
    override fun equals(other: Any?) = other is UnpaidObligations && data == other.data
    override fun hashCode() = data.hashCode()
}

data class ShortInternship(val offerMonth: Int, val startMonth: Int, val monthlyIncome: Money) {
    init { require(offerMonth >= 0 && startMonth >= offerMonth); requireCost(monthlyIncome) }
    fun active(month: Int) = month >= startMonth && month < startMonth + 3
}
data class HousingDecision(val month: Int, val reason: String, val eventId: String)
