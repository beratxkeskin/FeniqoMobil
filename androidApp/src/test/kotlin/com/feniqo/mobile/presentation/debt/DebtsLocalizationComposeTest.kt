@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:argument-list-wrapping",
)

package com.feniqo.mobile.presentation.debt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateDebtUseCase
import com.feniqo.mobile.domain.usecase.DeleteDebtUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtUseCase
import com.feniqo.mobile.domain.usecase.UpdateDebtUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.screen.DebtFormScreen
import com.feniqo.mobile.presentation.screen.DebtPaymentFormScreen
import com.feniqo.mobile.presentation.screen.DebtSnowballPlanScreen
import com.feniqo.mobile.presentation.screen.DebtsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class DebtsLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeDebtRepository : DebtRepository {
        val debtsFlow = MutableStateFlow<Map<EntityId, Debt>>(emptyMap())
        val paymentsFlow = MutableStateFlow<Map<EntityId, List<DebtPayment>>>(emptyMap())

        override fun observeDebts(): Flow<List<Debt>> = flowOf(debtsFlow.value.values.toList())

        override fun observeDebt(id: EntityId): Flow<Debt?> = flow {
            debtsFlow.collect { map -> emit(map[id]) }
        }

        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flow {
            paymentsFlow.collect { map -> emit(map[debtId] ?: emptyList()) }
        }

        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> {
            return RepositoryResult.Success(EntityId("debt-created"))
        }

        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }

        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> {
            return RepositoryResult.Success(EntityId("payment-created"))
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }
    }

    private fun sampleDebtDisplayModel(
        id: String,
        title: String,
        type: DebtType,
        amountMinor: Long,
        dueStatus: DebtDueStatus = DebtDueStatus.OnTime(10L),
    ): DebtDisplayModel = DebtDisplayModel(
        id = EntityId(id),
        title = title,
        avatarInitial = title.take(1),
        type = type,
        status = if (dueStatus is DebtDueStatus.Settled) DebtStatus.SETTLED else DebtStatus.OPEN,
        isOpen = dueStatus !is DebtDueStatus.Settled,
        isSettled = dueStatus is DebtDueStatus.Settled,
        principalAmount = Money(amountMinor, Currency.TRY),
        totalPaid = Money(0L, Currency.TRY),
        remainingAmount = Money(amountMinor, Currency.TRY),
        currency = Currency.TRY,
        dueDate = LocalDate(2026, 12, 31),
        description = null,
        dueStatus = dueStatus,
    )

    @Test
    fun debts_screen_and_cards_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val debtItem = sampleDebtDisplayModel("d-1", "Kredi Kartı", DebtType.DEBT, 5000_00L)
        val receivableItem = sampleDebtDisplayModel("r-1", "Ahmet Borç", DebtType.RECEIVABLE, 2000_00L)
        val settledItem = sampleDebtDisplayModel(
            "s-1",
            "Kira Borcu",
            DebtType.DEBT,
            1000_00L,
            dueStatus = DebtDueStatus.Settled,
        )

        val summary = DebtsSummaryUiModel(
            totalDebt = Money(5000_00L, Currency.TRY),
            activeDebtCount = 1,
            totalReceivable = Money(2000_00L, Currency.TRY),
            activeReceivableCount = 1,
            netBalanceMinor = -3000_00L,
            isNetPositive = false,
            isNetNegative = true,
            isNetZero = false,
            netStatus = DebtsNetStatus.DEBT_EXCEEDS,
            netBalanceDelta = MoneyDelta(-3000_00L, Currency.TRY),
            baseCurrency = Currency.TRY,
        )

        val insight = DebtInsightUiModel(
            payload = DebtInsightPayload.OverdueDebts(count = 1),
            type = DebtInsightType.WARNING,
        )

        val state = DebtsUiState(
            isLoading = false,
            debts = listOf(debtItem, receivableItem, settledItem),
            upcomingItems = listOf(debtItem),
            activeDebts = listOf(debtItem),
            activeReceivables = listOf(receivableItem),
            settledItems = listOf(settledItem),
            summary = summary,
            insight = insight,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtsScreen(
                    state = state,
                    onRetry = {},
                    onAddDebt = {},
                    onDebtClick = {},
                    onNavigateBack = {},
                    onNavigateToSnowballPlan = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Borç ve Alacaklar").assertIsDisplayed()
        composeRule.onNodeWithText("Net durum").assertIsDisplayed()
        composeRule.onNodeWithText("Gecikmiş Borç Uyarısı").assertIsDisplayed()
        composeRule.onNodeWithText("Yaklaşan Vadeler").assertIsDisplayed()
        composeRule.onAllNodesWithText("Borçlarım").assertCountEquals(2)
        composeRule.onAllNodesWithText("Alacaklarım").assertCountEquals(2)
        composeRule.onNodeWithText("Tamamlananlar").assertIsDisplayed()
        composeRule.onNodeWithText("Borç kapatma planı").assertExists()
        composeRule.onNodeWithText("+ Yeni kayıt").assertExists()
        composeRule.onAllNodesWithText("5.000,00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("2.000,00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("-3.000,00 ₺")[0].assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Debts & Receivables").assertIsDisplayed()
        composeRule.onNodeWithText("Net status").assertIsDisplayed()
        composeRule.onNodeWithText("Overdue Debt Warning").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming Due Dates").assertIsDisplayed()
        composeRule.onAllNodesWithText("My Debts").assertCountEquals(2)
        composeRule.onAllNodesWithText("My Receivables").assertCountEquals(2)
        composeRule.onAllNodesWithText("Completed").assertCountEquals(2)
        composeRule.onNodeWithText("Debt payoff plan").assertExists()
        composeRule.onNodeWithText("+ New record").assertExists()
        composeRule.onAllNodesWithText("5,000.00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("2,000.00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("-3,000.00 ₺")[0].assertExists()
    }

    @Test
    fun debt_form_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val input = DebtFormInput(
            debtId = null,
            titleInput = "",
            amountInput = "",
            currency = Currency.TRY,
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            descriptionInput = "",
        )

        val errors = DebtFormInputErrors(
            titleError = DebtFormFieldError.TITLE_REQUIRED,
            amountError = DebtFormFieldError.AMOUNT_REQUIRED,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtFormScreen(
                    input = input,
                    errors = errors,
                    isSubmitting = false,
                    isEditMode = false,
                    onBack = {},
                    onTitleChange = {},
                    onAmountChange = {},
                    onCurrencyChange = {},
                    onTypeChange = {},
                    onDueDateClick = {},
                    onDescriptionChange = {},
                    onRequestDelete = {},
                    onSubmit = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Yeni kayıt").assertIsDisplayed()
        composeRule.onNodeWithText("Kayıt bilgileri").assertIsDisplayed()
        composeRule.onNodeWithText("Borç").assertIsDisplayed()
        composeRule.onNodeWithText("Alacak").assertIsDisplayed()
        composeRule.onNodeWithText("Başlık zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Tutar zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam tutar *").performClick()
        composeRule.onNodeWithText("0,00").assertIsDisplayed()
        composeRule.onNodeWithText("Vade tarihi *").assertIsDisplayed()
        composeRule.onNodeWithText("31 Aralık 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Kaydı oluştur").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("New record").assertIsDisplayed()
        composeRule.onNodeWithText("Record info").assertIsDisplayed()
        composeRule.onNodeWithText("Debt").assertIsDisplayed()
        composeRule.onNodeWithText("Receivable").assertIsDisplayed()
        composeRule.onNodeWithText("Title is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Total amount *").performClick()
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithText("Due date *").assertIsDisplayed()
        composeRule.onNodeWithText("December 31, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Create record").assertIsDisplayed()
    }

    @Test
    fun debt_payment_form_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val parentDebt = Debt(
            id = EntityId("d-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Banka Kredisi",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val input = DebtPaymentFormInput(
            amountInput = "",
            paidOn = LocalDate(2026, 10, 15),
        )

        val errors = DebtPaymentFormInputErrors(
            amountError = DebtPaymentFormFieldError.AMOUNT_REQUIRED,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtPaymentFormScreen(
                    parentDebt = parentDebt,
                    remainingAmount = Money(5000_00L, Currency.TRY),
                    isSettled = false,
                    input = input,
                    errors = errors,
                    isSubmitting = false,
                    onBack = {},
                    onAmountChange = {},
                    onPaidOnClick = {},
                    onSubmit = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Ödeme ekle").assertIsDisplayed()
        composeRule.onNodeWithText("Kalan Borç").assertIsDisplayed()
        composeRule.onNodeWithText("5.000,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Ödeme tutarı zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("0,00").assertIsDisplayed()
        composeRule.onNodeWithText("Ödeme Tarihi *").assertIsDisplayed()
        composeRule.onNodeWithText("15 Ekim 2026").assertIsDisplayed()
        composeRule.onNodeWithText("+ Ödemeyi kaydet").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Add payment").assertIsDisplayed()
        composeRule.onNodeWithText("Remaining Debt").assertIsDisplayed()
        composeRule.onNodeWithText("5,000.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Payment amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithText("Payment Date *").assertIsDisplayed()
        composeRule.onNodeWithText("October 15, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("+ Save payment").assertIsDisplayed()
    }

    @Test
    fun debt_snowball_plan_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val populatedPlan = DebtSnowballPlanDisplayUiModel(
            currency = Currency.TRY,
            monthlyPaymentBudget = Money(1000_00L, Currency.TRY),
            totalDebtAmount = Money(5000_00L, Currency.TRY),
            totalMonths = 5,
            debtItems = listOf(
                DebtSnowballItemUiModel(
                    debtId = EntityId("d-1"),
                    debtTitle = "Küçük Borç",
                    initialRemainingAmount = Money(2000_00L, Currency.TRY),
                    totalAllocatedAmount = Money(2000_00L, Currency.TRY),
                    settledInMonth = 2,
                    orderIndex = 1,
                ),
            ),
            monthlyAllocations = listOf(
                DebtSnowballMonthlyAllocationUiModel(
                    month = 1,
                    debtId = EntityId("d-1"),
                    debtTitle = "Küçük Borç",
                    isMissingDebt = false,
                    allocatedAmount = Money(1000_00L, Currency.TRY),
                    remainingBalanceAfterPayment = Money(1000_00L, Currency.TRY),
                ),
            ),
        )

        val state = DebtSnowballPlanUiState(
            availableCurrencies = listOf(Currency.TRY, Currency.USD),
            selectedCurrency = Currency.TRY,
            budgetInput = "",
            budgetError = DebtSnowballFormFieldError.BUDGET_REQUIRED,
            isLoadingDebts = false,
            eligibleDebtsCount = 3,
            plan = populatedPlan,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtSnowballPlanScreen(
                    state = state,
                    onBack = {},
                    onCurrencySelect = {},
                    onBudgetChange = {},
                    onCalculate = {},
                    onRetry = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Borç kapatma planı").assertIsDisplayed()
        composeRule.onNodeWithText("Simülasyon para birimi").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık ayırabileceğin bütçe *").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık bütçe zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("3 uygun borç bulundu.").assertExists()
        composeRule.onNodeWithText("+ Planı hesapla").assertExists()
        composeRule.onNodeWithText("Aylık ayırabileceğin bütçe *").performClick()
        composeRule.onNodeWithText("0,00").assertIsDisplayed()
        composeRule.onAllNodesWithText("5.000,00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("1.000,00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("2.000,00 ₺")[0].assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Debt payoff plan").assertIsDisplayed()
        composeRule.onNodeWithText("Simulation currency").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly budget you can allocate *").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly budget is required.").assertIsDisplayed()
        composeRule.onNodeWithText("3 eligible debts found.").assertExists()
        composeRule.onNodeWithText("+ Calculate plan").assertExists()
        composeRule.onNodeWithText("Monthly budget you can allocate *").performClick()
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onAllNodesWithText("5,000.00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("1,000.00 ₺")[0].assertExists()
        composeRule.onAllNodesWithText("2,000.00 ₺")[0].assertExists()
    }

    @Test
    fun debt_form_edit_route_preserves_unsaved_draft_across_language_change() {
        var languageTag by mutableStateOf("tr")

        val repo = FakeDebtRepository()
        val existingDebtId = EntityId("debt-edit-1")
        val existingDebt = Debt(
            id = existingDebtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kayıtlı Borç",
            amount = Money(2500_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2027, 3, 10),
            status = DebtStatus.OPEN,
            description = "İlk açıklama",
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repo.debtsFlow.value = mapOf(existingDebtId to existingDebt)

        val viewModel = DebtFormViewModel(
            observeDebtUseCase = ObserveDebtUseCase(repo),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(repo),
            createDebtUseCase = CreateDebtUseCase(repo),
            updateDebtUseCase = UpdateDebtUseCase(repo),
            deleteDebtUseCase = DeleteDebtUseCase(repo),
            currentDateProvider = FakeCurrentDateProvider(LocalDate(2026, 9, 3)),
        )

        composeRule.setContent {
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            val locale = java.util.Locale.forLanguageTag(languageTag)
            java.util.Locale.setDefault(locale)
            configuration.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
            ) {
                DebtFormScreenRoute(
                    onNavigateBack = {},
                    onMessage = {},
                    initialDebtId = existingDebtId,
                    viewModel = viewModel,
                )
            }
        }

        // İlk yüklemede kayıtlı başlık görünür
        composeRule.onAllNodesWithText("Kayıtlı Borç")[0].assertExists()

        // Kullanıcı taslağı UI üzerinden değiştirir
        composeRule.onNodeWithContentDescription("Borç kayıt adı").performTextReplacement("Kaydedilmemiş Borç Başlığı")
        composeRule.onAllNodesWithText("Kaydedilmemiş Borç Başlığı")[0].assertExists()

        // Dil değişimi (TR -> EN): decimalSeparator değişse bile edit formu Room'dan yeniden yüklenmemeli
        composeRule.runOnIdle { languageTag = "en" }

        // EN modunda da kaydedilmemiş taslak adı korunmalıdır
        composeRule.onAllNodesWithText("Kaydedilmemiş Borç Başlığı")[0].assertExists()
        composeRule.onNodeWithContentDescription("Debt record name").assertExists()
        assertEquals("Kaydedilmemiş Borç Başlığı", viewModel.uiState.value.input.titleInput)
    }

    @Test
    fun debt_payment_form_screen_exceeds_remaining_error_formats_money_per_locale() {
        var languageTag by mutableStateOf("tr")

        val parentDebt = Debt(
            id = EntityId("d-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Telefon Taksiti",
            amount = Money(5000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val input = DebtPaymentFormInput(amountInput = "6000")
        val errors = DebtPaymentFormInputErrors(
            amountError = DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtPaymentFormScreen(
                    parentDebt = parentDebt,
                    remainingAmount = Money(2500_00L, Currency.TRY),
                    isSettled = false,
                    input = input,
                    errors = errors,
                    isSubmitting = false,
                    onBack = {},
                    onAmountChange = {},
                    onPaidOnClick = {},
                    onSubmit = {},
                )
            }
        }

        // TR assertions: 2.500,00 ₺
        composeRule.onNodeWithText("Ödeme tutarı kalan borcu aşamaz. Maksimum ödeme tutarı: 2.500,00 ₺").assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions: 2,500.00 ₺
        composeRule.onNodeWithText("Payment amount cannot exceed remaining debt. Maximum payment amount: 2,500.00 ₺").assertExists()
    }

    @Test
    fun debt_form_screen_content_descriptions_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                DebtFormScreen(
                    input = DebtFormInput(
                        titleInput = "Test",
                        amountInput = "100",
                        descriptionInput = "Not",
                        dueDate = LocalDate(2026, 12, 31),
                    ),
                    errors = DebtFormInputErrors(),
                    isSubmitting = false,
                    isEditMode = true,
                    onBack = {},
                    onTitleChange = {},
                    onAmountChange = {},
                    onCurrencyChange = {},
                    onTypeChange = {},
                    onDueDateClick = {},
                    onDescriptionChange = {},
                    onRequestDelete = {},
                    onSubmit = {},
                )
            }
        }

        // TR content descriptions
        composeRule.onNodeWithContentDescription("Geri").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Kaydı sil").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Borç kayıt adı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Borç toplam tutarı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Borç açıklaması").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vade tarihi seç").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN content descriptions
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete record").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Debt record name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Debt total amount").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Debt description").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Select due date").assertIsDisplayed()
    }
}
