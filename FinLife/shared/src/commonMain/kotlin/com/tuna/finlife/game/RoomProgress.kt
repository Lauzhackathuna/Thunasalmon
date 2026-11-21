package com.tuna.finlife.game

import com.tuna.finlife.model.FinancialSnapshot
import com.tuna.finlife.model.GoalStatus

// Condition and progression thresholds are synthetic game rules.
internal const val LOW_LIQUIDITY_MONTHS = 1.0

data class RoomProgress(
    val tier: RoomTier = RoomTier.SharedRoom,
    val inventory: Map<RoomItem, ItemCondition> = emptyMap(),
    val displayed: Map<RoomSlot, RoomItem> = emptyMap(),
    val qualifyingMonths: Int = 0,
)

data class DamagedItem(val item: RoomItem, val newCondition: ItemCondition)

data class RoomMonthChanges(
    val damage: DamagedItem? = null,
    val upgradedTo: RoomTier? = null,
)

internal fun RoomProgress.advance(
    snapshot: FinancialSnapshot,
    cashChange: Double,
): Pair<RoomProgress, RoomMonthChanges> {
    val qualifies = cashChange > 0 && snapshot.goalFeasibility == GoalStatus.OnTrack
    val streak = if (qualifies) qualifyingMonths + 1 else 0
    val earnedTier = when {
        streak >= 4 -> RoomTier.Flat
        streak >= 2 -> RoomTier.SmallStudio
        else -> RoomTier.SharedRoom
    }
    val nextTier = maxOf(tier, earnedTier)
    val damage = if (cashChange < 0 || snapshot.liquidityMonths < LOW_LIQUIDITY_MONTHS) {
        nextDamage()
    } else {
        null
    }
    val nextInventory = if (damage == null) inventory else inventory + (damage.item to damage.newCondition)
    return copy(tier = nextTier, inventory = nextInventory, qualifyingMonths = streak) to
        RoomMonthChanges(damage, upgradedTo = nextTier.takeIf { it > tier })
}

internal fun RoomProgress.claim(item: RoomItem): RoomProgress =
    copy(inventory = inventory + (item to ItemCondition.Intact), displayed = displayed + (item.slot to item))

internal fun RoomProgress.place(item: RoomItem): RoomProgress =
    if (item in inventory && displayed[item.slot] != item) {
        copy(displayed = displayed + (item.slot to item))
    } else {
        this
    }

internal fun RoomProgress.repair(item: RoomItem): RoomProgress = when (inventory[item]) {
    ItemCondition.Broken -> copy(inventory = inventory + (item to ItemCondition.Damaged))
    ItemCondition.Damaged -> copy(inventory = inventory + (item to ItemCondition.Intact))
    ItemCondition.Intact, null -> this
}

private fun RoomProgress.nextDamage(): DamagedItem? {
    val target = displayed.values
        .filter { inventory.getValue(it) != ItemCondition.Broken }
        .minWithOrNull(compareBy({ inventory.getValue(it) }, { it.slot }))
        ?: return null
    val condition = if (inventory.getValue(target) == ItemCondition.Intact) {
        ItemCondition.Damaged
    } else {
        ItemCondition.Broken
    }
    return DamagedItem(target, condition)
}
