package com.tuna.finlife.simulation

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScenarioSimulationTest {
    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-7)
    private val combined = SimulationParameters(internship = true,
        rentIncrease = RentIncrease(enabled = true), maintenance = UnexpectedMaintenance(enabled = true))

    @Test fun studyAndInternshipTimelinesMatchValidatedAmounts() {
        val reference = FinancialSimulation.simulate()
        val internship = FinancialSimulation.simulate(SimulationParameters(internship = true))
        assertEquals(listOf(12_000.0, 12_000.0, 72_000.0, 73_440.0), reference.flows.take(4).map { it.income })
        assertEquals(listOf(30_000.0, 12_000.0, 12_000.0, 72_000.0, 73_440.0), internship.flows.take(5).map { it.income })
        assertEquals(listOf(1_500.0, 1_530.0, 0.0, 0.0), reference.flows.take(4).map { it.breakdown!!.studies })
        near(0.0, internship.flows[0].breakdown!!.studies)
        near(1_530.0, internship.flows[1].breakdown!!.studies)
        near(1_560.6, internship.flows[2].breakdown!!.studies)
        near(0.0, internship.flows[3].breakdown!!.studies)
        near(10_400.0, internship.states[1].cash)
    }

    @Test fun rentIncreaseStartsAtYearTwoAndCompoundsOnTopOfInflation() {
        val comparison = SimulationComparisons.effect(SimulationParameters(rentIncrease = RentIncrease(true)), ScenarioId.S037)
        assertEquals(comparison.counterfactual.states.take(3), comparison.selected.states.take(3))
        assertEquals(comparison.counterfactual.flows.take(2), comparison.selected.flows.take(2))
        for (year in 0..9) {
            near(9_600.0 * 1.02.pow(year), comparison.counterfactual.flows[year].breakdown!!.rent)
            near(9_600.0 * 1.02.pow(year) * if (year < 2) 1.0 else 1.03.pow(year - 1),
                comparison.selected.flows[year].breakdown!!.rent)
            near(0.0, comparison.flows[year].income)
        }
    }

    @Test fun inflationIndexesAllCostsButNotNominalIncomeOrRepair() {
        val highInflation = combined.copy(assumptions = FinancialAssumptions(inflation = 0.1))
        val result = FinancialSimulation.simulate(highInflation)
        val normal = FinancialSimulation.simulate(combined)
        assertEquals(normal.flows.map { it.income }, result.flows.map { it.income })
        for (flow in result.flows) {
            near(12_000.0 * 1.1.pow(flow.year), flow.breakdown!!.essential)
            near(6_000.0 * 1.1.pow(flow.year), flow.breakdown.discretionary)
            near(flow.normalExpenses, flow.breakdown.total)
        }
        near(1_500.0 * 1.1.pow(2), result.flows[2].breakdown!!.studies)
        near(6_000.0, result.flows[4].maintenanceExpense)
        for (state in result.states) near(state.netWorth / 1.1.pow(state.year), state.realNetWorth)
    }

    @Test fun initialProfileGoalAndDeficitsMatchSpecification() {
        val result = FinancialSimulation.simulate()
        near(8_000.0, result.states[0].cash)
        assertEquals(20, result.states[0].age)
        assertEquals(30, result.finalState.age)
        assertEquals(10, result.finalState.year)
        near(result.finalState.netWorth / 350_000.0 * 100.0, result.finalState.goalPercentage!!)
        near(-9_100.0, result.states[1].cash)
        assertTrue(result.flows[0].needsFinancing)
        assertTrue(result.states.all { it.debt == 0.0 })
        assertTrue(result.flows.all { it.borrowing == 0.0 })
    }

    @Test fun allEightCombinationsMatchIndependentAnnualRecurrence() {
        for (mask in 0..7) {
            val internship = mask and 1 != 0
            val rent = mask and 2 != 0
            val repair = mask and 4 != 0
            val parameters = SimulationParameters(internship = internship, rentIncrease = RentIncrease(rent),
                maintenance = UnexpectedMaintenance(repair), investmentShare = 0.4)
            val result = FinancialSimulation.simulate(parameters)
            var cash = 8_000.0
            var investments = 0.0
            for (year in 0..9) {
                val startWork = if (internship) 3 else 2
                val studying = year < startWork && !(internship && year == 0)
                val income = if (internship && year == 0) 30_000.0 else if (studying) 12_000.0
                    else 72_000.0 * 1.02.pow(year - startWork)
                val rentMultiplier = if (rent && year >= 2) 1.03.pow(year - 1) else 1.0
                val expenses = (9_600.0 * rentMultiplier + 18_000.0 + if (studying) 1_500.0 else 0.0) *
                    1.02.pow(year) + if (repair && year == 4) 6_000.0 else 0.0
                val surplus = income - expenses
                val invested = if (surplus > 0.0) surplus * 0.4 else 0.0
                cash += surplus - invested
                investments = investments * 1.03 + invested
                val flow = result.flows[year]
                val state = result.states[year + 1]
                near(expenses, flow.expenses)
                near(cash, state.cash)
                near(investments, state.investments)
                near(cash + investments, state.netWorth)
                near(surplus - invested, state.cash - result.states[year].cash)
                near(0.0, state.debt)
                assertEquals(cash < 0.0, flow.needsFinancing)
            }
            assertEquals(result, FinancialSimulation.simulate(parameters))
        }
    }

    @Test fun eachCounterfactualRemovesOnlyItsOwnIntervention() {
        for (scenario in ScenarioId.entries) {
            val disabled = when (scenario) {
                ScenarioId.S005 -> combined.copy(internship = false)
                ScenarioId.S037 -> combined.copy(rentIncrease = RentIncrease(false))
                ScenarioId.S040 -> combined.copy(maintenance = UnexpectedMaintenance(false))
            }
            val effect = SimulationComparisons.effect(combined, scenario)
            assertEquals(FinancialSimulation.simulate(combined), effect.selected)
            assertEquals(FinancialSimulation.simulate(disabled), effect.counterfactual)
            val zero = SimulationComparisons.effect(disabled, scenario)
            assertTrue(zero.states.all { it.netWorth == 0.0 && it.realNetWorth == 0.0 })
            assertTrue(zero.flows.all { it.income == 0.0 && it.expenses == 0.0 })
        }
    }

    @Test fun comparisonUsesFullCombinedPathAndRetainsEconomicAssumptions() {
        val p = combined.copy(investmentShare = 0.7, assumptions = FinancialAssumptions(inflation = 0.04))
        val comparison = SimulationComparisons.againstReference(p)
        assertEquals(FinancialSimulation.simulate(p), comparison.selected)
        assertEquals(FinancialSimulation.simulate(SimulationParameters(investmentShare = 0.7,
            assumptions = p.assumptions)), comparison.counterfactual)
        near(comparison.selected.finalState.netWorth - comparison.counterfactual.finalState.netWorth,
            comparison.finalNetWorthDifference)
        val exclusiveSum = ScenarioId.entries.sumOf { SimulationComparisons.effect(p, it).finalNetWorthDifference }
        assertTrue(kotlin.math.abs(exclusiveSum - comparison.finalNetWorthDifference) > 1.0)
    }

    @Test fun repairAloneReducesFinalWealthExactlyWithoutInvestments() {
        near(-6_000.0, SimulationComparisons.maintenanceEffect(combined).finalNetWorthDifference)
        val comparison = SimulationComparisons.maintenanceEffect(combined)
        near(-6_000.0 / 1.02.pow(10), comparison.states.last().realNetWorth)
    }

    @Test fun configurableRatesAndZeroInflationAreRespected() {
        val result = FinancialSimulation.simulate(combined.copy(assumptions = FinancialAssumptions(inflation = 0.0),
            rentIncrease = RentIncrease(true, annualExtraRate = 0.1, startYear = 0)))
        near(10_560.0, result.flows[0].breakdown!!.rent)
        near(1_500.0, result.flows[2].breakdown!!.studies)
        result.states.forEach { near(it.netWorth, it.realNetWorth) }
    }

    @Test fun invalidRatesAndAmbiguousCustomPlansAreRejected() {
        assertFailsWith<IllegalArgumentException> { FinancialAssumptions(inflation = -1.0) }
        assertFailsWith<IllegalArgumentException> { FinancialAssumptions(startingSalary = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { RentIncrease(annualExtraRate = -0.1) }
        assertFailsWith<IllegalArgumentException> { combined.copy(annualPlans = listOf(AnnualPlan(0.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { SimulationParameters(years = 0) }
    }

    @Test fun catalogContainsOnlyThreeOfficialDefinitionsAndExplanationsAttributeAssumptions() {
        assertEquals(listOf("S005", "S037", "S040"), ScenarioCatalog.active.map { it.id })
        assertEquals(ScenarioDefinition("S005", "Education & skills", "Internship year", "Continue studies",
            "Paid internship year"), ScenarioCatalog.active[0])
        assertEquals(ScenarioDefinition("S037", "Housing & property", "Rent increase", "Stable rent",
            "Annual rent rises"), ScenarioCatalog.active[1])
        val explanations = FinancialExplanations.explain(combined).joinToString(" ")
        assertTrue(explanations.contains("pas des chiffres officiels UBS"))
        assertTrue(explanations.contains("S005 actif"))
        assertTrue(explanations.contains("S037 actif"))
    }
}
