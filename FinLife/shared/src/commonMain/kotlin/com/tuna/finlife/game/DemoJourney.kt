package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions

/** A normal seeded journey, fast-forwarded with explicit default answers to the EPFL offer. */
internal object DemoJourney {
    const val SEED = 19L

    fun atEpflOffer(playerName: String): SimulationSession {
        var session = AdventureRun.start(seed = SEED, data = StartingData.Default, playerName = playerName)
        repeat(100) {
            val pending = SimulationSessions.pendingDecisions(session)
            val unrelated = pending.firstOrNull { it.event !is LifeEvent.EpflJobOffer }
            if (unrelated != null) {
                session = AdventureRun.choose(session, unrelated.event.id,
                    requireNotNull(AdventureRun.defaultAnswer(unrelated.event)))
            } else if (pending.any { it.event is LifeEvent.EpflJobOffer }) {
                return session
            } else {
                session = AdventureRun.advance(session)
            }
        }
        error("Demo journey did not reach the EPFL offer")
    }
}
