package com.tuna.finlife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.i18n.DEFAULT_CURRENCY_CODE
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.cancel
import finlife.shared.generated.resources.demo_currency
import finlife.shared.generated.resources.language
import finlife.shared.generated.resources.language_english
import finlife.shared.generated.resources.language_french
import finlife.shared.generated.resources.reset_run
import finlife.shared.generated.resources.reset_run_message
import finlife.shared.generated.resources.settings
import finlife.shared.generated.resources.theme
import finlife.shared.generated.resources.theme_dark
import finlife.shared.generated.resources.theme_light
import finlife.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal data class DemoSettings(
    val language: AppLanguage,
    val themeMode: ThemeMode = ThemeMode.System,
    val currencyCode: String = DEFAULT_CURRENCY_CODE,
)

@Composable
internal fun SettingsSheet(
    settings: DemoSettings,
    canReset: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(Res.string.reset_run)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.medium)) {
                    Text(stringResource(Res.string.reset_run_message))
                    SimulationNotice()
                }
            },
            dismissButton = {
                PrimaryButton(stringResource(Res.string.cancel), onClick = { confirmingReset = false })
            },
            confirmButton = { PrimaryButton(stringResource(Res.string.reset_run), onClick = onReset) },
        )
    } else {
        GameSheet(onDismiss) {
            ScreenHeading(stringResource(Res.string.settings))
            Text(stringResource(Res.string.language), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                for (language in AppLanguage.entries) {
                    FilterChip(
                        selected = language == settings.language,
                        onClick = { onLanguageChange(language) },
                        label = { Text(stringResource(language.label)) },
                    )
                }
            }
            Text(stringResource(Res.string.theme), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small)) {
                for (mode in ThemeMode.entries) {
                    FilterChip(
                        selected = mode == settings.themeMode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(stringResource(mode.label)) },
                    )
                }
            }
            Text(stringResource(Res.string.demo_currency, settings.currencyCode))
            if (canReset) {
                PrimaryButton(stringResource(Res.string.reset_run), onClick = { confirmingReset = true })
            }
        }
    }
}

private val AppLanguage.label: StringResource
    get() = when (this) {
        AppLanguage.English -> Res.string.language_english
        AppLanguage.French -> Res.string.language_french
    }

internal val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.System -> Res.string.theme_system
        ThemeMode.Light -> Res.string.theme_light
        ThemeMode.Dark -> Res.string.theme_dark
    }
