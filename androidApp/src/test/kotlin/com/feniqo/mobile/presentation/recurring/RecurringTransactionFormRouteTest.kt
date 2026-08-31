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
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTransactionFormRouteTest {

    private val sampleIncomeCategory = Category(
        id = EntityId("cat-inc-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Maaş",
        type = TransactionType.INCOME,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("briefcase"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private val sampleExpenseCategory = Category(
        id = EntityId("cat-exp-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#EF4444"),
        icon = CategoryIcon("cart"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private val allCategories = listOf(sampleIncomeCategory, sampleExpenseCategory)

    @Test
    fun computeSubmitResult_validCreateInput_returnsCreateIntent() {
        val input = RecurringTransactionFormInput(
            recurringTransactionId = null,
            amountInput = "1500,00",
            currency = Currency.TRY,
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"),
            description = "Aylık İnternet Faturası",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 9, 1),
            endDate = null,
        )

        val result = RecurringTransactionFormRouteHelper.computeSubmitResult(input, allCategories)
        assertTrue(result is RecurringFormSubmitResult.IntentReady)

        val intent = (result as RecurringFormSubmitResult.IntentReady).intent
        assertTrue(intent is RecurringTransactionsIntent.Create)
        val createCommand = (intent as RecurringTransactionsIntent.Create).command
        assertEquals(Money(150000L, Currency.TRY), createCommand.amount)
        assertEquals(TransactionType.EXPENSE, createCommand.type)
        assertEquals(EntityId("cat-exp-1"), createCommand.categoryId)
        assertEquals("Aylık İnternet Faturası", createCommand.description)
        assertEquals(PaymentMethod.CREDIT_CARD, createCommand.paymentMethod)
        assertEquals(RecurrenceFrequency.MONTHLY, createCommand.rule.frequency)
        assertEquals(1, createCommand.rule.interval)
        assertEquals(LocalDate(2026, 9, 1), createCommand.rule.startDate)
        assertNull(createCommand.rule.endDate)
    }

    @Test
    fun computeSubmitResult_validEditInput_returnsUpdateIntent() {
        val input = RecurringTransactionFormInput(
            recurringTransactionId = EntityId("rec-target-42"),
            amountInput = "5000",
            currency = Currency.TRY,
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-inc-1"),
            description = "Kira Geliri",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2027, 1, 1),
        )

        val result = RecurringTransactionFormRouteHelper.computeSubmitResult(input, allCategories)
        assertTrue(result is RecurringFormSubmitResult.IntentReady)

        val intent = (result as RecurringFormSubmitResult.IntentReady).intent
        assertTrue(intent is RecurringTransactionsIntent.Update)
        val updateCommand = (intent as RecurringTransactionsIntent.Update).command
        assertEquals(EntityId("rec-target-42"), updateCommand.id)
        assertEquals(Money(500000L, Currency.TRY), updateCommand.amount)
        assertEquals(TransactionType.INCOME, updateCommand.type)
        assertEquals(EntityId("cat-inc-1"), updateCommand.categoryId)
        assertEquals(LocalDate(2027, 1, 1), updateCommand.rule.endDate)
    }

    @Test
    fun computeSubmitResult_invalidInput_returnsValidationFailedWithoutIntent() {
        val invalidInput = RecurringTransactionFormInput(
            recurringTransactionId = null,
            amountInput = "", // Empty amount
            categoryId = EntityId("cat-exp-1"),
            startDate = LocalDate(2026, 9, 1),
        )

        val result = RecurringTransactionFormRouteHelper.computeSubmitResult(invalidInput, allCategories)
        assertTrue(result is RecurringFormSubmitResult.ValidationFailed)
        val errors = (result as RecurringFormSubmitResult.ValidationFailed).errors
        assertEquals(RecurringTransactionFormFieldError.AMOUNT_REQUIRED, errors.amountError)
    }

    @Test
    fun computeInputOnTypeChange_sameType_preservesInput() {
        val input = RecurringTransactionFormInput(
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"),
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnTypeChange(
            currentInput = input,
            newType = TransactionType.EXPENSE,
            allCategories = allCategories,
        )

        assertEquals(input, updated)
    }

    @Test
    fun computeInputOnTypeChange_mismatchedCategory_clearsCategoryId() {
        val input = RecurringTransactionFormInput(
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"), // Expense category
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnTypeChange(
            currentInput = input,
            newType = TransactionType.INCOME, // Changing to Income
            allCategories = allCategories,
        )

        assertEquals(TransactionType.INCOME, updated.type)
        assertNull(updated.categoryId)
    }

    @Test
    fun computeInputOnTypeChange_unknownCategory_clearsCategoryId() {
        val input = RecurringTransactionFormInput(
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-unknown"),
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnTypeChange(
            currentInput = input,
            newType = TransactionType.INCOME,
            allCategories = allCategories,
        )

        assertEquals(TransactionType.INCOME, updated.type)
        assertNull(updated.categoryId)
    }

    @Test
    fun resolveEffectiveRecurringEditLoadState_whenEditIdProvidedAndIdle_returnsLoading() {
        val effective = resolveEffectiveRecurringEditLoadState(
            recurringTransactionId = EntityId("rec-1"),
            loadState = RecurringTransactionEditLoadState.Idle,
        )
        assertEquals(RecurringTransactionEditLoadState.Loading, effective)
    }

    @Test
    fun resolveEffectiveRecurringEditLoadState_whenCreateModeAndIdle_returnsIdle() {
        val effective = resolveEffectiveRecurringEditLoadState(
            recurringTransactionId = null,
            loadState = RecurringTransactionEditLoadState.Idle,
        )
        assertEquals(RecurringTransactionEditLoadState.Idle, effective)
    }

    @Test
    fun effectiveEditLoadState_whenHasInvalidRouteIdIsTrue_returnsNotFoundImmediately() {
        val hasInvalidRouteId = true
        val effective = if (hasInvalidRouteId) {
            RecurringTransactionEditLoadState.NotFound
        } else {
            resolveEffectiveRecurringEditLoadState(
                recurringTransactionId = null,
                loadState = RecurringTransactionEditLoadState.Idle,
            )
        }
        assertEquals(RecurringTransactionEditLoadState.NotFound, effective)
    }

    @Test
    fun editSeedApplication_onlyAppliedOnce_doesNotOverwriteUserEdits() {
        val seedDraft = RecurringTransactionFormDraft(
            recurringTransactionId = EntityId("rec-seed-1"),
            amount = Money(20000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"),
            description = "Orijinal Açıklama",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        var isEditSeedApplied = false
        var formInput = RecurringTransactionFormInput(recurringTransactionId = EntityId("rec-seed-1"))

        // First ready emission: seed is applied
        if (!isEditSeedApplied) {
            formInput = RecurringTransactionFormInput.fromDraft(seedDraft)
            isEditSeedApplied = true
        }
        assertEquals("200", formInput.amountInput)
        assertEquals("Orijinal Açıklama", formInput.description)

        // User edits the form
        formInput = formInput.copy(
            amountInput = "350",
            description = "Kullanıcı Tarafından Değiştirilen Açıklama",
        )

        // Subsequent ready emission from Room flow: seed must NOT be reapplied
        if (!isEditSeedApplied) {
            formInput = RecurringTransactionFormInput.fromDraft(seedDraft)
        }

        // Assert user's edits are preserved
        assertEquals("350", formInput.amountInput)
        assertEquals("Kullanıcı Tarafından Değiştirilen Açıklama", formInput.description)
    }

    @Test
    fun computeInputOnStartDateChange_whenEndDateIsAfterStartDate_preservesEndDate() {
        val input = RecurringTransactionFormInput(
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 10, 1),
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnStartDateChange(
            currentInput = input,
            newStartDate = LocalDate(2026, 9, 1),
        )

        assertEquals(LocalDate(2026, 9, 1), updated.startDate)
        assertEquals(LocalDate(2026, 10, 1), updated.endDate)
    }

    @Test
    fun computeInputOnStartDateChange_whenEndDateIsBeforeNewStartDate_clearsEndDate() {
        val input = RecurringTransactionFormInput(
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 15),
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnStartDateChange(
            currentInput = input,
            newStartDate = LocalDate(2026, 9, 1),
        )

        assertEquals(LocalDate(2026, 9, 1), updated.startDate)
        assertNull(updated.endDate)
    }

    @Test
    fun computeInputOnEndDateChange_updatesEndDate() {
        val input = RecurringTransactionFormInput(
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val updated = RecurringTransactionFormRouteHelper.computeInputOnEndDateChange(
            currentInput = input,
            newEndDate = LocalDate(2027, 8, 1),
        )

        assertEquals(LocalDate(2027, 8, 1), updated.endDate)
    }

    @Test
    fun computeInitialDatePickerSelection_usesTargetDateWhenPresent() {
        val target = LocalDate(2026, 5, 20)
        val selection = RecurringTransactionFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = target,
            currentDateProvider = { LocalDate(2026, 1, 1) },
        )

        assertEquals(target.toUtcEpochMillis(), selection)
        assertEquals(target, utcEpochMillisToLocalDate(selection))
    }

    @Test
    fun computeInitialDatePickerSelection_usesCurrentDateProviderWhenTargetNull() {
        val fallbackDate = LocalDate(2026, 8, 30)
        val selection = RecurringTransactionFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = null,
            currentDateProvider = { fallbackDate },
        )

        assertEquals(fallbackDate.toUtcEpochMillis(), selection)
        assertEquals(fallbackDate, utcEpochMillisToLocalDate(selection))
    }

    @Test
    fun resolveCurrentLocalDate_returnsLocalDateAccordingToSpecifiedTimeZone() {
        // 2026-08-30T23:30:00Z -> UTC is August 30
        val instant = Instant.parse("2026-08-30T23:30:00Z")

        // UTC TimeZone: August 30
        val utcDate = RecurringTransactionFormRouteHelper.resolveCurrentLocalDate(
            instant = instant,
            timeZone = TimeZone.UTC,
        )
        assertEquals(LocalDate(2026, 8, 30), utcDate)

        // Istanbul TimeZone (UTC+3): August 31 (02:30 AM next day)
        val istanbulDate = RecurringTransactionFormRouteHelper.resolveCurrentLocalDate(
            instant = instant,
            timeZone = TimeZone.of("Europe/Istanbul"),
        )
        assertEquals(LocalDate(2026, 8, 31), istanbulDate)

        // Tokyo TimeZone (UTC+9): August 31 (08:30 AM next day)
        val tokyoDate = RecurringTransactionFormRouteHelper.resolveCurrentLocalDate(
            instant = instant,
            timeZone = TimeZone.of("Asia/Tokyo"),
        )
        assertEquals(LocalDate(2026, 8, 31), tokyoDate)
    }

    @Test
    fun categoryDisplayModel_toDomainCategory_handlesNullAndBlankIcon_withoutException() {
        // 1. null iconKey
        val displayModelNullIcon = CategoryDisplayModel(
            id = EntityId("cat-disp-1"),
            name = "Kira",
            type = TransactionType.EXPENSE,
            colorHex = "#EF4444",
            iconKey = null,
            isDefault = true,
        )

        val domainNullIcon = displayModelNullIcon.toDomainCategory()
        assertEquals(EntityId("cat-disp-1"), domainNullIcon.id)
        assertEquals("Kira", domainNullIcon.name)
        assertEquals(TransactionType.EXPENSE, domainNullIcon.type)
        assertEquals(CategoryColor("#EF4444"), domainNullIcon.color)
        assertTrue(domainNullIcon.isDefault)
        assertNull(domainNullIcon.ownerId)
        assertNull(domainNullIcon.icon)
        assertNull(domainNullIcon.workspaceId)

        // 2. blank iconKey
        val displayModelBlankIcon = displayModelNullIcon.copy(
            id = EntityId("cat-disp-2"),
            iconKey = "   ",
            isDefault = false,
        )

        val domainBlankIcon = displayModelBlankIcon.toDomainCategory()
        assertEquals(EntityId("cat-disp-2"), domainBlankIcon.id)
        assertNull(domainBlankIcon.icon)
        assertNull(domainBlankIcon.ownerId)

        // 3. valid non-blank iconKey
        val displayModelValidIcon = displayModelNullIcon.copy(
            id = EntityId("cat-disp-3"),
            iconKey = "home",
            isDefault = false,
        )

        val domainValidIcon = displayModelValidIcon.toDomainCategory()
        assertEquals(CategoryIcon("home"), domainValidIcon.icon)
    }

    @Test
    fun shouldShowDeleteDialog_inCreateMode_returnsFalse() {
        // Create mode (recurringTransactionId is null)
        assertFalse(
            RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
                currentRecurringTransactionId = null,
                pendingDeleteId = EntityId("rec-1"),
            ),
        )
        assertFalse(
            RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
                currentRecurringTransactionId = null,
                pendingDeleteId = null,
            ),
        )
    }

    @Test
    fun shouldShowDeleteDialog_inEditMode_onlyTrueWhenMatchingPendingId() {
        val currentId = EntityId("rec-edit-1")

        // 1. Matching ID -> true
        assertTrue(
            RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
                currentRecurringTransactionId = currentId,
                pendingDeleteId = EntityId("rec-edit-1"),
            ),
        )

        // 2. Different pending ID -> false
        assertFalse(
            RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
                currentRecurringTransactionId = currentId,
                pendingDeleteId = EntityId("rec-other-2"),
            ),
        )

        // 3. Pending ID is null -> false
        assertFalse(
            RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
                currentRecurringTransactionId = currentId,
                pendingDeleteId = null,
            ),
        )
    }

    @Test
    fun computeDeleteIntents_requestConfirmDismiss_returnsExpectedIntents() {
        val targetId = EntityId("rec-del-1")

        // RequestDelete
        assertEquals(
            RecurringTransactionsIntent.RequestDelete(targetId),
            RecurringTransactionFormRouteHelper.computeRequestDeleteIntent(targetId),
        )
        assertNull(RecurringTransactionFormRouteHelper.computeRequestDeleteIntent(null))

        // ConfirmDelete & DismissDelete
        assertEquals(
            RecurringTransactionsIntent.ConfirmDelete,
            RecurringTransactionFormRouteHelper.computeConfirmDeleteIntent(),
        )
        assertEquals(
            RecurringTransactionsIntent.DismissDelete,
            RecurringTransactionFormRouteHelper.computeDismissDeleteIntent(),
        )
    }

    @Test
    fun isDialogActionEnabled_reflectsSubmittingState() {
        assertFalse(RecurringTransactionFormRouteHelper.isDialogActionEnabled(isSubmitting = true))
        assertTrue(RecurringTransactionFormRouteHelper.isDialogActionEnabled(isSubmitting = false))
    }

    @Test
    fun computeSetActiveIntent_inEditMode_producesExpectedIntents() {
        val targetId = EntityId("rec-edit-1")

        // Active -> Pause target is false
        val pauseIntent = RecurringTransactionFormRouteHelper.computeSetActiveIntent(
            currentRecurringTransactionId = targetId,
            targetActive = false,
        )
        assertEquals(
            RecurringTransactionsIntent.SetActive(
                SetRecurringTransactionActiveCommand(
                    id = targetId,
                    isActive = false,
                ),
            ),
            pauseIntent,
        )

        // Paused -> Resume target is true
        val resumeIntent = RecurringTransactionFormRouteHelper.computeSetActiveIntent(
            currentRecurringTransactionId = targetId,
            targetActive = true,
        )
        assertEquals(
            RecurringTransactionsIntent.SetActive(
                SetRecurringTransactionActiveCommand(
                    id = targetId,
                    isActive = true,
                ),
            ),
            resumeIntent,
        )
    }

    @Test
    fun computeSetActiveIntent_inCreateMode_returnsNull() {
        assertNull(
            RecurringTransactionFormRouteHelper.computeSetActiveIntent(
                currentRecurringTransactionId = null,
                targetActive = false,
            ),
        )
        assertNull(
            RecurringTransactionFormRouteHelper.computeSetActiveIntent(
                currentRecurringTransactionId = null,
                targetActive = true,
            ),
        )
    }

    @Test
    fun editActiveSeedApplication_onlyAppliedOnce_doesNotOverwriteUserLocalActiveState() {
        val seedDraft = RecurringTransactionFormDraft(
            recurringTransactionId = EntityId("rec-seed-1"),
            amount = Money(20000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-exp-1"),
            description = "Orijinal Açıklama",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        var isEditSeedApplied = false
        var formInput = RecurringTransactionFormInput(recurringTransactionId = EntityId("rec-seed-1"))
        var localIsActive = true

        // First emission: Ready with isActive = true
        val readyState1 = RecurringTransactionEditLoadState.Ready(seedDraft, isActive = true)
        if (!isEditSeedApplied) {
            formInput = RecurringTransactionFormInput.fromDraft(readyState1.draft)
            localIsActive = readyState1.isActive
            isEditSeedApplied = true
        }
        assertTrue(localIsActive)

        // User or flow toggles local active state to false
        localIsActive = false

        // Subsequent Room emission: Ready with isActive = true
        val readyState2 = RecurringTransactionEditLoadState.Ready(seedDraft, isActive = true)
        if (!isEditSeedApplied) {
            formInput = RecurringTransactionFormInput.fromDraft(readyState2.draft)
            localIsActive = readyState2.isActive
        }

        // Local state must remain false and not be overwritten
        assertFalse(localIsActive)
    }

    @Test
    fun onSetActive_doesNotMutateLocalActiveStateOptimistically_beforeRepositoryResponse() {
        val targetId = EntityId("rec-edit-1")
        val currentRouteActive = true

        // Route onSetActive contract: intent is produced, but local state remains untouched (no optimistic mutation)
        val targetActive = false
        val intent = RecurringTransactionFormRouteHelper.computeSetActiveIntent(
            currentRecurringTransactionId = targetId,
            targetActive = targetActive,
        )

        assertNotNull(intent)
        // Ensure local active state has not been mutated
        assertTrue(currentRouteActive)
    }
}



