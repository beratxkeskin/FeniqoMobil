@file:Suppress(
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
)

package com.feniqo.mobile.presentation.transaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncEntityType
import com.feniqo.mobile.presentation.component.TransactionConflictDialog
import com.feniqo.mobile.presentation.component.WorkspaceConflictResolutionDialog
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import com.feniqo.mobile.presentation.screen.TransactionSuccessScreen
import com.feniqo.mobile.presentation.sync.WorkspaceConflictDialogState
import com.feniqo.mobile.presentation.sync.WorkspaceConflictUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionRemainingSurfacesLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detail_and_success_surfaces_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var showSuccess by mutableStateOf(false)
        val transaction = transaction()

        composeRule.setContent {
            App(languageTag = languageTag) {
                if (showSuccess) {
                    TransactionSuccessScreen(
                        uiState = TransactionSuccessUiState(isLoading = false, transaction = transaction),
                        onAddNewTransaction = {},
                        onViewTransaction = {},
                        onClose = {},
                    )
                } else {
                    TransactionDetailScreen(
                        item = transaction,
                        onBack = {},
                        onEdit = {},
                        onDelete = {},
                        onResolveConflict = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("İşlem detayı").assertIsDisplayed()
        composeRule.onNodeWithText("Kredi kartı").assertExists()
        composeRule.onNodeWithText("Çakışmayı çöz").assertExists()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Transaction details").assertIsDisplayed()
        composeRule.onNodeWithText("Credit card").assertExists()
        composeRule.onNodeWithText("Resolve conflict").assertExists()

        composeRule.runOnIdle { showSuccess = true }
        composeRule.onNodeWithText("Transaction Saved!").assertIsDisplayed()
        composeRule.onNodeWithText("Saved on this device. Changes are in conflict.").assertIsDisplayed()
        composeRule.onNodeWithText("View transaction").assertExists()

        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("İşlem Kaydedildi!").assertIsDisplayed()
    }

    @Test
    fun transaction_conflict_dialog_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val conflict = SyncConflict(
            entityId = EntityId("transaction-1"),
            entityType = SyncEntityType.TRANSACTION,
            localVersion = 1,
            remoteVersion = 2,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionConflictDialog(
                    conflict = conflict,
                    resolving = false,
                    error = TransactionSurfaceUiMessage.CONFLICT_RESOLUTION_FAILED,
                    onResolve = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Bu işlem iki cihazda değişti").assertIsDisplayed()
        composeRule.onNodeWithText("Bu cihazdakini kullan").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("This transaction changed on two devices").assertIsDisplayed()
        composeRule.onNodeWithText("Use this device's version").assertIsDisplayed()
        composeRule.onNodeWithText("The conflict could not be resolved. Try again.").assertExists()
    }

    @Test
    fun workspace_conflict_dialog_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspaceConflictDialogState(
            conflict = WorkspaceConflictUiModel(
                entityId = "workspace-1",
                localVersion = 3,
                remoteVersion = 4,
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspaceConflictResolutionDialog(
                    dialogState = state,
                    onResolve = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Çalışma Alanı Çakışması").assertIsDisplayed()
        composeRule.onNodeWithText("Yerel Sürüm: v3").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Workspace Conflict").assertIsDisplayed()
        composeRule.onNodeWithText("Local Version: v3").assertIsDisplayed()
        composeRule.onNodeWithText("Use Server Data").assertIsDisplayed()
    }

    private fun transaction() = TransactionDisplayModel(
        id = EntityId("transaction-1"),
        amount = Money(12_500L, Currency.TRY),
        formattedAmount = "₺125,00",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("category-1"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-bag",
        description = "Market alışverişi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 10, 5),
        installment = null,
        hasReceipt = false,
        note = null,
        syncStatus = SyncStatus.CONFLICT,
    )
}
