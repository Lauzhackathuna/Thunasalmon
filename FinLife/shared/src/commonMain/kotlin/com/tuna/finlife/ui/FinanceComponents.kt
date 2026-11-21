package com.tuna.finlife.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KeyFigureCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingContent: @Composable () -> Unit = {},
) {
    OutlinedCard(modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(FinLifeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineLarge)
            supportingContent()
        }
    }
}

/** [amount] is the localized magnitude, without a sign. */
@Composable
fun DeltaIndicator(amount: String, direction: DeltaDirection, modifier: Modifier = Modifier) {
    Text(
        text = formatDelta(amount, direction),
        modifier = modifier,
        color = LocalDeltaColors.current.colorFor(direction),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val blue = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = FinLifeSpacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LocalButtonContainer.current,
            contentColor = blue,
        ),
        border = BorderStroke(1.dp, if (enabled) blue else blue.copy(alpha = 0.38f)),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
