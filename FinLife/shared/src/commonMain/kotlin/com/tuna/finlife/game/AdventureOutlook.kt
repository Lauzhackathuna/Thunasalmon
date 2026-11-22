package com.tuna.finlife.game

import com.tuna.finlife.simulation.*

/** All UI monetary metrics use these same definitions, in cents, until drawing pixels. */
internal enum class BalanceMetric {
    Cash, NetWorth, Theoretical;
    fun value(state: MonthlyState): Money = when (this) {
        Cash -> state.cash
        NetWorth -> state.netWorth
        Theoretical -> state.theoreticalCash
    }
}

internal data class FutureOption(
    val choices: Set<WhatIf>,
    val projection: TwelveMonthProjection,
    val stability: StabilityAssessment,
) {
    fun at(offset: Int): MonthlyState = projection.states[offset]
}

internal data class FutureImpact(val income: Money, val rentSaved: Money, val movingCosts: Money,
    val paidExpenses: Money, val unpaid: Money, val tuition: Money)

/** Product adapter only: every option is a full engine trajectory, never a sum of estimated effects. */
internal class AdventureOutlook private constructor(val options: List<FutureOption>) {
    val baseline get() = options.first { it.choices.isEmpty() }
    fun selected(choices: Set<WhatIf>) = options.firstOrNull { it.choices == choices } ?: baseline
    fun impact(option: FutureOption): FutureImpact {
        fun sum(p: TwelveMonthProjection, field: (MonthlyFlow) -> Money) =
            p.records.fold(Money.ZERO) { total, r -> total + field(r.flow) }
        val a = option.projection
        val b = baseline.projection
        return FutureImpact(sum(a) { it.income } - sum(b) { it.income },
            sum(b) { it.rent } - sum(a) { it.rent },
            sum(a) { it.decisionCosts } - sum(b) { it.decisionCosts },
            sum(a) { it.paidExpenses } - sum(b) { it.paidExpenses },
            option.stability.finalUnpaid - baseline.stability.finalUnpaid, sum(a) { it.tuition })
    }

    companion object {
        /** [sensitivity] changes the engine's inputs for this projection only; neutral = the run's own values. */
        fun calculate(base: SimulationSession, internshipSalary: Long, sensitivity: Sensitivity = Sensitivity()): AdventureOutlook {
            val states = AdventureRun.whatIfStates(base)
            val available = WhatIf.entries.filter { states[it] == WhatIfState.Available }
            val choices: List<Set<WhatIf>> = available.fold(listOf(emptySet())) { current, value ->
                current + current.map { it + value }
            }
            val profile = sensitivity.apply(base.profile)
            val options = choices.map { selected ->
                val proposed = AdventureRun.withWhatIfs(base, selected, internshipSalary)
                // Calendar is only what is already known. Include this month's already drawn problems,
                // even when their IDs start with random-. Never draw any future random events.
                val known = proposed.calendar.filter { it.month >= proposed.currentMonth && it !is LifeEvent.CompanyClosure }
                val commitments = proposed.selections.filter { choice -> known.any { it.id == choice.eventId } }
                val projection = FinancialProjections.nothingChanges(profile, proposed.configuration,
                    sensitivity.apply(proposed.currentState), known, commitments)
                FutureOption(selected, projection, FinancialStabilityAnalysis.evaluate(profile, base.configuration, projection))
            }
            check(options.all { it.projection.initialState == sensitivity.apply(base.currentState) })
            return AdventureOutlook(options)
        }
    }
}
