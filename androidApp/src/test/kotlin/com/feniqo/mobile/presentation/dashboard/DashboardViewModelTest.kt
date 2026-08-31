package com.feniqo.mobile.presentation.dashboard

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
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.CalculateDashboardSummaryUseCase
import com.feniqo.mobile.domain.usecase.CalculateMoneyScoreUseCase
import com.feniqo.mobile.domain.usecase.MoneyScoreInput
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveDashboardSummaryUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
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
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate(2026, 8, 27)
    private val testDateProvider = CurrentDateProvider { fixedToday }

    private class FakeTransactionRepo : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var shouldThrowOnObserve: Throwable? = null
        var observeSubscriptionCount = 0

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
            observeSubscriptionCount++
            val error = shouldThrowOnObserve
            return if (error != null) {
                flow { throw error }
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

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepo : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        var shouldThrowOnObserve: Throwable? = null

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> {
            val error = shouldThrowOnObserve
            return if (error != null) {
                flow { throw error }
            } else {
                categoriesFlow
            }
        }

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> {
            val error = shouldThrowOnObserve
            return if (error != null) {
                flow { throw error }
            } else {
                categoriesFlow
            }
        }

        override fun observeCategory(id: EntityId): Flow<Category?> =
            MutableStateFlow(categoriesFlow.value.firstOrNull { it.id == id })

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private fun createViewModel(
        transactionRepo: FakeTransactionRepo,
        categoryRepo: FakeCategoryRepo,
        dateProvider: CurrentDateProvider = testDateProvider,
        moneyScoreCalculator: CalculateMoneyScoreUseCase = CalculateMoneyScoreUseCase(),
    ): DashboardViewModel {
        val observeTransactions = ObserveTransactionsUseCase(transactionRepo)
        val observeSummary = ObserveDashboardSummaryUseCase(
            transactionRepository = transactionRepo,
            calculator = CalculateDashboardSummaryUseCase(),
        )
        val observeCategoriesHistory = ObserveCategoriesForHistoryLookupUseCase(categoryRepo)

        return DashboardViewModel(
            observeDashboardSummaryUseCase = observeSummary,
            observeTransactionsUseCase = observeTransactions,
            observeCategoriesForHistoryLookupUseCase = observeCategoriesHistory,
            calculateMoneyScoreUseCase = moneyScoreCalculator,
            currentDateProvider = dateProvider,
        )
    }

    @Test
    fun selectedMonth_computedDynamicallyFromDateProvider() = runTest {
        val fakeTxRepo = FakeTransactionRepo()
        val fakeCatRepo = FakeCategoryRepo()
        val dateProvider = CurrentDateProvider { LocalDate(2026, 8, 27) }

        val viewModel = createViewModel(fakeTxRepo, fakeCatRepo, dateProvider)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()
        assertEquals(YearMonth("2026-08"), viewModel.uiState.value.selectedMonth)
        collectJob.cancel()
    }

    @Test
    fun initialLoading_transitionsToSuccess_withCorrectDashboardData() = runTest {
        val fakeTxRepo = FakeTransactionRepo()
        val fakeCatRepo = FakeCategoryRepo()

        val sampleCat = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            icon = CategoryIcon("shopping"),
            color = CategoryColor("#10B981"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        fakeCatRepo.categoriesFlow.value = listOf(sampleCat)

        val sampleTx = Transaction(
            id = EntityId("tx-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(amountMinor = 15000, currency = Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = sampleCat.id,
            description = "Market harcaması",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 8, 20),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-20T10:00:00Z"),
        )
        fakeTxRepo.transactionsFlow.value = listOf(sampleTx)

        val viewModel = createViewModel(fakeTxRepo, fakeCatRepo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertNotNull(state.dashboard)

        val dashboard = state.dashboard!!
        assertEquals("Ağustos 2026", dashboard.formattedMonth)
        assertEquals("-150,00 ₺", dashboard.monthlySummary.formattedExpense)
        assertEquals("-150,00 ₺", dashboard.monthlySummary.formattedBalance)
        assertEquals(NetBalanceStatus.NEGATIVE, dashboard.monthlySummary.balanceStatus)

        // Top category matches
        assertNotNull(dashboard.topExpenseCategory)
        assertEquals("Market", dashboard.topExpenseCategory?.categoryName)
        assertEquals("150,00 ₺", dashboard.topExpenseCategory?.formattedAmount)

        // Recent transaction mapped
        assertEquals(1, dashboard.recentTransactions.size)
        assertEquals(EntityId("tx-1"), dashboard.recentTransactions[0].id)
        assertEquals("Market", dashboard.recentTransactions[0].categoryName)

        // MoneyScore is calculated and present
        assertNotNull(dashboard.moneyScore)
        assertTrue(dashboard.moneyScore!!.isProvisional)
        assertTrue(dashboard.moneyScore!!.explanationText.contains("nötr başlangıç puanlarıyla"))

        collectJob.cancel()
    }

    @Test
    fun moneyScore_matchesCalculateMoneyScoreUseCase_andExcludesOtherMonthTransactions() = runTest {
        val fakeTxRepo = FakeTransactionRepo()
        val fakeCatRepo = FakeCategoryRepo()

        val catIncome = Category(
            id = EntityId("cat-inc"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            icon = CategoryIcon("payments"),
            color = CategoryColor("#3B82F6"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val catExpense = Category(
            id = EntityId("cat-exp"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Kira",
            type = TransactionType.EXPENSE,
            icon = CategoryIcon("home"),
            color = CategoryColor("#EF4444"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        fakeCatRepo.categoriesFlow.value = listOf(catIncome, catExpense)

        // Ağustos işlemleri
        val txAugustIncome = Transaction(
            id = EntityId("tx-aug-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(amountMinor = 1000000L, currency = Currency.TRY), // 10.000 TL
            type = TransactionType.INCOME,
            categoryId = catIncome.id,
            description = "Ağustos Maaş",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 8, 1),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        val txAugustExpense = Transaction(
            id = EntityId("tx-aug-2"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(amountMinor = 400000L, currency = Currency.TRY), // 4.000 TL
            type = TransactionType.EXPENSE,
            categoryId = catExpense.id,
            description = "Ağustos Kira",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 8, 5),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-05T10:00:00Z"),
        )

        // Temmuz işlemi (Ağustos skor girdisine katılmamalı)
        val txJulyExpense = Transaction(
            id = EntityId("tx-july-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(amountMinor = 5000000L, currency = Currency.TRY), // 50.000 TL
            type = TransactionType.EXPENSE,
            categoryId = catExpense.id,
            description = "Temmuz Masrafı",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 7, 20),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-07-20T10:00:00Z"),
        )

        fakeTxRepo.transactionsFlow.value = listOf(txAugustIncome, txAugustExpense, txJulyExpense)

        val calculator = CalculateMoneyScoreUseCase()
        val viewModel = createViewModel(fakeTxRepo, fakeCatRepo, moneyScoreCalculator = calculator)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        val dashboard = state.dashboard!!
        val moneyScore = dashboard.moneyScore
        assertNotNull(moneyScore)

        // Bağımsız direkt hesaplanan beklenen skor
        val expectedScore = calculator(
            MoneyScoreInput(
                income = Money(amountMinor = 1000000L, currency = Currency.TRY),
                expense = Money(amountMinor = 400000L, currency = Currency.TRY),
                budgets = emptyList(),
                transactions = listOf(txAugustIncome, txAugustExpense),
                debts = emptyList(),
                goals = emptyList(),
                today = fixedToday,
            ),
        )

        assertEquals(expectedScore.total, moneyScore!!.totalScore)
        assertEquals(expectedScore.savings, moneyScore.savingsScore)
        assertEquals(expectedScore.budget, moneyScore.budgetScore)
        assertEquals(expectedScore.debt, moneyScore.debtScore)
        assertEquals(expectedScore.goal, moneyScore.goalScore)
        assertEquals(expectedScore.level, moneyScore.level)
        assertTrue(moneyScore.isProvisional)
        assertEquals(
            "Skor şu anda gelir-gider hareketleri ve henüz kullanılmayan bütçe, borç ve hedef modülleri için nötr başlangıç puanlarıyla hesaplanır.",
            moneyScore.explanationText,
        )

        collectJob.cancel()
    }

    @Test
    fun observationError_producesGenericError_andClearsOnRetry() = runTest {
        val fakeTxRepo = FakeTransactionRepo()
        val fakeCatRepo = FakeCategoryRepo()

        // Hata durumunu tetikle
        fakeTxRepo.shouldThrowOnObserve = RuntimeException("Room read failure")

        val viewModel = createViewModel(fakeTxRepo, fakeCatRepo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertNull(errorState.dashboard)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, errorState.observationError)

        // Hatayı düzelt ve retry() çağır
        fakeTxRepo.shouldThrowOnObserve = null
        val beforeRetrySubCount = fakeTxRepo.observeSubscriptionCount
        viewModel.retry()

        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertFalse(recoveredState.isLoading)
        assertNull(recoveredState.observationError)
        assertNotNull(recoveredState.dashboard)
        assertTrue(fakeTxRepo.observeSubscriptionCount > beforeRetrySubCount)

        collectJob.cancel()
    }

    @Test
    fun cancellationException_isNotCaughtAsGenericError() = runTest {
        val fakeTxRepo = FakeTransactionRepo()
        val fakeCatRepo = FakeCategoryRepo()

        fakeTxRepo.shouldThrowOnObserve = CancellationException("Coroutines cancelled")

        val viewModel = createViewModel(fakeTxRepo, fakeCatRepo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        // CancellationException observationError üretmemeli
        val state = viewModel.uiState.value
        assertNull(state.observationError)

        collectJob.cancel()
    }
}
