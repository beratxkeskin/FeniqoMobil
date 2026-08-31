package com.feniqo.mobile.presentation.util

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.TransactionType

/**
 * Saf presentation katmanı para ve oran formatlayıcısı.
 * Double/Float kullanmaz; en küçük birim (minor unit) Long aritmetiği ile çalışır.
 */
object MoneyFormatter {

    fun format(
        money: Money,
        includeSign: Boolean = false,
        type: TransactionType? = null,
    ): String {
        val digits = money.currency.minorUnitDigits
        val divisor = calculateDivisor(digits)

        val major = money.amountMinor / divisor
        val minor = money.amountMinor % divisor

        val formattedMajor = formatThousands(major)
        val formattedMinor = if (digits > 0) {
            minor.toString().padStart(digits, '0')
        } else {
            ""
        }

        val amountPart = if (digits > 0) {
            "$formattedMajor,$formattedMinor"
        } else {
            formattedMajor
        }

        val symbol = getCurrencySymbol(money.currency)
        val baseFormatted = "$amountPart $symbol"

        val prefix = when {
            includeSign && type == TransactionType.EXPENSE -> "-"
            includeSign && type == TransactionType.INCOME -> "+"
            else -> ""
        }

        return "$prefix$baseFormatted"
    }

    fun formatDelta(
        delta: MoneyDelta,
        includeSign: Boolean = true,
    ): String {
        val digits = delta.currency.minorUnitDigits
        val divisor = calculateDivisor(digits)

        val isNegative = delta.amountMinor < 0
        val absMinor = kotlin.math.abs(delta.amountMinor)
        val major = absMinor / divisor
        val minor = absMinor % divisor

        val formattedMajor = formatThousands(major)
        val formattedMinor = if (digits > 0) {
            minor.toString().padStart(digits, '0')
        } else {
            ""
        }

        val amountPart = if (digits > 0) {
            "$formattedMajor,$formattedMinor"
        } else {
            formattedMajor
        }

        val symbol = getCurrencySymbol(delta.currency)
        val baseFormatted = "$amountPart $symbol"

        val prefix = when {
            isNegative -> "-"
            includeSign && delta.amountMinor > 0 -> "+"
            else -> ""
        }

        return "$prefix$baseFormatted"
    }

    fun formatBasisPoints(basisPoints: RateBasisPoints): String {
        val value = basisPoints.value
        val isNegative = value < 0
        val absValue = kotlin.math.abs(value)
        val major = absValue / 100
        val minor = absValue % 100
        val formattedMinor = minor.toString().padStart(2, '0')
        val prefix = if (isNegative) "-" else ""
        return "$prefix%$major,$formattedMinor"
    }

    private fun calculateDivisor(digits: Int): Long {
        var divisor = 1L
        repeat(digits) {
            divisor *= 10L
        }
        return divisor
    }

    private fun formatThousands(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str

        val sb = StringBuilder()
        val offset = str.length % 3
        if (offset > 0) {
            sb.append(str.substring(0, offset))
            if (offset < str.length) sb.append('.')
        }
        var i = offset
        while (i < str.length) {
            if (i > offset) sb.append('.')
            sb.append(str.substring(i, i + 3))
            i += 3
        }
        return sb.toString()
    }

    private fun getCurrencySymbol(currency: Currency): String = when (currency) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
    }

    /**
     * En küçük para birimini (minor unit) Long aritmetiğiyle form giriş metnine dönüştürür.
     * Kayan nokta (Double/Float) ve metin kırpma kullanmaz; kuruş hanesi sıfır ise yalnız tam sayı,
     * küsurat varsa virgülden sonraki anlamlı basamakları üretir (örn: 12550 -> "125,5", 12505 -> "125,05").
     */
    fun formatMinorUnitsToInputText(amountMinor: Long, currency: Currency): String {
        val digits = currency.minorUnitDigits
        if (digits == 0) return amountMinor.toString()
        var divisor = 1L
        for (i in 1..digits) {
            divisor *= 10L
        }
        val main = amountMinor / divisor
        val rem = amountMinor % divisor
        return if (rem == 0L) {
            main.toString()
        } else {
            val remStr = rem.toString().padStart(digits, '0').trimEnd('0')
            "$main,$remStr"
        }
    }
}
