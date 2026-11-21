package com.tuna.finlife.game

import com.tuna.finlife.simulation.SimulationSession

/**
 * The run without the most recent what-ifs: [alternative] branches from the session saved just before
 * month [month]'s what-ifs and is advanced by the same engine and random draws, month by month with the
 * player's path. It never runs ahead of the player: no projection beyond what was actually simulated.
 */
internal data class Counterfactual(
    val month: Int,
    val removed: Set<WhatIf>,
    val alternative: SimulationSession,
) {
    /** Advances [alternative] up to [month]; stops early if the alternative would need a decision. */
    fun catchUp(month: Int): Counterfactual {
        var run = alternative
        while (run.currentMonth < month) {
            val next = AdventureRun.advance(run)
            if (next.currentMonth == run.currentMonth) break
            run = next
        }
        return copy(alternative = run)
    }

    companion object {
        /**
         * The counterfactual to show after the player advanced to [advanced]. What-ifs switched on in the
         * month just played ([draft]) start a new one; otherwise the [previous] one keeps following.
         */
        fun afterAdvance(previous: Counterfactual?, draft: WhatIfDraft?, advanced: SimulationSession): Counterfactual? {
            val started = draft?.takeIf { it.selected.isNotEmpty() }
                ?.let { Counterfactual(it.base.currentMonth, it.selected, it.base) }
            return (started ?: previous)?.catchUp(advanced.currentMonth)
        }
    }
}
