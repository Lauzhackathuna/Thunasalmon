package com.tuna.finlife.game

import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.Persona
import com.tuna.finlife.model.ScenarioId
import com.tuna.finlife.twin.FakeTwinEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ScenarioComparisonTest {
    private val twin = FakeTwinEngine()
    private val persona = Persona.MastersStudent
    private val opening = GameState.Playing(persona, twin.initialSnapshot(persona))

    @Test
    fun openingComparisonProjectsMonthFourWithoutChangingTheRun() {
        val comparison = assertNotNull(compareScenario(opening, ScenarioId.S037, twin))
        assertEquals(3, comparison.baseline.monthIndex)
        assertEquals(6_080.0, comparison.baseline.cash)
        assertEquals(6_024.0, comparison.alternative.cash)
        assertEquals(6_024.0 / 1_536.0, comparison.alternative.liquidityMonths)
        assertEquals(8_000.0, opening.snapshot.cash)
        assertNull(opening.lastMonth)
    }

    @Test
    fun propertyDeclineOnlyChangesNetWorth() {
        val comparison = assertNotNull(compareScenario(opening, ScenarioId.S039, twin))
        assertEquals(13_000.0, opening.snapshot.netWorth)
        assertEquals(11_080.0, comparison.baseline.netWorth)
        assertEquals(9_580.0, comparison.alternative.netWorth)
        assertEquals(comparison.baseline.copy(netWorth = comparison.alternative.netWorth), comparison.alternative)
    }

    @Test
    fun paidInternshipChangesIncomeWithRecurringExpensesUnchanged() {
        val monthThree = advance(opening, months = 3)
        val baseline = advance(monthThree)
        val alternative = advance(toggle(monthThree, ScenarioId.S005, true))

        assertEquals(1_200.0, baseline.lastMonth?.income)
        assertEquals(2_400.0, alternative.lastMonth?.income)
        assertEquals(baseline.lastMonth?.expenses, alternative.lastMonth?.expenses)
        assertEquals(7_280.0, alternative.snapshot.cash)
        assertEquals(listOf(LifeEvent.PaidInternship), alternative.lastMonth?.events)
    }

    @Test
    fun togglingRentChangesFutureMonthsWithoutRecalculatingClosedMonths() {
        val enabled = toggle(opening, ScenarioId.S037, true)
        val monthThree = advance(enabled, months = 3)
        assertEquals(advance(opening, months = 3).snapshot, monthThree.snapshot)
        val monthFour = advance(monthThree)
        assertEquals(1_536.0, monthFour.lastMonth?.expenses)
        assertEquals(listOf(LifeEvent.HigherRent), monthFour.lastMonth?.events)

        val disabled = toggle(monthFour, ScenarioId.S037, false)
        assertSame(monthFour.snapshot, disabled.snapshot)
        assertSame(monthFour.lastMonth, disabled.lastMonth)
        val monthFive = advance(disabled)
        assertEquals(1_480.0, monthFive.lastMonth?.expenses)
        assertEquals(5_744.0, monthFive.snapshot.cash)
    }

    @Test
    fun comparisonHoldsOtherTogglesFixedAndPredictsTheAppliedResult() {
        var state = toggle(opening, ScenarioId.S037, true)
        state = toggle(state, ScenarioId.S005, true)
        state = toggle(state, ScenarioId.S039, true)

        val comparison = assertNotNull(compareScenario(state, ScenarioId.S039, twin))
        assertEquals(7_224.0, comparison.baseline.cash)
        assertEquals(comparison.baseline.cash, comparison.alternative.cash)
        val monthFour = advance(state, months = 4)
        assertEquals(comparison.alternative, monthFour.snapshot)
        assertEquals(
            listOf(LifeEvent.HigherRent, LifeEvent.LowerPropertyValue, LifeEvent.PaidInternship),
            monthFour.lastMonth?.events,
        )
    }

    @Test
    fun finalMonthAndIneligiblePersonasCannotToggleTheScenario() {
        val final = advance(opening, months = 12)
        assertSame(final, toggle(final, ScenarioId.S037, true))
        assertNull(compareScenario(final, ScenarioId.S037, twin))

        val worker = Persona.NewlyArrivedWorker
        val workerState = GameState.Playing(worker, twin.initialSnapshot(worker))
        assertSame(workerState, toggle(workerState, ScenarioId.S039, true))
        assertNull(compareScenario(workerState, ScenarioId.S039, twin))
    }

    private fun toggle(state: GameState.Playing, scenario: ScenarioId, enabled: Boolean): GameState.Playing =
        assertIs(reduceGame(state, GameAction.SetScenario(scenario, enabled), twin))

    private fun advance(state: GameState.Playing, months: Int = 1): GameState.Playing {
        var current = state
        repeat(months) { current = assertIs(reduceGame(current, GameAction.AdvanceMonth, twin)) }
        return current
    }
}
