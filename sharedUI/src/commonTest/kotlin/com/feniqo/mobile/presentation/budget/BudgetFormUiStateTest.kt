package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetFormUiStateTest {

    private val sampleCategories = listOf(
        CategoryDisplayModel(
            id = EntityId("cat-expense-1"),
            name = "Market",
            type = TransactionType.EXPENSE,
            colorHex = "#10B981",
            iconKey = "shopping-cart",
        ),
        CategoryDisplayModel(
            id = EntityId("cat-income-1"),
            name = "Maaş",
            type = TransactionType.INCOME,
            colorHex = "#3B82F6",
            iconKey = "dollar-sign",
        ),
        CategoryDisplayModel(
            id = EntityId("cat-expense-2"),
            name = "Ulaşım",
            type = TransactionType.EXPENSE,
            colorHex = "#EF4444",
            iconKey = "car",
        ),
    )

    @Test
    fun filterExpenseCategories_excludesIncomeCategories() {
        val expenseCategories = BudgetFormDraft.filterExpenseCategories(sampleCategories)

        assertEquals(2, expenseCategories.size)
        assertTrue(expenseCategories.all { it.type == TransactionType.EXPENSE })
        assertFalse(expenseCategories.any { it.id == EntityId("cat-income-1") })
    }

    @Test
    fun draft_toUiState_filtersExpenseCategoriesAndBindsState() {
        val draft = BudgetFormDraft(
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedMonth = YearMonth("2026-08"),
            limitInput = "1500,00",
            currency = Currency.TRY,
        )

        val uiState = draft.toUiState(
            categories = sampleCategories,
            mutationState = BudgetMutationState(isSubmitting = false),
        )

        assertFalse(uiState.isEditMode)
        assertEquals(2, uiState.availableCategories.size)
        assertEquals("Market", uiState.selectedCategoryName)
        assertEquals("#10B981", uiState.selectedCategoryColorHex)
        assertEquals(EntityId("cat-expense-1"), uiState.selectedCategoryId)
        assertEquals(YearMonth("2026-08"), uiState.selectedMonth)
        assertEquals("1500,00", uiState.limitInput)
        assertTrue(uiState.canSubmit)
    }

    @Test
    fun createMode_toSubmitIntent_producesCorrectCreateBudgetIntent() {
        val draft = BudgetFormDraft(
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedMonth = YearMonth("2026-08"),
            limitInput = "1500,50",
            currency = Currency.EUR,
        )

        val intent = draft.toSubmitIntent()

        assertTrue(intent is BudgetIntent.CreateBudget)
        assertEquals(EntityId("cat-expense-1"), intent.categoryId)
        assertEquals(YearMonth("2026-08"), intent.month)
        assertEquals("1500,50", intent.limitInput)
        assertEquals(Currency.EUR, intent.currency)
    }

    @Test
    fun editMode_toSubmitIntent_producesCorrectUpdateBudgetIntent() {
        val draft = BudgetFormDraft(
            budgetId = EntityId("b-existing"),
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedMonth = YearMonth("2026-08"),
            limitInput = "4200,00",
            currency = Currency.TRY,
        )

        val intent = draft.toSubmitIntent()

        assertTrue(intent is BudgetIntent.UpdateBudget)
        assertEquals(EntityId("b-existing"), intent.id)
        assertEquals("4200,00", intent.limitInput)
        assertEquals(Currency.TRY, intent.currency)
    }

    @Test
    fun editMode_invariants_categoryAndMonthNotEditable() {
        val state = BudgetFormUiState(
            budgetId = EntityId("b-100"),
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedCategoryName = "Market",
            selectedMonth = YearMonth("2026-08"),
            limitInput = "3000,00",
            currency = Currency.TRY,
        )

        assertTrue(state.isEditMode)
        assertFalse(state.isCategoryEditable) // In edit mode, category cannot be changed
        assertFalse(state.isMonthEditable) // In edit mode, month cannot be changed
        assertTrue(state.isFormEnabled)
        assertTrue(state.canSubmit)
    }

    @Test
    fun submittingState_disablesFormAndSubmission() {
        val state = BudgetFormUiState(
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedMonth = YearMonth("2026-08"),
            limitInput = "2500",
            mutationState = BudgetMutationState(isSubmitting = true),
        )

        assertTrue(state.isSubmitting)
        assertFalse(state.isFormEnabled)
        assertFalse(state.isCategoryEditable)
        assertFalse(state.isMonthEditable)
        assertFalse(state.canSubmit)
    }

    @Test
    fun fieldErrors_mapToCorrectDisplayText() {
        assertEquals("Lütfen bir harcama kategorisi seçin.", BudgetFormFieldError.CATEGORY_REQUIRED.toDisplayText())
        assertEquals("Lütfen bir ay seçin.", BudgetFormFieldError.MONTH_REQUIRED.toDisplayText())
        assertEquals("Geçersiz ay formatı (YYYY-AA).", BudgetFormFieldError.MONTH_INVALID_FORMAT.toDisplayText())
        assertEquals("Lütfen bir bütçe limiti girin.", BudgetFormFieldError.AMOUNT_REQUIRED.toDisplayText())
        assertEquals("Geçerli bir tutar girin.", BudgetFormFieldError.AMOUNT_INVALID_FORMAT.toDisplayText())
        assertEquals("Bütçe limiti 0'dan büyük olmalıdır.", BudgetFormFieldError.AMOUNT_NON_POSITIVE.toDisplayText())
        assertEquals("Kuruş hanesi en fazla 2 basamak olabilir.", BudgetFormFieldError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS.toDisplayText())
        assertEquals("Bütçe limiti izin verilen üst sınırı aşıyor.", BudgetFormFieldError.AMOUNT_MAX_EXCEEDED.toDisplayText())
        assertEquals("Kaynak ve hedef ay aynı olamaz.", BudgetFormFieldError.SOURCE_AND_TARGET_MONTH_SAME.toDisplayText())
    }

    @Test
    fun eventRouting_dispatchesToCorrectCallbacks() {
        var backCalled = false
        var messageReceived: FinanceUiMessage? = null

        val handleEvent: (BudgetUiEvent) -> Unit = { event ->
            when (event) {
                is BudgetUiEvent.MutationSuccess -> backCalled = true
                is BudgetUiEvent.ShowMessage -> messageReceived = event.message
                is BudgetUiEvent.CopyCompleted -> Unit
            }
        }

        handleEvent(BudgetUiEvent.MutationSuccess(FinanceUiMessage.BUDGET_SAVED))
        assertTrue(backCalled)
        assertNull(messageReceived)

        backCalled = false
        handleEvent(BudgetUiEvent.ShowMessage(FinanceUiMessage.STORAGE_ERROR))
        assertFalse(backCalled)
        assertEquals(FinanceUiMessage.STORAGE_ERROR, messageReceived)
    }

    @Test
    fun resolveEffectiveBudgetEditLoadState_whenEditModeAndIdle_returnsLoading() {
        val effective = resolveEffectiveBudgetEditLoadState(
            budgetId = EntityId("b-123"),
            loadState = BudgetEditLoadState.Idle,
        )
        assertEquals(BudgetEditLoadState.Loading, effective)
    }

    @Test
    fun resolveEffectiveBudgetEditLoadState_whenEditModeAndReady_returnsReady() {
        val seed = BudgetFormSeed(
            budgetId = EntityId("b-123"),
            categoryId = EntityId("cat-1"),
            month = YearMonth("2026-08"),
            limitInput = "100",
            currency = Currency.TRY,
        )
        val effective = resolveEffectiveBudgetEditLoadState(
            budgetId = EntityId("b-123"),
            loadState = BudgetEditLoadState.Ready(seed),
        )
        assertEquals(BudgetEditLoadState.Ready(seed), effective)
    }

    @Test
    fun resolveEffectiveBudgetEditLoadState_whenEditModeAndNotFoundOrError_preservesState() {
        val notFoundEffective = resolveEffectiveBudgetEditLoadState(
            budgetId = EntityId("b-123"),
            loadState = BudgetEditLoadState.NotFound,
        )
        assertEquals(BudgetEditLoadState.NotFound, notFoundEffective)

        val errorState = BudgetEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
        val errorEffective = resolveEffectiveBudgetEditLoadState(
            budgetId = EntityId("b-123"),
            loadState = errorState,
        )
        assertEquals(errorState, errorEffective)
    }

    @Test
    fun resolveEffectiveBudgetEditLoadState_whenCreateModeAndIdle_returnsIdle() {
        val effective = resolveEffectiveBudgetEditLoadState(
            budgetId = null,
            loadState = BudgetEditLoadState.Idle,
        )
        assertEquals(BudgetEditLoadState.Idle, effective)
    }
}
