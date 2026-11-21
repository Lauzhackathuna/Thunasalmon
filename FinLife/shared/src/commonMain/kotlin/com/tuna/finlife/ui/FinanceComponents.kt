package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class DeltaDirection { Up, Down, Unchanged }

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
    val colors = LocalDeltaColors.current
    val (prefix, color) = when (direction) {
        DeltaDirection.Up -> "▲ +" to colors.gain
        DeltaDirection.Down -> "▼ −" to colors.loss
        DeltaDirection.Unchanged -> "" to colors.neutral
    }
    Text(
        text = prefix + amount,
        modifier = modifier,
        color = color,
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
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = FinLifeSpacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
