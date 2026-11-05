package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class TransactionFormMerchantComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sampleSuggestedCandidates(): List<MerchantSelectorItem> =
        listOf(
            MerchantSelectorItem(EntityId("bim"), "BİM", "groceries", null),
            MerchantSelectorItem(EntityId("a101"), "A101", "groceries", null),
            MerchantSelectorItem(EntityId("sok"), "ŞOK", "groceries", null),
            MerchantSelectorItem(EntityId("migros"), "Migros", "groceries", null),
            MerchantSelectorItem(EntityId("carrefoursa"), "CarrefourSA", "groceries", null),
            MerchantSelectorItem(EntityId("file_market"), "File Market", "groceries", null),
            MerchantSelectorItem(EntityId("tarim_kredi"), "Tarım Kredi", "groceries", null),
            MerchantSelectorItem(EntityId("getir"), "Getir", "groceries", null),
        )

    private fun sampleSearchableCandidates(): List<MerchantSelectorItem> =
        sampleSuggestedCandidates() + listOf(
            MerchantSelectorItem(EntityId("migros_sanal_market"), "Migros Sanal Market", "groceries", null),
            MerchantSelectorItem(EntityId("macrocenter"), "Macrocenter", "groceries", null),
            MerchantSelectorItem(EntityId("yemeksepeti_market"), "Yemeksepeti Market", "groceries", null),
        )

    private fun sampleCategories(): List<TransactionCategoryOptionUiModel> =
        listOf(
            TransactionCategoryOptionUiModel(
                id = EntityId("cat-market"),
                name = "Market",
                type = TransactionType.EXPENSE,
                colorHex = "#10B981",
                iconKey = "groceries",
                isSelectable = true,
                isHistorical = false,
            ),
        )

    private fun eligibleFormState(
        decision: TransactionMerchantCreateDecision = TransactionMerchantCreateDecision.Auto,
        suggestedCandidates: List<MerchantSelectorItem> = sampleSuggestedCandidates(),
        searchableCandidates: List<MerchantSelectorItem> = sampleSearchableCandidates(),
    ): TransactionFormUiState =
        TransactionFormUiState(
            amountText = "150,00",
            type = TransactionType.EXPENSE,
            selectedCategoryId = EntityId("cat-market"),
            availableCategories = sampleCategories(),
            title = "Market Alışverişi",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 10, 22),
            isMerchantSelectionEligible = true,
            merchantSuggestedCandidates = suggestedCandidates,
            merchantSearchableCandidates = searchableCandidates,
            merchantDecision = decision,
        )

    @Test
    fun eligible_create_state_renders_section_suggestions_search_and_not_listed() {
        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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

        compose.onNodeWithTag("transaction_merchant_section").assertIsDisplayed()
        compose.onNodeWithText("İş yeri / marka").assertIsDisplayed()
        compose.onNodeWithText("İsteğe bağlı").assertIsDisplayed()
        compose.onNodeWithText("Önerilen iş yerleri").assertIsDisplayed()

        // 8 önerilen aday render edilir
        compose.onNodeWithTag("merchant_chip_bim").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_a101").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_sok").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_migros").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_carrefoursa").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_file_market").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_tarim_kredi").assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_getir").assertIsDisplayed()

        // Searchable-only 3 kayıt inline chip olarak bulunmaz
        compose.onNodeWithTag("merchant_chip_migros_sanal_market").assertDoesNotExist()
        compose.onNodeWithTag("merchant_chip_macrocenter").assertDoesNotExist()
        compose.onNodeWithTag("merchant_chip_yemeksepeti_market").assertDoesNotExist()

        // "Tümünü ara" butonu mevcuttur
        compose.onNodeWithTag("merchant_search_all_button").assertIsDisplayed()

        // "Listede yok" ve "İşlem adını kullan" metinleri butonda birlikte görünür
        compose.onNodeWithTag("merchant_use_transaction_name_button").assertIsDisplayed()
        compose.onNodeWithText("Listede yok").assertIsDisplayed()
        compose.onNodeWithText("İşlem adını kullan").assertIsDisplayed()
    }

    @Test
    fun ineligible_state_hides_merchant_section_completely() {
        val state = eligibleFormState().copy(isMerchantSelectionEligible = false)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertDoesNotExist()
        compose.onNodeWithText("İş yeri / marka").assertDoesNotExist()
    }

    @Test
    fun edit_state_hides_merchant_section() {
        val state = eligibleFormState().copy(isEditMode = true, isMerchantSelectionEligible = false)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertDoesNotExist()
    }

    @Test
    fun income_state_hides_merchant_section() {
        val state = eligibleFormState().copy(type = TransactionType.INCOME, isMerchantSelectionEligible = false)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertDoesNotExist()
    }

    @Test
    fun workspace_state_hides_merchant_section() {
        val state = eligibleFormState().copy(activeWorkspaceId = EntityId("workspace-1"), isMerchantSelectionEligible = false)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertDoesNotExist()
    }

    @Test
    fun installment_state_hides_merchant_section() {
        val state = eligibleFormState().copy(isInstallmentEnabled = true, isMerchantSelectionEligible = false)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertDoesNotExist()
    }

    @Test
    fun initial_render_invokes_zero_callbacks() {
        var knownSelectedCount = 0
        var useNameCount = 0
        var clearCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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
                    onKnownMerchantSelected = { knownSelectedCount++ },
                    onUseTransactionNameSelected = { useNameCount++ },
                    onClearMerchantDecision = { clearCount++ },
                )
            }
        }

        assertEquals(0, knownSelectedCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
    }

    @Test
    fun known_merchant_chip_click_emits_stable_id_exactly_once() {
        var selectedId: EntityId? = null
        var callCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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
                    onKnownMerchantSelected = {
                        selectedId = it
                        callCount++
                    },
                )
            }
        }

        compose.onNodeWithTag("merchant_chip_migros").performClick()

        assertEquals(1, callCount)
        assertEquals(EntityId("migros"), selectedId)
    }

    @Test
    fun use_transaction_name_click_invokes_callback_exactly_once() {
        var callCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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
                    onUseTransactionNameSelected = { callCount++ },
                )
            }
        }

        compose.onNodeWithTag("merchant_use_transaction_name_button").performClick()

        assertEquals(1, callCount)
    }

    @Test
    fun known_merchant_selected_preview_renders_merchant_name_and_logo() {
        val state = eligibleFormState(decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros")))

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("selected_merchant_card").assertIsDisplayed()
        compose.onNodeWithText("Seçilen iş yeri").assertIsDisplayed()
        compose.onNodeWithText("Migros").assertIsDisplayed()
        compose.onNodeWithTag("merchant_selected_clear_button").assertIsDisplayed()
        // Öneriler gizlenir
        compose.onNodeWithTag("merchant_suggestions_flow_row").assertDoesNotExist()
    }

    @Test
    fun use_transaction_name_preview_renders_effective_title_and_category_icon() {
        val state = eligibleFormState(decision = TransactionMerchantCreateDecision.UseTransactionName)

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("use_transaction_name_card").assertIsDisplayed()
        compose.onNodeWithText("İşlem adını kullan").assertIsDisplayed()
        compose.onAllNodesWithText("Market Alışverişi").assertCountEquals(2)
        compose.onNodeWithTag("merchant_use_name_clear_button").assertIsDisplayed()
    }

    @Test
    fun clear_action_invokes_only_clear_callback() {
        val state = eligibleFormState(decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros")))
        var clearCalled = 0
        var knownSelected = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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
                    onKnownMerchantSelected = { knownSelected++ },
                    onClearMerchantDecision = { clearCalled++ },
                )
            }
        }

        compose.onNodeWithTag("merchant_selected_clear_button").performClick()

        assertEquals(1, clearCalled)
        assertEquals(0, knownSelected)
    }

    @Test
    fun explicit_decisions_render_exactly_one_clear_action() {
        var state by mutableStateOf(
            eligibleFormState(decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros"))),
        )
        var clearCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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
                    onClearMerchantDecision = { clearCount++ },
                )
            }
        }

        // KnownMerchant: Ekranda tam 1 adet "Otomatik tanımaya dön" semantik aksiyonu bulunur
        compose.onAllNodesWithContentDescription("Otomatik tanımaya dön").assertCountEquals(1)
        compose.onNodeWithTag("merchant_selected_clear_button").performClick()
        assertEquals(1, clearCount)

        // UseTransactionName durumuna geçildiğinde de tam 1 adet "Otomatik tanımaya dön" bulunur
        compose.runOnIdle {
            state = eligibleFormState(decision = TransactionMerchantCreateDecision.UseTransactionName)
        }

        compose.onAllNodesWithContentDescription("Otomatik tanımaya dön").assertCountEquals(1)
        compose.onNodeWithTag("merchant_use_name_clear_button").performClick()
        assertEquals(2, clearCount)
    }

    @Test
    fun search_all_bottom_sheet_opens_and_lists_only_category_candidates() {
        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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

        compose.onNodeWithTag("merchant_search_all_button").performClick()

        compose.onNodeWithTag("merchant_search_bottom_sheet").assertIsDisplayed()
        // Bottom sheet içinde searchable adaylar doğrulanır
        compose.onNodeWithTag("merchant_search_item_a101").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_bim").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_carrefoursa").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_file_market").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_getir").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_macrocenter").assertIsDisplayed()

        // Searchable-only adaylar da yerel listede bulunur ve filtrelemeyle doğrulanır
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("sanal")
        compose.onNodeWithTag("merchant_search_item_migros_sanal_market").assertIsDisplayed()
    }

    @Test
    fun search_query_filters_deterministically_and_does_not_show_irrelevant_merchants() {
        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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

        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("mig")

        compose.onNodeWithTag("merchant_search_item_migros").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_bim").assertDoesNotExist()
        compose.onNodeWithTag("merchant_search_item_a101").assertDoesNotExist()
    }

    @Test
    fun empty_search_results_shows_localized_no_results() {
        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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

        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("netflix")

        compose.onNodeWithTag("merchant_search_empty_text").assertIsDisplayed()
        compose.onNodeWithText("Sonuç bulunamadı").assertIsDisplayed()
    }

    @Test
    fun search_transient_interactions_produce_zero_selection_and_submit_callbacks() {
        var knownCount = 0
        var useNameCount = 0
        var clearCount = 0
        var submitCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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
                    onSubmit = { submitCount++ },
                    onBack = {},
                    onDismissMessage = {},
                    onAttachReceipt = {},
                    onRemoveReceipt = {},
                    onKnownMerchantSelected = { knownCount++ },
                    onUseTransactionNameSelected = { useNameCount++ },
                    onClearMerchantDecision = { clearCount++ },
                )
            }
        }

        // 1. "Tümünü ara" ile sheet'i aç
        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertIsDisplayed()
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)

        // 2. Arama sorgusu yaz
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("mig")
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)

        // 3. Query temizleme ikonuna bas
        compose.onNodeWithTag("clear_merchant_search_query_button").performClick()
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)

        // 4. Close düğmesiyle sheet'i kapat
        compose.onNodeWithTag("close_merchant_search_button").performClick()
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertDoesNotExist()
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)

        // 5. Sheet'i yeniden aç ve text field/query'nin temiz olduğunu doğrula
        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertIsDisplayed()
        // Tüm adaylar temiz sorguyla yeniden görünür
        compose.onNodeWithTag("merchant_search_item_bim").assertIsDisplayed()
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)
    }

    @Test
    fun bottom_sheet_closes_and_query_resets_when_form_is_submitting_or_disabled() {
        var isSubmitting by mutableStateOf(false)
        var knownCount = 0
        var useNameCount = 0
        var clearCount = 0
        var submitCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState().copy(isSubmitting = isSubmitting),
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
                    onSubmit = { submitCount++ },
                    onBack = {},
                    onDismissMessage = {},
                    onAttachReceipt = {},
                    onRemoveReceipt = {},
                    onKnownMerchantSelected = { knownCount++ },
                    onUseTransactionNameSelected = { useNameCount++ },
                    onClearMerchantDecision = { clearCount++ },
                )
            }
        }

        // Sheet'i aç ve query yaz
        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("mig")

        // Filtre devrede: migros görünür, bim görünmez
        compose.onNodeWithTag("merchant_search_item_migros").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_bim").assertDoesNotExist()

        // Form submit durumuna geçiyor (enabled = false)
        compose.runOnIdle { isSubmitting = true }

        // Sheet güvenle kapanır
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertDoesNotExist()

        // Sayaçlar sıfır kalır
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)

        // Form yeniden etkinleşiyor (enabled = true)
        compose.runOnIdle { isSubmitting = false }

        // Sheet yeniden açılır
        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertIsDisplayed()

        // Önceki sorgunun korunmadığı; filtrelenmiş olan bim ve a101 adaylarının yeniden görünmesi üzerinden kanıtlanır
        compose.onNodeWithTag("merchant_search_item_bim").assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_item_a101").assertIsDisplayed()

        // Yeniden açma ve sorgu sıfırlanması hiçbir seçim, clear veya submit callback'i üretmez
        assertEquals(0, knownCount)
        assertEquals(0, useNameCount)
        assertEquals(0, clearCount)
        assertEquals(0, submitCount)
    }

    @Test
    fun search_item_selection_emits_stable_id_and_closes_sheet() {
        var selectedId: EntityId? = null
        var callCount = 0

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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
                    onKnownMerchantSelected = {
                        selectedId = it
                        callCount++
                    },
                )
            }
        }

        compose.onNodeWithTag("merchant_search_all_button").performClick()
        compose.onNodeWithTag("merchant_search_text_field").performTextInput("sanal")
        compose.onNodeWithTag("merchant_search_item_migros_sanal_market").assertIsDisplayed().performClick()

        assertEquals(1, callCount)
        assertEquals(EntityId("migros_sanal_market"), selectedId)
        compose.onNodeWithTag("merchant_search_bottom_sheet").assertDoesNotExist()
    }

    @Test
    fun searchable_only_merchant_selected_preview_renders_name_and_logo() {
        val state =
            eligibleFormState(
                decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros_sanal_market")),
            )

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("selected_merchant_card").assertIsDisplayed()
        compose.onNodeWithText("Seçilen iş yeri").assertIsDisplayed()
        compose.onNodeWithText("Migros Sanal Market").assertIsDisplayed()
        compose.onNodeWithTag("merchant_selected_clear_button").assertIsDisplayed()
        compose.onNodeWithTag("merchant_suggestions_flow_row").assertDoesNotExist()
    }

    @Test
    fun searchable_list_empty_hides_search_all_button() {
        val state =
            eligibleFormState(
                suggestedCandidates = emptyList(),
                searchableCandidates = emptyList(),
            )

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertIsDisplayed()
        // Searchable boş olduğunda "Tümünü ara" butonu render edilmez
        compose.onNodeWithTag("merchant_search_all_button").assertDoesNotExist()
        // "Listede yok / İşlem adını kullan" butonu görünür
        compose.onNodeWithTag("merchant_use_transaction_name_button").assertIsDisplayed()
    }

    @Test
    fun suggested_list_empty_with_eligible_form_shows_only_not_listed_button() {
        val state =
            eligibleFormState(
                suggestedCandidates = emptyList(),
                searchableCandidates =
                    listOf(
                        MerchantSelectorItem(EntityId("migros_sanal_market"), "Migros Sanal Market", "groceries", null),
                    ),
            )

        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = state,
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

        compose.onNodeWithTag("transaction_merchant_section").assertIsDisplayed()
        // Suggested boş olduğunda öneri akışı ve başlığı görünmez
        compose.onNodeWithTag("merchant_suggestions_flow_row").assertDoesNotExist()
        compose.onNodeWithText("Önerilen iş yerleri").assertDoesNotExist()
        // "Tümünü ara" ve "Listede yok / İşlem adını kullan" görünür
        compose.onNodeWithTag("merchant_search_all_button").assertIsDisplayed()
        compose.onNodeWithTag("merchant_use_transaction_name_button").assertIsDisplayed()
    }

    @Test
    fun merchant_logo_is_decorative_merchant_name_announced_only_once_in_semantics() {
        compose.setContent {
            App(languageTag = "tr") {
                TransactionFormScreen(
                    uiState = eligibleFormState(decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros"))),
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

        // Migros adı semantics ağacında yalnızca 1 kez bulunur (logo dekoratiftir, clearAndSetSemantics içerir)
        compose.onAllNodesWithText("Migros").assertCountEquals(1)
    }

    @Test
    fun runtime_locale_switch_tr_to_en_updates_merchant_section() {
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                TransactionFormScreen(
                    uiState = eligibleFormState(),
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

        // TR metinleri
        compose.onNodeWithText("İş yeri / marka").assertIsDisplayed()
        compose.onNodeWithText("İsteğe bağlı").assertIsDisplayed()
        compose.onNodeWithText("Önerilen iş yerleri").assertIsDisplayed()
        compose.onNodeWithText("Tümünü ara").assertIsDisplayed()
        compose.onNodeWithText("Listede yok").assertIsDisplayed()
        compose.onNodeWithText("İşlem adını kullan").assertIsDisplayed()

        // EN geçişi
        compose.runOnIdle { languageTag = "en" }

        compose.onNodeWithText("Merchant or brand").assertIsDisplayed()
        compose.onNodeWithText("Optional").assertIsDisplayed()
        compose.onNodeWithText("Suggested merchants").assertIsDisplayed()
        compose.onNodeWithText("Search all").assertIsDisplayed()
        compose.onNodeWithText("Not listed").assertIsDisplayed()
        compose.onNodeWithText("Use transaction name").assertIsDisplayed()

        // Tekrar TR geçişi
        compose.runOnIdle { languageTag = "tr" }
        compose.onNodeWithText("İş yeri / marka").assertIsDisplayed()
        compose.onNodeWithText("Listede yok").assertIsDisplayed()
        compose.onNodeWithText("İşlem adını kullan").assertIsDisplayed()
    }

    @Test
    fun small_screen_and_large_font_critical_actions_accessible() {
        compose.setContent {
            App(languageTag = "tr") {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 1.5f),
                ) {
                    Box(modifier = Modifier.requiredSize(width = 320.dp, height = 480.dp)) {
                        TransactionFormScreen(
                            uiState = eligibleFormState(),
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
            }
        }

        // Küçük ekranda scroll edilerek merchant bölümüne, chip'lere ve aksiyonlara ulaşılabilir
        compose.onNodeWithTag("transaction_merchant_section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_bim").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("merchant_chip_getir").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("merchant_search_all_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("merchant_use_transaction_name_button").performScrollTo().assertIsDisplayed()
    }
}
