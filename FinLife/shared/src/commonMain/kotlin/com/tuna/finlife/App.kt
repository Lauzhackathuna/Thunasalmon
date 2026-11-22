package com.tuna.finlife

import finlife.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tuna.finlife.storage.rememberRunStorage
import com.tuna.finlife.game.JourneySave
import com.tuna.finlife.ui.ThemeMode
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
    var calculationError by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // What-ifs of the current month, still reversible. Cleared whenever time moves or the run changes.
    var whatIfDraft by remember { mutableStateOf<WhatIfDraft?>(null) }
    // The same run without the most recent what-ifs, shown dotted on the chart.
    var counterfactual by remember { mutableStateOf<Counterfactual?>(null) }
    var settings by remember {
        val language = if (Locale.current.language == "fr") AppLanguage.French else AppLanguage.English
        mutableStateOf(DemoSettings(language))
    }
    val storage = rememberRunStorage()
    val storageLock = remember { Mutex() }
    var loaded by remember { mutableStateOf(false) }
    var savingPaused by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    var restoreLocale by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(storage) {
        try {
            val restored = withContext(Dispatchers.Default) { storage.read()?.let(JourneySave::decode) }
            if (restored != null) {
                startingData = restored.data
                session = restored.session
                whatIfDraft = restored.draft
                ended = restored.ended
                val language = AppLanguage.valueOf(restored.language)
                restoreLocale = overrideAppLocale(language)
                settings = settings.copy(language = language, themeMode = ThemeMode.valueOf(restored.theme))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            println("FinLife restore failed: ${failure.message}")
            savingPaused = true
            storageError = true
        } finally { loaded = true }
    }
    LaunchedEffect(loaded, session, startingData, whatIfDraft, ended, settings, savingPaused) {
        if (loaded && !savingPaused) {
            val current = session
            val data = startingData
            val draft = whatIfDraft
            val isEnded = ended
            val preferences = settings
            try {
                withContext(Dispatchers.Default) {
                    storageLock.withLock {
                        storage.write(current?.let { JourneySave.encode(data, it, draft, isEnded, preferences.language.name, preferences.themeMode.name) })
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                println("FinLife save failed: ${failure.message}")
                savingPaused = true
                storageError = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { restoreLocale?.invoke() }
    }

    FinLifeTheme(settings.themeMode) {
        CompositionLocalProvider(LocalCurrencyCode provides settings.currencyCode) {
            key(settings.language) {
                if (!loaded) Text(stringResource(Res.string.loading_journey)) else when (val current = session) {
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
                                // A calculation error is recoverable and must never silently end the run.
                                val next = try {
                                    AdventureRun.advance(current)
                                } catch (failure: Exception) {
                                    println("FinLife advance failed: ${failure.message}")
                                    null
                                }
                                if (next == null) {
                                    calculationError = true
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
                        onChoose = choose@{ eventId, optionId ->
                            if (session === current) {
                                val next = try { AdventureRun.choose(current, eventId, optionId) } catch (failure: Exception) {
                                    println("FinLife decision failed: ${failure.message}")
                                    calculationError = true
                                    return@choose
                                }
                                // This answer is now the latest choice: the dotted line shows the other answer.
                                counterfactual = try {
                                    Counterfactual.afterChoice(current, eventId, optionId, next) ?: counterfactual?.catchUp(next)
                                } catch (_: IllegalArgumentException) {
                                    null
                                }
                                whatIfDraft = null
                                session = next
                            }
                        },
                        whatIfDraft = whatIfDraft ?: WhatIfDraft(current, internshipSalary = startingData.internshipSalary),
                        counterfactual = counterfactual,
                        startingData = startingData,
                        onUpdateAmounts = update@{ data ->
                            if (data == startingData) return@update true
                            val updated = try {
                                val draft = whatIfDraft
                                val base = AdventureRun.recalculate(draft?.base ?: current, data)
                                base?.let {
                                    val revisedDraft = draft?.copy(base = it, internshipSalary = data.internshipSalary)
                                    (revisedDraft?.session ?: it) to revisedDraft
                                }
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                            if (updated == null) false else {
                                startingData = data
                                session = updated.first
                                whatIfDraft = updated.second
                                counterfactual = null
                                true
                            }
                        },
                        onWhatIfToggle = { whatIf, on ->
                            if (session === current) {
                                val draft = (whatIfDraft ?: WhatIfDraft(current, internshipSalary = startingData.internshipSalary))
                                    .toggle(whatIf, on)
                                try {
                                    val proposed = draft.session
                                    whatIfDraft = draft
                                    session = proposed
                                } catch (failure: Exception) {
                                    println("FinLife preview failed: ${failure.message}")
                                    calculationError = true
                                }
                            }
                        },
                    )
                }
                if (storageError) AlertDialog(
                    onDismissRequest = { storageError = false },
                    text = { Text(stringResource(Res.string.save_error)) },
                    confirmButton = { TextButton(onClick = { storageError = false }) { Text(stringResource(Res.string.acknowledge)) } },
                )
                if (calculationError) AlertDialog(
                    onDismissRequest = { calculationError = false },
                    text = { Text(stringResource(Res.string.operation_error)) },
                    confirmButton = { TextButton(onClick = { calculationError = false }) { Text(stringResource(Res.string.acknowledge)) } },
                )
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
                            savingPaused = false
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
