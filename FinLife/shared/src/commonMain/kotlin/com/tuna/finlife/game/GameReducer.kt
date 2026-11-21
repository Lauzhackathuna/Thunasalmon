package com.tuna.finlife.game

import com.tuna.finlife.model.DEMO_HORIZON_MONTHS
import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.GoalStatus
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import com.tuna.finlife.model.ScenarioId
import com.tuna.finlife.model.appliesTo
import com.tuna.finlife.twin.TwinEngine

sealed interface GameState {
    data object ChoosingPersona : GameState

    data class Playing(
        val persona: Persona,
        val snapshot: FinancialSnapshot,
        val lastMonth: MonthOutcome? = null,
        val cashChange: Double? = null,
        val room: RoomProgress = RoomProgress(),
        val lastRoomChanges: RoomMonthChanges = RoomMonthChanges(),
        val activeScenarios: Set<ScenarioId> = emptySet(),
    ) : GameState {
        val canAdvanceMonth: Boolean get() = snapshot.monthIndex < DEMO_HORIZON_MONTHS - 1

        val mood: CharacterMood get() {
            val change = cashChange ?: 0.0
            return when {
                change < 0 || snapshot.liquidityMonths < LOW_LIQUIDITY_MONTHS -> CharacterMood.Stressed
                change > 0 && snapshot.goalFeasibility == GoalStatus.OnTrack -> CharacterMood.Thriving
                else -> CharacterMood.Okay
            }
        }

        fun claimStatus(item: RoomItem): ClaimStatus = when {
            item in room.inventory -> ClaimStatus.Owned
            !canAdvanceMonth -> ClaimStatus.SimulationComplete
            room.tier < item.requiredTier -> ClaimStatus.Locked
            else -> ClaimStatus.Available
        }
    }
}

sealed interface GameAction {
    data class SelectPersona(val persona: Persona) : GameAction
    data object AdvanceMonth : GameAction
    data class ClaimRoomItem(val item: RoomItem) : GameAction
    data class PlaceRoomItem(val item: RoomItem) : GameAction
    data class RepairRoomItem(val item: RoomItem) : GameAction
    data class SetScenario(val scenario: ScenarioId, val enabled: Boolean) : GameAction
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
            if (state is GameState.Playing && state.canAdvanceMonth) {
                val outcome = twin.advanceMonth(
                    persona = state.persona,
                    current = state.snapshot,
                    playerSpendThisMonth = 0.0,
                    activeScenarios = state.activeScenarios,
                )
                val cashChange = outcome.snapshot.cash - state.snapshot.cash
                val (room, changes) = state.room.advance(outcome.snapshot, cashChange)
                state.copy(
                    snapshot = outcome.snapshot,
                    lastMonth = outcome,
                    cashChange = cashChange,
                    room = room,
                    lastRoomChanges = changes,
                )
            } else {
                state
            }
        }
        is GameAction.ClaimRoomItem -> {
            if (state is GameState.Playing && state.claimStatus(action.item) == ClaimStatus.Available) {
                state.copy(room = state.room.claim(action.item))
            } else {
                state
            }
        }
        is GameAction.PlaceRoomItem -> state.updateRoom { place(action.item) }
        is GameAction.RepairRoomItem -> state.updateRoom { repair(action.item) }
        is GameAction.SetScenario -> {
            if (state is GameState.Playing && state.canAdvanceMonth && action.scenario.appliesTo(state.persona)) {
                val scenarios = if (action.enabled) {
                    state.activeScenarios + action.scenario
                } else {
                    state.activeScenarios - action.scenario
                }
                state.copy(activeScenarios = scenarios)
            } else {
                state
            }
        }
    }

private inline fun GameState.updateRoom(update: RoomProgress.() -> RoomProgress): GameState {
    if (this !is GameState.Playing) return this
    val updatedRoom = room.update()
    return if (updatedRoom === room) this else copy(room = updatedRoom)
}
