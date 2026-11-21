package com.tuna.finlife.game

import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.FakeTwinEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class GameReducerTest {
    private val engine = FakeTwinEngine()
    private val initial = GameState.ChoosingPersona

    @Test
    fun advancingBeforeSelectingAPersonaLeavesTheStateUnchanged() {
        assertSame(initial, reduceGame(initial, GameAction.AdvanceMonth, engine))
    }

    @Test
    fun selectingThenAdvancingRetainsTheOutcomeForTheSummary() {
        val selected = selectStudent()
        assertEquals(Persona.MastersStudent, selected.persona)
        assertEquals(8_000.0, selected.snapshot.cash)
        assertNull(selected.lastMonth)
        assertNull(selected.cashChange)

        val updated = assertIs<GameState.Playing>(reduceGame(selected, GameAction.AdvanceMonth, engine))
        assertEquals(0, updated.snapshot.monthIndex)
        assertEquals(6_920.0, updated.snapshot.cash)
        assertEquals(-1_080.0, updated.cashChange)
        assertEquals(updated.snapshot, updated.lastMonth?.snapshot)
        assertEquals(listOf(LifeEvent.TuitionDue), updated.lastMonth?.events)
        assertEquals(8_000.0, selected.snapshot.cash)
    }

    @Test
    fun advancingAfterMonthTwelveLeavesTheStateUnchanged() {
        var state: GameState = selectStudent()
        repeat(12) { state = reduceGame(state, GameAction.AdvanceMonth, engine) }

        val finalState = assertIs<GameState.Playing>(state)
        assertEquals(11, finalState.snapshot.monthIndex)
        assertFalse(finalState.canAdvanceMonth)
        assertSame(finalState, reduceGame(finalState, GameAction.AdvanceMonth, engine))
    }

    private fun selectStudent(): GameState.Playing = assertIs(
        reduceGame(initial, GameAction.SelectPersona(Persona.MastersStudent), engine),
    )
}
