package com.tuna.finlife.game

import com.tuna.finlife.model.DemoScenarios
import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.ScenarioId
import com.tuna.finlife.model.appliesTo
import com.tuna.finlife.twin.TwinEngine

internal data class ScenarioComparison(
    val baseline: FinancialSnapshot,
    val alternative: FinancialSnapshot,
)

internal fun compareScenario(
    state: GameState.Playing,
    scenario: ScenarioId,
    twin: TwinEngine,
): ScenarioComparison? {
    if (!state.canAdvanceMonth || !scenario.appliesTo(state.persona)) return null

    // Use the same opening balance for both branches and hold the other toggles fixed.
    val otherScenarios = state.activeScenarios - scenario
    val comparisonMonth = maxOf(state.snapshot.monthIndex + 1, DemoScenarios.FIRST_EFFECT_MONTH_INDEX)
    var opening = state.snapshot
    while (opening.monthIndex + 1 < comparisonMonth) {
        opening = twin.advanceMonth(state.persona, opening, 0.0, otherScenarios).snapshot
    }
    return ScenarioComparison(
        baseline = twin.advanceMonth(state.persona, opening, 0.0, otherScenarios).snapshot,
        alternative = twin.advanceMonth(state.persona, opening, 0.0, otherScenarios + scenario).snapshot,
    )
}
