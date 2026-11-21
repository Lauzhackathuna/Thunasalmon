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
        val cashChange: Double? = null,
        val ownedItems: Set<RoomItem> = emptySet(),
        val roomPurchasesThisMonth: Int = 0,
        val lastMonthRoomPurchases: Int = 0,
    ) : GameState {
        val canAdvanceMonth: Boolean get() = snapshot.monthIndex < DEMO_HORIZON_MONTHS - 1
        val availableCash: Double get() = snapshot.cash - roomPurchasesThisMonth

        fun purchaseStatus(item: RoomItem): PurchaseStatus = when {
            item in ownedItems -> PurchaseStatus.Owned
            !canAdvanceMonth -> PurchaseStatus.SimulationComplete
            availableCash < item.priceChf -> PurchaseStatus.NotEnoughCash
            else -> PurchaseStatus.Available
        }
    }
}

sealed interface GameAction {
    data class SelectPersona(val persona: Persona) : GameAction
    data object AdvanceMonth : GameAction
    data class BuyRoomItem(val item: RoomItem) : GameAction
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
                    playerSpendThisMonth = state.roomPurchasesThisMonth.toDouble(),
                    activeScenarios = emptySet(),
                )
                state.copy(
                    snapshot = outcome.snapshot,
                    lastMonth = outcome,
                    cashChange = outcome.snapshot.cash - state.snapshot.cash,
                    roomPurchasesThisMonth = 0,
                    lastMonthRoomPurchases = state.roomPurchasesThisMonth,
                )
            } else {
                state
            }
        }
        is GameAction.BuyRoomItem -> {
            if (state is GameState.Playing && state.purchaseStatus(action.item) == PurchaseStatus.Available) {
                state.copy(
                    ownedItems = state.ownedItems + action.item,
                    roomPurchasesThisMonth = state.roomPurchasesThisMonth + action.item.priceChf,
                )
            } else {
                state
            }
        }
    }
