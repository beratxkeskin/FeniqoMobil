@file:Suppress(
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
)

package com.feniqo.mobile.presentation.transaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.MerchantBlockConfirmDialog
import com.feniqo.mobile.presentation.component.MerchantSelectorBottomSheet
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class TransactionDetailMerchantCorrectionComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTransaction(
        merchant: TransactionMerchantDisplayModel? = null,
        canCorrectMerchant: Boolean = true,
        description: String = "Migros Market",
    ) = TransactionDisplayModel(
        id = EntityId("tx-1"),
        amount = Money(amountMinor = 12500L, currency = Currency.TRY),
        formattedAmount = "-125,00 \u20BA",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        categoryName = "Market",
        isCategoryUnavailable = false,
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-bag",
        description = description,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 10, 20),
        installment = null,
        hasReceipt = false,
        note = null,
        syncStatus = SyncStatus.SYNCED,
        canEdit = true,
        canDelete = true,
        merchant = merchant,
        canCorrectMerchant = canCorrectMerchant,
    )

    // 12. Tan\u0131nm\u0131\u015F merchant b\u00F6l\u00FCm\u00FC do\u011Fru render edilir
    @Test
    fun scenario12_knownMerchantSection_rendersCorrectly() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("migros"),
            displayName = "Migros",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = sampleTransaction(merchant = knownMerchant, canCorrectMerchant = true)
        var changeClicked = false
        var blockClicked = false

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    onOpenMerchantSelector = { changeClicked = true },
                    onOpenBlockConfirmDialog = { blockClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Merchant olarak tan\u0131nd\u0131").assertExists()
        composeRule.onAllNodesWithText("Migros").assertCountEquals(1)
        composeRule.onNodeWithText("De\u011fi\u015ftir").assertExists().performClick()
        assertTrue(changeClicked)

        composeRule.onNodeWithText("Do\u011fru de\u011fil").assertExists().performClick()
        assertTrue(blockClicked)
    }

    // 13. Merchant yokken "\u0130\u015F yeri se\u00E7" render edilir
    @Test
    fun scenario13_noMerchant_eligibleTransaction_rendersSelectMerchantAction() {
        val item = sampleTransaction(merchant = null, canCorrectMerchant = true)
        var selectClicked = false

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    onOpenMerchantSelector = { selectClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("\u0130\u015f yeri se\u00e7").assertExists().performClick()
        assertTrue(selectClicked)
        // Tan\u0131nm\u0131\u015F merchant butonlar\u0131 ("De\u011fi\u015ftir", "Do\u011fru de\u011fil") bulunmamal\u0131
        composeRule.onNodeWithText("Merchant olarak tan\u0131nd\u0131").assertDoesNotExist()
        composeRule.onNodeWithText("De\u011fi\u015ftir").assertDoesNotExist()
        composeRule.onNodeWithText("Do\u011fru de\u011fil").assertDoesNotExist()
    }

    // 14. De\u011fi\u015Ftir / \u0130\u015F yeri se\u00E7 se\u00E7icisi arama ve sonu\u00E7 durumlar\u0131n\u0131 render eder
    @Test
    fun scenario14_merchantSelectorBottomSheet_searchAndSelectionState() {
        var query = "mig"
        var selectedId by mutableStateOf<EntityId?>(null)
        var assignConfirmed = false
        val items = listOf(
            MerchantSelectorItem(
                id = EntityId("migros"),
                displayName = "Migros",
                categoryIconKey = "shopping-bag",
                verifiedLogoKey = null,
            ),
        )

        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantSelectorBottomSheet(
                    isSelectorOpen = true,
                    searchQuery = query,
                    searchResults = items,
                    selectedMerchantId = selectedId,
                    isInProgress = false,
                    error = null,
                    onSearchQueryChanged = { query = it },
                    onSelectMerchant = { selectedId = it },
                    onConfirm = { assignConfirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("\u0130\u015f yeri se\u00e7in").assertExists()
        composeRule.onNodeWithText("Migros").assertExists().performClick()
        assertEquals(EntityId("migros"), selectedId)

        composeRule.onNodeWithText("Onayla").assertExists().performClick()
        assertTrue(assignConfirmed)
    }

    // 14b. Se\u00E7ici bo\u015F arama durumunda "Sonu\u00E7 bulunamad\u0131" render eder
    @Test
    fun scenario14b_merchantSelectorBottomSheet_emptyResults() {
        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantSelectorBottomSheet(
                    isSelectorOpen = true,
                    searchQuery = "bulunmayan_isyeri",
                    searchResults = emptyList(),
                    selectedMerchantId = null,
                    isInProgress = false,
                    error = null,
                    onSearchQueryChanged = {},
                    onSelectMerchant = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Sonu\u00e7 bulunamad\u0131").assertExists()
    }

    // 15. Do\u011Fru de\u011Fil onay/iptal ak\u0131\u015F\u0131 do\u011Fru \u00E7al\u0131\u015F\u0131r
    @Test
    fun scenario15_merchantBlockConfirmDialog_confirmAndDismiss() {
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantBlockConfirmDialog(
                    isInProgress = false,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("\u0130\u015f yeri e\u015fle\u015fmesini kald\u0131r").assertExists()
        composeRule.onNodeWithText(
            "Bu i\u015flem i\u00e7in tan\u0131mlanan i\u015f yeri e\u015fle\u015fmesi kald\u0131r\u0131lacak ve gelecekte benzer i\u015flemler i\u00e7in bu e\u015fle\u015fme \u00F6nerilmeyecek. Onayl\u0131yor musunuz?",
        ).assertExists()

        composeRule.onNodeWithText("\u0130ptal").assertExists().performClick()
        assertTrue(dismissed)

        composeRule.onNodeWithText("Onayla").assertExists().performClick()
        assertTrue(confirmed)
    }

    // 16. Dekoratif MerchantLogo \u00E7ift anons \u00FCretmez (contentDescription = null)
    @Test
    fun scenario16_decorativeMerchantLogo_producesNoDuplicateAnnouncement() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("starbucks"),
            displayName = "Starbucks",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = sampleTransaction(merchant = knownMerchant, canCorrectMerchant = true)

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

        // Merchant kart\u0131nda Starbucks tek bir kez metin olarak bulunur, logo dekoratiftir
        composeRule.onAllNodesWithText("Starbucks").assertCountEquals(1)
    }

    // 17. TR\u2192EN runtime ge\u00E7i\u015Fi ayn\u0131 kompozisyon \u00FCzerinde do\u011Frulan\u0131r
    @Test
    fun scenario17_runtimeLocaleSwitch_trToEn_onSameComposition() {
        var languageTag by mutableStateOf("tr")
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("migros"),
            displayName = "Migros",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = sampleTransaction(merchant = knownMerchant, canCorrectMerchant = true)

        composeRule.setContent {
            App(languageTag = languageTag) {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                )
                MerchantBlockConfirmDialog(
                    isInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // TR kontrol\u00FC
        composeRule.onNodeWithText("Merchant olarak tan\u0131nd\u0131").assertExists()
        composeRule.onNodeWithText("De\u011fi\u015ftir").assertExists()
        composeRule.onNodeWithText("Do\u011fru de\u011fil").assertExists()
        composeRule.onNodeWithText("\u0130\u015f yeri e\u015fle\u015fmesini kald\u0131r").assertExists()
        composeRule.onNodeWithText("Onayla").assertExists()

        // Runtime dil de\u011Fi\u015Fimi: TR -> EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN kontrol\u00FC
        composeRule.onNodeWithText("Recognized as merchant").assertExists()
        composeRule.onNodeWithText("Change").assertExists()
        composeRule.onNodeWithText("Not correct").assertExists()
        composeRule.onNodeWithText("Remove merchant match").assertExists()
        composeRule.onNodeWithText("Confirm").assertExists()
    }

    // 18. Loading s\u0131ras\u0131nda eylemler g\u00FCvenli bi\u00E7imde devre d\u0131\u015F\u0131 kal\u0131r
    @Test
    fun scenario18_inProgressState_disablesActionsSafely() {
        val knownMerchant = TransactionMerchantDisplayModel(
            merchantId = EntityId("migros"),
            displayName = "Migros",
            verifiedLogoKey = null,
            canShowMerchantLogo = true,
        )
        val item = sampleTransaction(merchant = knownMerchant, canCorrectMerchant = true)

        composeRule.setContent {
            App(languageTag = "tr") {
                TransactionDetailScreen(
                    item = item,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    isCorrectionInProgress = true,
                )
                MerchantBlockConfirmDialog(
                    isInProgress = true,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // isInProgress = true iken dialog butonlar\u0131 disabled olmal\u0131d\u0131r
        composeRule.onNodeWithText("\u0130ptal").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithText("Onayla").assertExists().assertIsNotEnabled()
    }

    // 19. Block dialog bu hatanın TR karşılığını görünür gösterir
    @Test
    fun scenario19_merchantBlockConfirmDialog_displaysErrorInTurkish() {
        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantBlockConfirmDialog(
                    isInProgress = false,
                    error = MerchantCorrectionUiError.WORKSPACE_RESTRICTED,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Çalışma alanı işlemlerinde iş yeri düzeltilemez.",
        ).assertExists().assertIsDisplayed()
    }

    // 20. Aynı kompozisyonda TR->EN geçişinde hata metni İngilizceye döner
    @Test
    fun scenario20_merchantBlockConfirmDialog_runtimeLocaleSwitch_trToEn_translatesError() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                MerchantBlockConfirmDialog(
                    isInProgress = false,
                    error = MerchantCorrectionUiError.WORKSPACE_RESTRICTED,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Çalışma alanı işlemlerinde iş yeri düzeltilemez.",
        ).assertExists()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText(
            "Merchant cannot be corrected in workspace transactions.",
        ).assertExists()
    }

    // 21. Dialog kapatılıp yeniden açılınca eski hata görünmez
    @Test
    fun scenario21_merchantBlockConfirmDialog_dismissAndReopen_clearsError() {
        var isOpen by mutableStateOf(true)
        var currentError by mutableStateOf<MerchantCorrectionUiError?>(MerchantCorrectionUiError.GENERIC)

        composeRule.setContent {
            App(languageTag = "tr") {
                if (isOpen) {
                    MerchantBlockConfirmDialog(
                        isInProgress = false,
                        error = currentError,
                        onConfirm = {},
                        onDismiss = {
                            isOpen = false
                            currentError = null
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText(
            "İş yeri düzeltilemedi. Lütfen tekrar deneyin.",
        ).assertExists()

        composeRule.onNodeWithText("İptal").performClick()
        composeRule.waitForIdle()

        // Yeniden aç
        composeRule.runOnIdle {
            isOpen = true
        }

        composeRule.onNodeWithText(
            "İş yeri düzeltilemedi. Lütfen tekrar deneyin.",
        ).assertDoesNotExist()
        assertNull(currentError)
    }

    // 22. Failure sonrasında confirm tekrar kullanılabilir ve ikinci deneme başarılı olabilir
    @Test
    fun scenario22_merchantBlockConfirmDialog_afterFailure_confirmCanBeRetriedSuccessfully() {
        var inProgress by mutableStateOf(false)
        var currentError by mutableStateOf<MerchantCorrectionUiError?>(MerchantCorrectionUiError.GENERIC)
        var confirmAttempts = 0

        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantBlockConfirmDialog(
                    isInProgress = inProgress,
                    error = currentError,
                    onConfirm = {
                        confirmAttempts++
                        inProgress = true
                        // İkinci denemede hata çözülüyor simülasyonu
                        currentError = null
                        inProgress = false
                    },
                    onDismiss = {},
                )
            }
        }

        // İlk durumda hata görünür, butonlar etkin
        composeRule.onNodeWithText(
            "İş yeri düzeltilemedi. Lütfen tekrar deneyin.",
        ).assertExists()
        composeRule.onNodeWithText("Onayla").assertExists().performClick()

        // Retry tıklandıktan sonra confirm çağrıldı ve hata kalktı
        assertEquals(1, confirmAttempts)
        composeRule.onNodeWithText(
            "İş yeri düzeltilemedi. Lütfen tekrar deneyin.",
        ).assertDoesNotExist()
    }

    // 23. Assign selector hata gösterimi regresyona uğramaz
    @Test
    fun scenario23_merchantSelectorBottomSheet_errorDisplay_doesNotRegress() {
        composeRule.setContent {
            App(languageTag = "tr") {
                MerchantSelectorBottomSheet(
                    isSelectorOpen = true,
                    searchQuery = "",
                    searchResults = emptyList(),
                    selectedMerchantId = null,
                    isInProgress = false,
                    error = MerchantCorrectionUiError.WORKSPACE_RESTRICTED,
                    onSearchQueryChanged = {},
                    onSelectMerchant = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Çalışma alanı işlemlerinde iş yeri düzeltilemez.",
        ).assertExists().assertIsDisplayed()
    }
}
