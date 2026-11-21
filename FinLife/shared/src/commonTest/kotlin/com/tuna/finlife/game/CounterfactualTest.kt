package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.SimulationSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CounterfactualTest {
    /** Advances like the app does: the draft of the month just played starts or keeps the counterfactual. */
    private fun play(
        session: SimulationSession,
        previous: Counterfactual?,
        draft: WhatIfDraft? = null,
    ): Pair<SimulationSession, Counterfactual?> {
        val next = AdventureRun.advance(draft?.session ?: session)
        return next to Counterfactual.afterAdvance(previous, draft, next)
    }

    @Test
    fun noWhatIfMeansNoAlternative() {
        val (_, counterfactual) = play(AdventureRun.start(seed = 5), null)
        assertNull(counterfactual)
    }

    @Test
    fun alternativeIsTheSameRunWithoutTheWhatIfInLockstep() {
        val start = AdventureRun.start(seed = 5)
        var (main, counterfactual) = play(start, null, WhatIfDraft(start).toggle(WhatIf.SharedFlat, true))
        repeat(5) { play(main, counterfactual).let { (m, c) -> main = m; counterfactual = c } }
        val alternative = assertNotNull(counterfactual).alternative

        assertEquals(0, counterfactual!!.month)
        assertEquals(setOf(WhatIf.SharedFlat), counterfactual!!.removed)
        assertEquals(main.currentMonth, alternative.currentMonth)
        assertEquals(main.states[0], alternative.states[0])
        assertEquals(Money.chf(500), main.history[0].flow.rent)
        assertEquals(Money.chf(700), alternative.history[0].flow.rent)
        // Same random draws per month on both paths (maintenance does not depend on the home).
        fun repairs(run: SimulationSession) = run.calendar.filterIsInstance<LifeEvent.Maintenance>().map { it.month }
        assertEquals(repairs(main), repairs(alternative))
    }

    @Test
    fun aLaterWhatIfReplacesTheAlternative() {
        val start = AdventureRun.start(seed = 5)
        var (main, counterfactual) = play(start, null, WhatIfDraft(start).toggle(WhatIf.SharedFlat, true))
        play(main, counterfactual).let { (m, c) -> main = m; counterfactual = c }
        play(main, counterfactual, WhatIfDraft(main).toggle(WhatIf.Internship, true)).let { (m, c) -> main = m; counterfactual = c }

        assertEquals(2, counterfactual!!.month)
        assertEquals(setOf(WhatIf.Internship), counterfactual!!.removed)
        assertEquals(main.states.take(3), counterfactual!!.alternative.states.take(3))
        assertEquals(main.currentMonth, counterfactual!!.alternative.currentMonth)
    }

    @Test
    fun switchingOnThenOffLeavesThePreviousAlternative() {
        val start = AdventureRun.start(seed = 5)
        val draft = WhatIfDraft(start).toggle(WhatIf.Internship, true).toggle(WhatIf.Internship, false)
        val (_, counterfactual) = play(start, null, draft)
        assertNull(counterfactual)
    }
}
