package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.FieldError
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.StartingDataForm
import com.tuna.finlife.game.StartingField
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.i18n.displayMoney
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.error_empty
import finlife.shared.generated.resources.error_negative
import finlife.shared.generated.resources.error_number
import finlife.shared.generated.resources.error_too_large
import finlife.shared.generated.resources.field_groceries
import finlife.shared.generated.resources.field_health_insurance
import finlife.shared.generated.resources.field_internship_salary
import finlife.shared.generated.resources.field_moving_cost
import finlife.shared.generated.resources.field_other_expenses
import finlife.shared.generated.resources.field_parent_income
import finlife.shared.generated.resources.field_rent
import finlife.shared.generated.resources.field_salary
import finlife.shared.generated.resources.field_shared_rent
import finlife.shared.generated.resources.field_tuition
import finlife.shared.generated.resources.opening_cash
import finlife.shared.generated.resources.setup_defaults
import finlife.shared.generated.resources.setup_hint
import finlife.shared.generated.resources.setup_section_expenses
import finlife.shared.generated.resources.setup_section_start
import finlife.shared.generated.resources.setup_section_studies
import finlife.shared.generated.resources.setup_section_whatif
import finlife.shared.generated.resources.setup_start
import finlife.shared.generated.resources.setup_demo
import finlife.shared.generated.resources.setup_demo_hint
import finlife.shared.generated.resources.setup_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val Sections: List<Pair<StringResource, List<StartingField>>> = listOf(
    Res.string.setup_section_start to listOf(StartingField.OpeningCash, StartingField.ParentIncome),
    Res.string.setup_section_expenses to listOf(
        StartingField.Rent, StartingField.HealthInsurance, StartingField.Groceries, StartingField.OtherExpenses,
    ),
    Res.string.setup_section_studies to listOf(StartingField.Tuition, StartingField.SalaryAfterStudies, StartingField.InternshipSalary),
    Res.string.setup_section_whatif to listOf(StartingField.SharedFlatRent, StartingField.SharedFlatMovingCost),
)

/** Editable starting data. Start is enabled only when every field is a valid whole-franc amount. */
@Composable
internal fun SetupScreen(
    initial: StartingData,
    playerName: String,
    onStart: (StartingData) -> Unit,
    onOpenSettings: () -> Unit,
    onDemo: (() -> Unit)? = null,
) {
    var texts by remember(initial) { mutableStateOf(StartingDataForm.texts(initial)) }
    val data = StartingDataForm.build(texts)
    GameScreen(
        onOpenSettings = onOpenSettings,
        actions = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                PrimaryButton(stringResource(Res.string.setup_start), { data?.let(onStart) }, enabled = data != null)
                PrimaryButton(
                    stringResource(Res.string.setup_defaults),
                    { texts = StartingDataForm.texts(StartingData.Default) },
                )
            }
        },
    ) {
        ScreenHeading(stringResource(Res.string.setup_title, playerName))
        Text(
            stringResource(Res.string.setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onDemo != null) {
            PrimaryButton(stringResource(Res.string.setup_demo), onDemo)
            Text(stringResource(Res.string.setup_demo_hint), style = MaterialTheme.typography.bodySmall)
        }
        StartingAmountFields(texts) { field, value -> texts = texts + (field to value) }
    }
}

@Composable
internal fun StartingAmountFields(texts: Map<StartingField, String>, onChange: (StartingField, String) -> Unit) {
    val errors = StartingDataForm.errors(texts)
    for ((title, fields) in Sections) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        for (field in fields) {
            AmountField(field, texts.getValue(field), errors[field]) { onChange(field, it) }
        }
    }
}

@Composable
private fun AmountField(field: StartingField, text: String, error: FieldError?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(field.label)) },
        suffix = { Text(LocalCurrencyCode.current) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(errorText(it)) } },
        keyboardOptions = KeyboardOptions(
            // A minus sign is only meaningful for opening cash; number pads often lack it.
            keyboardType = if (field.allowsNegative) KeyboardType.Text else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun errorText(error: FieldError): String = when (error) {
    FieldError.Empty -> stringResource(Res.string.error_empty)
    FieldError.NotAWholeNumber -> stringResource(Res.string.error_number)
    FieldError.Negative -> stringResource(Res.string.error_negative)
    FieldError.TooLarge -> stringResource(Res.string.error_too_large, displayMoney(StartingData.MAX_ABS_CHF.toDouble()))
}

private val StartingField.label: StringResource
    get() = when (this) {
        StartingField.OpeningCash -> Res.string.opening_cash
        StartingField.ParentIncome -> Res.string.field_parent_income
        StartingField.Rent -> Res.string.field_rent
        StartingField.HealthInsurance -> Res.string.field_health_insurance
        StartingField.Groceries -> Res.string.field_groceries
        StartingField.OtherExpenses -> Res.string.field_other_expenses
        StartingField.Tuition -> Res.string.field_tuition
        StartingField.SalaryAfterStudies -> Res.string.field_salary
        StartingField.InternshipSalary -> Res.string.field_internship_salary
        StartingField.SharedFlatRent -> Res.string.field_shared_rent
        StartingField.SharedFlatMovingCost -> Res.string.field_moving_cost
    }

@Preview
@Composable
private fun SetupScreenPreview() {
    FinLifeTheme { SetupScreen(StartingData.Default, playerName = "Alex", onStart = {}, onOpenSettings = {}) }
}
