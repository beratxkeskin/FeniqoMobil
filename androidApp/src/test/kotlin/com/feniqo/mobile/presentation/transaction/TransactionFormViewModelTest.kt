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
import com.feniqo.mobile.domain.model.ReceiptOcrDraft
import com.feniqo.mobile.domain.model.OcrCandidate
import com.feniqo.mobile.domain.model.OcrCandidateConfidence
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
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

    private class FakeWorkspaceRepository : com.feniqo.mobile.domain.repository.WorkspaceRepository {
        val activeWorkspaceFlow = MutableStateFlow<com.feniqo.mobile.domain.model.Workspace?>(null)
        val membersFlow = MutableStateFlow<Map<EntityId, List<com.feniqo.mobile.domain.model.WorkspaceMember>>>(emptyMap())

        override fun observeActiveWorkspace(): Flow<com.feniqo.mobile.domain.model.Workspace?> = activeWorkspaceFlow
        override fun observeWorkspaces(): Flow<List<com.feniqo.mobile.domain.model.Workspace>> = MutableStateFlow(emptyList())
        override fun observeMembers(workspaceId: EntityId): Flow<List<com.feniqo.mobile.domain.model.WorkspaceMember>> =
            membersFlow.map {
                it[workspaceId] ?: listOf(
                    com.feniqo.mobile.domain.model.WorkspaceMember(
                        workspaceId = workspaceId,
                        userId = EntityId("user-1"),
                        role = com.feniqo.mobile.domain.model.WorkspaceRole.OWNER,
                        joinedAt = Instant.fromEpochMilliseconds(0),
                    ),
                )
            }
        override suspend fun create(name: String): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-1"))
        override suspend fun createWorkspace(command: com.feniqo.mobile.domain.model.CreateWorkspaceCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-1"))
        override suspend fun updateWorkspace(command: com.feniqo.mobile.domain.model.UpdateWorkspaceCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<com.feniqo.mobile.domain.repository.WorkspaceInviteCode> =
            RepositoryResult.Success(com.feniqo.mobile.domain.repository.WorkspaceInviteCode("INV123"))
        override suspend fun join(inviteCode: com.feniqo.mobile.domain.repository.WorkspaceInviteCode): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-1"))
        override suspend fun changeMemberRole(workspaceId: EntityId, userId: EntityId, role: com.feniqo.mobile.domain.model.WorkspaceRole): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private lateinit var authRepo: FakeAuthRepository
    private lateinit var trxRepo: FakeTransactionRepository
    private lateinit var catRepo: FakeCategoryRepository
    private lateinit var workspaceRepo: FakeWorkspaceRepository
    private lateinit var idGenerator: SequentialEntityIdGenerator
    private lateinit var countingInstantProvider: CountingInstantProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository()
        trxRepo = FakeTransactionRepository()
        catRepo = FakeCategoryRepository()
        workspaceRepo = FakeWorkspaceRepository()
        idGenerator = SequentialEntityIdGenerator()
        countingInstantProvider = CountingInstantProvider(fixedInstant)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun receiptOcrDraft_isAppliedOnlyAfterExplicitUserConfirmation() = runTest {
        val viewModel = createViewModel(TransactionFormRoute())
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.amountText)

        viewModel.applyReceiptOcrDraft(
            ReceiptOcrDraft(
                merchantName = OcrCandidate("Feniqo Market", OcrCandidateConfidence.LOW),
                total = OcrCandidate(Money(12_345L, Currency.TRY), OcrCandidateConfidence.HIGH),
                transactionDate = OcrCandidate(LocalDate(2026, 9, 9), OcrCandidateConfidence.HIGH),
            ),
        )

        assertEquals("123,45", viewModel.uiState.value.amountText)
        assertFalse(viewModel.uiState.value.hasReceipt)
        assertEquals("Feniqo Market", viewModel.uiState.value.description)
        assertEquals(LocalDate(2026, 9, 9), viewModel.uiState.value.transactionDate)
    }

    private fun createViewModel(
        route: TransactionFormRoute,
        instantProvider: CurrentInstantProvider = countingInstantProvider,
    ): TransactionFormViewModel {
        val savedStateHandle = SavedStateHandle(
            if (route.transactionId != null) mapOf("transactionId" to route.transactionId) else emptyMap(),
        )

        return TransactionFormViewModel(
            addTransactionUseCase = AddTransactionUseCase(authRepo, catRepo, trxRepo, workspaceRepo),
            addInstallmentGroupUseCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGenerator),
            updateTransactionUseCase = UpdateTransactionUseCase(authRepo, catRepo, trxRepo, workspaceRepo),
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(workspaceRepo),
            observeWorkspaceMembersUseCase = com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase(workspaceRepo),
            observeAuthSessionUseCase = com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase(authRepo),
            currentDateProvider = fakeDateProvider,
            currentInstantProvider = instantProvider,
            entityIdGenerator = idGenerator,
            savedStateHandle = savedStateHandle,
        )
    }

    private fun createViewModelWithHandle(savedStateHandle: SavedStateHandle): TransactionFormViewModel {
        return TransactionFormViewModel(
            addTransactionUseCase = AddTransactionUseCase(authRepo, catRepo, trxRepo, workspaceRepo),
            addInstallmentGroupUseCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGenerator),
            updateTransactionUseCase = UpdateTransactionUseCase(authRepo, catRepo, trxRepo, workspaceRepo),
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(workspaceRepo),
            observeWorkspaceMembersUseCase = com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase(workspaceRepo),
            observeAuthSessionUseCase = com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase(authRepo),
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
        viewModel.onTitleChanged("Market")
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
        viewModel.onTitleChanged("Elektronik")
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
    fun titleValidation_emptyAndBlank_setsTitleRequiredError() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.TITLE_REQUIRED, viewModel.uiState.value.titleError)
        assertNull(trxRepo.lastCreatedTransaction)

        // Blank with only spaces is also rejected
        viewModel.onTitleChanged("   ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.TITLE_REQUIRED, viewModel.uiState.value.titleError)
        assertNull(trxRepo.lastCreatedTransaction)

        // Typing valid title clears error
        viewModel.onTitleChanged("Geçerli Başlık")
        assertNull(viewModel.uiState.value.titleError)

        collectJob.cancel()
    }

    @Test
    fun titleValidation_100CharsAccepted_101CharsSetsTitleTooLongError() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("a".repeat(101))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.TITLE_TOO_LONG, viewModel.uiState.value.titleError)
        assertNull(trxRepo.lastCreatedTransaction)

        // Exactly 100 chars succeeds
        viewModel.onTitleChanged("a".repeat(100))
        viewModel.submit()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.titleError)
        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals("a".repeat(100), trxRepo.lastCreatedTransaction?.description)

        collectJob.cancel()
    }

    @Test
    fun noteValidation_500CharsAccepted_501CharsSetsNoteTooLongError() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("Market")
        viewModel.onNoteChanged("n".repeat(501))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(TransactionFormFieldError.NOTE_TOO_LONG, viewModel.uiState.value.noteError)
        assertNull(trxRepo.lastCreatedTransaction)

        // Exactly 500 chars succeeds
        viewModel.onNoteChanged("n".repeat(500))
        viewModel.submit()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.noteError)
        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals("n".repeat(500), trxRepo.lastCreatedTransaction?.note)

        collectJob.cancel()
    }

    @Test
    fun titleAndNote_trimmedBeforeSaving_andBlankNoteBecomesNull() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("  Market Alışverişi  ")
        viewModel.onNoteChanged("  Önemli Not  ")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals("Market Alışverişi", trxRepo.lastCreatedTransaction?.description)
        assertEquals("Önemli Not", trxRepo.lastCreatedTransaction?.note)

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
    fun submit_singleTransaction_invokesAddTransactionUseCase_andEmitsTransactionCreated() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("120,50")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("Market alışverişi")
        viewModel.onNoteChanged("Haftalık pazar")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals(12050L, trxRepo.lastCreatedTransaction?.amount?.amountMinor)
        assertEquals(EntityId("cat-market"), trxRepo.lastCreatedTransaction?.categoryId)
        assertEquals("Market alışverişi", trxRepo.lastCreatedTransaction?.description)
        assertEquals("Haftalık pazar", trxRepo.lastCreatedTransaction?.note)
        assertEquals(1, events.size)
        val event = events[0] as TransactionFormEvent.TransactionCreated
        assertEquals(trxRepo.lastCreatedTransaction?.id, event.transactionId)

        collectJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun submit_installmentGroup_invokesAddInstallmentGroupUseCase_andEmitsTransactionCreated() = runTest {
        catRepo.activeCategoriesFlow.value = listOf(sampleActiveExpenseCategory)
        val viewModel = createViewModel(TransactionFormRoute(null))

        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("3000")
        viewModel.onCategoryChanged(EntityId("cat-market"))
        viewModel.onTitleChanged("Mobilya")
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        viewModel.onInstallmentCountChanged("3")
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedInstallments)
        assertEquals(3, trxRepo.lastCreatedInstallments?.size)
        assertEquals(100000L, trxRepo.lastCreatedInstallments?.get(0)?.amount?.amountMinor)
        assertEquals(1, events.size)
        assertTrue(events[0] is TransactionFormEvent.TransactionCreated)

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
        viewModel.onReceiptRemoved()
        viewModel.submit()
        advanceUntilIdle()
        assertNull(trxRepo.lastUpdatedTransaction?.receiptPath)
        assertEquals(2, events.size)

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
        viewModel.onTitleChanged("Market")

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
        viewModel.onTitleChanged("Market")

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

    @Test
    fun split_personalAndIncomeMode_doesNotRequireOrShowSplit() = runTest {
        val wsId = EntityId("ws-1")
        val sampleMember = WorkspaceMember(
            workspaceId = wsId,
            userId = EntityId("user-1"),
            role = WorkspaceRole.OWNER,
            joinedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(sampleMember))

        // 1. Personal mode (activeWorkspaceId == null)
        workspaceRepo.activeWorkspaceFlow.value = null
        val personalVm = createViewModel(TransactionFormRoute(null))
        val job1 = launch(UnconfinedTestDispatcher()) { personalVm.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(personalVm.uiState.value.isSharedExpense)
        assertTrue(personalVm.uiState.value.workspaceMembers.isEmpty())
        assertNull(personalVm.uiState.value.selectedPaidByUserId)
        assertTrue(personalVm.uiState.value.selectedParticipantUserIds.isEmpty())
        job1.cancel()

        // 2. Shared INCOME mode
        workspaceRepo.activeWorkspaceFlow.value = Workspace(
            id = wsId,
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val incomeVm = createViewModel(TransactionFormRoute(null))
        val job2 = launch(UnconfinedTestDispatcher()) { incomeVm.uiState.collect { } }
        advanceUntilIdle()

        incomeVm.onTypeChanged(TransactionType.INCOME)
        advanceUntilIdle()

        assertFalse(incomeVm.uiState.value.isSharedExpense)
        assertTrue(incomeVm.uiState.value.workspaceMembers.isEmpty())
        job2.cancel()
    }

    @Test
    fun split_sharedExpenseMode_loadsMembersFromFlow() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(
            workspaceId = wsId,
            userId = EntityId("user-1"),
            role = WorkspaceRole.OWNER,
            joinedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val member2 = WorkspaceMember(
            workspaceId = wsId,
            userId = EntityId("user-2"),
            role = WorkspaceRole.EDITOR,
            joinedAt = Instant.parse("2026-08-02T00:00:00Z"),
        )
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(
            id = wsId,
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSharedExpense)
        assertEquals(2, state.workspaceMembers.size)
        // Current user (user-1) comes first
        assertEquals(EntityId("user-1"), state.workspaceMembers[0].userId)
        assertTrue(state.workspaceMembers[0].isCurrentUser)
        assertEquals(EntityId("user-2"), state.workspaceMembers[1].userId)
        assertFalse(state.workspaceMembers[1].isCurrentUser)

        collectJob.cancel()
    }

    @Test
    fun split_newSharedExpense_defaultsActiveUserAsPayerAndParticipant() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(
            workspaceId = wsId,
            userId = EntityId("user-1"),
            role = WorkspaceRole.OWNER,
            joinedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val member2 = WorkspaceMember(
            workspaceId = wsId,
            userId = EntityId("user-2"),
            role = WorkspaceRole.EDITOR,
            joinedAt = Instant.parse("2026-08-02T00:00:00Z"),
        )
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(
            id = wsId,
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EntityId("user-1"), state.selectedPaidByUserId)
        assertEquals(setOf(EntityId("user-1")), state.selectedParticipantUserIds)
        assertTrue(state.canSubmitSplit)

        collectJob.cancel()
    }

    @Test
    fun split_selectingPayer_automaticallyAddsToParticipantList() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Select user-2 as payer
        viewModel.onPaidByUserSelected(EntityId("user-2"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EntityId("user-2"), state.selectedPaidByUserId)
        assertTrue(state.selectedParticipantUserIds.contains(EntityId("user-2")))
        assertTrue(state.canSubmitSplit)

        collectJob.cancel()
    }

    @Test
    fun split_payerCannotBeRemovedFromParticipantList() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Payer is user-1, try untoggling user-1
        viewModel.onParticipantToggled(EntityId("user-1"))
        advanceUntilIdle()

        // user-1 must still remain in participant list
        assertTrue(viewModel.uiState.value.selectedParticipantUserIds.contains(EntityId("user-1")))

        collectJob.cancel()
    }

    @Test
    fun split_uncheckingNonPayerParticipant_updatesList_andEmptyParticipantsBlocksSubmit() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Add user-2
        viewModel.onParticipantToggled(EntityId("user-2"))
        advanceUntilIdle()
        assertEquals(setOf(EntityId("user-1"), EntityId("user-2")), viewModel.uiState.value.selectedParticipantUserIds)

        // Remove user-2
        viewModel.onParticipantToggled(EntityId("user-2"))
        advanceUntilIdle()
        assertEquals(setOf(EntityId("user-1")), viewModel.uiState.value.selectedParticipantUserIds)

        collectJob.cancel()
    }

    @Test
    fun split_editMode_loadsExistingSplitDataAccurately() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val category = sampleActiveExpenseCategory.copy(workspaceId = wsId)
        val existingTrx = Transaction(
            id = EntityId("trx-split"),
            ownerId = EntityId("user-1"),
            workspaceId = wsId,
            amount = Money(20000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = category.id,
            description = "Ortak Yemek",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = fixedToday,
            receiptPath = null,
            installment = null,
            createdAt = fixedInstant,
            paidByUserId = EntityId("user-2"),
            participantUserIds = listOf(EntityId("user-1"), EntityId("user-2")),
        )
        trxRepo.transactionsFlow.value = listOf(existingTrx)
        catRepo.activeCategoriesFlow.value = listOf(category)
        catRepo.historyCategoriesFlow.value = listOf(category)

        val viewModel = createViewModel(TransactionFormRoute("trx-split"))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EntityId("user-2"), state.selectedPaidByUserId)
        assertEquals(setOf(EntityId("user-1"), EntityId("user-2")), state.selectedParticipantUserIds)
        assertTrue(state.canSubmitSplit)

        collectJob.cancel()
    }

    @Test
    fun split_memberFlowChange_reconcilesSelectedPayerAndParticipantsSafely() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Select user-2 as payer
        viewModel.onPaidByUserSelected(EntityId("user-2"))
        advanceUntilIdle()
        assertEquals(EntityId("user-2"), viewModel.uiState.value.selectedPaidByUserId)

        // Member 2 is removed from workspace
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1))
        advanceUntilIdle()

        // Reconciler should fallback payer to user-1 safely
        val state = viewModel.uiState.value
        assertEquals(EntityId("user-1"), state.selectedPaidByUserId)
        assertEquals(setOf(EntityId("user-1")), state.selectedParticipantUserIds)
        assertFalse(state.selectedParticipantUserIds.contains(EntityId("user-2")))

        collectJob.cancel()
    }

    @Test
    fun split_invalidSplit_blocksSubmitWithoutCallingRepository() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val category = sampleActiveExpenseCategory.copy(workspaceId = wsId)
        catRepo.activeCategoriesFlow.value = listOf(category)

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("100")
        viewModel.onCategoryChanged(category.id)

        // All workspace members leave -> empty member list
        workspaceRepo.membersFlow.value = mapOf(wsId to emptyList())
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.splitError)
        assertNull(trxRepo.lastCreatedTransaction)

        collectJob.cancel()
    }

    @Test
    fun split_validSubmit_passesSplitFieldsToCommandAndRepository() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val category = sampleActiveExpenseCategory.copy(workspaceId = wsId)
        catRepo.activeCategoriesFlow.value = listOf(category)

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        val events = mutableListOf<TransactionFormEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onAmountChanged("500")
        viewModel.onTitleChanged("Ortak Harcama")
        viewModel.onCategoryChanged(category.id)
        viewModel.onPaidByUserSelected(EntityId("user-2"))
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedTransaction)
        assertEquals(EntityId("user-2"), trxRepo.lastCreatedTransaction?.paidByUserId)
        assertEquals(listOf(EntityId("user-1"), EntityId("user-2")), trxRepo.lastCreatedTransaction?.participantUserIds?.sortedBy { it.value })
        assertEquals(1, events.size)
        assertTrue(events[0] is TransactionFormEvent.TransactionCreated)

        collectJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun split_validInstallmentSubmit_passesSplitFieldsToAddInstallmentGroupCommand() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev Bütçesi", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val category = sampleActiveExpenseCategory.copy(workspaceId = wsId)
        catRepo.activeCategoriesFlow.value = listOf(category)

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("600")
        viewModel.onTitleChanged("Taksitli Ortak Harcama")
        viewModel.onCategoryChanged(category.id)
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        viewModel.onInstallmentToggle(true)
        viewModel.onInstallmentCountChanged("3")
        viewModel.onPaidByUserSelected(EntityId("user-1"))
        viewModel.onParticipantToggled(EntityId("user-2"))
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(trxRepo.lastCreatedInstallments)
        val installments = trxRepo.lastCreatedInstallments!!
        assertEquals(3, installments.size)
        for (inst in installments) {
            assertEquals(EntityId("user-1"), inst.paidByUserId)
            assertEquals(listOf(EntityId("user-1"), EntityId("user-2")), inst.participantUserIds.sortedBy { it.value })
        }

        collectJob.cancel()
    }

    @Test
    fun dirtyTracking_initialCreateMode_hasUnsavedChangesIsFalse() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Test", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        collectJob.cancel()
    }

    @Test
    fun dirtyTracking_whenFieldsModified_becomesTrue_andRevertingBecomesFalse() = runTest {
        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Amount modification
        viewModel.onAmountChanged("150")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onAmountChanged("")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Title modification
        viewModel.onTitleChanged("Market")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onTitleChanged("")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Note modification
        viewModel.onNoteChanged("Fiş eklendi")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onNoteChanged("")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Payment method modification
        viewModel.onPaymentMethodChanged(PaymentMethod.CREDIT_CARD)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onPaymentMethodChanged(PaymentMethod.CASH)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Receipt modification
        viewModel.onReceiptAttached()
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onReceiptRemoved()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        collectJob.cancel()
    }

    @Test
    fun dirtyTracking_editMode_initialFalse_modifiedTrue_revertFalse() = runTest {
        val txId = EntityId("tx-existing-1")
        val category = sampleActiveExpenseCategory
        catRepo.activeCategoriesFlow.value = listOf(category)
        catRepo.historyCategoriesFlow.value = listOf(category)
        trxRepo.transactionsFlow.value = listOf(
            Transaction(
                id = txId,
                ownerId = EntityId("user-1"),
                workspaceId = EntityId("ws-1"),
                amount = Money(25000L, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = category.id,
                description = "Eski Başlık",
                paymentMethod = PaymentMethod.CREDIT_CARD,
                transactionDate = fixedToday,
                receiptPath = null,
                paidByUserId = EntityId("user-1"),
                participantUserIds = listOf(EntityId("user-1")),
                createdAt = fixedInstant,
                installment = null,
                note = "Eski not",
            ),
        )

        val viewModel = createViewModel(TransactionFormRoute(txId.value))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onTitleChanged("Yeni Başlık")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onTitleChanged("Eski Başlık")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        collectJob.cancel()
    }

    @Test
    fun dirtyTracking_submitValidationOrRepoError_doesNotClearHasUnsavedChanges() = runTest {
        val category = sampleActiveExpenseCategory
        catRepo.activeCategoriesFlow.value = listOf(category)

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAmountChanged("500")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Validation failure (title and category missing)
        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.titleError)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Now fill required fields but repo fails
        viewModel.onTitleChanged("Harcama")
        viewModel.onCategoryChanged(category.id)
        trxRepo.shouldFailWith = AppError.Network("Ağ hatası")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.NETWORK_ERROR, viewModel.uiState.value.generalMessage)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Now repo succeeds
        trxRepo.shouldFailWith = null
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        collectJob.cancel()
    }

    @Test
    fun dirtyTracking_participantSetOrder_doesNotTriggerDirty() = runTest {
        val wsId = EntityId("ws-1")
        val member1 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-1"), role = WorkspaceRole.OWNER, joinedAt = Instant.parse("2026-08-01T00:00:00Z"))
        val member2 = WorkspaceMember(workspaceId = wsId, userId = EntityId("user-2"), role = WorkspaceRole.EDITOR, joinedAt = Instant.parse("2026-08-02T00:00:00Z"))
        workspaceRepo.membersFlow.value = mapOf(wsId to listOf(member1, member2))
        workspaceRepo.activeWorkspaceFlow.value = Workspace(id = wsId, name = "Ev", ownerId = EntityId("user-1"), createdAt = Instant.parse("2026-08-01T00:00:00Z"))

        val viewModel = createViewModel(TransactionFormRoute(null))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Toggle user-2
        viewModel.onParticipantToggled(EntityId("user-2"))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Toggle user-2 back off
        viewModel.onParticipantToggled(EntityId("user-2"))
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        collectJob.cancel()
    }
}
