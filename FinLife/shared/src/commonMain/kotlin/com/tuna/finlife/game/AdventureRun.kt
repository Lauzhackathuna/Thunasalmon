package com.tuna.finlife.game

import com.tuna.finlife.simulation.DataProvenance
import com.tuna.finlife.simulation.HousingContract
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.LifeStatus
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

/** Optional situations the player can switch on; each applies from the current month as a choice already made. */
internal enum class WhatIf { Internship, SharedFlat }

internal enum class WhatIfState {
    /** Can be switched on this month. */
    Available,
    /** Switched on: scheduled for this month or still in effect. The engine cannot undo it. */
    Active,
    /** Not possible right now (e.g. internship after graduation, or a decision is pending). */
    Unavailable,
}

/**
 * App-side setup of the adventure on top of the engine. Problems (rent rises, maintenance) are drawn
 * by [RandomTroubles] when each month opens; what-ifs are switched on by the player. Both are added to the
 * engine calendar through [SimulationSessions.fork], so all money comes from the engine.
 */
internal object AdventureRun {
    /** [seed] drives the random problems; a new one per run unless given (tests). */
    fun start(seed: Long = Random.nextLong(), data: StartingData = StartingData.Default): SimulationSession {
        val home = HousingContract("maya-home", Money.chf(data.rent), 0, HousingType.ALONE)
        val profile = MonthlyProfile.alexia().copy(
            profileId = "finlife-maya",
            name = "Maya",
            cash = Money.chf(data.openingCash),
            studentMonthlyIncome = Money.chf(data.parentIncome),
            startingMonthlySalary = Money.chf(data.salaryAfterStudies),
            housing = home,
            healthInsurance = Money.chf(data.healthInsurance),
            groceries = Money.chf(data.groceries),
            otherExpenses = Money.chf(data.otherExpenses),
            tuitionPayment = Money.chf(data.tuition),
            provenance = if (data == StartingData.Default) DataProvenance.TEAM_ASSUMPTION else DataProvenance.USER_INPUT,
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

    fun whatIfStates(session: SimulationSession): Map<WhatIf, WhatIfState> {
        val month = session.currentMonth
        val open = session.status == SessionStatus.READY && month < session.configuration.horizonMonths &&
            noPendingDecision(session)
        val state = session.currentState
        return WhatIf.entries.associateWith { whatIf ->
            val scheduled = session.calendar.any { it.id == whatIfId(whatIf, month) }
            val inEffect = when (whatIf) {
                WhatIf.Internship -> state.status == LifeStatus.INTERN
                WhatIf.SharedFlat -> state.housing.type == HousingType.SHARED
            }
            val possible = when (whatIf) {
                WhatIf.Internship -> state.status == LifeStatus.STUDENT
                WhatIf.SharedFlat -> true
            }
            when {
                scheduled || inEffect -> WhatIfState.Active
                open && possible -> WhatIfState.Available
                else -> WhatIfState.Unavailable
            }
        }
    }

    /** Switches the what-if on for the current month; it takes effect when the month is advanced. */
    fun applyWhatIf(
        session: SimulationSession,
        whatIf: WhatIf,
        offer: SharedFlatOffer = SharedFlatOffer.Default,
    ): SimulationSession {
        if (whatIfStates(session)[whatIf] != WhatIfState.Available) return session
        val month = session.currentMonth
        val id = whatIfId(whatIf, month)
        return when (whatIf) {
            // interactive = false: the engine treats the internship as already accepted.
            WhatIf.Internship -> withEvents(session, listOf(LifeEvent.Internship(id, month, interactive = false)))
            // A housing choice is always a decision in the engine, so the move is answered right away.
            WhatIf.SharedFlat -> SimulationSessions.submit(
                withEvents(
                    session,
                    listOf(
                        LifeEvent.HousingChoice(
                            id,
                            month,
                            HousingContract("shared-flat-$month", offer.monthlyRent, month, HousingType.SHARED),
                            offer.movingCost,
                            PaymentResponsibility.CHARACTER,
                        ),
                    ),
                ),
                id,
                "move",
            )
        }
    }

    /**
     * [base] with the selected what-ifs switched on for its current month. Rebuilt from [base] each time,
     * so a what-if can be switched off again until the month is advanced.
     * Internship first: adding an event forks the month, which drops answers already given in it
     * (the shared flat's "move").
     */
    fun withWhatIfs(
        base: SimulationSession,
        selected: Set<WhatIf>,
        offer: SharedFlatOffer = SharedFlatOffer.Default,
    ): SimulationSession =
        WhatIf.entries.filter { it in selected }.fold(base) { session, whatIf -> applyWhatIf(session, whatIf, offer) }

    /** False if a decision is pending or the engine rejects the coming month (amount limit reached). */
    private fun noPendingDecision(session: SimulationSession): Boolean = try {
        SimulationSessions.pendingDecisions(session).isEmpty()
    } catch (_: IllegalArgumentException) {
        false
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
