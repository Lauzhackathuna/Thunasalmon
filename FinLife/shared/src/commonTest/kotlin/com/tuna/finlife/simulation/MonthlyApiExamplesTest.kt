package com.tuna.finlife.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Executable counterparts of the integration examples in simulation/README.md. */
class MonthlyApiExamplesTest {
    @Test fun interactiveReadmeExample() {
        val profile = MonthlyProfile.alexia()
        val config = MonthlyConfiguration(
            horizonMonths = 6,
            annualInflation = 0.0,
            randomEvents = RandomEventSettings(enabled = false),
        )
        var session = FinancialSimulation.startSession(profile, config,
            events = listOf(MicroEvents.plant("plant", month = 2)))
        session = SimulationSessions.runUntilBlocked(session)
        val options = SimulationSessions.pendingDecisions(session).single().options
        session = SimulationSessions.submit(session, "plant", "first")
        session = SimulationSessions.advance(session)
        val monthResult = session.history.last()
        val explanations = monthResult.explanation()
        session = SimulationSessions.runUntilBlocked(session)
        val past = SimulationSessions.viewState(session, 2)
        var alternative = SimulationSessions.fork(session, 2, "no-plant")
        alternative = SimulationSessions.submit(alternative, "plant", "second")
        alternative = SimulationSessions.runUntilBlocked(alternative)
        val difference = MonthlyComparisons.compare(session, alternative, month = 6)
        val summaries = MonthlyComparisons.annualSummaries(session)
        assertEquals(2, options.size)
        assertTrue(explanations.isNotEmpty())
        assertEquals(Money.chf(6_000), past.cash)
        assertEquals(Money.chf(3_565), session.currentState.cash)
        assertEquals(Money.chf(3_600), alternative.currentState.cash)
        assertEquals(Money.chf(-35), difference.nominalNetWorth)
        assertFalse(summaries.single().isFullYear)
    }

    @Test fun combinedReadmeExample() {
        val config = MonthlyConfiguration(horizonMonths = 48,
            randomEvents = RandomEventSettings(enabled = false))
        val events = listOf(
            LifeEvent.Internship("S005", month = 3, interactive = false),
            LifeEvent.RentRise("S037", month = 24, rule = MonthlyRentRule("initial-home", 24)),
            LifeEvent.Maintenance("S040", month = 40, responsibility = PaymentResponsibility.CHARACTER),
        )
        var result = SimulationSessions.runUntilBlocked(FinancialSimulation.startSession(configuration = config, events = events))
        result = SimulationSessions.runUntilBlocked(SimulationSessions.submit(result, "S037", "stay"))
        assertEquals(48, result.currentMonth)
        assertEquals(Money.chf(6_000), result.history[40].flow.exceptionalExpenses)
        assertEquals(36, result.currentState.careerStartMonth)
        assertEquals(ScenarioCatalog.maintenance, events.last().definition)
    }
}
