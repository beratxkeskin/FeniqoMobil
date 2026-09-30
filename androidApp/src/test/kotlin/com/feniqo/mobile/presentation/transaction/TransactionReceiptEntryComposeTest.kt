package com.feniqo.mobile.presentation.transaction

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionReceiptEntryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transactionForm_releaseGate_hidesReceiptEntryEvenWhenUiStateRequestsIt() {
        composeRule.setContent {
            FeniqoTheme {
                TransactionFormScreen(
                    uiState = TransactionFormUiState(isReceiptFeatureAvailable = true),
                    onAmountChange = {},
                    onCurrencyChange = {},
                    onTypeChange = {},
                    onCategoryChange = {},
                    onDateClick = {},
                    onDescriptionChange = {},
                    onPaymentMethodChange = {},
                    onInstallmentToggle = {},
                    onInstallmentCountChange = {},
                    onRetryCategories = {},
                    onSubmit = {},
                    onBack = {},
                    onDismissMessage = {},
                    onAttachReceipt = {},
                    onRemoveReceipt = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Ayrıntıları göster").performScrollTo().performClick()
        composeRule.onNodeWithText("Makbuz").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Makbuzdan bilgi oku").assertDoesNotExist()
    }

    @Test
    fun transactionDetail_exposesOnlySupportedEditNavigation() {
        var editCount = 0
        composeRule.setContent {
            FeniqoTheme {
                TransactionDetailScreen(
                    item = editableTransaction(hasReceipt = false),
                    onBack = {},
                    onEdit = { editCount++ },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Makbuzdan bilgi oku").assertDoesNotExist()
        composeRule.onNodeWithText("Makbuz bilgileri").assertDoesNotExist()
        composeRule.onNodeWithText("İşlemi düzenle")
            .performScrollTo()
            .assertExists()
            .performClick()

        assertEquals(1, editCount)
    }

    private fun editableTransaction(hasReceipt: Boolean) = TransactionDisplayModel(
        id = EntityId("11111111-1111-4111-8111-111111111111"),
        amount = Money(12_500L, Currency.TRY),
        formattedAmount = "₺125,00",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("22222222-2222-4222-8222-222222222222"),
        categoryName = "Market",
        categoryColorHex = null,
        categoryIconKey = null,
        description = "Haftalık alışveriş",
        paymentMethod = PaymentMethod.CASH,
        transactionDate = LocalDate(2026, 9, 30),
        installment = null,
        hasReceipt = hasReceipt,
    )
}
