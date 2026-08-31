package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionValidationRulesTest {

    private val today = LocalDate(2026, 8, 21)

    @Test
    fun validateAmount_delegatesCorrectlyToMoneyAmountParser() {
        val valid = TransactionValidationRules.validateAmount("125,50", Currency.TRY)
        assertTrue(valid is TransactionValidationResult.Valid)
        assertEquals(12550L, valid.value)

        val empty = TransactionValidationRules.validateAmount("", Currency.TRY)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.AMOUNT_EMPTY), empty)

        val invalidFormat = TransactionValidationRules.validateAmount("abc", Currency.TRY)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.AMOUNT_INVALID_FORMAT), invalidFormat)

        val nonPositive = TransactionValidationRules.validateAmount("0", Currency.TRY)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.AMOUNT_NON_POSITIVE), nonPositive)

        val excessiveDigits = TransactionValidationRules.validateAmount("10.555", Currency.USD)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS), excessiveDigits)
    }

    @Test
    fun validateCategory_withValidAndInvalidInputs_returnsExpectedResults() {
        val valid = TransactionValidationRules.validateCategory(EntityId("cat-123"))
        assertTrue(valid is TransactionValidationResult.Valid)
        assertEquals(EntityId("cat-123"), valid.value)

        val nullCat = TransactionValidationRules.validateCategory(null)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.CATEGORY_REQUIRED), nullCat)
    }

    @Test
    fun validateDate_withPastPresentAndFutureDates_returnsExpectedResults() {
        val past = LocalDate(2026, 8, 15)
        val validPast = TransactionValidationRules.validateDate(past, today)
        assertTrue(validPast is TransactionValidationResult.Valid)
        assertEquals(past, validPast.value)

        val validToday = TransactionValidationRules.validateDate(today, today)
        assertTrue(validToday is TransactionValidationResult.Valid)
        assertEquals(today, validToday.value)

        val future = LocalDate(2026, 8, 22)
        val invalidFuture = TransactionValidationRules.validateDate(future, today)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.DATE_FUTURE), invalidFuture)
    }

    @Test
    fun validateDescription_withValidNullAndOverlengthDescriptions_returnsExpectedResults() {
        val validDesc = TransactionValidationRules.validateDescription("Market alışverişi")
        assertTrue(validDesc is TransactionValidationResult.Valid)
        assertEquals("Market alışverişi", validDesc.value)

        val nullDesc = TransactionValidationRules.validateDescription(null)
        assertTrue(nullDesc is TransactionValidationResult.Valid)
        assertEquals(null, nullDesc.value)

        val blankDesc = TransactionValidationRules.validateDescription("   ")
        assertTrue(blankDesc is TransactionValidationResult.Valid)
        assertEquals(null, blankDesc.value)

        val overlength = "a".repeat(501)
        val invalidDesc = TransactionValidationRules.validateDescription(overlength)
        assertEquals(TransactionValidationResult.Invalid(TransactionValidationError.DESCRIPTION_TOO_LONG), invalidDesc)
    }
}
