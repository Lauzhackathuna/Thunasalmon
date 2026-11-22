package com.tuna.finlife.game

import com.tuna.finlife.simulation.LifeEvent
import com.tuna.finlife.simulation.MonthlyRentRule
import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.PaymentResponsibility
import kotlin.random.Random

/**
 * Random problems drawn by the app (product rules, not engine rules):
 * each month, rent rises at probability 1/12 and maintenance at probability 1/12,
 * with at most one rent rise and two maintenance bills per 12-month block (months 0–11, 12–23, …).
 * Random maintenance uses a synthetic CHF 400 repair bill.
 */
internal object RandomTroubles {
    /** Each month: rent rises with probability 1 in [RENT_ODDS], maintenance with 1 in [MAINTENANCE_ODDS]. */
    const val RENT_ODDS = 12
    const val MAINTENANCE_ODDS = 12
    private const val MONTHLY_RENT_PROBABILITY = 1.0 / RENT_ODDS
    private const val MONTHLY_MAINTENANCE_PROBABILITY = 1.0 / MAINTENANCE_ODDS
    val MAINTENANCE_COST: Money = Money.chf(400)
    const val BLOCK_MONTHS = 12
    const val MAX_RENT_RISES_PER_BLOCK = 1
    const val MAX_MAINTENANCE_PER_BLOCK = 2
    private const val RENT_PREFIX = "random-rent-"
    private const val MAINTENANCE_PREFIX = "random-maintenance-"

    /** Deterministic in ([seed], [month]); drawing the same month twice gives the same events. */
    fun draw(seed: Long, month: Int, housingId: String, calendar: List<LifeEvent>): List<LifeEvent> {
        val random = Random(seed xor (month.toLong() * -0x61c8864680b583ebL))
        val rentRoll = random.nextDouble()
        val maintenanceRoll = random.nextDouble()
        return decide(rentRoll, maintenanceRoll, month, housingId, calendar)
    }

    /** Rolls are uniform in [0, 1). Each event uses its own threshold. */
    fun decide(
        rentRoll: Double,
        maintenanceRoll: Double,
        month: Int,
        housingId: String,
        calendar: List<LifeEvent>,
    ): List<LifeEvent> {
        val block = month / BLOCK_MONTHS
        val inBlock = calendar.filter { it.month / BLOCK_MONTHS == block }
        val ids = calendar.map { it.id }.toSet()
        return buildList {
            val rentId = "$RENT_PREFIX$month"
            if (rentRoll < MONTHLY_RENT_PROBABILITY && rentId !in ids &&
                inBlock.count { it.id.startsWith(RENT_PREFIX) } < MAX_RENT_RISES_PER_BLOCK
            ) add(LifeEvent.RentRise(rentId, month, MonthlyRentRule(housingId, month)))
            val maintenanceId = "$MAINTENANCE_PREFIX$month"
            if (maintenanceRoll < MONTHLY_MAINTENANCE_PROBABILITY && maintenanceId !in ids &&
                inBlock.count { it.id.startsWith(MAINTENANCE_PREFIX) } < MAX_MAINTENANCE_PER_BLOCK
            ) add(LifeEvent.OneOffExpense(maintenanceId, month, "Minor household repair", MAINTENANCE_COST, PaymentResponsibility.CHARACTER))
        }
    }

    fun isRandom(event: LifeEvent): Boolean = event.id.startsWith(RENT_PREFIX) || event.id.startsWith(MAINTENANCE_PREFIX)
}
