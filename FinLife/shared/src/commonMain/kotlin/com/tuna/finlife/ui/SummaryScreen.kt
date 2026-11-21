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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.RunSummary
import com.tuna.finlife.simulation.SimulationSession
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.continue_simulation
import finlife.shared.generated.resources.months_simulated
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.start_over
import finlife.shared.generated.resources.summary_title
import finlife.shared.generated.resources.total_expenses
import finlife.shared.generated.resources.total_income
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SummaryScreen(
    session: SimulationSession,
    onContinue: (() -> Unit)?,
    onStartOver: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val summary = remember(session) { RunSummary.of(session) }
    GameScreen(
        onOpenSettings = onOpenSettings,
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
                LedgerLine(stringResource(Res.string.cash_balance), money(summary.cash))
                LedgerLine(stringResource(Res.string.net_worth), money(summary.netWorth))
                LedgerLine(stringResource(Res.string.total_income), money(summary.totalIncome))
                LedgerLine(stringResource(Res.string.total_expenses), money(summary.totalExpenses))
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
