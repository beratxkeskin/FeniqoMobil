package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleAwareMoneyFormatterTest {
    @Test
    fun formatsPositiveTryInTurkishAndEnglish() {
        val money = Money(125_050L, Currency.TRY)
        val tr = formatMoney(money, MoneyFormatOptions.forTurkish(Currency.TRY))
        assertEquals("1.250,50 ₺", tr)

        val en = formatMoney(money, MoneyFormatOptions.forEnglish(Currency.TRY))
        assertEquals("1,250.50 ₺", en)
    }

    @Test
    fun formatsPositiveUsdInTurkishAndEnglish() {
        val money = Money(125_050L, Currency.USD)
        val tr = formatMoney(money, MoneyFormatOptions.forTurkish(Currency.USD))
        assertEquals("1.250,50 $", tr)

        val en = formatMoney(money, MoneyFormatOptions.forEnglish(Currency.USD))
        assertEquals("$1,250.50", en)
    }

    @Test
    fun formatsNegativeMoneyDeltaInEnglishUsdAndTurkishTry() {
        val negUsd = MoneyDelta(-125_050L, Currency.USD)
        val enUsd = formatMoneyDelta(negUsd, MoneyFormatOptions.forEnglish(Currency.USD))
        assertEquals("-$1,250.50", enUsd)

        val negTry = MoneyDelta(-125_050L, Currency.TRY)
        val trTry = formatMoneyDelta(negTry, MoneyFormatOptions.forTurkish(Currency.TRY))
        assertEquals("-1.250,50 ₺", trTry)
    }

    @Test
    fun formatsPositiveMoneyDeltaWithExplicitSign() {
        val posUsd = MoneyDelta(125_050L, Currency.USD)
        val enUsd = formatMoneyDelta(posUsd, MoneyFormatOptions.forEnglish(Currency.USD), showPositiveSign = true)
        assertEquals("+$1,250.50", enUsd)

        val posTry = MoneyDelta(125_050L, Currency.TRY)
        val trTry = formatMoneyDelta(posTry, MoneyFormatOptions.forTurkish(Currency.TRY), showPositiveSign = true)
        assertEquals("+1.250,50 ₺", trTry)
    }

    @Test
    fun formatsZeroMoneyAndMoneyDelta() {
        val zeroTry = Money.zero(Currency.TRY)
        assertEquals("0,00 ₺", formatMoney(zeroTry, MoneyFormatOptions.forTurkish(Currency.TRY)))
        assertEquals("0.00 ₺", formatMoney(zeroTry, MoneyFormatOptions.forEnglish(Currency.TRY)))

        val zeroUsd = Money.zero(Currency.USD)
        assertEquals("$0.00", formatMoney(zeroUsd, MoneyFormatOptions.forEnglish(Currency.USD)))

        val zeroDelta = MoneyDelta(0L, Currency.TRY)
        assertEquals("0,00 ₺", formatMoneyDelta(zeroDelta, MoneyFormatOptions.forTurkish(Currency.TRY), showPositiveSign = true))
    }

    @Test
    fun formatsArbitraryMinorUnitDigitsThroughRawFormatter() {
        // 0 basamaklı örneğin JPY benzeri durum
        val zeroDigitsOptions =
            MoneyFormatOptions(
                groupSeparator = ',',
                decimalSeparator = '.',
                symbolPlacement = SymbolPlacement.PREFIX,
                symbolSpacing = false,
            )
        val zeroDigitsFormatted =
            formatRawMinor(
                amountMinor = 1500L,
                minorUnitDigits = 0,
                symbol = "¥",
                options = zeroDigitsOptions,
            )
        assertEquals("¥1,500", zeroDigitsFormatted)

        // 3 basamaklı örneğin KWD benzeri durum
        val threeDigitsOptions =
            MoneyFormatOptions(
                groupSeparator = '.',
                decimalSeparator = ',',
                symbolPlacement = SymbolPlacement.SUFFIX,
                symbolSpacing = true,
            )
        val threeDigitsFormatted =
            formatRawMinor(
                amountMinor = 12_345L,
                minorUnitDigits = 3,
                symbol = "KD",
                options = threeDigitsOptions,
            )
        assertEquals("12,345 KD", threeDigitsFormatted)
    }

    @Test
    fun handlesLongBoundariesSafelyWithoutAbsOverflow() {
        val minDelta = MoneyDelta(Long.MIN_VALUE, Currency.USD)
        val minFormatted = formatMoneyDelta(minDelta, MoneyFormatOptions.forEnglish(Currency.USD))
        // Long.MIN_VALUE = -9223372036854775808 -> -92,233,720,368,547,758.08
        assertEquals("-$92,233,720,368,547,758.08", minFormatted)

        val rawMaxFormatted =
            formatRawMinor(
                amountMinor = Long.MAX_VALUE,
                minorUnitDigits = 2,
                symbol = "₺",
                options = MoneyFormatOptions.forTurkish(Currency.TRY),
            )
        // Long.MAX_VALUE = 9223372036854775807 -> 92.233.720.368.547.758,07 ₺
        assertEquals("92.233.720.368.547.758,07 ₺", rawMaxFormatted)
    }
}
