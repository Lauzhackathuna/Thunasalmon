package com.tuna.finlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.i18n.LocalCurrencyCode
import com.tuna.finlife.i18n.overrideAppLocale
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.ui.AdventureScreen
import com.tuna.finlife.ui.DemoSettings
import com.tuna.finlife.ui.FinLifeTheme
import com.tuna.finlife.ui.PersonaSelectionScreen
import com.tuna.finlife.ui.SettingsSheet
import com.tuna.finlife.ui.SetupScreen
import com.tuna.finlife.ui.SummaryScreen

@Composable
@Preview
fun App() {
    // Editable on the setup screen; kept for the next run within this app session.
    var startingData by remember { mutableStateOf(StartingData.Default) }
    var setupOpen by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SimulationSession?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // What-ifs of the current month, still reversible. Cleared whenever time moves or the run changes.
    var whatIfDraft by remember { mutableStateOf<WhatIfDraft?>(null) }
    // The same run without the most recent what-ifs, shown dotted on the chart.
    var counterfactual by remember { mutableStateOf<Counterfactual?>(null) }
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
                when (val current = session) {
                    null -> if (setupOpen) {
                        SetupScreen(
                            initial = startingData,
                            onStart = { data ->
                                startingData = data
                                setupOpen = false
                                whatIfDraft = null
                                counterfactual = null
                                ended = false
                                session = AdventureRun.start(data = data)
                            },
                            onOpenSettings = { settingsOpen = true },
                        )
                    } else {
                        PersonaSelectionScreen(
                            openingCash = startingData.openingCash.toDouble(),
                            onOpenSettings = { settingsOpen = true },
                            onSelect = { setupOpen = true },
                        )
                    }
                    else -> if (ended || current.status == SessionStatus.COMPLETED) {
                        SummaryScreen(
                            session = current,
                            onContinue = if (current.status == SessionStatus.COMPLETED) null else ({ ended = false }),
                            onStartOver = {
                                session = null
                                ended = false
                                whatIfDraft = null
                                counterfactual = null
                            },
                            onOpenSettings = { settingsOpen = true },
                        )
                    } else AdventureScreen(
                        session = current,
                        onOpenSettings = { settingsOpen = true },
                        onAdvance = {
                            if (session === current) {
                                // The engine rejects a month whose amounts exceed its limit: end the run there.
                                val next = try {
                                    AdventureRun.advance(current)
                                } catch (_: IllegalArgumentException) {
                                    null
                                }
                                if (next == null) {
                                    ended = true
                                } else {
                                    counterfactual = try {
                                        Counterfactual.afterAdvance(counterfactual, whatIfDraft, next)
                                    } catch (_: IllegalArgumentException) {
                                        null
                                    }
                                    whatIfDraft = null
                                    session = next
                                }
                            }
                        },
                        onEnd = { ended = true },
                        onChoose = { eventId, optionId ->
                            if (session === current) {
                                val next = AdventureRun.choose(current, eventId, optionId)
                                counterfactual = counterfactual?.catchUp(next.currentMonth)
                                whatIfDraft = null
                                session = next
                            }
                        },
                        whatIfDraft = whatIfDraft ?: WhatIfDraft(current, offer = startingData.sharedFlatOffer),
                        counterfactual = counterfactual,
                        onWhatIfToggle = { whatIf, on ->
                            if (session === current) {
                                val draft = (whatIfDraft ?: WhatIfDraft(current, offer = startingData.sharedFlatOffer))
                                    .toggle(whatIf, on)
                                whatIfDraft = draft
                                session = draft.session
                            }
                        },
                    )
                }
                if (settingsOpen) {
                    SettingsSheet(
                        settings = settings,
                        canReset = session != null,
                        onLanguageChange = { language ->
                            if (language != settings.language) {
                                val restore = overrideAppLocale(language)
                                if (restoreLocale == null) restoreLocale = restore
                                settings = settings.copy(language = language)
                            }
                        },
                        onThemeChange = { settings = settings.copy(themeMode = it) },
                        onReset = {
                            session = null
                            setupOpen = false
                            whatIfDraft = null
                            counterfactual = null
                            ended = false
                            settingsOpen = false
                        },
                        onDismiss = { settingsOpen = false },
                    )
                }
            }
        }
    }
}
