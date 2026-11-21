package com.tuna.finlife.twin

import com.tuna.finlife.model.GoalStatus
import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.MonthOutcome
import com.tuna.finlife.model.Persona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeTwinEngineTest {
    private val engine = FakeTwinEngine()

    @Test
    fun eachPersonaReplaysTheSameTwelveMonths() {
        for (persona in Persona.entries) {
            val firstRun = runYear(persona)
            assertEquals(firstRun, runYear(persona))
            assertEquals((0..11).toList(), firstRun.map { it.snapshot.monthIndex })
        }
    }

    @Test
    fun studentPaysTuitionInMonthsOneAndSeven() {
        val persona = Persona.MastersStudent
        val opening = engine.initialSnapshot(persona)
        val months = runYear(persona)

        assertEquals(-1, opening.monthIndex)
        assertEquals(8_000.0, opening.cash)
        assertEquals(1_200.0, months.first().income)
        assertEquals(2_280.0, months.first().expenses)
        assertEquals(6_920.0, months.first().snapshot.cash)
        assertEquals(6_920.0 / 1_480.0, months.first().snapshot.liquidityMonths)
        assertEquals(1_480.0, months[1].expenses)
        assertEquals(listOf(0, 6), months.filter { LifeEvent.TuitionDue in it.events }.map { it.snapshot.monthIndex })
        assertEquals(3_040.0, months.last().snapshot.cash)
        assertEquals(GoalStatus.OnTrack, months.last().snapshot.goalFeasibility)
    }

    @Test
    fun workerStartsReceivingPayAndSendingRemittancesInMonthTwo() {
        val months = runYear(Persona.NewlyArrivedWorker)

        assertEquals(0.0, months[0].income)
        assertEquals(4_200.0, months[0].expenses)
        assertEquals(listOf(LifeEvent.ArrivalCosts), months[0].events)
        assertEquals(GoalStatus.AtRisk, months[0].snapshot.goalFeasibility)
        assertEquals(3_700.0, months[1].income)
        assertEquals(2_700.0, months[1].expenses)
        assertEquals(3_300.0, months[1].snapshot.cash)
        assertTrue(months[1].events.isEmpty())
    }

    @Test
    fun apprenticeIncomeIncludesFamilySupport() {
        val months = runYear(Persona.Apprentice)

        assertEquals(2_200.0, months[0].income)
        assertEquals(2_800.0, months[0].expenses)
        assertEquals(3_900.0, months[0].snapshot.cash)
        assertEquals(4_200.0, months[1].snapshot.cash)
    }

    @Test
    fun spendingReducesCashAndCanProduceAnUncoveredShortfall() {
        val persona = Persona.NewlyArrivedWorker
        val opening = engine.initialSnapshot(persona)
        val outcome = engine.advanceMonth(persona, opening, 2_600.0, emptySet())

        assertEquals(4_200.0, outcome.expenses)
        assertEquals(-300.0, outcome.snapshot.cash)
        assertEquals(outcome.snapshot.cash, outcome.snapshot.netWorth)
        assertEquals(-300.0 / 2_400.0, outcome.snapshot.liquidityMonths)
        assertEquals(GoalStatus.OffTrack, outcome.snapshot.goalFeasibility)
        assertEquals(1, outcome.snapshot.constraintBreaches)
    }

    private fun runYear(persona: Persona): List<MonthOutcome> {
        var snapshot = engine.initialSnapshot(persona)
        return List(12) {
            engine.advanceMonth(persona, snapshot, 0.0, emptySet())
                .also { snapshot = it.snapshot }
        }
    }
}
