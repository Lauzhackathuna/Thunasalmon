package com.tuna.finlife.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import finlife.shared.generated.resources.assistant_open
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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

/** A panel beside the game (docked) or in a dialog ([onClose] set). [reserveEnd]: the gear overlaps its top-right corner. */
internal typealias SidePanel = @Composable (onClose: (() -> Unit)?, reserveEnd: Boolean) -> Unit

@Composable
internal fun GameScreen(
    header: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    sidePanel: SidePanel? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TooltipHost {
        BoxWithConstraints(Modifier.fillMaxSize().background(LocalGameBackdrop.current).safeContentPadding()) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            // The side panel sits on the right when the game keeps at least its single-column width beside it.
            val docked = sidePanel != null && maxWidth >= SidePanelDockWidth * fontScale
            var panelOpen by remember { mutableStateOf(false) }
            var panelVisible by remember { mutableStateOf(false) }
            LaunchedEffect(panelOpen) { if (panelOpen) panelVisible = true }
            LaunchedEffect(panelVisible) {
                if (!panelVisible && panelOpen) {
                    delay(160)
                    panelOpen = false
                }
            }
            val closePanel: () -> Unit = { panelVisible = false }
            if (docked) {
                Row(Modifier.fillMaxSize()) {
                    GameBody(header, actions, cornerWidth = 0.dp, Modifier.weight(1f).fillMaxHeight(), content)
                    VerticalDivider()
                    Box(
                        Modifier.width(SidePanelWidth * fontScale.coerceAtMost(1.25f)).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        sidePanel(null, onOpenSettings != null)
                    }
                }
            } else {
                // Content stays clear of the top-right controls when the window leaves no free margin for them.
                val corner = (if (onOpenSettings != null) FinLifeSpacing.touchTarget + FinLifeSpacing.medium * 2 else 0.dp) +
                    (if (sidePanel != null) SidePanelButtonWidth else 0.dp)
                GameBody(header, actions, corner, Modifier.fillMaxSize(), content)
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(FinLifeSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            ) {
                if (sidePanel != null && !docked) PrimaryButton(stringResource(Res.string.assistant_open), { panelOpen = true })
                if (onOpenSettings != null) SettingsGearButton(onOpenSettings)
            }
            if (sidePanel != null && !docked && panelOpen) {
                Dialog(onDismissRequest = closePanel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    AnimatedVisibility(
                        visible = panelVisible,
                        enter = fadeIn(tween(210)) + scaleIn(tween(240), initialScale = 0.975f),
                        exit = fadeOut(tween(130)) + scaleOut(tween(160), targetScale = 0.98f),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f).widthIn(max = 560.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                        ) { sidePanel(closePanel, false) }
                    }
                }
            }
        }
    } }
}

/** Width from which the side panel is docked on the right instead of opened from a button. */
private val SidePanelDockWidth = 900.dp
private val SidePanelWidth = 360.dp
/** Room taken by the side panel's button next to the gear. */
private val SidePanelButtonWidth = 140.dp

@Composable
private fun GameBody(
    header: (@Composable () -> Unit)?,
    actions: (@Composable () -> Unit)?,
    cornerWidth: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val wideRoom = maxWidth >= 760.dp * fontScale && header != null && actions != null
        val pinControls = maxHeight >= 480.dp * fontScale
        val contentWidth = if (wideRoom) FinLifeContentWidth * 2 else FinLifeContentWidth
        val reserveTop = cornerWidth > 0.dp && maxWidth < contentWidth + cornerWidth * 2
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = if (reserveTop) FinLifeSpacing.touchTarget + FinLifeSpacing.small else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wideRoom) {
                Row(
                    modifier = Modifier.weight(1f).widthIn(max = FinLifeContentWidth * 2).fillMaxWidth()
                        .padding(FinLifeSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.large),
                ) {
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { header() }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                    ) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) { content() }
                        actions()
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).then(screenWidth())
                            .padding(vertical = if (header == null) FinLifeSpacing.large else FinLifeSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                    ) {
                        header?.invoke()
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
    }
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
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
    ) {
        Box(Modifier.width(4.dp).height(28.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
    }
}
