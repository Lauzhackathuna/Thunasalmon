package com.tuna.finlife.game

import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.FakeTwinEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RoomPurchaseTest {
    private val twin = FakeTwinEngine()
    private val opening = GameState.Playing(Persona.MastersStudent, twin.initialSnapshot(Persona.MastersStudent))

    @Test
    fun aPurchaseCanUseTheExactAvailableCash() {
        val state = opening.copy(snapshot = opening.snapshot.copy(cash = 80.0))
        val purchased = buy(state, RoomItem.ReadingLamp)

        assertEquals(0.0, purchased.availableCash)
        assertEquals(80, purchased.roomPurchasesThisMonth)
        assertEquals(setOf(RoomItem.ReadingLamp), purchased.ownedItems)
        assertEquals(PurchaseStatus.NotEnoughCash, purchased.purchaseStatus(RoomItem.Plant))
        assertSame(purchased, buy(purchased, RoomItem.Plant))
    }

    @Test
    fun unaffordableAndDuplicatePurchasesLeaveStateUnchanged() {
        val lowCash = opening.copy(snapshot = opening.snapshot.copy(cash = 79.0))
        assertSame(lowCash, buy(lowCash, RoomItem.ReadingLamp))
        val purchased = buy(opening, RoomItem.ReadingLamp)
        assertEquals(PurchaseStatus.Owned, purchased.purchaseStatus(RoomItem.ReadingLamp))
        assertSame(purchased, buy(purchased, RoomItem.ReadingLamp))
    }

    @Test
    fun roomPurchasesReduceAvailableCashAndSettleOnce() {
        val furnished = RoomItem.entries.fold(opening, ::buy)
        assertEquals(7_730.0, furnished.availableCash)
        assertEquals(8_000.0, furnished.snapshot.cash)

        val firstMonth = advance(furnished)
        assertEquals(6_650.0, firstMonth.availableCash)
        assertEquals(-1_350.0, firstMonth.cashChange)
        assertEquals(270, firstMonth.lastMonthRoomPurchases)
        assertEquals(0, firstMonth.roomPurchasesThisMonth)
        assertEquals(RoomItem.entries.toSet(), firstMonth.ownedItems)
        assertEquals(2_280.0, firstMonth.lastMonth?.expenses)

        val secondMonth = advance(firstMonth)
        assertEquals(6_370.0, secondMonth.availableCash)
        assertEquals(0, secondMonth.lastMonthRoomPurchases)
    }

    @Test
    fun purchasesAreBlockedAfterTheFinalMonth() {
        var state = buy(opening, RoomItem.Plant)
        repeat(12) { state = advance(state) }

        assertEquals(PurchaseStatus.SimulationComplete, state.purchaseStatus(RoomItem.Chair))
        assertSame(state, buy(state, RoomItem.Chair))
        assertEquals(setOf(RoomItem.Plant), state.ownedItems)
    }

    private fun buy(state: GameState.Playing, item: RoomItem): GameState.Playing =
        assertIs(reduceGame(state, GameAction.BuyRoomItem(item), twin))

    private fun advance(state: GameState.Playing): GameState.Playing =
        assertIs(reduceGame(state, GameAction.AdvanceMonth, twin))
}
