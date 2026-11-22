package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.RunSummary
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SimulationSession
import finlife.shared.generated.resources.*
import com.tuna.finlife.game.kind
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.continue_simulation
import finlife.shared.generated.resources.months_simulated
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.start_over
import finlife.shared.generated.resources.summary_title
import finlife.shared.generated.resources.total_expenses
import finlife.shared.generated.resources.total_income
import finlife.shared.generated.resources.unpaid_bills
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SummaryScreen(
    session: SimulationSession,
    onContinue: (() -> Unit)?,
    onStartOver: () -> Unit,
    onOpenSettings: () -> Unit,
    sidePanel: SidePanel? = null,
) {
    val summary = remember(session) { RunSummary.of(session) }
    GameScreen(
        onOpenSettings = onOpenSettings,
        sidePanel = sidePanel,
        actions = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (onContinue != null) PrimaryButton(stringResource(Res.string.continue_simulation), onContinue)
                PrimaryButton(stringResource(Res.string.start_over), onStartOver)
            }
        },
    ) {
        ScreenHeading(stringResource(Res.string.summary_title))
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(FinLifeSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                Text(
                    stringResource(Res.string.months_simulated, summary.monthsSimulated),
                    style = MaterialTheme.typography.titleLarge,
                )
                HorizontalDivider()
                LedgerLine(stringResource(Res.string.actual_cash), money(summary.cash))
                LedgerLine(stringResource(Res.string.opening_balance), money(summary.initialCash))
                LedgerLine(stringResource(Res.string.net_worth), money(summary.netWorth))
                LedgerLine(stringResource(Res.string.theoretical_balance), money(summary.theoretical))
                LedgerLine(stringResource(Res.string.paid_expenses), money(summary.totalPaid))
                LedgerLine(stringResource(Res.string.total_income), money(summary.totalIncome))
                LedgerLine(stringResource(Res.string.total_expenses), money(summary.totalExpenses))
                if (summary.unpaid > Money.ZERO) {
                    LedgerLine(stringResource(Res.string.unpaid_bills), money(summary.unpaid))
                    Text(stringResource(Res.string.shortfall_hint), color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text(stringResource(Res.string.journey_recap), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.journey_change, money(summary.netWorth - summary.initialNetWorth), money(summary.initialNetWorth)))
                if (summary.internshipIncome > Money.ZERO) Text(stringResource(Res.string.journey_stage, money(summary.internshipIncome)))
                val events = remember(session) { session.calendar.associateBy { it.id } }
                var showEvents by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.material3.TextButton(onClick = { showEvents = !showEvents }) {
                    Text(stringResource(Res.string.journey_recap))
                }
                if (showEvents) for (record in session.history) for (outcome in record.events) {
                    events[outcome.eventId]?.let { event ->
                        outcomeText(event.kind(), outcome, record)?.let { text ->
                            Text(stringResource(Res.string.month_number, record.month + 1) + " · " + text)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SummaryScreenPreview() {
    // Synthetic demo run: the first three months of the adventure with a fixed seed.
    val session = remember { (1..3).fold(AdventureRun.start(seed = 1)) { run, _ -> AdventureRun.advance(run) } }
    FinLifeTheme {
        SummaryScreen(session, onContinue = {}, onStartOver = {}, onOpenSettings = {})
    }
}
