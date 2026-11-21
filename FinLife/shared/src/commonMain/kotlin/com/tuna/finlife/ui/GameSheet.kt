package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.close_settings
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameSheet(
    onDismiss: () -> Unit,
    sheetMaxWidth: Dp = FinLifeContentWidth,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val viewportHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Leave space for the sheet handle and system insets when content needs to scroll.
    val heightLimit = if (viewportHeight > 80.dp) Modifier.heightIn(max = viewportHeight - 80.dp) else Modifier
    val bodyScrolls = viewportHeight == 0.dp || viewportHeight >= 360.dp * density.fontScale.coerceAtLeast(1f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetMaxWidth = sheetMaxWidth,
    ) {
        val sheetScroll = if (bodyScrolls) Modifier else Modifier.verticalScroll(rememberScrollState())
        Column(
            modifier = Modifier.fillMaxWidth().then(heightLimit).then(sheetScroll)
                .padding(horizontal = FinLifeSpacing.large, vertical = FinLifeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            if (bodyScrolls) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                ) {
                    content()
                }
            } else {
                content()
            }
            SimulationNotice(Modifier.padding(top = FinLifeSpacing.small))
            PrimaryButton(
                label = stringResource(Res.string.close_settings),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
