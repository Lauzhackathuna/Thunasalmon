package com.tuna.finlife.twin

import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.GoalStatus
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import com.tuna.finlife.model.ScenarioId

/** Deterministic demo engine using synthetic CHF assumptions. Scenario effects are added in U7. */
class FakeTwinEngine : TwinEngine {
    override fun initialSnapshot(persona: Persona): FinancialSnapshot {
        val (profile, scenario) = syntheticPersona(persona)
        return snapshot(
            monthIndex = -1,
            cash = profile.openingCashChf,
            recurringExpenses = scenario.phases.first().monthlyExpensesChf,
        )
    }

    override fun advanceMonth(
        persona: Persona,
        current: FinancialSnapshot,
        playerSpendThisMonth: Double,
        activeScenarios: Set<ScenarioId>,
    ): MonthOutcome {
        val (profile, scenario) = syntheticPersona(persona)
        val month = current.monthIndex + 1
        require(month in 0 until profile.horizonMonths)
        require(playerSpendThisMonth.isFinite() && playerSpendThisMonth >= 0)
        val phase = scenario.phases.single { month in it.startMonth until it.endMonth }
        val oneOffCosts = scenario.oneOffCosts.filter { it.month == month }
        val expenses = phase.monthlyExpensesChf + oneOffCosts.sumOf { it.amountChf }
        val cash = current.cash + phase.monthlyIncomeChf - expenses - playerSpendThisMonth
        return MonthOutcome(
            snapshot = snapshot(month, cash, phase.monthlyExpensesChf),
            income = phase.monthlyIncomeChf,
            expenses = expenses,
            events = oneOffCosts.map { it.event },
        )
    }

    private fun snapshot(monthIndex: Int, cash: Double, recurringExpenses: Double): FinancialSnapshot {
        // Synthetic definitions for the demo header, pending replacement by the real twin.
        val liquidity = cash / recurringExpenses
        val goalStatus = when {
            liquidity >= 2.0 -> GoalStatus.OnTrack
            liquidity >= 0.0 -> GoalStatus.AtRisk
            else -> GoalStatus.OffTrack
        }
        return FinancialSnapshot(
            monthIndex = monthIndex,
            cash = cash,
            netWorth = cash,
            liquidityMonths = liquidity,
            goalFeasibility = goalStatus,
            constraintBreaches = if (cash < 0) 1 else 0,
        )
    }
}
