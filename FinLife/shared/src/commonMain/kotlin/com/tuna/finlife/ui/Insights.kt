package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuna.finlife.game.AdventureOutlook
import com.tuna.finlife.game.BalanceMetric
import com.tuna.finlife.game.DifferenceBreakdown
import com.tuna.finlife.game.FlowItem
import com.tuna.finlife.game.ModelAssumptions
import com.tuna.finlife.game.Sensitivity
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.percentLabel
import com.tuna.finlife.simulation.GoalProgress
import com.tuna.finlife.simulation.Money
import finlife.shared.generated.resources.*
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Every value the engine uses for this run, grouped as on the setup screen. Read-only. */
@Composable
internal fun AssumptionsSheet(values: ModelAssumptions, onDismiss: () -> Unit) {
    GameSheet(onDismiss = onDismiss) {
        ScreenHeading(playerStringResource(Res.string.assumptions_title))
        Text(playerStringResource(Res.string.assumptions_intro), style = MaterialTheme.typography.bodyMedium)
        Section(Res.string.assumptions_section_start) {
            LedgerLine(playerStringResource(Res.string.assumptions_opening_cash), money(values.openingCash))
        }
        Section(Res.string.assumptions_section_income) {
            LedgerLine(playerStringResource(Res.string.assumptions_parent_income), money(values.parentIncome))
            LedgerLine(playerStringResource(Res.string.assumptions_internship, values.internshipMonths), money(values.internshipMonthlyPay))
            LedgerLine(playerStringResource(Res.string.assumptions_salary), money(values.salaryAfterStudies))
            LedgerLine(playerStringResource(Res.string.assumptions_salary_growth), percent(values.annualSalaryGrowth))
            LedgerLine(playerStringResource(Res.string.assumptions_salary_tax), percent(values.salaryTaxRate))
        }
        Section(Res.string.assumptions_section_studies) {
            LedgerLine(playerStringResource(Res.string.assumptions_study_length), playerStringResource(Res.string.assumptions_months, values.studyMonths))
            LedgerLine(playerStringResource(Res.string.assumptions_tuition, values.tuitionEveryMonths), money(values.tuition))
        }
        Section(Res.string.assumptions_section_costs) {
            LedgerLine(playerStringResource(Res.string.assumptions_rent), money(values.rent))
            LedgerLine(playerStringResource(Res.string.assumptions_insurance), money(values.healthInsurance))
            LedgerLine(playerStringResource(Res.string.assumptions_groceries), money(values.groceries))
            LedgerLine(playerStringResource(Res.string.assumptions_other), money(values.otherExpenses))
            LedgerLine(playerStringResource(Res.string.assumptions_inflation), percent(values.annualInflation))
        }
        Section(Res.string.assumptions_section_housing) {
            LedgerLine(playerStringResource(Res.string.assumptions_shared_rent), money(values.sharedFlatRent))
            LedgerLine(playerStringResource(Res.string.assumptions_moving), money(values.sharedFlatMovingCost))
            LedgerLine(playerStringResource(Res.string.assumptions_rent_rise), percent(values.rentRiseAnnualRate))
        }
        Section(Res.string.assumptions_section_random) {
            Text(playerStringResource(Res.string.assumptions_rent_rise_odds, values.rentRiseOdds, values.maxRentRisesPerBlock, values.blockMonths))
            Text(playerStringResource(Res.string.assumptions_repair_odds, money(values.repairCost), values.repairOdds,
                values.maxRepairsPerBlock, values.blockMonths))
        }
        Section(Res.string.assumptions_section_goal) {
            Text(playerStringResource(Res.string.goal_target, money(values.goal.nominalAmount), values.goal.dueMonth))
        }
        Section(Res.string.assumptions_section_rules) {
            Text(playerStringResource(Res.string.assumptions_rule_cash))
            if (values.investmentShare == 0.0) Text(playerStringResource(Res.string.assumptions_rule_savings))
            Text(playerStringResource(if (values.salaryTaxRate > 0.0) Res.string.assumptions_rule_taxes_salary else Res.string.assumptions_rule_taxes))
            Text(playerStringResource(Res.string.assumptions_rule_outlook))
        }
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable () -> Unit) {
    HorizontalDivider()
    Text(playerStringResource(title), style = MaterialTheme.typography.titleMedium)
    content()
}

private fun percent(rate: Double) = percentLabel(rate) + " %"

/** The engine's goal: target net worth by a month, and how far the current net worth is from it. */
@Composable
internal fun GoalProgressView(progress: GoalProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Title on its own line: beside a long target, a narrow screen would squeeze it into a column.
        Text(playerStringResource(Res.string.goal_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(playerStringResource(Res.string.goal_target, money(progress.goal.nominalAmount), progress.goal.dueMonth),
            style = MaterialTheme.typography.labelLarge)
        val fraction = (progress.percentage / 100.0).coerceIn(0.0, 1.0).toFloat()
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            playerStringResource(Res.string.goal_progress, progress.percentage.coerceAtLeast(0.0).toInt()) +
                if (progress.dueDateSimulated) " " + playerStringResource(Res.string.goal_due) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Sliders on two assumptions (rent of the current flat, income from parents). The engine recomputes the
 * 12-month outlook with the changed values; the panel shows the result against the unchanged outlook.
 */
@Composable
internal fun SensitivityPanel(draft: WhatIfDraft, unchanged: AdventureOutlook) {
    var rent by remember(draft.base) { mutableStateOf(0) }
    var income by remember(draft.base) { mutableStateOf(0) }
    val sensitivity = Sensitivity(rent, income)
    val adjusted = remember(draft.base, draft.internshipSalary, sensitivity) {
        if (sensitivity.isNeutral) null
        else runCatching { AdventureOutlook.calculate(draft.base, draft.internshipSalary, sensitivity) }.getOrNull()
    }
    Text(playerStringResource(Res.string.sensitivity_title), style = MaterialTheme.typography.titleMedium)
    Text(playerStringResource(Res.string.sensitivity_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    PercentSlider(playerStringResource(Res.string.sensitivity_rent, signedPercent(rent)), rent) { rent = it }
    PercentSlider(playerStringResource(Res.string.sensitivity_income, signedPercent(income)), income) { income = it }
    if (!sensitivity.isNeutral) {
        TextButton(onClick = { rent = 0; income = 0 }) { Text(playerStringResource(Res.string.sensitivity_reset)) }
    }
    // Same projections as the stability below: no extra engine run. Amounts are read in the chart's tooltip.
    SensitivityChart(sensitivitySeries(unchanged.selected(draft.selected), adjusted?.selected(draft.selected)), Modifier.fillMaxWidth())
    if (adjusted != null) {
        val option = adjusted.selected(draft.selected)
        Text(playerStringResource(Res.string.sensitivity_stability, stabilityLabel(option.stability.category)),
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PercentSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    val range = Sensitivity.RANGE
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(((it / Sensitivity.STEP).roundToInt() * Sensitivity.STEP).coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / Sensitivity.STEP - 1,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}

private fun signedPercent(value: Int) = when {
    value > 0 -> "+$value %"
    value < 0 -> "−${abs(value)} %"
    else -> "0 %"
}

/** "+300 CHF" for a gain, "−300 CHF" for a loss. */
@Composable
private fun signedMoney(value: Money): String =
    if (value > Money.ZERO) "+" + money(value) else if (value < Money.ZERO) "−" + money(-value) else money(value)

/** Why Maya's path and the dotted line end apart, item by item (engine amounts, no estimate). */
@Composable
internal fun DifferenceBreakdownCard(breakdown: DifferenceBreakdown, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(FinLifeSpacing.medium), verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            Text(playerStringResource(Res.string.breakdown_title), style = MaterialTheme.typography.titleMedium)
            Text(when {
                breakdown.total > Money.ZERO -> playerStringResource(Res.string.breakdown_more, breakdown.month, money(breakdown.total))
                breakdown.total < Money.ZERO -> playerStringResource(Res.string.breakdown_less, breakdown.month, money(-breakdown.total))
                else -> playerStringResource(Res.string.breakdown_same, breakdown.month)
            })
            if (breakdown.effects.isNotEmpty()) {
                Text(playerStringResource(Res.string.breakdown_items, breakdown.fromMonth + 1),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (effect in breakdown.effects) {
                    LedgerLine(playerStringResource(effect.item.label), signedMoney(effect.effect))
                }
            }
        }
    }
}

private val FlowItem.label: StringResource
    get() = when (this) {
        FlowItem.ParentIncome -> Res.string.breakdown_parent_income
        FlowItem.InternshipPay -> Res.string.breakdown_internship
        FlowItem.Salary -> Res.string.breakdown_salary
        FlowItem.Rent -> Res.string.breakdown_rent
        FlowItem.HealthInsurance -> Res.string.breakdown_insurance
        FlowItem.Groceries -> Res.string.breakdown_groceries
        FlowItem.OtherExpenses -> Res.string.breakdown_other
        FlowItem.Tuition -> Res.string.breakdown_tuition
        FlowItem.UnexpectedCosts -> Res.string.breakdown_unexpected
        FlowItem.DecisionCosts -> Res.string.breakdown_decisions
        FlowItem.Interest -> Res.string.breakdown_interest
        FlowItem.Taxes -> Res.string.breakdown_taxes
        FlowItem.InvestmentReturn -> Res.string.breakdown_return
    }
