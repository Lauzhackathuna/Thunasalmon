package com.tuna.finlife.assistant

import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.answerDefaults
import com.tuna.finlife.game.playDefaults
import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.simulation.SessionStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Opt-in check against the real local model (not part of the normal test run): set FINLIFE_LIVE_OLLAMA=1.
 * Routing and full answers are scored and written to build/assistant-live-report.txt. Small models vary,
 * so this reports a pass rate instead of failing the build.
 */
class AssistantLiveEvaluation {
    private class Case(
        val question: String,
        val context: AssistantContext,
        val language: AppLanguage,
        val expect: (Extraction) -> Boolean,
        val expectation: String,
        val fullAnswer: Boolean = false,
        val history: List<ChatTurn> = emptyList(),
    )

    private fun context(session: com.tuna.finlife.simulation.SimulationSession, cf: Counterfactual? = null) =
        AssistantContext(session, cf, emptySet(), StartingData.Default.internshipSalary, "CHF")

    @Test
    fun evaluate() {
        if (System.getenv("FINLIFE_LIVE_OLLAMA") != "1") return
        val model = OllamaAssistantModel.fromEnvironment()
        val fresh = answerDefaults(AdventureRun.start(seed = 5))
        val played = context(playDefaults(fresh, 4))
        val waiting = context((1L..500L).map { AdventureRun.start(seed = it) }.first { it.status == SessionStatus.AWAITING_DECISION })
        val draft = WhatIfDraft(fresh).toggle(WhatIf.SharedFlat, true)
        var main = AdventureRun.advance(answerDefaults(draft.session))
        var cf = Counterfactual.afterAdvance(null, draft, main)
        repeat(2) { main = AdventureRun.advance(answerDefaults(main)); cf = cf?.catchUp(main) }
        val dotted = context(main, cf)

        fun topic(t: Topic, month: Int? = null, end: Int? = null): (Extraction) -> Boolean =
            { it.topic == t && (month == null || it.month == month) && (end == null || it.endMonth == end) }
        val en = AppLanguage.English
        val fr = AppLanguage.French
        val cases = listOf(
            Case("What happened in month 2?", played, en, topic(Topic.PlayedMonths, 2), "played_months 2", fullAnswer = true),
            Case("Pourquoi mon solde a baissé au mois 3 ?", played, fr, topic(Topic.PlayedMonths, 3), "played_months 3", fullAnswer = true),
            Case("Explain months 1 to 3.", played, en, topic(Topic.PlayedMonths, 1, 3), "played_months 1-3"),
            Case("What happened last month?", played, en, topic(Topic.PlayedMonths, 4), "played_months 4"),
            Case("How much money does Maya have now?", played, en, topic(Topic.Current), "current", fullAnswer = true),
            Case("Que montre la ligne pointillée ?", dotted, fr, topic(Topic.LatestChoice), "latest_choice", fullAnswer = true),
            Case("What are my options for the rent increase?", waiting, en, topic(Topic.PendingDecision), "pending_decision", fullAnswer = true),
            Case("Quelles sont les options pour la hausse de loyer ?", waiting, fr, topic(Topic.PendingDecision), "pending_decision", fullAnswer = true),
            Case("What happens over the next 12 months if nothing changes?", played, en, topic(Topic.Projection), "projection", fullAnswer = true),
            Case("Sur les 12 prochains mois, la colocation change quoi ?", played, fr, topic(Topic.Projection), "projection", fullAnswer = true),
            Case("What does net worth mean?", played, en, topic(Topic.Concept), "concept", fullAnswer = true),
            Case("Comment fonctionnent les problèmes aléatoires ?", played, fr, topic(Topic.Concept), "concept"),
            Case("Should I buy Tesla stock with my savings?", played, en, topic(Topic.OutOfScope), "out_of_scope", fullAnswer = true),
            Case("Quel temps fera-t-il demain ?", played, fr, topic(Topic.OutOfScope), "out_of_scope"),
            Case("Should Maya accept the new rent?", waiting, en, { it.topic == Topic.PendingDecision || it.topic == Topic.OutOfScope },
                "pending_decision or out_of_scope (no advice)", fullAnswer = true),
            Case("And that one?", played, en, { it.topic == Topic.Unclear }, "unclear"),
            Case("the second one", played, en, topic(Topic.PlayedMonths, 2), "played_months 2 (follow-up)",
                history = listOf(ChatTurn(Speaker.User, "What happened in that month?"),
                    ChatTurn(Speaker.Assistant, AssistantAnswer.AskWhichMonth(4).routingText()))),
        )

        val report = StringBuilder()
        var routed = 0
        var answered = 0
        var answers = 0
        runBlocking {
            for (case in cases) {
                val routing = AssistantPrompts.routing(AssistantFacts.routingSummary(case.context), case.language)
                val started = System.nanoTime()
                val extraction = model.extract(routing, case.history + ChatTurn(Speaker.User, case.question))
                val routeSeconds = (System.nanoTime() - started) / 1e9
                val ok = extraction is ModelResult.Ok && case.expect(extraction.value)
                if (ok) routed++
                report.appendLine("[${if (ok) "PASS" else "FAIL"}] ${case.question}")
                report.appendLine("    routing: $extraction (expected ${case.expectation}) in ${"%.1f".format(routeSeconds)} s")
                if (case.fullAnswer) {
                    answers++
                    val t = System.nanoTime()
                    val answer = Assistant(model).answer(case.question, case.history, case.context, case.language)
                    val seconds = (System.nanoTime() - t) / 1e9
                    val shown = answer is AssistantAnswer.Explanation || answer == AssistantAnswer.OutOfScope
                    if (shown) answered++
                    report.appendLine("    answer (${"%.1f".format(seconds)} s): $answer")
                }
            }
        }
        report.appendLine()
        report.appendLine("Routing: $routed/${cases.size} as expected. Full answers shown (checked explanation or out-of-scope): $answered/$answers.")
        val file = File("build/assistant-live-report.txt")
        file.writeText(report.toString())
        println(report)
    }
}
