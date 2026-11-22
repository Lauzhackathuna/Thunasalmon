package com.tuna.finlife.game

import com.tuna.finlife.simulation.DecisionOption
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions

/** What the dotted path does differently from the player's latest choice. */
internal sealed interface AlternativeChoice {
    /** The month played without the what-ifs the player switched on. */
    data class WithoutWhatIfs(val whatIfs: Set<WhatIf>) : AlternativeChoice

    /** The same decision answered with [option] instead of the player's answer. */
    data class OtherAnswer(val event: LifeEvent, val option: DecisionOption) : AlternativeChoice
}

/**
 * The run as it would have gone with the other side of the player's latest choice. [alternative] branches
 * from the session just before that choice (month [month]) and is advanced by the same engine and random
 * draws, month by month with the player's path. It never runs ahead of the player: no projection beyond
 * what was actually simulated. Only one exists at a time: a newer choice replaces it.
 */
internal data class Counterfactual(
    val month: Int,
    val choice: AlternativeChoice,
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
                run = AdventureRun.commitDecision(run, pending.event.id, answer) ?: return copy(alternative = run)
            }
            val next = AdventureRun.advance(run)
            if (next.currentMonth == run.currentMonth) break
            run = next
        }
        return copy(alternative = run)
    }

    companion object {
        /**
         * After the player advanced to [advanced]: what-ifs switched on in the month just played ([draft])
         * are the latest choice and start a new alternative; otherwise the [previous] one keeps following.
         */
        fun afterAdvance(previous: Counterfactual?, draft: WhatIfDraft?, advanced: SimulationSession): Counterfactual? {
            val started = draft?.takeIf { it.selected.isNotEmpty() }
                ?.let { Counterfactual(it.base.currentMonth, AlternativeChoice.WithoutWhatIfs(it.selected), it.base) }
            return (started ?: previous)?.catchUp(advanced)
        }

        /**
         * After the player answered [eventId] with [optionId] in [before] and the game moved to [after]:
         * the other answer starts a new alternative. Null if the decision has no single other answer.
         */
        fun afterChoice(before: SimulationSession, eventId: String, optionId: String, after: SimulationSession): Counterfactual? {
            val pending = SimulationSessions.pendingDecisions(before).singleOrNull { it.event.id == eventId } ?: return null
            val other = pending.options.filter { it.id != optionId }.singleOrNull() ?: return null
            val branched = AdventureRun.commitDecision(before, eventId, other.id) ?: return null
            return Counterfactual(before.currentMonth, AlternativeChoice.OtherAnswer(pending.event, other), branched)
                .catchUp(after)
        }
    }
}
