package com.tuna.finlife.simulation

/** Deltas are selected minus alternative at the same completed date. Flows are cumulative through that date. */
data class MonthlyComparison(
    val month: Int,
    val cash: Money,
    val income: Money,
    val expenses: Money,
    val investments: Money,
    val investmentContributions: Money,
    val nominalNetWorth: Money,
    val realNetWorth: Money,
    val paidExpenses: Money = expenses,
)

data class AnnualMonthlySummary(
    val startMonth: Int,
    val endMonthExclusive: Int,
    val opening: MonthlyState,
    val closing: MonthlyState,
    val income: Money,
    val expenses: Money,
    val contributions: Money,
    val investmentReturn: Money,
    val borrowing: Money,
    val principalRepayment: Money,
    val paidExpenses: Money = expenses,
) {
    val isFullYear: Boolean get() = endMonthExclusive - startMonth == 12
}

object MonthlyComparisons {
    fun compare(selected: SimulationSession, alternative: SimulationSession, month: Int): MonthlyComparison {
        // Real values must share a base date/currency and inflation convention.
        require(selected.profile.profileId == alternative.profile.profileId)
        require(selected.configuration.annualInflation == alternative.configuration.annualInflation)
        val a = SimulationSessions.viewState(selected, month)
        val b = SimulationSessions.viewState(alternative, month)
        fun sum(session: SimulationSession, field: (MonthlyFlow) -> Money): Money =
            session.history.take(month).map { field(it.flow) }.total()
        return MonthlyComparison(month, a.cash - b.cash,
            sum(selected) { it.income } - sum(alternative) { it.income },
            sum(selected) { it.expenses } - sum(alternative) { it.expenses },
            a.investments - b.investments,
            sum(selected) { it.investmentContribution } - sum(alternative) { it.investmentContribution },
            a.netWorth - b.netWorth, a.realNetWorth - b.realNetWorth,
            sum(selected) { it.paidExpenses } - sum(alternative) { it.paidExpenses })
    }

    /** Aggregates only actual monthly transitions; includes a labelled partial final year. */
    fun annualSummaries(session: SimulationSession): List<AnnualMonthlySummary> = session.history.chunked(12).map { months ->
        fun sum(field: (MonthlyFlow) -> Money) = months.map { field(it.flow) }.total()
        AnnualMonthlySummary(months.first().month, months.last().after.month, months.first().before, months.last().after,
            sum { it.income }, sum { it.expenses }, sum { it.investmentContribution }, sum { it.investmentReturn },
            sum { it.newBorrowing }, sum { it.principalRepayment }, sum { it.paidExpenses })
    }
}
