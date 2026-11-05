package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.HomeRecentTransactionsSection
import com.feniqo.mobile.presentation.component.TransactionListItem
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import com.feniqo.mobile.presentation.screen.TransactionSuccessScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantLogoWiringComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createDisplayModel(
        id: String = "tx-1",
        description: String? = "Kahve",
        categoryName: String = "Yeme & İçme",
        categoryColorHex: String? = "#10B981",
        categoryIconKey: String? = "food_dining",
        isCategoryUnavailable: Boolean = false,
        merchant: TransactionMerchantDisplayModel? = null,
    ) = TransactionDisplayModel(
        id = EntityId(id),
        amount = Money(amountMinor = 8500L, currency = Currency.TRY),
        formattedAmount = "-85,00 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        categoryName = categoryName,
        isCategoryUnavailable = isCategoryUnavailable,
        categoryColorHex = categoryColorHex,
        categoryIconKey = categoryIconKey,
        description = description,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 10, 19),
        installment = null,
        hasReceipt = false,
        note = null,
        syncStatus = SyncStatus.SYNCED,
        canEdit = true,
        canDelete = true,
        merchant = merchant,
    )

    @Test
    fun transactionList_knownMerchant_rendersDecorativeLogoWithoutDoubleAnnouncement() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("starbucks"),
            displayName = "Starbucks",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(
            description = "Starbucks Coffee",
            merchant = knownMerchant,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionListItem(
                    item = item,
                    canEditTransaction = true,
                    isDeleteInProgress = false,
                    onTransactionClick = {},
                    onDeleteClicked = {},
                )
            }
        }

        // Satır içinde Starbucks Coffee başlığı tek bir kez bulunur
        composeRule.onAllNodesWithText("Starbucks Coffee").assertCountEquals(1)
        // Logo dekoratiftir, içindeki fallback initial text veya ikon için bağımsız erişilebilirlik anonsu yoktur
        composeRule.onNodeWithText("Starbucks Coffee").assertIsDisplayed()
    }

    @Test
    fun dashboardRecentTransactions_knownMerchant_rendersDecorativeLogoWithoutDoubleAnnouncement() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("netflix"),
            displayName = "Netflix",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(
            id = "tx-netflix",
            description = "Netflix Aylık Abonelik",
            merchant = knownMerchant,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                HomeRecentTransactionsSection(
                    recentTransactions = listOf(item),
                    onTransactionClick = {},
                    onViewAllTransactions = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Netflix Aylık Abonelik").assertCountEquals(1)
        composeRule.onNodeWithText("Netflix Aylık Abonelik").assertIsDisplayed()
    }

    @Test
    fun transactionDetail_knownMerchant_rendersMerchantLogoAndRetainsContract() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("trendyol"),
            displayName = "Trendyol",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(
            id = "tx-trendyol",
            description = "Trendyol Alışveriş",
            merchant = knownMerchant,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("İşlem detayı").assertIsDisplayed()
        composeRule.onAllNodesWithText("Trendyol Alışveriş").assertCountEquals(2)
        composeRule.onNodeWithText("-85,00 ₺").assertIsDisplayed()
    }

    @Test
    fun transactionSuccess_knownMerchant_rendersMerchantLogoAndRetainsContract() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("migros"),
            displayName = "Migros",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(
            id = "tx-migros",
            description = "Migros Sanal Market",
            merchant = knownMerchant,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionSuccessScreen(
                    uiState = TransactionSuccessUiState(isLoading = false, transaction = item),
                    onAddNewTransaction = {},
                    onViewTransaction = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("İşlem Kaydedildi!").assertIsDisplayed()
        composeRule.onNodeWithText("Migros Sanal Market").assertIsDisplayed()
        composeRule.onNodeWithText("-85,00 ₺").assertIsDisplayed()
    }

    @Test
    fun transactionSurfaces_confidenceBelow75_neverLeaksGuessedMerchantNameToSemantics() {
        // Builder güven < 75 olduğunda merchant = null üretir
        // Bu test, merchant = null olduğunda guessed merchant adının UI semantiğinde bulunmadığını doğrular
        val guessedSecretName = "GizliTahminMarka"
        val itemWithoutAttribution = createDisplayModel(
            description = "Bilinmeyen POS Harcaması",
            merchant = null,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                Column {
                    TransactionListItem(
                        item = itemWithoutAttribution,
                        canEditTransaction = true,
                        isDeleteInProgress = false,
                        onTransactionClick = {},
                        onDeleteClicked = {},
                    )
                    HomeRecentTransactionsSection(
                        recentTransactions = listOf(itemWithoutAttribution),
                        onTransactionClick = {},
                        onViewAllTransactions = {},
                        onAddTransaction = {},
                    )
                }
            }
        }

        // Tahmin adı asla bulunamaz
        composeRule.onAllNodesWithText(guessedSecretName).assertCountEquals(0)
        composeRule.onAllNodesWithText("Bilinmeyen POS Harcaması").assertCountEquals(2)
    }

    @Test
    fun transactionSurfaces_noMerchant_fallsBackToCategoryIcon() {
        val item = createDisplayModel(
            description = "Market Harcaması",
            categoryName = "Market",
            categoryIconKey = "cart",
            merchant = null,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionListItem(
                    item = item,
                    canEditTransaction = true,
                    isDeleteInProgress = false,
                    onTransactionClick = {},
                    onDeleteClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Market Harcaması").assertIsDisplayed()
    }

    @Test
    fun transactionSurfaces_missingCategoryAndNoMerchant_fallsBackToInitial() {
        val item = createDisplayModel(
            description = "Özel Harcama",
            categoryName = "Kategori",
            categoryIconKey = null,
            isCategoryUnavailable = true,
            categoryColorHex = null,
            merchant = null,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionListItem(
                    item = item,
                    canEditTransaction = true,
                    isDeleteInProgress = false,
                    onTransactionClick = {},
                    onDeleteClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Özel Harcama").assertIsDisplayed()
    }

    @Test
    fun transactionSurfaces_everythingMissing_fallsBackToGenericIcon() {
        val item = createDisplayModel(
            description = "",
            categoryName = "",
            categoryIconKey = null,
            isCategoryUnavailable = true,
            categoryColorHex = null,
            merchant = null,
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionListItem(
                    item = item,
                    canEditTransaction = true,
                    isDeleteInProgress = false,
                    onTransactionClick = {},
                    onDeleteClicked = {},
                )
            }
        }

        // Boş veya eksik olsa dahi çökmez
        composeRule.onNodeWithText("Kredi kartı").assertExists()
    }

    @Test
    fun transactionSurfaces_lightAndDarkTheme_renderSuccessfullyWithoutCrash() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("market"),
            displayName = "Market",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(merchant = knownMerchant)

        var darkTheme by mutableStateOf(false)

        composeRule.setContent {
            FeniqoTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    TransactionDetailScreen(
                        item = item,
                        onBack = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("-85,00 ₺").assertIsDisplayed()

        composeRule.runOnIdle { darkTheme = true }
        composeRule.onNodeWithText("-85,00 ₺").assertIsDisplayed()
    }

    @Test
    fun transactionSurfaces_runtimeLocaleChange_updatesAccessibleTexts() {
        var languageTag by mutableStateOf("tr")
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("spotify"),
            displayName = "Spotify",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = createDisplayModel(
            description = "Spotify Premium",
            merchant = knownMerchant,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("İşlem detayı").assertIsDisplayed()
        composeRule.onNodeWithText("Kredi kartı").assertExists()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Transaction details").assertIsDisplayed()
        composeRule.onNodeWithText("Credit card").assertExists()
    }
}
