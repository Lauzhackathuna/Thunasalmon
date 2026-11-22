package com.tuna.finlife.game

import com.tuna.finlife.simulation.MonthlyProfile
import com.tuna.finlife.simulation.MonthlyState

/**
 * A "what if this assumption were different?" test for the 12-month outlook: the rent of Maya's current
 * home and her monthly income from her parents, changed by whole percentages. Only the engine's inputs
 * change; the engine recomputes every month. The run itself is never modified.
 */
internal data class Sensitivity(val rentPercent: Int = 0, val incomePercent: Int = 0) {
    init {
        require(rentPercent in RANGE && incomePercent in RANGE)
    }

    val isNeutral: Boolean get() = rentPercent == 0 && incomePercent == 0

    fun apply(profile: MonthlyProfile): MonthlyProfile =
        if (incomePercent == 0) profile
        else profile.copy(studentMonthlyIncome = profile.studentMonthlyIncome.scaled(1.0 + incomePercent / 100.0))

    /** The current housing contract's rent; a move during the projection signs a new contract, unaffected. */
    fun apply(state: MonthlyState): MonthlyState =
        if (rentPercent == 0) state
        else state.copy(housing = state.housing.copy(monthlyRent = state.housing.monthlyRent.scaled(1.0 + rentPercent / 100.0)))

    companion object {
        val RANGE = -50..50
        const val STEP = 5
    }
}
