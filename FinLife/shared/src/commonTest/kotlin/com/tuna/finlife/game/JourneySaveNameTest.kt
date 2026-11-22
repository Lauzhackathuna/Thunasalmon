package com.tuna.finlife.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JourneySaveNameTest {
    @Test
    fun roundTripsThePlayerNameAlongsideTheSameFinancialSession() {
        val data = StartingData.Default
        val session = AdventureRun.start(seed = 17, data = data, playerName = "Éloïse")
        val encoded = JourneySave.encode(data, session, null, false, "French", "Dark", "Éloïse")
        val restored = JourneySave.decode(encoded)

        assertEquals("Éloïse", restored.playerName)
        assertEquals("Éloïse", restored.session.profile.name)
        assertEquals(data, restored.data)
        assertEquals(session.currentMonth, restored.session.currentMonth)
        assertEquals(session.currentState.cash, restored.session.currentState.cash)
        assertFalse(restored.ended)
    }

    @Test
    fun supportsExistingVersionOneSavesByPromptingForAName() {
        val data = StartingData.Default
        val session = AdventureRun.start(seed = 17)
        val modern = JourneySave.encode(data, session, null, false, "English", "System", "Player")
        val legacyBody = modern.substringBeforeLast("\nHASH|").lines()
            .filterNot { it.startsWith("N|") }
            .map { if (it == "FINLIFE|3") "FINLIFE|1" else it }
            .joinToString("\n")
        val old = legacyBody + "\nHASH|" + checksum(legacyBody)
        val restored = JourneySave.decode(old)

        assertEquals("", restored.playerName)
        assertEquals(session.currentState.cash, restored.session.currentState.cash)
    }

    private fun checksum(value: String): String {
        var hash = 2166136261u
        value.forEach { hash = (hash xor it.code.toUInt()) * 16777619u }
        return hash.toString(16)
    }
}
