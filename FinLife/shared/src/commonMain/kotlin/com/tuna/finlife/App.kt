package com.tuna.finlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.GameAction
import com.tuna.finlife.game.GameState
import com.tuna.finlife.game.compareScenario
import com.tuna.finlife.game.reduceGame
import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.i18n.overrideAppLocale
import com.tuna.finlife.model.Persona
import com.tuna.finlife.twin.FakeTwinEngine
import com.tuna.finlife.ui.DemoSettings
import com.tuna.finlife.ui.FinLifeTheme
import com.tuna.finlife.ui.PersonaSelectionScreen
import com.tuna.finlife.ui.RoomScreen
import com.tuna.finlife.ui.SettingsSheet

@Composable
@Preview
fun App() {
    val twin = remember { FakeTwinEngine() }
    var state by remember { mutableStateOf<GameState>(GameState.ChoosingPersona) }
    var dismissedMonth by remember { mutableIntStateOf(-1) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settings by remember {
        val language = if (Locale.current.language == "fr") AppLanguage.French else AppLanguage.English
        mutableStateOf(DemoSettings(language))
    }
    var restoreLocale by remember { mutableStateOf<(() -> Unit)?>(null) }

    DisposableEffect(Unit) {
        onDispose { restoreLocale?.invoke() }
    }

    FinLifeTheme(settings.themeMode) {
        CompositionLocalProvider(LocalCurrencyCode provides settings.currencyCode) {
            key(settings.language) {
                when (val current = state) {
                    GameState.ChoosingPersona -> {
                        val openingSnapshots = remember(twin) {
                            mapOf(Persona.MastersStudent to twin.initialSnapshot(Persona.MastersStudent))
                        }
                        PersonaSelectionScreen(
                            openingSnapshots = openingSnapshots,
                            onOpenSettings = { settingsOpen = true },
                            onSelect = { state = reduceGame(state, GameAction.SelectPersona(it), twin) },
                        )
                    }
                    is GameState.Playing -> RoomScreen(
                        state = current,
                        dismissedMonth = dismissedMonth,
                        onSummaryDismissed = { dismissedMonth = it },
                        onOpenSettings = { settingsOpen = true },
                        compare = { compareScenario(current, it, twin) },
                        onAction = { action ->
                            if (action != GameAction.AdvanceMonth || state == current) {
                                state = reduceGame(state, action, twin)
                            }
                        },
                    )
                }
                if (settingsOpen) {
                    SettingsSheet(
                        settings = settings,
                        canReset = state is GameState.Playing,
                        onLanguageChange = { language ->
                            if (language != settings.language) {
                                val restore = overrideAppLocale(language)
                                if (restoreLocale == null) restoreLocale = restore
                                settings = settings.copy(language = language)
                            }
                        },
                        onThemeChange = { settings = settings.copy(themeMode = it) },
                        onReset = {
                            state = GameState.ChoosingPersona
                            dismissedMonth = -1
                            settingsOpen = false
                        },
                        onDismiss = { settingsOpen = false },
                    )
                }
            }
        }
    }
}
