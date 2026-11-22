package com.tuna.finlife.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.tuna.finlife.game.PlayerName
import finlife.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The first screen of a new journey, replacing the fixed Maya persona. */
@Composable
internal fun NameEntryScreen(
    onContinue: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val normalized = PlayerName.normalize(name)

    GameScreen(
        onOpenSettings = onOpenSettings,
        actions = {
            PrimaryButton(
                stringResource(Res.string.name_continue),
                { normalized?.let(onContinue) },
                enabled = normalized != null,
            )
        },
    ) {
        ScreenHeading(stringResource(Res.string.first_year_abroad))
        Text(stringResource(Res.string.name_intro), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.name_label)) },
            placeholder = { Text(stringResource(Res.string.name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = name.isNotBlank() && normalized == null,
            supportingText = if (name.isNotBlank() && normalized == null) {
                { Text(stringResource(Res.string.name_error, PlayerName.MAX_LENGTH)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        )
        Text(
            stringResource(Res.string.name_context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
