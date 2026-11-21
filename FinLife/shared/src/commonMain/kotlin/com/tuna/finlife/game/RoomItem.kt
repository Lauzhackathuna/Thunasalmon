package com.tuna.finlife.game

enum class RoomSlot { Left, Center, Right }

// Synthetic CHF prices for the demo shop.
enum class RoomItem(val priceChf: Int, val slot: RoomSlot) {
    ReadingLamp(80, RoomSlot.Left),
    Chair(150, RoomSlot.Center),
    Plant(40, RoomSlot.Right),
}

enum class PurchaseStatus { Available, Owned, NotEnoughCash, SimulationComplete }
