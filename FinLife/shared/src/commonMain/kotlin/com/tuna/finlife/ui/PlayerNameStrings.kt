package com.tuna.finlife.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One first name for the entire app. Screens never rely on a built-in demo persona. */
internal val LocalPlayerName = compositionLocalOf { "" }

/** Personalize existing bilingual story strings while retaining their numeric formatting. */
@Composable
internal fun playerStringResource(resource: StringResource, vararg formatArgs: Any): String =
    stringResource(resource, *formatArgs).replace("Maya", LocalPlayerName.current)
