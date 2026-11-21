package com.tuna.finlife.game

import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions

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
    /**
     * Advances [alternative] up to [main]'s month. A decision that also exists on the player's path
     * (same event, e.g. the same drawn rent rise) gets the player's answer; otherwise the engine's default
     * answer (keep the housing, decline offers). A decision with neither stops the alternative.
     */
    fun catchUp(main: SimulationSession): Counterfactual {
        val answers = main.selections.associate { it.eventId to it.optionId }
        var run = alternative
        while (run.currentMonth < main.currentMonth) {
            for (pending in SimulationSessions.pendingDecisions(run)) {
                val answer = answers[pending.event.id] ?: AdventureRun.defaultAnswer(pending.event)
                    ?: return copy(alternative = run)
                run = SimulationSessions.submit(run, pending.event.id, answer)
            }
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
            return (started ?: previous)?.catchUp(advanced)
        }
    }
}
