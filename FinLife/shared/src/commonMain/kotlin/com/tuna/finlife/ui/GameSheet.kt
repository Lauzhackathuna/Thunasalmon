package com.tuna.finlife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.close_settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A native Material sheet with an intentional entrance and a graceful exit.
 * The sheet remains mounted while hiding so the close button never cuts the animation short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameSheet(
    onDismiss: () -> Unit,
    sheetMaxWidth: Dp = FinLifeContentWidth,
    footerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val viewportHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val heightLimit = if (viewportHeight > 80.dp) Modifier.heightIn(max = viewportHeight - 80.dp) else Modifier
    val bodyScrolls = viewportHeight == 0.dp || viewportHeight >= 360.dp * density.fontScale.coerceAtLeast(1f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var contentVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(240),
        label = "FinLife sheet content reveal",
    )
    val closeSheet: () -> Unit = {
        if (!closing) {
            closing = true
            contentVisible = false
            scope.launch {
                try { sheetState.hide() } finally { onDismiss() }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetMaxWidth = sheetMaxWidth,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) { TooltipHost {
        val sheetScroll = if (bodyScrolls) Modifier else Modifier.verticalScroll(rememberScrollState())
        Column(
            modifier = Modifier.fillMaxWidth().then(heightLimit).then(sheetScroll)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = (1f - contentAlpha) * with(density) { 9.dp.toPx() }
                }
                .padding(horizontal = FinLifeSpacing.large, vertical = FinLifeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
        ) {
            if (bodyScrolls) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium),
                ) { content() }
            } else {
                content()
            }
            SimulationNotice(Modifier.padding(top = FinLifeSpacing.small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                footerAction?.invoke()
                PrimaryButton(
                    label = stringResource(Res.string.close_settings),
                    onClick = closeSheet,
                )
            }
        }
    } }
}
