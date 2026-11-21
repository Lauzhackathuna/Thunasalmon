package com.tuna.finlife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.kind
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.PendingDecision
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.advance_month
import finlife.shared.generated.resources.adventure_arrival
import finlife.shared.generated.resources.adventure_month
import finlife.shared.generated.resources.adventure_title
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.closing_cash
import finlife.shared.generated.resources.decision_costs
import finlife.shared.generated.resources.end_simulation
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.maya_name
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.monthly_rent
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.opening_balance
import finlife.shared.generated.resources.close_settings
import finlife.shared.generated.resources.simulation_complete
import finlife.shared.generated.resources.unexpected_costs
import finlife.shared.generated.resources.whatif_hint
import finlife.shared.generated.resources.whatif_internship
import finlife.shared.generated.resources.whatif_shared_flat
import finlife.shared.generated.resources.whatif_title
import kotlin.math.abs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdventureScreen(
    session: SimulationSession,
    onOpenSettings: () -> Unit,
    onAdvance: () -> Unit,
    onEnd: () -> Unit,
    onChoose: (eventId: String, optionId: String) -> Unit,
    whatIfDraft: WhatIfDraft,
    counterfactual: Counterfactual?,
    onWhatIfToggle: (WhatIf, Boolean) -> Unit,
) {
    val record = remember(session) { session.history.lastOrNull() }
    val pending = remember(session) {
        if (session.status == SessionStatus.AWAITING_DECISION) SimulationSessions.pendingDecisions(session) else emptyList()
    }
    var whatIfOpen by remember { mutableStateOf(false) }
    if (whatIfOpen) WhatIfDialog(whatIfDraft, onWhatIfToggle, onDismiss = { whatIfOpen = false })
    GameScreen(
        onOpenSettings = onOpenSettings,
        header = { AdventureFinances(session, record, counterfactual, showHistory = pending.isEmpty()) },
        actions = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (session.status == SessionStatus.READY) {
                    PrimaryButton(stringResource(Res.string.advance_month), onAdvance)
                }
                if (pending.isEmpty() && session.status != SessionStatus.COMPLETED) {
                    PrimaryButton(stringResource(Res.string.whatif_title), { whatIfOpen = true })
                }
                PrimaryButton(stringResource(Res.string.end_simulation), onEnd)
            }
        },
    ) {
        if (pending.isNotEmpty()) {
            for (decision in pending) {
                PendingDecisionView(session, decision) { optionId -> onChoose(decision.event.id, optionId) }
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
                        val events = remember(session) { session.calendar.associateBy { it.id } }
                        for (outcome in record.events) {
                            val event = events[outcome.eventId] ?: continue
                            outcomeText(event.kind(), outcome, record)?.let { Text(it) }
                        }
                        Text(stringResource(Res.string.adventure_month, money(record.flow.income), money(record.flow.expenses)))
                        HorizontalDivider()
                        LedgerLine(stringResource(Res.string.monthly_rent), money(record.flow.rent))
                        if (record.flow.decisionCosts > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.decision_costs), money(record.flow.decisionCosts))
                        }
                        if (record.flow.exceptionalExpenses > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.unexpected_costs), money(record.flow.exceptionalExpenses))
                        }
                    }
                    if (session.status == SessionStatus.COMPLETED) Text(stringResource(Res.string.simulation_complete))
                }
            }
        }
    }
}

/** Pop-up with one switch per what-if. Switches can change freely until the month is advanced. */
@Composable
private fun WhatIfDialog(draft: WhatIfDraft, onToggle: (WhatIf, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.whatif_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                Text(
                    stringResource(Res.string.whatif_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (whatIf in WhatIf.entries) {
                    val on = draft.isOn(whatIf)
                    val enabled = draft.canSwitch(whatIf)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = FinLifeSpacing.touchTarget)
                            .toggleable(
                                value = on,
                                enabled = enabled,
                                role = Role.Switch,
                                onValueChange = { onToggle(whatIf, it) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                    ) {
                        Text(stringResource(whatIf.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        // Locked-on switches (in effect from an earlier month) keep their "on" colour.
                        Switch(checked = on, onCheckedChange = null, enabled = enabled || on)
                    }
                }
            }
        },
        confirmButton = { PrimaryButton(stringResource(Res.string.close_settings), onDismiss) },
    )
}

private val WhatIf.label: StringResource
    get() = when (this) {
        WhatIf.Internship -> Res.string.whatif_internship
        WhatIf.SharedFlat -> Res.string.whatif_shared_flat
    }

@Composable
private fun AdventureFinances(
    session: SimulationSession,
    record: MonthlyRecord?,
    counterfactual: Counterfactual?,
    showHistory: Boolean,
) {
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
                if (showHistory) CashChart(session, counterfactual, Modifier.fillMaxWidth())
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
private fun PendingDecisionView(session: SimulationSession, decision: PendingDecision, onChoose: (String) -> Unit) {
    val kind = remember(decision) { decision.event.kind() }
    val previews = remember(session, decision) {
        decision.options.associate { it.id to AdventureRun.preview(session, decision.event.id, it.id) }
    }
    val copy = decisionCopy(kind, decision.event.month)
    if (copy.title.isNotEmpty()) Text(copy.title, style = MaterialTheme.typography.titleLarge)
    Text(copy.prompt, style = MaterialTheme.typography.bodyMedium)
    for (option in decision.options) {
        val outcome = previews[option.id]
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(FinLifeSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (outcome != null) {
                    LedgerLine(stringResource(Res.string.monthly_rent), money(outcome.flow.rent))
                    LedgerLine(stringResource(Res.string.decision_costs), money(outcome.flow.decisionCosts))
                    LedgerLine(stringResource(Res.string.closing_cash), money(outcome.after.cash))
                } else {
                    LedgerLine(stringResource(Res.string.decision_costs), money(option.immediateCost))
                }
                PrimaryButton(optionLabel(kind, option), { onChoose(option.id) })
            }
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
