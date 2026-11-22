package com.tuna.finlife.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tuna.finlife.assistant.AssistantAnswer
import com.tuna.finlife.assistant.AssistantChat
import com.tuna.finlife.assistant.ChatEntry
import com.tuna.finlife.assistant.ModelFailure
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.assistant_close
import finlife.shared.generated.resources.assistant_intro
import finlife.shared.generated.resources.assistant_model_missing
import finlife.shared.generated.resources.assistant_model_note
import finlife.shared.generated.resources.assistant_month_not_played
import finlife.shared.generated.resources.assistant_no_decision
import finlife.shared.generated.resources.assistant_no_dotted_line
import finlife.shared.generated.resources.assistant_no_projection
import finlife.shared.generated.resources.assistant_not_running
import finlife.shared.generated.resources.assistant_nothing_played
import finlife.shared.generated.resources.assistant_out_of_scope
import finlife.shared.generated.resources.assistant_placeholder
import finlife.shared.generated.resources.assistant_projection_caption
import finlife.shared.generated.resources.assistant_range_too_long
import finlife.shared.generated.resources.assistant_rephrase
import finlife.shared.generated.resources.assistant_retry
import finlife.shared.generated.resources.assistant_send
import finlife.shared.generated.resources.assistant_server_error
import finlife.shared.generated.resources.assistant_thinking
import finlife.shared.generated.resources.assistant_timeout
import finlife.shared.generated.resources.assistant_title
import finlife.shared.generated.resources.assistant_unreliable
import finlife.shared.generated.resources.assistant_which_month
import finlife.shared.generated.resources.simulation_note
import org.jetbrains.compose.resources.stringResource

/**
 * Explain-only chat about the run. [onAsk] sends a question with a snapshot of the run; the panel itself never
 * changes the simulation. [onClose] is set when the panel is shown in a dialog; [reserveEnd] leaves room for
 * the settings gear drawn over its top-right corner.
 */
@Composable
internal fun AssistantPanel(
    chat: AssistantChat,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    reserveEnd: Boolean = false,
) {
    var draft by remember(chat) { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat) { chat.refresh() }
    LaunchedEffect(chat.entries.size, chat.busy) {
        val count = chat.entries.size + if (chat.busy) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }
    val send = {
        if (!chat.busy && draft.isNotBlank()) {
            onAsk(draft)
            draft = ""
        }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(FinLifeSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(end = if (reserveEnd) FinLifeSpacing.touchTarget + FinLifeSpacing.small else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.assistant_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onClose != null) TextButton(onClick = onClose) { Text(stringResource(Res.string.assistant_close)) }
        }
        Text(
            stringResource(Res.string.assistant_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        chat.problem?.let { problem ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(FinLifeSpacing.medium).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                ) {
                    Text(problemText(problem, chat.modelName), style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(stringResource(Res.string.assistant_retry), { chat.refresh() })
                }
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
        ) {
            itemsIndexed(chat.entries) { _, entry ->
                when (entry) {
                    is ChatEntry.Question -> QuestionBubble(entry.text)
                    is ChatEntry.Answer -> AnswerBubble(entry.answer, chat.modelName)
                }
            }
            if (chat.busy) item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.assistant_thinking), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinLifeSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    // Enter sends, Shift+Enter keeps a new line.
                    if (event.key == Key.Enter && !event.isShiftPressed) {
                        if (event.type == KeyEventType.KeyDown) send()
                        true
                    } else false
                },
                placeholder = { Text(stringResource(Res.string.assistant_placeholder)) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            PrimaryButton(stringResource(Res.string.assistant_send), send, enabled = !chat.busy && draft.isNotBlank())
        }
        Text(
            stringResource(Res.string.assistant_model_note, chat.modelName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.simulation_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(max = 280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = FinLifeSpacing.medium, vertical = FinLifeSpacing.small),
        )
    }
}

@Composable
private fun AnswerBubble(answer: AssistantAnswer, modelName: String) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(answerText(answer, modelName), style = MaterialTheme.typography.bodyMedium)
        if (answer is AssistantAnswer.Explanation && answer.projection) {
            Text(
                stringResource(Res.string.assistant_projection_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun answerText(answer: AssistantAnswer, modelName: String): String = when (answer) {
    is AssistantAnswer.Explanation -> answer.text
    is AssistantAnswer.Clarification -> answer.text ?: stringResource(Res.string.assistant_rephrase)
    is AssistantAnswer.AskWhichMonth -> stringResource(Res.string.assistant_which_month, answer.lastPlayed)
    AssistantAnswer.NothingPlayedYet -> stringResource(Res.string.assistant_nothing_played)
    is AssistantAnswer.MonthNotPlayed -> stringResource(Res.string.assistant_month_not_played, answer.lastPlayed)
    AssistantAnswer.RangeTooLong -> stringResource(Res.string.assistant_range_too_long)
    AssistantAnswer.NoDottedLine -> stringResource(Res.string.assistant_no_dotted_line)
    AssistantAnswer.NoPendingDecision -> stringResource(Res.string.assistant_no_decision)
    AssistantAnswer.ProjectionUnavailable -> stringResource(Res.string.assistant_no_projection)
    AssistantAnswer.OutOfScope -> stringResource(Res.string.assistant_out_of_scope)
    AssistantAnswer.Unreliable -> stringResource(Res.string.assistant_unreliable)
    is AssistantAnswer.Unavailable -> problemText(answer.failure, modelName)
}

@Composable
private fun problemText(problem: ModelFailure, modelName: String): String = when (problem) {
    ModelFailure.NotRunning -> stringResource(Res.string.assistant_not_running)
    ModelFailure.ModelMissing -> stringResource(Res.string.assistant_model_missing, modelName)
    ModelFailure.Timeout -> stringResource(Res.string.assistant_timeout)
    ModelFailure.ServerError, ModelFailure.Malformed -> stringResource(Res.string.assistant_server_error)
}
