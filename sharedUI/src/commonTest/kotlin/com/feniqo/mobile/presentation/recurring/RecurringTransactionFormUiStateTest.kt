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
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTransactionFormUiStateTest {

    @Test
    fun categoryFilter_filtersByDraftTransactionType() {
        val expenseCat1 = sampleCategory("cat-exp-1", "Kira", TransactionType.EXPENSE)
        val expenseCat2 = sampleCategory("cat-exp-2", "Market", TransactionType.EXPENSE)
        val incomeCat1 = sampleCategory("cat-inc-1", "Maaş", TransactionType.INCOME)
        val allCategories = listOf(expenseCat1, expenseCat2, incomeCat1)

        val expenseDraft = sampleDraft(type = TransactionType.EXPENSE, categoryId = "cat-exp-1")
        val expenseState = RecurringTransactionFormUiState.create(expenseDraft, allCategories)
        assertEquals(2, expenseState.availableCategories.size)
        assertTrue(expenseState.availableCategories.all { it.type == TransactionType.EXPENSE })
        assertEquals(expenseCat1, expenseState.selectedCategory)
        assertFalse(expenseState.isSelectedCategoryMissing)

        val incomeDraft = sampleDraft(type = TransactionType.INCOME, categoryId = "cat-inc-1")
        val incomeState = RecurringTransactionFormUiState.create(incomeDraft, allCategories)
        assertEquals(1, incomeState.availableCategories.size)
        assertEquals(TransactionType.INCOME, incomeState.availableCategories[0].type)
        assertEquals(incomeCat1, incomeState.selectedCategory)
        assertFalse(incomeState.isSelectedCategoryMissing)
    }

    @Test
    fun createDraft_toCreateCommand_producesExpectedCommand() {
        val draft = RecurringTransactionFormDraft(
            recurringTransactionId = null,
            amount = Money(75000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = " Fiber İnternet ",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2027, 8, 1),
        )

        assertTrue(draft.isCreateMode)
        assertFalse(draft.isEditMode)

        val command = draft.toCreateCommand()
        assertEquals(Money(75000L, Currency.TRY), command.amount)
        assertEquals(TransactionType.EXPENSE, command.type)
        assertEquals(EntityId("cat-1"), command.categoryId)
        assertEquals("Fiber İnternet", command.description)
        assertEquals(PaymentMethod.CREDIT_CARD, command.paymentMethod)
        assertEquals(RecurrenceFrequency.MONTHLY, command.rule.frequency)
        assertEquals(1, command.rule.interval)
        assertEquals(LocalDate(2026, 8, 1), command.rule.startDate)
        assertEquals(LocalDate(2027, 8, 1), command.rule.endDate)
    }

    @Test
    fun editDraft_toUpdateCommand_producesExpectedCommand() {
        val draft = RecurringTransactionFormDraft(
            recurringTransactionId = EntityId("rec-123"),
            amount = Money(120000L, Currency.USD),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-salary"),
            description = null,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            frequency = RecurrenceFrequency.YEARLY,
            interval = 2,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        assertFalse(draft.isCreateMode)
        assertTrue(draft.isEditMode)

        val command = draft.toUpdateCommand()
        assertEquals(EntityId("rec-123"), command.id)
        assertEquals(Money(120000L, Currency.USD), command.amount)
        assertEquals(TransactionType.INCOME, command.type)
        assertEquals(EntityId("cat-salary"), command.categoryId)
        assertNull(command.description)
        assertEquals(PaymentMethod.BANK_TRANSFER, command.paymentMethod)
        assertEquals(RecurrenceFrequency.YEARLY, command.rule.frequency)
        assertEquals(2, command.rule.interval)
        assertEquals(LocalDate(2026, 1, 1), command.rule.startDate)
        assertNull(command.rule.endDate)
    }

    @Test
    fun fromDomain_seedsAllFieldsFaithfully() {
        val domain = RecurringTransaction(
            id = EntityId("rec-origin"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(25000L, Currency.EUR),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-sub"),
            description = "Bulut Depolama",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 3,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 12, 31),
            ),
            lastGeneratedDate = LocalDate(2026, 6, 22),
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(123456L),
        )

        val draft = RecurringTransactionFormDraft.fromDomain(domain)

        assertEquals(EntityId("rec-origin"), draft.recurringTransactionId)
        assertEquals(domain.amount, draft.amount)
        assertEquals(domain.type, draft.type)
        assertEquals(domain.categoryId, draft.categoryId)
        assertEquals(domain.description, draft.description)
        assertEquals(domain.paymentMethod, draft.paymentMethod)
        assertEquals(domain.rule.frequency, draft.frequency)
        assertEquals(domain.rule.interval, draft.interval)
        assertEquals(domain.rule.startDate, draft.startDate)
        assertEquals(domain.rule.endDate, draft.endDate)
        assertTrue(draft.isEditMode)
    }

    @Test
    fun invalidModeCommandConversions_failClosed() {
        val createDraft = sampleDraft(recurringTransactionId = null)
        assertFailsWith<IllegalStateException> {
            createDraft.toUpdateCommand()
        }

        val editDraft = sampleDraft(recurringTransactionId = EntityId("rec-edit"))
        assertFailsWith<IllegalStateException> {
            editDraft.toCreateCommand()
        }
    }

    @Test
    fun endDate_nullAndNonNull_handledCorrectly() {
        val draftWithEndDate = sampleDraft(endDate = LocalDate(2027, 1, 1))
        assertEquals(LocalDate(2027, 1, 1), draftWithEndDate.toCreateCommand().rule.endDate)

        val draftWithoutEndDate = sampleDraft(endDate = null)
        assertNull(draftWithoutEndDate.toCreateCommand().rule.endDate)
    }

    @Test
    fun missingSelectedCategory_doesNotThrow_andIsFlaggedInState() {
        val draft = sampleDraft(type = TransactionType.EXPENSE, categoryId = "cat-deleted")
        val state = RecurringTransactionFormUiState.create(
            draft = draft,
            categories = emptyList(),
        )

        assertTrue(state.availableCategories.isEmpty())
        assertNull(state.selectedCategory)
        assertTrue(state.isSelectedCategoryMissing)
    }

    @Test
    fun selectedCategory_withTypeMismatch_isFilteredOutAndFlaggedAsMissing() {
        val incomeCategory = sampleCategory("cat-income-salary", "Maaş", TransactionType.INCOME)
        val expenseCategory = sampleCategory("cat-expense-rent", "Kira", TransactionType.EXPENSE)
        val allCategories = listOf(incomeCategory, expenseCategory)

        // Expense taslağı Income kategorisinin ID'sini gösteriyor
        val expenseDraft = sampleDraft(type = TransactionType.EXPENSE, categoryId = "cat-income-salary")

        val state = RecurringTransactionFormUiState.create(
            draft = expenseDraft,
            categories = allCategories,
        )

        assertEquals(1, state.availableCategories.size)
        assertEquals(expenseCategory, state.availableCategories[0])
        assertFalse(state.availableCategories.any { it.id == incomeCategory.id })
        assertNull(state.selectedCategory)
        assertTrue(state.isSelectedCategoryMissing)
    }

    private fun sampleDraft(
        recurringTransactionId: EntityId? = null,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "cat-1",
        endDate: LocalDate? = null,
    ) = RecurringTransactionFormDraft(
        recurringTransactionId = recurringTransactionId,
        amount = Money(50000L, Currency.TRY),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Test açıklaması",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 1,
        startDate = LocalDate(2026, 8, 1),
        endDate = endDate,
    )

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
