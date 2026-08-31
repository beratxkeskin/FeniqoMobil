package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BudgetObservationUseCasesTest {

    private class FakeBudgetRepository : BudgetRepository {
        var budgetsToEmit: List<Budget> = emptyList()
        var budgetToEmit: Budget? = null
        var lastObservedBudgetId: EntityId? = null
        var throwCancellation: Boolean = false

        override fun observeBudgets(month: YearMonth, workspaceId: EntityId?): Flow<List<Budget>> = flow {
            if (throwCancellation) throw CancellationException("Budget flow cancelled")
            emit(budgetsToEmit)
        }

        override fun observeBudget(id: EntityId): Flow<Budget?> = flow {
            lastObservedBudgetId = id
            if (throwCancellation) throw CancellationException("Budget flow cancelled")
            emit(budgetToEmit)
        }
        override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("b1"))
        override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun copyBudgets(command: com.feniqo.mobile.domain.model.CopyBudgetsCommand): RepositoryResult<com.feniqo.mobile.domain.model.CopyBudgetsResult> =
            RepositoryResult.Success(com.feniqo.mobile.domain.model.CopyBudgetsResult(0))
    }

    private class FakeTransactionRepository : TransactionRepository {
        var transactionsToEmit: List<Transaction> = emptyList()
        var lastFilter: TransactionFilter? = null

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = flow {
            lastFilter = filter
            emit(transactionsToEmit)
        }

        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("t1"))
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("t1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        var categoriesToEmit: List<Category> = emptyList()

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = flowOf(emptyList())
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(null)
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flow {
            emit(categoriesToEmit)
        }
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("c1"))
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private val budgetRepo = FakeBudgetRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val categoryRepo = FakeCategoryRepository()

    private val observeBudgetUseCase = ObserveBudgetUseCase(budgetRepo)

    private val observeBudgetsWithProgressUseCase = ObserveBudgetsWithProgressUseCase(
        budgetRepository = budgetRepo,
        transactionRepository = transactionRepo,
        categoryRepository = categoryRepo,
    )

    private val observeBudgetAlertsUseCase = ObserveBudgetAlertsUseCase(
        observeBudgetsWithProgressUseCase = observeBudgetsWithProgressUseCase,
    )

    private val testMonth = YearMonth("2026-08")
    private val defaultCategory = Category(
        id = EntityId("cat_groceries"),
        ownerId = EntityId("user_1"),
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#4CAF50"),
        icon = null,
        isDefault = false,
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(1700000000000L),
    )

    private fun createBudget(
        id: String = "b_1",
        categoryId: String = "cat_groceries",
        limitMinor: Long = 100_000L, // 1000.00 TRY
        currency: Currency = Currency.TRY,
    ) = Budget(
        id = EntityId(id),
        ownerId = EntityId("user_1"),
        workspaceId = null,
        categoryId = EntityId(categoryId),
        month = testMonth,
        limit = Money(limitMinor, currency),
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(1700000000000L),
    )

    private fun createExpense(
        id: String,
        categoryId: String,
        amountMinor: Long,
        currency: Currency = Currency.TRY,
        date: String = "2026-08-15",
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user_1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Harcama",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate.parse(date),
        receiptPath = null,
        installment = null,
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(1700000000000L),
    )

    @Test
    fun flows_combine_to_produce_correct_progress() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        categoryRepo.categoriesToEmit = listOf(defaultCategory)
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 40_000L), // 400.00 TRY
        )

        val items = observeBudgetsWithProgressUseCase(testMonth).first()

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(defaultCategory, item.category)
        assertEquals(0, item.excludedDifferentCurrencyTransactionCount)
        assertEquals(40_000L, item.progress.spent.amountMinor)
        assertEquals(60_000L, item.progress.remaining.amountMinor)
        assertEquals(RateBasisPoints(4_000), item.progress.usageRate) // 40%
        assertEquals(BudgetHealth.SAFE, item.progress.health)
    }

    @Test
    fun usage_rate_79_99_percent_is_safe() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 79_990L), // 79.99%
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(RateBasisPoints(7_999), item.progress.usageRate)
        assertEquals(BudgetHealth.SAFE, item.progress.health)
    }

    @Test
    fun usage_rate_exact_80_percent_is_warning() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 80_000L), // 80.00%
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(RateBasisPoints(8_000), item.progress.usageRate)
        assertEquals(BudgetHealth.WARNING, item.progress.health)
    }

    @Test
    fun usage_rate_99_99_percent_is_warning() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 99_990L), // 99.99%
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(RateBasisPoints(9_999), item.progress.usageRate)
        assertEquals(BudgetHealth.WARNING, item.progress.health)
    }

    @Test
    fun usage_rate_exact_100_percent_is_exceeded() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 100_000L), // 100.00%
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(RateBasisPoints(10_000), item.progress.usageRate)
        assertEquals(BudgetHealth.EXCEEDED, item.progress.health)
    }

    @Test
    fun usage_rate_above_100_percent_is_exceeded() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_1", "cat_groceries", 120_000L), // 120.00%
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(RateBasisPoints(12_000), item.progress.usageRate)
        assertEquals(BudgetHealth.EXCEEDED, item.progress.health)
    }

    @Test
    fun transactions_for_different_categories_are_ignored() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_other", "cat_other", 50_000L),
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(0L, item.progress.spent.amountMinor)
        assertEquals(0, item.excludedDifferentCurrencyTransactionCount)
    }

    @Test
    fun transactions_for_different_months_are_ignored() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_past", "cat_groceries", 50_000L, date = "2026-07-31"),
            createExpense("tx_future", "cat_groceries", 50_000L, date = "2026-09-01"),
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(0L, item.progress.spent.amountMinor)
    }

    @Test
    fun income_transactions_are_ignored() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_income", "cat_groceries", 50_000L, type = TransactionType.INCOME),
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(0L, item.progress.spent.amountMinor)
    }

    @Test
    fun different_currency_transactions_are_excluded_and_increment_counter() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(limitMinor = 100_000L, currency = Currency.TRY))
        transactionRepo.transactionsToEmit = listOf(
            createExpense("tx_try", "cat_groceries", 30_000L, currency = Currency.TRY),
            createExpense("tx_usd", "cat_groceries", 10_000L, currency = Currency.USD),
            createExpense("tx_eur", "cat_groceries", 10_000L, currency = Currency.EUR),
        )

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertEquals(30_000L, item.progress.spent.amountMinor)
        assertEquals(2, item.excludedDifferentCurrencyTransactionCount)
    }

    @Test
    fun deleted_or_missing_category_is_safely_null() = runTest {
        budgetRepo.budgetsToEmit = listOf(createBudget(categoryId = "cat_deleted"))
        categoryRepo.categoriesToEmit = emptyList() // Category not found in history lookup

        val item = observeBudgetsWithProgressUseCase(testMonth).first().first()

        assertNull(item.category)
        assertEquals(BudgetHealth.SAFE, item.progress.health)
    }

    @Test
    fun budget_list_order_is_preserved() = runTest {
        val b1 = createBudget("b_1", "cat_1")
        val b2 = createBudget("b_2", "cat_2")
        val b3 = createBudget("b_3", "cat_3")
        budgetRepo.budgetsToEmit = listOf(b2, b3, b1)

        val items = observeBudgetsWithProgressUseCase(testMonth).first()

        assertEquals(listOf(EntityId("b_2"), EntityId("b_3"), EntityId("b_1")), items.map { it.progress.budget.id })
    }

    @Test
    fun alert_use_case_filters_safe_and_sorts_exceeded_before_warning() = runTest {
        val bSafe = createBudget("b_safe", "cat_safe", limitMinor = 100_000L)
        val bWarn80 = createBudget("b_warn80", "cat_w80", limitMinor = 100_000L)
        val bWarn90 = createBudget("b_warn90", "cat_w90", limitMinor = 100_000L)
        val bExceed100 = createBudget("b_exceed100", "cat_e100", limitMinor = 100_000L)
        val bExceed150 = createBudget("b_exceed150", "cat_e150", limitMinor = 100_000L)

        budgetRepo.budgetsToEmit = listOf(bSafe, bWarn80, bExceed100, bWarn90, bExceed150)
        transactionRepo.transactionsToEmit = listOf(
            createExpense("t1", "cat_safe", 50_000L), // 50% SAFE
            createExpense("t2", "cat_w80", 80_000L), // 80% WARNING
            createExpense("t3", "cat_w90", 90_000L), // 90% WARNING
            createExpense("t4", "cat_e100", 100_000L), // 100% EXCEEDED
            createExpense("t5", "cat_e150", 150_000L), // 150% EXCEEDED
        )

        val alerts = observeBudgetAlertsUseCase(testMonth).first()

        assertEquals(4, alerts.size)
        // Sıralama: EXCEEDED önce (usageRate azalan), ardından WARNING (usageRate azalan)
        assertEquals(listOf(EntityId("b_exceed150"), EntityId("b_exceed100"), EntityId("b_warn90"), EntityId("b_warn80")), alerts.map { it.progress.budget.id })
    }

    @Test
    fun cancellation_exception_from_source_flow_is_not_swallowed() = runTest {
        budgetRepo.throwCancellation = true

        assertFailsWith<CancellationException> {
            observeBudgetsWithProgressUseCase(testMonth).first()
        }
    }

    @Test
    fun observeBudgetUseCase_passesCorrectIdAndEmitsBudget() = runTest {
        val sampleBudget = createBudget(id = "b_target", categoryId = "cat_groceries")
        budgetRepo.budgetToEmit = sampleBudget

        val emitted = observeBudgetUseCase(EntityId("b_target")).first()

        assertEquals(EntityId("b_target"), budgetRepo.lastObservedBudgetId)
        assertEquals(sampleBudget, emitted)
    }

    @Test
    fun observeBudgetUseCase_emitsNullWhenBudgetNotFoundOrDeleted() = runTest {
        budgetRepo.budgetToEmit = null

        val emitted = observeBudgetUseCase(EntityId("b_nonexistent")).first()

        assertEquals(EntityId("b_nonexistent"), budgetRepo.lastObservedBudgetId)
        assertNull(emitted)
    }

    @Test
    fun observeBudgetUseCase_rethrowsCancellationException() = runTest {
        budgetRepo.throwCancellation = true

        assertFailsWith<CancellationException> {
            observeBudgetUseCase(EntityId("b_target")).first()
        }
    }
}
