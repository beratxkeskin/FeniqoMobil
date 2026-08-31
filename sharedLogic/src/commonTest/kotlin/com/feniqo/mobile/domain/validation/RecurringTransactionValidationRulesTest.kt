package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class RecurringTransactionValidationRulesTest {

    private val validAmount = Money(10000L, Currency.TRY)
    private val validCategoryId = EntityId("cat-1")
    private val validRule = RecurrenceRule(
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 1,
        startDate = LocalDate(2026, 8, 1),
        endDate = LocalDate(2026, 12, 31),
    )

    @Test
    fun validCreateCommand_returnsValid() {
        val command = CreateRecurringTransactionCommand(
            amount = validAmount,
            type = TransactionType.EXPENSE,
            categoryId = validCategoryId,
            description = "  Netflix Aboneliği  ",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            rule = validRule,
        )

        val result = RecurringTransactionValidationRules.validateCreateCommand(command)

        assertTrue(result is RecurringTransactionValidationResult.Valid)
        assertEquals("Netflix Aboneliği", result.value.description)
        assertEquals(validAmount, result.value.amount)
        assertEquals(TransactionType.EXPENSE, result.value.type)
        assertEquals(validCategoryId, result.value.categoryId)
    }

    @Test
    fun validUpdateCommand_returnsValid() {
        val command = UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = validAmount,
            type = TransactionType.EXPENSE,
            categoryId = validCategoryId,
            description = "Abonelik",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            rule = validRule,
        )

        val result = RecurringTransactionValidationRules.validateUpdateCommand(
            command = command,
            existingLastGeneratedDate = LocalDate(2026, 8, 1),
        )

        assertTrue(result is RecurringTransactionValidationResult.Valid)
        assertEquals(EntityId("rec-1"), result.value.id)
    }

    @Test
    fun nonPositiveAmount_returnsInvalid() {
        val zeroResult = RecurringTransactionValidationRules.validateAmount(Money(0L, Currency.TRY))
        assertTrue(zeroResult is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE, zeroResult.error)

        val negativeMinorResult = RecurringTransactionValidationRules.validateAmountMinor(-500L, Currency.TRY)
        assertTrue(negativeMinorResult is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE, negativeMinorResult.error)

        val negativeInputResult = RecurringTransactionValidationRules.validateAmountInput("-500", Currency.TRY)
        assertTrue(negativeInputResult is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE, negativeInputResult.error)

        assertFailsWith<IllegalArgumentException> {
            Money(-500L, Currency.TRY)
        }
    }

    @Test
    fun emptyOrWhitespaceDescription_normalizesToNull() {
        val emptyResult = RecurringTransactionValidationRules.validateDescription("")
        assertTrue(emptyResult is RecurringTransactionValidationResult.Valid)
        assertNull(emptyResult.value)

        val blankResult = RecurringTransactionValidationRules.validateDescription("   ")
        assertTrue(blankResult is RecurringTransactionValidationResult.Valid)
        assertNull(blankResult.value)

        val nullResult = RecurringTransactionValidationRules.validateDescription(null)
        assertTrue(nullResult is RecurringTransactionValidationResult.Valid)
        assertNull(nullResult.value)
    }

    @Test
    fun descriptionTooLong_returnsInvalid() {
        val longDesc = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1)
        val result = RecurringTransactionValidationRules.validateDescription(longDesc)

        assertTrue(result is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.DESCRIPTION_TOO_LONG, result.error)
    }

    @Test
    fun intervalNonPositive_failsClosed() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 0,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            )
        }
    }

    @Test
    fun endDateBeforeStartDate_failsClosed() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 10),
                endDate = LocalDate(2026, 8, 5),
            )
        }
    }

    @Test
    fun updateStartDateAfterLastGeneratedDate_returnsInvalid() {
        val lastGenerated = LocalDate(2026, 8, 15)
        val newRule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 20), // Moved forward past last generated
            endDate = null,
        )

        val result = RecurringTransactionValidationRules.validateRuleUpdate(
            rule = newRule,
            lastGeneratedDate = lastGenerated,
        )

        assertTrue(result is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.START_DATE_AFTER_LAST_GENERATED, result.error)
    }

    @Test
    fun updateEndDateBeforeLastGeneratedDate_returnsInvalid() {
        val lastGenerated = LocalDate(2026, 8, 15)
        val newRule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 10), // Moved backwards before last generated
        )

        val result = RecurringTransactionValidationRules.validateRuleUpdate(
            rule = newRule,
            lastGeneratedDate = lastGenerated,
        )

        assertTrue(result is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.END_DATE_BEFORE_LAST_GENERATED, result.error)
    }

    @Test
    fun applyRecurringRuleUpdate_validUpdate_returnsValidAndPreservesImmutableFields() {
        val originalCreatedAt = Instant.fromEpochMilliseconds(1700000000000L)
        val originalLastGenerated = LocalDate(2026, 8, 15)

        val existing = RecurringTransaction(
            id = EntityId("rec-1"),
            ownerId = EntityId("user-1"),
            workspaceId = EntityId("ws-1"),
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-old"),
            description = "Eski açıklama",
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            lastGeneratedDate = originalLastGenerated,
            isActive = true,
            createdAt = originalCreatedAt,
        )

        val updateCommand = UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = Money(7500L, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-new"),
            description = "  Yeni açıklama  ",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                startDate = LocalDate(2026, 8, 1),
                endDate = LocalDate(2026, 12, 31),
            ),
        )

        val result = RecurringTransactionValidationRules.applyRecurringRuleUpdate(existing, updateCommand)

        assertTrue(result is RecurringTransactionValidationResult.Valid)
        val updated = result.value

        // Preserved immutable fields
        assertEquals(EntityId("rec-1"), updated.id)
        assertEquals(EntityId("user-1"), updated.ownerId)
        assertEquals(EntityId("ws-1"), updated.workspaceId)
        assertEquals(originalCreatedAt, updated.createdAt)
        assertEquals(originalLastGenerated, updated.lastGeneratedDate)
        assertEquals(true, updated.isActive)

        // Modified editable fields
        assertEquals(Money(7500L, Currency.TRY), updated.amount)
        assertEquals(TransactionType.INCOME, updated.type)
        assertEquals(EntityId("cat-new"), updated.categoryId)
        assertEquals("Yeni açıklama", updated.description)
        assertEquals(PaymentMethod.BANK_TRANSFER, updated.paymentMethod)
        assertEquals(updateCommand.rule, updated.rule)
    }

    @Test
    fun applyRecurringRuleUpdate_startDateAfterLastGenerated_returnsInvalidAndProducesNoEntity() {
        val existing = RecurringTransaction(
            id = EntityId("rec-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            lastGeneratedDate = LocalDate(2026, 8, 15),
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(1700000000000L),
        )

        val invalidCommand = UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                startDate = LocalDate(2026, 8, 20), // After lastGeneratedDate (8-15)
                endDate = null,
            ),
        )

        val result = RecurringTransactionValidationRules.applyRecurringRuleUpdate(existing, invalidCommand)

        assertTrue(result is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.START_DATE_AFTER_LAST_GENERATED, result.error)
    }

    @Test
    fun applyRecurringRuleUpdate_endDateBeforeLastGenerated_returnsInvalidAndProducesNoEntity() {
        val existing = RecurringTransaction(
            id = EntityId("rec-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            lastGeneratedDate = LocalDate(2026, 8, 15),
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(1700000000000L),
        )

        val invalidCommand = UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = LocalDate(2026, 8, 10), // Before lastGeneratedDate (8-15)
            ),
        )

        val result = RecurringTransactionValidationRules.applyRecurringRuleUpdate(existing, invalidCommand)

        assertTrue(result is RecurringTransactionValidationResult.Invalid)
        assertEquals(RecurringTransactionValidationError.END_DATE_BEFORE_LAST_GENERATED, result.error)
    }

    @Test
    fun applyRecurringRuleUpdate_failsClosedOnIdMismatch() {
        val existing = RecurringTransaction(
            id = EntityId("rec-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = validRule,
            lastGeneratedDate = null,
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(1700000000000L),
        )

        val mismatchCommand = UpdateRecurringTransactionCommand(
            id = EntityId("rec-2"),
            amount = Money(7500L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = validRule,
        )

        assertFailsWith<IllegalArgumentException> {
            RecurringTransactionValidationRules.applyRecurringRuleUpdate(existing, mismatchCommand)
        }
    }

    @Test
    fun setRecurringTransactionActiveCommand_carriesOnlyIdAndIsActive() {
        val pauseCommand = SetRecurringTransactionActiveCommand(
            id = EntityId("rec-1"),
            isActive = false,
        )
        assertEquals(EntityId("rec-1"), pauseCommand.id)
        assertEquals(false, pauseCommand.isActive)

        val resumeCommand = SetRecurringTransactionActiveCommand(
            id = EntityId("rec-1"),
            isActive = true,
        )
        assertEquals(EntityId("rec-1"), resumeCommand.id)
        assertEquals(true, resumeCommand.isActive)
    }
}
