package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SimulationSession

/** Totals over the months actually simulated when the player ends the run. */
internal data class RunSummary(
    val monthsSimulated: Int,
    /** Cash minus unpaid bills (the engine's theoreticalCash): negative when Maya cannot pay. */
    val cash: Money,
    val netWorth: Money,
    val totalIncome: Money,
    val totalExpenses: Money,
    val unpaid: Money,
) {
    companion object {
        fun of(session: SimulationSession): RunSummary {
            val flows = session.history.map { it.flow }
            return RunSummary(
                monthsSimulated = flows.size,
                cash = session.currentState.theoreticalCash,
                netWorth = session.currentState.netWorth,
                totalIncome = flows.fold(Money.ZERO) { total, flow -> total + flow.income },
                totalExpenses = flows.fold(Money.ZERO) { total, flow -> total + flow.expenses },
                unpaid = session.currentState.unpaid.items.fold(Money.ZERO) { total, bill -> total + bill.missing },
            )
        }
    }
}
