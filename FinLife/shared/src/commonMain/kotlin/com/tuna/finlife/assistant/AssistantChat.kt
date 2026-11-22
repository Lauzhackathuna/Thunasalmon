package com.tuna.finlife.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuna.finlife.i18n.AppLanguage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface ChatEntry {
    data class Question(val text: String) : ChatEntry
    data class Answer(val answer: AssistantAnswer) : ChatEntry
}

/**
 * One conversation, kept in memory only: a new run starts a new chat and nothing is written to disk.
 * Questions are answered one at a time
 */
internal class AssistantChat(private val model: AssistantModel, private val scope: CoroutineScope) {
    val modelName: String get() = model.modelName
    val entries = mutableStateListOf<ChatEntry>()
    var busy by mutableStateOf(false)
        private set
    /** Last known problem with the local model; null when it answered or the check passed. */
    var problem by mutableStateOf<ModelFailure?>(null)
        private set
    private var job: Job? = null

    fun refresh() {
        scope.launch { problem = model.check() }
    }

    fun ask(question: String, context: AssistantContext, language: AppLanguage) {
        val text = question.trim()
        if (busy || text.isEmpty()) return
        val history = entries.map { it.turn() }
        entries += ChatEntry.Question(text)
        busy = true
        job = scope.launch {
            val answer = try {
                Assistant(model).answer(text, history, context, language)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An engine figure could not be gathered for this question: say so rather than guess.
                AssistantAnswer.Unreliable
            }
            entries += ChatEntry.Answer(answer)
            problem = (answer as? AssistantAnswer.Unavailable)?.failure
            busy = false
        }
    }

    /** Stops an answer in progress (the chat is being replaced). */
    fun close() {
        job?.cancel()
    }

    private fun ChatEntry.turn(): ChatTurn = when (this) {
        is ChatEntry.Question -> ChatTurn(Speaker.User, text)
        is ChatEntry.Answer -> ChatTurn(Speaker.Assistant, answer.routingText())
    }
}

/** What the routing model sees of an earlier answer (English, never shown to the player). */
internal fun AssistantAnswer.routingText(): String = when (this) {
    is AssistantAnswer.Explanation -> text
    is AssistantAnswer.Clarification -> text ?: "Could you rephrase the question?"
    is AssistantAnswer.AskWhichMonth -> "Which month do you mean? Months 1 to $lastPlayed have been played."
    AssistantAnswer.NothingPlayedYet -> "No month has been played yet."
    is AssistantAnswer.MonthNotPlayed -> "That month has not been played. Months 1 to $lastPlayed have been played."
    AssistantAnswer.RangeTooLong -> "Ask about at most $MAX_MONTHS_PER_QUESTION months at a time."
    AssistantAnswer.NoDottedLine -> "There is no dotted line on the chart right now."
    AssistantAnswer.NoPendingDecision -> "No decision is waiting right now."
    AssistantAnswer.ProjectionUnavailable -> "The engine cannot project from here."
    AssistantAnswer.ProjectionTooFar -> "The engine simulates at most 12 months ahead."
    AssistantAnswer.OutOfScope -> "I can only explain this simulation."
    AssistantAnswer.Unreliable -> "I could not explain this reliably."
    is AssistantAnswer.Unavailable -> "The local model is unavailable."
}
