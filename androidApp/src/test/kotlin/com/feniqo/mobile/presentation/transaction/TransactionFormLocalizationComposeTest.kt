package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionFormLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun form_primary_surface_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionFormScreen(
                    uiState = TransactionFormUiState(),
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

        composeRule.onNodeWithText("Gider ekle").assertIsDisplayed()
        composeRule.onNodeWithText("Gideri kaydet").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Add expense").assertIsDisplayed()
        composeRule.onNodeWithText("Save expense").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Gider ekle").assertIsDisplayed()
    }

    @Test
    fun typed_validation_messages_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    Text(TransactionFormFieldError.AMOUNT_REQUIRED.toLocalizedText())
                    Text(TransactionFormFieldError.CATEGORY_UNAVAILABLE.toLocalizedText())
                    Text(TransactionFormFieldError.SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT.toLocalizedText())
                }
            }
        }

        composeRule.onNodeWithText("Tutar boş bırakılamaz.").assertIsDisplayed()
        composeRule.onNodeWithText("Bu kategori artık kullanılamıyor. Lütfen başka bir kategori seçin.").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("This category is no longer available. Select another category.").assertIsDisplayed()
        val customInstallmentMessage =
            "Custom amount splits cannot be used with installment transactions. Select an equal split or turn off installments."
        composeRule
            .onNodeWithText(customInstallmentMessage)
            .assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Tutar boş bırakılamaz.").assertIsDisplayed()
    }
}
