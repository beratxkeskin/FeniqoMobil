package com.feniqo.mobile.presentation.asset

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.presentation.component.AssetTypeSelectionBottomSheet
import com.feniqo.mobile.presentation.component.CurrencySelectionBottomSheet
import com.feniqo.mobile.presentation.screen.AssetDetailScreen
import com.feniqo.mobile.presentation.screen.AssetDistributionScreen
import com.feniqo.mobile.presentation.screen.AssetFormScreen
import com.feniqo.mobile.presentation.screen.AssetsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class AssetsLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun assets_main_screen_and_components_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val singleUnitAsset = AssetDisplayModel(
            id = EntityId("a-1"),
            name = "Apple Inc",
            type = AssetType.STOCKS,
            currentValue = Money(125_050L, Currency.USD),
            currency = Currency.USD,
            quantity = AssetQuantity(1L, 0),
            trackingSymbol = "AAPL",
            autoTrack = true,
            valueSource = AssetValueSource.MARKET_FRESH,
        )

        val multiUnitAsset = AssetDisplayModel(
            id = EntityId("a-2"),
            name = "Gram Altın",
            type = AssetType.PRECIOUS_METALS,
            currentValue = Money(250_000L, Currency.TRY),
            currency = Currency.TRY,
            quantity = AssetQuantity(2L, 0),
            trackingSymbol = null,
            autoTrack = false,
            valueSource = AssetValueSource.MANUAL,
        )

        val netWorth = NetWorthDisplayModel(
            totals = listOf(Money(375_050L, Currency.USD)),
            currencyTotals = listOf(
                CurrencyTotalDisplayItem(
                    currency = Currency.USD,
                    total = Money(375_050L, Currency.USD),
                    assetCount = 2,
                ),
            ),
            assetCount = 2,
            hasMultipleCurrencies = false,
            primaryCurrency = Currency.USD,
            primaryTotal = Money(375_050L, Currency.USD),
            sourceSummary = NetWorthSourceSummary.MARKET_AND_MANUAL,
        )

        val distribution = AssetDistributionDisplayModel(
            currency = Currency.USD,
            overallTotal = Money(375_050L, Currency.USD),
            assetCount = 2,
            items = listOf(
                AssetDistributionItemDisplayModel(
                    type = AssetType.STOCKS,
                    total = Money(125_050L, Currency.USD),
                    percentageBps = 3_333,
                    assetCount = 1,
                ),
            ),
        )

        val state = AssetsUiState(
            isLoading = false,
            netWorth = netWorth,
            distributionSummary = distribution,
            assets = listOf(singleUnitAsset, multiUnitAsset),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                AssetsScreen(
                    state = state,
                    onBack = {},
                    onRetry = {},
                    onRefreshPrices = {},
                    onAddAsset = {},
                    onAssetClick = {},
                    onDistributionClick = {},
                )
            }
        }

        // TR: Başlık, USD formatı (özet: 3.750,50 $, öğe: 1.250,50 $), 1 birim ve 2 birim çoğul kontrolü
        composeRule.onNodeWithText("Varlıklar").assertIsDisplayed()
        composeRule.onNodeWithText("USD varlık toplamı").assertIsDisplayed()
        composeRule.onNodeWithText("3.750,50 $").assertIsDisplayed()
        composeRule.onNodeWithText("1.250,50 $").assertIsDisplayed()
        composeRule.onNodeWithText("2 varlık · Piyasa ve manuel değerler").assertIsDisplayed()
        composeRule.onNodeWithText("Dağılım").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dağılım detayını aç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hisse senedi").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Değerli metal").assertIsDisplayed()
        composeRule.onNodeWithText("1 birim · Güncel piyasa").assertIsDisplayed()
        composeRule.onNodeWithText("2 birim · Manuel giriş").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni varlık").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Başlık, USD formatı (özet: $3,750.50, öğe: $1,250.50), 1 unit ve 2 units çoğul kontrolü
        composeRule.onNodeWithText("Assets").assertIsDisplayed()
        composeRule.onNodeWithText("USD total assets").assertIsDisplayed()
        composeRule.onNodeWithText("$3,750.50").assertIsDisplayed()
        composeRule.onNodeWithText("$1,250.50").assertIsDisplayed()
        composeRule.onNodeWithText("2 assets · Market and manual values").assertIsDisplayed()
        composeRule.onNodeWithText("Distribution").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open distribution details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stocks").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Precious metals").assertIsDisplayed()
        composeRule.onNodeWithText("1 unit · Current market").assertIsDisplayed()
        composeRule.onNodeWithText("2 units · Manual entry").assertIsDisplayed()
        composeRule.onNodeWithText("New asset").assertIsDisplayed()
    }

    @Test
    fun asset_detail_and_delete_dialog_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val detailAsset = AssetDetailDisplayModel(
            id = EntityId("a-detail"),
            name = "Gram Altın",
            type = AssetType.PRECIOUS_METALS,
            currentValue = Money(250_000L, Currency.TRY),
            currency = Currency.TRY,
            quantity = AssetQuantity(2L, 0),
            purchaseUnitPrice = Money(200_000L, Currency.TRY),
            calculatedCost = Money(200_000L, Currency.TRY),
            difference = MoneyDelta(50_000L, Currency.TRY),
            differencePercentageBps = 2500,
            hasCalculatedCost = true,
            valueSource = AssetValueSource.MARKET_FRESH,
            isPriceVerificationFailed = false,
            canRetryPrice = false,
            trackingSymbol = "GLD",
            autoTrack = true,
        )

        val state = AssetDetailUiState(
            isLoading = false,
            asset = detailAsset,
            pendingDeleteConfirmation = true,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                AssetDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onRetryPrice = {},
                )
            }
        }

        // TR: Etiketler, para formatları, çoğul (2 birim) ve silme diyaloğu
        composeRule.onNodeWithText("Güncel toplam değer").assertIsDisplayed()
        composeRule.onNodeWithText("2.500,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Değer farkı").assertIsDisplayed()
        composeRule.onNodeWithText("+500,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("%25").assertIsDisplayed()
        composeRule.onNodeWithText("2 birim").assertIsDisplayed()
        composeRule.onNodeWithText("Varlık silinsin mi?").assertIsDisplayed()
        composeRule.onNodeWithText("Gram Altın kaydını silmek istediğine emin misin?").assertIsDisplayed()
        composeRule.onNodeWithText("Bu işlem bir satış emri oluşturmaz.").assertIsDisplayed()
        composeRule.onNodeWithText("Vazgeç").assertIsDisplayed()
        composeRule.onNodeWithText("Sil").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Etiketler, para formatları, çoğul (2 units) ve silme diyaloğu
        composeRule.onNodeWithText("Current total value").assertIsDisplayed()
        composeRule.onNodeWithText("2,500.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Value difference").assertIsDisplayed()
        composeRule.onNodeWithText("+500.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText("2 units").assertIsDisplayed()
        composeRule.onNodeWithText("Delete asset?").assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to delete Gram Altın?").assertIsDisplayed()
        composeRule.onNodeWithText("This action does not create a sell order.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun asset_distribution_and_donut_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val state = AssetDistributionUiState(
            isLoading = false,
            selectedCurrency = Currency.USD,
            availableCurrencies = listOf(Currency.USD),
            distribution = AssetDistributionDisplayModel(
                currency = Currency.USD,
                overallTotal = Money(100_000L, Currency.USD),
                assetCount = 1,
                items = listOf(
                    AssetDistributionItemDisplayModel(
                        type = AssetType.CASH,
                        total = Money(80_990L, Currency.USD),
                        percentageBps = 8099,
                        assetCount = 1,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                AssetDistributionScreen(
                    state = state,
                    onBack = {},
                    onSelectCurrency = {},
                    onRetry = {},
                )
            }
        }

        // TR: Başlık, USD toplam formatı ($ sonda), donut merkez etiketi, tür ve oran (%80,99)
        composeRule.onNodeWithText("Varlık dağılımı").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam varlık değeri").assertIsDisplayed()
        composeRule.onNodeWithText("1.000,00 $").assertIsDisplayed()
        composeRule.onNodeWithText("varlık").assertIsDisplayed()
        composeRule.onNodeWithText("Nakit").assertIsDisplayed()
        composeRule.onNodeWithText("%80,99").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Başlık, USD toplam formatı ($ başta), donut merkez etiketi (1 -> asset), tür ve oran (80.99%)
        composeRule.onNodeWithText("Asset distribution").assertIsDisplayed()
        composeRule.onNodeWithText("Total asset value").assertIsDisplayed()
        composeRule.onNodeWithText("$1,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("asset").assertIsDisplayed()
        composeRule.onNodeWithText("Cash").assertIsDisplayed()
        composeRule.onNodeWithText("80.99%").assertIsDisplayed()
    }

    @Test
    fun asset_form_errors_and_sheet_selectors_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val state = AssetFormUiState(
            input = AssetFormInput(
                assetId = null,
                nameInput = "",
                type = AssetType.CASH,
                currency = Currency.TRY,
                currentValueInput = "",
                quantityInput = "",
                purchaseUnitPriceInput = "",
                trackingSymbolInput = "",
                autoTrack = false,
            ),
            errors = AssetFormErrors(
                name = AssetFormFieldError.NAME_REQUIRED,
                currentValue = AssetFormFieldError.CURRENT_VALUE_REQUIRED,
            ),
            isSubmitting = false,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    AssetFormScreen(
                        state = state,
                        onBack = {},
                        onInputChange = {},
                        onSubmit = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onDismissDelete = {},
                    )
                    AssetTypeSelectionBottomSheet(
                        selectedType = AssetType.CASH,
                        onTypeSelect = {},
                        onDismiss = {},
                    )
                    CurrencySelectionBottomSheet(
                        selectedCurrency = Currency.TRY,
                        onCurrencySelect = {},
                        onDismiss = {},
                    )
                }
            }
        }

        // TR: Form başlığı, alan etiketleri, 2 temsili alan hatası, butonlar ve seçici modal metinleri
        composeRule.onNodeWithText("Yeni varlık").assertIsDisplayed()
        composeRule.onNodeWithText("Varlık adı").assertIsDisplayed()
        composeRule.onNodeWithText("Güncel toplam değer").assertIsDisplayed()
        composeRule.onNodeWithText("Varlık adı zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Güncel değer zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Varlığı kaydet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Varlık türü").assertCountEquals(2)
        composeRule.onNodeWithText("Para birimi seç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Seçili").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Form başlığı, alan etiketleri, 2 temsili alan hatası, butonlar ve seçici modal metinleri
        composeRule.onNodeWithText("New asset").assertIsDisplayed()
        composeRule.onNodeWithText("Asset name").assertIsDisplayed()
        composeRule.onNodeWithText("Current total value").assertIsDisplayed()
        composeRule.onNodeWithText("Asset name is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Current value is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Save asset").assertIsDisplayed()
        composeRule.onAllNodesWithText("Asset type").assertCountEquals(2)
        composeRule.onNodeWithText("Select currency").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }
}
