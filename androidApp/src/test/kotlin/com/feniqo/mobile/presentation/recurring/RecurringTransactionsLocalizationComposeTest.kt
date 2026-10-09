package com.feniqo.mobile.presentation.recurring

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.DeleteRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase
import com.feniqo.mobile.domain.usecase.SetRecurringTransactionActiveUseCase
import com.feniqo.mobile.domain.usecase.UpdateRecurringTransactionUseCase
import com.feniqo.mobile.presentation.common.FakeWorkspaceRepository
import com.feniqo.mobile.presentation.component.RecurringTransactionDeleteDialog
import com.feniqo.mobile.presentation.screen.RecurringTransactionFormScreen
import com.feniqo.mobile.presentation.screen.RecurringTransactionsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class RecurringTransactionsLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun populatedListAndCards_localizedInTrAndEn() {
        var languageTag by mutableStateOf("tr")

        val expenseItem =
            RecurringTransactionDisplayModel(
                id = EntityId("rec-exp-1"),
                categoryId = EntityId("cat-1"),
                categoryName = "Kira",
                categoryColorHex = "#10B981",
                categoryIconKey = "home",
                isCategoryMissing = false,
                amount = Money(50000L, Currency.TRY),
                currency = Currency.TRY,
                type = TransactionType.EXPENSE,
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
                lastGeneratedDate = LocalDate(2026, 8, 1),
                isNeverGenerated = false,
                nextOccurrenceDate = LocalDate(2026, 9, 1),
                isActive = true,
                isPaused = false,
                description = null,
                paymentMethod = PaymentMethod.BANK_TRANSFER,
            )

        val incomeItem =
            RecurringTransactionDisplayModel(
                id = EntityId("rec-inc-1"),
                categoryId = EntityId("cat-2"),
                categoryName = "Maaş",
                categoryColorHex = "#3B82F6",
                categoryIconKey = "briefcase",
                isCategoryMissing = false,
                amount = Money(125050L, Currency.TRY),
                currency = Currency.TRY,
                type = TransactionType.INCOME,
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 3,
                startDate = LocalDate(2026, 6, 1),
                endDate = null,
                lastGeneratedDate = null,
                isNeverGenerated = true,
                nextOccurrenceDate = LocalDate(2026, 9, 1),
                isActive = false,
                isPaused = true,
                description = "3 Aylık Prim",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
            )

        val uiState =
            RecurringTransactionsUiState(
                isLoading = false,
                items = listOf(expenseItem, incomeItem),
                observationError = null,
                activeWorkspaceName = "Kişisel",
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                RecurringTransactionsScreen(
                    state = uiState,
                    onRetry = {},
                    onAddRecurringTransaction = {},
                    onRecurringTransactionClick = {},
                )
            }
        }

        // TR Assertions
        composeRule.onNodeWithText("Tekrarlayan\nişlemler").assertIsDisplayed()
        composeRule.onNodeWithText("+ Yeni kural").assertIsDisplayed()
        composeRule.onNodeWithText("Kira").assertIsDisplayed()
        composeRule.onNodeWithText("3 Aylık Prim").assertIsDisplayed()
        composeRule.onNodeWithText("Aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Duraklatıldı").assertIsDisplayed()
        composeRule.onNodeWithText("Her ay").assertIsDisplayed()
        composeRule.onNodeWithText("Her 3 ayda bir").assertIsDisplayed()
        composeRule.onNodeWithText("500,00 ₺", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("+1.250,50 ₺").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN Assertions
        composeRule.onNodeWithText("Recurring\ntransactions").assertIsDisplayed()
        composeRule.onNodeWithText("+ New rule").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onAllNodesWithText("Paused").assertCountEquals(2)
        composeRule.onNodeWithText("Every month").assertIsDisplayed()
        composeRule.onNodeWithText("Every 3 months").assertIsDisplayed()
        composeRule.onNodeWithText("500.00 ₺", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("+1,250.50 ₺").assertIsDisplayed()
    }

    @Test
    fun missingCategoryFallback_dynamicallyResolvesInTrAndEn() {
        var languageTag by mutableStateOf("tr")

        val missingCategoryItem =
            RecurringTransactionDisplayModel(
                id = EntityId("rec-missing-1"),
                categoryId = EntityId("cat-deleted"),
                categoryName = null,
                categoryColorHex = null,
                categoryIconKey = null,
                isCategoryMissing = true,
                amount = Money(25000L, Currency.TRY),
                currency = Currency.TRY,
                type = TransactionType.EXPENSE,
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
                lastGeneratedDate = null,
                isNeverGenerated = true,
                nextOccurrenceDate = LocalDate(2026, 8, 15),
                isActive = true,
                isPaused = false,
                description = null,
                paymentMethod = PaymentMethod.CREDIT_CARD,
            )

        val uiState =
            RecurringTransactionsUiState(
                isLoading = false,
                items = listOf(missingCategoryItem),
                observationError = null,
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                RecurringTransactionsScreen(
                    state = uiState,
                    onRetry = {},
                    onAddRecurringTransaction = {},
                    onRecurringTransactionClick = {},
                )
            }
        }

        // TR: Bilinmeyen Kategori
        composeRule.onNodeWithText("Bilinmeyen Kategori").assertIsDisplayed()
        composeRule.onNodeWithText("Her 2 haftada bir").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Unknown Category
        composeRule.onNodeWithText("Unknown Category").assertIsDisplayed()
        composeRule.onNodeWithText("Every 2 weeks").assertIsDisplayed()
    }

    @Test
    fun formLabelsAndFieldErrors_localizedInTrAndEn() {
        var languageTag by mutableStateOf("tr")
        var currentErrors by mutableStateOf(RecurringTransactionFormInputErrors())

        val input =
            RecurringTransactionFormInput(
                recurringTransactionId = null,
                amountInput = "",
                currency = Currency.TRY,
                type = TransactionType.EXPENSE,
                categoryId = null,
                description = "",
                paymentMethod = PaymentMethod.CREDIT_CARD,
                frequency = RecurrenceFrequency.MONTHLY,
                intervalInput = "1",
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                RecurringTransactionFormScreen(
                    input = input,
                    categories = emptyList(),
                    errors = currentErrors,
                    mutationState = RecurringTransactionMutationState(isSubmitting = false),
                    isEditMode = false,
                    isActive = true,
                    onBack = {},
                    onSetActive = {},
                    onRequestDelete = {},
                    onAmountChange = {},
                    onTypeChange = {},
                    onCategoryChange = {},
                    onDescriptionChange = {},
                    onPaymentMethodChange = {},
                    onFrequencyChange = {},
                    onIntervalChange = {},
                    onStartDateClick = {},
                    onEndDateClick = {},
                    onClearEndDate = {},
                    onSubmit = {},
                )
            }
        }

        // TR Form labels and placeholder
        composeRule.onNodeWithText("Yeni kural").assertIsDisplayed()
        composeRule.onNodeWithText("Tutar").assertIsDisplayed()
        composeRule.onNodeWithText("Gider").assertIsDisplayed()
        composeRule.onNodeWithText("Gelir").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrar").assertIsDisplayed()
        composeRule.onNodeWithText("Başlangıç").assertIsDisplayed()
        composeRule.onNodeWithText("Bitiş").assertIsDisplayed()
        composeRule.onNodeWithText("Ödeme yöntemi").assertIsDisplayed()
        composeRule.onNodeWithText("Açıklama (isteğe bağlı)").assertIsDisplayed()
        composeRule.onNodeWithText("0,00").assertExists()
        composeRule.onNodeWithText("Kuralı kaydet").assertIsDisplayed()

        // Set all errors to verify TR error messages
        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_REQUIRED,
                    categoryError = RecurringTransactionFormFieldError.CATEGORY_REQUIRED,
                    intervalError = RecurringTransactionFormFieldError.INTERVAL_INVALID,
                    startDateError = RecurringTransactionFormFieldError.START_DATE_REQUIRED,
                    endDateError = RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE,
                    descriptionError = RecurringTransactionFormFieldError.DESCRIPTION_TOO_LONG,
                )
        }
        composeRule.onNodeWithText("Sıfırdan büyük bir tutar gir.").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori seçimi zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Geçersiz tekrar aralığı.").assertIsDisplayed()
        composeRule.onNodeWithText("Başlangıç tarihi zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Bitiş tarihi başlangıçtan önce olamaz.").assertIsDisplayed()
        composeRule.onNodeWithText("Açıklama en fazla 500 karakter olabilir.").assertIsDisplayed()

        // Verify remaining errors in TR
        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_INVALID,
                    categoryError = RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH,
                    intervalError = RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE,
                )
        }
        composeRule.onNodeWithText("Geçersiz tutar formatı.").assertIsDisplayed()
        composeRule.onNodeWithText("Seçilen kategori işlem türüyle uyuşmuyor.").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrar aralığı en az 1 olmalıdır.").assertIsDisplayed()

        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE,
                )
        }
        composeRule.onNodeWithText("Sıfırdan büyük bir tutar gir.").assertIsDisplayed()

        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE,
                )
        }
        composeRule.onNodeWithText("Tutar izin verilen limiti aşıyor.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_REQUIRED,
                    categoryError = RecurringTransactionFormFieldError.CATEGORY_REQUIRED,
                    intervalError = RecurringTransactionFormFieldError.INTERVAL_INVALID,
                    startDateError = RecurringTransactionFormFieldError.START_DATE_REQUIRED,
                    endDateError = RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE,
                    descriptionError = RecurringTransactionFormFieldError.DESCRIPTION_TOO_LONG,
                )
        }

        // EN Form labels and placeholder
        composeRule.onNodeWithText("New rule").assertIsDisplayed()
        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("Category").assertIsDisplayed()
        composeRule.onNodeWithText("Repeat").assertIsDisplayed()
        composeRule.onNodeWithText("Start date").assertIsDisplayed()
        composeRule.onNodeWithText("End date").assertIsDisplayed()
        composeRule.onNodeWithText("Payment method").assertIsDisplayed()
        composeRule.onNodeWithText("Description (optional)").assertIsDisplayed()
        composeRule.onNodeWithText("0.00").assertExists()
        composeRule.onNodeWithText("Save rule").assertIsDisplayed()

        // EN Error messages
        composeRule.onNodeWithText("Enter an amount greater than zero.").assertIsDisplayed()
        composeRule.onNodeWithText("Category selection is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Invalid repeat interval.").assertIsDisplayed()
        composeRule.onNodeWithText("Start date is required.").assertIsDisplayed()
        composeRule.onNodeWithText("End date cannot be before start date.").assertIsDisplayed()
        composeRule.onNodeWithText("Description can be at most 500 characters.").assertIsDisplayed()

        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_INVALID,
                    categoryError = RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH,
                    intervalError = RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE,
                )
        }
        composeRule.onNodeWithText("Invalid amount format.").assertIsDisplayed()
        composeRule.onNodeWithText("Selected category does not match the transaction type.").assertIsDisplayed()
        composeRule.onNodeWithText("Repeat interval must be at least 1.").assertIsDisplayed()

        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE,
                )
        }
        composeRule.onNodeWithText("Enter an amount greater than zero.").assertIsDisplayed()

        composeRule.runOnIdle {
            currentErrors =
                RecurringTransactionFormInputErrors(
                    amountError = RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE,
                )
        }
        composeRule.onNodeWithText("Amount exceeds the allowed limit.").assertIsDisplayed()
    }

    @Test
    fun runtimeTransition_updatesTextDateMoneyAndDialogs() {
        var languageTag by mutableStateOf("tr")
        var showDeleteDialog by mutableStateOf(false)

        val item =
            RecurringTransactionDisplayModel(
                id = EntityId("rec-dyn-1"),
                categoryId = EntityId("cat-1"),
                categoryName = "Kira",
                categoryColorHex = "#10B981",
                categoryIconKey = "home",
                isCategoryMissing = false,
                amount = Money(750000L, Currency.TRY),
                currency = Currency.TRY,
                type = TransactionType.EXPENSE,
                frequency = RecurrenceFrequency.YEARLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = LocalDate(2027, 8, 1),
                lastGeneratedDate = LocalDate(2026, 8, 1),
                isNeverGenerated = false,
                nextOccurrenceDate = LocalDate(2027, 8, 1),
                isActive = true,
                isPaused = false,
                description = "Yıllık Sözleşme",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                RecurringTransactionsScreen(
                    state =
                        RecurringTransactionsUiState(
                            isLoading = false,
                            items = listOf(item),
                        ),
                    onRetry = {},
                    onAddRecurringTransaction = {},
                    onRecurringTransactionClick = {},
                )

                if (showDeleteDialog) {
                    RecurringTransactionDeleteDialog(
                        isSubmitting = false,
                        onConfirm = {},
                        onDismiss = { showDeleteDialog = false },
                    )
                }
            }
        }

        // TR initial assertions
        composeRule.onNodeWithText("Tekrarlayan\nişlemler").assertIsDisplayed()
        composeRule.onNodeWithText("7.500,00 ₺", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Her yıl").assertIsDisplayed()
        composeRule.onNodeWithText("1 Ağustos 2027", substring = true).assertIsDisplayed()

        // Open delete dialog in TR
        composeRule.runOnIdle { showDeleteDialog = true }
        composeRule.onNodeWithText("Kural silinsin mi?").assertIsDisplayed()
        composeRule.onNodeWithText("Kuralı sil").assertIsDisplayed()
        composeRule.onNodeWithText("Vazgeç").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Recurring\ntransactions").assertIsDisplayed()
        composeRule.onNodeWithText("7,500.00 ₺", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Every year").assertIsDisplayed()
        composeRule.onNodeWithText("August 1, 2027", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Delete this rule?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete rule").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun formEditRoute_preservesUnsavedDraftAcrossLanguageChange() {
        var languageTag by mutableStateOf("tr")

        val recId = EntityId("rec-seed-preserve")
        val sampleRecurring =
            RecurringTransaction(
                id = recId,
                ownerId = EntityId("user-1"),
                workspaceId = null,
                amount = Money(250000L, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("cat-exp-1"),
                description = "Ofis Kirası",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                rule =
                    RecurrenceRule(
                        frequency = RecurrenceFrequency.MONTHLY,
                        interval = 1,
                        startDate = LocalDate(2026, 8, 1),
                        endDate = null,
                    ),
                lastGeneratedDate = null,
                isActive = true,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            )

        val sampleCat =
            Category(
                id = EntityId("cat-exp-1"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                name = "Kira",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#10B981"),
                icon = CategoryIcon("home"),
                isDefault = false,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            )

        val fakeRecurringRepo =
            FakeRecurringTransactionRepository().apply {
                singleRecurringFlows[recId] = MutableStateFlow(sampleRecurring)
            }
        val fakeCatRepo =
            FakeCategoryRepository().apply {
                categoriesFlow.value = listOf(sampleCat)
            }
        val fakeWsRepo = FakeWorkspaceRepository()

        val viewModel =
            RecurringTransactionsViewModel(
                observeRecurringTransactionsUseCase = ObserveRecurringTransactionsUseCase(fakeRecurringRepo),
                observeRecurringTransactionUseCase = ObserveRecurringTransactionUseCase(fakeRecurringRepo),
                observeCategoriesUseCase = ObserveCategoriesUseCase(fakeCatRepo),
                createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(fakeRecurringRepo),
                updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(fakeRecurringRepo),
                setRecurringTransactionActiveUseCase = SetRecurringTransactionActiveUseCase(fakeRecurringRepo),
                deleteRecurringTransactionUseCase = DeleteRecurringTransactionUseCase(fakeRecurringRepo),
                observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(fakeWsRepo),
            )

        composeRule.setContent {
            val resources = LocalContext.current.resources
            val configuration = android.content.res.Configuration(LocalConfiguration.current)
            val locale = Locale.forLanguageTag(languageTag)
            Locale.setDefault(locale)
            configuration.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)

            CompositionLocalProvider(
                LocalConfiguration provides configuration,
            ) {
                RecurringTransactionFormScreenRoute(
                    availableCategories = listOf(sampleCat),
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onMessage = {},
                    initialRecurringTransactionId = recId,
                )
            }
        }

        // Wait for edit load state to seed input
        composeRule.waitForIdle()

        // Verify initial loaded values
        composeRule.onAllNodesWithText("Ofis Kirası")[0].assertExists()

        // User edits the description text
        composeRule.onNodeWithText("Ofis Kirası").performTextReplacement("Merkez Ofis Kirası 2026")
        composeRule.onAllNodesWithText("Merkez Ofis Kirası 2026")[0].assertExists()

        // Switch language at runtime to EN
        composeRule.runOnIdle { languageTag = "en" }

        // Unsaved user input must be preserved and labels must update to English
        composeRule.onAllNodesWithText("Merkez Ofis Kirası 2026")[0].assertExists()
        composeRule.onNodeWithText("Edit rule").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
    }

    private class FakeRecurringTransactionRepository : RecurringTransactionRepository {
        val recurringFlow = MutableStateFlow<List<RecurringTransaction>>(emptyList())
        val singleRecurringFlows = mutableMapOf<EntityId, MutableStateFlow<RecurringTransaction?>>()

        override fun observeRecurringTransactions(): Flow<List<RecurringTransaction>> = recurringFlow

        override fun observeRecurringTransaction(id: EntityId): Flow<RecurringTransaction?> =
            singleRecurringFlows.getOrPut(id) { MutableStateFlow(null) }

        override suspend fun create(command: CreateRecurringTransactionCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("rec-new"))

        override suspend fun update(command: UpdateRecurringTransactionCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun setActive(command: SetRecurringTransactionActiveCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun generateDueTransactions(
            throughDate: LocalDate,
            maxOccurrencesPerRule: Int,
            maxTotalOccurrences: Int,
            createdAt: Instant,
        ): RepositoryResult<GenerateRecurringTransactionsResult> = error("Not supported")
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun observeCategories(
            type: TransactionType?,
            workspaceId: EntityId?,
        ): Flow<List<Category>> = categoriesFlow

        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(null)

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow

        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("cat-1"))

        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
