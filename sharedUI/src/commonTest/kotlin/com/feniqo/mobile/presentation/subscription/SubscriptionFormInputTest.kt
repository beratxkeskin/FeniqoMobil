package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionFormInputTest {

    private val expenseCategory = Category(
        id = EntityId("cat-exp-1"),
        ownerId = null,
        workspaceId = null,
        name = "Abonelikler",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#FF0000"),
        icon = CategoryIcon("music"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(0L),
    )

    private val incomeCategory = Category(
        id = EntityId("cat-inc-1"),
        ownerId = null,
        workspaceId = null,
        name = "Maaş",
        type = TransactionType.INCOME,
        color = CategoryColor("#00FF00"),
        icon = CategoryIcon("cash"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(0L),
    )

    @Test
    fun validCreateInput_normalizesToValidDraft() {
        val input = SubscriptionFormInput(
            subscriptionId = null,
            nameInput = "  Spotify Premium  ",
            amountInput = "59,99",
            currency = Currency.TRY,
            categoryId = EntityId("cat-exp-1"),
            frequency = RecurrenceFrequency.MONTHLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2027, 8, 1),
        )

        val result = input.toDraft(listOf(expenseCategory))
        assertTrue(result is SubscriptionFormNormalizationResult.Valid)
        val draft = result.draft

        assertNull(draft.subscriptionId)
        assertEquals("Spotify Premium", draft.name)
        assertEquals(Money(5999L, Currency.TRY), draft.amount)
        assertEquals(EntityId("cat-exp-1"), draft.categoryId)
        assertEquals(RecurrenceFrequency.MONTHLY, draft.frequency)
        assertEquals(1, draft.interval)
        assertEquals(LocalDate(2026, 8, 1), draft.startDate)
        assertEquals(LocalDate(2027, 8, 1), draft.endDate)
        assertEquals(LocalDate(2026, 8, 1), draft.nextRenewalDate)
        assertTrue(draft.isCreateMode)
        assertFalse(draft.isEditMode)
    }

    @Test
    fun validEditInput_normalizesToValidDraftWithTargetIdAndRetainedNextRenewalDate() {
        val input = SubscriptionFormInput(
            subscriptionId = EntityId("sub-edit-1"),
            nameInput = "Netflix",
            amountInput = "199,99",
            currency = Currency.TRY,
            categoryId = null,
            frequency = RecurrenceFrequency.YEARLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
            nextRenewalDate = LocalDate(2027, 1, 1),
        )

        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Valid)
        val draft = result.draft

        assertEquals(EntityId("sub-edit-1"), draft.subscriptionId)
        assertEquals("Netflix", draft.name)
        assertEquals(Money(19999L, Currency.TRY), draft.amount)
        assertNull(draft.categoryId)
        assertEquals(RecurrenceFrequency.YEARLY, draft.frequency)
        assertEquals(1, draft.interval)
        assertEquals(LocalDate(2026, 1, 1), draft.startDate)
        assertNull(draft.endDate)
        assertEquals(LocalDate(2027, 1, 1), draft.nextRenewalDate)
        assertTrue(draft.isEditMode)
        assertFalse(draft.isCreateMode)
    }

    @Test
    fun incomeCategory_triggersTypeMismatchError() {
        val input = SubscriptionFormInput(
            nameInput = "Gym",
            amountInput = "1000",
            categoryId = EntityId("cat-inc-1"),
            startDate = LocalDate(2026, 8, 1),
        )

        val result = input.toDraft(listOf(incomeCategory))
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.CATEGORY_TYPE_MISMATCH, result.errors.categoryError)
    }

    @Test
    fun blankName_triggersNameRequiredError() {
        val input = SubscriptionFormInput(
            nameInput = "   ",
            amountInput = "100",
            startDate = LocalDate(2026, 8, 1),
        )

        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.NAME_REQUIRED, result.errors.nameError)
    }

    @Test
    fun nameTooLong_triggersNameTooLongError() {
        val longName = "A".repeat(Subscription.MAX_NAME_LENGTH + 1)
        val input = SubscriptionFormInput(
            nameInput = longName,
            amountInput = "100",
            startDate = LocalDate(2026, 8, 1),
        )

        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.NAME_TOO_LONG, result.errors.nameError)
    }

    @Test
    fun invalidAmount_triggersAppropriateErrors() {
        val emptyAmount = SubscriptionFormInput(nameInput = "Test", amountInput = "", startDate = LocalDate(2026, 8, 1)).toDraft()
        assertTrue(emptyAmount is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.AMOUNT_REQUIRED, emptyAmount.errors.amountError)

        val zeroAmount = SubscriptionFormInput(nameInput = "Test", amountInput = "0", startDate = LocalDate(2026, 8, 1)).toDraft()
        assertTrue(zeroAmount is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.AMOUNT_NON_POSITIVE, zeroAmount.errors.amountError)

        val invalidFormat = SubscriptionFormInput(nameInput = "Test", amountInput = "abc", startDate = LocalDate(2026, 8, 1)).toDraft()
        assertTrue(invalidFormat is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.AMOUNT_INVALID, invalidFormat.errors.amountError)
    }

    @Test
    fun invalidInterval_triggersErrors() {
        val nonNumber = SubscriptionFormInput(nameInput = "Test", amountInput = "100", intervalInput = "abc", startDate = LocalDate(2026, 8, 1)).toDraft()
        assertTrue(nonNumber is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.INTERVAL_INVALID, nonNumber.errors.intervalError)

        val nonPositive = SubscriptionFormInput(nameInput = "Test", amountInput = "100", intervalInput = "0", startDate = LocalDate(2026, 8, 1)).toDraft()
        assertTrue(nonPositive is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.INTERVAL_NON_POSITIVE, nonPositive.errors.intervalError)
    }

    @Test
    fun missingStartDate_triggersStartDateRequired() {
        val input = SubscriptionFormInput(
            nameInput = "Test",
            amountInput = "100",
            startDate = null,
        )
        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.START_DATE_REQUIRED, result.errors.startDateError)
    }

    @Test
    fun endDateBeforeStartDate_triggersEndDateBeforeStartDate() {
        val input = SubscriptionFormInput(
            nameInput = "Test",
            amountInput = "100",
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 7, 31),
        )
        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.END_DATE_BEFORE_START_DATE, result.errors.endDateError)
    }

    @Test
    fun categoryNotFound_triggersCategoryNotFoundError() {
        val input = SubscriptionFormInput(
            nameInput = "Gym",
            amountInput = "1000",
            categoryId = EntityId("cat-deleted-999"),
            startDate = LocalDate(2026, 8, 1),
        )

        val result = input.toDraft(listOf(expenseCategory))
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.CATEGORY_NOT_FOUND, result.errors.categoryError)
    }

    @Test
    fun differentCurrencies_arePreservedInCommands() {
        listOf(Currency.TRY, Currency.USD, Currency.EUR).forEach { selectedCurrency ->
            val createInput = SubscriptionFormInput(
                nameInput = "Cloud Server",
                amountInput = "19,99",
                currency = selectedCurrency,
                startDate = LocalDate(2026, 8, 1),
            )
            val createResult = createInput.toDraft()
            assertTrue(createResult is SubscriptionFormNormalizationResult.Valid)
            val createCommand = createResult.draft.toCreateCommand()
            assertEquals(Money(1999L, selectedCurrency), createCommand.amount)

            val editInput = SubscriptionFormInput(
                subscriptionId = EntityId("sub-123"),
                nameInput = "Cloud Server",
                amountInput = "19,99",
                currency = selectedCurrency,
                startDate = LocalDate(2026, 8, 1),
                nextRenewalDate = LocalDate(2026, 9, 1),
            )
            val editResult = editInput.toDraft()
            assertTrue(editResult is SubscriptionFormNormalizationResult.Valid)
            val updateCommand = editResult.draft.toUpdateCommand()
            assertEquals(Money(1999L, selectedCurrency), updateCommand.amount)
        }
    }

    @Test
    fun editMode_startDateAfterNextRenewalDate_triggersError() {
        val input = SubscriptionFormInput(
            subscriptionId = EntityId("sub-edit-1"),
            nameInput = "Netflix",
            amountInput = "199,99",
            startDate = LocalDate(2026, 9, 15),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )

        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.START_DATE_AFTER_NEXT_RENEWAL, result.errors.startDateError)
    }

    @Test
    fun editMode_endDateBeforeNextRenewalDate_triggersError() {
        val input = SubscriptionFormInput(
            subscriptionId = EntityId("sub-edit-1"),
            nameInput = "Netflix",
            amountInput = "199,99",
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 8, 15),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )

        val result = input.toDraft()
        assertTrue(result is SubscriptionFormNormalizationResult.Invalid)
        assertEquals(SubscriptionFormFieldError.END_DATE_BEFORE_NEXT_RENEWAL, result.errors.endDateError)
    }

    @Test
    fun fromDraft_seedsInputFaithfully() {
        val draft = SubscriptionFormDraft(
            subscriptionId = EntityId("sub-seed-1"),
            name = "iCloud Storage",
            amount = Money(2999L, Currency.TRY),
            categoryId = EntityId("cat-exp-1"),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2028, 5, 1),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )

        val input = SubscriptionFormInput.fromDraft(draft)
        assertEquals(EntityId("sub-seed-1"), input.subscriptionId)
        assertEquals("iCloud Storage", input.nameInput)
        assertEquals("29,99", input.amountInput)
        assertEquals(Currency.TRY, input.currency)
        assertEquals(EntityId("cat-exp-1"), input.categoryId)
        assertEquals(RecurrenceFrequency.MONTHLY, input.frequency)
        assertEquals("1", input.intervalInput)
        assertEquals(LocalDate(2026, 5, 1), input.startDate)
        assertEquals(LocalDate(2028, 5, 1), input.endDate)
        assertEquals(LocalDate(2026, 9, 1), input.nextRenewalDate)
    }
}

