package com.tuna.finlife.game

import com.tuna.finlife.simulation.HousingContract
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.LifeStatus
import com.tuna.finlife.simulation.MicroEvents
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyProfile
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.PaymentResponsibility
import com.tuna.finlife.simulation.RandomEventSettings
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.random.Random

/** Optional situations the player can try in the current month. */
internal enum class WhatIf { Internship, SharedFlat, Plant, Oven }

/**
 * App-side setup of the adventure on top of the engine. Problems (rent rises, maintenance) are drawn
 * by [RandomTroubles] when each month opens; what-ifs are added on request. Both are added to the
 * engine calendar through [SimulationSessions.fork], so all money comes from the engine.
 */
internal object AdventureRun {
    // Synthetic demo assumptions for the shared-flat what-if (previously the month-four housing choice).
    private val SHARED_FLAT_RENT = Money.chf(500)
    private val SHARED_FLAT_MOVING_COST = Money.chf(500)

    /** [seed] drives the random problems; a new one per run unless given (tests). */
    fun start(seed: Long = Random.nextLong()): SimulationSession {
        val home = HousingContract("maya-home", Money.chf(700), 0, HousingType.ALONE)
        val profile = MonthlyProfile.alexia().copy(
            profileId = "finlife-maya",
            name = "Maya",
            housing = home,
            groceries = Money.chf(230),
        )
        val session = SimulationSessions.start(
            profile = profile,
            configuration = MonthlyConfiguration(
                // Open-ended for the player: the engine's maximum horizon. The player ends the run.
                horizonMonths = MonthlyConfiguration.MAX_MONTHS,
                annualInflation = 0.0,
                randomSeed = seed,
                // The engine's own random calendar is off: problems come from RandomTroubles only.
                randomEvents = RandomEventSettings(enabled = false),
            ),
        )
        return openMonth(session)
    }

    /** Advances one month, then draws the problems of the month that opens. */
    fun advance(session: SimulationSession): SimulationSession {
        val next = SimulationSessions.advance(session)
        return if (next.currentMonth > session.currentMonth) openMonth(next) else next
    }

    /**
     * Records the answer to one pending decision. Once every decision of the month is answered,
     * the month is advanced. Returns [session] unchanged if the decision or option is not pending.
     */
    fun choose(session: SimulationSession, eventId: String, optionId: String): SimulationSession {
        val answered = submit(session, eventId, optionId) ?: return session
        return if (SimulationSessions.pendingDecisions(answered).isEmpty()) advance(answered) else answered
    }

    /** The month that would result from this option, or null if other decisions of the month remain open. */
    fun preview(session: SimulationSession, eventId: String, optionId: String): MonthlyRecord? {
        val answered = submit(session, eventId, optionId) ?: return null
        if (SimulationSessions.pendingDecisions(answered).isNotEmpty()) return null
        val next = SimulationSessions.advance(answered)
        return if (next.history.size > session.history.size) next.history.last() else null
    }

    fun availableWhatIfs(session: SimulationSession): List<WhatIf> {
        if (session.status != SessionStatus.READY || SimulationSessions.pendingDecisions(session).isNotEmpty()) return emptyList()
        val month = session.currentMonth
        if (month >= session.configuration.horizonMonths) return emptyList()
        val state = session.currentState
        return WhatIf.entries.filter { whatIf ->
            session.calendar.none { it.id == whatIfId(whatIf, month) } && when (whatIf) {
                WhatIf.Internship -> state.status == LifeStatus.STUDENT
                WhatIf.SharedFlat -> state.housing.type != HousingType.SHARED
                WhatIf.Plant, WhatIf.Oven -> true
            }
        }
    }

    /** Adds the what-if to the current month; its decision is then pending. */
    fun addWhatIf(session: SimulationSession, whatIf: WhatIf): SimulationSession {
        if (whatIf !in availableWhatIfs(session)) return session
        val month = session.currentMonth
        val id = whatIfId(whatIf, month)
        val event = when (whatIf) {
            WhatIf.Internship -> LifeEvent.Internship(id, month)
            WhatIf.SharedFlat -> LifeEvent.HousingChoice(
                id,
                month,
                HousingContract("shared-flat-$month", SHARED_FLAT_RENT, month, HousingType.SHARED),
                SHARED_FLAT_MOVING_COST,
                PaymentResponsibility.CHARACTER,
            )
            WhatIf.Plant -> MicroEvents.plant(id, month)
            WhatIf.Oven -> MicroEvents.oven(id, month, PaymentResponsibility.CHARACTER)
        }
        // advance() stops before any transition while the new decision is unanswered.
        return SimulationSessions.advance(withEvents(session, listOf(event)))
    }

    private fun whatIfId(whatIf: WhatIf, month: Int) = "whatif-${whatIf.name.lowercase()}-$month"

    private fun submit(session: SimulationSession, eventId: String, optionId: String): SimulationSession? {
        val pending = SimulationSessions.pendingDecisions(session).singleOrNull { it.event.id == eventId }
        if (session.status != SessionStatus.AWAITING_DECISION || pending == null ||
            pending.options.none { it.id == optionId }
        ) return null
        return SimulationSessions.submit(session, eventId, optionId)
    }

    private fun openMonth(session: SimulationSession): SimulationSession {
        if (session.status != SessionStatus.READY) return session
        val drawn = RandomTroubles.draw(
            session.configuration.randomSeed,
            session.currentMonth,
            session.currentState.housing.id,
            session.calendar,
        )
        return if (drawn.isEmpty()) session else withEvents(session, drawn)
    }

    /** Adds events from the current month on. No past month or answer is changed. */
    private fun withEvents(session: SimulationSession, events: List<LifeEvent>): SimulationSession {
        var branchId = "run-${session.currentMonth}-${session.calendar.size + events.size}"
        if (branchId == session.branchId) branchId += "b"
        return SimulationSessions.fork(session, session.currentMonth, branchId, events)
    }
}
