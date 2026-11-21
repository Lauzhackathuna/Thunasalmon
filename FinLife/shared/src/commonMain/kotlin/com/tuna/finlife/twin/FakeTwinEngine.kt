package com.tuna.finlife.twin

import com.tuna.finlife.model.DemoScenarios
import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.GoalStatus
import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import com.tuna.finlife.model.ScenarioId
import com.tuna.finlife.model.appliesTo

/** Deterministic demo engine using synthetic CHF assumptions. */
class FakeTwinEngine : TwinEngine {
    override fun initialSnapshot(persona: Persona): FinancialSnapshot {
        val (profile, scenario) = syntheticPersona(persona)
        return snapshot(
            monthIndex = -1,
            cash = profile.openingCashChf,
            recurringExpenses = scenario.phases.first().monthlyExpensesChf,
            propertyValue = propertyValue(persona, decline = false),
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
        fun isActive(id: ScenarioId): Boolean =
            month >= DemoScenarios.FIRST_EFFECT_MONTH_INDEX && id in activeScenarios && id.appliesTo(persona)

        val higherRent = isActive(ScenarioId.S037)
        val propertyDecline = isActive(ScenarioId.S039)
        val internship = isActive(ScenarioId.S005)
        val recurringExpenses = phase.monthlyExpensesChf + if (higherRent) DemoScenarios.RENT_SURCHARGE_CHF else 0.0
        val expenses = recurringExpenses + oneOffCosts.sumOf { it.amountChf }
        val income = if (internship) DemoScenarios.INTERNSHIP_INCOME_CHF else phase.monthlyIncomeChf
        val cash = current.cash + income - expenses - playerSpendThisMonth
        return MonthOutcome(
            snapshot = snapshot(month, cash, recurringExpenses, propertyValue(persona, propertyDecline)),
            income = income,
            expenses = expenses,
            events = buildList {
                addAll(oneOffCosts.map { it.event })
                if (higherRent) add(LifeEvent.HigherRent)
                if (propertyDecline) add(LifeEvent.LowerPropertyValue)
                if (internship) add(LifeEvent.PaidInternship)
            },
        )
    }

    private fun propertyValue(persona: Persona, decline: Boolean): Double = when {
        !ScenarioId.S039.appliesTo(persona) -> 0.0
        decline -> DemoScenarios.PROPERTY_ALTERNATIVE_CHF
        else -> DemoScenarios.PROPERTY_BASELINE_CHF
    }

    private fun snapshot(
        monthIndex: Int,
        cash: Double,
        recurringExpenses: Double,
        propertyValue: Double,
    ): FinancialSnapshot {
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
            netWorth = cash + propertyValue,
            liquidityMonths = liquidity,
            goalFeasibility = goalStatus,
            constraintBreaches = if (cash < 0) 1 else 0,
        )
    }
}
