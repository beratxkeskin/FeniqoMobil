package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.AddInstallmentGroupUseCase
import com.feniqo.mobile.domain.usecase.AddTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.domain.usecase.UpdateTransactionUseCase
import com.feniqo.mobile.navigation.TransactionFormRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.CurrentInstantProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate(2026, 8, 24)
    private val fixedInstant = Instant.parse("2026-08-24T12:00:00Z")

    private val fakeDateProvider = CurrentDateProvider { fixedToday }

    private class CountingInstantProvider(val fixed: Instant) : CurrentInstantProvider {
        var callCount = 0
        override fun now(): Instant {
            callCount++
            return fixed
        }
    }

    private class FakeAuthRepository : AuthRepository {
        val sessionFlow = MutableStateFlow<AuthSession?>(
            AuthSession(
                userId = EntityId("user-1"),
                email = "test@feniqo.com",
                expiresAt = Instant.parse("2026-12-31T00:00:00Z"),
            ),
        )
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var lastCreatedTransaction: Transaction? = null
        var lastCreatedInstallments: List<Transaction>? = null
        var lastUpdatedTransaction: Transaction? = null
        var shouldFailWith: AppError? = null

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactionsFlow

        override fun observeTransaction(id: EntityId): Flow<Transaction?> =
            transactionsFlow.map { list -> list.find { it.id == id } }

        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactionsFlow.map { list -> list.filter { it.installment?.groupId == groupId } }

        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            shouldFailWith?.let { return RepositoryResult.Failure(it) }
            lastCreatedTransaction = transaction
            transactionsFlow.value = transactionsFlow.value + transaction
            return RepositoryResult.Success(transaction.id)
        }

        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> {
            shouldFailWith?.let { return RepositoryResult.Failure(it) }
            lastCreatedInstallments = transactions
            transactionsFlow.value = transactionsFlow.value + transactions
            return RepositoryResult.Success(transactions.first().installment!!.groupId)
        }

        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
            shouldFailWith?.let { return RepositoryResult.Failure(it) }
            lastUpdatedTransaction = transaction
            transactionsFlow.value = transactionsFlow.value.map { if (it.id == transaction.id) transaction else it }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        val activeCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        val historyCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        var shouldThrowOnObserve: Throwable? = null
        var observeCallCount = 0

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> {
            observeCallCount++
            val exception = shouldThrowOnObserve
            return if (exception != null) {
                flow { throw exception }
            } else {
                activeCategoriesFlow.map { list ->
                    list.filter { cat ->
                        (type == null || cat.type == type) && (workspaceId == null || cat.workspaceId == workspaceId)
                    }
                }
            }
        }

        override fun observeCategory(id: EntityId): Flow<Category?> =
            activeCategoriesFlow.map { list ->
                (list + historyCategoriesFlow.value).find { it.id == id }
            }

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            historyCategoriesFlow.map { list ->
                list.filter { cat -> workspaceId == null || cat.workspaceId == workspaceId }
            }

        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class SequentialEntityIdGenerator : EntityIdGenerator {
        private var count = 0
        override fun nextId(): EntityId = EntityId("gen-${++count}")
    }

    private lateinit var authRepo: FakeAuthRepository
    private lateinit var trxRepo: FakeTransactionRepository
    private lateinit var catRepo: FakeCategoryRepository
    private lateinit var idGenerator: SequentialEntityIdGenerator
    private lateinit var countingInstantProvider: CountingInstantProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository()
        trxRepo = FakeTransactionRepository()
        catRepo = FakeCategoryRepository()
        idGenerator = SequentialEntityIdGenerator()
        countingInstantProvider = CountingInstantProvider(fixedInstant)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        route: TransactionFormRoute,
        instantProvider: CurrentInstantProvider = countingInstantProvider,
    ): TransactionFormViewModel {
        val savedStateHandle = SavedStateHandle(
            if (route.transactionId != null) mapOf("transactionId" to route.transactionId) else emptyMap(),
        )

        return TransactionFormViewModel(
            addTransactionUseCase = AddTransactionUseCase(authRepo, catRepo, trxRepo),
            addInstallmentGroupUseCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGenerator),
            updateTransactionUseCase = UpdateTransactionUseCase(authRepo, catRepo, trxRepo),
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            currentDateProvider = fakeDateProvider,
            currentInstantProvider = instantProvider,
            entityIdGenerator = idGenerator,
            savedStateHandle = savedStateHandle,
        )
    }

    private fun createViewModelWithHandle(savedStateHandle: SavedStateHandle): TransactionFormViewModel {
        return TransactionFormViewModel(
            addTransactionUseCase = AddTransactionUseCase(authRepo, catRepo, trxRepo),
            addInstallmentGroupUseCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGenerator),
            updateTransactionUseCase = UpdateTransactionUseCase(authRepo, catRepo, trxRepo),
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            currentDateProvider = fakeDateProvider,
            currentInstantProvider = countingInstantProvider,
            entityIdGenerator = idGenerator,
            savedStateHandle = savedStateHandle,
        )
    }

    private val sampleActiveExpenseCategory = Category(
        id = EntityId("cat-market"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#4CAF50"),
        icon = CategoryIcon("shopping_cart"),
        isDefault = false,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private val sampleActiveIncomeCategory = Category(
        id = EntityId("cat-salary"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Maaş",
        type = TransactionType.INCOME,
        color = CategoryColor("#2196F3"),
        icon = CategoryIcon("payments"),
        isDefault = false,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    @Test
    fun addMode_withNullRoute_initializesWithCurrentDate_andEmptyFields() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEditMode)
        assertFalse(state.isLoadingTransaction)
        assertNull(state.loadError)
        assertEquals(fixedToday, state.transactionDate)
        assertEquals(Currency.TRY, state.currency)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("", state.amountText)
        assertEquals(PaymentMethod.CASH, state.paymentMethod)
        assertNull(state.selectedCategoryId)
        assertEquals(1, state.availableCategories.size)
        assertEquals("Market", state.availableCategories[0].name)
        assertTrue(state.availableCategories[0].isSelectable)
        assertFalse(state.availableCategories[0].isHistorical)

        collectJob.cancel()
    }

    @Test
    fun editMode_withValidId_loadsExistingTransactionData_andSetsEditMode() = runTest {
        val existingTrx = Transaction(
            id = EntityId("trx-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(15050L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-market"),
            description = "Haftalık Alışveriş",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 8, 20),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-20T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory)

        val viewModel = createViewModel(TransactionFormRoute("trx-1"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertFalse(state.isLoadingTransaction)
        assertNull(state.loadError)
        assertEquals("150,5", state.amountText)
        assertEquals(Currency.TRY, state.currency)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals(EntityId("cat-market"), state.selectedCategoryId)
        assertEquals("Haftalık Alışveriş", state.description)
        assertEquals(PaymentMethod.CREDIT_CARD, state.paymentMethod)
        assertEquals(LocalDate(2026, 8, 20), state.transactionDate)
        assertNull(state.existingInstallment)

        collectJob.cancel()
    }

    @Test
    fun editMode_withBlankId_neverFallsToAddMode_setsLoadError_andBlocksSubmit() = runTest {
        val viewModel = createViewModel(TransactionFormRoute("   "))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, state.loadError)
        assertFalse(state.isLoadingTransaction)

        // Attempt submit while loadError is present
        viewModel.onAmountChanged("100")
        viewModel.submit()
        advanceUntilIdle()

        assertNull(trxRepo.lastCreatedTransaction)
        assertNull(trxRepo.lastUpdatedTransaction)

        collectJob.cancel()
    }

    @Test
    fun editMode_withRouteDecodeFailure_setsLoadError_andBlocksSubmit() = runTest {
        // Corrupted SavedStateHandle that cannot be decoded into TransactionFormRoute
        val corruptedHandle = SavedStateHandle(mapOf("transactionId" to 12345))
        val viewModel = createViewModelWithHandle(corruptedHandle)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, state.loadError)

        viewModel.onAmountChanged("100")
        viewModel.submit()
        advanceUntilIdle()

        assertNull(trxRepo.lastCreatedTransaction)
        assertNull(trxRepo.lastUpdatedTransaction)

        collectJob.cancel()
    }

    @Test
    fun editMode_withInvalidEntityId_doesNotCrash_setsLoadError_andBlocksSubmit() = runTest {
        val viewModel = createViewModel(TransactionFormRoute("\t\n"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, state.loadError)

        collectJob.cancel()
    }

    @Test
    fun editMode_transactionNotFound_setsLoadError_andBlocksSubmit() = runTest {
        trxRepo.transactionsFlow.value = emptyList()

        val viewModel = createViewModel(TransactionFormRoute("non-existent-trx"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, state.loadError)

        viewModel.submit()
        advanceUntilIdle()
        assertNull(trxRepo.lastUpdatedTransaction)

        collectJob.cancel()
    }

    @Test
    fun categoryObservation_whenExceptionThrown_setsCategoryLoadError_andDoesNotCrash() = runTest {
        catRepo.shouldThrowOnObserve = RuntimeException("database query failed")

        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.categoryLoadError)
        assertTrue(state.availableCategories.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun categoryObservation_retryCategories_restartsSubscription_andClearsErrorOnSuccess() = runTest {
        catRepo.shouldThrowOnObserve = RuntimeException("connection timeout")
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.categoryLoadError)

        // Fix repository error and trigger retry
        catRepo.shouldThrowOnObserve = null
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        viewModel.retryCategories()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.categoryLoadError)
        assertEquals(1, state.availableCategories.size)
        assertEquals("Market", state.availableCategories[0].name)

        collectJob.cancel()
    }

    @Test(expected = AssertionError::class)
    fun categoryObservation_criticalAssertionError_rethrows() = runTest {
        catRepo.shouldThrowOnObserve = AssertionError("critical test assertion")
        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()
        collectJob.cancel()
    }

    @Test
    fun categoryObservation_cancellationException_isRethrownAndNotSwallowedAsGenericError() = runTest {
        catRepo.shouldThrowOnObserve = CancellationException("category observation cancelled")
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("CancellationException generic categoryLoadError'a çevrilmemelidir", state.categoryLoadError)
        assertTrue(state.availableCategories.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun categorySelection_historicalCategoryCannotBeSelectedViaUserCallback() = runTest {
        val deletedCat = Category(
            id = EntityId("cat-deleted"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#9E9E9E"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val existingTrx = Transaction(
            id = EntityId("trx-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(500000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-deleted"),
            description = "Eski Kira",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 8, 1),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory, deletedCat)

        val viewModel = createViewModel(TransactionFormRoute("trx-2"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Initially loaded with historical category
        assertEquals(EntityId("cat-deleted"), viewModel.uiState.value.selectedCategoryId)

        // User chooses active category
        viewModel.onCategoryChanged(EntityId("cat-market"))
        assertEquals(EntityId("cat-market"), viewModel.uiState.value.selectedCategoryId)
        assertNull(viewModel.uiState.value.categoryError)

        // User attempts to re-select historical category
        viewModel.onCategoryChanged(EntityId("cat-deleted"))
        assertEquals(EntityId("cat-market"), viewModel.uiState.value.selectedCategoryId)
        assertEquals(TransactionFormFieldError.CATEGORY_UNAVAILABLE, viewModel.uiState.value.categoryError)

        // User clears category
        viewModel.onCategoryChanged(null)
        assertNull(viewModel.uiState.value.selectedCategoryId)
        assertNull(viewModel.uiState.value.categoryError)

        collectJob.cancel()
    }

    @Test
    fun currentInstantProvider_isNotCalledOnEditSubmit() = runTest {
        val wsCustom = EntityId("ws-custom")
        val customCategory = sampleActiveExpenseCategory.copy(workspaceId = wsCustom)
        val existingTrx = Transaction(
            id = EntityId("trx-3"),
            ownerId = EntityId("user-1"),
            workspaceId = wsCustom,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-market"),
            description = "Eski Açıklama",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = fixedInstant,
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(customCategory)
        catRepo.historyCategoriesFlow.value = listOf(customCategory)

        val viewModel = createViewModel(TransactionFormRoute("trx-3"), countingInstantProvider)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("150")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastUpdatedTransaction)
        assertEquals("Edit submit sırasında CurrentInstantProvider çağrılmamalıdır", 0, countingInstantProvider.callCount)

        collectJob.cancel()
    }

    @Test
    fun currentInstantProvider_calledExactlyOnceOnSingleAdd() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory)

        val viewModel = createViewModel(TransactionFormRoute(null), countingInstantProvider)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("250")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals("Single add submit sırasında CurrentInstantProvider yalnız 1 kez çağrılmalıdır", 1, countingInstantProvider.callCount)
        assertEquals(fixedInstant, trxRepo.lastCreatedTransaction?.createdAt)

        collectJob.cancel()
    }

    @Test
    fun currentInstantProvider_calledExactlyOnceOnInstallmentAdd() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory)

        val viewModel = createViewModel(TransactionFormRoute(null), countingInstantProvider)

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("3000")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        viewModel.onInstallmentCountChanged("3")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedInstallments)
        assertEquals("Installment add submit sırasında CurrentInstantProvider yalnız 1 kez çağrılmalıdır", 1, countingInstantProvider.callCount)

        collectJob.cancel()
    }

    @Test
    fun singleCategoryCollector_typeChangeDoesNotCauseRaceCondition_andClearsSelectedCategory() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory, sampleActiveIncomeCategory)

        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Select expense category
        viewModel.onCategoryChanged(EntityId("cat-market"))
        assertEquals(EntityId("cat-market"), viewModel.uiState.value.selectedCategoryId)
        assertEquals(1, viewModel.uiState.value.availableCategories.size)
        assertEquals("Market", viewModel.uiState.value.availableCategories[0].name)

        // Change type to INCOME
        viewModel.onTypeChanged(TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, viewModel.uiState.value.type)
        assertNull(viewModel.uiState.value.selectedCategoryId)
        assertEquals(1, viewModel.uiState.value.availableCategories.size)
        assertEquals("Maaş", viewModel.uiState.value.availableCategories[0].name)

        collectJob.cancel()
    }

    @Test
    fun typeChange_whenInstallmentOptionBecomesUnavailable_resetsInstallmentEnabled() = runTest {
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }

        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        assertTrue(viewModel.uiState.value.isInstallmentEnabled)

        // Changing type to INCOME makes installment unavailable
        viewModel.onTypeChanged(TransactionType.INCOME)
        assertFalse(viewModel.uiState.value.isInstallmentEnabled)

        collectJob.cancel()
    }

    @Test
    fun paymentMethodChange_whenNonCreditCard_resetsInstallmentEnabled() = runTest {
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }

        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        assertTrue(viewModel.uiState.value.isInstallmentEnabled)

        // Switching to CASH
        viewModel.onPaymentMethodChanged(PaymentMethod.CASH)
        assertFalse(viewModel.uiState.value.isInstallmentEnabled)

        collectJob.cancel()
    }

    @Test
    fun historicalCategory_isPresentedAsNonSelectable_andHasHistoricalSuffix() = runTest {
        val deletedCat = Category(
            id = EntityId("cat-deleted"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#9E9E9E"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val existingTrx = Transaction(
            id = EntityId("trx-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(500000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-deleted"),
            description = "Eski Ev Kirası",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 8, 1),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory, deletedCat)

        val viewModel = createViewModel(TransactionFormRoute("trx-2"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.availableCategories.size)
        val histCatOption = state.availableCategories.find { it.id == EntityId("cat-deleted") }
        assertNotNull(histCatOption)
        assertEquals("Eski Kira (Silinmiş)", histCatOption?.name)
        assertTrue(histCatOption!!.isHistorical)
        assertFalse(histCatOption.isSelectable)

        collectJob.cancel()
    }

    @Test
    fun categoryValidation_whenSelectedCategoryIsUnavailable_returnsCategoryUnavailableError() = runTest {
        val deletedCat = Category(
            id = EntityId("cat-deleted"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#9E9E9E"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val existingTrx = Transaction(
            id = EntityId("trx-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(500000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-deleted"),
            description = "Kira",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 8, 1),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        catRepo.historyCategoriesFlow.value = listOf(sampleActiveExpenseCategory, deletedCat)

        val viewModel = createViewModel(TransactionFormRoute("trx-2"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Trying to submit with the historical/deleted category without changing it
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.CATEGORY_UNAVAILABLE, viewModel.uiState.value.categoryError)
        assertNull(trxRepo.lastUpdatedTransaction)

        collectJob.cancel()
    }

    @Test
    fun descriptionValidation_tooLong_setsDescriptionError_andClearingDescriptionResetsError() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onDescriptionChanged("a".repeat(501))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.DESCRIPTION_TOO_LONG, viewModel.uiState.value.descriptionError)
        assertNull(trxRepo.lastCreatedTransaction)

        // Typing shorter description clears error
        viewModel.onDescriptionChanged("Kısa açıklama")
        assertNull(viewModel.uiState.value.descriptionError)

        collectJob.cancel()
    }

    @Test
    fun formatMinorUnitsToInputText_formatsDifferentCurrenciesAndDecimalsCorrectly() {
        assertEquals("0", TransactionFormViewModel.formatMinorUnitsToInputText(0L, Currency.TRY))
        assertEquals("100", TransactionFormViewModel.formatMinorUnitsToInputText(10000L, Currency.TRY))
        assertEquals("125,5", TransactionFormViewModel.formatMinorUnitsToInputText(12550L, Currency.TRY))
        assertEquals("125,05", TransactionFormViewModel.formatMinorUnitsToInputText(12505L, Currency.TRY))
        assertEquals("0,99", TransactionFormViewModel.formatMinorUnitsToInputText(99L, Currency.USD))
        assertEquals("1500", TransactionFormViewModel.formatMinorUnitsToInputText(150000L, Currency.EUR))
    }

    @Test
    fun submit_singleTransaction_invokesAddTransactionUseCase_andEmitsNavigateBack() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("120,50")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onDescriptionChanged("Market alışverişi")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals(12050L, trxRepo.lastCreatedTransaction?.amount?.amountMinor)
        assertEquals(EntityId("cat-market"), trxRepo.lastCreatedTransaction?.categoryId)
        assertEquals("Market alışverişi", trxRepo.lastCreatedTransaction?.description)
        assertEquals(1, events.size)
        assertEquals(TransactionFormEvent.NavigateBack, events[0])

        collectJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun submit_installmentGroup_invokesAddInstallmentGroupUseCase_andEmitsNavigateBack() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("3000")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        viewModel.onInstallmentCountChanged("3")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedInstallments)
        assertEquals(3, trxRepo.lastCreatedInstallments?.size)
        assertEquals(100000L, trxRepo.lastCreatedInstallments?.get(0)?.amount?.amountMinor)
        assertEquals(1, events.size)
        assertEquals(TransactionFormEvent.NavigateBack, events[0])

        collectJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun submit_installmentPreValidation_amountTooSmall_setsInstallmentAmountTooSmallError() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // 0.05 TRY = 5 minor units, cannot be split into 6 installments
        viewModel.onAmountChanged("0,05")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        viewModel.onInstallmentCountChanged("6")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL, viewModel.uiState.value.installmentCountError)
        assertNull(trxRepo.lastCreatedInstallments)

        collectJob.cancel()
    }

    @Test
    fun submit_editMode_invokesUpdateTransactionUseCase_preservingWorkspaceAndReceipt() = runTest {
        val receipt = ReceiptPath("receipts/user-1/receipt.jpg")
        val wsCustom = EntityId("ws-custom")
        val customCategory = sampleActiveExpenseCategory.copy(workspaceId = wsCustom)
        val existingTrx = Transaction(
            id = EntityId("trx-3"),
            ownerId = EntityId("user-1"),
            workspaceId = wsCustom,
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-market"),
            description = "Eski Açıklama",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = fixedToday,
            receiptPath = receipt,
            installment = null,
            createdAt = fixedInstant,
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(customCategory)
        catRepo.historyCategoriesFlow.value = listOf(customCategory)

        val viewModel = createViewModel(TransactionFormRoute("trx-3"))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("150")
        viewModel.onDescriptionChanged("Güncel Açıklama")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastUpdatedTransaction)
        assertEquals(15000L, trxRepo.lastUpdatedTransaction?.amount?.amountMinor)
        assertEquals("Güncel Açıklama", trxRepo.lastUpdatedTransaction?.description)
        assertEquals(wsCustom, trxRepo.lastUpdatedTransaction?.workspaceId)
        assertEquals(receipt, trxRepo.lastUpdatedTransaction?.receiptPath)
        assertEquals(1, events.size)

        collectJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun submit_doubleSubmit_executesOnlyOnce() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))

        viewModel.submit()
        viewModel.submit() // second rapid submit
        advanceUntilIdle()

        assertEquals(1, trxRepo.transactionsFlow.value.size)

        collectJob.cancel()
    }

    @Test
    fun submit_cancellation_cleansUpSubmittingState_andAllowsSubsequentSubmit() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))

        trxRepo.shouldFailWith = null
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)

        collectJob.cancel()
    }

    @Test
    fun submit_failure_preservesFormState_andShowsGeneralMessage() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        trxRepo.shouldFailWith = AppError.Storage("disk_full")

        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onDescriptionChanged("Deneme")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.STORAGE_ERROR, viewModel.uiState.value.generalMessage)
        assertEquals("100", viewModel.uiState.value.amountText)
        assertEquals("Deneme", viewModel.uiState.value.description)
        assertEquals(0, events.size)

        collectJob.cancel()
        eventJob.cancel()
    }
}
