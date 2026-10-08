@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:argument-list-wrapping",
    "ktlint:standard:no-unused-imports",
    "ktlint:standard:no-empty-first-line-in-class-body",
    "ktlint:standard:class-signature",
    "ktlint:standard:blank-line-before-declaration",
    "ktlint:standard:parameter-list-wrapping",
    "ktlint:standard:chain-method-continuation",
    "ktlint:standard:function-expression-body",
)

package com.feniqo.mobile.presentation.subscription

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.SetSubscriptionLifecycleCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPaymentSourceType
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.presentation.screen.SubscriptionsScreen
import com.feniqo.mobile.presentation.screen.SubscriptionDetailScreen
import com.feniqo.mobile.presentation.screen.SubscriptionFormScreen
import com.feniqo.mobile.presentation.component.SubscriptionDeleteDialog
import com.feniqo.mobile.presentation.component.SubscriptionAdvanceRenewalDialog
import com.feniqo.mobile.presentation.subscription.SubscriptionMutationState
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase
import com.feniqo.mobile.domain.usecase.CreateSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPriceHistoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionActiveUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionLifecycleUseCase
import com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FakeWorkspaceRepository
import com.feniqo.mobile.presentation.component.SubscriptionAdvanceRenewalDialog
import com.feniqo.mobile.presentation.component.SubscriptionDeleteDialog
import com.feniqo.mobile.presentation.screen.SubscriptionDetailScreen
import com.feniqo.mobile.presentation.screen.SubscriptionFormScreen
import com.feniqo.mobile.presentation.screen.SubscriptionsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class SubscriptionsLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixedToday = LocalDate(2026, 10, 1)

    private class FakeCurrentDateProvider(
        private val today: LocalDate,
    ) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeSubscriptionRepository(
        val subscriptionsFlow: MutableStateFlow<List<Subscription>> = MutableStateFlow(emptyList()),
        val singleSubscriptionFlow: MutableStateFlow<Subscription?> = MutableStateFlow(null),
    ) : SubscriptionRepository {

        override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptionsFlow

        override fun observeSubscription(id: EntityId): Flow<Subscription?> = singleSubscriptionFlow

        override fun observePriceHistories(subscriptionId: EntityId?): Flow<List<SubscriptionPriceHistory>> = flowOf(emptyList())

        override fun observePayments(subscriptionId: EntityId?): Flow<List<SubscriptionPayment>> = flowOf(emptyList())

        override suspend fun create(command: CreateSubscriptionCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("sub-new"))

        override suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun setActive(command: SetSubscriptionActiveCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun setLifecycle(command: SetSubscriptionLifecycleCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> =
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 11, 1)))

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository(
        categories: List<Category> = emptyList(),
    ) : CategoryRepository {

        val categoriesFlow = MutableStateFlow(categories)

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow

        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(categoriesFlow.value.firstOrNull { it.id == id })

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private fun sampleDisplayModel(
        id: String,
        name: String,
        amountMinor: Long,
        renewalStatus: SubscriptionRenewalStatus,
        lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
        nextRenewalDate: LocalDate = LocalDate(2026, 10, 1),
    ): SubscriptionDisplayModel = SubscriptionDisplayModel(
        id = EntityId(id),
        name = name,
        categoryId = EntityId("cat-1"),
        categoryName = "Eğlence",
        categoryColorHex = "#10B981",
        categoryIconKey = "entertainment",
        isCategoryUnassigned = false,
        isCategoryMissing = false,
        amount = Money(amountMinor, Currency.TRY),
        currency = Currency.TRY,
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 1,
        startDate = LocalDate(2025, 10, 1),
        endDate = null,
        nextRenewalDate = nextRenewalDate,
        isActive = lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE,
        isPaused = lifecycleStatus == SubscriptionLifecycleStatus.PAUSED,
        renewalStatus = renewalStatus,
        lifecycleStatus = lifecycleStatus,
    )

    @Test
    fun subscriptions_screen_and_cards_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val dueTodayItem = sampleDisplayModel(
            id = "sub-1",
            name = "Spotify",
            amountMinor = 5999L,
            renewalStatus = SubscriptionRenewalStatus.DueToday,
            nextRenewalDate = LocalDate(2026, 10, 1),
        )

        val overdueItem = sampleDisplayModel(
            id = "sub-2",
            name = "Netflix",
            amountMinor = 14999L,
            renewalStatus = SubscriptionRenewalStatus.Overdue(daysOverdue = 3L),
            nextRenewalDate = LocalDate(2026, 9, 28),
        )

        val pausedItem = sampleDisplayModel(
            id = "sub-3",
            name = "YouTube Premium",
            amountMinor = 7999L,
            renewalStatus = SubscriptionRenewalStatus.Inactive,
            lifecycleStatus = SubscriptionLifecycleStatus.PAUSED,
            nextRenewalDate = LocalDate(2026, 10, 15),
        )

        val state = SubscriptionsUiState(
            isLoading = false,
            items = listOf(dueTodayItem, overdueItem, pausedItem),
            filteredItems = listOf(dueTodayItem, overdueItem, pausedItem),
            selectedFilter = SubscriptionFilter.ALL,
            estimatedSummaries = listOf(
                SubscriptionEstimatedCostSummaryUiModel(
                    currency = Currency.TRY,
                    monthlyCost = Money(20998L, Currency.TRY),
                    yearlyCost = Money(251976L, Currency.TRY),
                    activeCount = 2,
                )
            ),
            actualSpendings = listOf(
                SubscriptionActualSpendingUiModel(
                    currency = Currency.TRY,
                    currentMonthActual = Money(5999L, Currency.TRY),
                    previousMonthActual = Money(5999L, Currency.TRY),
                    monthlyTrendBasisPoints = 0L,
                    isPreviousZero = false,
                    isCurrentPartial = false,
                )
            ),
            upcomingPayments = listOf(dueTodayItem),
            overduePayments = listOf(overdueItem),
            insights = listOf(
                SubscriptionInsightUiModel(
                    id = "insight-1",
                    payload = SubscriptionInsightPayload.PriceIncreases(count = 1),
                    isWarning = false,
                )
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                SubscriptionsScreen(
                    state = state,
                    onRetry = {},
                    onAddSubscription = {},
                    onSubscriptionClick = {},
                )
            }
        }

        // TR Assertions
        composeRule.onNodeWithText("Abonelikler").assertIsDisplayed()
        composeRule.onNodeWithText("Tahmini aylık maliyet").assertIsDisplayed()
        composeRule.onNodeWithText("Yıllık yaklaşık").assertIsDisplayed()
        composeRule.onNodeWithText("Tümü").assertIsDisplayed()
        composeRule.onNodeWithText("Aktif").assertIsDisplayed()
        composeRule.onAllNodesWithText("Duraklatıldı")[0].assertExists()
        composeRule.onAllNodesWithText("Bugün yenileniyor")[0].assertExists()
        composeRule.onAllNodesWithText("3 gün gecikti")[0].assertExists()
        composeRule.onNodeWithText("Bu ay kaydedilen ödemeler").assertExists()
        composeRule.onAllNodesWithText("59,99 ₺", substring = true)[0].assertExists()
        composeRule.onAllNodesWithText("149,99 ₺", substring = true)[0].assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN Assertions
        composeRule.onNodeWithText("Subscriptions").assertIsDisplayed()
        composeRule.onNodeWithText("Estimated monthly cost").assertIsDisplayed()
        composeRule.onNodeWithText("Yearly approx.").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onAllNodesWithText("Paused")[0].assertExists()
        composeRule.onAllNodesWithText("Renews today")[0].assertExists()
        composeRule.onAllNodesWithText("3 days overdue")[0].assertExists()
        composeRule.onNodeWithText("Payments recorded this month").assertExists()
        composeRule.onAllNodesWithText("59.99 ₺", substring = true)[0].assertExists()
        composeRule.onAllNodesWithText("149.99 ₺", substring = true)[0].assertExists()
    }

    @Test
    fun subscription_detail_screen_and_components_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val sampleSub = Subscription(
            id = EntityId("sub-detail-1"),
            ownerId = EntityId("owner-1"),
            workspaceId = null,
            name = "Netflix",
            amount = Money(22900L, Currency.TRY),
            categoryId = EntityId("cat-1"),
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2025, 10, 14),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 10, 14),
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
        )

        val state = SubscriptionDetailUiState(
            isLoading = false,
            subscription = sampleSub,
            category = Category(
                id = EntityId("cat-1"),
                ownerId = EntityId("owner-1"),
                workspaceId = null,
                name = "Eğlence",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#10B981"),
                icon = CategoryIcon("entertainment"),
                isDefault = false,
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            ),
            monthlyChartBars = listOf(
                SubscriptionMonthlyBarModel(year = 2026, monthNumber = 9, amount = Money(22900L, Currency.TRY), isCurrentMonth = false, ratio = 1f),
                SubscriptionMonthlyBarModel(year = 2026, monthNumber = 10, amount = Money(22900L, Currency.TRY), isCurrentMonth = true, ratio = 1f),
            ),
            recentPayments = listOf(
                SubscriptionRecentPaymentModel(
                    id = "pay-1",
                    paymentDate = LocalDate(2026, 9, 14),
                    amount = Money(22900L, Currency.TRY),
                    isManual = true,
                ),
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                SubscriptionDetailScreen(
                    uiState = state,
                    onBack = {},
                    onEditClick = {},
                    onToggleLifecycleClick = {},
                    onCancelClick = {},
                    onToggleReminderClick = {},
                    onAdvanceRenewal = {},
                    onManageSubscription = {},
                )
            }
        }

        // TR Assertions
        composeRule.onNodeWithText("Abonelikler").assertIsDisplayed()
        composeRule.onNodeWithText("Döngü").assertIsDisplayed()
        composeRule.onNodeWithText("Başlangıç").assertIsDisplayed()
        composeRule.onNodeWithText("Sonraki yenileme", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Aylık ödemeler").assertExists()
        composeRule.onNodeWithText("Son ödemeler").assertExists()
        composeRule.onNodeWithText("Ödendi olarak kaydet").assertExists()
        composeRule.onNodeWithText("Aboneliği düzenle").assertExists()
        composeRule.onAllNodesWithText("229,00 ₺")[0].assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN Assertions
        composeRule.onNodeWithText("Subscriptions").assertIsDisplayed()
        composeRule.onNodeWithText("Cycle").assertIsDisplayed()
        composeRule.onNodeWithText("Start date").assertIsDisplayed()
        composeRule.onNodeWithText("Next renewal", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Monthly payments").assertExists()
        composeRule.onNodeWithText("Recent payments").assertExists()
        composeRule.onNodeWithText("Record as paid").assertExists()
        composeRule.onNodeWithText("Edit subscription").assertExists()
        composeRule.onAllNodesWithText("229.00 ₺")[0].assertExists()
    }

    @Test
    fun subscription_form_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val input = SubscriptionFormInput(
            subscriptionId = null,
            nameInput = "",
            amountInput = "",
            currency = Currency.TRY,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalInput = "1",
            startDate = LocalDate(2026, 10, 1),
        )

        val errors = SubscriptionFormInputErrors(
            nameError = SubscriptionFormFieldError.NAME_REQUIRED,
            amountError = SubscriptionFormFieldError.AMOUNT_REQUIRED,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                SubscriptionFormScreen(
                    input = input,
                    categories = emptyList(),
                    errors = errors,
                    mutationState = SubscriptionMutationState(isSubmitting = false),
                    isEditMode = false,
                    isActive = true,
                    onBack = {},
                    onSetActive = {},
                    onRequestAdvanceRenewal = {},
                    onRequestDelete = {},
                    onNameChange = {},
                    onAmountChange = {},
                    onCurrencyChange = {},
                    onCategoryChange = {},
                    onFrequencyChange = {},
                    onIntervalChange = {},
                    onStartDateClick = {},
                    onEndDateClick = {},
                    onClearEndDate = {},
                    onAutoRenewChange = {},
                    onReminderEnabledChange = {},
                    onWebsiteUrlChange = {},
                    onNotesChange = {},
                    onTemplateSelect = {},
                    onSubmit = {},
                )
            }
        }

        // TR Assertions
        composeRule.onNodeWithText("Yeni abonelik").assertIsDisplayed()
        composeRule.onNodeWithText("Servis / abonelik adı").assertIsDisplayed()
        composeRule.onNodeWithText("Abonelik adı zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Tutar zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("0,00").assertExists()
        composeRule.onNodeWithText("Kaydet").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN Assertions
        composeRule.onNodeWithText("New subscription").assertIsDisplayed()
        composeRule.onNodeWithText("Service / subscription name").assertIsDisplayed()
        composeRule.onNodeWithText("Subscription name is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("0.00").assertExists()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun subscription_dialogs_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var showDeleteDialog by mutableStateOf(true)
        var showAdvanceDialog by mutableStateOf(true)

        composeRule.setContent {
            App(languageTag = languageTag) {
                if (showDeleteDialog) {
                    SubscriptionDeleteDialog(
                        isSubmitting = false,
                        onConfirm = {},
                        onDismiss = { showDeleteDialog = false },
                    )
                }
                if (showAdvanceDialog) {
                    SubscriptionAdvanceRenewalDialog(
                        subscriptionName = "Spotify",
                        amount = Money(22900L, Currency.TRY),
                        nextRenewalDate = LocalDate(2026, 10, 1),
                        calculatedFollowingRenewalDate = LocalDate(2026, 11, 1),
                        isSubmitting = false,
                        onConfirm = {},
                        onDismiss = { showAdvanceDialog = false },
                    )
                }
            }
        }

        // TR Assertions
        composeRule.onNodeWithText("Aboneliği Sil").assertIsDisplayed()
        composeRule.onNodeWithText("Sil").assertIsDisplayed()
        composeRule.onAllNodesWithText("Vazgeç")[0].assertExists()
        composeRule.onAllNodesWithText("Ödendi Olarak Kaydet")[0].assertExists()
        composeRule.onAllNodesWithText("-229,00 ₺")[0].assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN Assertions
        composeRule.onNodeWithText("Delete Subscription").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cancel")[0].assertExists()
        composeRule.onAllNodesWithText("Record as Paid")[0].assertExists()
        composeRule.onAllNodesWithText("-229.00 ₺")[0].assertExists()
    }

    @Test
    fun subscription_form_edit_route_preserves_unsaved_draft_across_language_change() {
        var languageTag by mutableStateOf("tr")

        val subId = EntityId("sub-edit-1")
        val sampleSub = Subscription(
            id = subId,
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Spotify",
            amount = Money(5999L, Currency.TRY),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 10, 1),
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val subRepo = FakeSubscriptionRepository(
            singleSubscriptionFlow = MutableStateFlow(sampleSub),
        )
        val catRepo = FakeCategoryRepository()
        val wsRepo = FakeWorkspaceRepository()
        val dateProvider = FakeCurrentDateProvider(fixedToday)

        val viewModel = SubscriptionsViewModel(
            observeSubscriptionsUseCase = ObserveSubscriptionsUseCase(subRepo),
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeSubscriptionPriceHistoriesUseCase = ObserveSubscriptionPriceHistoriesUseCase(subRepo),
            observeSubscriptionPaymentsUseCase = ObserveSubscriptionPaymentsUseCase(subRepo),
            createSubscriptionUseCase = CreateSubscriptionUseCase(subRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subRepo),
            setSubscriptionActiveUseCase = SetSubscriptionActiveUseCase(subRepo),
            setSubscriptionLifecycleUseCase = SetSubscriptionLifecycleUseCase(subRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subRepo),
            currentDateProvider = dateProvider,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(wsRepo),
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
                SubscriptionFormScreenRoute(
                    availableCategories = emptyList(),
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onMessage = {},
                    initialSubscriptionId = subId,
                )
            }
        }

        // Wait for edit load state to seed input
        composeRule.waitForIdle()

        // Verify initial loaded name "Spotify"
        composeRule.onAllNodesWithText("Spotify")[0].assertExists()

        // User enters extra text to change draft
        composeRule.onNodeWithContentDescription("Servis / abonelik adı").performTextReplacement("Spotify HiFi")
        composeRule.onAllNodesWithText("Spotify HiFi")[0].assertExists()

        // Switch language at runtime to EN
        composeRule.runOnIdle { languageTag = "en" }

        // Draft should be preserved, screen text should be English
        composeRule.onAllNodesWithText("Spotify HiFi")[0].assertExists()
        composeRule.onNodeWithContentDescription("Service / subscription name").assertExists()
        composeRule.onNodeWithText("Edit subscription").assertExists()
    }
}
