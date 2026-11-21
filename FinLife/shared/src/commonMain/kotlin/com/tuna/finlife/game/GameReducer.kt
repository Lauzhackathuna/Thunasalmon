package com.tuna.finlife.game

import com.tuna.finlife.model.DEMO_HORIZON_MONTHS
import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.TwinEngine

sealed interface GameState {
    data object ChoosingPersona : GameState

    data class Playing(
        val persona: Persona,
        val snapshot: FinancialSnapshot,
        val lastMonth: MonthOutcome? = null,
    ) : GameState
}

sealed interface GameAction {
    data class SelectPersona(val persona: Persona) : GameAction
    data object AdvanceMonth : GameAction
}

fun reduceGame(state: GameState, action: GameAction, twin: TwinEngine): GameState =
    when (action) {
        is GameAction.SelectPersona -> {
            if (state == GameState.ChoosingPersona) {
                GameState.Playing(action.persona, twin.initialSnapshot(action.persona))
            } else {
                state
            }
        }
        GameAction.AdvanceMonth -> {
            if (state is GameState.Playing && state.snapshot.monthIndex < DEMO_HORIZON_MONTHS - 1) {
                val outcome = twin.advanceMonth(
                    persona = state.persona,
                    current = state.snapshot,
                    playerSpendThisMonth = 0.0,
                    activeScenarios = emptySet(),
                )
                state.copy(snapshot = outcome.snapshot, lastMonth = outcome)
            } else {
                state
            }
        }
    }
