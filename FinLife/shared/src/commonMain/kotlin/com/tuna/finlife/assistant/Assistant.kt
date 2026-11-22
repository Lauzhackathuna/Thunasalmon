package com.tuna.finlife.assistant

import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.simulation.SimulationSessions

/** What the panel shows for one question. Everything except [Explanation] and [Clarification] is app wording. */
internal sealed interface AssistantAnswer {
    /** Model wording of engine facts, checked by [ReplyGuard]. [projectionMonths]: show the projection caption. */
    data class Explanation(val text: String, val projectionMonths: Int? = null) : AssistantAnswer
    /** The model's clarification question, or null for the app's generic one. */
    data class Clarification(val text: String?) : AssistantAnswer
    data class AskWhichMonth(val lastPlayed: Int) : AssistantAnswer
    data object NothingPlayedYet : AssistantAnswer
    data class MonthNotPlayed(val lastPlayed: Int) : AssistantAnswer
    data object RangeTooLong : AssistantAnswer
    data object NoDottedLine : AssistantAnswer
    data object NoPendingDecision : AssistantAnswer
    data object ProjectionUnavailable : AssistantAnswer
    /** The question asks further ahead than the engine simulates. */
    data object ProjectionTooFar : AssistantAnswer
    data object OutOfScope : AssistantAnswer
    /** The model's wording failed the checks twice, or could not be read. */
    data object Unreliable : AssistantAnswer
    data class Unavailable(val failure: ModelFailure, val modelName: String) : AssistantAnswer
}

/** Either an app answer without the model, or the facts the model must explain. */
internal sealed interface Plan {
    data class Answer(val answer: AssistantAnswer) : Plan
    data class Explain(val facts: FactSheet, val projectionMonths: Int? = null) : Plan
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
            .replace("Maya", context.playerName)
        val extraction = when (val result = model.extract(routing, history.takeLast(6) + ChatTurn(Speaker.User, question))) {
            is ModelResult.Ok -> result.value
            is ModelResult.Failed -> return failed(result.failure)
        }
        val plan = when (val p = plan(extraction, context, question)) {
            is Plan.Answer -> return p.answer
            is Plan.Explain -> p
        }
        // Keep the demo's causal salary explanation deterministic: small models confuse this job with an internship.
        if (extraction.topic == Topic.PlayedMonths && (extraction.endMonth == null || extraction.endMonth == extraction.month) &&
            Regex("salaire|salary", RegexOption.IGNORE_CASE).containsMatchIn(question)) {
            val record = extraction.month?.let { context.session.history.getOrNull(it - 1) }
            val accepted = record?.events?.any { outcome ->
                outcome.optionId == "epfl" &&
                    outcome.application == com.tuna.finlife.simulation.EventApplication.APPLIED &&
                    context.session.calendar.any { it.id == outcome.eventId && it is com.tuna.finlife.simulation.LifeEvent.EpflJobOffer }
            } == true
            if (accepted && record != null) {
                val salary = m(record.flow.professionalIncome, context.currency)
                val tax = m(record.flow.taxes, context.currency)
                return AssistantAnswer.Explanation(if (language == AppLanguage.French)
                    "Explication du moteur : au mois ${record.month + 1}, le choix du poste fictif à l’EPFL a fixé le salaire brut à $salary. Les impôts et cotisations simulés sur ce salaire sont de $tax. Ce poste n’a pas de risque de faillite dans le jeu. Ces montants sont des flux mensuels, pas le solde bancaire ni le patrimoine net."
                else "Engine explanation: in month ${record.month + 1}, accepting the fictional EPFL job set gross salary to $salary. Simulated taxes and contributions on that salary are $tax. This job has no company-failure risk in the game. These amounts are monthly flows, not bank balance or net worth.")
            }
        }
        val system = AssistantPrompts.explanation(language)
            .replace("Maya", context.playerName)
        val facts = FactSheet(listOf("Player first name: ${context.playerName}.") +
            plan.facts.lines.map { it.replace("Maya", context.playerName) })
        var correction: String? = null
        repeat(2) {
            val reply = when (val result = model.explain(system, AssistantPrompts.explanationMessage(question, facts, correction))) {
                is ModelResult.Ok -> withoutEchoedQuestion(result.value, question)
                is ModelResult.Failed -> return failed(result.failure)
            }
            val numbers = ReplyGuard.unsupportedNumbers(reply, facts.text)
            val advice = ReplyGuard.advicePhrases(reply)
            val bindings = ReplyGuard.unsupportedMetricAmounts(reply, facts.text)
            if (reply.isNotEmpty() && numbers.isEmpty() && advice.isEmpty() && bindings.isEmpty()) {
                return AssistantAnswer.Explanation(reply, plan.projectionMonths)
            }
            correction = AssistantPrompts.correction(numbers, advice) +
                if (bindings.isEmpty()) "" else " Keep each signed amount attached to its FACTS metric; unsupported bindings: ${bindings.joinToString()}."
        }
        return AssistantAnswer.Unreliable
    }

    /** Small models sometimes start by repeating the question: drop that first line. */
    private fun withoutEchoedQuestion(reply: String, question: String): String {
        val text = reply.trim()
        return if (text.startsWith(question.trim())) text.removePrefix(question.trim()).trim() else text
    }

    private fun failed(failure: ModelFailure): AssistantAnswer =
        if (failure == ModelFailure.Malformed) AssistantAnswer.Unreliable else AssistantAnswer.Unavailable(failure, model.modelName)

    companion object {
        /** Validates the model's [extraction] against the run and picks the facts. No model call. */
        fun plan(extraction: Extraction, context: AssistantContext, question: String = ""): Plan {
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
                Topic.Projection -> {
                    val months = extraction.monthsAhead?.takeIf { it >= 1 } ?: AssistantFacts.PROJECTION_MONTHS
                    if (months > AssistantFacts.PROJECTION_MONTHS) return Plan.Answer(AssistantAnswer.ProjectionTooFar)
                    AssistantFacts.projection(context, months)
                        ?.let { Plan.Explain(it, projectionMonths = months) }
                        ?: Plan.Answer(AssistantAnswer.ProjectionUnavailable)
                }
                Topic.Concept -> Plan.Explain(AssistantFacts.concepts(context))
                Topic.OutOfScope -> Plan.Answer(AssistantAnswer.OutOfScope)
                Topic.Unclear -> Plan.Answer(AssistantAnswer.Clarification(clarification(extraction.clarification, played, question)))
            }
        }

        /** The model's question if it is usable: short, and quoting no number other than played months. */
        private fun clarification(text: String?, played: Int, asked: String): String? {
            val question = text?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 } ?: return null
            // A small model may copy the question (or a prompt example) instead of asking something.
            if (asked.isNotBlank() && question.contains(asked.trim(), ignoreCase = true)) return null
            if (!question.trimEnd().endsWith("?")) return null
            val months = (0..played).map { it * 100L }.toSet()
            return question.takeIf { ReplyGuard.numbers(it).all { (_, value) -> value in months } }
        }
    }
}
