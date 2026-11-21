package com.tuna.finlife.model

// Synthetic CHF assumptions used by the fake twin and shown in the comparison sheet.
internal object DemoScenarios {
    const val FIRST_EFFECT_MONTH_INDEX = 3
    const val RENT_SURCHARGE_CHF = 56.0
    const val PROPERTY_BASELINE_CHF = 5_000.0
    const val PROPERTY_ALTERNATIVE_CHF = 3_500.0
    const val INTERNSHIP_INCOME_CHF = 2_400.0
}

internal fun ScenarioId.appliesTo(persona: Persona): Boolean = this == ScenarioId.S037 || persona == Persona.MastersStudent
