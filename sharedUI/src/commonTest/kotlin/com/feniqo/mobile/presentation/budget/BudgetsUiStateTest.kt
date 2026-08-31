package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetsUiStateTest {

    private val safeBudget = BudgetProgressDisplayModel(
        id = EntityId("b-1"),
        categoryId = EntityId("c-market"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-cart",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "2.000,00 ₺",
        limitMinor = 200_000L,
        formattedSpent = "1.000,00 ₺",
        spentMinor = 100_000L,
        formattedRemaining = "1.000,00 ₺",
        remainingMinor = 100_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 5_000,
        formattedUsageRate = "%50,00",
        usageProgressFraction = 0.5f,
        health = BudgetHealth.SAFE,
        excludedDifferentCurrencyTransactionCount = 0,
    )

    private val exceededBudgetWithExcludedTx = BudgetProgressDisplayModel(
        id = EntityId("b-2"),
        categoryId = EntityId("c-ulasim"),
        categoryName = "Ulaşım",
        categoryColorHex = "#EF4444",
        categoryIconKey = "car",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "500,00 ₺",
        limitMinor = 50_000L,
        formattedSpent = "650,00 ₺",
        spentMinor = 65_000L,
        formattedRemaining = "-150,00 ₺",
        remainingMinor = -15_000L,
        isRemainingNegative = true,
        usageRateBasisPoints = 13_000,
        formattedUsageRate = "%130,00",
        usageProgressFraction = 1.0f, // Clamped to 1f for progress indicator safety
        health = BudgetHealth.EXCEEDED,
        excludedDifferentCurrencyTransactionCount = 3,
    )

    @Test
    fun budgetsUiState_defaultValues_matchContract() {
        val state = BudgetsUiState()
        assertTrue(state.isLoading)
        assertNull(state.selectedMonth)
        assertTrue(state.budgets.isEmpty())
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
    }

    @Test
    fun budgetsUiState_isEmpty_returnsTrue_onlyWhenNotLoading_andNoError_andBudgetsEmpty() {
        val loadingEmpty = BudgetsUiState(isLoading = true, budgets = emptyList())
        assertFalse(loadingEmpty.isEmpty)

        val errorEmpty = BudgetsUiState(
            isLoading = false,
            budgets = emptyList(),
            observationError = FinanceUiMessage.GENERIC_ERROR,
        )
        assertFalse(errorEmpty.isEmpty)

        val loadedEmpty = BudgetsUiState(
            isLoading = false,
            budgets = emptyList(),
            observationError = null,
        )
        assertTrue(loadedEmpty.isEmpty)

        val loadedWithItems = BudgetsUiState(
            isLoading = false,
            budgets = listOf(safeBudget),
            observationError = null,
        )
        assertFalse(loadedWithItems.isEmpty)
    }

    @Test
    fun budgetProgressDisplayModel_exceededCard_holdsClampedProgressAndExactUsageRate() {
        assertEquals(1.0f, exceededBudgetWithExcludedTx.usageProgressFraction)
        assertEquals("%130,00", exceededBudgetWithExcludedTx.formattedUsageRate)
        assertEquals(13_000, exceededBudgetWithExcludedTx.usageRateBasisPoints)
        assertEquals(BudgetHealth.EXCEEDED, exceededBudgetWithExcludedTx.health)
        assertTrue(exceededBudgetWithExcludedTx.isRemainingNegative)
    }

    @Test
    fun budgetProgressDisplayModel_excludedTransactions_detectedCorrectly() {
        assertFalse(safeBudget.hasExcludedTransactions)
        assertEquals(0, safeBudget.excludedDifferentCurrencyTransactionCount)

        assertTrue(exceededBudgetWithExcludedTx.hasExcludedTransactions)
        assertEquals(3, exceededBudgetWithExcludedTx.excludedDifferentCurrencyTransactionCount)
    }

    @Test
    fun yearMonth_previousMonth_crossesYearBoundaryCorrectly() {
        assertEquals(YearMonth("2026-07"), YearMonth("2026-08").previousMonth())
        assertEquals(YearMonth("2025-12"), YearMonth("2026-01").previousMonth())
        assertEquals(YearMonth("2023-12"), YearMonth("2024-01").previousMonth())
    }

    @Test
    fun yearMonth_nextMonth_crossesYearBoundaryCorrectly() {
        assertEquals(YearMonth("2026-09"), YearMonth("2026-08").nextMonth())
        assertEquals(YearMonth("2027-01"), YearMonth("2026-12").nextMonth())
        assertEquals(YearMonth("2025-01"), YearMonth("2024-12").nextMonth())
    }

    @Test
    fun categoryIconResolver_resolvesKnownIconsAndHandlesNullOrUnknown() {
        assertEquals("🛒", CategoryIconResolver.resolveIconEmojiOrNull("shopping-cart"))
        assertEquals("🛒", CategoryIconResolver.resolveIconEmojiOrNull("cart"))
        assertEquals("🚗", CategoryIconResolver.resolveIconEmojiOrNull("car"))
        assertEquals("☕", CategoryIconResolver.resolveIconEmojiOrNull("coffee"))
        assertEquals("🍽️", CategoryIconResolver.resolveIconEmojiOrNull("utensils"))
        assertEquals("💼", CategoryIconResolver.resolveIconEmojiOrNull("briefcase"))
        assertEquals("🩺", CategoryIconResolver.resolveIconEmojiOrNull("heart-pulse"))

        assertNull(CategoryIconResolver.resolveIconEmojiOrNull(null))
        assertNull(CategoryIconResolver.resolveIconEmojiOrNull(""))
        assertNull(CategoryIconResolver.resolveIconEmojiOrNull("unknown-icon-key"))
    }

    @Test
    fun monthNavigation_invokesCallbackWithAccurateYearMonth() {
        var selectedMonth: YearMonth? = YearMonth("2026-08")
        val onMonthSelected: (YearMonth) -> Unit = { selectedMonth = it }

        // Önceki ay simülasyonu
        selectedMonth?.let { onMonthSelected(it.previousMonth()) }
        assertEquals(YearMonth("2026-07"), selectedMonth)

        // Sonraki ay simülasyonu
        val current = selectedMonth
        if (current != null) {
            onMonthSelected(current.nextMonth())
        }
        assertEquals(YearMonth("2026-08"), selectedMonth)

        // Yıl başı geri simülasyonu
        val janMonth = YearMonth("2026-01")
        onMonthSelected(janMonth.previousMonth())
        assertEquals(YearMonth("2025-12"), selectedMonth)
    }

    @Test
    fun retryCallback_invokesSuccessfullyOnErrorState() {
        var retryCount = 0
        val onRetry: () -> Unit = { retryCount++ }

        val errorState = BudgetsUiState(
            isLoading = false,
            selectedMonth = YearMonth("2026-08"),
            observationError = FinanceUiMessage.GENERIC_ERROR,
        )

        if (errorState.observationError != null) {
            onRetry()
        }

        assertEquals(1, retryCount)
    }

    @Test
    fun editBudgetCallback_passesCorrectIdAndMonthFromLoadedBudget() {
        var clickedId: EntityId? = null
        var clickedMonth: YearMonth? = null
        val onEditBudget: (EntityId, YearMonth) -> Unit = { id, month ->
            clickedId = id
            clickedMonth = month
        }

        val loadedState = BudgetsUiState(
            isLoading = false,
            selectedMonth = YearMonth("2026-08"),
            budgets = listOf(safeBudget, exceededBudgetWithExcludedTx),
        )

        // Liste doluyken kart tıklaması simülasyonu
        if (!loadedState.isLoading && loadedState.observationError == null && !loadedState.isEmpty) {
            val targetBudget = loadedState.budgets[1]
            onEditBudget(targetBudget.id, targetBudget.month)
        }

        assertEquals(EntityId("b-2"), clickedId)
        assertEquals(YearMonth("2026-08"), clickedMonth)
    }

    @Test
    fun editBudgetCallback_isNotInvokedWhenLoadingOrErrorOrEmpty() {
        var wasInvoked = false
        val onEditBudget: (EntityId, YearMonth) -> Unit = { _, _ -> wasInvoked = true }

        val loadingState = BudgetsUiState(isLoading = true, budgets = emptyList())
        val errorState = BudgetsUiState(isLoading = false, observationError = FinanceUiMessage.GENERIC_ERROR, budgets = emptyList())
        val emptyState = BudgetsUiState(isLoading = false, observationError = null, budgets = emptyList())

        fun simulateEdit(state: BudgetsUiState) {
            if (!state.isLoading && state.observationError == null && !state.isEmpty) {
                state.budgets.firstOrNull()?.let { onEditBudget(it.id, it.month) }
            }
        }

        simulateEdit(loadingState)
        simulateEdit(errorState)
        simulateEdit(emptyState)

        assertFalse(wasInvoked)
    }

    @Test
    fun deleteConfirmationState_holdsTargetAndProgressFlag() {
        val confirmation = BudgetDeleteConfirmationState(
            target = safeBudget,
            isDeleting = false,
        )
        assertEquals(EntityId("b-1"), confirmation.target.id)
        assertEquals("Market", confirmation.target.categoryName)
        assertEquals("2.000,00 ₺", confirmation.target.formattedLimit)
        assertFalse(confirmation.isDeleting)

        val deleting = confirmation.copy(isDeleting = true)
        assertTrue(deleting.isDeleting)
    }

    @Test
    fun requestDeleteCallback_passesCorrectTargetFromLoadedBudget() {
        var requestedTarget: BudgetProgressDisplayModel? = null
        val onRequestDelete: (BudgetProgressDisplayModel) -> Unit = { target ->
            requestedTarget = target
        }

        val loadedState = BudgetsUiState(
            isLoading = false,
            selectedMonth = YearMonth("2026-08"),
            budgets = listOf(safeBudget, exceededBudgetWithExcludedTx),
        )

        // Liste doluyken silme eylemi simülasyonu
        if (!loadedState.isLoading && loadedState.observationError == null && !loadedState.isEmpty) {
            val targetBudget = loadedState.budgets[0]
            onRequestDelete(targetBudget)
        }

        assertEquals(EntityId("b-1"), requestedTarget?.id)
        assertEquals("Market", requestedTarget?.categoryName)
        assertEquals("2.000,00 ₺", requestedTarget?.formattedLimit)
    }

    @Test
    fun requestDeleteCallback_isNotInvokedWhenLoadingOrErrorOrEmpty() {
        var wasInvoked = false
        val onRequestDelete: (BudgetProgressDisplayModel) -> Unit = { wasInvoked = true }

        val loadingState = BudgetsUiState(isLoading = true, budgets = emptyList())
        val errorState = BudgetsUiState(isLoading = false, observationError = FinanceUiMessage.GENERIC_ERROR, budgets = emptyList())
        val emptyState = BudgetsUiState(isLoading = false, observationError = null, budgets = emptyList())

        fun simulateDelete(state: BudgetsUiState) {
            if (!state.isLoading && state.observationError == null && !state.isEmpty) {
                state.budgets.firstOrNull()?.let { onRequestDelete(it) }
            }
        }

        simulateDelete(loadingState)
        simulateDelete(errorState)
        simulateDelete(emptyState)

        assertFalse(wasInvoked)
    }

    @Test
    fun budgetCopyConfirmationState_holdsSourceTargetAndProgressFlag() {
        val confirmation = BudgetCopyConfirmationState(
            sourceMonth = YearMonth("2026-07"),
            targetMonth = YearMonth("2026-08"),
            isCopying = false,
        )
        assertEquals(YearMonth("2026-07"), confirmation.sourceMonth)
        assertEquals(YearMonth("2026-08"), confirmation.targetMonth)
        assertFalse(confirmation.isCopying)

        val copying = confirmation.copy(isCopying = true)
        assertTrue(copying.isCopying)
    }

    @Test
    fun formatCopyResultMessage_formatsAllOutcomeVariationsCorrectly() {
        assertEquals("3 bütçe kopyalandı.", formatCopyResultMessage(copiedCount = 3, skippedCount = 0))
        assertEquals("3 bütçe kopyalandı; 2 mevcut bütçe atlandı.", formatCopyResultMessage(copiedCount = 3, skippedCount = 2))
        assertEquals("Kopyalanacak yeni bütçe bulunamadı; 2 mevcut bütçe atlandı.", formatCopyResultMessage(copiedCount = 0, skippedCount = 2))
        assertEquals("Kaynak ayda kopyalanacak bütçe bulunamadı.", formatCopyResultMessage(copiedCount = 0, skippedCount = 0))
    }

    @Test
    fun copyActionEnabledContract_disabledWhenLoadingOrError_enabledWhenLoaded() {
        val loadingState = BudgetsUiState(isLoading = true)
        assertFalse(!loadingState.isLoading && loadingState.observationError == null)

        val errorState = BudgetsUiState(isLoading = false, observationError = FinanceUiMessage.GENERIC_ERROR)
        assertFalse(!errorState.isLoading && errorState.observationError == null)

        val loadedState = BudgetsUiState(isLoading = false, observationError = null, selectedMonth = YearMonth("2026-08"))
        assertTrue(!loadedState.isLoading && loadedState.observationError == null)
    }
}
