package com.tuna.finlife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.BalanceMetric
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.DifferenceBreakdown
import com.tuna.finlife.game.ModelAssumptions
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.kind
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.MonthlySimulation
import com.tuna.finlife.simulation.PendingDecision
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.TwelveMonthProjection
import com.tuna.finlife.simulation.SimulationSessions
import finlife.shared.generated.resources.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.tuna.finlife.simulation.LifeStatus
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.advance_month
import finlife.shared.generated.resources.adventure_arrival
import finlife.shared.generated.resources.adventure_month
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.closing_cash
import finlife.shared.generated.resources.decision_costs
import finlife.shared.generated.resources.end_simulation
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.financial_details
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.monthly_rent
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.opening_balance
import finlife.shared.generated.resources.close_settings
import finlife.shared.generated.resources.simulation_complete
import finlife.shared.generated.resources.unexpected_costs
import finlife.shared.generated.resources.unpaid_bills
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
    startingData: StartingData,
    onUpdateAmounts: (StartingData) -> Boolean,
    onWhatIfToggle: (WhatIf, Boolean) -> Unit,
    sidePanel: SidePanel? = null,
) {
    val record = remember(session) { session.history.lastOrNull() }
    val pending = remember(session) {
        if (session.status == SessionStatus.AWAITING_DECISION) SimulationSessions.pendingDecisions(session) else emptyList()
    }
    var whatIfOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var assumptionsOpen by remember { mutableStateOf(false) }
    var chartMetric by remember { mutableStateOf(BalanceMetric.Cash) }
    val outlook = rememberOutlook(whatIfDraft).getOrNull()
    val selectedProjection = outlook?.selected(whatIfDraft.selected)?.projection
    val baselineProjection = outlook?.baseline?.projection
    if (whatIfOpen) WhatIfDialog(whatIfDraft, onWhatIfToggle, onDismiss = { whatIfOpen = false })
    if (assumptionsOpen) {
        AssumptionsSheet(remember(session, startingData) { ModelAssumptions.of(session, startingData.internshipSalary) }, onDismiss = {
            assumptionsOpen = false
        }, firstName = session.profile.name)
    }
    if (detailsOpen) FinancialDetailsSheet(session.currentState, record, startingData, onUpdateAmounts) { detailsOpen = false }
    GameScreen(
        onOpenSettings = onOpenSettings,
        sidePanel = sidePanel,
        dockSidePanel = true,
        dashboardFocus = true,
        header = {
            AdventureFinances(session, record, counterfactual, showHistory = pending.isEmpty(),
                projection = if (pending.isEmpty()) selectedProjection else null,
                baseline = if (pending.isEmpty() && whatIfDraft.selected.isNotEmpty()) baselineProjection else null,
                chartMetric = chartMetric,
                onMetricChange = { chartMetric = it },
                onShowDetails = { detailsOpen = true })
        },
        actions = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (session.status == SessionStatus.READY) {
                    Button(onClick = onAdvance, modifier = Modifier.heightIn(min = FinLifeSpacing.touchTarget)) { Text(stringResource(Res.string.advance_month)) }
                }
                if (pending.isEmpty() && session.status != SessionStatus.COMPLETED) {
                    PrimaryButton(stringResource(Res.string.whatif_title), { whatIfOpen = true })
                }
                PrimaryButton(stringResource(Res.string.assumptions_open), { assumptionsOpen = true })
                TextButton(onClick = onEnd) { Text(stringResource(Res.string.end_simulation)) }
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
                        Text(stringResource(Res.string.adventure_arrival, session.profile.name, money(session.profile.studentMonthlyIncome)))
                    } else {
                        val events = remember(session) { session.calendar.associateBy { it.id } }
                        for (outcome in record.events) {
                            val event = events[outcome.eventId] ?: continue
                            outcomeText(event.kind(), outcome, record, session.profile.name)?.let { Text(it) }
                        }
                        Text(stringResource(Res.string.adventure_month, session.profile.name, money(record.flow.income), money(record.flow.expenses)))
                        HorizontalDivider()
                        LedgerLine(stringResource(Res.string.monthly_rent), money(record.flow.rent))
                        if (record.flow.decisionCosts > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.decision_costs), money(record.flow.decisionCosts))
                        }
                        if (record.flow.exceptionalExpenses > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.unexpected_costs), money(record.flow.exceptionalExpenses))
                        }
                        if (record.flow.taxes > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.tax_expense), money(record.flow.taxes))
                        }
                        if (record.flow.tuition > Money.ZERO) Text(stringResource(Res.string.event_tuition, money(record.flow.tuition)))
                        if (record.before.shortInternship?.active(record.month) == true && record.after.shortInternship?.active(record.after.month) == false)
                            Text(stringResource(Res.string.event_stage_end))
                        if (record.before.status != LifeStatus.EMPLOYED && record.after.status == LifeStatus.EMPLOYED)
                            Text(stringResource(Res.string.event_employment))
                        // Cash never goes below zero in the engine: what could not be paid stays owed.
                        if (record.financingNeed > Money.ZERO) {
                            LedgerLine(stringResource(Res.string.unpaid_bills), money(record.financingNeed))
                        }
                    }
                    if (session.status == SessionStatus.COMPLETED) Text(stringResource(Res.string.simulation_complete))
                }
            }
            OutlookCard(whatIfDraft, chartMetric, outlook)
        }
    }
}

/** Pop-up with one switch per what-if. Switches can change freely until the month is advanced. */
@Composable
private fun WhatIfDialog(draft: WhatIfDraft, onToggle: (WhatIf, Boolean) -> Unit, onDismiss: () -> Unit) {
    GameSheet(onDismiss = onDismiss, sheetMaxWidth = 900.dp) {
        ScreenHeading(stringResource(Res.string.whatif_title))
        Text(stringResource(Res.string.preview_only))
        Text(stringResource(Res.string.stage_assumption, money(Money.chf(draft.internshipSalary)), money(Money.chf(draft.internshipSalary).scaled(3.0))))
        for (whatIf in WhatIf.entries) {
            val on = draft.isOn(whatIf)
            val enabled = draft.canSwitch(whatIf)
            Row(
                Modifier.fillMaxWidth().heightIn(min = FinLifeSpacing.touchTarget)
                    .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle(whatIf, it) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                Text(stringResource(whatIf.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = on, onCheckedChange = null, enabled = enabled)
            }
        }
        Text(stringResource(Res.string.whatif_dashboard_hint), style = MaterialTheme.typography.bodyMedium)
    }
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
    projection: TwelveMonthProjection?,
    baseline: TwelveMonthProjection?,
    chartMetric: BalanceMetric,
    onMetricChange: (BalanceMetric) -> Unit,
    onShowDetails: () -> Unit,
) {
    var explainEquality by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
        KeyFigureCard(
            label = stringResource(Res.string.actual_cash),
            value = money(session.currentState.cash),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (record != null) {
                val change = (record.after.cash - record.before.cash).cents / 100.0
                DeltaIndicator(displayMoney(abs(change)), DeltaDirection.fromAmount(change))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(FinLifeSpacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.xSmall)) {
                        Text(stringResource(Res.string.net_worth), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(session.currentState.netWorth), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.xSmall)) {
                        Text(stringResource(Res.string.theoretical_balance), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(session.currentState.theoreticalCash), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (session.currentState.netWorth == session.currentState.theoreticalCash &&
                session.currentState.investments == Money.ZERO && session.currentState.debt == Money.ZERO) {
                TextButton(onClick = { explainEquality = !explainEquality }) {
                    Text(stringResource(Res.string.equal_metrics_toggle))
                }
                AnimatedVisibility(visible = explainEquality) {
                    Text(stringResource(Res.string.equal_metrics_hint).replace("Maya", session.profile.name),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (session.currentState.unpaid.items.isNotEmpty()) {
                Text(stringResource(Res.string.shortfall_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (record != null && showHistory) {
                LedgerLine(stringResource(Res.string.income), money(record.flow.income))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.expenses), Modifier.weight(1f))
                if (record != null) Text(money(record.flow.expenses), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onShowDetails) { Text(stringResource(Res.string.financial_details)) }
            }
            HorizontalDivider()
            GoalProgressView(session.goal)
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(FinLifeSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                Text(stringResource(Res.string.dashboard_timeline_title),
                    style = MaterialTheme.typography.titleLarge)
                CashChart(session, counterfactual, Modifier.fillMaxWidth(),
                    projection = projection, baseline = baseline, combineHistory = projection != null,
                    selectedMetric = chartMetric, onSelectMetric = onMetricChange)
            }
        }
        val breakdown = remember(session, counterfactual) { counterfactual?.let { DifferenceBreakdown.of(session, it) } }
        if (breakdown != null) DifferenceBreakdownCard(breakdown, firstName = session.profile.name)
    }
}

@Composable
private fun PendingDecisionView(session: SimulationSession, decision: PendingDecision, onChoose: (String) -> Unit) {
    val kind = remember(decision) { decision.event.kind() }
    val previews = remember(session, decision) {
        decision.options.associate { it.id to AdventureRun.preview(session, decision.event.id, it.id) }
    }
    val shared = remember(session, decision) {
        MonthlySimulation.sharedContract(decision.event.month, session.configuration).monthlyRent
    }
    val copy = decisionCopy(kind, decision.event.month, shared, session.configuration.sharedMovingCostAt(decision.event.month), session.profile.name)
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
internal fun money(value: Money): String = displayMoney(value)
