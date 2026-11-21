package com.tuna.finlife.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.StartingDataForm
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyRecord
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.decision_costs
import finlife.shared.generated.resources.edit_financial_amounts
import finlife.shared.generated.resources.expenses
import finlife.shared.generated.resources.field_groceries
import finlife.shared.generated.resources.field_health_insurance
import finlife.shared.generated.resources.field_internship_salary
import finlife.shared.generated.resources.field_other_expenses
import finlife.shared.generated.resources.field_parent_income
import finlife.shared.generated.resources.field_salary
import finlife.shared.generated.resources.field_tuition
import finlife.shared.generated.resources.financial_details
import finlife.shared.generated.resources.financial_edit_error
import finlife.shared.generated.resources.financial_edit_hint
import finlife.shared.generated.resources.income
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.monthly_rent
import finlife.shared.generated.resources.no_closed_month
import finlife.shared.generated.resources.paid_expenses
import finlife.shared.generated.resources.interest_expense
import finlife.shared.generated.resources.save_financial_amounts
import finlife.shared.generated.resources.unexpected_costs
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinancialDetailsSheet(
    record: MonthlyRecord?,
    initial: StartingData,
    onSave: (StartingData) -> Boolean,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var texts by remember(initial) { mutableStateOf(StartingDataForm.texts(initial)) }
    var saveFailed by remember { mutableStateOf(false) }

    val data = if (editing) StartingDataForm.build(texts) else null
    GameSheet(
        onDismiss,
        footerAction = if (editing) {
            {
                PrimaryButton(
                    label = stringResource(Res.string.save_financial_amounts),
                    enabled = data != null,
                    onClick = {
                        if (data != null) {
                            saveFailed = !onSave(data)
                            if (!saveFailed) editing = false
                        }
                    },
                )
            }
        } else null,
    ) {
        ScreenHeading(stringResource(Res.string.financial_details))
        if (editing) {
            Text(stringResource(Res.string.financial_edit_hint))
            if (saveFailed) Text(stringResource(Res.string.financial_edit_error), color = MaterialTheme.colorScheme.error)
            StartingAmountFields(texts) { field, value ->
                texts = texts + (field to value)
                saveFailed = false
            }
        } else {
            TextButton(onClick = { editing = true }) { Text(stringResource(Res.string.edit_financial_amounts)) }
            if (record == null) {
                Text(stringResource(Res.string.no_closed_month))
            } else {
                Text(stringResource(Res.string.month_number, record.month + 1), style = MaterialTheme.typography.titleMedium)
                val flow = record.flow
                LedgerLine(stringResource(Res.string.income), money(flow.income))
                LedgerLine(stringResource(Res.string.field_parent_income), money(flow.studentIncome))
                LedgerLine(stringResource(Res.string.field_internship_salary), money(flow.internshipIncome))
                LedgerLine(stringResource(Res.string.field_salary), money(flow.professionalIncome))
                LedgerLine(stringResource(Res.string.expenses), money(flow.expenses))
                LedgerLine(stringResource(Res.string.monthly_rent), money(flow.rent))
                LedgerLine(stringResource(Res.string.field_health_insurance), money(flow.healthInsurance))
                LedgerLine(stringResource(Res.string.field_groceries), money(flow.groceries))
                LedgerLine(stringResource(Res.string.field_other_expenses), money(flow.otherExpenses))
                LedgerLine(stringResource(Res.string.field_tuition), money(flow.tuition))
                if (flow.exceptionalExpenses != Money.ZERO) LedgerLine(stringResource(Res.string.unexpected_costs), money(flow.exceptionalExpenses))
                if (flow.decisionCosts != Money.ZERO) LedgerLine(stringResource(Res.string.decision_costs), money(flow.decisionCosts))
                if (flow.interest != Money.ZERO) LedgerLine(stringResource(Res.string.interest_expense), money(flow.interest))
                if (flow.paidExpenses != flow.expenses) LedgerLine(stringResource(Res.string.paid_expenses), money(flow.paidExpenses))
            }
        }
    }
}
