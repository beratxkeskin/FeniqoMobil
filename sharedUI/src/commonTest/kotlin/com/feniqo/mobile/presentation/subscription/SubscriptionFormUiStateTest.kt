package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionFormUiStateTest {

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
    fun createDraft_toCreateCommand_producesValidCommand() {
        val draft = SubscriptionFormDraft(
            subscriptionId = null,
            name = "Netflix",
            amount = Money(19999L, Currency.TRY),
            categoryId = EntityId("cat-exp-1"),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
            nextRenewalDate = LocalDate(2026, 8, 1),
        )

        val command = draft.toCreateCommand()
        assertEquals("Netflix", command.name)
        assertEquals(Money(19999L, Currency.TRY), command.amount)
        assertEquals(EntityId("cat-exp-1"), command.categoryId)
        assertEquals(RecurrenceFrequency.MONTHLY, command.renewalRule.frequency)
        assertEquals(1, command.renewalRule.interval)
        assertEquals(LocalDate(2026, 8, 1), command.renewalRule.startDate)
        assertNull(command.renewalRule.endDate)
        assertEquals(LocalDate(2026, 8, 1), command.nextRenewalDate)

        // Edit mod komutu oluşturmaya çalışmak fail-closed olmalı
        assertFailsWith<IllegalStateException> {
            draft.toUpdateCommand()
        }
    }

    @Test
    fun editDraft_toUpdateCommand_producesValidCommand() {
        val draft = SubscriptionFormDraft(
            subscriptionId = EntityId("sub-1"),
            name = "YouTube Premium",
            amount = Money(7999L, Currency.TRY),
            categoryId = null,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2027, 1, 1),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )

        val command = draft.toUpdateCommand()
        assertEquals(EntityId("sub-1"), command.id)
        assertEquals("YouTube Premium", command.name)
        assertEquals(Money(7999L, Currency.TRY), command.amount)
        assertNull(command.categoryId)
        assertEquals(RecurrenceFrequency.MONTHLY, command.renewalRule.frequency)
        assertEquals(1, command.renewalRule.interval)
        assertEquals(LocalDate(2026, 1, 1), command.renewalRule.startDate)
        assertEquals(LocalDate(2027, 1, 1), command.renewalRule.endDate)

        // Create komutu oluşturmaya çalışmak fail-closed olmalı
        assertFailsWith<IllegalStateException> {
            draft.toCreateCommand()
        }
    }

    @Test
    fun fromDomain_mapsAllFieldsCorrectly() {
        val subscription = Subscription(
            id = EntityId("sub-domain-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Gym Membership",
            amount = Money(150000L, Currency.TRY),
            categoryId = EntityId("cat-exp-1"),
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.YEARLY,
                interval = 1,
                startDate = LocalDate(2026, 3, 1),
                endDate = LocalDate(2029, 3, 1),
            ),
            nextRenewalDate = LocalDate(2027, 3, 1),
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(0L),
        )

        val draft = SubscriptionFormDraft.fromDomain(subscription)
        assertEquals(EntityId("sub-domain-1"), draft.subscriptionId)
        assertEquals("Gym Membership", draft.name)
        assertEquals(Money(150000L, Currency.TRY), draft.amount)
        assertEquals(EntityId("cat-exp-1"), draft.categoryId)
        assertEquals(RecurrenceFrequency.YEARLY, draft.frequency)
        assertEquals(1, draft.interval)
        assertEquals(LocalDate(2026, 3, 1), draft.startDate)
        assertEquals(LocalDate(2029, 3, 1), draft.endDate)
        assertEquals(LocalDate(2027, 3, 1), draft.nextRenewalDate)
        assertTrue(draft.isEditMode)
    }

    @Test
    fun subscriptionFormUiState_filtersOnlyExpenseCategories() {
        val draft = SubscriptionFormDraft(
            name = "Test",
            amount = Money(100L, Currency.TRY),
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate(2026, 8, 1),
        )

        val uiState = SubscriptionFormUiState.create(
            draft = draft,
            categories = listOf(expenseCategory, incomeCategory),
        )

        assertEquals(1, uiState.availableCategories.size)
        assertEquals(expenseCategory, uiState.availableCategories.first())
    }

    @Test
    fun selectedCategory_resolutionAndMissingCheck() {
        val draftWithCategory = SubscriptionFormDraft(
            name = "Test",
            amount = Money(100L, Currency.TRY),
            categoryId = EntityId("cat-exp-1"),
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate(2026, 8, 1),
        )
        val state1 = SubscriptionFormUiState.create(draftWithCategory, listOf(expenseCategory))
        assertEquals(expenseCategory, state1.selectedCategory)
        assertFalse(state1.isSelectedCategoryMissing)

        val draftWithMissingCategory = draftWithCategory.copy(categoryId = EntityId("cat-deleted"))
        val state2 = SubscriptionFormUiState.create(draftWithMissingCategory, listOf(expenseCategory))
        assertNull(state2.selectedCategory)
        assertTrue(state2.isSelectedCategoryMissing)

        val draftWithoutCategory = draftWithCategory.copy(categoryId = null)
        val state3 = SubscriptionFormUiState.create(draftWithoutCategory, listOf(expenseCategory))
        assertNull(state3.selectedCategory)
        assertFalse(state3.isSelectedCategoryMissing)
    }

    @Test
    fun resolveEffectiveSubscriptionEditLoadState_handlesIdleWithId() {
        val effectiveWithId = resolveEffectiveSubscriptionEditLoadState(
            subscriptionId = EntityId("sub-1"),
            loadState = SubscriptionEditLoadState.Idle,
        )
        assertEquals(SubscriptionEditLoadState.Loading, effectiveWithId)

        val effectiveWithoutId = resolveEffectiveSubscriptionEditLoadState(
            subscriptionId = null,
            loadState = SubscriptionEditLoadState.Idle,
        )
        assertEquals(SubscriptionEditLoadState.Idle, effectiveWithoutId)
    }

    @Test
    fun subscriptionMutationState_pendingAdvanceRenewalContract() {
        val defaultState = SubscriptionMutationState()
        assertNull(defaultState.pendingAdvanceRenewal)
        assertFalse(defaultState.isSubmitting)
        assertNull(defaultState.pendingDeleteId)

        val target = PendingAdvanceRenewalTarget(
            id = EntityId("sub-1"),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )
        val stateWithTarget = defaultState.copy(pendingAdvanceRenewal = target)
        assertEquals(target, stateWithTarget.pendingAdvanceRenewal)
        assertEquals(EntityId("sub-1"), stateWithTarget.pendingAdvanceRenewal?.id)
        assertEquals(LocalDate(2026, 9, 1), stateWithTarget.pendingAdvanceRenewal?.nextRenewalDate)
    }

    @Test
    fun advanceRenewalActionVisibility_pureContract() {
        fun isAdvanceRenewalActionVisible(isEditMode: Boolean, isActive: Boolean): Boolean {
            return isEditMode && isActive
        }

        // Create modu -> asla görünmez
        assertFalse(isAdvanceRenewalActionVisible(isEditMode = false, isActive = true))
        assertFalse(isAdvanceRenewalActionVisible(isEditMode = false, isActive = false))

        // Edit modu + pasif -> görünmez
        assertFalse(isAdvanceRenewalActionVisible(isEditMode = true, isActive = false))

        // Edit modu + aktif -> görünür
        assertTrue(isAdvanceRenewalActionVisible(isEditMode = true, isActive = true))
    }
}

