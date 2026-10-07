@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class SymbolPlacement {
    PREFIX,
    SUFFIX,
}

data class MoneyFormatOptions(
    val groupSeparator: Char,
    val decimalSeparator: Char,
    val symbolPlacement: SymbolPlacement,
    val symbolSpacing: Boolean = true,
) {
    companion object {
        fun forTurkish(currency: Currency): MoneyFormatOptions {
            return MoneyFormatOptions(
                groupSeparator = '.',
                decimalSeparator = ',',
                symbolPlacement = SymbolPlacement.SUFFIX,
                symbolSpacing = true,
            )
        }

        fun forEnglish(currency: Currency): MoneyFormatOptions {
            return when (currency) {
                Currency.USD, Currency.EUR, Currency.GBP ->
                    MoneyFormatOptions(
                        groupSeparator = ',',
                        decimalSeparator = '.',
                        symbolPlacement = SymbolPlacement.PREFIX,
                        symbolSpacing = false,
                    )
                Currency.TRY ->
                    MoneyFormatOptions(
                        groupSeparator = ',',
                        decimalSeparator = '.',
                        symbolPlacement = SymbolPlacement.SUFFIX,
                        symbolSpacing = true,
                    )
            }
        }
    }
}

/**
 * Para birimi sembolünü döner.
 */
fun getCurrencySymbol(currency: Currency): String {
    return when (currency) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
        Currency.GBP -> "£"
    }
}

/**
 * Binlik basamak ayırıcısını ekler.
 */
private fun formatThousands(
    digits: String,
    separator: Char,
): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    val offset = digits.length % 3
    if (offset > 0) {
        sb.append(digits.substring(0, offset))
        if (offset < digits.length) sb.append(separator)
    }
    for (i in offset until digits.length step 3) {
        sb.append(digits.substring(i, i + 3))
        if (i + 3 < digits.length) sb.append(separator)
    }
    return sb.toString()
}

/**
 * Alt seviye saf para formatlayıcısı.
 * Double/Float kullanmaz; Long.MIN_VALUE için abs() taşması korumalıdır.
 */
fun formatRawMinor(
    amountMinor: Long,
    minorUnitDigits: Int,
    symbol: String,
    options: MoneyFormatOptions,
    showPositiveSign: Boolean = false,
): String {
    val isNegative = amountMinor < 0L
    val rawDigits =
        if (amountMinor == Long.MIN_VALUE) {
            "9223372036854775808"
        } else {
            kotlin.math.abs(amountMinor).toString()
        }

    val (majorDigits, minorPart) =
        if (minorUnitDigits <= 0) {
            rawDigits to ""
        } else if (rawDigits.length <= minorUnitDigits) {
            val padded = rawDigits.padStart(minorUnitDigits + 1, '0')
            padded.dropLast(minorUnitDigits) to padded.takeLast(minorUnitDigits)
        } else {
            rawDigits.dropLast(minorUnitDigits) to rawDigits.takeLast(minorUnitDigits)
        }

    val formattedMajor = formatThousands(majorDigits, options.groupSeparator)
    val amountText =
        if (minorUnitDigits > 0) {
            "$formattedMajor${options.decimalSeparator}$minorPart"
        } else {
            formattedMajor
        }

    val spacing = if (options.symbolSpacing) " " else ""
    val signPrefix =
        when {
            isNegative -> "-"
            showPositiveSign && amountMinor > 0L -> "+"
            else -> ""
        }

    return when (options.symbolPlacement) {
        SymbolPlacement.PREFIX -> "$signPrefix$symbol$spacing$amountText"
        SymbolPlacement.SUFFIX -> "$signPrefix$amountText$spacing$symbol"
    }
}

/**
 * Money nesnesini verilen biçim ayarlarına göre formatlar.
 */
fun formatMoney(
    money: Money,
    options: MoneyFormatOptions,
): String {
    return formatRawMinor(
        amountMinor = money.amountMinor,
        minorUnitDigits = money.currency.minorUnitDigits,
        symbol = getCurrencySymbol(money.currency),
        options = options,
        showPositiveSign = false,
    )
}

/**
 * MoneyDelta nesnesini (pozitif, negatif, sıfır) verilen biçim ayarlarına göre formatlar.
 */
fun formatMoneyDelta(
    delta: MoneyDelta,
    options: MoneyFormatOptions,
    showPositiveSign: Boolean = false,
): String {
    return formatRawMinor(
        amountMinor = delta.amountMinor,
        minorUnitDigits = delta.currency.minorUnitDigits,
        symbol = getCurrencySymbol(delta.currency),
        options = options,
        showPositiveSign = showPositiveSign,
    )
}

/**
 * Compose için locale-aware Money formatlayıcısı.
 */
@Composable
fun formatLocalizedMoney(money: Money): String {
    val languageCode = stringResource(Res.string.common_language_code)
    val options =
        if (languageCode == "en") {
            MoneyFormatOptions.forEnglish(money.currency)
        } else {
            MoneyFormatOptions.forTurkish(money.currency)
        }
    return formatMoney(money, options)
}

/**
 * Compose için locale-aware MoneyDelta formatlayıcısı.
 */
@Composable
fun formatLocalizedMoneyDelta(
    delta: MoneyDelta,
    showPositiveSign: Boolean = false,
): String {
    val languageCode = stringResource(Res.string.common_language_code)
    val options =
        if (languageCode == "en") {
            MoneyFormatOptions.forEnglish(delta.currency)
        } else {
            MoneyFormatOptions.forTurkish(delta.currency)
        }
    return formatMoneyDelta(delta, options, showPositiveSign = showPositiveSign)
}
