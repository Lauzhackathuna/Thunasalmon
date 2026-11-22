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
import com.tuna.finlife.simulation.MonthlySimulation
import com.tuna.finlife.simulation.RandomEventSettings
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.random.Random

/**
 * Optional situations the player can switch on; each applies from the current month as a choice already made.
 * [Internship] is the engine's 3-month paid offer (LifeEvent.ShortInternshipOffer), alongside studies.
 */
internal enum class WhatIf { Internship, SharedFlat, CareerBreak }

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
 * engine calendar through [SimulationSessions.schedule], so all money comes from the engine.
 */
internal object AdventureRun {
    /** Flat synthetic share of the salary paid as taxes and social contributions (team assumption). */
    const val SALARY_TAX_RATE = 0.20

    /** [seed] drives the random problems; a new one per run unless given (tests). */
    fun start(seed: Long = Random.nextLong(), data: StartingData = StartingData.Default, playerName: String = "Player"): SimulationSession {
        return openMonth(createSession(seed, data, playerName = playerName))
    }

    private fun createSession(seed: Long, data: StartingData, events: List<LifeEvent> = emptyList(), playerName: String = "Player"): SimulationSession {
        val home = HousingContract("maya-home", Money.chf(data.rent), 0, HousingType.ALONE)
        val profile = MonthlyProfile.alexia().copy(
            profileId = "finlife-maya",
            name = requireNotNull(PlayerName.normalize(playerName)),
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
        return SimulationSessions.start(
            profile = profile,
            configuration = MonthlyConfiguration(
                // Open-ended for the player: the engine's maximum horizon. The player ends the run.
                horizonMonths = MonthlyConfiguration.MAX_MONTHS,
                annualInflation = 0.02,
                annualSalaryGrowth = 0.0,
                randomSeed = seed,
                // The engine's shared-flat contract (what-if and answer to a rent rise) uses these values.
                sharedReferenceRent = Money.chf(data.sharedFlatRent),
                sharedMovingCost = Money.chf(data.sharedFlatMovingCost),
                // The engine's own random calendar is off: problems come from RandomTroubles only.
                randomEvents = RandomEventSettings(enabled = false),
                // Synthetic team assumption: taxes and social contributions on the salary after studies.
                incomeTaxRate = SALARY_TAX_RATE,
            ),
            events = events,
        )
    }

    /** Replays the same calendar and answers from opening with revised assumptions. */
    fun recalculate(session: SimulationSession, data: StartingData): SimulationSession? {
        val seed = session.configuration.randomSeed
        val base = createSession(seed, data, playerName = session.profile.name)
        val configuration = base.configuration
        val revisedEvents = session.calendar.map { event ->
            when (event) {
                is LifeEvent.ShortInternshipOffer -> event.copy(monthlyIncome = Money.chf(data.internshipSalary))
                is LifeEvent.HousingChoice -> if (event.id == whatIfId(WhatIf.SharedFlat, event.month)) {
                    MonthlySimulation.sharedHousingChoice(event.month, configuration, event.id)
                } else event
                else -> event
            }
        }
        return restore(seed, data, revisedEvents, session.selections, session.currentMonth, playerName = session.profile.name)
    }

    /** Deterministic replay of recorded choices, used only for assumption edits and local restoration. */
    fun restore(seed: Long, data: StartingData, events: List<LifeEvent>, selections: List<com.tuna.finlife.simulation.DecisionSelection>,
        completedMonths: Int, playerName: String = "Player"): SimulationSession? {
        require(completedMonths in 0..MonthlyConfiguration.MAX_MONTHS)
        require(selections.map { it.eventId }.distinct().size == selections.size)
        val answers = selections.associate { it.eventId to it.optionId }
        var replay = createSession(seed, data, playerName = playerName)
        for (month in 0..completedMonths) {
            // Adventure events are revealed when their month opens. Feeding the entire saved
            // calendar at month zero would earmark future random repairs before they were known.
            events.filter { it.month == month }.forEach { replay = SimulationSessions.schedule(replay, it) }
            repeat(events.count { it.month == month && it.interactive }) {
                val pending = SimulationSessions.pendingDecisions(replay).firstOrNull { it.event.id in answers }
                if (pending != null) replay = SimulationSessions.submit(replay, pending.event.id, answers.getValue(pending.event.id))
            }
            val waiting = SimulationSessions.pendingDecisions(replay).isNotEmpty()
            if (month == completedMonths) {
                if (replay.selections.toSet() != selections.toSet()) return null
                events.filter { it.month > completedMonths }.forEach { replay = SimulationSessions.schedule(replay, it) }
                return if (waiting) SimulationSessions.advance(replay) else replay
            }
            if (waiting) return null
            replay = SimulationSessions.advance(replay)
            if (replay.currentMonth != month + 1) return null
        }
        return null
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
        val answered = commitDecision(session, eventId, optionId) ?: return session
        return if (SimulationSessions.pendingDecisions(answered).isEmpty()) advance(answered) else answered
    }

    /** The month that would result from this option, or null if other decisions of the month remain open. */
    fun preview(session: SimulationSession, eventId: String, optionId: String): MonthlyRecord? {
        val answered = commitDecision(session, eventId, optionId) ?: return null
        if (SimulationSessions.pendingDecisions(answered).isNotEmpty()) return null
        val next = SimulationSessions.advance(answered)
        return if (next.history.size > session.history.size) next.history.last() else null
    }

    /**
     * The engine's own default answer when nobody chose (FinancialProjections: keep the housing,
     * decline new offers). Null for decisions without such a default (purchases).
     */
    fun defaultAnswer(event: LifeEvent): String? = when (event) {
        is LifeEvent.RentRise, is LifeEvent.HousingChoice, is LifeEvent.EpflJobOffer -> "stay"
        is LifeEvent.Internship, is LifeEvent.ShortInternshipOffer -> "decline"
        else -> null
    }

    fun whatIfStates(session: SimulationSession): Map<WhatIf, WhatIfState> {
        val month = session.currentMonth
        val open = session.status == SessionStatus.READY && month < session.configuration.horizonMonths &&
            noPendingDecision(session)
        val state = session.currentState
        return WhatIf.entries.associateWith { whatIf ->
            val scheduled = session.calendar.any { it.id == whatIfId(whatIf, month) }
            val inEffect = when (whatIf) {
                // Accepted offer still running (its three months have not all passed).
                WhatIf.Internship -> state.shortInternship?.let { it.active(month) || month < it.startMonth } == true
                WhatIf.SharedFlat -> state.housing.type == HousingType.SHARED
                WhatIf.CareerBreak -> state.careerBreakRemaining > 0
            }
            val possible = when (whatIf) {
                WhatIf.Internship -> state.status == LifeStatus.STUDENT && month <= MonthlyConfiguration.MAX_MONTHS - 3
                WhatIf.SharedFlat -> true
                WhatIf.CareerBreak -> state.status == LifeStatus.EMPLOYED && !state.jobLost &&
                    state.careerBreakRemaining == 0 && month <= MonthlyConfiguration.MAX_MONTHS - 6
            }
            when {
                scheduled || inEffect -> WhatIfState.Active
                open && possible -> WhatIfState.Available
                else -> WhatIfState.Unavailable
            }
        }
    }

    /** Switches the what-if on for the current month; it takes effect when the month is advanced. */
    fun applyWhatIf(session: SimulationSession, whatIf: WhatIf, internshipSalary: Long = StartingData.Default.internshipSalary): SimulationSession {
        if (whatIfStates(session)[whatIf] != WhatIfState.Available) return session
        val month = session.currentMonth
        val id = whatIfId(whatIf, month)
        return when (whatIf) {
            // The engine's 3-month offer is a decision: accept it right away (switching on = accepting).
            WhatIf.Internship -> SimulationSessions.submit(
                withEvents(session, listOf(LifeEvent.ShortInternshipOffer(id, month, monthlyIncome = Money.chf(internshipSalary)))),
                id, "accept",
            )
            // The engine's own shared-flat proposal; it is a decision, so the move is answered right away.
            WhatIf.SharedFlat -> SimulationSessions.submit(SimulationSessions.proposeSharedHousing(session), id, "move")
            WhatIf.CareerBreak -> withEvents(session, listOf(LifeEvent.CareerBreak(id, month)))
        }
    }

    /**
     * [base] with the selected what-ifs switched on for its current month. Rebuilt from [base] each time,
     * so a what-if can be switched off again until the month is advanced.
     */
    fun withWhatIfs(base: SimulationSession, selected: Set<WhatIf>, internshipSalary: Long): SimulationSession =
        WhatIf.entries.filter { it in selected }.fold(base) { session, whatIf -> applyWhatIf(session, whatIf, internshipSalary) }

    /** False if a decision is pending or the engine rejects the coming month (amount limit reached). */
    private fun noPendingDecision(session: SimulationSession): Boolean = try {
        SimulationSessions.pendingDecisions(session).isEmpty()
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun whatIfId(whatIf: WhatIf, month: Int) = when (whatIf) {
        // Id chosen by the engine's proposeSharedHousing.
        WhatIf.SharedFlat -> "FINLIFE_SHARED_$month"
        else -> "whatif-${whatIf.name.lowercase()}-$month"
    }

    /**
     * Store a choice without advancing time. Keeping the private-sector job exposes that run
     * to one independently seeded 30% failure draw over the following twelve months.
     * The drawn event is committed to the calendar so saves and counterfactuals replay exactly.
     */
    internal fun commitDecision(session: SimulationSession, eventId: String, optionId: String): SimulationSession? {
        val answered = submit(session, eventId, optionId) ?: return null
        val offer = session.calendar.filterIsInstance<LifeEvent.EpflJobOffer>()
            .singleOrNull { it.id == eventId } ?: return answered
        if (optionId != "stay") return answered
        val draw = Random(session.configuration.randomSeed xor offer.id.hashCode().toLong() xor 0x4550464cL)
        if (draw.nextDouble() >= offer.privateCompanyFailureProbability) return answered
        val month = offer.month + draw.nextInt(1, offer.riskHorizonMonths + 1)
        if (month >= session.configuration.horizonMonths) return answered
        return SimulationSessions.schedule(answered, LifeEvent.CompanyClosure("company-closure-${offer.id}", month, offer.id))
    }

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
        val state = session.currentState
        val jobOffer = if (state.status == LifeStatus.EMPLOYED && !state.jobLost &&
            state.employerType == com.tuna.finlife.simulation.EmployerType.PRIVATE &&
            state.careerBreakRemaining == 0 &&
            state.careerStartMonth != null && session.currentMonth >= state.careerStartMonth + 3 &&
            session.currentMonth + 12 < session.configuration.horizonMonths &&
            session.calendar.none { it is LifeEvent.EpflJobOffer }
        ) listOf(LifeEvent.EpflJobOffer("epfl-offer-${session.currentMonth}", session.currentMonth))
        else emptyList()
        if (drawn.isEmpty() && jobOffer.isEmpty()) return session
        val indexed = drawn.map { event ->
            if (event is LifeEvent.OneOffExpense) {
                event.copy(amount = event.amount.scaled(session.configuration.priceIndex(event.month)))
            } else event
        }
        val scheduled = withEvents(session, indexed + jobOffer)
        // A drawn rent rise can be a decision now: surface it before anything else happens this month.
        return if (noPendingDecision(scheduled)) scheduled else SimulationSessions.advance(scheduled)
    }

    /** Adds events to the current month; answers already given this month are kept. */
    private fun withEvents(session: SimulationSession, events: List<LifeEvent>): SimulationSession =
        events.fold(session) { run, event -> SimulationSessions.schedule(run, event) }
}
