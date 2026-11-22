package com.tuna.finlife.game

import com.tuna.finlife.simulation.*
import kotlin.test.*

class DemoJourneyTest {
    @Test fun demoIsReproducibleAndRoundTripsAtThePendingOffer() {
        val first = DemoJourney.atEpflOffer("Taïky")
        val second = DemoJourney.atEpflOffer("Taïky")
        assertEquals(27, first.currentMonth)
        assertEquals(first.states, second.states)
        assertEquals(first.history.map { it.flow }, second.history.map { it.flow })
        assertEquals(first.calendar, second.calendar)
        assertEquals("Taïky", first.profile.name)
        assertIs<LifeEvent.EpflJobOffer>(SimulationSessions.pendingDecisions(first).single().event)
        val restored = JourneySave.decode(JourneySave.encode(StartingData.Default, first, null, false,
            "French", "System", "Taïky")).session
        assertEquals(first.currentState, restored.currentState)
        assertEquals(first.selections, restored.selections)
        val offer = SimulationSessions.pendingDecisions(restored).single().event
        for (choice in listOf("stay", "epfl")) {
            val originalNext = AdventureRun.choose(first, offer.id, choice)
            val restoredNext = AdventureRun.choose(restored, offer.id, choice)
            assertEquals(originalNext.currentState, restoredNext.currentState)
            assertEquals(originalNext.calendar, restoredNext.calendar)
        }
    }

    @Test fun projectionsKeepKnownClosuresButDoNotDrawFutureOnes() {
        val offered = DemoJourney.atEpflOffer("Alex")
        val offer = SimulationSessions.pendingDecisions(offered).single().event
        val privateJob = requireNotNull(AdventureRun.commitDecision(offered, offer.id, "stay"))
        val noFailure = FinancialProjections.nothingChanges(privateJob)
        assertTrue(noFailure.records.all { it.flow.professionalIncome == Money.chf(6000) })
        val knownClosure = SimulationSessions.schedule(privateJob,
            LifeEvent.CompanyClosure("known-closure", privateJob.currentMonth, offer.id))
        val projected = FinancialProjections.nothingChanges(knownClosure)
        assertEquals(Money.ZERO, projected.records.first().flow.professionalIncome)
        val outlook = AdventureOutlook.calculate(knownClosure, StartingData.Default.internshipSalary)
        assertTrue(outlook.baseline.projection.records.all { it.flow.professionalIncome == Money.ZERO })
        assertFalse(projected.finalState.cash < Money.ZERO)
    }
}
