package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.MicroEvents
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.PaymentResponsibility

/**
 * What the UI needs to describe an engine event. Values are read from the engine's own event;
 * nothing here adds amounts, rates or rules.
 */
internal sealed interface EventKind {
    data class Internship(val months: Int, val monthlyIncome: Money) : EventKind
    data class RentRise(val annualExtraRate: Double, val contractId: String) : EventKind
    data class Maintenance(val amount: Money) : EventKind
    data class Plant(val cost: Money) : EventKind
    data class Oven(val repairCost: Money, val replacementCost: Money) : EventKind
    /** A purchase choice the app has no dedicated wording for: shows the engine's own labels. */
    data class Purchase(val title: String, val firstLabel: String, val secondLabel: String) : EventKind
    data class Housing(val newRent: Money, val movingCost: Money) : EventKind
    data class OneOffExpense(val label: String, val amount: Money) : EventKind
    data class Financing(val borrowing: Money, val principalRepayment: Money) : EventKind
}

// The engine distinguishes its plant and oven micro-events only by title, so compare with its factories.
private val plantTitle = MicroEvents.plant("probe", 0).title
private val ovenTitle = MicroEvents.oven("probe", 0, PaymentResponsibility.CHARACTER).title

internal fun LifeEvent.kind(): EventKind = when (this) {
    is LifeEvent.Internship -> EventKind.Internship(durationMonths, monthlyIncome)
    is LifeEvent.RentRise -> EventKind.RentRise(rule.annualExtraRate, rule.contractId)
    is LifeEvent.Maintenance -> EventKind.Maintenance(nominalAmount)
    is LifeEvent.PurchaseChoice -> when (title) {
        plantTitle -> EventKind.Plant(firstCost)
        ovenTitle -> EventKind.Oven(firstCost, secondCost)
        else -> EventKind.Purchase(title, firstLabel, secondLabel)
    }
    is LifeEvent.HousingChoice -> EventKind.Housing(newContract.monthlyRent, movingCost)
    is LifeEvent.OneOffExpense -> EventKind.OneOffExpense(label, amount)
    is LifeEvent.Financing -> EventKind.Financing(borrowing, principalRepayment)
}

/** Whole percentages without decimals (0.03 -> "3"), otherwise one decimal (0.025 -> "2.5"). */
internal fun percentLabel(rate: Double): String {
    val tenths = kotlin.math.round(rate * 1000).toLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}
