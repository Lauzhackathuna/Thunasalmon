package com.tuna.finlife.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerNameTest {
    @Test
    fun acceptsAndNormalizesInternationalFirstNames() {
        assertEquals("Taïky", PlayerName.normalize("  Taïky  "))
        assertEquals("Anne-Marie", PlayerName.normalize("Anne-Marie"))
        assertEquals("O’Neil", PlayerName.normalize("O’Neil"))
        assertEquals("Jean Luc", PlayerName.normalize("Jean   Luc"))
        assertEquals("李", PlayerName.normalize("李"))
    }

    @Test
    fun refusesInvalidOrTooLongNames() {
        assertNull(PlayerName.normalize(""))
        assertNull(PlayerName.normalize("   "))
        assertNull(PlayerName.normalize("42"))
        assertNull(PlayerName.normalize("Alex|FINLIFE"))
        assertNull(PlayerName.normalize("A".repeat(PlayerName.MAX_LENGTH + 1)))
    }
}
