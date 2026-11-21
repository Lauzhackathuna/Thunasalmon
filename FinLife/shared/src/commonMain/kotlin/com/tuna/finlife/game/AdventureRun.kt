package com.tuna.finlife.game

import com.tuna.finlife.simulation.HousingContract
import com.tuna.finlife.simulation.HousingType
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyProfile
import com.tuna.finlife.simulation.MonthlyRecord
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.PaymentResponsibility
import com.tuna.finlife.simulation.RandomEventSettings
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSession
import com.tuna.finlife.simulation.SimulationSessions

internal enum class HousingDecision(val optionId: String) { Stay("stay"), Move("move") }

internal object AdventureRun {
    private const val HOUSING_EVENT = "month-four-housing"
    private const val DECISION_MONTH_INDEX = 3

    // Synthetic demo assumptions. Only one interactive event is scheduled.
    fun start(): SimulationSession {
        val home = HousingContract("maya-home", Money.chf(700), 0, HousingType.ALONE)
        val profile = MonthlyProfile.alexia().copy(
            profileId = "finlife-maya",
            name = "Maya",
            housing = home,
            groceries = Money.chf(230),
        )
        return SimulationSessions.start(
            profile = profile,
            configuration = MonthlyConfiguration(
                horizonMonths = 12,
                annualInflation = 0.0,
                randomEvents = RandomEventSettings(enabled = false),
            ),
            events = listOf(
                LifeEvent.RentRise(
                    "month-four-rent",
                    DECISION_MONTH_INDEX,
                    MonthlyRentRule(home.id, DECISION_MONTH_INDEX, annualExtraRate = 0.08),
                ),
                LifeEvent.HousingChoice(
                    HOUSING_EVENT,
                    DECISION_MONTH_INDEX,
                    HousingContract("maya-shared-flat", Money.chf(500), DECISION_MONTH_INDEX, HousingType.SHARED),
                    Money.chf(500),
                    PaymentResponsibility.CHARACTER,
                ),
            ),
        )
    }

    fun choose(session: SimulationSession, choice: HousingDecision): SimulationSession {
        if (session.status != SessionStatus.AWAITING_DECISION ||
            SimulationSessions.pendingDecisions(session).none { it.event.id == HOUSING_EVENT }
        ) return session
        return SimulationSessions.advance(SimulationSessions.submit(session, HOUSING_EVENT, choice.optionId))
    }

    fun preview(session: SimulationSession, choice: HousingDecision): MonthlyRecord =
        choose(session, choice).history.last()
}
