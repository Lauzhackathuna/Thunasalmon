package com.tuna.finlife.simulation

import kotlin.math.max
import kotlin.math.pow

/** Pure annual engine, with no UI, clock, randomness, persistence or implicit financing. */
object FinancialSimulation {
    /** Explicit conversion for costs quoted at t=0. Never apply to a payment-year nominal bill. */
    fun inflate(baseAmount: Double, annualInflation: Double, year: Int): Double {
        require(baseAmount.isFinite() && baseAmount >= 0.0)
        require(annualInflation.isFinite() && annualInflation > -1.0)
        require(year >= 0)
        return (baseAmount * (1.0 + annualInflation).pow(year)).also { require(it.isFinite()) }
    }

    /** Builds the validated V1 reference; salary growth is already nominal. */
    fun referencePlans(years: Int = 10, expenseInflation: Double = 0.02): List<AnnualPlan> =
        plans(SimulationParameters(years = years, assumptions = FinancialAssumptions(inflation = expenseInflation)))

    /** Generates the combined S005/S037 path. S040 is applied once by simulate. */
    fun plans(parameters: SimulationParameters): List<AnnualPlan> {
        parameters.annualPlans?.let { return it.toList() }
        val a = parameters.assumptions
        val workStart = if (parameters.internship) 3 else 2
        val studyStart = if (parameters.internship) 1 else 0
        return List(parameters.years) { year ->
            val studying = year in studyStart until workStart
            val income = when {
                parameters.internship && year == 0 -> a.internshipIncome
                studying -> a.studentIncome
                else -> a.startingSalary * (1.0 + a.nominalSalaryGrowth).pow(year - workStart)
            }
            val rent = parameters.rentIncrease
            val extraRent = if (rent.enabled && year >= rent.startYear)
                (1.0 + rent.annualExtraRate).pow(year - rent.startYear + 1) else 1.0
            val expenses = ExpenseBreakdown(
                inflate(a.initialRent, a.inflation, year) * extraRent,
                inflate(a.essentialExpenses, a.inflation, year),
                inflate(a.discretionaryExpenses, a.inflation, year),
                if (studying) inflate(a.studyFees, a.inflation, year) else 0.0,
            )
            AnnualPlan(income, expenses.total, breakdown = expenses)
        }
    }

    fun simulate(parameters: SimulationParameters = SimulationParameters()): SimulationResult {
        val p = parameters
        val states = mutableListOf<FinancialState>()
        val flows = mutableListOf<AnnualCashFlow>()
        fun state(year: Int, cash: Double, investments: Double, debt: Double): FinancialState {
            val netWorth = cash + investments - debt
            val percentage = p.goal?.let { netWorth / it * 100.0 }
            require(listOf(cash, investments, debt, netWorth).all { it.isFinite() })
            require(percentage == null || percentage.isFinite())
            val priceIndex = (1.0 + p.assumptions.inflation).pow(year)
            require(priceIndex.isFinite() && priceIndex > 0.0)
            val realNetWorth = netWorth / priceIndex
            require(realNetWorth.isFinite())
            return FinancialState(year, cash, investments, debt, percentage, realNetWorth)
        }
        states += state(0, p.initial.cash, p.initial.investments, p.initial.debt)
        plans(p).forEachIndexed { year, plan ->
            val before = states.last()
            require(plan.principalRepayment <= before.debt + plan.borrowing) {
                "Principal repayment exceeds outstanding debt in year $year"
            }
            val maintenance = if (p.maintenance.enabled && p.maintenance.paymentYear == year)
                p.maintenance.nominalAmount else 0.0
            val oneOff = plan.oneOffExpenses + maintenance
            val interest = before.debt * p.debtInterestRate
            val netFlow = plan.income + plan.borrowing - plan.normalExpenses - oneOff - interest -
                plan.principalRepayment
            val invested = p.investmentShare * max(netFlow, 0.0)
            val after = state(
                year + 1,
                before.cash + netFlow - invested,
                before.investments * (1.0 + p.investmentReturn) + invested,
                before.debt + plan.borrowing - plan.principalRepayment,
            )
            flows += AnnualCashFlow(year, plan.income, plan.normalExpenses, oneOff, maintenance,
                interest, plan.borrowing, plan.principalRepayment, invested, after.cash < 0.0, plan.breakdown)
            states += after
        }
        return SimulationResult(states.toList(), flows.toList())
    }
}
