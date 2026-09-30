package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.navigation.TransactionDetailRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = EntityId("user-1")

    private class FakeTransactionRepository : TransactionRepository {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())
        var observedId: EntityId? = null

        override fun observeTransaction(id: EntityId): Flow<Transaction?> {
            observedId = id
            return transactions.map { list -> list.find { it.id == id } }
        }

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactions
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactions.map { list -> list.filter { it.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction) = RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>) =
            RepositoryResult.Success(transactions.first().id)
        override suspend fun update(transaction: Transaction) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            transactions.value = transactions.value.filterNot { it.id == id }
            return RepositoryResult.Success(Unit)
        }
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
            transactions.value = transactions.value.filterNot { it.id in ids }
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = MutableStateFlow<List<Category>>(emptyList())
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?) = categories
        override fun observeCategory(id: EntityId) = categories.map { list -> list.find { it.id == id } }
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?) = categories
        override suspend fun create(category: Category) = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
    }

    private inner class FakeAuthRepository : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(
            AuthSession(userId, "user@feniqo.com", Instant.parse("2026-09-01T00:00:00Z")),
        )
        override fun observeCurrentProfile() = MutableStateFlow<com.feniqo.mobile.domain.model.UserProfile?>(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(userId)
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository()
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(rawId: Any?): TransactionDetailViewModel = TransactionDetailViewModel(
        observeTransactionUseCase = ObserveTransactionUseCase(transactionRepository),
        observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(categoryRepository),
        deleteTransactionUseCase = DeleteTransactionUseCase(FakeAuthRepository(), transactionRepository),
        currentDateProvider = CurrentDateProvider { LocalDate(2026, 9, 30) },
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to rawId)),
    )

    @Test
    fun validRoute_observesExactTransactionAndBuildsDetail() = runTest {
        val category = category()
        val transaction = transaction()
        categoryRepository.categories.value = listOf(category)
        transactionRepository.transactions.value = listOf(transaction)
        val viewModel = createViewModel(TransactionDetailRoute("transaction-1").transactionId)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(EntityId("transaction-1"), transactionRepository.observedId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("Market", viewModel.uiState.value.item?.categoryName)
        collectJob.cancel()
    }

    @Test
    fun invalidRoute_failsClosedWithoutObservingRepository() = runTest {
        val viewModel = createViewModel("   ")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.item)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(transactionRepository.observedId)
        collectJob.cancel()
    }

    @Test
    fun confirmedDelete_emitsDeletedEventAndRemovesTransaction() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestDelete()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.deleteDialog)
        viewModel.confirmSingleDelete()
        advanceUntilIdle()

        assertEquals(TransactionDetailEvent.Deleted, viewModel.events.first())
        assertEquals(emptyList<Transaction>(), transactionRepository.transactions.value)
        collectJob.cancel()
    }

    private fun category() = Category(
        id = EntityId("category-1"),
        ownerId = null,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("shopping-bag"),
        isDefault = true,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun transaction() = Transaction(
        id = EntityId("transaction-1"),
        ownerId = userId,
        workspaceId = null,
        amount = Money(12_500L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("category-1"),
        description = "Market alışverişi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 29),
        receiptPath = null,
        installment = null,
        createdAt = Instant.parse("2026-09-29T12:00:00Z"),
    )
}
