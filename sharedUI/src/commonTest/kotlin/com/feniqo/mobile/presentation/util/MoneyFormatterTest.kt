package com.feniqo.mobile.presentation.util

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {

    @Test
    fun format_withZeroAmount_returnsZeroWithDecimalsAndSymbol() {
        val money = Money(0L, Currency.TRY)
        val formatted = MoneyFormatter.format(money)
        assertEquals("0,00 ₺", formatted)
    }

    @Test
    fun format_withSingleDigitMinor_padsWithLeadingZero() {
        val money = Money(105L, Currency.TRY) // 1.05 TRY
        val formatted = MoneyFormatter.format(money)
        assertEquals("1,05 ₺", formatted)
    }

    @Test
    fun format_withThousandsSeparator_formatsThousandsCorrectly() {
        val money = Money(125050L, Currency.TRY) // 1,250.50 TRY
        val formatted = MoneyFormatter.format(money)
        assertEquals("1.250,50 ₺", formatted)
    }

    @Test
    fun format_withMillions_formatsMultipleThousandsSeparatorsCorrectly() {
        val money = Money(123456789L, Currency.TRY) // 1,234,567.89 TRY
        val formatted = MoneyFormatter.format(money)
        assertEquals("1.234.567,89 ₺", formatted)
    }

    @Test
    fun format_withMaxAmountMinor_formatsMaxSupportedAmountCorrectly() {
        val money = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY)
        val formatted = MoneyFormatter.format(money)
        assertEquals("9.223.372.036.854,77 ₺", formatted)
    }

    @Test
    fun format_withDifferentCurrencies_usesCorrectSymbols() {
        assertEquals("100,00 ₺", MoneyFormatter.format(Money(10000L, Currency.TRY)))
        assertEquals("100,00 $", MoneyFormatter.format(Money(10000L, Currency.USD)))
        assertEquals("100,00 €", MoneyFormatter.format(Money(10000L, Currency.EUR)))
    }

    @Test
    fun format_withIncludeSignAndExpense_prefixesMinus() {
        val money = Money(50000L, Currency.TRY)
        val formatted = MoneyFormatter.format(money, includeSign = true, type = TransactionType.EXPENSE)
        assertEquals("-500,00 ₺", formatted)
    }

    @Test
    fun format_withIncludeSignAndIncome_prefixesPlus() {
        val money = Money(50000L, Currency.TRY)
        val formatted = MoneyFormatter.format(money, includeSign = true, type = TransactionType.INCOME)
        assertEquals("+500,00 ₺", formatted)
    }

    @Test
    fun format_withoutIncludeSign_hasNoSignPrefix() {
        val money = Money(50000L, Currency.TRY)
        val formatted = MoneyFormatter.format(money, includeSign = false, type = TransactionType.EXPENSE)
        assertEquals("500,00 ₺", formatted)
    }

    @Test
    fun format_withIncludeSignTrueAndNullType_hasNoSignPrefix() {
        val money = Money(50000L, Currency.TRY)
        val formatted = MoneyFormatter.format(money, includeSign = true, type = null)
        assertEquals("500,00 ₺", formatted)
    }

    @Test
    fun formatMinorUnitsToInputText_formatsDifferentCurrenciesAndDecimalsCorrectly() {
        assertEquals("0", MoneyFormatter.formatMinorUnitsToInputText(0L, Currency.TRY))
        assertEquals("100", MoneyFormatter.formatMinorUnitsToInputText(10000L, Currency.TRY))
        assertEquals("125,5", MoneyFormatter.formatMinorUnitsToInputText(12550L, Currency.TRY))
        assertEquals("125,05", MoneyFormatter.formatMinorUnitsToInputText(12505L, Currency.TRY))
        assertEquals("0,99", MoneyFormatter.formatMinorUnitsToInputText(99L, Currency.USD))
        assertEquals("1500", MoneyFormatter.formatMinorUnitsToInputText(150000L, Currency.EUR))
        assertEquals("250000", MoneyFormatter.formatMinorUnitsToInputText(25000000L, Currency.TRY))
    }
}
