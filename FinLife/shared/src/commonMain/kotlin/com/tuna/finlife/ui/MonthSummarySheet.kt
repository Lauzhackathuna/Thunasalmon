package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.MonthOutcome
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.arrival_costs
import finlife.shared.generated.resources.close_summary
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.month_summary
import finlife.shared.generated.resources.month_events
import finlife.shared.generated.resources.tuition_due
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthSummarySheet(outcome: MonthOutcome, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetMaxWidth = FinLifeContentWidth,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = FinLifeSpacing.large, vertical = FinLifeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            ScreenHeading(stringResource(Res.string.month_summary))
            Text(stringResource(Res.string.month_number, outcome.snapshot.monthIndex + 1))
            SummaryAmount(stringResource(Res.string.income), displayMoney(outcome.income))
            SummaryAmount(stringResource(Res.string.expenses), displayMoney(outcome.expenses))
            if (outcome.events.isNotEmpty()) {
                Text(stringResource(Res.string.month_events), style = MaterialTheme.typography.titleMedium)
                for (event in outcome.events) {
                    val label = when (event) {
                        LifeEvent.TuitionDue -> Res.string.tuition_due
                        LifeEvent.ArrivalCosts -> Res.string.arrival_costs
                    }
                    Text(stringResource(label))
                }
            }
            SimulationNotice(Modifier.padding(top = FinLifeSpacing.small))
            PrimaryButton(
                label = stringResource(Res.string.close_summary),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(amount, style = MaterialTheme.typography.titleMedium)
}
