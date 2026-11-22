package com.tuna.finlife.assistant

import com.tuna.finlife.game.percentLabel

/**
 * English versions of the five assumptions the engine lists (in French) with every 12-month projection
 * (FinancialProjections.project). Kept in the same order; a test fails if the engine's list changes length.
 */
internal object ProjectionAssumptions {
    const val COUNT = 5

    fun english(annualInflation: Double): List<String> = listOf(
        "Months simulated one by one by the engine; prices indexed at ${percentLabel(annualInflation)}% inflation per year, applied monthly.",
        "No random events; only events already known are included.",
        "Without an explicit choice: the housing is kept, new offers are declined, and optional purchases are left out.",
        "Commitments already made, study deadlines and unpaid bills carry over; there is no automatic loan.",
        "Bills due and payments made are kept apart; balance = cash on the account minus unpaid bills.",
    )
}
