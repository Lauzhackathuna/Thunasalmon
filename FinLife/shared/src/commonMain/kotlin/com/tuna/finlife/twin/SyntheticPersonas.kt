package com.tuna.finlife.twin

import com.tuna.finlife.model.DEMO_HORIZON_MONTHS
import com.tuna.finlife.model.LifeEvent
import com.tuna.finlife.model.Persona

internal data class SyntheticProfile(
    val openingCashChf: Double,
    val horizonMonths: Int = DEMO_HORIZON_MONTHS,
)

internal data class Phase(
    val startMonth: Int,
    val endMonth: Int,
    val monthlyIncomeChf: Double,
    val monthlyExpensesChf: Double,
)

internal data class OneOffCost(val month: Int, val amountChf: Double, val event: LifeEvent)

internal data class SyntheticScenario(val phases: List<Phase>, val oneOffCosts: List<OneOffCost>)

internal data class SyntheticPersona(val profile: SyntheticProfile, val scenario: SyntheticScenario)

// All amounts are synthetic demo assumptions in CHF. Months are zero-indexed; phase ends are exclusive.
private val Student = run {
    val rent = 700.0
    val healthInsurance = 250.0
    val groceries = 230.0
    val otherExpenses = 300.0
    val monthlyExpenses = rent + healthInsurance + groceries + otherExpenses
    val tuition = 800.0
    SyntheticPersona(
        profile = SyntheticProfile(openingCashChf = 8_000.0),
        scenario = SyntheticScenario(
            phases = listOf(Phase(0, DEMO_HORIZON_MONTHS, 1_200.0, monthlyExpenses)),
            oneOffCosts = listOf(
                OneOffCost(0, tuition, LifeEvent.TuitionDue),
                OneOffCost(6, tuition, LifeEvent.TuitionDue),
            ),
        ),
    )
}

private val Worker = run {
    val livingCosts = 2_400.0
    val remittances = 300.0
    SyntheticPersona(
        profile = SyntheticProfile(openingCashChf = 6_500.0),
        scenario = SyntheticScenario(
            phases = listOf(
                Phase(0, 1, 0.0, livingCosts),
                Phase(1, DEMO_HORIZON_MONTHS, 3_700.0, livingCosts + remittances),
            ),
            oneOffCosts = listOf(OneOffCost(0, 1_800.0, LifeEvent.ArrivalCosts)),
        ),
    )
}

private val Apprentice = run {
    val wages = 1_900.0
    val familySupport = 300.0
    SyntheticPersona(
        profile = SyntheticProfile(openingCashChf = 4_500.0),
        scenario = SyntheticScenario(
            phases = listOf(Phase(0, DEMO_HORIZON_MONTHS, wages + familySupport, 1_900.0)),
            oneOffCosts = listOf(OneOffCost(0, 900.0, LifeEvent.ArrivalCosts)),
        ),
    )
}

internal fun syntheticPersona(persona: Persona): SyntheticPersona = when (persona) {
    Persona.MastersStudent -> Student
    Persona.NewlyArrivedWorker -> Worker
    Persona.Apprentice -> Apprentice
}
