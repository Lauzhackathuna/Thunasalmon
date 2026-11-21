package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.ClaimStatus
import com.tuna.finlife.game.GameAction
import com.tuna.finlife.game.ItemCondition
import com.tuna.finlife.game.RoomItem
import com.tuna.finlife.game.RoomTier
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.armchair
import finlife.shared.generated.resources.broken
import finlife.shared.generated.resources.chair
import finlife.shared.generated.resources.claim
import finlife.shared.generated.resources.claim_item
import finlife.shared.generated.resources.damaged
import finlife.shared.generated.resources.flat
import finlife.shared.generated.resources.intact
import finlife.shared.generated.resources.inventory
import finlife.shared.generated.resources.inventory_empty
import finlife.shared.generated.resources.place
import finlife.shared.generated.resources.place_item
import finlife.shared.generated.resources.plant
import finlife.shared.generated.resources.reading_lamp
import finlife.shared.generated.resources.repair
import finlife.shared.generated.resources.repair_item
import finlife.shared.generated.resources.shared_room
import finlife.shared.generated.resources.shop
import finlife.shared.generated.resources.simulation_complete
import finlife.shared.generated.resources.small_studio
import finlife.shared.generated.resources.table_lamp
import finlife.shared.generated.resources.unlocks_at
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomShopSheet(state: GameState.Playing, onAction: (GameAction) -> Unit, onDismiss: () -> Unit) {
    GameSheet(onDismiss) {
        ScreenHeading(stringResource(Res.string.shop))
        if (!state.canAdvanceMonth) {
            Text(
                stringResource(Res.string.simulation_complete),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (item in RoomItem.entries) {
            if (item !in state.room.inventory) {
                ShopItemRow(item, state.claimStatus(item)) { onAction(GameAction.ClaimRoomItem(item)) }
            }
        }
        if (state.room.inventory.isNotEmpty()) {
            Text(
                stringResource(Res.string.inventory),
                modifier = Modifier.padding(top = FinLifeSpacing.small),
                style = MaterialTheme.typography.titleMedium,
            )
            for (item in RoomItem.entries) {
                val condition = state.room.inventory[item] ?: continue
                InventoryItemRow(
                    item = item,
                    condition = condition,
                    displayed = state.room.displayed[item.slot] == item,
                    onAction = onAction,
                )
            }
        } else {
            Text(
                stringResource(Res.string.inventory_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShopItemRow(item: RoomItem, status: ClaimStatus, onClaim: () -> Unit) {
    val name = stringResource(item.label)
    val action = stringResource(Res.string.claim_item, name)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (status == ClaimStatus.Locked) {
                Text(
                    stringResource(Res.string.unlocks_at, stringResource(item.requiredTier.label)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton(
            label = stringResource(Res.string.claim),
            modifier = Modifier.semantics { contentDescription = action },
            enabled = status == ClaimStatus.Available,
            onClick = onClaim,
        )
    }
}

@Composable
private fun InventoryItemRow(
    item: RoomItem,
    condition: ItemCondition,
    displayed: Boolean,
    onAction: (GameAction) -> Unit,
) {
    val name = stringResource(item.label)
    Column {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(condition.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            if (condition != ItemCondition.Intact) {
                val action = stringResource(Res.string.repair_item, name)
                PrimaryButton(
                    label = stringResource(Res.string.repair),
                    onClick = { onAction(GameAction.RepairRoomItem(item)) },
                    modifier = Modifier.semantics { contentDescription = action },
                )
            }
            if (!displayed) {
                val action = stringResource(Res.string.place_item, name)
                PrimaryButton(
                    label = stringResource(Res.string.place),
                    onClick = { onAction(GameAction.PlaceRoomItem(item)) },
                    modifier = Modifier.semantics { contentDescription = action },
                )
            }
        }
    }
}

internal val RoomItem.label: StringResource
    get() = when (this) {
        RoomItem.ReadingLamp -> Res.string.reading_lamp
        RoomItem.Chair -> Res.string.chair
        RoomItem.Plant -> Res.string.plant
        RoomItem.TableLamp -> Res.string.table_lamp
        RoomItem.Armchair -> Res.string.armchair
    }

internal val RoomTier.label: StringResource
    get() = when (this) {
        RoomTier.SharedRoom -> Res.string.shared_room
        RoomTier.SmallStudio -> Res.string.small_studio
        RoomTier.Flat -> Res.string.flat
    }

internal val ItemCondition.label: StringResource
    get() = when (this) {
        ItemCondition.Intact -> Res.string.intact
        ItemCondition.Damaged -> Res.string.damaged
        ItemCondition.Broken -> Res.string.broken
    }
