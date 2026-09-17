package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.navigation.TransactionSuccessRoute
import com.feniqo.mobile.presentation.component.QuickAddAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionSuccessViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var observeTransactionCallCount = 0

        override fun observeTransaction(id: EntityId): Flow<Transaction?> {
            observeTransactionCallCount++
            return transactionsFlow.map { list -> list.find { it.id == id } }
        }

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactionsFlow
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactionsFlow.map { list -> list.filter { it.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> =
            RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(transactions.first().id)
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override fun observeCategory(id: EntityId): Flow<Category?> =
            categoriesFlow.map { list -> list.find { it.id == id } }
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private lateinit var trxRepo: FakeTransactionRepository
    private lateinit var catRepo: FakeCategoryRepository

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        trxRepo = FakeTransactionRepository()
        catRepo = FakeCategoryRepository()
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(route: TransactionSuccessRoute?): TransactionSuccessViewModel {
        val savedStateHandle = SavedStateHandle(
            if (route != null) mapOf("transactionId" to route.transactionId) else emptyMap(),
        )
        return TransactionSuccessViewModel(
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun transactionSuccess_observesRoomSSOT_andBuildsCompleteDisplayModel() = runTest {
        val category = Category(
            id = EntityId("cat-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = CategoryIcon("shopping-bag"),
            isDefault = true,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val transaction = Transaction(
            id = EntityId("tx-101"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(45000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-market"),
            description = "Haftalık Market Alışverişi",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 12),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-09-12T12:00:00Z"),
            note = "Süt ve ekmek alındı",
        )

        catRepo.categoriesFlow.value = listOf(category)
        trxRepo.transactionsFlow.value = listOf(transaction)

        val viewModel = createViewModel(TransactionSuccessRoute("tx-101"))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.transaction)

        val item = state.transaction!!
        assertEquals(EntityId("tx-101"), item.id)
        assertEquals("Haftalık Market Alışverişi", item.description)
        assertEquals("Süt ve ekmek alındı", item.note)
        assertEquals("Market", item.categoryName)
        assertEquals("#10B981", item.categoryColorHex)
        assertEquals(PaymentMethod.CREDIT_CARD, item.paymentMethod)
        assertEquals(TransactionType.EXPENSE, item.type)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_whenTransactionNotFound_emitsErrorState() = runTest {
        val viewModel = createViewModel(TransactionSuccessRoute("tx-non-existent"))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        assertEquals("İşlem bulunamadı.", state.errorMessage)
        assertEquals(1, trxRepo.observeTransactionCallCount)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_whenRouteMissing_emitsInvalidParameterError() = runTest {
        val viewModel = createViewModel(null)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        assertEquals("Geçersiz işlem parametresi.", state.errorMessage)
        assertEquals(0, trxRepo.observeTransactionCallCount)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_whenTransactionIdEmpty_doesNotCrash_andDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel(TransactionSuccessRoute(""))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        assertEquals("Geçersiz işlem parametresi.", state.errorMessage)
        assertEquals(0, trxRepo.observeTransactionCallCount)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_whenTransactionIdWhitespaceOnly_doesNotCrash_andDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel(TransactionSuccessRoute("   "))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        assertEquals("Geçersiz işlem parametresi.", state.errorMessage)
        assertEquals(0, trxRepo.observeTransactionCallCount)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_whenRouteParameterWrongType_emitsInvalidParameterError_andDoesNotCallUseCase() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("transactionId" to 12345))
        val viewModel = TransactionSuccessViewModel(
            observeTransactionUseCase = ObserveTransactionUseCase(trxRepo),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(catRepo),
            savedStateHandle = savedStateHandle,
        )
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        assertEquals("Geçersiz işlem parametresi.", state.errorMessage)
        assertEquals(0, trxRepo.observeTransactionCallCount)

        collectJob.cancel()
    }

    @Test
    fun transactionSuccess_preservesIdWithoutTrimming() = runTest {
        val viewModel = createViewModel(TransactionSuccessRoute("  tx-spaced  "))
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(1, trxRepo.observeTransactionCallCount)
        collectJob.cancel()
    }

    @Test
    fun quickAddActions_availabilityContract_isMaintained() {
        assertTrue(QuickAddAction.EXPENSE.available)
        assertTrue(QuickAddAction.INCOME.available)
        assertFalse(QuickAddAction.TRANSFER.available)
        assertTrue(QuickAddAction.DEBT_RECEIVABLE.available)
        assertTrue(QuickAddAction.RECURRING_TRANSACTION.available)
    }
}
