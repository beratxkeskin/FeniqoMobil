package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoriesUiState
import com.feniqo.mobile.presentation.transaction.TransactionFilterUiModel
import com.feniqo.mobile.presentation.transaction.TransactionFormUiState
import com.feniqo.mobile.presentation.transaction.TransactionPeriodPreset
import com.feniqo.mobile.presentation.transaction.TransactionsUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceUiStateTest {

    @Test
    fun transactionFormUiState_defaultValues_haveNullDateAndCashPayment() {
        val state = TransactionFormUiState()
        assertNull(state.transactionDate)
        assertEquals(PaymentMethod.CASH, state.paymentMethod)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertFalse(state.isEditMode)
        assertFalse(state.isInstallmentOptionAvailable)
    }

    @Test
    fun transactionFormUiState_installmentOptionAvailability_followsBusinessRules() {
        // Default: EXPENSE + CASH -> false
        val defaultState = TransactionFormUiState()
        assertFalse(defaultState.isInstallmentOptionAvailable)

        // NEW + EXPENSE + CREDIT_CARD -> true
        val creditCardExpense = defaultState.copy(paymentMethod = PaymentMethod.CREDIT_CARD)
        assertTrue(creditCardExpense.isInstallmentOptionAvailable)

        // NEW + INCOME + CREDIT_CARD -> false
        val creditCardIncome = creditCardExpense.copy(type = TransactionType.INCOME)
        assertFalse(creditCardIncome.isInstallmentOptionAvailable)

        // EDIT MODE + EXPENSE + CREDIT_CARD -> false
        val editModeCreditCardExpense = creditCardExpense.copy(isEditMode = true)
        assertFalse(editModeCreditCardExpense.isInstallmentOptionAvailable)
    }

    @Test
    fun transactionFilterUiModel_activeFilterCount_calculatesCorrectly() {
        val emptyFilter = TransactionFilterUiModel()
        assertEquals(0, emptyFilter.activeFilterCount)

        val oneFilter = emptyFilter.copy(type = TransactionType.EXPENSE)
        assertEquals(1, oneFilter.activeFilterCount)

        val allFilters = TransactionFilterUiModel(
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-1"),
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            periodPreset = TransactionPeriodPreset.THIS_MONTH,
            workspaceId = EntityId("ws-1"),
        )
        assertEquals(5, allFilters.activeFilterCount)
    }

    @Test
    fun listStates_initialLoadingState_isTrue() {
        val transactionsState = TransactionsUiState()
        assertTrue(transactionsState.isLoading)

        val categoriesState = CategoriesUiState()
        assertTrue(categoriesState.isLoading)
    }
}
