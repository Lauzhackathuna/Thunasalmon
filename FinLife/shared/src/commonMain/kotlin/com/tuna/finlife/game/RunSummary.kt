package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SimulationSession

/** Totals over the months actually simulated when the player ends the run. */
internal data class RunSummary(
    val monthsSimulated: Int,
    /** Actual spendable cash; unpaid obligations are shown separately. */
    val cash: Money,
    val netWorth: Money,
    val totalIncome: Money,
    val totalExpenses: Money,
    val unpaid: Money,
    val theoretical: Money,
    val initialCash: Money,
    val initialNetWorth: Money,
    val totalPaid: Money,
    val internshipIncome: Money,
) {
    companion object {
        fun of(session: SimulationSession): RunSummary {
            val flows = session.history.map { it.flow }
            return RunSummary(
                monthsSimulated = flows.size,
                theoretical = session.currentState.theoreticalCash,
                initialCash = session.initialState.cash, initialNetWorth = session.initialState.netWorth,
                totalPaid = flows.fold(Money.ZERO) { total, flow -> total + flow.paidExpenses },
                internshipIncome = flows.fold(Money.ZERO) { total, flow -> total + flow.internshipIncome },
                cash = session.currentState.cash,
                netWorth = session.currentState.netWorth,
                totalIncome = flows.fold(Money.ZERO) { total, flow -> total + flow.income },
                totalExpenses = flows.fold(Money.ZERO) { total, flow -> total + flow.expenses },
                unpaid = session.currentState.unpaid.items.fold(Money.ZERO) { total, bill -> total + bill.missing },
            )
        }
    }
}
