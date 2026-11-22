package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyFlow
import com.tuna.finlife.simulation.SimulationSession

/** One line of the engine's monthly flow. [sign]: +1 for what adds to net worth, −1 for what removes from it. */
internal enum class FlowItem(val sign: Int, val read: (MonthlyFlow) -> Money) {
    ParentIncome(+1, { it.studentIncome }),
    InternshipPay(+1, { it.internshipIncome }),
    Salary(+1, { it.professionalIncome }),
    Rent(-1, { it.rent }),
    HealthInsurance(-1, { it.healthInsurance }),
    Groceries(-1, { it.groceries }),
    OtherExpenses(-1, { it.otherExpenses }),
    Tuition(-1, { it.tuition }),
    UnexpectedCosts(-1, { it.exceptionalExpenses }),
    DecisionCosts(-1, { it.decisionCosts }),
    Interest(-1, { it.interest }),
    Taxes(-1, { it.taxes }),
    InvestmentReturn(+1, { it.investmentReturn }),
}

/** How much one flow item moved Maya's path above (+) or below (−) the dotted line, in net worth. */
internal data class FlowEffect(val item: FlowItem, val effect: Money)

/**
 * Why the two lines end apart: the gap in net worth at [month], split by flow item. Both paths are identical
 * before [fromMonth], and the engine checks every month that the change in net worth equals
 * income − expenses + investment return, so the effects add up exactly to [total]. No estimate is involved:
 * each effect is a sum of engine amounts.
 */
internal data class DifferenceBreakdown(
    val fromMonth: Int,
    val month: Int,
    val total: Money,
    /** Items that differ, largest effect first. */
    val effects: List<FlowEffect>,
) {
    companion object {
        fun of(main: SimulationSession, alternative: SimulationSession, fromMonth: Int): DifferenceBreakdown? {
            val month = minOf(main.currentMonth, alternative.currentMonth)
            if (month <= fromMonth) return null
            val mine = main.history.subList(fromMonth, month)
            val theirs = alternative.history.subList(fromMonth, month)
            val effects = FlowItem.entries.map { item ->
                val difference = mine.fold(Money.ZERO) { sum, record -> sum + item.read(record.flow) } -
                    theirs.fold(Money.ZERO) { sum, record -> sum + item.read(record.flow) }
                FlowEffect(item, if (item.sign > 0) difference else -difference)
            }.filter { it.effect != Money.ZERO }.sortedByDescending { kotlin.math.abs(it.effect.cents) }
            val total = main.states[month].netWorth - alternative.states[month].netWorth
            return DifferenceBreakdown(fromMonth, month, total, effects)
        }

        fun of(main: SimulationSession, counterfactual: Counterfactual): DifferenceBreakdown? =
            of(main, counterfactual.alternative, counterfactual.month)
    }
}
