package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.CalculateReportDateRangeUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveFilteredReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveMultiCurrencyReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportAvailabilityUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportSyncStatusUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FakeWorkspaceRepository
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate(2026, 9, 17)
    private val testWorkspace = Workspace(
        id = EntityId("ws-1"),
        name = "Kişisel Bütçe",
        ownerId = EntityId("user-1"),
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
        type = WorkspaceType.PERSONAL,
        currency = Currency.TRY,
    )

    private fun createTx(
        id: String,
        amountMinor: Long,
        currency: Currency = Currency.TRY,
        type: TransactionType = TransactionType.EXPENSE,
        date: LocalDate = LocalDate(2026, 9, 15),
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = EntityId("ws-1"),
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId("cat-1"),
        description = "Test transaction",
        paymentMethod = PaymentMethod.CASH,
        transactionDate = date,
        receiptPath = null,
        installment = null,
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    )

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> =
            transactionsFlow.map { list ->
                val p = filter.period
                val t = filter.type
                val c = filter.categoryId
                val w = filter.workspaceId
                list.filter { tx ->
                    val inPeriod = p == null || (tx.transactionDate >= p.startDate && tx.transactionDate <= p.endDate)
                    val inType = t == null || tx.type == t
                    val inCategory = c == null || tx.categoryId == c
                    val inWorkspace = w == null || tx.workspaceId == w
                    inPeriod && inType && inCategory && inWorkspace
                }
            }

        override fun observeTransaction(id: EntityId): Flow<Transaction?> = TODO()
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = TODO()
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = TODO()
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = TODO()
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = TODO()
    }

    private class FakeSyncRepository : SyncRepository {
        val syncOverviewFlow = MutableStateFlow(
            SyncOverview(
                phase = SyncPhase.IDLE,
                pendingOperationCount = 0,
                failedOperationCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAt = null,
                lastError = null,
            )
        )

        override fun observeOverview(): Flow<SyncOverview> = syncOverviewFlow
        override fun observeConflicts(): Flow<List<com.feniqo.mobile.domain.repository.SyncConflict>> = TODO()
        override suspend fun requestSync(): RepositoryResult<Unit> = TODO()
        override suspend fun retryFailedOperations(): RepositoryResult<Unit> = TODO()
        override suspend fun resolveConflict(entityId: EntityId, resolution: ConflictResolution): RepositoryResult<Unit> = TODO()
    }

    private class TestUserSettingsRepository : UserSettingsRepository {
        val settingsFlow = MutableStateFlow(UserSettings(maskAmounts = false))
        override fun observeSettings(): Flow<UserSettings> = settingsFlow
        override suspend fun updateTheme(theme: ThemePreference): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateCurrency(currency: Currency): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateLanguage(language: AppLanguage): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateRegion(region: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateDateFormat(format: DateFormatPreference): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateNumberFormat(format: NumberFormatPreference): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateFirstDayOfWeek(firstDay: FirstDayOfWeekPreference): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun updateMaskAmounts(mask: Boolean): RepositoryResult<Unit> {
            settingsFlow.value = settingsFlow.value.copy(maskAmounts = mask)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateNotificationPreferences(preferences: NotificationPreferences): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class TestCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override fun observeCategory(id: EntityId): Flow<Category?> = TODO()
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override suspend fun create(category: Category): RepositoryResult<EntityId> = TODO()
        override suspend fun update(category: Category): RepositoryResult<Unit> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
    }

    private class TestBudgetRepository : com.feniqo.mobile.domain.repository.BudgetRepository {
        val budgetsFlow = MutableStateFlow<List<com.feniqo.mobile.domain.model.Budget>>(emptyList())
        override fun observeBudgets(month: com.feniqo.mobile.domain.model.YearMonth, workspaceId: EntityId?): Flow<List<com.feniqo.mobile.domain.model.Budget>> = budgetsFlow
        override fun observeBudget(id: EntityId): Flow<com.feniqo.mobile.domain.model.Budget?> = TODO()
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateBudgetCommand): RepositoryResult<EntityId> = TODO()
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateBudgetCommand): RepositoryResult<Unit> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
        override suspend fun copyBudgets(command: com.feniqo.mobile.domain.model.CopyBudgetsCommand): RepositoryResult<com.feniqo.mobile.domain.model.CopyBudgetsResult> = TODO()
    }

    private class TestSubscriptionRepository : com.feniqo.mobile.domain.repository.SubscriptionRepository {
        val subsFlow = MutableStateFlow<List<com.feniqo.mobile.domain.model.Subscription>>(emptyList())
        override fun observeSubscriptions(): Flow<List<com.feniqo.mobile.domain.model.Subscription>> = subsFlow
        override fun observeSubscription(id: EntityId): Flow<com.feniqo.mobile.domain.model.Subscription?> = TODO()
        override fun observePriceHistories(subscriptionId: EntityId?): Flow<List<com.feniqo.mobile.domain.model.SubscriptionPriceHistory>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun observePayments(subscriptionId: EntityId?): Flow<List<com.feniqo.mobile.domain.model.SubscriptionPayment>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateSubscriptionCommand): RepositoryResult<EntityId> = TODO()
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateSubscriptionCommand): RepositoryResult<Unit> = TODO()
        override suspend fun setActive(command: com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand): RepositoryResult<Unit> = TODO()
        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
    }

    private class TestDebtRepository : com.feniqo.mobile.domain.repository.DebtRepository {
        val debtsFlow = MutableStateFlow<List<com.feniqo.mobile.domain.model.Debt>>(emptyList())
        override fun observeDebts(): Flow<List<com.feniqo.mobile.domain.model.Debt>> = debtsFlow
        override fun observeDebt(id: EntityId): Flow<com.feniqo.mobile.domain.model.Debt?> = TODO()
        override fun observePayments(debtId: EntityId): Flow<List<com.feniqo.mobile.domain.model.DebtPayment>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateDebtCommand): RepositoryResult<EntityId> = TODO()
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateDebtCommand): RepositoryResult<Unit> = TODO()
        override suspend fun addPayment(command: com.feniqo.mobile.domain.model.AddDebtPaymentCommand): RepositoryResult<EntityId> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
    }

    private class TestRecurringTransactionRepository : com.feniqo.mobile.domain.repository.RecurringTransactionRepository {
        val recFlow = MutableStateFlow<List<com.feniqo.mobile.domain.model.RecurringTransaction>>(emptyList())
        override fun observeRecurringTransactions(): Flow<List<com.feniqo.mobile.domain.model.RecurringTransaction>> = recFlow
        override fun observeRecurringTransaction(id: EntityId): Flow<com.feniqo.mobile.domain.model.RecurringTransaction?> = TODO()
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand): RepositoryResult<EntityId> = TODO()
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand): RepositoryResult<Unit> = TODO()
        override suspend fun setActive(command: com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand): RepositoryResult<Unit> = TODO()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = TODO()
        override suspend fun generateDueTransactions(
            throughDate: LocalDate,
            maxOccurrencesPerRule: Int,
            maxTotalOccurrences: Int,
            createdAt: kotlinx.datetime.Instant,
        ): RepositoryResult<com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult> = TODO()
    }

    private fun createViewModel(
        txRepo: FakeTransactionRepository,
        syncRepo: FakeSyncRepository,
        userSettingsRepo: TestUserSettingsRepository = TestUserSettingsRepository(),
        workspaceRepo: FakeWorkspaceRepository = FakeWorkspaceRepository(testWorkspace),
        categoryRepo: TestCategoryRepository = TestCategoryRepository(),
        budgetRepo: TestBudgetRepository = TestBudgetRepository(),
        subscriptionRepo: TestSubscriptionRepository = TestSubscriptionRepository(),
        debtRepo: TestDebtRepository = TestDebtRepository(),
        recurringRepo: TestRecurringTransactionRepository = TestRecurringTransactionRepository(),
    ): ReportsViewModel {
        val dateRangeCalculator = CalculateReportDateRangeUseCase()
        val observeFilteredReportUseCase = ObserveFilteredReportUseCase(txRepo, dateRangeCalculator)
        val observeMultiCurrencyReportUseCase = ObserveMultiCurrencyReportUseCase(txRepo, dateRangeCalculator)
        val observeReportAvailabilityUseCase = ObserveReportAvailabilityUseCase(txRepo, dateRangeCalculator)
        val observeReportSyncStatusUseCase = ObserveReportSyncStatusUseCase(syncRepo)
        val observeDetailedReportOverviewUseCase = com.feniqo.mobile.domain.usecase.ObserveDetailedReportOverviewUseCase(
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
            dateRangeCalculator = dateRangeCalculator,
        )
        val observeCashFlowUseCase = com.feniqo.mobile.domain.usecase.ObserveCashFlowUseCase(txRepo)
        val observeCategoryBreakdownUseCase = com.feniqo.mobile.domain.usecase.ObserveCategoryBreakdownUseCase(
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
            dateRangeCalculator = dateRangeCalculator,
        )
        val observeSpendingCalendarUseCase = com.feniqo.mobile.domain.usecase.ObserveSpendingCalendarUseCase(
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
        )
        val observeBudgetPerformanceReportUseCase = com.feniqo.mobile.domain.usecase.ObserveBudgetPerformanceReportUseCase(
            budgetRepository = budgetRepo,
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
        )
        val observeSubscriptionSummaryReportUseCase = com.feniqo.mobile.domain.usecase.ObserveSubscriptionSummaryReportUseCase(
            subscriptionRepository = subscriptionRepo,
            categoryRepository = categoryRepo,
        )
        val observeDebtSummaryReportUseCase = com.feniqo.mobile.domain.usecase.ObserveDebtSummaryReportUseCase(debtRepo)
        val observeForecastReportUseCase = com.feniqo.mobile.domain.usecase.ObserveForecastReportUseCase(
            transactionRepository = txRepo,
            recurringTransactionRepository = recurringRepo,
            subscriptionRepository = subscriptionRepo,
        )
        val observeFinancialInsightsUseCase = com.feniqo.mobile.domain.usecase.ObserveFinancialInsightsUseCase(
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
        )

        return ReportsViewModel(
            observeFilteredReportUseCase = observeFilteredReportUseCase,
            observeMultiCurrencyReportUseCase = observeMultiCurrencyReportUseCase,
            observeReportAvailabilityUseCase = observeReportAvailabilityUseCase,
            observeReportSyncStatusUseCase = observeReportSyncStatusUseCase,
            observeDetailedReportOverviewUseCase = observeDetailedReportOverviewUseCase,
            observeCashFlowUseCase = observeCashFlowUseCase,
            observeCategoryBreakdownUseCase = observeCategoryBreakdownUseCase,
            observeSpendingCalendarUseCase = observeSpendingCalendarUseCase,
            observeBudgetPerformanceReportUseCase = observeBudgetPerformanceReportUseCase,
            observeSubscriptionSummaryReportUseCase = observeSubscriptionSummaryReportUseCase,
            observeDebtSummaryReportUseCase = observeDebtSummaryReportUseCase,
            observeForecastReportUseCase = observeForecastReportUseCase,
            observeFinancialInsightsUseCase = observeFinancialInsightsUseCase,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(workspaceRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(categoryRepo),
            userSettingsRepository = userSettingsRepo,
            currentDateProvider = CurrentDateProvider { fixedToday },
            dateRangeCalculator = dateRangeCalculator,
        )
    }

    @Test
    fun emptyWorkspace_isShownWhenNoReportableTransactionsExist() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue("State should be EmptyWorkspace when no transactions exist", state.contentState is ReportsContentState.EmptyWorkspace)
        job.cancel()
    }

    @Test
    fun emptyFiltered_isShownWhenWorkspaceHasTransactionsButFiltersYieldZero() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        // Add an income transaction in August 2026 (outside current September 2026 filter)
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-old", 10000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 8, 10))
        )

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue("State should be EmptyFiltered when workspace has transactions outside filter", state.contentState is ReportsContentState.EmptyFiltered)
        job.cancel()
    }

    @Test
    fun success_isShownWhenFilteredTransactionsExist() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        // Add transactions in current month
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-1", 50000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 9, 5)),
            createTx("tx-2", 20000, Currency.TRY, TransactionType.EXPENSE, LocalDate(2026, 9, 10)),
            createTx("tx-3", 1000, Currency.USD, TransactionType.EXPENSE, LocalDate(2026, 9, 12)),
        )

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue("State should be Success", state.contentState is ReportsContentState.Success)
        val success = state.contentState as ReportsContentState.Success
        assertEquals(2, success.multiCurrencySummaries.size)
        assertEquals(Currency.TRY, success.multiCurrencySummaries[0].currency)
        assertEquals(Currency.USD, success.multiCurrencySummaries[1].currency)
        job.cancel()
    }

    @Test
    fun categoryBreakdown_usesRoomCategoryNamesAndFallsBackForMissingHistory() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        val categoryRepo = TestCategoryRepository()
        categoryRepo.categoriesFlow.value = listOf(
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId("user-1"),
                workspaceId = EntityId("ws-1"),
                name = "Market",
                type = TransactionType.EXPENSE,
                color = com.feniqo.mobile.domain.model.CategoryColor("#EF4444"),
                icon = com.feniqo.mobile.domain.model.CategoryIcon("groceries"),
                isDefault = false,
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            )
        )
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-market", 20_000, date = LocalDate(2026, 9, 10)),
            createTx("tx-missing", 10_000, date = LocalDate(2026, 9, 11)).copy(categoryId = EntityId("cat-deleted")),
        )

        val vm = createViewModel(txRepo, syncRepo, categoryRepo = categoryRepo)
        val job = launch { vm.uiState.collect {} }

        val breakdown = (vm.uiState.value.contentState as ReportsContentState.Success).categoryBreakdown
        assertEquals("Market", breakdown.first { it.categoryId == EntityId("cat-1") }.name)
        assertEquals("Silinmiş kategori", breakdown.first { it.categoryId == EntityId("cat-deleted") }.name)
        job.cancel()
    }

    @Test
    fun offlineState_preservesSuccessContentAndShowsPendingCount() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-1", 50000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 9, 5)),
        )
        syncRepo.syncOverviewFlow.value = SyncOverview(
            phase = SyncPhase.OFFLINE,
            pendingOperationCount = 4,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue("Content should still be Success even when offline", state.contentState is ReportsContentState.Success)
        assertTrue("Connection state should be Offline", state.connectionState is ReportConnectionState.Offline)
        assertEquals(4, (state.connectionState as ReportConnectionState.Offline).pendingOperationCount)
        job.cancel()
    }

    @Test
    fun offlineState_withZeroPending_setsConnectionToOfflineZero() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-1", 50000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 9, 5)),
        )
        syncRepo.syncOverviewFlow.value = SyncOverview(
            phase = SyncPhase.OFFLINE,
            pendingOperationCount = 0,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue(state.connectionState is ReportConnectionState.Offline)
        assertEquals(0, (state.connectionState as ReportConnectionState.Offline).pendingOperationCount)
        job.cancel()
    }

    @Test
    fun maskAmounts_masksValuesInUiModels() = runTest(UnconfinedTestDispatcher()) {
        val userSettingsRepo = TestUserSettingsRepository()
        userSettingsRepo.settingsFlow.value = UserSettings(maskAmounts = true)

        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-1", 50000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 9, 5)),
            createTx("tx-2", 20000, Currency.TRY, TransactionType.EXPENSE, LocalDate(2026, 9, 10)),
        )

        val vm = createViewModel(txRepo, syncRepo, userSettingsRepo = userSettingsRepo)
        val job = launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        val success = state.contentState as ReportsContentState.Success
        assertTrue("Income display text should be masked", success.incomeFormatted.contains("•") || success.incomeFormatted.contains("*"))
        assertTrue("Expense display text should be masked", success.expenseFormatted.contains("•") || success.expenseFormatted.contains("*"))
        job.cancel()
    }

    @Test
    fun filterActions_removeChipAndClearFilters() = runTest(UnconfinedTestDispatcher()) {
        val txRepo = FakeTransactionRepository()
        val syncRepo = FakeSyncRepository()
        txRepo.transactionsFlow.value = listOf(
            createTx("tx-1", 50000, Currency.TRY, TransactionType.INCOME, LocalDate(2026, 9, 5)),
        )

        val vm = createViewModel(txRepo, syncRepo)
        val job = launch { vm.uiState.collect {} }

        // Change filter to non-default
        vm.applyFilter(
            ReportFilterUiState(
                periodPreset = ReportPeriodPreset.LAST_MONTH,
                typeFilter = ReportTypeFilter.EXPENSE,
                currency = Currency.USD,
            )
        )

        assertEquals(ReportTypeFilter.EXPENSE, vm.uiState.value.filterState.typeFilter)
        assertEquals(Currency.USD, vm.uiState.value.filterState.currency)

        // Remove currency chip
        vm.removeFilterChip(ActiveFilterChipUiModel("currency", ActiveFilterType.CURRENCY, "USD"))
        assertEquals(Currency.TRY, vm.uiState.value.filterState.currency)

        // Clear all filters
        vm.clearFilters()
        assertEquals(ReportPeriodPreset.THIS_MONTH, vm.uiState.value.filterState.periodPreset)
        assertEquals(ReportTypeFilter.ALL, vm.uiState.value.filterState.typeFilter)
        assertEquals(Currency.TRY, vm.uiState.value.filterState.currency)

        job.cancel()
    }
}
