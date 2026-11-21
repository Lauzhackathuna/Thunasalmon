package com.tuna.finlife.simulation

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** CHF cents. Bound keeps additions/scaling checked and below Double's integer precision limit. */
data class Money(val cents: Long) : Comparable<Money> {
    init { require(cents in -MAX_CENTS..MAX_CENTS) { "Money exceeds supported range" } }
    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun unaryMinus() = Money(-cents)
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)
    /** Rates use Double; monetary results are rounded once to cents, ties away from zero. */
    fun scaled(factor: Double): Money {
        require(factor.isFinite())
        val value = cents.toDouble() * factor
        require(value.isFinite() && abs(value) <= MAX_CENTS.toDouble())
        // Compensate sub-nanocent representation noise at decimal ties (e.g. 100 * 1.005).
        // This tolerance is 1e-11 CHF, not a currency rounding or an extra fee.
        val rounded = floor(abs(value) + 0.5 + 1e-9).toLong()
        return Money(if (value < 0) -rounded else rounded)
    }
    override fun toString(): String {
        val magnitude = abs(cents)
        return "${if (cents < 0) "-" else ""}${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')} CHF"
    }
    companion object {
        const val MAX_CENTS: Long = 9_000_000_000_000L
        val ZERO = Money(0)
        fun chf(whole: Long): Money {
            require(whole in -(MAX_CENTS / 100)..(MAX_CENTS / 100))
            return Money(whole * 100)
        }
    }
}

internal fun Iterable<Money>.total(): Money = fold(Money.ZERO) { a, b -> a + b }
internal fun Money.nonNegative(): Money = maxOf(this, Money.ZERO)
internal fun requireCost(value: Money) { require(value >= Money.ZERO) }
internal fun validateRate(rate: Double, minimum: Double = -1.0) {
    require(rate.isFinite() && rate > minimum && rate <= 1.0) { "Annual rate must be finite, > $minimum and <= 1" }
}

enum class DataProvenance {
    HISTORICAL_SYNTHETIC_DATA, USER_INPUT, TEAM_ASSUMPTION, CHALLENGE_SCENARIO_DEFINITION, SIMULATED_RESULT
}

data class SourcedValue<T>(val value: T, val provenance: DataProvenance)
/** Historical rows are separate data, never automatically fed into simulated cash flows. */
data class HistoricalMonthlyObservation(
    val profileId: String,
    val period: String,
    val cash: Money?,
    val income: Money?,
    val expenses: Money?,
    val provenance: DataProvenance = DataProvenance.HISTORICAL_SYNTHETIC_DATA,
) {
    init { require(provenance == DataProvenance.HISTORICAL_SYNTHETIC_DATA) }
}

enum class HousingType { UNKNOWN, ALONE, SHARED, OTHER }
enum class PaymentResponsibility { CHARACTER, OTHER_PARTY }
enum class LifeStatus { STUDENT, INTERN, EMPLOYED }
enum class InvestmentProfile(val annualReturn: Double) { PRUDENT(0.01), BALANCED(0.03), DYNAMIC(0.05) }

data class FinancialGoal(val nominalAmount: Money = Money.chf(350_000), val dueMonth: Int = 120) {
    init { require(nominalAmount > Money.ZERO); require(dueMonth > 0) }
}

/** Rent is nominal at priceMonth; inflation thereafter is relative to that date. */
data class HousingContract(
    val id: String,
    val monthlyRent: Money,
    val priceMonth: Int = 0,
    val type: HousingType = HousingType.UNKNOWN,
) {
    init { require(id.isNotBlank()); requireCost(monthlyRent); require(priceMonth >= 0) }
}

/** No defaults: a future UBS adapter must supply missing fields explicitly. Use alexia() for the demo. */
data class MonthlyProfile(
    val profileId: String,
    val name: String,
    val initialAge: Int,
    val city: String,
    val cash: Money,
    val investments: Money,
    val debt: Money,
    val studentMonthlyIncome: Money,
    val startingMonthlySalary: Money,
    val housing: HousingContract,
    val healthInsurance: Money,
    val groceries: Money,
    val otherExpenses: Money,
    val studyMonths: Int,
    val tuitionPayment: Money,
    val tuitionEveryStudyMonths: Int,
    val provenance: DataProvenance,
    val initialEnvelopes: EnvelopeBalances? = null,
) {
    val currency: String get() = "CHF"
    init {
        require(profileId.isNotBlank() && name.isNotBlank())
        require(initialAge in 0..120 && studyMonths in 0..MonthlyConfiguration.MAX_MONTHS)
        require(tuitionEveryStudyMonths > 0 && housing.priceMonth == 0)
        requireCost(cash)
        require(initialEnvelopes == null || initialEnvelopes.total == cash)
        listOf(investments, debt, studentMonthlyIncome, startingMonthlySalary, healthInsurance,
            groceries, otherExpenses, tuitionPayment).forEach(::requireCost)
    }
    companion object {
        fun alexia() = MonthlyProfile("finlife-alexia", "Alexia", 20, "Lausanne",
            Money.chf(8_000), Money.ZERO, Money.ZERO, Money.chf(1_200), Money.chf(6_000),
            HousingContract("initial-home", Money.chf(1000), type = HousingType.ALONE), Money.chf(250), Money.chf(250),
            Money.chf(300), 24, Money.chf(800), 6, DataProvenance.TEAM_ASSUMPTION)
    }
}

data class RandomEventSettings(
    val enabled: Boolean = true,
    val opportunityProbability: Double = 0.4,
    val ovenResponsibility: PaymentResponsibility? = null,
    val alternativeHousing: HousingContract? = null,
    val movingCost: Money = Money.chf(500),
) {
    init {
        require(opportunityProbability.isFinite() && opportunityProbability in 0.0..1.0)
        requireCost(movingCost)
    }
}

data class MonthlyConfiguration(
    val horizonMonths: Int = 120,
    val annualInflation: Double = 0.02,
    val annualSalaryGrowth: Double = 0.02,
    val annualInvestmentReturn: Double = InvestmentProfile.BALANCED.annualReturn,
    val annualDebtInterest: Double = 0.0,
    val investmentShare: Double = 0.0,
    val goal: FinancialGoal = FinancialGoal(),
    val randomSeed: Long = 1L,
    val randomEvents: RandomEventSettings = RandomEventSettings(),
    val assumptionsProvenance: DataProvenance = DataProvenance.TEAM_ASSUMPTION,
    val sharedReferenceRent: Money = Money.chf(800),
    val sharedMovingCost: Money = Money.ZERO,
) {
    init {
        requireCost(sharedReferenceRent); requireCost(sharedMovingCost)
        require(horizonMonths in 1..MAX_MONTHS)
        validateRate(annualInflation); validateRate(annualSalaryGrowth); validateRate(annualInvestmentReturn)
        require(annualDebtInterest.isFinite() && annualDebtInterest in 0.0..1.0)
        require(investmentShare.isFinite() && investmentShare in 0.0..1.0)
    }
    fun priceIndex(month: Int): Double {
        require(month >= 0)
        return (1.0 + annualInflation).pow(month / 12.0).also { require(it.isFinite() && it > 0) }
    }
    companion object { const val MAX_MONTHS = 1_200 }
}

data class MonthlyRentRule(val contractId: String, val startMonth: Int, val annualExtraRate: Double = 0.03) {
    init {
        require(contractId.isNotBlank() && startMonth >= 0)
        require(annualExtraRate.isFinite() && annualExtraRate in 0.0..1.0)
    }
}

/** State m is the opening of month m. All fields needed for a standalone transition are explicit. */
data class MonthlyState(
    val month: Int,
    val cash: Money,
    val investments: Money,
    val debt: Money,
    val realNetWorth: Money,
    val studyMonthsCompleted: Int,
    val studyMonthsRemaining: Int,
    val lastPaidTuitionProgress: Int,
    val internshipMonthsRemaining: Int,
    val internshipMonthlyIncome: Money,
    val careerStartMonth: Int?,
    val housing: HousingContract,
    val rentRule: MonthlyRentRule?,
    val envelopes: EnvelopeBalances = EnvelopeBalances(reserve = cash),
    val unpaid: UnpaidObligations = UnpaidObligations(),
    val shortInternship: ShortInternship? = null,
    val housingDecision: HousingDecision? = null,
) {
    /** Legacy lastPaidTuitionProgress now tracks the last assessed deadline; payment lives in unpaid/ledger. */
    val lastAssessedTuitionProgress: Int get() = lastPaidTuitionProgress
    val accountsPayable: Money get() = unpaid.items.filter { !it.bankPrincipal }.map { it.missing }.total()
    val theoreticalCash: Money get() = cash - unpaid.items.map { it.missing }.total()
    val netWorth: Money get() = cash + investments - debt - accountsPayable
    val status: LifeStatus get() = when {
        internshipMonthsRemaining > 0 -> LifeStatus.INTERN
        studyMonthsRemaining > 0 -> LifeStatus.STUDENT
        else -> LifeStatus.EMPLOYED
    }
    init {
        requireCost(cash); require(envelopes.total == cash)
        require(month >= 0 && studyMonthsCompleted >= 0 && studyMonthsRemaining >= 0)
        require(lastPaidTuitionProgress in -1..studyMonthsCompleted)
        require(internshipMonthsRemaining >= 0)
        require(internshipMonthsRemaining == 0 || studyMonthsRemaining > 0)
        require(careerStartMonth == null || careerStartMonth in 0..month)
        require((studyMonthsRemaining == 0) == (careerStartMonth != null))
        requireCost(investments); requireCost(debt); requireCost(internshipMonthlyIncome)
    }
}

data class MonthlyFlow(
    val studentIncome: Money = Money.ZERO,
    val internshipIncome: Money = Money.ZERO,
    val professionalIncome: Money = Money.ZERO,
    val rent: Money = Money.ZERO,
    val healthInsurance: Money = Money.ZERO,
    val groceries: Money = Money.ZERO,
    val otherExpenses: Money = Money.ZERO,
    val tuition: Money = Money.ZERO,
    val exceptionalExpenses: Money = Money.ZERO,
    val decisionCosts: Money = Money.ZERO,
    val interest: Money = Money.ZERO,
    val newBorrowing: Money = Money.ZERO,
    val principalRepayment: Money = Money.ZERO,
    val investmentContribution: Money = Money.ZERO,
    val investmentReturn: Money = Money.ZERO,
    /** Actual cash payments, including older arrears. expenses remains this month's accrued expense. */
    val paidExpenses: Money = rent + healthInsurance + groceries + otherExpenses + tuition + exceptionalExpenses + decisionCosts + interest,
) {
    val income: Money get() = studentIncome + internshipIncome + professionalIncome
    val recurringExpenses: Money get() = rent + healthInsurance + groceries + otherExpenses
    val expenses: Money get() = recurringExpenses + tuition + exceptionalExpenses + decisionCosts + interest
    val netCashFlow: Money get() = income + newBorrowing - paidExpenses - principalRepayment
    val netWorthChange: Money get() = income - expenses + investmentReturn
}
