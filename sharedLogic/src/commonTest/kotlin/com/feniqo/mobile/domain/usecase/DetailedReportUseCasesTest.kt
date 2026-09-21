package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DetailedReportUseCasesTest {

    private val referenceDate = LocalDate(2026, 9, 17)

    @Test
    fun observeDetailedReportOverview_calculatesTrendAndTopCategory() = runTest {
        val tx1 = createTx("t1", 10_000_00L, Currency.TRY, TransactionType.INCOME, "cat-salary", LocalDate(2026, 9, 5))
        val tx2 = createTx("t2", 2_000_00L, Currency.TRY, TransactionType.EXPENSE, "cat-market", LocalDate(2026, 9, 10))
        val tx3 = createTx("t3", 1_500_00L, Currency.TRY, TransactionType.EXPENSE, "cat-rent", LocalDate(2026, 9, 12))

        val cat1 = Category(EntityId("cat-salary"), EntityId("u1"), null, "Maaş", TransactionType.INCOME, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_salary"), false, Instant.fromEpochMilliseconds(0))
        val cat2 = Category(EntityId("cat-market"), EntityId("u1"), null, "Market", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_cart"), false, Instant.fromEpochMilliseconds(0))
        val cat3 = Category(EntityId("cat-rent"), EntityId("u1"), null, "Kira", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_home"), false, Instant.fromEpochMilliseconds(0))

        val useCase = ObserveDetailedReportOverviewUseCase(
            transactionRepository = FakeTxRepo(listOf(tx1, tx2, tx3)),
            categoryRepository = FakeCategoryRepo(listOf(cat1, cat2, cat3)),
        )

        val result = useCase(ReportFilter(), null, referenceDate).first()

        assertEquals(6, result.trendPoints.size)
        assertNotNull(result.topCategory)
        assertEquals("Market", result.topCategory!!.categoryName)
        assertEquals(2_000_00L, result.topCategory!!.amount.amountMinor)
    }

    @Test
    fun observeCashFlow_calculatesMonthlyPointsAndAverages() = runTest {
        val tx1 = createTx("t1", 5_000_00L, Currency.TRY, TransactionType.INCOME, "cat-1", LocalDate(2026, 9, 1))
        val tx2 = createTx("t2", 3_000_00L, Currency.TRY, TransactionType.EXPENSE, "cat-2", LocalDate(2026, 9, 15))
        val tx3 = createTx("t3", 4_000_00L, Currency.TRY, TransactionType.INCOME, "cat-1", LocalDate(2026, 8, 1))
        val tx4 = createTx("t4", 2_000_00L, Currency.TRY, TransactionType.EXPENSE, "cat-2", LocalDate(2026, 8, 15))

        val useCase = ObserveCashFlowUseCase(FakeTxRepo(listOf(tx1, tx2, tx3, tx4)))
        val summary = useCase(monthCount = 6, currency = Currency.TRY, workspaceId = null, referenceDate = referenceDate).first()

        assertEquals(6, summary.points.size)
        assertTrue(summary.averageIncome.amountMinor > 0)
        assertTrue(summary.averageExpense.amountMinor > 0)
    }

    @Test
    fun observeSpendingCalendar_calculatesDailyTotals() = runTest {
        val tx1 = createTx("t1", 1_200_00L, Currency.TRY, TransactionType.EXPENSE, "c1", LocalDate(2026, 9, 5))
        val tx2 = createTx("t2", 800_00L, Currency.TRY, TransactionType.EXPENSE, "c1", LocalDate(2026, 9, 5))
        val tx3 = createTx("t3", 500_00L, Currency.TRY, TransactionType.EXPENSE, "c2", LocalDate(2026, 9, 12))

        val cat1 = Category(EntityId("c1"), EntityId("u1"), null, "Market", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_cart"), false, Instant.fromEpochMilliseconds(0))
        val cat2 = Category(EntityId("c2"), EntityId("u1"), null, "Ulaşım", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_bus"), false, Instant.fromEpochMilliseconds(0))

        val useCase = ObserveSpendingCalendarUseCase(
            transactionRepository = FakeTxRepo(listOf(tx1, tx2, tx3)),
            categoryRepository = FakeCategoryRepo(listOf(cat1, cat2)),
        )

        val summary = useCase(YearMonth.from(2026, 9), Currency.TRY, null).first()

        assertEquals(30, summary.days.size)
        assertEquals(2_500_00L, summary.totalMonthExpense.amountMinor)
        assertEquals(LocalDate(2026, 9, 5), summary.highestExpenseDay)

        val day5 = summary.days.find { it.date == LocalDate(2026, 9, 5) }
        assertNotNull(day5)
        assertEquals(2_000_00L, day5.expense.amountMinor)
        assertEquals(2, day5.transactionCount)
    }

    @Test
    fun observeBudgetPerformance_calculatesOnTrackAndExceeded() = runTest {
        val b1 = Budget(EntityId("b1"), EntityId("u1"), null, EntityId("c1"), YearMonth.from(2026, 9), Money(5_000_00L, Currency.TRY), Instant.fromEpochMilliseconds(0))
        val b2 = Budget(EntityId("b2"), EntityId("u1"), null, EntityId("c2"), YearMonth.from(2026, 9), Money(2_000_00L, Currency.TRY), Instant.fromEpochMilliseconds(0))

        val tx1 = createTx("t1", 4_000_00L, Currency.TRY, TransactionType.EXPENSE, "c1", LocalDate(2026, 9, 5))
        val tx2 = createTx("t2", 2_500_00L, Currency.TRY, TransactionType.EXPENSE, "c2", LocalDate(2026, 9, 8)) // exceeded

        val cat1 = Category(EntityId("c1"), EntityId("u1"), null, "Market", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_cart"), false, Instant.fromEpochMilliseconds(0))
        val cat2 = Category(EntityId("c2"), EntityId("u1"), null, "Eğlence", TransactionType.EXPENSE, com.feniqo.mobile.domain.model.CategoryColor("#2D5A43"), com.feniqo.mobile.domain.model.CategoryIcon("ic_game"), false, Instant.fromEpochMilliseconds(0))

        val useCase = ObserveBudgetPerformanceReportUseCase(
            budgetRepository = FakeBudgetRepo(listOf(b1, b2)),
            transactionRepository = FakeTxRepo(listOf(tx1, tx2)),
            categoryRepository = FakeCategoryRepo(listOf(cat1, cat2)),
        )

        val perf = useCase(YearMonth.from(2026, 9), Currency.TRY, null).first()

        assertEquals(7_000_00L, perf.totalBudget.amountMinor)
        assertEquals(6_500_00L, perf.totalSpent.amountMinor)
        assertEquals(1, perf.budgetsOnTrackCount)
        assertEquals(1, perf.budgetsExceededCount)
    }

    @Test
    fun observeSubscriptionSummary_calculatesMonthlyTotalAndUpcoming() = runTest {
        val s1 = createSub("s1", "Netflix", 229_00L, Currency.TRY, LocalDate(2026, 9, 20))
        val s2 = createSub("s2", "Spotify", 60_00L, Currency.TRY, LocalDate(2026, 9, 23))

        val useCase = ObserveSubscriptionSummaryReportUseCase(
            subscriptionRepository = FakeSubRepo(listOf(s1, s2)),
            categoryRepository = FakeCategoryRepo(emptyList()),
        )

        val summary = useCase(Currency.TRY).first()

        assertEquals(2, summary.activeSubscriptionCount)
        assertEquals(289_00L, summary.totalMonthlyEstimate.amountMinor)
        assertEquals(2, summary.upcomingPayments.size)
        assertEquals("Netflix", summary.upcomingPayments[0].name)
    }

    @Test
    fun observeDebtSummary_calculatesRemainingAndNet() = runTest {
        val d1 = Debt(EntityId("d1"), EntityId("u1"), null, "Banka", Money(10_000_00L, Currency.TRY), DebtType.DEBT, LocalDate(2026, 9, 25), DebtStatus.OPEN, null, Instant.fromEpochMilliseconds(0))
        val d2 = Debt(EntityId("d2"), EntityId("u1"), null, "Ahmet", Money(4_000_00L, Currency.TRY), DebtType.RECEIVABLE, LocalDate(2026, 9, 28), DebtStatus.OPEN, null, Instant.fromEpochMilliseconds(0))

        val useCase = ObserveDebtSummaryReportUseCase(FakeDebtRepo(listOf(d1, d2)))
        val summary = useCase(Currency.TRY, referenceDate).first()

        assertEquals(10_000_00L, summary.totalDebtRemaining.amountMinor)
        assertEquals(4_000_00L, summary.totalReceivableRemaining.amountMinor)
        assertEquals(-6_000_00L, summary.netBalance.amountMinor)
        assertEquals(2, summary.upcomingDeadlines.size)
    }

    private fun createTx(
        id: String,
        amountMinor: Long,
        currency: Currency,
        type: TransactionType,
        categoryId: String,
        date: LocalDate,
    ): Transaction = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Test transaction $id",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = null,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun createSub(
        id: String,
        name: String,
        amountMinor: Long,
        currency: Currency,
        renewalDate: LocalDate,
    ): Subscription = Subscription(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        amount = Money(amountMinor, currency),
        categoryId = null,
        renewalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        ),
        nextRenewalDate = renewalDate,
        isActive = true,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private class FakeTxRepo(private val transactions: List<Transaction>) : TransactionRepository {
        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
            var res = transactions
            if (filter.period != null) {
                res = res.filter { it.transactionDate >= filter.period!!.startDate && it.transactionDate <= filter.period!!.endDate }
            }
            if (filter.type != null) {
                res = res.filter { it.type == filter.type }
            }
            if (filter.categoryId != null) {
                res = res.filter { it.categoryId == filter.categoryId }
            }
            return flowOf(res)
        }
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(transactions.find { it.id == id })
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("g1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepo(private val categories: List<Category>) : CategoryRepository {
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = flowOf(categories)
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(categories.find { it.id == id })
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flowOf(categories)
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeBudgetRepo(private val budgets: List<Budget>) : BudgetRepository {
        override fun observeBudgets(month: YearMonth, workspaceId: EntityId?): Flow<List<Budget>> =
            flowOf(budgets.filter { it.month == month })
        override fun observeBudget(id: EntityId): Flow<Budget?> = flowOf(budgets.find { it.id == id })
        override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("b"))
        override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> =
            RepositoryResult.Success(CopyBudgetsResult(0, emptyList()))
    }

    private class FakeSubRepo(private val subs: List<Subscription>) : SubscriptionRepository {
        override fun observeSubscriptions(): Flow<List<Subscription>> = flowOf(subs)
        override fun observeSubscription(id: EntityId): Flow<Subscription?> = flowOf(subs.find { it.id == id })
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateSubscriptionCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("s"))
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateSubscriptionCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun setActive(command: com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult> =
            RepositoryResult.Failure(com.feniqo.mobile.domain.model.AppError.Unknown("sub"))
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeDebtRepo(private val debts: List<Debt>) : DebtRepository {
        override fun observeDebts(): Flow<List<Debt>> = flowOf(debts)
        override fun observeDebt(id: EntityId): Flow<Debt?> = flowOf(debts.find { it.id == id })
        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flowOf(emptyList())
        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateDebtCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("d"))
        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateDebtCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun addPayment(command: com.feniqo.mobile.domain.model.AddDebtPaymentCommand): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("p"))
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
