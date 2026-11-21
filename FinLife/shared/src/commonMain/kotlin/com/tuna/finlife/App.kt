package com.tuna.finlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.GameAction
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.reduceGame
import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.FakeTwinEngine
import com.tuna.finlife.ui.FinLifeTheme
import com.tuna.finlife.ui.PersonaSelectionScreen
import com.tuna.finlife.ui.RoomScreen

@Composable
@Preview
fun App() {
    val twin = remember { FakeTwinEngine() }
    var state by remember { mutableStateOf<GameState>(GameState.ChoosingPersona) }

    FinLifeTheme {
        when (val current = state) {
            GameState.ChoosingPersona -> {
                val openingSnapshots = remember(twin) { Persona.entries.associateWith(twin::initialSnapshot) }
                PersonaSelectionScreen(
                    openingSnapshots = openingSnapshots,
                    onSelect = { state = reduceGame(state, GameAction.SelectPersona(it), twin) },
                )
            }
            is GameState.Playing -> RoomScreen(
                state = current,
                onAdvanceMonth = {
                    if (state == current) {
                        state = reduceGame(current, GameAction.AdvanceMonth, twin)
                    }
                },
                onBuyItem = { state = reduceGame(state, GameAction.BuyRoomItem(it), twin) },
            )
        }
    }
}
