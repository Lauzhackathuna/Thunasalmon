package com.tuna.finlife.game

enum class RoomSlot { Left, Center, Right }

enum class RoomTier { SharedRoom, SmallStudio, Flat }

enum class RoomItem(val slot: RoomSlot, val requiredTier: RoomTier = RoomTier.SharedRoom) {
    ReadingLamp(RoomSlot.Left),
    Chair(RoomSlot.Center),
    Plant(RoomSlot.Right),
    TableLamp(RoomSlot.Left, RoomTier.SmallStudio),
    Armchair(RoomSlot.Center, RoomTier.Flat),
}

enum class ClaimStatus { Available, Owned, Locked, SimulationComplete }

enum class ItemCondition { Intact, Damaged, Broken }

enum class CharacterMood { Thriving, Okay, Stressed }
