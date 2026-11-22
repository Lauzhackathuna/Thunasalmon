package com.tuna.finlife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
internal fun OutlookCard(draft: WhatIfDraft, metric: BalanceMetric, outlook: AdventureOutlook?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FinLifeSpacing.medium), verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            Text(playerStringResource(Res.string.outlook_title), style = MaterialTheme.typography.titleLarge)
            if (outlook == null) {
                Text(playerStringResource(Res.string.outlook_unavailable))
            } else {
                val option = outlook.selected(draft.selected)
                Text(scenarioLabel(option.choices), style = MaterialTheme.typography.labelLarge)
                Checkpoints(option, if (draft.selected.isEmpty()) null else outlook.baseline, metric)
                HorizontalDivider()
                Text(playerStringResource(Res.string.outlook_all_metrics), style = MaterialTheme.typography.labelLarge)
                LedgerLine(playerStringResource(Res.string.actual_cash), money(option.at(12).cash))
                LedgerLine(playerStringResource(Res.string.net_worth), money(option.at(12).netWorth))
                LedgerLine(playerStringResource(Res.string.theoretical_balance), money(option.at(12).theoreticalCash))
                // The balance card above already explains when net worth and balance are equal.
                StabilityPanel(option.stability, compact = true)
                HorizontalDivider()
                SensitivityPanel(draft, outlook)
            }
            Text(playerStringResource(Res.string.outlook_assumptions), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun FuturePreview(draft: WhatIfDraft) {
    val outlook = rememberOutlook(draft).getOrNull()
    if (outlook == null) {
        Text(playerStringResource(Res.string.outlook_unavailable), color = MaterialTheme.colorScheme.error)
        return
    }
    val option = outlook.selected(draft.selected)
    val impact = outlook.impact(option)
    Text(scenarioLabel(option.choices), style = MaterialTheme.typography.titleLarge)
    CashChart(draft.base, null, Modifier.fillMaxWidth(), option.projection, outlook.baseline.projection)
    Checkpoints(option, outlook.baseline)
    HorizontalDivider()
    Text(playerStringResource(Res.string.impact_income) + ": " + money(impact.income))
    Text(playerStringResource(Res.string.impact_rent) + ": " + money(impact.rentSaved))
    if (impact.movingCosts != Money.ZERO) LedgerLine(playerStringResource(Res.string.impact_move), money(impact.movingCosts))
    if (impact.unpaid != Money.ZERO) LedgerLine(playerStringResource(Res.string.avoided_unpaid), money(-impact.unpaid))
    if (WhatIf.Internship in option.choices) Text(playerStringResource(Res.string.stage_temporary), style = MaterialTheme.typography.bodySmall)
    if (WhatIf.CareerBreak in option.choices) Text(playerStringResource(Res.string.career_break_temporary), style = MaterialTheme.typography.bodySmall)
    StabilityPanel(option.stability)
    HorizontalDivider()
    for (alternative in outlook.options) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FinLifeSpacing.small)) {
                Text(scenarioLabel(alternative.choices), style = MaterialTheme.typography.titleSmall)
                LedgerLine(playerStringResource(Res.string.actual_cash), money(alternative.at(12).cash))
                LedgerLine(playerStringResource(Res.string.theoretical_balance), money(alternative.at(12).theoreticalCash))
                Text(stabilityLabel(alternative.stability.category), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Text(playerStringResource(Res.string.impact_tuition), style = MaterialTheme.typography.titleSmall)
    for (record in option.projection.records.filter { it.flow.tuition > Money.ZERO }) {
        Text(playerStringResource(Res.string.tuition_due, record.month + 1, money(record.flow.tuition)))
    }
    Text(playerStringResource(Res.string.outlook_assumptions), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Checkpoints(option: FutureOption, baseline: FutureOption? = null, metric: BalanceMetric = BalanceMetric.Cash) {
    Text(metricLabel(metric), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
    ) {
        for (offset in listOf(3, 6, 12)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    Modifier.widthIn(min = 120.dp).padding(FinLifeSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.xSmall),
                ) {
                    Text(playerStringResource(Res.string.checkpoint, offset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(metric.value(option.at(offset))),
                        style = MaterialTheme.typography.titleMedium)
                    if (baseline != null) {
                        Text(playerStringResource(Res.string.future_difference,
                            money(metric.value(option.at(offset)) - metric.value(baseline.at(offset)))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Text(playerStringResource(Res.string.future_owed, money(option.stability.finalUnpaid)),
        color = if (option.stability.finalUnpaid > Money.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    Text(playerStringResource(Res.string.projection_trend,
        money(option.at(12).theoreticalCash - option.at(0).theoreticalCash)), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun StabilityPanel(assessment: StabilityAssessment, compact: Boolean = false) {
    var details by remember { mutableStateOf(!compact) }
    val coverage = assessment.coverage.first()
    Text(playerStringResource(Res.string.stability_title) + " · " + stabilityLabel(assessment.category),
        style = MaterialTheme.typography.titleMedium,
        color = if (assessment.category == FinancialStability.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    if (compact) {
        if (coverage.balance == BudgetBalance.STRUCTURAL_DEFICIT) Text(playerStringResource(Res.string.stability_deficit))
        TextButton(onClick = { details = !details }) { Text(playerStringResource(Res.string.stability_details)) }
    }
    if (!details) return
    LedgerLine(playerStringResource(Res.string.essential_monthly), money(coverage.essentialExpenses))
    LedgerLine(playerStringResource(Res.string.envelope_reserve), money(coverage.reserve))
    Text(playerStringResource(if (coverage.absorbsUnexpectedExpense) Res.string.shock_absorbable else Res.string.shock_not_absorbable,
        money(StabilitySettings().unexpectedExpense)), style = MaterialTheme.typography.bodySmall)
    Text(playerStringResource(Res.string.stability_budget, money(coverage.structuralBalance)))
    // The compact view already states the deficit above the button.
    if (!compact && coverage.balance == BudgetBalance.STRUCTURAL_DEFICIT) Text(playerStringResource(Res.string.stability_deficit))
    Text(coverage.monthsCovered?.let { playerStringResource(Res.string.stability_cover, displayMonths(it)) }
        ?: playerStringResource(Res.string.stability_zero))
    if (assessment.deficitMonths > 0) Text(playerStringResource(Res.string.stability_need,
        assessment.deficitMonths, money(assessment.maximumFinancingNeed)))
    Text(playerStringResource(Res.string.stability_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun stabilityLabel(value: FinancialStability) = playerStringResource(when (value) {
    FinancialStability.STABLE -> Res.string.stability_stable
    FinancialStability.WATCH -> Res.string.stability_watch
    FinancialStability.FRAGILE -> Res.string.stability_fragile
    FinancialStability.CRITICAL -> Res.string.stability_critical
})

@Composable
private fun scenarioLabel(choices: Set<WhatIf>): String {
    if (choices == setOf(WhatIf.Internship, WhatIf.SharedFlat)) return playerStringResource(Res.string.scenario_both)
    if (choices.isEmpty()) return playerStringResource(Res.string.scenario_none)
    return choices.sortedBy { it.ordinal }.map { choice ->
        playerStringResource(when (choice) {
            WhatIf.Internship -> Res.string.scenario_internship
            WhatIf.SharedFlat -> Res.string.scenario_shared
            WhatIf.CareerBreak -> Res.string.scenario_career_break
        })
    }.joinToString(" + ")
}
