package com.tuna.finlife.game

import com.tuna.finlife.simulation.Money
import com.tuna.finlife.simulation.MonthlyConfiguration
import com.tuna.finlife.simulation.MonthlyProfile

/** Player-editable starting data, in whole CHF. [Default] is the engine's own reference profile. */
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
    val internshipSalary: Long = 300,
    /** Legacy saves had no name; new runs explicitly ask for one. */
    val firstName: String = FirstName.LegacyDefault,
) {
    init {
        StartingField.entries.forEach { require(this[it] in 0..MAX_ABS_CHF) { "Invalid starting amount: $it" } }
        require(FirstName.normalize(firstName) == firstName) { "Invalid first name" }
    }

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
        StartingField.InternshipSalary -> internshipSalary
    }

    companion object {
        /**
         * Read from the engine's reference profile (MonthlyProfile.alexia) and default configuration,
         * so the app follows the engine team's synthetic values instead of keeping its own copy.
         */
        val Default: StartingData by lazy {
            val profile = MonthlyProfile.alexia()
            val configuration = MonthlyConfiguration()
            fun chf(money: Money) = money.cents / 100
            StartingData(
                openingCash = chf(profile.cash),
                parentIncome = chf(profile.studentMonthlyIncome),
                rent = chf(profile.housing.monthlyRent),
                healthInsurance = chf(profile.healthInsurance),
                groceries = chf(profile.groceries),
                otherExpenses = chf(profile.otherExpenses),
                tuition = chf(profile.tuitionPayment),
                salaryAfterStudies = chf(profile.startingMonthlySalary),
                sharedFlatRent = chf(configuration.sharedReferenceRent),
                sharedFlatMovingCost = chf(configuration.sharedMovingCost),
            )
        }

        /**
         * Engine limit is ±9e12 cents (±90 billion CHF) for every amount and intermediate result.
         * Spread over the 1,200-month maximum and ~10 amounts per month, 7.5 million CHF per field keeps
         * ordinary runs inside it. Not a financial rule; only a technical bound.
         */
        const val MAX_ABS_CHF = Money.MAX_CENTS / 100 / MonthlyConfiguration.MAX_MONTHS / 10

        fun of(values: Map<StartingField, Long>, firstName: String = FirstName.LegacyDefault) = StartingData(
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
            values.getValue(StartingField.InternshipSalary),
            firstName,
        )
    }
}

/** Every field is an amount of 0 or more (the V3 engine rejects negative cash). */
internal enum class StartingField(val allowsNegative: Boolean = false) {
    OpeningCash,
    ParentIncome,
    Rent,
    HealthInsurance,
    Groceries,
    OtherExpenses,
    Tuition,
    SalaryAfterStudies,
    InternshipSalary,
    SharedFlatRent,
    SharedFlatMovingCost,
}

internal enum class FieldError { Empty, NotAWholeNumber, Negative, TooLarge }

internal object StartingDataForm {
    /** Parses whole CHF. Spaces, apostrophes, commas and thin spaces are accepted as digit grouping. */
    fun parse(text: String, field: StartingField): Result<Long> {
        val grouped = text.trim().replace('−', '-').map { if (it in " ,’  ") '\'' else it }.joinToString("")
        if ('\'' in grouped && !Regex("-?[0-9]{1,3}('[0-9]{3})+").matches(grouped)) return Result.Invalid(FieldError.NotAWholeNumber)
        val cleaned = grouped.replace("'", "")
        if (cleaned.isEmpty()) return Result.Invalid(FieldError.Empty)
        val negative = cleaned.startsWith("-")
        val digits = if (negative) cleaned.drop(1) else cleaned
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return Result.Invalid(FieldError.NotAWholeNumber)
        if (negative && !field.allowsNegative) return Result.Invalid(FieldError.Negative)
        // More than 10 digits is out of range anyway; avoid Long overflow while parsing.
        if (digits.trimStart('0').length > 10) return Result.Invalid(FieldError.TooLarge)
        val value = (digits.trimStart('0').ifEmpty { "0" }).toLong().let { if (negative) -it else it }
        if (kotlin.math.abs(value) > StartingData.MAX_ABS_CHF) return Result.Invalid(FieldError.TooLarge)
        return Result.Valid(value)
    }

    fun errors(texts: Map<StartingField, String>): Map<StartingField, FieldError> =
        StartingField.entries.mapNotNull { field ->
            (parse(texts[field].orEmpty(), field) as? Result.Invalid)?.let { field to it.error }
        }.toMap()

    /** The starting data, or null while any field is invalid. */
    fun build(texts: Map<StartingField, String>, firstName: String = FirstName.LegacyDefault): StartingData? {
        val values = StartingField.entries.associateWith { parse(texts[it].orEmpty(), it) }
        if (values.values.any { it is Result.Invalid }) return null
        return FirstName.normalize(firstName)?.let { valid ->
            StartingData.of(values.mapValues { (it.value as Result.Valid).value }, valid)
        }
    }

    fun texts(data: StartingData): Map<StartingField, String> = StartingField.entries.associateWith { data[it].toString() }

    sealed interface Result<out T> {
        data class Valid<T>(val value: T) : Result<T>
        data class Invalid(val error: FieldError) : Result<Nothing>
    }
}


/** A short, human first name, safe to insert into localized UI and AI prompts. */
internal object FirstName {
    const val LegacyDefault = "Maya"
    const val MAX_LENGTH = 32

    fun normalize(raw: String): String? {
        if (raw.any { it.isWhitespace() && it != ' ' }) return null
        val value = raw.trim().replace(Regex(" +"), " ")
        if (value.length !in 1..MAX_LENGTH || !value.first().isLetter() || !value.last().isLetter()) return null
        val separators = " -'’"
        if (!value.all { it.isLetter() || it in separators }) return null
        if (value.windowed(2).any { it[0] in separators && it[1] in separators }) return null
        return value
    }
}
