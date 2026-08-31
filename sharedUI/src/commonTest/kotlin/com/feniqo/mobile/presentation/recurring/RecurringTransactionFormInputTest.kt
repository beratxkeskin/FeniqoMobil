package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTransactionFormInputTest {

    @Test
    fun validCreateInput_normalizesToValidDraft() {
        val input = RecurringTransactionFormInput(
            recurringTransactionId = null,
            amountInput = "1250,50",
            currency = Currency.TRY,
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"),
            description = "  Elektrik ve Su Faturası  ",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalInput = "2",
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2027, 8, 1),
        )

        val result = input.toDraft()
        assertTrue(result is RecurringTransactionFormNormalizationResult.Valid)
        val draft = result.draft

        assertNull(draft.recurringTransactionId)
        assertEquals(Money(125050L, Currency.TRY), draft.amount)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(EntityId("cat-exp-1"), draft.categoryId)
        assertEquals("Elektrik ve Su Faturası", draft.description)
        assertEquals(PaymentMethod.BANK_TRANSFER, draft.paymentMethod)
        assertEquals(RecurrenceFrequency.MONTHLY, draft.frequency)
        assertEquals(2, draft.interval)
        assertEquals(LocalDate(2026, 8, 1), draft.startDate)
        assertEquals(LocalDate(2027, 8, 1), draft.endDate)
    }

    @Test
    fun validEditInput_normalizesToValidDraftWithTargetId() {
        val input = RecurringTransactionFormInput(
            recurringTransactionId = EntityId("rec-edit-1"),
            amountInput = "500",
            currency = Currency.TRY,
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-inc-1"),
            description = "Kira Geliri",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            frequency = RecurrenceFrequency.YEARLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        val result = input.toDraft()
        assertTrue(result is RecurringTransactionFormNormalizationResult.Valid)
        val draft = result.draft

        assertEquals(EntityId("rec-edit-1"), draft.recurringTransactionId)
        assertEquals(Money(50000L, Currency.TRY), draft.amount)
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals(EntityId("cat-inc-1"), draft.categoryId)
        assertEquals("Kira Geliri", draft.description)
        assertEquals(PaymentMethod.BANK_TRANSFER, draft.paymentMethod)
        assertEquals(RecurrenceFrequency.YEARLY, draft.frequency)
        assertEquals(1, draft.interval)
        assertEquals(LocalDate(2026, 1, 1), draft.startDate)
        assertNull(draft.endDate)
    }

    @Test
    fun nonTryCurrency_parsesCorrectMinorUnits() {
        val usdInput = RecurringTransactionFormInput(
            amountInput = "49.99",
            currency = Currency.USD,
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 1),
        )
        val usdResult = usdInput.toDraft()
        assertTrue(usdResult is RecurringTransactionFormNormalizationResult.Valid)
        assertEquals(Money(4999L, Currency.USD), usdResult.draft.amount)

        val eurInput = RecurringTransactionFormInput(
            amountInput = "199,5",
            currency = Currency.EUR,
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 1),
        )
        val eurResult = eurInput.toDraft()
        assertTrue(eurResult is RecurringTransactionFormNormalizationResult.Valid)
        assertEquals(Money(19950L, Currency.EUR), eurResult.draft.amount)
    }

    @Test
    fun invalidAndEmptyAmount_returnsAmountErrors() {
        // 1. Empty amount
        val emptyInput = RecurringTransactionFormInput(
            amountInput = "   ",
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 1),
        )
        val emptyResult = emptyInput.toDraft()
        assertTrue(emptyResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.AMOUNT_REQUIRED, emptyResult.errors.amountError)

        // 2. Zero / non-positive amount
        val zeroInput = RecurringTransactionFormInput(
            amountInput = "0,00",
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 1),
        )
        val zeroResult = zeroInput.toDraft()
        assertTrue(zeroResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE, zeroResult.errors.amountError)

        // 3. Invalid format
        val invalidFormatInput = RecurringTransactionFormInput(
            amountInput = "12abc34",
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 1),
        )
        val formatResult = invalidFormatInput.toDraft()
        assertTrue(formatResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.AMOUNT_INVALID, formatResult.errors.amountError)
    }

    @Test
    fun invalidInterval_returnsIntervalErrors() {
        // Zero interval
        val zeroIntervalInput = RecurringTransactionFormInput(
            amountInput = "100",
            categoryId = EntityId("cat-1"),
            intervalInput = "0",
            startDate = LocalDate(2026, 8, 1),
        )
        val zeroResult = zeroIntervalInput.toDraft()
        assertTrue(zeroResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE, zeroResult.errors.intervalError)

        // Negative interval
        val negIntervalInput = RecurringTransactionFormInput(
            amountInput = "100",
            categoryId = EntityId("cat-1"),
            intervalInput = "-2",
            startDate = LocalDate(2026, 8, 1),
        )
        val negResult = negIntervalInput.toDraft()
        assertTrue(negResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE, negResult.errors.intervalError)

        // Non-number interval
        val textIntervalInput = RecurringTransactionFormInput(
            amountInput = "100",
            categoryId = EntityId("cat-1"),
            intervalInput = "xyz",
            startDate = LocalDate(2026, 8, 1),
        )
        val textResult = textIntervalInput.toDraft()
        assertTrue(textResult is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.INTERVAL_INVALID, textResult.errors.intervalError)
    }

    @Test
    fun endDateBeforeStartDate_returnsEndDateError() {
        val input = RecurringTransactionFormInput(
            amountInput = "100",
            categoryId = EntityId("cat-1"),
            startDate = LocalDate(2026, 8, 10),
            endDate = LocalDate(2026, 8, 5),
        )
        val result = input.toDraft()
        assertTrue(result is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE, result.errors.endDateError)
    }

    @Test
    fun categoryTypeMismatch_returnsCategoryTypeMismatchError() {
        val incomeCat = sampleCategory("cat-inc", "Maaş", TransactionType.INCOME)
        val expenseCat = sampleCategory("cat-exp", "Market", TransactionType.EXPENSE)
        val categories = listOf(incomeCat, expenseCat)

        val mismatchInput = RecurringTransactionFormInput(
            amountInput = "200",
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-inc"), // Expense transaction pointing to income category
            startDate = LocalDate(2026, 8, 1),
        )
        val result = mismatchInput.toDraft(categories)
        assertTrue(result is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH, result.errors.categoryError)
    }

    @Test
    fun unknownCategoryWhenCategoryListProvided_returnsCategoryRequiredError() {
        val incomeCat = sampleCategory("cat-inc", "Maaş", TransactionType.INCOME)
        val expenseCat = sampleCategory("cat-exp", "Market", TransactionType.EXPENSE)
        val categories = listOf(incomeCat, expenseCat)

        val unknownCategoryInput = RecurringTransactionFormInput(
            amountInput = "200",
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-deleted-or-unknown"),
            startDate = LocalDate(2026, 8, 1),
        )
        val result = unknownCategoryInput.toDraft(categories)
        assertTrue(result is RecurringTransactionFormNormalizationResult.Invalid)
        assertEquals(RecurringTransactionFormFieldError.CATEGORY_REQUIRED, result.errors.categoryError)
    }

    @Test
    fun blankDescription_normalizesToNull() {
        val input = RecurringTransactionFormInput(
            amountInput = "100",
            categoryId = EntityId("cat-1"),
            description = "    ",
            startDate = LocalDate(2026, 8, 1),
        )
        val result = input.toDraft()
        assertTrue(result is RecurringTransactionFormNormalizationResult.Valid)
        assertNull(result.draft.description)
    }

    @Test
    fun roundTrip_fromDraftToInputAndBackToDraft_preservesAllValues() {
        val originalDraft = RecurringTransactionFormDraft(
            recurringTransactionId = EntityId("rec-roundtrip-99"),
            amount = Money(345670L, Currency.TRY), // 3456,70 TL
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-99"),
            description = "Aylık Lisans Ücreti",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 3,
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2027, 5, 1),
        )

        val input = RecurringTransactionFormInput.fromDraft(originalDraft)
        assertEquals("3456,7", input.amountInput)
        assertEquals(Currency.TRY, input.currency)
        assertEquals(TransactionType.EXPENSE, input.type)
        assertEquals(EntityId("cat-exp-99"), input.categoryId)
        assertEquals("Aylık Lisans Ücreti", input.description)
        assertEquals(PaymentMethod.CREDIT_CARD, input.paymentMethod)
        assertEquals(RecurrenceFrequency.MONTHLY, input.frequency)
        assertEquals("3", input.intervalInput)
        assertEquals(LocalDate(2026, 5, 1), input.startDate)
        assertEquals(LocalDate(2027, 5, 1), input.endDate)

        val reDraftResult = input.toDraft()
        assertTrue(reDraftResult is RecurringTransactionFormNormalizationResult.Valid)
        val roundTripDraft = reDraftResult.draft

        assertEquals(originalDraft, roundTripDraft)
    }

    private fun sampleCategory(id: String, name: String, type: TransactionType) = Category(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        type = type,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("briefcase"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
