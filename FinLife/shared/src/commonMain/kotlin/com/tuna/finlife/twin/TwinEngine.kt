package com.tuna.finlife.twin

import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import com.tuna.finlife.model.ScenarioId

interface TwinEngine {
    fun initialSnapshot(persona: Persona): FinancialSnapshot

    /**
     * [current] is the opening snapshot or the result of the last completed month.
     * [playerSpendThisMonth] is additional to the scripted expenses reported in the outcome.
     */
    fun advanceMonth(
        persona: Persona,
        current: FinancialSnapshot,
        playerSpendThisMonth: Double,
        activeScenarios: Set<ScenarioId>,
    ): MonthOutcome
}
