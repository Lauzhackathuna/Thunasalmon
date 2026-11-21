package com.tuna.finlife.game

import com.tuna.finlife.model.GoalStatus
import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.FakeTwinEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RoomProgressTest {
    private val twin = FakeTwinEngine()
    private val opening = GameState.Playing(Persona.MastersStudent, twin.initialSnapshot(Persona.MastersStudent))

    @Test
    fun furnitureClaimsLeaveFinancesAndMonthlyOutcomesUnchanged() {
        val furnished = listOf(RoomItem.ReadingLamp, RoomItem.Chair, RoomItem.Plant).fold(opening, ::claim)
        assertEquals(opening.snapshot, furnished.snapshot)
        assertEquals(3, furnished.room.displayed.size)
        assertEquals(ClaimStatus.Owned, furnished.claimStatus(RoomItem.ReadingLamp))
        assertSame(furnished, claim(furnished, RoomItem.ReadingLamp))

        val firstMonth = advance(furnished)
        assertEquals(advance(opening).lastMonth, firstMonth.lastMonth)
        assertEquals(6_920.0, firstMonth.snapshot.cash)
        assertEquals(-1_080.0, firstMonth.cashChange)
    }

    @Test
    fun lossesDamageOneDisplayedItemAndPreferIntactItemsLeftToRight() {
        var state = listOf(RoomItem.Plant, RoomItem.Chair, RoomItem.ReadingLamp).fold(opening, ::claim)
        for (item in listOf(RoomItem.ReadingLamp, RoomItem.Chair, RoomItem.Plant)) {
            state = advance(state)
            assertEquals(DamagedItem(item, ItemCondition.Damaged), state.lastRoomChanges.damage)
        }

        state = advance(state)
        assertEquals(DamagedItem(RoomItem.ReadingLamp, ItemCondition.Broken), state.lastRoomChanges.damage)
        assertEquals(ItemCondition.Damaged, state.room.inventory[RoomItem.Chair])
    }

    @Test
    fun lowLiquidityCanCauseDamageEvenWithACashGain() {
        val state = claim(opening, RoomItem.Plant)
        val lowLiquidity = opening.snapshot.copy(liquidityMonths = 0.75, goalFeasibility = GoalStatus.AtRisk)
        val (_, changes) = state.room.advance(lowLiquidity, cashChange = 100.0)

        assertEquals(DamagedItem(RoomItem.Plant, ItemCondition.Damaged), changes.damage)
    }

    @Test
    fun repairIsFreeAndRestoresOneStepEvenAfterTheFinalMonth() {
        var state = claim(opening, RoomItem.Plant)
        repeat(12) { state = advance(state) }

        assertEquals(ClaimStatus.SimulationComplete, state.claimStatus(RoomItem.Chair))
        assertSame(state, claim(state, RoomItem.Chair))
        assertEquals(ItemCondition.Broken, state.room.inventory[RoomItem.Plant])
        val repairedOnce = apply(state, GameAction.RepairRoomItem(RoomItem.Plant))
        assertEquals(ItemCondition.Damaged, repairedOnce.room.inventory[RoomItem.Plant])
        val repairedTwice = apply(repairedOnce, GameAction.RepairRoomItem(RoomItem.Plant))
        assertEquals(ItemCondition.Intact, repairedTwice.room.inventory[RoomItem.Plant])
        assertEquals(state.snapshot, repairedTwice.snapshot)
    }

    @Test
    fun qualifyingMonthsUnlockTiersAndAlternativesCanBePlacedWithoutLosingInventory() {
        val persona = Persona.Apprentice
        var state = claim(GameState.Playing(persona, twin.initialSnapshot(persona)), RoomItem.ReadingLamp)
        assertEquals(ClaimStatus.Locked, state.claimStatus(RoomItem.TableLamp))
        assertSame(state, claim(state, RoomItem.TableLamp))
        assertEquals(CharacterMood.Okay, state.mood)

        state = advance(state)
        assertEquals(CharacterMood.Stressed, state.mood)
        state = advance(state)
        assertEquals(CharacterMood.Thriving, state.mood)
        assertEquals(RoomTier.SharedRoom, state.room.tier)
        state = advance(state)
        assertEquals(RoomTier.SmallStudio, state.lastRoomChanges.upgradedTo)

        state = claim(state, RoomItem.TableLamp)
        assertEquals(RoomItem.TableLamp, state.room.displayed[RoomSlot.Left])
        state = apply(state, GameAction.PlaceRoomItem(RoomItem.ReadingLamp))
        assertEquals(RoomItem.ReadingLamp, state.room.displayed[RoomSlot.Left])
        assertEquals(setOf(RoomItem.ReadingLamp, RoomItem.TableLamp), state.room.inventory.keys)

        state = advance(advance(state))
        assertEquals(RoomTier.Flat, state.lastRoomChanges.upgradedTo)
        assertEquals(ClaimStatus.Available, state.claimStatus(RoomItem.Armchair))
    }

    @Test
    fun aNonqualifyingMonthResetsProgressWithoutDowngradingTheRoom() {
        val studio = RoomProgress(tier = RoomTier.SmallStudio, qualifyingMonths = 3)
        val atRisk = opening.snapshot.copy(goalFeasibility = GoalStatus.AtRisk)
        val (after, changes) = studio.advance(atRisk, cashChange = 100.0)

        assertEquals(0, after.qualifyingMonths)
        assertEquals(RoomTier.SmallStudio, after.tier)
        assertEquals(RoomMonthChanges(), changes)
    }

    private fun claim(state: GameState.Playing, item: RoomItem): GameState.Playing =
        apply(state, GameAction.ClaimRoomItem(item))

    private fun advance(state: GameState.Playing): GameState.Playing =
        apply(state, GameAction.AdvanceMonth)

    private fun apply(state: GameState.Playing, action: GameAction): GameState.Playing =
        assertIs(reduceGame(state, action, twin))
}
