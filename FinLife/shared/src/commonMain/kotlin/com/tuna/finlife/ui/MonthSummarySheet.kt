package com.tuna.finlife.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.MonthOutcome
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.arrival_costs
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.month_summary
import finlife.shared.generated.resources.month_events
import finlife.shared.generated.resources.room_purchases
import finlife.shared.generated.resources.tuition_due
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MonthSummarySheet(outcome: MonthOutcome, roomPurchases: Int, onDismiss: () -> Unit) {
    GameSheet(onDismiss) {
        ScreenHeading(stringResource(Res.string.month_summary))
        Text(stringResource(Res.string.month_number, outcome.snapshot.monthIndex + 1))
        SummaryAmount(stringResource(Res.string.income), displayMoney(outcome.income))
        SummaryAmount(stringResource(Res.string.expenses), displayMoney(outcome.expenses))
        if (roomPurchases > 0) {
            SummaryAmount(stringResource(Res.string.room_purchases), displayMoney(roomPurchases.toDouble()))
        }
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
    }
}

@Composable
private fun SummaryAmount(label: String, amount: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(amount, style = MaterialTheme.typography.titleMedium)
}
