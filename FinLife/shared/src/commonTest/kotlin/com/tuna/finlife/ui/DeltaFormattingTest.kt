package com.tuna.finlife.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class DeltaFormattingTest {
    private val colors = DeltaColors(gain = Color.Green, loss = Color.Red, neutral = Color.Gray)

    @Test
    fun gainsHaveAnUpArrowPlusSignAndGainColor() {
        val direction = DeltaDirection.fromAmount(250.0)
        assertEquals("▲ +CHF 250", formatDelta("CHF 250", direction))
        assertEquals(colors.gain, colors.colorFor(direction))
    }

    @Test
    fun lossesHaveADownArrowMinusSignAndLossColor() {
        val direction = DeltaDirection.fromAmount(-250.0)
        assertEquals("▼ −250 CHF", formatDelta("250 CHF", direction))
        assertEquals(colors.loss, colors.colorFor(direction))
    }

    @Test
    fun noChangeIsUnsignedAndNeutral() {
        val direction = DeltaDirection.fromAmount(0.0)
        assertEquals("CHF 0", formatDelta("CHF 0", direction))
        assertEquals(colors.neutral, colors.colorFor(direction))
    }
}
