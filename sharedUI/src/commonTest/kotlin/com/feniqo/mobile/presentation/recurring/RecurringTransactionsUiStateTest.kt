package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTransactionsUiStateTest {

    @Test
    fun uiState_isEmpty_onlyTrueWhenNotLoadingNoErrorAndItemsEmpty() {
        // Initial / loading
        val loadingState = RecurringTransactionsUiState(
            isLoading = true,
            items = emptyList(),
            observationError = null,
        )
        assertFalse(loadingState.isEmpty)

        // Error state with empty list
        val errorState = RecurringTransactionsUiState(
            isLoading = false,
            items = emptyList(),
            observationError = FinanceUiMessage.GENERIC_ERROR,
        )
        assertFalse(errorState.isEmpty)

        // Populated state
        val populatedState = RecurringTransactionsUiState(
            isLoading = false,
            items = listOf(sampleDisplayModel("rec-1")),
            observationError = null,
        )
        assertFalse(populatedState.isEmpty)

        // Truly empty state
        val emptyState = RecurringTransactionsUiState(
            isLoading = false,
            items = emptyList(),
            observationError = null,
        )
        assertTrue(emptyState.isEmpty)
    }

    @Test
    fun displayModel_computedProperties_reflectStateAccurately() {
        val activeItem = sampleDisplayModel("rec-1", isActive = true, lastGeneratedDate = LocalDate(2026, 8, 1))
        assertTrue(activeItem.isActive)
        assertFalse(activeItem.isPaused)
        assertFalse(activeItem.isNeverGenerated)
        assertEquals("rec-1", activeItem.id.value)

        val pausedNeverGenerated = sampleDisplayModel("rec-2", isActive = false, lastGeneratedDate = null)
        assertFalse(pausedNeverGenerated.isActive)
        assertTrue(pausedNeverGenerated.isPaused)
        assertTrue(pausedNeverGenerated.isNeverGenerated)
        assertNull(pausedNeverGenerated.lastGeneratedDate)
    }

    @Test
    fun retryIntent_isSingleton() {
        val intent1: RecurringTransactionsIntent = RecurringTransactionsIntent.Retry
        val intent2: RecurringTransactionsIntent = RecurringTransactionsIntent.Retry
        assertEquals(intent1, intent2)
    }

    private fun sampleDisplayModel(
        id: String,
        isActive: Boolean = true,
        lastGeneratedDate: LocalDate? = null,
    ): RecurringTransactionDisplayModel = RecurringTransactionDisplayModel(
        id = EntityId(id),
        categoryId = EntityId("cat-1"),
        categoryName = "Kira",
        categoryColorHex = "#10B981",
        categoryIconKey = "home",
        isCategoryMissing = false,
        amount = Money(50000L, Currency.TRY),
        formattedAmount = "-500,00 ₺",
        currency = Currency.TRY,
        type = TransactionType.EXPENSE,
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 1,
        formattedFrequency = "Her ay",
        startDate = LocalDate(2026, 8, 1),
        formattedStartDate = "1 Ağustos 2026",
        endDate = null,
        formattedEndDate = null,
        lastGeneratedDate = lastGeneratedDate,
        formattedLastGeneratedDate = lastGeneratedDate?.let { "1 Ağustos 2026" },
        isNeverGenerated = lastGeneratedDate == null,
        nextOccurrenceDate = LocalDate(2026, 8, 1),
        formattedNextOccurrenceDate = "1 Ağustos 2026",
        isActive = isActive,
        isPaused = !isActive,
        description = "Kira ödemesi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
    )
}
