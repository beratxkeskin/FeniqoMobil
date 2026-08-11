package com.feniqo.mobile.domain.model

/** V1'de desteklenen para birimleri ve ondalık basamak bilgileri. */
enum class Currency(val code: String, val minorUnitDigits: Int) {
    TRY(code = "TRY", minorUnitDigits = 2),
    USD(code = "USD", minorUnitDigits = 2),
    EUR(code = "EUR", minorUnitDigits = 2),
}

/**
 * Tutarı en küçük para biriminde saklar; kayan nokta hassasiyet hatalarını engeller.
 * Örnek: 125,50 TRY, amountMinor = 12_550 olarak temsil edilir.
 *
 * Bu sınıf metin biçimleme yapmaz. Yerelleştirilmiş gösterim presentation katmanında kalır.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency,
) {
    init {
        require(amountMinor >= 0) { "Para tutarı negatif olamaz." }
        require(amountMinor <= MAX_AMOUNT_MINOR) { "Para tutarı desteklenen güvenli sınırı aşıyor." }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Farklı para birimleri toplanamaz." }
        check(MAX_AMOUNT_MINOR - amountMinor >= other.amountMinor) { "Para toplamı güvenli sınırı aşıyor." }
        return copy(amountMinor = amountMinor + other.amountMinor)
    }

    companion object {
        /** Baz puan hesaplarında 10.000 ile güvenli çarpım yapılabilen üst sınır. */
        const val MAX_AMOUNT_MINOR: Long = Long.MAX_VALUE / 10_000

        fun zero(currency: Currency): Money = Money(amountMinor = 0, currency = currency)
    }
}

/** Gelir-gider farkı gibi negatif olabilen parasal sonuçları temsil eder. */
data class MoneyDelta(
    val amountMinor: Long,
    val currency: Currency,
) {
    companion object {
        fun between(income: Money, expense: Money): MoneyDelta {
            require(income.currency == expense.currency) {
                "Farklı para birimleri arasında net tutar hesaplanamaz."
            }
            return MoneyDelta(
                amountMinor = income.amountMinor - expense.amountMinor,
                currency = income.currency,
            )
        }
    }
}
