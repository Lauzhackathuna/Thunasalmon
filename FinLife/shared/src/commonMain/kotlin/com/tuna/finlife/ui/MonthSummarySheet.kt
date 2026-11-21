package com.tuna.finlife.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tuna.finlife.game.RoomMonthChanges
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
import finlife.shared.generated.resources.room_changes
import finlife.shared.generated.resources.furniture_condition_change
import finlife.shared.generated.resources.room_upgrade
import finlife.shared.generated.resources.tuition_due
import finlife.shared.generated.resources.event_higher_rent
import finlife.shared.generated.resources.event_lower_property
import finlife.shared.generated.resources.event_paid_internship
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MonthSummarySheet(outcome: MonthOutcome, roomChanges: RoomMonthChanges, onDismiss: () -> Unit) {
    GameSheet(onDismiss) {
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
                    LifeEvent.HigherRent -> Res.string.event_higher_rent
                    LifeEvent.LowerPropertyValue -> Res.string.event_lower_property
                    LifeEvent.PaidInternship -> Res.string.event_paid_internship
                }
                Text(stringResource(label))
            }
        }
        if (roomChanges.damage != null || roomChanges.upgradedTo != null) {
            Text(stringResource(Res.string.room_changes), style = MaterialTheme.typography.titleMedium)
            roomChanges.damage?.let {
                Text(
                    stringResource(
                        Res.string.furniture_condition_change,
                        stringResource(it.item.label),
                        stringResource(it.newCondition.label),
                    ),
                )
            }
            roomChanges.upgradedTo?.let { Text(stringResource(Res.string.room_upgrade, stringResource(it.label))) }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(amount, style = MaterialTheme.typography.titleMedium)
}
