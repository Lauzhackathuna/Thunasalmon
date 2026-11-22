package com.tuna.finlife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tuna.finlife.game.*
import com.tuna.finlife.i18n.displayMonths
import com.tuna.finlife.simulation.*
import finlife.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberOutlook(draft: WhatIfDraft): Result<AdventureOutlook> = remember(draft.base, draft.internshipSalary) {
    runCatching { AdventureOutlook.calculate(draft.base, draft.internshipSalary) }
}

@Composable
internal fun OutlookCard(draft: WhatIfDraft, metric: BalanceMetric, outlook: AdventureOutlook? = rememberOutlook(draft).getOrNull()) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FinLifeSpacing.medium), verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            Text(stringResource(Res.string.outlook_title), style = MaterialTheme.typography.titleLarge)
            if (outlook == null) {
                Text(stringResource(Res.string.outlook_unavailable))
            } else {
                val option = outlook.selected(draft.selected)
                Text(scenarioLabel(option.choices), style = MaterialTheme.typography.labelLarge)
                Checkpoints(option, if (draft.selected.isEmpty()) null else outlook.baseline, metric)
                HorizontalDivider()
                Text(stringResource(Res.string.outlook_all_metrics), style = MaterialTheme.typography.labelLarge)
                LedgerLine(stringResource(Res.string.actual_cash), money(option.at(12).cash))
                LedgerLine(stringResource(Res.string.net_worth), money(option.at(12).netWorth))
                LedgerLine(stringResource(Res.string.theoretical_balance), money(option.at(12).theoreticalCash))
                if (option.at(12).investments == Money.ZERO && option.at(12).debt == Money.ZERO) {
                    Text(stringResource(Res.string.equal_metrics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StabilityPanel(option.stability, compact = true)
            }
            Text(stringResource(Res.string.outlook_assumptions), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun FuturePreview(draft: WhatIfDraft) {
    val outlook = rememberOutlook(draft).getOrNull()
    if (outlook == null) {
        Text(stringResource(Res.string.outlook_unavailable), color = MaterialTheme.colorScheme.error)
        return
    }
    val option = outlook.selected(draft.selected)
    val impact = outlook.impact(option)
    Text(scenarioLabel(option.choices), style = MaterialTheme.typography.titleLarge)
    CashChart(draft.base, null, Modifier.fillMaxWidth(), option.projection, outlook.baseline.projection)
    Checkpoints(option, outlook.baseline)
    HorizontalDivider()
    Text(stringResource(Res.string.impact_income) + ": " + money(impact.income))
    Text(stringResource(Res.string.impact_rent) + ": " + money(impact.rentSaved))
    if (impact.movingCosts != Money.ZERO) LedgerLine(stringResource(Res.string.impact_move), money(impact.movingCosts))
    if (impact.unpaid != Money.ZERO) LedgerLine(stringResource(Res.string.avoided_unpaid), money(-impact.unpaid))
    Text(stringResource(Res.string.stage_temporary), style = MaterialTheme.typography.bodySmall)
    StabilityPanel(option.stability)
    HorizontalDivider()
    for (alternative in outlook.options) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FinLifeSpacing.small)) {
                Text(scenarioLabel(alternative.choices), style = MaterialTheme.typography.titleSmall)
                LedgerLine(stringResource(Res.string.actual_cash), money(alternative.at(12).cash))
                LedgerLine(stringResource(Res.string.theoretical_balance), money(alternative.at(12).theoreticalCash))
                Text(stabilityLabel(alternative.stability.category), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Text(stringResource(Res.string.impact_tuition), style = MaterialTheme.typography.titleSmall)
    for (record in option.projection.records.filter { it.flow.tuition > Money.ZERO }) {
        Text(stringResource(Res.string.tuition_due, record.month + 1, money(record.flow.tuition)))
    }
    Text(stringResource(Res.string.outlook_assumptions), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Checkpoints(option: FutureOption, baseline: FutureOption? = null, metric: BalanceMetric = BalanceMetric.Cash) {
    Text(metricLabel(metric), style = MaterialTheme.typography.labelSmall)
    for (offset in listOf(3, 6, 12)) {
        LedgerLine(stringResource(Res.string.checkpoint, offset), money(metric.value(option.at(offset))))
        if (baseline != null) Text(
            stringResource(Res.string.future_difference,
                money(metric.value(option.at(offset)) - metric.value(baseline.at(offset)))),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(stringResource(Res.string.future_owed, money(option.stability.finalUnpaid)),
        color = if (option.stability.finalUnpaid > Money.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(Res.string.projection_trend,
        money(option.at(12).theoreticalCash - option.at(0).theoreticalCash)), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun StabilityPanel(assessment: StabilityAssessment, compact: Boolean = false) {
    var details by remember { mutableStateOf(!compact) }
    val coverage = assessment.coverage.first()
    Text(stringResource(Res.string.stability_title) + " · " + stabilityLabel(assessment.category),
        style = MaterialTheme.typography.titleMedium,
        color = if (assessment.category == FinancialStability.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    if (compact) {
        if (coverage.balance == BudgetBalance.STRUCTURAL_DEFICIT) Text(stringResource(Res.string.stability_deficit))
        TextButton(onClick = { details = !details }) { Text(stringResource(Res.string.stability_details)) }
    }
    if (!details) return
    LedgerLine(stringResource(Res.string.essential_monthly), money(coverage.essentialExpenses))
    LedgerLine(stringResource(Res.string.envelope_reserve), money(coverage.reserve))
    Text(stringResource(if (coverage.absorbsUnexpectedExpense) Res.string.shock_absorbable else Res.string.shock_not_absorbable,
        money(StabilitySettings().unexpectedExpense)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.stability_budget, money(coverage.structuralBalance)))
    if (coverage.balance == BudgetBalance.STRUCTURAL_DEFICIT) Text(stringResource(Res.string.stability_deficit))
    Text(coverage.monthsCovered?.let { stringResource(Res.string.stability_cover, displayMonths(it)) }
        ?: stringResource(Res.string.stability_zero))
    if (assessment.deficitMonths > 0) Text(stringResource(Res.string.stability_need,
        assessment.deficitMonths, money(assessment.maximumFinancingNeed)))
    Text(stringResource(Res.string.stability_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun stabilityLabel(value: FinancialStability) = stringResource(when (value) {
    FinancialStability.STABLE -> Res.string.stability_stable
    FinancialStability.WATCH -> Res.string.stability_watch
    FinancialStability.FRAGILE -> Res.string.stability_fragile
    FinancialStability.CRITICAL -> Res.string.stability_critical
})

@Composable
private fun scenarioLabel(choices: Set<WhatIf>) = stringResource(when {
    choices.size == 2 -> Res.string.scenario_both
    WhatIf.Internship in choices -> Res.string.scenario_internship
    WhatIf.SharedFlat in choices -> Res.string.scenario_shared
    else -> Res.string.scenario_none
})
