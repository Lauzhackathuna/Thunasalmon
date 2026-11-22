package com.tuna.finlife.simulation

private fun validateEvent(id: String, month: Int) { require(id.isNotBlank() && month >= 0) }

/** Definitions and numeric assumptions have separate provenance. No mutable payloads. */
sealed class LifeEvent {
    abstract val id: String
    abstract val month: Int
    abstract val interactive: Boolean
    open val scenarioId: ScenarioId? get() = null
    val definition: ScenarioDefinition? get() = scenarioId?.let { id -> ScenarioCatalog.active.single { it.id == id.name } }
    val definitionProvenance: DataProvenance get() = if (scenarioId != null)
        DataProvenance.CHALLENGE_SCENARIO_DEFINITION else DataProvenance.TEAM_ASSUMPTION
    open val numericProvenance: DataProvenance get() = DataProvenance.TEAM_ASSUMPTION

    data class Internship(
        override val id: String,
        override val month: Int,
        val durationMonths: Int = 12,
        val monthlyIncome: Money = Money.chf(2_500),
        override val interactive: Boolean = true,
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    ) : LifeEvent() {
        override val scenarioId: ScenarioId get() = ScenarioId.S005
        init { validateEvent(id, month); require(durationMonths in 1..MonthlyConfiguration.MAX_MONTHS); requireCost(monthlyIncome) }
    }

    /** Internal FinLife offer; additive, exactly three months, never suspends academic progress. */
    data class ShortInternshipOffer(
        override val id: String = "FINLIFE_INTERNSHIP_OFFER",
        override val month: Int,
        val startMonth: Int = month,
        val monthlyIncome: Money = Money.chf(300),
    ) : LifeEvent() {
        override val interactive: Boolean get() = true
        val title: String get() = "Une entreprise te propose un stage rémunéré pendant trois mois. Acceptes-tu ?"
        val durationMonths: Int get() = 3
        init { validateEvent(id, month); require(startMonth >= month && startMonth <= MonthlyConfiguration.MAX_MONTHS - 3); requireCost(monthlyIncome) }
    }

    data class RentRise(override val id: String, override val month: Int, val rule: MonthlyRentRule,
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION) : LifeEvent() {
        override val interactive: Boolean get() = true
        override val scenarioId: ScenarioId get() = ScenarioId.S037
        init { validateEvent(id, month); require(rule.startMonth == month) }
    }

    data class Maintenance(
        override val id: String,
        override val month: Int,
        val responsibility: PaymentResponsibility,
        val nominalAmount: Money = Money.chf(6_000),
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    ) : LifeEvent() {
        override val interactive: Boolean get() = false
        override val scenarioId: ScenarioId get() = ScenarioId.S040
        init { validateEvent(id, month); requireCost(nominalAmount) }
    }

    data class PurchaseChoice(
        override val id: String,
        override val month: Int,
        val title: String,
        val firstLabel: String,
        val firstCost: Money,
        val secondLabel: String,
        val secondCost: Money,
        val responsibility: PaymentResponsibility,
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    ) : LifeEvent() {
        override val interactive: Boolean get() = true
        init {
            validateEvent(id, month); require(title.isNotBlank() && firstLabel.isNotBlank() && secondLabel.isNotBlank())
            requireCost(firstCost); requireCost(secondCost)
        }
    }

    data class HousingChoice(
        override val id: String,
        override val month: Int,
        val newContract: HousingContract,
        val movingCost: Money,
        val responsibility: PaymentResponsibility,
        val newRentRule: MonthlyRentRule? = null,
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    ) : LifeEvent() {
        override val interactive: Boolean get() = true
        init {
            validateEvent(id, month); requireCost(movingCost)
            require(newContract.priceMonth == month)
            require(newRentRule == null || (newRentRule.contractId == newContract.id && newRentRule.startMonth >= month))
        }
    }

    /** Voluntary six-month unpaid break. Ordinary bills continue throughout the break. */
    data class CareerBreak(
        override val id: String,
        override val month: Int,
        val durationMonths: Int = 6,
    ) : LifeEvent() {
        override val interactive: Boolean get() = false
        init { validateEvent(id, month); require(durationMonths == 6) }
    }

    /** A fictional EPFL offer: the player chooses a lower, stable salary or their private job. */
    data class EpflJobOffer(
        override val id: String,
        override val month: Int,
        val monthlySalary: Money = Money.chf(4_500),
        val privateCompanyFailureProbability: Double = 0.30,
        val riskHorizonMonths: Int = 12,
    ) : LifeEvent() {
        override val interactive: Boolean get() = true
        init {
            validateEvent(id, month); requireCost(monthlySalary)
            require(privateCompanyFailureProbability.isFinite() && privateCompanyFailureProbability in 0.0..1.0)
            require(riskHorizonMonths in 1..12)
        }
    }

    /** Pre-drawn, deterministic company collapse following a "keep current job" choice. */
    data class CompanyClosure(
        override val id: String,
        override val month: Int,
        val offerId: String,
    ) : LifeEvent() {
        override val interactive: Boolean get() = false
        init { validateEvent(id, month); require(offerId.isNotBlank()) }
    }

    data class OneOffExpense(
        override val id: String,
        override val month: Int,
        val label: String,
        val amount: Money,
        val responsibility: PaymentResponsibility,
        override val numericProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    ) : LifeEvent() {
        override val interactive: Boolean get() = false
        init { validateEvent(id, month); require(label.isNotBlank()); requireCost(amount) }
    }

    /** Explicit financing only: no inferred repayment calendar or automatic loan. */
    data class Financing(
        override val id: String,
        override val month: Int,
        val borrowing: Money = Money.ZERO,
        val principalRepayment: Money = Money.ZERO,
        override val numericProvenance: DataProvenance = DataProvenance.USER_INPUT,
    ) : LifeEvent() {
        override val interactive: Boolean get() = false
        init { validateEvent(id, month); requireCost(borrowing); requireCost(principalRepayment) }
    }
}

object MicroEvents {
    fun plant(id: String, month: Int, cost: Money = Money.chf(35)) = LifeEvent.PurchaseChoice(
        id, month, "Acheter une plante", "Acheter", cost, "Ne pas acheter", Money.ZERO, PaymentResponsibility.CHARACTER)
    fun oven(id: String, month: Int, responsibility: PaymentResponsibility,
        repairCost: Money = Money.chf(450), replacementCost: Money = Money.chf(800)) = LifeEvent.PurchaseChoice(
        id, month, "Four en panne", "Réparer", repairCost, "Remplacer", replacementCost, responsibility)
    fun transport(id: String, month: Int, amount: Money, responsibility: PaymentResponsibility) =
        LifeEvent.OneOffExpense(id, month, "Transport ponctuel", amount, responsibility)
    fun equipment(id: String, month: Int, amount: Money, responsibility: PaymentResponsibility) =
        LifeEvent.OneOffExpense(id, month, "Remplacement d'un petit équipement", amount, responsibility)
}

data class DecisionOption(val id: String, val label: String, val immediateCost: Money)
data class DecisionSelection(val eventId: String, val optionId: String)
enum class EventApplication { APPLIED, DECLINED, NOT_APPLICABLE }
data class EventOutcome(
    val eventId: String,
    val application: EventApplication,
    val reason: String,
    val chargedCost: Money = Money.ZERO,
    val optionId: String? = null,
)

class PendingDecision internal constructor(val event: LifeEvent, options: List<DecisionOption>) {
    private val optionData = options.toList()
    val options: List<DecisionOption> get() = optionData.toList()
}

/** Own PRNG fixes the calendar algorithm across Kotlin platforms and library upgrades. */
object EventCalendar {
    private class Generator(seed: Long) {
        private var state: UInt = (seed xor (seed ushr 32)).toUInt().let { if (it == 0u) 0x6d2b79f5u else it }
        fun next(): UInt {
            state = state xor (state shl 13)
            state = state xor (state shr 17)
            state = state xor (state shl 5)
            return state
        }
        fun index(bound: Int): Int = (next() % bound.toUInt()).toInt()
        fun chance(probability: Double): Boolean = next().toDouble() / 4294967296.0 < probability
    }

    fun generate(profile: MonthlyProfile, configuration: MonthlyConfiguration, scheduled: List<LifeEvent> = emptyList()): List<LifeEvent> {
        validate(scheduled, configuration.horizonMonths)
        val calendar = scheduled.toMutableList()
        if (!configuration.randomEvents.enabled) return calendar.sortedWith(order)
        val random = Generator(configuration.randomSeed)
        val settings = configuration.randomEvents
        for (block in 0 until configuration.horizonMonths step 6) {
            val length = minOf(6, configuration.horizonMonths - block)
            for (slot in 0..1) {
                if (!random.chance(settings.opportunityProbability)) continue
                val month = block + random.index(length)
                // Keep empty months possible and at most two interactive months in ANY six-month window.
                if (calendar.any { it.month == month }) continue
                val candidate: LifeEvent = if (slot == 0) {
                    if (settings.ovenResponsibility != null && random.index(2) == 0)
                        MicroEvents.oven("random-$block-$slot", month, settings.ovenResponsibility)
                    else MicroEvents.plant("random-$block-$slot", month)
                } else if (settings.alternativeHousing != null) {
                    val home = settings.alternativeHousing.copy(id = "${settings.alternativeHousing.id}-$month", priceMonth = month)
                    LifeEvent.HousingChoice("random-$block-$slot", month, home, settings.movingCost, PaymentResponsibility.CHARACTER)
                } else if (month < profile.studyMonths) {
                    LifeEvent.Internship("random-$block-$slot", month)
                } else continue
                if (calendar.any { it.id == candidate.id }) continue
                val interactiveMonths = (calendar.filter { it.interactive }.map { it.month } + month).distinct()
                if (interactiveMonths.any { start -> interactiveMonths.count { it in start until start + 6 } > 2 }) continue
                calendar += candidate
            }
        }
        return calendar.sortedWith(order)
    }

    internal val order = compareBy<LifeEvent>({ it.month }, {
        when (it) { is LifeEvent.HousingChoice -> 0; is LifeEvent.RentRise -> 1; else -> 2 }
    }, { it.id })

    internal fun validate(events: List<LifeEvent>, horizon: Int) {
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate event ID" }
        require(events.all { it.month in 0 until horizon }) { "Event outside simulation horizon" }
        for (sameMonth in events.groupBy { it.month }.values) {
            require(sameMonth.count { it is LifeEvent.Internship } <= 1)
            require(sameMonth.count { it is LifeEvent.ShortInternshipOffer } <= 1)
            require(sameMonth.count { it is LifeEvent.HousingChoice } <= 1)
            require(sameMonth.count { it is LifeEvent.RentRise } <= 1)
            require(sameMonth.count { it is LifeEvent.EpflJobOffer } <= 1)
        }
    }
}
