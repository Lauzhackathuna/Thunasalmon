package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuna.finlife.game.GameAction
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.ScenarioComparison
import com.tuna.finlife.i18n.displayMoney
import com.tuna.finlife.i18n.displayMonths
import com.tuna.finlife.model.DemoScenarios
import com.tuna.finlife.model.ScenarioId
import com.tuna.finlife.model.appliesTo
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.alternative
import finlife.shared.generated.resources.baseline
import finlife.shared.generated.resources.constraint_breaches
import finlife.shared.generated.resources.goal_feasibility
import finlife.shared.generated.resources.liquidity_months
import finlife.shared.generated.resources.month_number
import finlife.shared.generated.resources.net_worth
import finlife.shared.generated.resources.scenario_internship
import finlife.shared.generated.resources.scenario_internship_description
import finlife.shared.generated.resources.scenario_property
import finlife.shared.generated.resources.scenario_property_description
import finlife.shared.generated.resources.scenario_rent
import finlife.shared.generated.resources.scenario_rent_description
import finlife.shared.generated.resources.simulation_complete
import finlife.shared.generated.resources.what_if
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WhatIfSheet(
    state: GameState.Playing,
    compare: (ScenarioId) -> ScenarioComparison?,
    onAction: (GameAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedScenario by remember { mutableStateOf(ScenarioId.S037) }
    val comparison = remember(state.snapshot, state.activeScenarios, selectedScenario) {
        compare(selectedScenario)
    }
    GameSheet(onDismiss, sheetMaxWidth = FinLifeContentWidth * 1.5f) {
        ScreenHeading(stringResource(Res.string.what_if))
        if (!state.canAdvanceMonth) {
            Text(stringResource(Res.string.simulation_complete))
        }
        val controls: @Composable () -> Unit = {
            ScenarioControls(state, selectedScenario, onSelect = { selectedScenario = it }, onAction)
        }
        val details: @Composable () -> Unit = {
            Text(scenarioDescription(selectedScenario), style = MaterialTheme.typography.bodyMedium)
            if (comparison != null) ScenarioMetrics(comparison)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 560.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.large)) {
                    Column(Modifier.weight(0.8f)) { controls() }
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                    ) {
                        details()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                    controls()
                    HorizontalDivider()
                    details()
                }
            }
        }
    }
}

@Composable
private fun ScenarioControls(
    state: GameState.Playing,
    selectedScenario: ScenarioId,
    onSelect: (ScenarioId) -> Unit,
    onAction: (GameAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        for (scenario in ScenarioId.entries) {
            if (!scenario.appliesTo(state.persona)) continue
            val label = stringResource(scenario.label)
            val selected = scenario == selectedScenario
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { onSelect(scenario) },
                    modifier = Modifier.weight(1f).semantics { this.selected = selected },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = scenario in state.activeScenarios,
                    onCheckedChange = {
                        onSelect(scenario)
                        onAction(GameAction.SetScenario(scenario, it))
                    },
                    modifier = Modifier.semantics { contentDescription = label },
                    enabled = state.canAdvanceMonth,
                )
            }
        }
    }
}

@Composable
private fun ScenarioMetrics(comparison: ScenarioComparison) {
    val (baseline, alternative) = comparison
    Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        Text(
            stringResource(Res.string.month_number, baseline.monthIndex + 1),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
            Spacer(Modifier.weight(1.2f))
            Text(stringResource(Res.string.baseline), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(Res.string.alternative), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
        ComparisonRow(
            stringResource(Res.string.liquidity_months),
            displayMonths(baseline.liquidityMonths),
            displayMonths(alternative.liquidityMonths),
        )
        ComparisonRow(
            stringResource(Res.string.net_worth),
            displayMoney(baseline.netWorth),
            displayMoney(alternative.netWorth),
        )
        ComparisonRow(
            stringResource(Res.string.goal_feasibility),
            stringResource(baseline.goalFeasibility.label),
            stringResource(alternative.goalFeasibility.label),
        )
        ComparisonRow(
            stringResource(Res.string.constraint_breaches),
            baseline.constraintBreaches.toString(),
            alternative.constraintBreaches.toString(),
        )
    }
}

@Composable
private fun ComparisonRow(label: String, baseline: String, alternative: String) {
    val style = MaterialTheme.typography.bodyMedium
    Row(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
        Text(label, Modifier.weight(1.2f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(baseline, Modifier.weight(1f), style = style)
        Text(alternative, Modifier.weight(1f), style = style)
    }
}

private val ScenarioId.label: StringResource
    get() = when (this) {
        ScenarioId.S037 -> Res.string.scenario_rent
        ScenarioId.S039 -> Res.string.scenario_property
        ScenarioId.S005 -> Res.string.scenario_internship
    }

@Composable
private fun scenarioDescription(scenario: ScenarioId): String {
    val month = DemoScenarios.FIRST_EFFECT_MONTH_INDEX + 1
    return when (scenario) {
        ScenarioId.S037 -> stringResource(
            Res.string.scenario_rent_description,
            month,
            displayMoney(DemoScenarios.RENT_SURCHARGE_CHF),
        )
        ScenarioId.S039 -> stringResource(
            Res.string.scenario_property_description,
            month,
            displayMoney(DemoScenarios.PROPERTY_BASELINE_CHF),
            displayMoney(DemoScenarios.PROPERTY_ALTERNATIVE_CHF),
        )
        ScenarioId.S005 -> stringResource(
            Res.string.scenario_internship_description,
            month,
            displayMoney(DemoScenarios.INTERNSHIP_INCOME_CHF),
        )
    }
}
