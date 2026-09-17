package com.feniqo.mobile.presentation.util

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountMaskingTest {

    @Test
    fun formatMasked_whenMaskIsTrue_returnsMaskedDots() {
        val money = Money(150000, Currency.TRY) // 1.500,00 ₺
        val masked = MoneyFormatter.formatMasked(money, mask = true)
        assertEquals(MoneyFormatter.MASKED_TEXT, masked)
    }

    @Test
    fun formatMasked_whenMaskIsFalse_returnsFormattedAmount() {
        val money = Money(150000, Currency.TRY) // 1.500,00 ₺
        val unmasked = MoneyFormatter.formatMasked(money, mask = false)
        assertEquals("1.500,00 ₺", unmasked)
    }

    @Test
    fun formatMasked_withSign_whenMaskIsTrue_stillReturnsMaskedDots() {
        val money = Money(4550, Currency.TRY)
        val masked = MoneyFormatter.formatMasked(money, mask = true, includeSign = true, type = TransactionType.EXPENSE)
        assertEquals(MoneyFormatter.MASKED_TEXT, masked)
    }

    @Test
    fun getAccessibleDescription_whenMaskIsTrue_neverRevealsRawAmount() {
        val rawDescription = "Net bakiye: 25.400,00 ₺"
        val desc = MoneyFormatter.getAccessibleDescription(rawDescription, isMasked = true)
        assertEquals(MoneyFormatter.MASKED_ACCESSIBLE_DESCRIPTION, desc)
    }

    @Test
    fun getAccessibleDescription_whenMaskIsFalse_returnsRawDescription() {
        val rawDescription = "Net bakiye: 25.400,00 ₺"
        val desc = MoneyFormatter.getAccessibleDescription(rawDescription, isMasked = false)
        assertEquals(rawDescription, desc)
    }
}
