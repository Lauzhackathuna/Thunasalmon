package com.tuna.finlife.simulation

enum class SessionStatus { READY, AWAITING_DECISION, COMPLETED }

/** Immutable snapshot. Collections returned to callers are copies, never shared mutable branch state. */
class SimulationSession internal constructor(
    val branchId: String,
    val parentBranchId: String?,
    val forkMonth: Int?,
    val profile: MonthlyProfile,
    val configuration: MonthlyConfiguration,
    val initialState: MonthlyState,
    calendar: List<LifeEvent>,
    records: List<MonthlyRecord>,
    selections: List<DecisionSelection>,
    val status: SessionStatus,
) {
    private val calendarData = calendar.toList()
    private val recordData = records.toList()
    private val selectionData = selections.toList()
    val calendar: List<LifeEvent> get() = calendarData.toList()
    val history: List<MonthlyRecord> get() = recordData.toList()
    val selections: List<DecisionSelection> get() = selectionData.toList()
    val states: List<MonthlyState> get() = listOf(initialState) + recordData.map { it.after }
    val currentState: MonthlyState get() = recordData.lastOrNull()?.after ?: initialState
    val currentMonth: Int get() = currentState.month
    val goal: GoalProgress get() = GoalProgress(configuration.goal, currentMonth, currentState.netWorth,
        currentState.netWorth.cents.toDouble() / configuration.goal.nominalAmount.cents * 100.0,
        currentMonth >= configuration.goal.dueMonth)

    /** These are uncomputed future effects, never forecasts or a promise that the goal will be met. */
    fun unsimulatedConsequences(): List<String> = buildList {
        if (currentMonth < configuration.horizonMonths) add("${configuration.horizonMonths - currentMonth} mois du parcours ne sont pas encore simulés.")
        if (currentState.internshipMonthsRemaining > 0) add("${currentState.internshipMonthsRemaining} mois de stage restent à simuler.")
        if (currentState.studyMonthsRemaining > 0) add("${currentState.studyMonthsRemaining} mois d'études restent à effectuer ; carrière ultérieure non encore simulée.")
        if (currentState.rentRule != null) add("La règle de loyer persiste au-delà de l'état courant ; ses coûts futurs ne sont pas encore simulés.")
        if (configuration.goal.dueMonth > currentMonth) add("Objectif échéant au mois ${configuration.goal.dueMonth}, pas au mois $currentMonth ; échéance non encore simulée.")
    }
}

data class GoalProgress(
    val goal: FinancialGoal,
    val observedMonth: Int,
    val nominalNetWorth: Money,
    val percentage: Double,
    val dueDateSimulated: Boolean,
)

object SimulationSessions {
    fun start(
        profile: MonthlyProfile = MonthlyProfile.alexia(),
        configuration: MonthlyConfiguration = MonthlyConfiguration(),
        events: List<LifeEvent> = emptyList(),
        branchId: String = "main",
    ): SimulationSession {
        require(branchId.isNotBlank())
        val calendar = EventCalendar.generate(profile, configuration, events)
        return SimulationSession(branchId, null, null, profile, configuration,
            MonthlySimulation.initialState(profile, configuration), calendar, emptyList(), emptyList(), SessionStatus.READY)
    }

    /** Inspection never advances time, applies a cash flow or draws random events. */
    fun inspect(session: SimulationSession): MonthStep? {
        if (session.currentMonth == session.configuration.horizonMonths) return null
        val events = session.calendar.filter { it.month == session.currentMonth }
        val eventIds = events.map { it.id }.toSet()
        return MonthlySimulation.calculateMonth(session.profile, session.configuration, session.currentState,
            events, session.selections.filter { it.eventId in eventIds },
            session.calendar.filter { it.month > session.currentMonth })
    }

    fun currentEvents(session: SimulationSession): List<LifeEvent> =
        session.calendar.filter { it.month == session.currentMonth }

    fun pendingDecisions(session: SimulationSession): List<PendingDecision> =
        (inspect(session) as? MonthStep.AwaitingDecision)?.decisions ?: emptyList()

    /** Adds an explicit opportunity without changing the branch or advancing its clock. */
    fun schedule(session: SimulationSession, event: LifeEvent): SimulationSession {
        require(event.month >= session.currentMonth)
        val calendar = (session.calendar + event).sortedWith(EventCalendar.order)
        EventCalendar.validate(calendar, session.configuration.horizonMonths)
        return SimulationSession(session.branchId, session.parentBranchId, session.forkMonth, session.profile,
            session.configuration, session.initialState, calendar, session.history, session.selections, session.status)
    }

    fun proposeSharedHousing(session: SimulationSession): SimulationSession {
        require(session.currentState.housing.type != HousingType.SHARED)
        return schedule(session, MonthlySimulation.sharedHousingChoice(session.currentMonth, session.configuration))
    }

    /** Records a response but does not advance the clock. A past response is changed through fork(). */
    fun submit(session: SimulationSession, eventId: String, optionId: String): SimulationSession {
        val pending = pendingDecisions(session).singleOrNull { it.event.id == eventId }
        require(pending != null) { "No unresolved applicable decision with this ID in the current month" }
        require(pending.options.any { it.id == optionId }) { "Unknown decision option" }
        val next = snapshot(session, selections = session.selections + DecisionSelection(eventId, optionId), status = SessionStatus.READY)
        return snapshot(next, status = if (pendingDecisions(next).isEmpty()) SessionStatus.READY else SessionStatus.AWAITING_DECISION)
    }

    fun advance(session: SimulationSession): SimulationSession = when (val result = inspect(session)) {
        null -> snapshot(session, status = SessionStatus.COMPLETED)
        is MonthStep.AwaitingDecision -> snapshot(session, status = SessionStatus.AWAITING_DECISION)
        is MonthStep.Calculated -> snapshot(session, records = session.history + result.record,
            status = if (result.record.after.month == session.configuration.horizonMonths) SessionStatus.COMPLETED else SessionStatus.READY)
    }

    /** Advances at most maxMonths transitions, stopping before any unanswered decision. */
    fun runUntilBlocked(session: SimulationSession, maxMonths: Int = session.configuration.horizonMonths): SimulationSession {
        require(maxMonths >= 0)
        var result = session
        repeat(maxMonths) {
            result = advance(result)
            if (result.status != SessionStatus.READY) return result
        }
        return result
    }

    fun viewState(session: SimulationSession, month: Int): MonthlyState {
        require(month in 0..session.currentMonth)
        return session.states[month]
    }

    /** Keep external events, copy the common prefix, discard responses at/after the fork date. No RNG. */
    fun fork(session: SimulationSession, month: Int, branchId: String, additionalEvents: List<LifeEvent> = emptyList()): SimulationSession {
        require(month in 0..session.currentMonth)
        require(branchId.isNotBlank() && branchId != session.branchId)
        require(additionalEvents.all { it.month >= month }) { "No retroactive intervention" }
        val calendar = (session.calendar + additionalEvents).sortedWith(EventCalendar.order)
        EventCalendar.validate(calendar, session.configuration.horizonMonths)
        val pastIds = calendar.filter { it.month < month }.map { it.id }.toSet()
        return SimulationSession(branchId, session.branchId, month, session.profile, session.configuration,
            session.initialState, calendar, session.history.take(month), session.selections.filter { it.eventId in pastIds },
            if (month == session.configuration.horizonMonths) SessionStatus.COMPLETED else SessionStatus.READY)
    }

    private fun snapshot(session: SimulationSession, records: List<MonthlyRecord> = session.history,
        selections: List<DecisionSelection> = session.selections, status: SessionStatus = session.status): SimulationSession =
        SimulationSession(session.branchId, session.parentBranchId, session.forkMonth, session.profile, session.configuration,
            session.initialState, session.calendar, records, selections, status)
}
