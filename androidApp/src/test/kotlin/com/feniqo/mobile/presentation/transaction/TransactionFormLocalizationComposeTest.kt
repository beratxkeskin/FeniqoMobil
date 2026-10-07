package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.resolveLocalizedText
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.TransactionSplitSection
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel
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
                    Text(CustomSplitInactiveMemberMessage.PAYER.toLocalizedText())
                    Text(FinanceUiMessage.NETWORK_ERROR.toLocalizedText())
                    Text(FinanceUiMessage.TRANSACTION_SAVED.toLocalizedText())
                }
            }
        }

        composeRule.onNodeWithText("Tutar boş bırakılamaz.").assertIsDisplayed()
        composeRule.onNodeWithText("Bu kategori artık kullanılamıyor. Lütfen başka bir kategori seçin.").assertIsDisplayed()
        composeRule.onNodeWithText("Harcamayı ödeyen kişi çalışma alanında aktif üye değil.").assertIsDisplayed()
        composeRule.onNodeWithText("Ağ bağlantısı kurulamadı. Lütfen internet bağlantınızı kontrol edin.").assertIsDisplayed()
        composeRule.onNodeWithText("İşlem başarıyla kaydedildi.").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("This category is no longer available. Select another category.").assertIsDisplayed()
        val customInstallmentMessage =
            "Custom amount splits cannot be used with installment transactions. Select an equal split or turn off installments."
        composeRule
            .onNodeWithText(customInstallmentMessage)
            .assertIsDisplayed()
        composeRule.onNodeWithText("The payer is no longer an active workspace member.").assertIsDisplayed()
        composeRule.onNodeWithText("Could not connect to the network. Check your internet connection.").assertIsDisplayed()
        composeRule.onNodeWithText("Transaction saved successfully.").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Tutar boş bırakılamaz.").assertIsDisplayed()
    }

    @Test
    fun split_editor_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val memberId = EntityId("member")
        val member =
            WorkspaceMemberUiModel(
                userId = memberId,
                displayName = "Ayşe",
                role = WorkspaceRole.EDITOR,
                isCurrentUser = true,
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionSplitSection(
                    members = listOf(member),
                    isLoading = false,
                    selectedPaidByUserId = memberId,
                    selectedParticipantUserIds = setOf(memberId),
                    onPaidByUserSelected = {},
                    onParticipantToggled = {},
                    errorText = null,
                    enabled = true,
                    splitMode = TransactionSplitMode.EQUAL,
                    currency = Currency.TRY,
                    amountText = "100",
                )
            }
        }

        composeRule.onNodeWithText("Gider paylaşımı").assertIsDisplayed()
        composeRule.onNodeWithText("Paylaşım şekli").assertExists()
        composeRule.onNodeWithContentDescription("Ayşe ödeyen kişi seçimi").assertExists()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Expense sharing").assertIsDisplayed()
        composeRule.onNodeWithText("Split method").assertExists()
        composeRule.onNodeWithContentDescription("Select Ayşe as payer").assertExists()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Gider paylaşımı").assertIsDisplayed()
    }

    @Test
    fun suspend_finance_message_resolver_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                var message by remember { mutableStateOf("") }
                LaunchedEffect(languageTag) {
                    message = FinanceUiMessage.NETWORK_ERROR.resolveLocalizedText()
                }
                Text(message)
            }
        }

        composeRule
            .onNodeWithText("Ağ bağlantısı kurulamadı. Lütfen internet bağlantınızı kontrol edin.")
            .assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule
            .onNodeWithText("Could not connect to the network. Check your internet connection.")
            .assertIsDisplayed()
    }
}
