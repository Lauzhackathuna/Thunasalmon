package com.tuna.finlife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.HousingDecision
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.advance_month
import finlife.shared.generated.resources.adventure_arrival
import finlife.shared.generated.resources.adventure_month
import finlife.shared.generated.resources.adventure_title
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.cash_history
import finlife.shared.generated.resources.choose_housing
import finlife.shared.generated.resources.closing_cash
import finlife.shared.generated.resources.end_simulation
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.housing_moved
import finlife.shared.generated.resources.housing_prompt
import finlife.shared.generated.resources.housing_stayed
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.maya_name
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.monthly_rent
import finlife.shared.generated.resources.move_choice
import finlife.shared.generated.resources.moving_cost
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.opening_balance
import finlife.shared.generated.resources.settings
import finlife.shared.generated.resources.simulation_complete
import finlife.shared.generated.resources.stay_choice
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdventureScreen(
    session: SimulationSession,
    onOpenSettings: () -> Unit,
    onAdvance: () -> Unit,
    onEnd: () -> Unit,
    onChoose: (HousingDecision) -> Unit,
) {
    val record = remember(session) { session.history.lastOrNull() }
    val preview = remember(session) {
        if (session.status == SessionStatus.AWAITING_DECISION) {
            HousingDecision.entries.associateWith { AdventureRun.preview(session, it) }
        } else {
            emptyMap()
        }
    }
    GameScreen(
        header = { AdventureFinances(session, record, showHistory = preview.isEmpty()) },
        actions = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (session.status == SessionStatus.READY) {
                    PrimaryButton(stringResource(Res.string.advance_month), onAdvance)
                }
                PrimaryButton(stringResource(Res.string.end_simulation), onEnd)
                PrimaryButton(stringResource(Res.string.settings), onOpenSettings)
            }
        },
    ) {
        if (preview.isNotEmpty()) {
            val stay = preview.getValue(HousingDecision.Stay)
            Text(stringResource(Res.string.choose_housing), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    Res.string.housing_prompt,
                    stay.month + 1,
                    money(stay.flow.income),
                    money(stay.flow.rent),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            for ((decision, outcome) in preview) {
                HousingOption(decision, outcome) { onChoose(decision) }
            }
        } else {
            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(FinLifeSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                ) {
                    Text(
                        if (record == null) stringResource(Res.string.opening_balance)
                        else stringResource(Res.string.month_number, record.month + 1),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (record == null) {
                        Text(stringResource(Res.string.adventure_arrival, money(session.profile.studentMonthlyIncome)))
                    } else {
                        val decision = record.decisions.singleOrNull()
                        if (decision != null) {
                            val message = if (decision.optionId == HousingDecision.Move.optionId) {
                                Res.string.housing_moved
                            } else {
                                Res.string.housing_stayed
                            }
                            Text(stringResource(message))
                        }
                        Text(stringResource(Res.string.adventure_month, money(record.flow.income), money(record.flow.expenses)))
                        HorizontalDivider()
                        LedgerLine(stringResource(Res.string.monthly_rent), money(record.flow.rent))
                        if (record.flow.decisionCosts > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.moving_cost), money(record.flow.decisionCosts))
                        }
                    }
                    if (session.status == SessionStatus.COMPLETED) Text(stringResource(Res.string.simulation_complete))
                }
            }
        }
    }
}

@Composable
private fun AdventureFinances(session: SimulationSession, record: MonthlyRecord?, showHistory: Boolean) {
    val cash by animateFloatAsState(session.currentState.cash.cents.toFloat() / 100f, label = "cash balance")
    Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        ScreenHeading(stringResource(Res.string.adventure_title, stringResource(Res.string.maya_name)))
        KeyFigureCard(
            label = stringResource(Res.string.cash_balance),
            value = displayMoney(cash.toDouble()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (record != null) {
                val change = (record.after.cash - record.before.cash).cents / 100.0
                DeltaIndicator(displayMoney(abs(change)), DeltaDirection.fromAmount(change))
                if (showHistory) CashHistory(session)
            }
            HorizontalDivider()
            LedgerLine(stringResource(Res.string.net_worth), money(session.currentState.netWorth))
            if (record != null && showHistory) {
                LedgerLine(stringResource(Res.string.income), money(record.flow.income))
                LedgerLine(stringResource(Res.string.expenses), money(record.flow.expenses))
            }
        }
    }
}

@Composable
private fun CashHistory(session: SimulationSession) {
    val amounts = remember(session) { session.states.map { it.cash.cents } }
    val description = stringResource(Res.string.cash_history)
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = description },
    ) {
        val low = amounts.min()
        val range = (amounts.max() - low).coerceAtLeast(1)
        val line = Path()
        val fill = Path()
        for ((index, cash) in amounts.withIndex()) {
            val x = size.width * index / amounts.lastIndex
            val fraction = (cash - low).toFloat() / range
            val y = size.height * (0.85f - 0.7f * fraction)
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.lineTo(0f, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun HousingOption(decision: HousingDecision, record: MonthlyRecord, onChoose: () -> Unit) {
    val label = stringResource(if (decision == HousingDecision.Stay) Res.string.stay_choice else Res.string.move_choice)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinLifeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            LedgerLine(stringResource(Res.string.monthly_rent), money(record.flow.rent))
            LedgerLine(stringResource(Res.string.moving_cost), money(record.flow.decisionCosts))
            LedgerLine(stringResource(Res.string.closing_cash), money(record.after.cash))
            PrimaryButton(label, onChoose)
        }
    }
}

@Composable
internal fun LedgerLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun money(value: Money): String = displayMoney(value.cents / 100.0)
