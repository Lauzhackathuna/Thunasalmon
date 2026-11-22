package com.tuna.finlife.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The bank balance is the focal point. Values slide in softly, rather than jumping,
 * when the simulation advances. The engine remains the sole source of amounts.
 */
@Composable
fun KeyFigureCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingContent: @Composable () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.large
    OutlinedCard(
        modifier = modifier.shadow(3.dp, shape, clip = false).animateContentSize(tween(240)),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(FinLifeSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "FinLife balance change",
            ) { currentValue ->
                Text(currentValue, style = MaterialTheme.typography.headlineLarge)
            }
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

/** Minimal tactile feedback on Desktop and Android, without bouncy visual noise. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(125),
        label = "FinLife button press",
    )
    val blue = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (hovered && enabled) MaterialTheme.colorScheme.primaryContainer
                      else LocalButtonContainer.current,
        animationSpec = tween(150),
        label = "FinLife button hover",
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = FinLifeSpacing.touchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = blue,
        ),
        border = BorderStroke(1.dp, if (enabled) blue.copy(alpha = 0.55f) else blue.copy(alpha = 0.25f)),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
