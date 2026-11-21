package com.tuna.finlife.game

import com.tuna.finlife.simulation.HousingContract
import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.MicroEvents
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.PaymentResponsibility
import com.tuna.finlife.simulation.RandomEventSettings
import com.tuna.finlife.simulation.SessionStatus
import com.tuna.finlife.simulation.SimulationSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class EventKindTest {
    @Test
    fun everyEngineEventMapsToItsKindWithEngineValues() {
        val character = PaymentResponsibility.CHARACTER
        assertEquals(EventKind.Internship(12, Money.chf(2_500)), LifeEvent.Internship("i", 0).kind())
        assertEquals(
            EventKind.RentRise(0.03, "home"),
            LifeEvent.RentRise("r", 2, MonthlyRentRule("home", 2)).kind(),
        )
        assertEquals(EventKind.Maintenance(Money.chf(6_000)), LifeEvent.Maintenance("m", 0, character).kind())
        assertEquals(EventKind.Plant(Money.chf(35)), MicroEvents.plant("p", 0).kind())
        assertEquals(EventKind.Oven(Money.chf(450), Money.chf(800)), MicroEvents.oven("o", 0, character).kind())
        assertEquals(
            EventKind.Purchase("Title", "A", "B"),
            LifeEvent.PurchaseChoice("c", 0, "Title", "A", Money.ZERO, "B", Money.ZERO, character).kind(),
        )
        assertEquals(
            EventKind.Housing(Money.chf(500), Money.chf(300)),
            LifeEvent.HousingChoice("h", 1, HousingContract("flat", Money.chf(500), 1), Money.chf(300), character).kind(),
        )
        assertEquals(
            EventKind.OneOffExpense(MicroEvents.transport("t", 0, Money.chf(40), character).label, Money.chf(40)),
            MicroEvents.transport("t", 0, Money.chf(40), character).kind(),
        )
        assertEquals(
            EventKind.Financing(Money.chf(100), Money.ZERO),
            LifeEvent.Financing("f", 0, borrowing = Money.chf(100)).kind(),
        )
    }

    @Test
    fun percentLabelsDropTrailingZeroes() {
        assertEquals("3", percentLabel(0.03))
        assertEquals("8", percentLabel(0.08))
        assertEquals("2.5", percentLabel(0.025))
        assertEquals("0", percentLabel(0.0))
    }

    @Test
    fun genericChoiceWaitsUntilEveryDecisionOfTheMonthIsAnswered() {
        val session = SimulationSessions.runUntilBlocked(
            SimulationSessions.start(
                configuration = MonthlyConfiguration(horizonMonths = 12, randomEvents = RandomEventSettings(enabled = false)),
                events = listOf(MicroEvents.plant("plant", 1), LifeEvent.Internship("internship", 1)),
            ),
        )
        assertEquals(SessionStatus.AWAITING_DECISION, session.status)
        assertNull(AdventureRun.preview(session, "plant", "first"))
        assertSame(session, AdventureRun.choose(session, "plant", "unknown"))
        assertSame(session, AdventureRun.choose(session, "missing", "first"))

        val halfway = AdventureRun.choose(session, "plant", "first")
        assertEquals(1, halfway.currentMonth)
        assertEquals(SessionStatus.AWAITING_DECISION, halfway.status)

        val preview = assertNotNull(AdventureRun.preview(halfway, "internship", "accept"))
        val done = AdventureRun.choose(halfway, "internship", "accept")
        assertEquals(2, done.currentMonth)
        assertEquals(preview.after, done.currentState)
        assertEquals(Money.chf(35), done.history.last().flow.decisionCosts)
    }
}
