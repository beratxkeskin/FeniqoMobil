package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SubscriptionValidationRulesTest {

    private val sampleStartDate = LocalDate(2026, 1, 1)
    private val sampleNextRenewalDate = LocalDate(2026, 2, 1)
    private val sampleAmount = Money(15000L, Currency.TRY)

    private fun sampleRule(
        interval: Int = 1,
        startDate: LocalDate = sampleStartDate,
        endDate: LocalDate? = null,
    ) = RecurrenceRule(
        frequency = RecurrenceFrequency.MONTHLY,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
    )

    private fun sampleSubscription(
        id: String = "sub-1",
        name: String = "Spotify",
        amount: Money = sampleAmount,
        categoryId: String? = "cat-1",
        rule: RecurrenceRule = sampleRule(),
        nextRenewalDate: LocalDate = sampleNextRenewalDate,
        isActive: Boolean = true,
    ) = Subscription(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = EntityId("ws-1"),
        name = name,
        amount = amount,
        categoryId = categoryId?.let(::EntityId),
        renewalRule = rule,
        nextRenewalDate = nextRenewalDate,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(5000L),
    )

    @Test
    fun validateCreateCommand_validCommand_trimsNameAndSucceeds() {
        val command = CreateSubscriptionCommand(
            name = "  YouTube Premium  ",
            amount = Money(7999L, Currency.TRY),
            categoryId = EntityId("cat-1"),
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )

        val result = SubscriptionValidationRules.validateCreateCommand(command)
        val valid = assertIs<SubscriptionValidationResult.Valid<CreateSubscriptionCommand>>(result)
        assertEquals("YouTube Premium", valid.value.name)
        assertEquals(Money(7999L, Currency.TRY), valid.value.amount)
        assertEquals(EntityId("cat-1"), valid.value.categoryId)
    }

    @Test
    fun validateCreateCommand_withNullableCategoryId_succeeds() {
        val command = CreateSubscriptionCommand(
            name = "Netflix",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )

        val result = SubscriptionValidationRules.validateCreateCommand(command)
        val valid = assertIs<SubscriptionValidationResult.Valid<CreateSubscriptionCommand>>(result)
        assertNull(valid.value.categoryId)
    }

    @Test
    fun validateCreateCommand_withBlankName_returnsNameBlankError() {
        val emptyCommand = CreateSubscriptionCommand(
            name = "",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )
        val spaceCommand = emptyCommand.copy(name = "   ")

        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_BLANK),
            SubscriptionValidationRules.validateCreateCommand(emptyCommand),
        )
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_BLANK),
            SubscriptionValidationRules.validateCreateCommand(spaceCommand),
        )
    }

    @Test
    fun validateCreateCommand_withTooLongName_returnsNameTooLongError() {
        val longName = "a".repeat(501)
        val command = CreateSubscriptionCommand(
            name = longName,
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )

        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_TOO_LONG),
            SubscriptionValidationRules.validateCreateCommand(command),
        )
    }

    @Test
    fun validateCreateCommand_withMaxLengthTrimmedName_succeeds() {
        val exact500Name = "a".repeat(500)
        val command = CreateSubscriptionCommand(
            name = "  $exact500Name  ",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )

        val result = SubscriptionValidationRules.validateCreateCommand(command)
        val valid = assertIs<SubscriptionValidationResult.Valid<CreateSubscriptionCommand>>(result)
        assertEquals(exact500Name, valid.value.name)
        assertEquals(500, valid.value.name.length)
    }

    @Test
    fun validateUpdateCommand_withTooLongName_returnsNameTooLongError() {
        val longName = "x".repeat(501)
        val command = UpdateSubscriptionCommand(
            id = EntityId("sub-1"),
            name = longName,
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
        )

        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_TOO_LONG),
            SubscriptionValidationRules.validateUpdateCommand(command, sampleNextRenewalDate),
        )
    }

    @Test
    fun validateAmount_withZeroOrNegativeAmount_returnsAmountNonPositiveError() {
        val zeroResult = SubscriptionValidationRules.validateAmount(Money(0L, Currency.TRY))
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE),
            zeroResult,
        )

        val negativeMinorResult = SubscriptionValidationRules.validateAmountMinor(-500L, Currency.TRY)
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE),
            negativeMinorResult,
        )

        val negativeInputResult = SubscriptionValidationRules.validateAmountInput("-500", Currency.TRY)
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE),
            negativeInputResult,
        )

        val zeroCreateCommand = CreateSubscriptionCommand(
            name = "iCloud",
            amount = Money(0L, Currency.TRY),
            categoryId = null,
            renewalRule = sampleRule(),
            nextRenewalDate = sampleNextRenewalDate,
        )
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE),
            SubscriptionValidationRules.validateCreateCommand(zeroCreateCommand),
        )
    }

    @Test
    fun recurrenceRule_invariantsEnforcement_andFailClosedRules() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 0,
                startDate = sampleStartDate,
                endDate = null,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 5, 1),
            )
        }
    }

    @Test
    fun validateCreateCommand_withNextRenewalDateOutsideRuleBounds_returnsExpectedErrors() {
        // Next renewal date before start date
        val beforeStartCommand = CreateSubscriptionCommand(
            name = "Hosting",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(startDate = LocalDate(2026, 5, 1)),
            nextRenewalDate = LocalDate(2026, 4, 30),
        )
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_BEFORE_START_DATE),
            SubscriptionValidationRules.validateCreateCommand(beforeStartCommand),
        )

        // Next renewal date after bounded end date
        val afterEndCommand = CreateSubscriptionCommand(
            name = "Hosting",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2026, 6, 1),
            ),
            nextRenewalDate = LocalDate(2026, 6, 2),
        )
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_AFTER_END_DATE),
            SubscriptionValidationRules.validateCreateCommand(afterEndCommand),
        )
    }

    @Test
    fun applySubscriptionUpdate_validCommand_updatesOnlyMutableFieldsAndPreservesImmutableFields() {
        val original = sampleSubscription(
            id = "sub-100",
            name = "Old Name",
            amount = Money(10000L, Currency.TRY),
            categoryId = "cat-old",
            isActive = true,
        )

        val updateCommand = UpdateSubscriptionCommand(
            id = EntityId("sub-100"),
            name = "  New Name  ",
            amount = Money(25000L, Currency.TRY),
            categoryId = EntityId("cat-new"),
            renewalRule = sampleRule(interval = 2),
        )

        val result = SubscriptionValidationRules.applySubscriptionUpdate(original, updateCommand)
        val valid = assertIs<SubscriptionValidationResult.Valid<Subscription>>(result)
        val updated = valid.value

        // Mutable fields updated
        assertEquals("New Name", updated.name)
        assertEquals(Money(25000L, Currency.TRY), updated.amount)
        assertEquals(EntityId("cat-new"), updated.categoryId)
        assertEquals(2, updated.renewalRule.interval)

        // Immutable fields preserved
        assertEquals(original.id, updated.id)
        assertEquals(original.ownerId, updated.ownerId)
        assertEquals(original.workspaceId, updated.workspaceId)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.nextRenewalDate, updated.nextRenewalDate)
        assertEquals(original.isActive, updated.isActive)
    }

    @Test
    fun applySubscriptionUpdate_idMismatch_failsClosedWithIllegalArgumentException() {
        val original = sampleSubscription(id = "sub-1")
        val mismatchCommand = UpdateSubscriptionCommand(
            id = EntityId("sub-2"),
            name = "Name",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(),
        )

        assertFailsWith<IllegalArgumentException> {
            SubscriptionValidationRules.applySubscriptionUpdate(original, mismatchCommand)
        }
    }

    @Test
    fun applySubscriptionUpdate_whenNewRuleInvalidatesExistingNextRenewalDate_returnsInvalidAndNoEntity() {
        val original = sampleSubscription(
            nextRenewalDate = LocalDate(2026, 3, 1),
        )

        // 1. New start date pushed after nextRenewalDate
        val newStartDateCommand = UpdateSubscriptionCommand(
            id = original.id,
            name = "Subscription",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(startDate = LocalDate(2026, 4, 1)),
        )
        val result1 = SubscriptionValidationRules.applySubscriptionUpdate(original, newStartDateCommand)
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_BEFORE_START_DATE),
            result1,
        )

        // 2. New end date pulled before nextRenewalDate
        val newEndDateCommand = UpdateSubscriptionCommand(
            id = original.id,
            name = "Subscription",
            amount = sampleAmount,
            categoryId = null,
            renewalRule = sampleRule(
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2026, 2, 28),
            ),
        )
        val result2 = SubscriptionValidationRules.applySubscriptionUpdate(original, newEndDateCommand)
        assertEquals(
            SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_AFTER_END_DATE),
            result2,
        )
    }

    @Test
    fun setSubscriptionActiveCommand_holdsDataDeterministically() {
        val pauseCommand = SetSubscriptionActiveCommand(id = EntityId("sub-1"), isActive = false)
        val resumeCommand = SetSubscriptionActiveCommand(id = EntityId("sub-1"), isActive = true)

        assertEquals(EntityId("sub-1"), pauseCommand.id)
        assertEquals(false, pauseCommand.isActive)

        assertEquals(EntityId("sub-1"), resumeCommand.id)
        assertEquals(true, resumeCommand.isActive)
    }
}
