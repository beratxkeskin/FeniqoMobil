package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyAmountParserTest {

    @Test
    fun parseToMinorUnits_withValidInputs_returnsCorrectMinorUnits() {
        // "10" -> 1000 minor units
        val r1 = MoneyAmountParser.parseToMinorUnits("10", Currency.TRY)
        assertTrue(r1 is MoneyAmountParser.ParseResult.Success)
        assertEquals(1000L, r1.amountMinor)

        // "10,5" -> 1050 minor units
        val r2 = MoneyAmountParser.parseToMinorUnits("10,5", Currency.USD)
        assertTrue(r2 is MoneyAmountParser.ParseResult.Success)
        assertEquals(1050L, r2.amountMinor)

        // "10.50" -> 1050 minor units
        val r3 = MoneyAmountParser.parseToMinorUnits("10.50", Currency.EUR)
        assertTrue(r3 is MoneyAmountParser.ParseResult.Success)
        assertEquals(1050L, r3.amountMinor)

        // "0.05" -> 5 minor units
        val r4 = MoneyAmountParser.parseToMinorUnits("0.05", Currency.TRY)
        assertTrue(r4 is MoneyAmountParser.ParseResult.Success)
        assertEquals(5L, r4.amountMinor)

        // "0,5" -> 50 minor units
        val r5 = MoneyAmountParser.parseToMinorUnits("0,5", Currency.TRY)
        assertTrue(r5 is MoneyAmountParser.ParseResult.Success)
        assertEquals(50L, r5.amountMinor)
    }

    @Test
    fun parseToMinorUnits_withLeadingAndTrailingWhitespace_trimsAndParsesSuccessfully() {
        val result = MoneyAmountParser.parseToMinorUnits("   150,25   ", Currency.TRY)
        assertTrue(result is MoneyAmountParser.ParseResult.Success)
        assertEquals(15025L, result.amountMinor)
    }

    @Test
    fun parseToMinorUnits_withEmptyOrWhitespace_returnsEmptyError() {
        val r1 = MoneyAmountParser.parseToMinorUnits("", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.EMPTY), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("    ", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.EMPTY), r2)
    }

    @Test
    fun parseToMinorUnits_withZeroOrZeroEquivalent_returnsNonPositiveError() {
        val r1 = MoneyAmountParser.parseToMinorUnits("0", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.NON_POSITIVE), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("0,00", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.NON_POSITIVE), r2)

        val r3 = MoneyAmountParser.parseToMinorUnits("00.0", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.NON_POSITIVE), r3)
    }

    @Test
    fun parseToMinorUnits_withNegativeSign_returnsInvalidFormat() {
        val r1 = MoneyAmountParser.parseToMinorUnits("-10", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("-0.5", Currency.USD)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)

        val r3 = MoneyAmountParser.parseToMinorUnits("+10", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r3)
    }

    @Test
    fun parseToMinorUnits_withScientificNotation_returnsInvalidFormat() {
        val r1 = MoneyAmountParser.parseToMinorUnits("1e5", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("2.5E3", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)
    }

    @Test
    fun parseToMinorUnits_withCurrencySymbolsOrLetters_returnsInvalidFormat() {
        val r1 = MoneyAmountParser.parseToMinorUnits("100 ₺", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("$50", Currency.USD)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)

        val r3 = MoneyAmountParser.parseToMinorUnits("€20", Currency.EUR)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r3)

        val r4 = MoneyAmountParser.parseToMinorUnits("abc", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r4)
    }

    @Test
    fun parseToMinorUnits_withMissingIntegerOrFractionalPart_returnsInvalidFormat() {
        // ".5" and ",5" have missing integer part
        val r1 = MoneyAmountParser.parseToMinorUnits(".5", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits(",5", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)

        // "10." and "10," have missing fractional part after separator
        val r3 = MoneyAmountParser.parseToMinorUnits("10.", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r3)

        val r4 = MoneyAmountParser.parseToMinorUnits("10,", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r4)
    }

    @Test
    fun parseToMinorUnits_withAmbiguousBothDotAndComma_returnsInvalidFormat() {
        val r1 = MoneyAmountParser.parseToMinorUnits("1.250,50", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("1,250.50", Currency.USD)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)
    }

    @Test
    fun parseToMinorUnits_withMultipleSeparators_returnsInvalidFormat() {
        val r1 = MoneyAmountParser.parseToMinorUnits("10..5", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("10,,5", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r2)

        val r3 = MoneyAmountParser.parseToMinorUnits("1.2.3", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.INVALID_FORMAT), r3)
    }

    @Test
    fun parseToMinorUnits_withExcessiveDecimalDigits_returnsExcessiveDecimalDigitsError() {
        val r1 = MoneyAmountParser.parseToMinorUnits("10,555", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS), r1)

        val r2 = MoneyAmountParser.parseToMinorUnits("1.999", Currency.USD)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS), r2)
    }

    @Test
    fun parseToMinorUnits_withMaxAmountBoundaryAndOverflow_handlesCorrectly() {
        // Exact MAX_AMOUNT_MINOR boundary
        val maxMinor = Money.MAX_AMOUNT_MINOR
        val wholePart = maxMinor / 100
        val fracPart = (maxMinor % 100).toString().padStart(2, '0')
        val maxInput = "$wholePart.$fracPart"

        val rValid = MoneyAmountParser.parseToMinorUnits(maxInput, Currency.TRY)
        assertTrue(rValid is MoneyAmountParser.ParseResult.Success)
        assertEquals(maxMinor, rValid.amountMinor)

        // Exceeding MAX_AMOUNT_MINOR
        val exceededWhole = wholePart + 1
        val exceededInput = "$exceededWhole.$fracPart"
        val rExceeded = MoneyAmountParser.parseToMinorUnits(exceededInput, Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED), rExceeded)

        // Huge overflow beyond Long
        val rHuge = MoneyAmountParser.parseToMinorUnits("99999999999999999999999999", Currency.TRY)
        assertEquals(MoneyAmountParser.ParseResult.Invalid(MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED), rHuge)
    }
}
