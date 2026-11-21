package com.tuna.finlife.game

import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions

/** Answers every open decision with the engine's default answer (rent rise: keep the home). */
internal fun answerDefaults(session: SimulationSession): SimulationSession =
    SimulationSessions.pendingDecisions(session).fold(session) { run, pending ->
        SimulationSessions.submit(run, pending.event.id, requireNotNull(AdventureRun.defaultAnswer(pending.event)))
    }

/** Plays [months] months like a player who always keeps the defaults. */
internal fun playDefaults(session: SimulationSession, months: Int): SimulationSession =
    (1..months).fold(session) { run, _ -> AdventureRun.advance(answerDefaults(run)) }
