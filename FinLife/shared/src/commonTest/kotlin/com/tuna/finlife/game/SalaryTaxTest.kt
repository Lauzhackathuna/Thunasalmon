package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeStatus
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyProfile
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SalaryTaxTest {
    /** Plays past the end of studies (24 months), keeping the default answers. */
    private val run = playDefaults(answerDefaults(AdventureRun.start(seed = 5)), 28)

    @Test
    fun onlyTheSalaryIsTaxedAtTheGameRate() {
        assertEquals(0.20, run.configuration.incomeTaxRate)
        for (record in run.history) {
            val flow = record.flow
            assertEquals(flow.professionalIncome.scaled(AdventureRun.SALARY_TAX_RATE), flow.taxes, "month ${record.month}")
            if (record.before.status != LifeStatus.EMPLOYED) assertEquals(Money.ZERO, flow.taxes)
        }
        val employed = run.history.last()
        assertEquals(LifeStatus.EMPLOYED, employed.before.status)
        assertEquals(Money.chf(1200), employed.flow.taxes) // 20% of the 6000 CHF salary
        // Taxes count as expenses, and the engine's own net-worth check held every month.
        assertEquals(employed.flow.recurringExpenses + employed.flow.tuition + employed.flow.exceptionalExpenses +
            employed.flow.decisionCosts + employed.flow.interest + employed.flow.taxes, employed.flow.expenses)
        assertEquals(employed.flow.income - employed.flow.expenses + employed.flow.investmentReturn, employed.netWorthChange)
    }

    @Test
    fun theEngineDefaultIsNoTax() {
        assertEquals(0.0, MonthlyConfiguration().incomeTaxRate)
        val profile = MonthlyProfile.alexia().copy(studyMonths = 0)
        val session = SimulationSessions.runUntilBlocked(
            SimulationSessions.start(profile, MonthlyConfiguration(horizonMonths = 3, randomEvents = run.configuration.randomEvents)), 3)
        assertTrue(session.history.all { it.flow.taxes == Money.ZERO && it.flow.professionalIncome > Money.ZERO })
        assertFailsWith<IllegalArgumentException> { MonthlyConfiguration(incomeTaxRate = 1.5) }
    }

    @Test
    fun theAssumptionsScreenShowsTheRate() {
        assertEquals(AdventureRun.SALARY_TAX_RATE, ModelAssumptions.of(run, 300).salaryTaxRate)
    }
}
