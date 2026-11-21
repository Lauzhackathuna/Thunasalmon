package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CounterfactualTest {
    /** Advances like the app's Advance button: the month's what-ifs start or keep the counterfactual. */
    private fun play(
        session: SimulationSession,
        previous: Counterfactual?,
        draft: WhatIfDraft? = null,
    ): Pair<SimulationSession, Counterfactual?> {
        val next = AdventureRun.advance(answerDefaults(draft?.session ?: session))
        return next to Counterfactual.afterAdvance(previous, draft, next)
    }

    /** Answers a pending decision like the app's option button. */
    private fun answer(session: SimulationSession, optionId: String): Pair<SimulationSession, Counterfactual?> {
        val eventId = SimulationSessions.pendingDecisions(session).single().event.id
        val next = AdventureRun.choose(session, eventId, optionId)
        return next to Counterfactual.afterChoice(session, eventId, optionId, next)
    }

    /** First seed whose opening month draws a rent rise, so the run starts on that decision. */
    private fun runStartingOnRentRise(): SimulationSession =
        (1L..500L).map { AdventureRun.start(seed = it) }.first { it.status == SessionStatus.AWAITING_DECISION }

    @Test
    fun noChoiceMeansNoAlternative() {
        val (_, counterfactual) = play(AdventureRun.start(seed = 5), null)
        assertNull(counterfactual)
    }

    @Test
    fun withoutTheWhatIfFollowsInLockstep() {
        val defaults = StartingData.Default
        val start = answerDefaults(AdventureRun.start(seed = 5))
        var (main, counterfactual) = play(start, null, WhatIfDraft(start).toggle(WhatIf.SharedFlat, true))
        repeat(5) { play(main, counterfactual).let { (m, c) -> main = m; counterfactual = c } }
        val cf = assertNotNull(counterfactual)

        assertEquals(0, cf.month)
        assertEquals(AlternativeChoice.WithoutWhatIfs(setOf(WhatIf.SharedFlat)), cf.choice)
        assertEquals(main.currentMonth, cf.alternative.currentMonth)
        assertEquals(main.states[0], cf.alternative.states[0])
        assertEquals(Money.chf(defaults.sharedFlatRent), main.history[0].flow.rent)
        assertEquals(Money.chf(defaults.rent), cf.alternative.history[0].flow.rent)
        // Same random draws per month on both paths (repairs do not depend on the home).
        fun repairs(run: SimulationSession) = run.calendar.filterIsInstance<LifeEvent.Maintenance>().map { it.month }
        assertEquals(repairs(main), repairs(cf.alternative))
    }

    @Test
    fun answeringARentRiseShowsTheOtherAnswer() {
        val waiting = runStartingOnRentRise()
        val (main, counterfactual) = answer(waiting, "stay")
        val cf = assertNotNull(counterfactual)
        val choice = assertIs<AlternativeChoice.OtherAnswer>(cf.choice)
        assertIs<LifeEvent.RentRise>(choice.event)
        assertEquals("move", choice.option.id)
        assertEquals(0, cf.month)
        assertEquals(main.currentMonth, cf.alternative.currentMonth)
        // The player kept the home, the dotted path moved into the shared flat.
        assertEquals(Money.chf(StartingData.Default.sharedFlatRent), cf.alternative.history[0].flow.rent)
        assertTrue(main.history[0].flow.rent > Money.chf(StartingData.Default.rent))
    }

    @Test
    fun onlyTheLatestChoiceKeepsADottedLine() {
        val waiting = runStartingOnRentRise()
        var (main, counterfactual) = answer(waiting, "stay")
        main = answerDefaults(main)
        play(main, counterfactual, WhatIfDraft(main).toggle(WhatIf.Internship, true)).let { (m, c) -> main = m; counterfactual = c }
        val cf = assertNotNull(counterfactual)
        assertEquals(1, cf.month)
        assertEquals(AlternativeChoice.WithoutWhatIfs(setOf(WhatIf.Internship)), cf.choice)
        assertEquals(main.states.take(2), cf.alternative.states.take(2))
        assertEquals(main.currentMonth, cf.alternative.currentMonth)
    }

    @Test
    fun theAlternativeKeepsFollowingAfterLaterMonths() {
        val waiting = runStartingOnRentRise()
        var (main, counterfactual) = answer(waiting, "move")
        repeat(10) { play(main, counterfactual).let { (m, c) -> main = m; counterfactual = c } }
        assertEquals(main.currentMonth, assertNotNull(counterfactual).alternative.currentMonth)
    }

    @Test
    fun switchingOnThenOffLeavesNoAlternative() {
        val start = answerDefaults(AdventureRun.start(seed = 5))
        val draft = WhatIfDraft(start).toggle(WhatIf.Internship, true).toggle(WhatIf.Internship, false)
        val (_, counterfactual) = play(start, null, draft)
        assertNull(counterfactual)
    }
}
