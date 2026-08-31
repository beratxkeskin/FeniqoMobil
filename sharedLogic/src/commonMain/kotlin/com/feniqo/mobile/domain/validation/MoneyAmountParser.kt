package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money

/**
 * Kullanıcı tarafından girilen para miktarını kayan nokta (Double/Float/BigDecimal) kullanmadan,
 * güvenli saf metin ayrıştırma kurallarıyla en küçük para birimine (Long minor unit) dönüştürür.
 *
 * Yerelleştirilmiş metin formatlama bu sınıfa ait değildir; presentation katmanında kalır.
 */
object MoneyAmountParser {

    sealed interface ParseResult {
        data class Success(val amountMinor: Long) : ParseResult
        data class Invalid(val error: MoneyParseError) : ParseResult
    }

    enum class MoneyParseError {
        EMPTY,
        INVALID_FORMAT,
        NON_POSITIVE,
        EXCESSIVE_DECIMAL_DIGITS,
        MAX_AMOUNT_EXCEEDED,
    }

    fun parseToMinorUnits(input: String, currency: Currency): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ParseResult.Invalid(MoneyParseError.EMPTY)

        // İşaret (+/-), harf, bilimsel gösterim (e/E), para sembolleri ve yabancı karakterler yasak
        if (!trimmed.all { it.isDigit() || it == '.' || it == ',' }) {
            return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
        }

        // Aynı girdide hem nokta hem virgül birlikte bulunamaz (belirsiz/tahminden kaçınılmalı)
        if ('.' in trimmed && ',' in trimmed) {
            return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
        }

        val separator = if (',' in trimmed) ',' else if ('.' in trimmed) '.' else null
        val parts = if (separator != null) trimmed.split(separator) else listOf(trimmed)

        // Birden fazla aynı ayırıcı (örn. 10..5 veya 1.2.3)
        if (parts.size > 2) return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)

        val intPartStr = parts[0]
        // Tamsayı kısmı zorunlu: ".5" veya ",5" geçersiz
        if (intPartStr.isEmpty()) return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
        if (!intPartStr.all { it.isDigit() }) return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)

        val fracPartStr = if (parts.size == 2) {
            val frac = parts[1]
            // Ayırıcı kullanıldıysa ondalık kısım zorunlu: "10." veya "10," geçersiz
            if (frac.isEmpty()) return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
            if (!frac.all { it.isDigit() }) return ParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
            if (frac.length > currency.minorUnitDigits) {
                return ParseResult.Invalid(MoneyParseError.EXCESSIVE_DECIMAL_DIGITS)
            }
            frac.padEnd(currency.minorUnitDigits, '0')
        } else {
            "".padEnd(currency.minorUnitDigits, '0')
        }

        val combinedStr = "$intPartStr$fracPartStr".trimStart('0').ifEmpty { "0" }
        val amountMinor = combinedStr.toLongOrNull()
            ?: return ParseResult.Invalid(MoneyParseError.MAX_AMOUNT_EXCEEDED)

        if (amountMinor <= 0L) return ParseResult.Invalid(MoneyParseError.NON_POSITIVE)
        if (amountMinor > Money.MAX_AMOUNT_MINOR) {
            return ParseResult.Invalid(MoneyParseError.MAX_AMOUNT_EXCEEDED)
        }

        return ParseResult.Success(amountMinor)
    }
}
