package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionsLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transactions_surface_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionsScreen(
                    state = TransactionsUiState(isLoading = false),
                    canAddTransaction = true,
                    onSearchQueryChanged = {},
                    onFilterClick = {},
                    onFilterDismiss = {},
                    onTypeFilterChanged = {},
                    onCategoryFilterChanged = {},
                    onPaymentMethodFilterChanged = {},
                    onPeriodPresetChanged = {},
                    onClearFilters = {},
                    onDeleteClicked = {},
                    onDismissDeleteDialog = {},
                    onConfirmSingleDelete = {},
                    onConfirmInstallmentDelete = {},
                    onRetryObservation = {},
                )
            }
        }

        composeRule.onNodeWithText("İşlemler").assertIsDisplayed()
        composeRule.onNodeWithText("Tüm zamanlar").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onAllNodesWithText("Transactions")[0].assertIsDisplayed()
        composeRule.onNodeWithText("All time").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("İşlemler").assertIsDisplayed()
    }

    @Test
    fun transaction_enum_labels_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    Text(TransactionType.EXPENSE.toLocalizedText())
                    Text(PaymentMethod.CREDIT_CARD.toLocalizedText())
                    Text(TransactionPeriodPreset.LAST_30_DAYS.toLocalizedText())
                    Text(TransactionSortOrder.AMOUNT_DESC.toLocalizedText())
                }
            }
        }

        composeRule.onNodeWithText("Gider").assertIsDisplayed()
        composeRule.onNodeWithText("Kredi kartı").assertIsDisplayed()
        composeRule.onNodeWithText("Son 30 gün").assertIsDisplayed()
        composeRule.onNodeWithText("Tutar: Azalan").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Credit card").assertIsDisplayed()
        composeRule.onNodeWithText("Last 30 days").assertIsDisplayed()
        composeRule.onNodeWithText("Amount: High to low").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Gider").assertIsDisplayed()
    }
}
