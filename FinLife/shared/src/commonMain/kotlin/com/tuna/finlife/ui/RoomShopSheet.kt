package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.PurchaseStatus
import com.tuna.finlife.game.RoomItem
import com.tuna.finlife.i18n.displayMoney
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.available_cash
import finlife.shared.generated.resources.buy
import finlife.shared.generated.resources.chair
import finlife.shared.generated.resources.inventory
import finlife.shared.generated.resources.not_enough_cash
import finlife.shared.generated.resources.plant
import finlife.shared.generated.resources.reading_lamp
import finlife.shared.generated.resources.shop
import finlife.shared.generated.resources.simulation_complete
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomShopSheet(state: GameState.Playing, onBuyItem: (RoomItem) -> Unit, onDismiss: () -> Unit) {
    GameSheet(onDismiss) {
        ScreenHeading(stringResource(Res.string.shop))
        Text(stringResource(Res.string.available_cash, displayMoney(state.availableCash)))
        if (!state.canAdvanceMonth) {
            Text(
                stringResource(Res.string.simulation_complete),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (item in RoomItem.entries) {
            if (item !in state.ownedItems) {
                ShopItemRow(item, state.purchaseStatus(item)) { onBuyItem(item) }
            }
        }
        if (state.ownedItems.isNotEmpty()) {
            Text(
                stringResource(Res.string.inventory),
                modifier = Modifier.padding(top = FinLifeSpacing.small),
                style = MaterialTheme.typography.titleMedium,
            )
            for (item in RoomItem.entries) {
                if (item in state.ownedItems) Text(stringResource(item.label))
            }
        }
    }
}

@Composable
private fun ShopItemRow(item: RoomItem, status: PurchaseStatus, onBuy: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(item.label), style = MaterialTheme.typography.titleMedium)
                Text(displayMoney(item.priceChf.toDouble()), style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(
                label = stringResource(Res.string.buy),
                enabled = status == PurchaseStatus.Available,
                onClick = onBuy,
            )
        }
        if (status == PurchaseStatus.NotEnoughCash) {
            Text(
                stringResource(Res.string.not_enough_cash),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val RoomItem.label: StringResource
    get() = when (this) {
        RoomItem.ReadingLamp -> Res.string.reading_lamp
        RoomItem.Chair -> Res.string.chair
        RoomItem.Plant -> Res.string.plant
    }
