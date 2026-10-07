@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.Currency
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Saf KMP sayı ve oran formatlayıcısı.
 * Double/Float kullanmaz; Int ve Long aritmetiğiyle çalışır.
 */
fun formatBasisPointsRateNumber(
    basisPoints: Int,
    decimalSeparator: String,
): String {
    val absLong = kotlin.math.abs(basisPoints.toLong())
    val major = absLong / 100
    val minor = (absLong % 100).toInt()
    return if (minor == 0) {
        "$major"
    } else {
        val minorStr = minor.toString().padStart(2, '0').trimEnd('0')
        "$major$decimalSeparator$minorStr"
    }
}

/**
 * Basis-points değerini aktif locale kurallarına göre yüzdelik metne dönüştürür.
 * TR: %80, %80,5, -%25
 * EN: 80%, 80.5%, -25%
 */
@Composable
fun formatLocalizedRateBasisPoints(basisPoints: Int): String {
    val languageCode = stringResource(Res.string.common_language_code)
    val decimalSeparator = stringResource(Res.string.common_decimal_separator)
    val formattedNumber = formatBasisPointsRateNumber(basisPoints, decimalSeparator)
    val prefix = if (basisPoints < 0) "-" else ""
    return if (languageCode == "tr") {
        "$prefix%$formattedNumber"
    } else {
        "$prefix$formattedNumber%"
    }
}

/**
 * Aktif locale için ondalık basamak ayırıcı karakterini döndürür.
 */
@Composable
fun currentLocaleDecimalSeparator(): Char = stringResource(Res.string.common_decimal_separator).firstOrNull() ?: ','

/**
 * AssetQuantity nesnesini verilen ondalık ayırıcıyla metne dönüştürür.
 */
fun formatAssetQuantity(
    quantity: AssetQuantity,
    decimalSeparator: String,
): String {
    if (quantity.scale == 0) return quantity.unscaledValue.toString()
    val digits = quantity.unscaledValue.toString().padStart(quantity.scale + 1, '0')
    val fractional = digits.takeLast(quantity.scale).trimEnd('0')
    return if (fractional.isEmpty()) {
        digits.dropLast(quantity.scale)
    } else {
        "${digits.dropLast(quantity.scale)}$decimalSeparator$fractional"
    }
}

/**
 * Para biriminin minor unit tutarını form alanlarında düzenlenebilir metne dönüştürür.
 * Kayan nokta kullanmaz; kuruş hanesi sıfır ise yalnız tam sayı, küsurat varsa anlamlı basamakları üretir.
 */
fun formatMinorUnitsToInputText(
    amountMinor: Long,
    currency: Currency,
    decimalSeparator: Char,
): String {
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
        "$main$decimalSeparator$remStr"
    }
}
