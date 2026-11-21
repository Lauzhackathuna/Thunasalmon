package com.tuna.finlife.simulation

/** All monetary inputs are CHF nominal in their payment year. */
data class AnnualPlan(
    val income: Double,
    val normalExpenses: Double,
    val oneOffExpenses: Double = 0.0,
    val borrowing: Double = 0.0,
    val principalRepayment: Double = 0.0,
    val breakdown: ExpenseBreakdown? = null,
) {
    init {
        listOf(income, normalExpenses, oneOffExpenses, borrowing, principalRepayment)
            .forEach { require(it.isFinite() && it >= 0.0) }
    }
}

data class InitialBalances(
    val cash: Double = 8_000.0,
    val investments: Double = 0.0,
    val debt: Double = 0.0,
) {
    init {
        require(cash.isFinite())
        require(investments.isFinite() && investments >= 0.0)
        require(debt.isFinite() && debt >= 0.0)
    }
}

/** S040: a one-time bill borne by the character, regardless of housing tenure. */
data class UnexpectedMaintenance(
    val enabled: Boolean = false,
    val paymentYear: Int = 4,
    val nominalAmount: Double = 6_000.0,
) {
    init {
        require(paymentYear >= 0)
        require(nominalAmount.isFinite() && nominalAmount >= 0.0)
    }
}

data class SimulationParameters(
    val annualPlans: List<AnnualPlan>? = null,
    val initial: InitialBalances = InitialBalances(),
    val investmentShare: Double = 0.0,
    val investmentReturn: Double = 0.03,
    val debtInterestRate: Double = 0.06,
    val goal: Double? = 350_000.0,
    val maintenance: UnexpectedMaintenance = UnexpectedMaintenance(),
    val internship: Boolean = false,
    val rentIncrease: RentIncrease = RentIncrease(),
    val assumptions: FinancialAssumptions = FinancialAssumptions(),
    val years: Int = 10,
) {
    init {
        require(years > 0)
        require(annualPlans == null || annualPlans.isNotEmpty())
        require(annualPlans == null || (!internship && !rentIncrease.enabled)) {
            "Custom nominal plans cannot be combined with generated S005/S037 plans"
        }
        require(investmentShare.isFinite() && investmentShare in 0.0..1.0)
        require(investmentReturn.isFinite() && investmentReturn >= -1.0)
        require(debtInterestRate.isFinite() && debtInterestRate >= 0.0)
        require(goal == null || (goal.isFinite() && goal > 0.0))
        require(!maintenance.enabled || maintenance.paymentYear in 0 until (annualPlans?.size ?: years))
    }
}

/** State t precedes the flows of year t; year t changes state t+1. */
data class FinancialState(
    val year: Int,
    val cash: Double,
    val investments: Double,
    val debt: Double,
    val goalPercentage: Double?,
    val realNetWorth: Double,
    val age: Int = 20 + year,
) {
    val netWorth: Double get() = cash + investments - debt
}

data class AnnualCashFlow(
    val year: Int,
    val income: Double,
    val normalExpenses: Double,
    val oneOffExpenses: Double,
    val maintenanceExpense: Double,
    val interest: Double,
    val borrowing: Double,
    val principalRepayment: Double,
    val invested: Double,
    val needsFinancing: Boolean,
    val breakdown: ExpenseBreakdown? = null,
) {
    /** Maintenance is already included in oneOffExpenses. Principal is not an expense. */
    val expenses: Double get() = normalExpenses + oneOffExpenses + interest
    val netCashFlow: Double get() = income + borrowing - expenses - principalRepayment
}

data class SimulationResult(val states: List<FinancialState>, val flows: List<AnnualCashFlow>) {
    val finalState: FinancialState get() = states.last()
}

data class ScenarioDefinition(
    val id: String,
    val category: String,
    val scenario: String,
    val baseline: String,
    val intervention: String,
)

object ScenarioCatalog {
    val active: List<ScenarioDefinition> get() = listOf(
        ScenarioDefinition("S005", "Education & skills", "Internship year", "Continue studies", "Paid internship year"),
        ScenarioDefinition("S037", "Housing & property", "Rent increase", "Stable rent", "Annual rent rises"),
        maintenance,
    )
    val requestedIds: List<String> get() = listOf("S005", "S037", "S040")
    val maintenance: ScenarioDefinition get() = ScenarioDefinition(
        "S040", "Housing & property", "Unexpected maintenance", "Normal upkeep", "Major repair bill",
    )
}

/** Numeric defaults are team assumptions, not official UBS figures. */
data class FinancialAssumptions(
    val inflation: Double = 0.02,
    val initialRent: Double = 9_600.0,
    val essentialExpenses: Double = 12_000.0,
    val discretionaryExpenses: Double = 6_000.0,
    val studyFees: Double = 1_500.0,
    val studentIncome: Double = 12_000.0,
    val internshipIncome: Double = 30_000.0,
    val startingSalary: Double = 72_000.0,
    val nominalSalaryGrowth: Double = 0.02,
) {
    init {
        require(inflation.isFinite() && inflation > -1.0)
        require(nominalSalaryGrowth.isFinite() && nominalSalaryGrowth > -1.0)
        listOf(initialRent, essentialExpenses, discretionaryExpenses, studyFees,
            studentIncome, internshipIncome, startingSalary).forEach { require(it.isFinite() && it >= 0.0) }
    }
}

data class RentIncrease(
    val enabled: Boolean = false,
    val annualExtraRate: Double = 0.03,
    val startYear: Int = 2,
) {
    init {
        require(annualExtraRate.isFinite() && annualExtraRate >= 0.0)
        require(startYear >= 0)
    }
}

/** Breakdown of normalExpenses; these components must not be added a second time. */
data class ExpenseBreakdown(
    val rent: Double,
    val essential: Double,
    val discretionary: Double,
    val studies: Double,
) {
    val total: Double get() = rent + essential + discretionary + studies
}

enum class ScenarioId { S005, S037, S040 }
