package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration

/** Player-editable starting data, in whole CHF. [Default] holds the previous fixed demo values. */
internal data class StartingData(
    val openingCash: Long,
    val parentIncome: Long,
    val rent: Long,
    val healthInsurance: Long,
    val groceries: Long,
    val otherExpenses: Long,
    val tuition: Long,
    val salaryAfterStudies: Long,
    val sharedFlatRent: Long,
    val sharedFlatMovingCost: Long,
) {
    operator fun get(field: StartingField): Long = when (field) {
        StartingField.OpeningCash -> openingCash
        StartingField.ParentIncome -> parentIncome
        StartingField.Rent -> rent
        StartingField.HealthInsurance -> healthInsurance
        StartingField.Groceries -> groceries
        StartingField.OtherExpenses -> otherExpenses
        StartingField.Tuition -> tuition
        StartingField.SalaryAfterStudies -> salaryAfterStudies
        StartingField.SharedFlatRent -> sharedFlatRent
        StartingField.SharedFlatMovingCost -> sharedFlatMovingCost
    }

    val sharedFlatOffer: SharedFlatOffer get() = SharedFlatOffer(Money.chf(sharedFlatRent), Money.chf(sharedFlatMovingCost))

    companion object {
        // Synthetic demo assumptions (Maya profile and the shared-flat what-if), unchanged from before.
        val Default = StartingData(8_000, 1_200, 700, 250, 230, 300, 800, 6_000, 500, 500)

        /**
         * Engine limit is ±9e12 cents (±90 billion CHF) for every amount and intermediate result.
         * Spread over the 1,200-month maximum and ~10 amounts per month, 7.5 million CHF per field keeps
         * ordinary runs inside it. Not a financial rule; only a technical bound.
         */
        const val MAX_ABS_CHF = Money.MAX_CENTS / 100 / MonthlyConfiguration.MAX_MONTHS / 10

        fun of(values: Map<StartingField, Long>) = StartingData(
            values.getValue(StartingField.OpeningCash),
            values.getValue(StartingField.ParentIncome),
            values.getValue(StartingField.Rent),
            values.getValue(StartingField.HealthInsurance),
            values.getValue(StartingField.Groceries),
            values.getValue(StartingField.OtherExpenses),
            values.getValue(StartingField.Tuition),
            values.getValue(StartingField.SalaryAfterStudies),
            values.getValue(StartingField.SharedFlatRent),
            values.getValue(StartingField.SharedFlatMovingCost),
        )
    }
}

/** Monthly rent and one-time moving cost offered by the shared-flat what-if. */
internal data class SharedFlatOffer(val monthlyRent: Money, val movingCost: Money) {
    companion object {
        val Default get() = StartingData.Default.sharedFlatOffer
    }
}

/** Opening cash may be negative (the engine allows it); every other field is an amount of 0 or more. */
internal enum class StartingField(val allowsNegative: Boolean = false) {
    OpeningCash(allowsNegative = true),
    ParentIncome,
    Rent,
    HealthInsurance,
    Groceries,
    OtherExpenses,
    Tuition,
    SalaryAfterStudies,
    SharedFlatRent,
    SharedFlatMovingCost,
}

internal enum class FieldError { Empty, NotAWholeNumber, Negative, TooLarge }

internal object StartingDataForm {
    /** Parses whole CHF. Spaces, apostrophes, commas and thin spaces are accepted as digit grouping. */
    fun parse(text: String, field: StartingField): Result<Long> {
        val cleaned = text.trim().filterNot { it == ' ' || it == '\'' || it == ',' || it == '’' || it == ' ' || it == ' ' }
            .replace('−', '-')
        if (cleaned.isEmpty()) return Result.Invalid(FieldError.Empty)
        val negative = cleaned.startsWith("-")
        val digits = if (negative) cleaned.drop(1) else cleaned
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return Result.Invalid(FieldError.NotAWholeNumber)
        if (negative && !field.allowsNegative) return Result.Invalid(FieldError.Negative)
        // More than 10 digits is out of range anyway; avoid Long overflow while parsing.
        if (digits.trimStart('0').length > 10) return Result.Invalid(FieldError.TooLarge)
        val value = digits.toLong().let { if (negative) -it else it }
        if (kotlin.math.abs(value) > StartingData.MAX_ABS_CHF) return Result.Invalid(FieldError.TooLarge)
        return Result.Valid(value)
    }

    fun errors(texts: Map<StartingField, String>): Map<StartingField, FieldError> =
        StartingField.entries.mapNotNull { field ->
            (parse(texts[field].orEmpty(), field) as? Result.Invalid)?.let { field to it.error }
        }.toMap()

    /** The starting data, or null while any field is invalid. */
    fun build(texts: Map<StartingField, String>): StartingData? {
        val values = StartingField.entries.associateWith { parse(texts[it].orEmpty(), it) }
        if (values.values.any { it is Result.Invalid }) return null
        return StartingData.of(values.mapValues { (it.value as Result.Valid).value })
    }

    fun texts(data: StartingData): Map<StartingField, String> = StartingField.entries.associateWith { data[it].toString() }

    sealed interface Result<out T> {
        data class Valid<T>(val value: T) : Result<T>
        data class Invalid(val error: FieldError) : Result<Nothing>
    }
}
