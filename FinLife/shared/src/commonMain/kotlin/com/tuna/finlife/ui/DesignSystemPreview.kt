package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.advance_month
import finlife.shared.generated.resources.cash_balance
import finlife.shared.generated.resources.monthly_change
import finlife.shared.generated.resources.preview_cash
import finlife.shared.generated.resources.preview_delta
import finlife.shared.generated.resources.simulation_note
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
fun DesignSystemPreview() {
    var mode by remember { mutableStateOf(ThemeMode.System) }
    FinLifeTheme(mode = mode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.safeContentPadding().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = FinLifeContentWidth).fillMaxWidth().padding(FinLifeSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                        for (option in ThemeMode.entries) {
                            FilterChip(
                                selected = mode == option,
                                onClick = { mode = option },
                                label = { Text(stringResource(option.label)) },
                            )
                        }
                    }
                    // Synthetic values for the component preview.
                    KeyFigureCard(
                        label = stringResource(Res.string.cash_balance),
                        value = stringResource(Res.string.preview_cash),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.monthly_change), style = MaterialTheme.typography.bodyMedium)
                        DeltaIndicator(stringResource(Res.string.preview_delta), DeltaDirection.Up)
                    }
                    PrimaryButton(
                        label = stringResource(Res.string.advance_month),
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.simulation_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
