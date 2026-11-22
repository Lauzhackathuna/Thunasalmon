package com.tuna.finlife.assistant

import com.tuna.finlife.game.AdventureRun
import com.tuna.finlife.game.Counterfactual
import com.tuna.finlife.game.StartingData
import com.tuna.finlife.game.WhatIf
import com.tuna.finlife.game.WhatIfDraft
import com.tuna.finlife.game.answerDefaults
import com.tuna.finlife.game.playDefaults
import com.tuna.finlife.i18n.AppLanguage
import com.tuna.finlife.simulation.FinancialProjections
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyComparisons
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs a suspend block that never actually suspends (the fake model answers at once). */
internal fun <T> runNow(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return assertNotNull(result).getOrThrow()
}

/** Scripted model: answers from queues and records what it was sent. */
internal class FakeModel(
    private val status: ModelFailure? = null,
    extractions: List<ModelResult<Extraction>> = emptyList(),
    replies: List<ModelResult<String>> = emptyList(),
) : AssistantModel {
    override val modelName = "fake:1b"
    private val extractions = ArrayDeque(extractions)
    private val replies = ArrayDeque(replies)
    val extractCalls = mutableListOf<Pair<String, List<ChatTurn>>>()
    val explainCalls = mutableListOf<Pair<String, String>>()

    override suspend fun check() = status
    override suspend fun extract(system: String, turns: List<ChatTurn>): ModelResult<Extraction> {
        extractCalls += system to turns
        return extractions.removeFirst()
    }
    override suspend fun explain(system: String, user: String): ModelResult<String> {
        explainCalls += system to user
        return replies.removeFirst()
    }
}

class AssistantTest {
    private fun context(session: SimulationSession, counterfactual: Counterfactual? = null) =
        AssistantContext(session, counterfactual, emptySet(), StartingData.Default.internshipSalary, "CHF")

    private val fresh = answerDefaults(AdventureRun.start(seed = 5))
    private val played = playDefaults(fresh, 4)

    private fun runStartingOnRentRise(): SimulationSession =
        (1L..500L).map { AdventureRun.start(seed = it) }.first { it.status == SessionStatus.AWAITING_DECISION }

    private fun plan(extraction: Extraction, session: SimulationSession = played, cf: Counterfactual? = null) =
        Assistant.plan(extraction, context(session, cf))

    // --- Validation of the model's structured extraction ---

    @Test
    fun aPlayedMonthNeedsAMonthThatWasPlayed() {
        assertEquals(Plan.Answer(AssistantAnswer.NothingPlayedYet), plan(Extraction(Topic.PlayedMonths, 1), fresh))
        assertEquals(Plan.Answer(AssistantAnswer.AskWhichMonth(4)), plan(Extraction(Topic.PlayedMonths)))
        assertEquals(Plan.Answer(AssistantAnswer.MonthNotPlayed(4)), plan(Extraction(Topic.PlayedMonths, 5)))
        assertEquals(Plan.Answer(AssistantAnswer.MonthNotPlayed(4)), plan(Extraction(Topic.PlayedMonths, 0)))
        val month = assertIs<Plan.Explain>(plan(Extraction(Topic.PlayedMonths, 2)))
        assertTrue(month.facts.lines.contains("Month 2:"))
        assertTrue(month.facts.lines.none { it == "Month 1:" || it == "Month 3:" })
    }

    @Test
    fun aRangeIsOrderedAndLimitedToTwelveMonths() {
        val range = assertIs<Plan.Explain>(plan(Extraction(Topic.PlayedMonths, 4, 2)))
        assertEquals(listOf("Month 2:", "Month 3:", "Month 4:"), range.facts.lines.filter { it.startsWith("Month ") && it.endsWith(":") })
        val long = playDefaults(fresh, 14)
        assertEquals(Plan.Answer(AssistantAnswer.RangeTooLong), plan(Extraction(Topic.PlayedMonths, 1, 13), long))
        assertIs<Plan.Explain>(plan(Extraction(Topic.PlayedMonths, 1, 12), long))
    }

    @Test
    fun topicsWithoutTheirDataGetAnAppAnswer() {
        assertEquals(Plan.Answer(AssistantAnswer.NoDottedLine), plan(Extraction(Topic.LatestChoice)))
        assertEquals(Plan.Answer(AssistantAnswer.NoPendingDecision), plan(Extraction(Topic.PendingDecision)))
        assertEquals(Plan.Answer(AssistantAnswer.OutOfScope), plan(Extraction(Topic.OutOfScope, 2)))
    }

    @Test
    fun clarificationsQuotingOtherNumbersAreReplaced() {
        assertEquals(Plan.Answer(AssistantAnswer.Clarification("Which month, 2 or 3?")),
            plan(Extraction(Topic.Unclear, clarification = "Which month, 2 or 3?")))
        assertEquals(Plan.Answer(AssistantAnswer.Clarification(null)),
            plan(Extraction(Topic.Unclear, clarification = "Do you mean the 1200 CHF rent?")))
        assertEquals(Plan.Answer(AssistantAnswer.Clarification(null)), plan(Extraction(Topic.Unclear, clarification = " ")))
        // A copied question, or a statement, is not a clarification question.
        assertEquals(Plan.Answer(AssistantAnswer.Clarification(null)), Assistant.plan(
            Extraction(Topic.Unclear, clarification = "And that one? with nothing earlier to refer to"), context(played), "And that one?"))
        assertEquals(Plan.Answer(AssistantAnswer.Clarification(null)), plan(Extraction(Topic.Unclear, clarification = "I am not sure.")))
    }

    // --- Facts are the engine's figures ---

    @Test
    fun monthFactsQuoteTheEngineRecord() {
        val record = played.history[1]
        val facts = AssistantFacts.playedMonths(context(played), 2, 2).text
        assertTrue("Income: ${m(record.flow.income, "CHF")}" in facts, facts)
        assertTrue("Expenses: ${m(record.flow.expenses, "CHF")}" in facts, facts)
        assertTrue("rent ${m(record.flow.rent, "CHF")}" in facts, facts)
        assertTrue("${m(record.after.netWorth, "CHF")} at the end" in facts, facts)
        assertTrue("${m(record.after.theoreticalCash, "CHF")} at the end" in facts, facts)
    }

    @Test
    fun projectionFactsAreTheEngineProjection() {
        val explain = assertIs<Plan.Explain>(plan(Extraction(Topic.Projection)))
        assertEquals(12, explain.projectionMonths)
        val reference = FinancialProjections.nothingChanges(played)
        val end = reference.finalState
        assertTrue("End of month ${played.currentMonth + 12}: net worth ${m(end.netWorth, "CHF")}" in explain.facts.text, explain.facts.text)
        ProjectionAssumptions.english(played.configuration.annualInflation).forEach { assertTrue(it in explain.facts.text) }
        assertEquals(ProjectionAssumptions.COUNT, reference.assumptions.size, "The engine's assumption list changed: update ProjectionAssumptions")
    }

    @Test
    fun aShorterHorizonGivesOnlyThoseMonthsOfTheEngineProjection() {
        val explain = assertIs<Plan.Explain>(plan(Extraction(Topic.Projection, monthsAhead = 3)))
        assertEquals(3, explain.projectionMonths)
        val third = FinancialProjections.nothingChanges(played).states[3]
        assertTrue("End of month ${played.currentMonth + 3}: net worth ${m(third.netWorth, "CHF")}" in explain.facts.text, explain.facts.text)
        assertTrue("months ${played.currentMonth + 1} to ${played.currentMonth + 3}" in explain.facts.text)
        assertTrue("End of month ${played.currentMonth + 4}:" !in explain.facts.text)
        assertEquals(Plan.Answer(AssistantAnswer.ProjectionTooFar), plan(Extraction(Topic.Projection, monthsAhead = 24)))
        assertEquals(12, assertIs<Plan.Explain>(plan(Extraction(Topic.Projection, monthsAhead = 0))).projectionMonths)
    }

    @Test
    fun housingIsDescribedAsRented() {
        val text = AssistantFacts.current(context(played)).text + AssistantFacts.concepts(context(played)).text
        assertTrue("does not own it" in text, text)
        assertTrue(listOf("own home", "Own home", "keep the home", "kept the home").none { it in text }, text)
    }

    @Test
    fun studiedScenariosGetEnglishLabels() {
        val scenarios = assertNotNull(AssistantFacts.studiedScenarios(context(played)))
        assertEquals(
            setOf("Current rented flat, alone, without the internship", "Current rented flat, alone, with the 3-month internship",
                "Shared flat, without the internship", "Shared flat, with the 3-month internship"),
            scenarios.map { it.first }.toSet(),
        )
        // The labels follow the engine's own: the move scenarios pay the shared-flat rent.
        val shared = scenarios.first { it.first == "Shared flat, without the internship" }.second
        assertEquals(Money.chf(StartingData.Default.sharedFlatRent).cents.toDouble(),
            shared.records.first().flow.rent.cents.toDouble(), absoluteTolerance = 2000.0)
    }

    @Test
    fun latestChoiceFactsCompareBothPaths() {
        var main = fresh
        val draft = WhatIfDraft(main, internshipSalary = StartingData.Default.internshipSalary).toggle(WhatIf.SharedFlat, true)
        main = AdventureRun.advance(answerDefaults(draft.session))
        var cf = Counterfactual.afterAdvance(null, draft, main)
        repeat(2) { main = AdventureRun.advance(answerDefaults(main)); cf = cf?.catchUp(main) }
        val counterfactual = assertNotNull(cf)
        val facts = assertIs<Plan.Explain>(plan(Extraction(Topic.LatestChoice), main, counterfactual)).facts.text
        val difference = MonthlyComparisons.compare(main, counterfactual.alternative, main.currentMonth).nominalNetWorth
        val words = if (difference.cents > 0) "more" else "less"
        assertTrue("Maya's path has ${m(Money(kotlin.math.abs(difference.cents)), "CHF")} $words net worth than the dotted line" in facts, facts)
        assertTrue("on the dotted line Maya stays in the flat she rents alone" in facts, facts)
        assertTrue("switched on the move to the shared flat" in facts, facts)
    }

    @Test
    fun pendingDecisionFactsPreviewEachOption() {
        val waiting = runStartingOnRentRise()
        val facts = assertIs<Plan.Explain>(plan(Extraction(Topic.PendingDecision), waiting)).facts.text
        val event = waiting.calendar.filterIsInstance<LifeEvent.RentRise>().first()
        for (option in listOf("stay", "move")) {
            val preview = assertNotNull(AdventureRun.preview(waiting, event.id, option))
            assertTrue("net worth ${m(preview.after.netWorth, "CHF")}" in facts, facts)
        }
    }

    @Test
    fun conceptsUseTheAppRules() {
        val facts = AssistantFacts.concepts(context(played)).text
        assertTrue("rent rise with probability 1 in 12" in facts && "1 in 12" in facts && "400 CHF" in facts, facts)
        assertTrue("not financial advice" in facts)
    }

    // --- Orchestration with the model ---

    private fun ask(model: FakeModel, history: List<ChatTurn> = emptyList(), language: AppLanguage = AppLanguage.English) =
        runNow { Assistant(model).answer("What happened in month 2?", history, context(played), language) }

    private val month2 = ModelResult.Ok(Extraction(Topic.PlayedMonths, 2))

    @Test
    fun ollamaUnavailableIsReportedWithoutCallingTheModel() {
        val model = FakeModel(status = ModelFailure.NotRunning)
        assertEquals(AssistantAnswer.Unavailable(ModelFailure.NotRunning, "fake:1b"), ask(model))
        assertTrue(model.extractCalls.isEmpty())
        assertEquals(AssistantAnswer.Unavailable(ModelFailure.ModelMissing, "fake:1b"), ask(FakeModel(status = ModelFailure.ModelMissing)))
        assertEquals(AssistantAnswer.Unavailable(ModelFailure.Timeout, "fake:1b"),
            ask(FakeModel(extractions = listOf(ModelResult.Failed(ModelFailure.Timeout)))))
        assertEquals(AssistantAnswer.Unreliable, ask(FakeModel(extractions = listOf(ModelResult.Failed(ModelFailure.Malformed)))))
    }

    @Test
    fun aCheckedReplyIsShown() {
        val income = m(played.history[1].flow.income, "CHF")
        val model = FakeModel(extractions = listOf(month2), replies = listOf(ModelResult.Ok("In month 2 Maya received $income.")))
        assertEquals(AssistantAnswer.Explanation("In month 2 Maya received $income."), ask(model))
        val (system, user) = model.explainCalls.single()
        assertTrue("Never calculate new numbers" in system && "English" in system)
        assertTrue(user.startsWith("QUESTION:\nWhat happened in month 2?") && "FACTS:\n" in user)
    }

    @Test
    fun aReplyWithAnInventedNumberIsAskedAgainThenReplaced() {
        val retried = FakeModel(extractions = listOf(month2),
            replies = listOf(ModelResult.Ok("Maya saved 123456 CHF."), ModelResult.Ok("Maya had no unpaid bills in month 2.")))
        assertIs<AssistantAnswer.Explanation>(ask(retried))
        assertTrue("NOTE: " in retried.explainCalls[1].second && "123456" in retried.explainCalls[1].second)

        val stubborn = FakeModel(extractions = listOf(month2),
            replies = listOf(ModelResult.Ok("Maya saved 123456 CHF."), ModelResult.Ok("Maya saved 654321 CHF.")))
        assertEquals(AssistantAnswer.Unreliable, ask(stubborn))
    }

    @Test
    fun adviceIsAskedAgain() {
        val model = FakeModel(extractions = listOf(month2),
            replies = listOf(ModelResult.Ok("You should move."), ModelResult.Ok("You should really move.")))
        assertEquals(AssistantAnswer.Unreliable, ask(model))
        assertTrue("gave advice" in model.explainCalls[1].second)
    }

    @Test
    fun routingSeesRecentTurnsAndTheReplyLanguage() {
        val history = (1..8).map { ChatTurn(if (it % 2 == 1) Speaker.User else Speaker.Assistant, "turn $it") }
        val model = FakeModel(extractions = listOf(ModelResult.Ok(Extraction(Topic.OutOfScope))))
        assertEquals(AssistantAnswer.OutOfScope, ask(model, history, AppLanguage.French))
        val (system, turns) = model.extractCalls.single()
        assertEquals(history.takeLast(6) + ChatTurn(Speaker.User, "What happened in month 2?"), turns)
        assertTrue("Months played: 1 to 4" in system && "French" in system, system)
        assertNull(model.explainCalls.firstOrNull())
    }
    @Test fun aRealIncomeMislabelledAsNetWorthIsRetriedAndRejected() {
        val income = m(played.history[1].flow.income, "CHF")
        val model = FakeModel(extractions = listOf(month2), replies = listOf(
            ModelResult.Ok("Net worth: $income."), ModelResult.Ok("Patrimoine net : $income.")))
        assertEquals(AssistantAnswer.Unreliable, ask(model))
        assertTrue("unsupported bindings" in model.explainCalls[1].second)
    }

    @Test fun epflSalaryExplanationUsesEngineFactsWithoutModelWording() {
        val offered = com.tuna.finlife.game.DemoJourney.atEpflOffer("Alex")
        val event = com.tuna.finlife.simulation.SimulationSessions.pendingDecisions(offered).single().event
        val selected = AdventureRun.choose(offered, event.id, "epfl")
        for (language in AppLanguage.entries) {
            val model = FakeModel(extractions = listOf(ModelResult.Ok(Extraction(Topic.PlayedMonths, 28))))
            val answer = runNow { Assistant(model).answer("Pourquoi le salaire a-t-il changé au mois 28 ?",
                emptyList(), context(selected), language) }
            val text = assertIs<AssistantAnswer.Explanation>(answer).text
            assertTrue("4500 CHF" in text && "900 CHF" in text, text)
            assertTrue(model.explainCalls.isEmpty())
        }
    }
}
