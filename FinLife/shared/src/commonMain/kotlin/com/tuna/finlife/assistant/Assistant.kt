package com.tuna.finlife.assistant

import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.simulation.SimulationSessions

/** What the panel shows for one question. Everything except [Explanation] and [Clarification] is app wording. */
internal sealed interface AssistantAnswer {
    /** Model wording of engine facts, checked by [ReplyGuard]. [projection]: show the projection caption. */
    data class Explanation(val text: String, val projection: Boolean) : AssistantAnswer
    /** The model's clarification question, or null for the app's generic one. */
    data class Clarification(val text: String?) : AssistantAnswer
    data class AskWhichMonth(val lastPlayed: Int) : AssistantAnswer
    data object NothingPlayedYet : AssistantAnswer
    data class MonthNotPlayed(val lastPlayed: Int) : AssistantAnswer
    data object RangeTooLong : AssistantAnswer
    data object NoDottedLine : AssistantAnswer
    data object NoPendingDecision : AssistantAnswer
    data object ProjectionUnavailable : AssistantAnswer
    data object OutOfScope : AssistantAnswer
    /** The model's wording failed the checks twice, or could not be read. */
    data object Unreliable : AssistantAnswer
    data class Unavailable(val failure: ModelFailure, val modelName: String) : AssistantAnswer
}

/** Either an app answer without the model, or the facts the model must explain. */
internal sealed interface Plan {
    data class Answer(val answer: AssistantAnswer) : Plan
    data class Explain(val facts: FactSheet, val projection: Boolean = false) : Plan
}

internal const val MAX_MONTHS_PER_QUESTION = 12

/** Explain-only assistant: routes a question, gathers the engine's figures and has the model word them. */
internal class Assistant(private val model: AssistantModel) {
    suspend fun answer(
        question: String,
        history: List<ChatTurn>,
        context: AssistantContext,
        language: AppLanguage,
    ): AssistantAnswer {
        model.check()?.let { return AssistantAnswer.Unavailable(it, model.modelName) }
        val routing = AssistantPrompts.routing(AssistantFacts.routingSummary(context), language)
        val extraction = when (val result = model.extract(routing, history.takeLast(6) + ChatTurn(Speaker.User, question))) {
            is ModelResult.Ok -> result.value
            is ModelResult.Failed -> return failed(result.failure)
        }
        val plan = when (val p = plan(extraction, context)) {
            is Plan.Answer -> return p.answer
            is Plan.Explain -> p
        }
        val system = AssistantPrompts.explanation(language)
        var correction: String? = null
        repeat(2) {
            val reply = when (val result = model.explain(system, AssistantPrompts.explanationMessage(question, plan.facts, correction))) {
                is ModelResult.Ok -> result.value.trim()
                is ModelResult.Failed -> return failed(result.failure)
            }
            val numbers = ReplyGuard.unsupportedNumbers(reply, plan.facts.text)
            val advice = ReplyGuard.advicePhrases(reply)
            if (reply.isNotEmpty() && numbers.isEmpty() && advice.isEmpty()) {
                return AssistantAnswer.Explanation(reply, plan.projection)
            }
            correction = AssistantPrompts.correction(numbers, advice)
        }
        return AssistantAnswer.Unreliable
    }

    private fun failed(failure: ModelFailure): AssistantAnswer =
        if (failure == ModelFailure.Malformed) AssistantAnswer.Unreliable else AssistantAnswer.Unavailable(failure, model.modelName)

    companion object {
        /** Validates the model's [extraction] against the run and picks the facts. No model call. */
        fun plan(extraction: Extraction, context: AssistantContext): Plan {
            val played = context.monthsPlayed
            return when (extraction.topic) {
                Topic.PlayedMonths -> {
                    if (played == 0) return Plan.Answer(AssistantAnswer.NothingPlayedYet)
                    val first = extraction.month ?: return Plan.Answer(AssistantAnswer.AskWhichMonth(played))
                    val last = extraction.endMonth ?: first
                    val from = minOf(first, last)
                    val to = maxOf(first, last)
                    when {
                        from < 1 || to > played -> Plan.Answer(AssistantAnswer.MonthNotPlayed(played))
                        to - from + 1 > MAX_MONTHS_PER_QUESTION -> Plan.Answer(AssistantAnswer.RangeTooLong)
                        else -> Plan.Explain(AssistantFacts.playedMonths(context, from, to))
                    }
                }
                Topic.Current -> Plan.Explain(AssistantFacts.current(context))
                Topic.LatestChoice -> context.counterfactual
                    ?.let { Plan.Explain(AssistantFacts.latestChoice(context, it)) }
                    ?: Plan.Answer(AssistantAnswer.NoDottedLine)
                Topic.PendingDecision ->
                    if (SimulationSessions.pendingDecisions(context.session).isEmpty()) Plan.Answer(AssistantAnswer.NoPendingDecision)
                    else Plan.Explain(AssistantFacts.pendingDecisions(context))
                Topic.Projection -> AssistantFacts.projection(context)
                    ?.let { Plan.Explain(it, projection = true) }
                    ?: Plan.Answer(AssistantAnswer.ProjectionUnavailable)
                Topic.Concept -> Plan.Explain(AssistantFacts.concepts(context))
                Topic.OutOfScope -> Plan.Answer(AssistantAnswer.OutOfScope)
                Topic.Unclear -> Plan.Answer(AssistantAnswer.Clarification(clarification(extraction.clarification, played)))
            }
        }

        /** The model's question if it is usable: short, and quoting no number other than played months. */
        private fun clarification(text: String?, played: Int): String? {
            val question = text?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 } ?: return null
            val months = (0..played).map { it * 100L }.toSet()
            return question.takeIf { ReplyGuard.numbers(it).all { (_, value) -> value in months } }
        }
    }
}
