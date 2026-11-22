package com.tuna.finlife.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.money_decimal_separator
import finlife.shared.generated.resources.money_group_separator
import finlife.shared.generated.resources.money_pattern
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

internal const val DEFAULT_CURRENCY_CODE = "CHF"
internal val LocalCurrencyCode = staticCompositionLocalOf { DEFAULT_CURRENCY_CODE }

@Composable
internal fun displayMoney(amount: Double): String {
    val rounded = (amount * 100).roundToLong()
    val number = displayMagnitude(abs(rounded), 100, 2)
    val formatted = stringResource(Res.string.money_pattern, number, LocalCurrencyCode.current)
    return if (rounded < 0) "−$formatted" else formatted
}

@Composable
internal fun displayMonths(amount: Double): String {
    val rounded = (amount * 10).roundToLong()
    val formatted = displayMagnitude(abs(rounded), 10, 1)
    return if (rounded < 0) "−$formatted" else formatted
}

@Composable
private fun displayMagnitude(magnitude: Long, scale: Int, fractionDigits: Int): String {
    val separator = stringResource(Res.string.money_group_separator)
    val whole = (magnitude / scale).toString().reversed().chunked(3).asReversed()
        .joinToString(separator) { it.reversed() }
    val fraction = magnitude % scale
    return if (fraction == 0L) {
        whole
    } else {
        whole + stringResource(Res.string.money_decimal_separator) + fraction.toString().padStart(fractionDigits, '0')
    }
}

/** Exact cents formatter for simulated values, without an intermediate floating-point conversion. */
internal fun formatCents(cents: Long, group: String = "’", decimal: String = "."): String {
    require(cents in -com.tuna.finlife.simulation.Money.MAX_CENTS..com.tuna.finlife.simulation.Money.MAX_CENTS)
    val magnitude = abs(cents)
    val whole = (magnitude / 100).toString().reversed().chunked(3).asReversed().joinToString(group) { it.reversed() }
    val fraction = magnitude % 100
    return (if (cents < 0) "−" else "") + whole + if (fraction == 0L) "" else decimal + fraction.toString().padStart(2, '0')
}

@Composable
internal fun displayMoney(amount: com.tuna.finlife.simulation.Money): String = stringResource(
    Res.string.money_pattern,
    formatCents(amount.cents, stringResource(Res.string.money_group_separator), stringResource(Res.string.money_decimal_separator)),
    LocalCurrencyCode.current,
)
