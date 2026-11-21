package com.tuna.finlife.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.simulation_note
import finlife.shared.generated.resources.synthetic_data
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GameScreen(
    header: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TooltipHost {
        BoxWithConstraints(Modifier.fillMaxSize().background(LocalGameBackdrop.current).safeContentPadding()) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val wideRoom = maxWidth >= 760.dp * fontScale && header != null && actions != null
            val pinControls = maxHeight >= 700.dp * fontScale
            // Keep content clear of the top-right gear when the window leaves no free margin for it.
            val contentWidth = if (wideRoom) FinLifeContentWidth * 2 else FinLifeContentWidth
            val gearMargin = (FinLifeSpacing.touchTarget + FinLifeSpacing.medium * 2) * 2
            val reserveTop = onOpenSettings != null && maxWidth < contentWidth + gearMargin
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(top = if (reserveTop) FinLifeSpacing.touchTarget + FinLifeSpacing.small else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (wideRoom) {
                    Row(
                        modifier = Modifier.weight(1f).widthIn(max = FinLifeContentWidth * 2).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(FinLifeSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.large),
                    ) {
                        Box(Modifier.weight(1f)) { header() }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                        ) {
                            content()
                            actions()
                        }
                    }
                } else {
                    if (pinControls && header != null) {
                        Box(screenWidth().padding(top = FinLifeSpacing.medium)) { header() }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).then(screenWidth())
                                .padding(vertical = if (header == null) FinLifeSpacing.large else FinLifeSpacing.medium),
                            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                        ) {
                            if (!pinControls) header?.invoke()
                            content()
                            if (!pinControls) actions?.invoke()
                        }
                    }
                    if (pinControls && actions != null) {
                        Box(screenWidth().padding(vertical = FinLifeSpacing.small)) { actions() }
                    }
                }
                SimulationNotice(screenWidth().padding(vertical = FinLifeSpacing.small))
            }
            if (onOpenSettings != null) {
                SettingsGearButton(onOpenSettings, Modifier.align(Alignment.TopEnd).padding(FinLifeSpacing.medium))
            }
        }
    } }
}

private fun screenWidth(): Modifier =
    Modifier.widthIn(max = FinLifeContentWidth).fillMaxWidth().padding(horizontal = FinLifeSpacing.large)

@Composable
internal fun SimulationNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
    ) {
        HorizontalDivider()
        Text(stringResource(Res.string.synthetic_data), style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(Res.string.simulation_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ScreenHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier.semantics { heading() },
    )
}
