package com.tuna.finlife.i18n

import androidx.compose.runtime.Composable
import finlife.shared.generated.resources.Res
import finlife.shared.generated.resources.money_decimal_separator
import finlife.shared.generated.resources.money_group_separator
import finlife.shared.generated.resources.money_pattern
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_CURRENCY_CODE = "CHF"

@Composable
internal fun displayMoney(amount: Double, currencyCode: String = DEFAULT_CURRENCY_CODE): String {
    val minorUnits = (amount * 100).roundToLong()
    val magnitude = abs(minorUnits)
    val separator = stringResource(Res.string.money_group_separator)
    val whole = (magnitude / 100).toString().reversed().chunked(3).asReversed()
        .joinToString(separator) { it.reversed() }
    val cents = magnitude % 100
    val number = if (cents == 0L) {
        whole
    } else {
        whole + stringResource(Res.string.money_decimal_separator) + cents.toString().padStart(2, '0')
    }
    val formatted = stringResource(Res.string.money_pattern, number, currencyCode)
    return if (minorUnits < 0) "−$formatted" else formatted
}
