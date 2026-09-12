package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate(2026, 8, 21)
    private val testDateProvider = CurrentDateProvider { fixedToday }

    private class FakeTransactionRepo : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var shouldThrowOnObserve: Throwable? = null
        var lastFilter: TransactionFilter? = null
        var lastDeletedId: EntityId? = null
        var lastDeletedScope: Set<EntityId>? = null
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var deferredDelete: CompletableDeferred<RepositoryResult<Unit>>? = null
        var throwOnDelete: CancellationException? = null
        var softDeleteCallCount = 0
        var observeSubscriptionCount = 0

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
            observeSubscriptionCount++
            lastFilter = filter
            val exception = shouldThrowOnObserve
            return if (exception != null) {
                flow { throw exception }
            } else {
                transactionsFlow
            }
        }

        override fun observeTransaction(id: EntityId): Flow<Transaction?> =
            MutableStateFlow(transactionsFlow.value.firstOrNull { it.id == id })

        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            MutableStateFlow(transactionsFlow.value.filter { it.installment?.groupId == groupId })

        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> =
            RepositoryResult.Success(transaction.id)

        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(transactions.first().installment!!.groupId)

        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            softDeleteCallCount++
            lastDeletedId = id
            throwOnDelete?.let { throw it }
            deferredDelete?.let { return it.await() }
            return deleteResult
        }

        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
            softDeleteCallCount++
            lastDeletedScope = ids
            throwOnDelete?.let { throw it }
            deferredDelete?.let { return it.await() }
            return deleteResult
        }
    }

    private class FakeCategoryRepo : CategoryRepository {
        val activeCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        val historyCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        var lastActiveType: TransactionType? = null
        var lastActiveWorkspaceId: EntityId? = null
        var lastHistoryWorkspaceId: EntityId? = null
        var activeObserveCallCount = 0
        var historyObserveCallCount = 0
        var observeDeferred: CompletableDeferred<Unit>? = null

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> {
            activeObserveCallCount++
            lastActiveType = type
            lastActiveWorkspaceId = workspaceId
            val deferred = observeDeferred
            return if (deferred != null) {
                flow {
                    deferred.await()
                    activeCategoriesFlow.collect { emit(it) }
                }
            } else {
                activeCategoriesFlow
            }
        }

        override fun observeCategory(id: EntityId): Flow<Category?> =
            MutableStateFlow(activeCategoriesFlow.value.firstOrNull { it.id == id })

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> {
            historyObserveCallCount++
            lastHistoryWorkspaceId = workspaceId
            return historyCategoriesFlow
        }

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeAuthRepo(private val userId: EntityId = EntityId("user-1")) : com.feniqo.mobile.domain.repository.AuthRepository {
        override fun observeSession(): Flow<com.feniqo.mobile.domain.repository.AuthSession?> =
            MutableStateFlow(com.feniqo.mobile.domain.repository.AuthSession(userId, "u@f.com", Instant.parse("2026-08-01T00:00:00Z")))
        override fun observeCurrentProfile(): Flow<com.feniqo.mobile.domain.model.UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(userId)
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private fun createViewModel(
        trxRepo: FakeTransactionRepo = FakeTransactionRepo(),
        catRepo: FakeCategoryRepo = FakeCategoryRepo(),
        authRepo: FakeAuthRepo = FakeAuthRepo(),
        fakeWorkspaceRepo: com.feniqo.mobile.presentation.common.FakeWorkspaceRepository = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository(),
        savedStateHandle: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    ): Triple<TransactionsViewModel, FakeTransactionRepo, FakeCategoryRepo> {
        val observeTrx = ObserveTransactionsUseCase(trxRepo)
        val observeCatHistory = ObserveCategoriesForHistoryLookupUseCase(catRepo)
        val observeCatActive = ObserveCategoriesUseCase(catRepo)
        val deleteTrx = DeleteTransactionUseCase(authRepo, trxRepo)
        val vm = TransactionsViewModel(
            observeTransactionsUseCase = observeTrx,
            observeCategoriesForHistoryLookupUseCase = observeCatHistory,
            observeCategoriesUseCase = observeCatActive,
            deleteTransactionUseCase = deleteTrx,
            currentDateProvider = testDateProvider,
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo),
            savedStateHandle = savedStateHandle,
        )
        return Triple(vm, trxRepo, catRepo)
    }

    @Test
    fun initialState_hasLoadingTrue_andReceivingEmissionSetsLoadingFalse() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        assertTrue(viewModel.uiState.value.isLoading)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.groupedItems.isEmpty())
        assertTrue(viewModel.uiState.value.availableCategories.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun transactionAndCategoryEmission_combinesCorrectlyIntoGroupedItems() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val cat = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#4CAF50"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(cat)
        catRepo.historyCategoriesFlow.value = listOf(cat)

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(15000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Market Alışverişi",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.groupedItems.size)
        val group = state.groupedItems[0]
        assertEquals(fixedToday, group.date)
        assertEquals("Bugün", group.formattedDate)
        assertEquals(1, group.items.size)
        val item = group.items[0]
        assertEquals("Market", item.categoryName)
        assertEquals("-150,00 ₺", item.formattedAmount)

        collectJob.cancel()
    }

    @Test
    fun observationError_showsGenericErrorAndRecoversOnFilterChange() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        trxRepo.shouldThrowOnObserve = RuntimeException("Database observation failed")

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertTrue(errorState.groupedItems.isEmpty())
        assertTrue(errorState.availableCategories.isEmpty())
        assertEquals(FinanceUiMessage.GENERIC_ERROR, errorState.observationError)
        assertNull(errorState.userMessage)

        trxRepo.shouldThrowOnObserve = null
        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(5000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Kurtarılan İşlem",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        viewModel.onSearchQueryChanged("Kurtarılan")
        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertFalse(recoveredState.isLoading)
        assertEquals(1, recoveredState.groupedItems.size)
        assertEquals(EntityId("t1"), recoveredState.groupedItems[0].items[0].id)
        assertNull(recoveredState.observationError)
        assertNull(recoveredState.userMessage)

        collectJob.cancel()
    }

    @Test
    fun observationError_criticalThrowable_isNotSwallowedAndRethrown() {
        val expectedError = AssertionError("Critical system assertion error")
        var thrown: Throwable? = null
        try {
            runTest {
                val (viewModel, trxRepo, _) = createViewModel()
                trxRepo.shouldThrowOnObserve = expectedError

                val collectJob = launch(UnconfinedTestDispatcher()) {
                    viewModel.uiState.collect()
                }
                advanceUntilIdle()
                collectJob.cancel()
            }
        } catch (t: Throwable) {
            thrown = t
        }

        assertNotNull(thrown)
        assertTrue(thrown === expectedError || thrown?.cause === expectedError || thrown?.suppressed?.contains(expectedError) == true)
    }

    @Test
    fun searchAndFilterChanges_areForwardedToDomainFilter() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()
        catRepo.activeCategoriesFlow.value = listOf(
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId("user-1"),
                workspaceId = EntityId("ws-1"),
                name = "Market",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#4CAF50"),
                icon = null,
                isDefault = false,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.onWorkspaceChanged(EntityId("ws-1"))
        viewModel.onTypeFilterChanged(TransactionType.EXPENSE)
        viewModel.onCategoryFilterChanged(EntityId("cat-1"))
        viewModel.onPaymentMethodFilterChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onPeriodPresetChanged(TransactionPeriodPreset.THIS_MONTH)
        viewModel.onSearchQueryChanged("   market  ")

        advanceUntilIdle()

        val filter = trxRepo.lastFilter
        assertNotNull(filter)
        assertEquals("market", filter?.query)
        assertEquals(TransactionType.EXPENSE, filter?.type)
        assertEquals(EntityId("cat-1"), filter?.categoryId)
        assertEquals(PaymentMethod.CREDIT_CARD, filter?.paymentMethod)
        val expectedPeriod = ReportPeriod(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31))
        assertEquals(expectedPeriod, filter?.period)
        assertEquals(EntityId("ws-1"), filter?.workspaceId)
        assertEquals(EntityId("ws-1"), catRepo.lastHistoryWorkspaceId)
        assertEquals(EntityId("ws-1"), catRepo.lastActiveWorkspaceId)

        // Clear filters
        viewModel.clearFilters()
        advanceUntilIdle()

        val clearedFilter = trxRepo.lastFilter
        assertNull(clearedFilter?.query)
        assertNull(clearedFilter?.type)
        assertNull(clearedFilter?.categoryId)
        assertEquals(EntityId("ws-1"), clearedFilter?.workspaceId)

        collectJob.cancel()
    }

    @Test
    fun activeCategoryOptions_convertsUiModels_withDeterministicSort_andExcludesHistoryOnly() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val catB = Category(
            id = EntityId("cat-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Banka",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#2196F3"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val catA = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Akaryakıt",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#FF9800"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val deletedCat = Category(
            id = EntityId("cat-del"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kategori",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#9E9E9E"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

        // active categories only contains A and B (in arbitrary order)
        catRepo.activeCategoriesFlow.value = listOf(catB, catA)
        // history categories contains all including deleted
        catRepo.historyCategoriesFlow.value = listOf(catB, catA, deletedCat)

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(20000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-del"),
            description = "Eski Harcama",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        // Verify deterministic sort by name: "Akaryakıt" then "Banka"
        assertEquals(2, state.availableCategories.size)
        assertEquals("Akaryakıt", state.availableCategories[0].name)
        assertEquals("#FF9800", state.availableCategories[0].colorHex)
        assertEquals("Banka", state.availableCategories[1].name)
        assertEquals("#2196F3", state.availableCategories[1].colorHex)

        // Verify history lookup resolved deleted category in item, but deleted category is not in availableCategories
        assertEquals("Eski Kategori", state.groupedItems[0].items[0].categoryName)
        assertTrue(state.availableCategories.none { it.id == EntityId("cat-del") })

        collectJob.cancel()
    }

    @Test
    fun typeFilterConsistency_clearsCategoryWhenTypeChanges_preservesWhenSame() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()
        catRepo.activeCategoriesFlow.value = listOf(
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                name = "Market",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#4CAF50"),
                icon = null,
                isDefault = false,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        // Set type and category
        viewModel.onTypeFilterChanged(TransactionType.EXPENSE)
        viewModel.onCategoryFilterChanged(EntityId("cat-1"))
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.filter.type)
        assertEquals(EntityId("cat-1"), viewModel.uiState.value.filter.categoryId)
        assertEquals(TransactionType.EXPENSE, catRepo.lastActiveType)

        // Set SAME type -> category should be preserved
        viewModel.onTypeFilterChanged(TransactionType.EXPENSE)
        advanceUntilIdle()
        assertEquals(EntityId("cat-1"), viewModel.uiState.value.filter.categoryId)

        // Set DIFFERENT type -> category should be atomically cleared
        viewModel.onTypeFilterChanged(TransactionType.INCOME)
        advanceUntilIdle()
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.filter.type)
        assertNull(viewModel.uiState.value.filter.categoryId)
        assertEquals(TransactionType.INCOME, catRepo.lastActiveType)

        collectJob.cancel()
    }

    @Test
    fun workspaceFilterConsistency_clearsCategoryWhenWorkspaceChanges_preservesWhenSame() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()
        catRepo.activeCategoriesFlow.value = listOf(
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId("user-1"),
                workspaceId = EntityId("ws-1"),
                name = "Market",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#4CAF50"),
                icon = null,
                isDefault = false,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.onWorkspaceChanged(EntityId("ws-1"))
        viewModel.onCategoryFilterChanged(EntityId("cat-1"))
        advanceUntilIdle()

        assertEquals(EntityId("ws-1"), viewModel.uiState.value.filter.workspaceId)
        assertEquals(EntityId("cat-1"), viewModel.uiState.value.filter.categoryId)

        // Same workspace -> preserve
        viewModel.onWorkspaceChanged(EntityId("ws-1"))
        advanceUntilIdle()
        assertEquals(EntityId("cat-1"), viewModel.uiState.value.filter.categoryId)

        // Changed workspace -> category cleared
        viewModel.onWorkspaceChanged(EntityId("ws-2"))
        advanceUntilIdle()
        assertEquals(EntityId("ws-2"), viewModel.uiState.value.filter.workspaceId)
        assertNull(viewModel.uiState.value.filter.categoryId)
        assertEquals(EntityId("ws-2"), catRepo.lastActiveWorkspaceId)
        assertEquals(EntityId("ws-2"), catRepo.lastHistoryWorkspaceId)

        collectJob.cancel()
    }

    @Test
    fun deletedActiveCategory_isSafelyClearedFromFilterWithoutInfiniteLoop() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val cat1 = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Yemek",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#E91E63"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(cat1)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.onCategoryFilterChanged(EntityId("cat-1"))
        advanceUntilIdle()
        assertEquals(EntityId("cat-1"), viewModel.uiState.value.filter.categoryId)

        // Category deleted from active list
        catRepo.activeCategoriesFlow.value = emptyList()
        advanceUntilIdle()

        // Category filter is safely set to null
        assertNull(viewModel.uiState.value.filter.categoryId)
        assertTrue(viewModel.uiState.value.availableCategories.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun retryObservation_restartsObservationFlow_withoutModifyingFilters() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        trxRepo.shouldThrowOnObserve = RuntimeException("Temporary network error")

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.observationError)
        assertNull(viewModel.uiState.value.userMessage)

        // Fix backend and retry without touching filters
        trxRepo.shouldThrowOnObserve = null
        val cat = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#4CAF50"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(5000000L, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-1"),
            description = "Ağustos Maaşı",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(cat)
        catRepo.historyCategoriesFlow.value = listOf(cat)
        trxRepo.transactionsFlow.value = listOf(trx)

        viewModel.retryObservation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(1, state.groupedItems.size)
        assertEquals(1, state.availableCategories.size)
        assertEquals("Maaş", state.availableCategories[0].name)
        assertNull(state.userMessage)

        collectJob.cancel()
    }

    @Test
    fun filterSheetVisibility_openAndDismissEvents_workDeterministically() = runTest {
        val (viewModel, _, _) = createViewModel()

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertFalse(viewModel.uiState.value.isFilterExpanded)

        viewModel.openFilters()
        assertTrue(viewModel.uiState.value.isFilterExpanded)

        // Multiple open calls remain true
        viewModel.openFilters()
        assertTrue(viewModel.uiState.value.isFilterExpanded)

        viewModel.dismissFilters()
        assertFalse(viewModel.uiState.value.isFilterExpanded)

        viewModel.dismissFilters()
        assertFalse(viewModel.uiState.value.isFilterExpanded)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_singleTransaction_opensSingleDialog_andConfirmsCorrectly() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]

        viewModel.onDeleteClicked(displayModel)
        val dialog = viewModel.uiState.value.deleteDialog
        assertTrue(dialog is TransactionDeleteDialogState.Single)

        viewModel.confirmSingleDelete()
        advanceUntilIdle()

        assertEquals(EntityId("t1"), trxRepo.lastDeletedId)
        assertNull(viewModel.uiState.value.deleteDialog)
        assertEquals(FinanceUiMessage.TRANSACTION_DELETED, viewModel.uiState.value.userMessage)

        viewModel.consumeMessage()
        assertNull(viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_installmentTransaction_opensInstallmentDialog_andConfirmsWithScope() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = InstallmentInfo(1, 3, EntityId("grp-1")),
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]

        viewModel.onDeleteClicked(displayModel)
        val dialog = viewModel.uiState.value.deleteDialog
        assertTrue(dialog is TransactionDeleteDialogState.Installment)

        viewModel.confirmInstallmentDelete(InstallmentDeleteScope.THIS_AND_FOLLOWING)
        advanceUntilIdle()

        assertEquals(1, trxRepo.softDeleteCallCount)
        assertNull(viewModel.uiState.value.deleteDialog)
        assertEquals(FinanceUiMessage.TRANSACTION_DELETED, viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_installmentTransaction_withOnlyThisScope_deletesAndEmitsSuccessMessage() = runTest {
        val (viewModel, trxRepo, _) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = InstallmentInfo(1, 3, EntityId("grp-1")),
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]
        viewModel.onDeleteClicked(displayModel)
        assertTrue(viewModel.uiState.value.deleteDialog is TransactionDeleteDialogState.Installment)

        viewModel.confirmInstallmentDelete(InstallmentDeleteScope.ONLY_THIS)
        advanceUntilIdle()

        assertEquals(1, trxRepo.softDeleteCallCount)
        assertNull(viewModel.uiState.value.deleteDialog)
        assertEquals(FinanceUiMessage.TRANSACTION_DELETED, viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_installmentTransaction_withAllGroupScope_deletesAndEmitsSuccessMessage() = runTest {
        val (viewModel, trxRepo, _) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = InstallmentInfo(1, 3, EntityId("grp-1")),
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]
        viewModel.onDeleteClicked(displayModel)
        assertTrue(viewModel.uiState.value.deleteDialog is TransactionDeleteDialogState.Installment)

        viewModel.confirmInstallmentDelete(InstallmentDeleteScope.ALL_GROUP)
        advanceUntilIdle()

        assertEquals(1, trxRepo.softDeleteCallCount)
        assertNull(viewModel.uiState.value.deleteDialog)
        assertEquals(FinanceUiMessage.TRANSACTION_DELETED, viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_handlesCancellationWithoutEmittingMessages_andAllowsSubsequentDelete() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)
        trxRepo.throwOnDelete = CancellationException("Delete cancelled")

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]
        viewModel.onDeleteClicked(displayModel)

        viewModel.confirmSingleDelete()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        assertNull(viewModel.uiState.value.userMessage)

        trxRepo.throwOnDelete = null
        viewModel.onDeleteClicked(displayModel)
        viewModel.confirmSingleDelete()
        advanceUntilIdle()

        assertEquals(2, trxRepo.softDeleteCallCount)
        assertEquals(FinanceUiMessage.TRANSACTION_DELETED, viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun deleteFlow_handlesFailureAndDoubleSubmit() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val deferred = CompletableDeferred<RepositoryResult<Unit>>()
        trxRepo.deferredDelete = deferred

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]
        viewModel.onDeleteClicked(displayModel)

        viewModel.confirmSingleDelete()
        assertTrue(viewModel.uiState.value.isDeleteInProgress)

        viewModel.confirmSingleDelete()
        assertEquals(1, trxRepo.softDeleteCallCount)

        deferred.complete(RepositoryResult.Failure(AppError.Validation("transaction_not_found")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        assertNull(viewModel.uiState.value.deleteDialog)
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, viewModel.uiState.value.userMessage)

        collectJob.cancel()
    }

    @Test
    fun dismissDeleteDialog_clearsDialogState() = runTest {
        val (viewModel, trxRepo, catRepo) = createViewModel()

        val trx = Transaction(
            id = EntityId("t1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(trx)

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val displayModel = viewModel.uiState.value.groupedItems[0].items[0]
        viewModel.onDeleteClicked(displayModel)
        assertNotNull(viewModel.uiState.value.deleteDialog)

        viewModel.dismissDeleteDialog()
        assertNull(viewModel.uiState.value.deleteDialog)

        collectJob.cancel()
    }

    @Test
    fun init_withSavedStateHandle_seedsInitialFilter() = runTest {
        val handle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "categoryId" to "cat-food",
                "startDate" to "2026-09-01",
                "endDate" to "2026-09-30",
            )
        )
        val catRepo = FakeCategoryRepo()
        val catFood = Category(
            id = EntityId("cat-food"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Yemek",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(catFood)

        val (vm, _, _) = createViewModel(catRepo = catRepo, savedStateHandle = handle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        assertEquals(EntityId("cat-food"), vm.uiState.value.filter.categoryId)
        assertNotNull(vm.uiState.value.filter.customPeriod)
        assertEquals(LocalDate(2026, 9, 1), vm.uiState.value.filter.customPeriod?.startDate)
        assertEquals(LocalDate(2026, 9, 30), vm.uiState.value.filter.customPeriod?.endDate)

        collector.cancel()
    }

    @Test
    fun summary_calculatesNormalTryIncomeAndSpending() = runTest {
        val (vm, trxRepo, catRepo) = createViewModel()
        val expenseTx = Transaction(
            id = EntityId("tx-exp"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(150_00L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        val incomeTx = Transaction(
            id = EntityId("tx-inc"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(350_00L, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-2"),
            description = null,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(expenseTx, incomeTx)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        val summary = vm.uiState.value.summary
        assertEquals("150,00 ₺", summary.totalSpendingFormatted)
        assertEquals("350,00 ₺", summary.totalIncomeFormatted)
        assertEquals(2, summary.transactionCount)
        assertNull(vm.uiState.value.observationError)

        collector.cancel()
    }

    @Test
    fun summary_handlesEmptyTransactionList() = runTest {
        val (vm, trxRepo, _) = createViewModel()
        trxRepo.transactionsFlow.value = emptyList()

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        val summary = vm.uiState.value.summary
        assertEquals("0,00 ₺", summary.totalSpendingFormatted)
        assertEquals("0,00 ₺", summary.totalIncomeFormatted)
        assertEquals(0, summary.transactionCount)
        assertNull(vm.uiState.value.observationError)

        collector.cancel()
    }

    @Test
    fun summary_failsClosed_onMixedCurrency() = runTest {
        val (vm, trxRepo, _) = createViewModel()
        val tryTx = Transaction(
            id = EntityId("tx-try"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(100_00L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        val usdTx = Transaction(
            id = EntityId("tx-usd"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(50_00L, Currency.USD),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(tryTx, usdTx)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        // Fail-closed hata üretilmeli, kısmi özet veya liste gösterilmemeli
        assertEquals(FinanceUiMessage.GENERIC_ERROR, vm.uiState.value.observationError)
        assertTrue(vm.uiState.value.groupedItems.isEmpty())

        collector.cancel()
    }

    @Test
    fun summary_failsClosed_onLongOverflow() = runTest {
        val (vm, trxRepo, _) = createViewModel()
        val hugeTx1 = Transaction(
            id = EntityId("tx-huge-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        val hugeTx2 = Transaction(
            id = EntityId("tx-huge-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(hugeTx1, hugeTx2)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        // safeAdd veya Money toplam sınırı nedeniyle fail-closed hata vermeli
        assertEquals(FinanceUiMessage.GENERIC_ERROR, vm.uiState.value.observationError)
        assertTrue(vm.uiState.value.groupedItems.isEmpty())

        collector.cancel()
    }

    @Test
    fun summary_handlesOnlyIncome_andOnlyExpense() = runTest {
        val (vm, trxRepo, _) = createViewModel()
        val onlyIncome = Transaction(
            id = EntityId("tx-inc"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(500_00L, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(onlyIncome)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        assertEquals("0,00 ₺", vm.uiState.value.summary.totalSpendingFormatted)
        assertEquals("500,00 ₺", vm.uiState.value.summary.totalIncomeFormatted)

        collector.cancel()
    }

    @Test
    fun routeCategory_categoriesLoading_transactionsEmitTwiceThenCategoryArrives_filterPreserved() = runTest {
        val catId = "cat-valid"
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("categoryId" to catId))
        val catRepo = FakeCategoryRepo()
        val trxRepo = FakeTransactionRepo()

        // Kategoriler loading durumunda tutulur
        val categoryGate = CompletableDeferred<Unit>()
        catRepo.observeDeferred = categoryGate

        val (vm, _, _) = createViewModel(trxRepo = trxRepo, catRepo = catRepo, savedStateHandle = handle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        // Transactions 1. kez yayın yapar
        val tx1 = Transaction(
            id = EntityId("tx-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(100_00L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId(catId),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(tx1)
        advanceUntilIdle()

        // Transactions 2. kez yayın yapar (kategoriler hâlâ loading)
        val tx2 = Transaction(
            id = EntityId("tx-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(200_00L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId(catId),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(tx1, tx2)
        advanceUntilIdle()

        // Kategoriler henüz yüklenirken route filtresi erken temizlenmemeli
        assertEquals(EntityId(catId), vm.uiState.value.filter.categoryId)

        // Sonra geçerli kategori gelir
        val validCat = Category(
            id = EntityId(catId),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(validCat)
        categoryGate.complete(Unit)
        advanceUntilIdle()

        // Geçerli kategori yüklendiğinde filtre korunur
        assertEquals(EntityId(catId), vm.uiState.value.filter.categoryId)

        collector.cancel()
    }

    @Test
    fun routeCategory_initialReliableCategorySnapshotEmpty_invalidRouteFilterCleared() = runTest {
        val catId = "cat-invalid"
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("categoryId" to catId))
        val catRepo = FakeCategoryRepo()
        // İlk gerçek Room snapshot'ı boş gelir
        catRepo.activeCategoriesFlow.value = emptyList()
        catRepo.historyCategoriesFlow.value = emptyList()

        val (vm, _, _) = createViewModel(catRepo = catRepo, savedStateHandle = handle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        // İlk güvenilir kategori snapshot'ı boş geldiğinde geçersiz route filtresi temizlenir
        assertNull(vm.uiState.value.filter.categoryId)

        collector.cancel()
    }

    @Test
    fun routeCategory_historicalCategoryFoundInSnapshot_filterPreserved() = runTest {
        val catId = "cat-historical"
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("categoryId" to catId))
        val catRepo = FakeCategoryRepo()
        val historicalCat = Category(
            id = EntityId(catId),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kategori",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = emptyList()
        catRepo.historyCategoriesFlow.value = listOf(historicalCat)

        val (vm, _, _) = createViewModel(catRepo = catRepo, savedStateHandle = handle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        // Tarihsel kategori snapshot'ta bulunduğu için filtre korunur
        assertEquals(EntityId(catId), vm.uiState.value.filter.categoryId)

        collector.cancel()
    }

    @Test
    fun routeCategory_workspaceChanged_filterCleared() = runTest {
        val catId = "cat-1"
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("categoryId" to catId))
        val catRepo = FakeCategoryRepo()
        val cat1 = Category(
            id = EntityId(catId),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(cat1)

        val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository(
            initialActiveWorkspace = com.feniqo.mobile.domain.model.Workspace(
                id = EntityId("ws-1"),
                name = "Workspace 1",
                ownerId = EntityId("user-1"),
                currency = Currency.TRY,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )

        val (vm, _, _) = createViewModel(
            catRepo = catRepo,
            fakeWorkspaceRepo = fakeWorkspaceRepo,
            savedStateHandle = handle,
        )
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        // Başlangıçta kategori korunur
        assertEquals(EntityId(catId), vm.uiState.value.filter.categoryId)

        // Workspace değiştiğinde
        fakeWorkspaceRepo.activeWorkspaceFlow.value = com.feniqo.mobile.domain.model.Workspace(
            id = EntityId("ws-2"),
            name = "Workspace 2",
            ownerId = EntityId("user-1"),
            currency = Currency.TRY,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        advanceUntilIdle()

        // Workspace değişimi filtreyi temizlemelidir
        assertNull(vm.uiState.value.filter.categoryId)

        collector.cancel()
    }

    @Test
    fun clearFilters_clearsCategoryAndCustomPeriodTogether() = runTest {
        val handle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "categoryId" to "cat-1",
                "startDate" to "2026-08-01",
                "endDate" to "2026-08-31",
            ),
        )
        val catRepo = FakeCategoryRepo()
        val cat1 = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        catRepo.activeCategoriesFlow.value = listOf(cat1)

        val (vm, _, _) = createViewModel(catRepo = catRepo, savedStateHandle = handle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(EntityId("cat-1"), vm.uiState.value.filter.categoryId)
        assertNotNull(vm.uiState.value.filter.customPeriod)

        vm.clearFilters()
        advanceUntilIdle()

        assertNull(vm.uiState.value.filter.categoryId)
        assertNull(vm.uiState.value.filter.customPeriod)

        collector.cancel()
    }

    @Test
    fun onSortOrderChanged_updatesFilterSortOrder_andSortsItems() = runTest {
        val trxSmall = Transaction(
            id = EntityId("t-small"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(1000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Küçük",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        val trxLarge = Transaction(
            id = EntityId("t-large"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(50000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Büyük",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T11:00:00Z"),
        )
        val trxRepo = FakeTransactionRepo()
        trxRepo.transactionsFlow.value = listOf(trxSmall, trxLarge)

        val (vm, _, _) = createViewModel(trxRepo = trxRepo)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(TransactionSortOrder.NEWEST, vm.uiState.value.filter.sortOrder)

        // Tutar: Azalan sıralamasına geç
        vm.onSortOrderChanged(TransactionSortOrder.AMOUNT_DESC)
        advanceUntilIdle()

        assertEquals(TransactionSortOrder.AMOUNT_DESC, vm.uiState.value.filter.sortOrder)
        val firstItem = vm.uiState.value.groupedItems.first().items.first()
        assertEquals(EntityId("t-large"), firstItem.id)

        // Summary kontrolü: Net ve Daily Bars
        val summary = vm.uiState.value.summary
        assertEquals("-510,00 ₺", summary.netFormatted)
        assertFalse(summary.isNetPositive)
        assertEquals(1, summary.dailyBars.size)
        assertEquals(51000L, summary.dailyBars.first().expenseMinor)

        collector.cancel()
    }

    @Test
    fun routeCustomPeriod_isInitiallyPreservedInFilterAndDomainQuery() = runTest {
        val savedStateHandle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "startDate" to "2026-08-01",
                "endDate" to "2026-08-15",
            )
        )
        val (vm, trxRepo, _) = createViewModel(savedStateHandle = savedStateHandle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val expectedPeriod = ReportPeriod(LocalDate(2026, 8, 1), LocalDate(2026, 8, 15))
        assertEquals(expectedPeriod, vm.uiState.value.filter.customPeriod)
        assertEquals(expectedPeriod, trxRepo.lastFilter?.period)

        collector.cancel()
    }

    @Test
    fun onPeriodPresetChanged_whenSelectingThisMonth_clearsCustomPeriodAndUsesPresetInQuery() = runTest {
        val savedStateHandle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "startDate" to "2026-08-01",
                "endDate" to "2026-08-15",
            )
        )
        val (vm, trxRepo, _) = createViewModel(savedStateHandle = savedStateHandle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(ReportPeriod(LocalDate(2026, 8, 1), LocalDate(2026, 8, 15)), vm.uiState.value.filter.customPeriod)

        // Kullanıcı Bu Ay seçer
        vm.onPeriodPresetChanged(TransactionPeriodPreset.THIS_MONTH)
        advanceUntilIdle()

        assertNull(vm.uiState.value.filter.customPeriod)
        assertEquals(TransactionPeriodPreset.THIS_MONTH, vm.uiState.value.filter.periodPreset)

        val expectedThisMonthPeriod = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_MONTH, fixedToday)
        assertEquals(expectedThisMonthPeriod, trxRepo.lastFilter?.period)

        collector.cancel()
    }

    @Test
    fun clearFilters_clearsBothCustomPeriodAndPeriodPreset() = runTest {
        val savedStateHandle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "startDate" to "2026-08-01",
                "endDate" to "2026-08-15",
            )
        )
        val (vm, trxRepo, _) = createViewModel(savedStateHandle = savedStateHandle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        vm.clearFilters()
        advanceUntilIdle()

        assertNull(vm.uiState.value.filter.customPeriod)
        assertNull(vm.uiState.value.filter.periodPreset)
        assertNull(trxRepo.lastFilter?.period)

        collector.cancel()
    }

    @Test
    fun periodChipLabel_whenCustomPeriodIsActive_isNotMisleadingAllTime() = runTest {
        val savedStateHandle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "startDate" to "2026-08-01",
                "endDate" to "2026-08-15",
            )
        )
        val (vm, _, _) = createViewModel(savedStateHandle = savedStateHandle)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val label = vm.uiState.value.periodChipLabel
        assertTrue(label != "Tüm zamanlar")
        assertTrue(label == "1 – 15 Ağu" || label == "Özel Dönem")

        // Preset seçildiğinde etiketin o presete dönüştüğünü doğrula
        vm.onPeriodPresetChanged(TransactionPeriodPreset.THIS_MONTH)
        advanceUntilIdle()
        assertEquals("Bu ay", vm.uiState.value.periodChipLabel)

        // Filtreler temizlendiğinde "Tüm zamanlar" olduğunu doğrula
        vm.clearFilters()
        advanceUntilIdle()
        assertEquals("Tüm zamanlar", vm.uiState.value.periodChipLabel)

        collector.cancel()
    }
}
