package com.tuna.finlife.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinancialSimulationTest {
    private val enabled = SimulationParameters(maintenance = UnexpectedMaintenance(enabled = true))
    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-7)

    @Test fun referenceMatchesDocumentedFirstYear() {
        val result = FinancialSimulation.simulate()
        assertEquals(11, result.states.size)
        assertEquals(10, result.flows.size)
        near(-9_100.0, result.states[1].cash)
        near(29_100.0, result.flows[0].expenses)
    }

    @Test fun maintenanceDoesNotChangeEarlierStatesOrFlows() {
        val comparison = SimulationComparisons.maintenanceEffect(enabled)
        assertEquals(comparison.counterfactual.states.take(5), comparison.selected.states.take(5))
        assertEquals(comparison.counterfactual.flows.take(4), comparison.selected.flows.take(4))
        near(-6_000.0, comparison.states[5].netWorth)
    }

    @Test fun nominalBillIsCountedExactlyOnceEvenWithInflation() {
        val p = enabled.copy(annualPlans = FinancialSimulation.referencePlans(expenseInflation = 0.08))
        val comparison = SimulationComparisons.maintenanceEffect(p)
        near(6_000.0, comparison.selected.flows.sumOf { it.maintenanceExpense })
        near(6_000.0, comparison.selected.flows.sumOf { it.oneOffExpenses })
        near(6_000.0, comparison.flows.sumOf { it.expenses })
        near(-6_000.0, comparison.finalNetWorthDifference)
    }

    @Test fun cashDebtAndNetWorthReconcileWithExplicitLoanAndInvestments() {
        val p = enabled.copy(investmentShare = 0.6, annualPlans = List(10) { year ->
            AnnualPlan(60_000.0, 27_600.0, oneOffExpenses = if (year == 1) 20_000.0 else 0.0,
                borrowing = if (year == 1) 20_000.0 else 0.0,
                principalRepayment = if (year in 2..6) 4_000.0 else 0.0)
        })
        val result = FinancialSimulation.simulate(p)
        result.flows.forEach { flow ->
            val before = result.states[flow.year]
            val after = result.states[flow.year + 1]
            near(flow.netCashFlow - flow.invested, after.cash - before.cash)
            near(before.debt + flow.borrowing - flow.principalRepayment, after.debt)
            near(after.cash + after.investments - after.debt, after.netWorth)
            near(before.investments * 1.03 + flow.invested, after.investments)
            assertTrue(after.debt >= 0.0)
        }
        near(0.0, result.flows[1].interest)
        near(1_200.0, result.flows[2].interest)
    }

    @Test fun insufficientCashSignalsNeedWithoutImplicitDebtOrAssetSale() {
        val result = FinancialSimulation.simulate(enabled.copy(
            initial = InitialBalances(cash = 0.0, investments = 1_000.0),
            annualPlans = List(10) { AnnualPlan(0.0, 0.0) }, investmentReturn = 0.0))
        near(-6_000.0, result.finalState.cash)
        near(1_000.0, result.finalState.investments)
        assertTrue(result.flows[4].needsFinancing)
        assertTrue(result.flows.all { it.borrowing == 0.0 })
        assertTrue(result.states.all { it.debt == 0.0 })
    }

    @Test fun finalDifferenceWithoutInvestmentsIsMinusBill() {
        near(-6_000.0, SimulationComparisons.maintenanceEffect(enabled).finalNetWorthDifference)
    }

    @Test fun disabledMaintenanceHasZeroCounterfactualEffect() {
        val comparison = SimulationComparisons.maintenanceEffect(SimulationParameters())
        assertEquals(comparison.selected, comparison.counterfactual)
        assertTrue(comparison.states.all { it.netWorth == 0.0 })
        assertTrue(comparison.flows.all { it.income == 0.0 && it.expenses == 0.0 })
    }

    @Test fun amountAndYearAreConfigurableIncludingBoundaryYears() {
        for (year in listOf(0, 9)) {
            val result = FinancialSimulation.simulate(enabled.copy(
                maintenance = UnexpectedMaintenance(true, year, 321.0)))
            near(321.0, result.flows[year].maintenanceExpense)
            assertEquals(1, result.flows.count { it.maintenanceExpense != 0.0 })
        }
    }

    @Test fun interactionsAreRecomputedRatherThanSummed() {
        val baseline = SimulationParameters(annualPlans = List(10) { AnnualPlan(10_000.0, 8_000.0) })
        val combined = baseline.copy(investmentShare = 1.0, maintenance = enabled.maintenance)
        val effect = SimulationComparisons.maintenanceEffect(combined)
        val standalone = SimulationComparisons.maintenanceEffect(baseline.copy(maintenance = enabled.maintenance))
        assertTrue(effect.finalNetWorthDifference < standalone.finalNetWorthDifference)
        near(0.0, effect.selected.flows[4].invested)
        near(2_000.0, effect.counterfactual.flows[4].invested)
        assertEquals(FinancialSimulation.simulate(combined.copy(maintenance = UnexpectedMaintenance())),
            effect.counterfactual)
    }

    @Test fun simulationIsReproducibleAndDoesNotModifyInput() {
        val snapshot = enabled.copy(annualPlans = enabled.annualPlans?.toList())
        assertEquals(FinancialSimulation.simulate(enabled), FinancialSimulation.simulate(enabled))
        assertEquals(snapshot, enabled)
    }

    @Test fun invalidInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { UnexpectedMaintenance(nominalAmount = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { UnexpectedMaintenance(paymentYear = -1) }
        assertFailsWith<IllegalArgumentException> { enabled.copy(maintenance = UnexpectedMaintenance(true, 10)) }
        assertFailsWith<IllegalArgumentException> { enabled.copy(investmentShare = 1.1) }
        assertFailsWith<IllegalArgumentException> { enabled.copy(goal = 0.0) }
        assertFailsWith<IllegalArgumentException> { FinancialSimulation.simulate(enabled.copy(
            annualPlans = List(10) { AnnualPlan(0.0, 0.0, principalRepayment = 1.0) })) }
    }

    @Test fun inflationAndGoalAreExplicit() {
        near(121.0, FinancialSimulation.inflate(100.0, 0.1, 2))
        near(-9.1, FinancialSimulation.simulate(SimulationParameters(goal = 100_000.0)).states[1].goalPercentage!!)
    }

    @Test fun catalogAndExplanationExposeMaintenanceConvention() {
        assertEquals(listOf("S005", "S037", "S040"), ScenarioCatalog.requestedIds)
        assertEquals("Housing & property", ScenarioCatalog.maintenance.category)
        val explanation = FinancialExplanations.maintenance(enabled).joinToString(" ")
        assertTrue(explanation.contains("à la charge du personnage"))
        assertTrue(explanation.contains("aucune inflation supplémentaire"))
    }
}
