package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import com.feniqo.mobile.presentation.screen.TransactionSuccessScreen
import com.feniqo.mobile.presentation.screen.TransactionsScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionAccessibilityComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun list_smallScreenAndLargeFont_stacksSummaryAndKeepsSearchClearTargetAccessible() {
        compose.setContent {
            LargeFontSmallScreen {
                TransactionsScreen(
                    state = TransactionsUiState(
                        isLoading = false,
                        searchQuery = "market",
                        summary = TransactionSummaryUiModel(
                            totalIncomeFormatted = "₺123.456.789,00",
                            totalSpendingFormatted = "₺98.765.432,00",
                            transactionCount = 42,
                        ),
                    ),
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

        compose.onNodeWithText("₺123.456.789,00").assertIsDisplayed()
        compose.onNodeWithText("₺98.765.432,00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Temizle").assertIsDisplayed()
    }

    @Test
    fun detail_smallScreenAndLargeFont_keepsLongContentAndActionsReachable() {
        val transaction = transaction(
            description = "Aylık ev alışverişi ve temizlik malzemeleri için çok uzun işlem adı",
            categoryName = "Market ve Ev İhtiyaçları Kategorisi",
            note = "Teslimat notu ve kampanya ayrıntıları ekran genişliğinden uzun olabilir.",
        )

        compose.setContent {
            LargeFontSmallScreen {
                TransactionDetailScreen(
                    item = transaction,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        compose.onNodeWithText("İşlemi düzenle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(transaction.note!!).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun success_smallScreenAndLargeFont_keepsLongContentAndActionsReachable() {
        val transaction = transaction(
            description = "Aylık ev alışverişi ve temizlik malzemeleri için çok uzun işlem adı",
            categoryName = "Market ve Ev İhtiyaçları Kategorisi",
            note = "Teslimat notu ve kampanya ayrıntıları ekran genişliğinden uzun olabilir.",
        )
        compose.setContent {
            LargeFontSmallScreen {
                TransactionSuccessScreen(
                    uiState = TransactionSuccessUiState(isLoading = false, transaction = transaction),
                    onAddNewTransaction = {},
                    onViewTransaction = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("İşlemi görüntüle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(transaction.note!!).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun form_smallScreenAndLargeFont_keepsSubmitActionVisibleAndExpandable() {
        compose.setContent {
            LargeFontSmallScreen {
                TransactionFormScreen(
                    uiState = TransactionFormUiState(
                        title = "Uzun başlıklı market ve ev ihtiyaçları işlemi",
                        description = "Uzun başlıklı market ve ev ihtiyaçları işlemi",
                    ),
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

        compose.onNodeWithContentDescription("Gideri kaydet").assertIsDisplayed()
    }

    @Composable
    private fun LargeFontSmallScreen(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
            FeniqoTheme {
                Box(Modifier.requiredSize(width = 320.dp, height = 480.dp)) {
                    content()
                }
            }
        }
    }

    private fun transaction(
        description: String,
        categoryName: String,
        note: String,
    ) = TransactionDisplayModel(
        id = EntityId("11111111-1111-4111-8111-111111111111"),
        amount = Money(12_345_678L, Currency.TRY),
        formattedAmount = "₺123.456,78",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("22222222-2222-4222-8222-222222222222"),
        categoryName = categoryName,
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-bag",
        description = description,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 30),
        installment = InstallmentDisplayModel(1, 12, "1/12"),
        hasReceipt = false,
        note = note,
    )
}
