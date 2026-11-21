package com.tuna.finlife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.GameAction
import com.tuna.finlife.game.LOW_LIQUIDITY_MONTHS
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.i18n.displayMonths
import com.tuna.finlife.model.GoalStatus
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.advance_month
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.constraint_breaches
import finlife.shared.generated.resources.goal_feasibility
import finlife.shared.generated.resources.goal_off_track
import finlife.shared.generated.resources.goal_on_track
import finlife.shared.generated.resources.goal_at_risk
import finlife.shared.generated.resources.liquidity_months
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.monthly_change
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.opening_balance
import finlife.shared.generated.resources.room_title
import finlife.shared.generated.resources.shop
import finlife.shared.generated.resources.speech_opening
import finlife.shared.generated.resources.speech_gain
import finlife.shared.generated.resources.speech_loss
import finlife.shared.generated.resources.speech_low_liquidity
import finlife.shared.generated.resources.speech_unchanged
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomScreen(
    state: GameState.Playing,
    onAction: (GameAction) -> Unit,
) {
    var dismissedMonth by remember { mutableIntStateOf(-1) }
    var shopOpen by remember { mutableStateOf(false) }
    GameScreen(
        header = { FinanceHeader(state) },
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                PrimaryButton(
                    label = stringResource(Res.string.advance_month),
                    onClick = { onAction(GameAction.AdvanceMonth) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canAdvanceMonth,
                )
                OutlinedButton(
                    onClick = { shopOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = FinLifeSpacing.touchTarget),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(Res.string.shop))
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            Text(stringResource(state.room.tier.label), style = MaterialTheme.typography.titleMedium)
            RoomCanvas(state.room, state.mood)
            CharacterSpeech(state)
        }
    }
    val summary = state.lastMonth
    if (summary != null && summary.snapshot.monthIndex > dismissedMonth) {
        MonthSummarySheet(summary, state.lastRoomChanges) { dismissedMonth = summary.snapshot.monthIndex }
    } else if (shopOpen) {
        RoomShopSheet(state, onAction, onDismiss = { shopOpen = false })
    }
}

@Composable
private fun CharacterSpeech(state: GameState.Playing) {
    val change = state.cashChange
    val message = when {
        change == null -> Res.string.speech_opening
        state.snapshot.liquidityMonths < LOW_LIQUIDITY_MONTHS -> Res.string.speech_low_liquidity
        change < 0 -> Res.string.speech_loss
        change > 0 -> Res.string.speech_gain
        else -> Res.string.speech_unchanged
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(message),
            modifier = Modifier.padding(horizontal = FinLifeSpacing.medium, vertical = FinLifeSpacing.small),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FinanceHeader(state: GameState.Playing) {
    val snapshot = state.snapshot
    val animatedCash by animateFloatAsState(snapshot.cash.toFloat(), label = "cash balance")
    Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
            ScreenHeading(stringResource(Res.string.room_title), Modifier.alignByBaseline())
            Text(
                text = stringResource(personaCopy(state.persona).name),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        KeyFigureCard(
            label = stringResource(Res.string.cash_balance),
            value = displayMoney(animatedCash.toDouble()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val month = if (snapshot.monthIndex < 0) {
                stringResource(Res.string.opening_balance)
            } else {
                stringResource(Res.string.month_number, snapshot.monthIndex + 1)
            }
            Text(month, style = MaterialTheme.typography.bodyMedium)
            state.cashChange?.let { change ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                    Text(stringResource(Res.string.monthly_change), style = MaterialTheme.typography.bodyMedium)
                    DeltaIndicator(displayMoney(abs(change)), DeltaDirection.fromAmount(change))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            MetricTile(
                stringResource(Res.string.liquidity_months),
                displayMonths(snapshot.liquidityMonths),
                Modifier.weight(1f),
            )
            MetricTile(stringResource(Res.string.net_worth), displayMoney(snapshot.netWorth), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            val goalLabel = when (snapshot.goalFeasibility) {
                GoalStatus.OnTrack -> Res.string.goal_on_track
                GoalStatus.AtRisk -> Res.string.goal_at_risk
                GoalStatus.OffTrack -> Res.string.goal_off_track
            }
            MetricTile(stringResource(Res.string.goal_feasibility), stringResource(goalLabel), Modifier.weight(1f))
            MetricTile(
                stringResource(Res.string.constraint_breaches),
                snapshot.constraintBreaches.toString(),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(FinLifeSpacing.small)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
