package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DashboardSummary
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CoreUseCasesTest {

    @Test
    fun add_transaction_assigns_owner_from_session_and_normalizes_description() = runTest {
        val categoryRepository = FakeCategoryRepository(listOf(expenseCategory()))
        val transactionRepository = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(authRepository(), categoryRepository, transactionRepository)

        val result = useCase(transactionCommand(description = "  Market  "), TODAY, NOW)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        val saved = transactionRepository.observeTransaction(TRANSACTION_ID).first()
        assertEquals(USER_ID, saved?.ownerId)
        assertEquals("Market", saved?.description)
    }

    @Test
    fun add_transaction_rejects_a_future_date_before_repository_write() = runTest {
        val transactionRepository = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(
            authRepository(),
            FakeCategoryRepository(listOf(expenseCategory())),
            transactionRepository,
        )

        val result = useCase(
            transactionCommand(transactionDate = LocalDate(2026, 8, 6)),
            TODAY,
            NOW,
        )

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_date_cannot_be_future", failure.error.code)
        assertNull(transactionRepository.observeTransaction(TRANSACTION_ID).first())
    }

    @Test
    fun update_transaction_preserves_owner_and_creation_time() = runTest {
        val existing = transaction(description = "Eski")
        val transactionRepository = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(
            authRepository(),
            FakeCategoryRepository(listOf(expenseCategory())),
            transactionRepository,
        )

        val result = useCase(transactionCommand(description = "Yeni"), TODAY)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val updated = transactionRepository.observeTransaction(TRANSACTION_ID).first()
        assertEquals(USER_ID, updated?.ownerId)
        assertEquals(NOW, updated?.createdAt)
        assertEquals("Yeni", updated?.description)
    }

    @Test
    fun delete_transaction_uses_soft_delete_contract() = runTest {
        val transactionRepository = FakeTransactionRepository(listOf(transaction()))
        val useCase = DeleteTransactionUseCase(authRepository(), transactionRepository)

        val result = useCase(TRANSACTION_ID)

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals(TRANSACTION_ID, transactionRepository.lastSoftDeletedId)
    }

    @Test
    fun add_transaction_with_custom_category_in_same_workspace_succeeds() = runTest {
        val wsId = EntityId("ws-1")
        val cat = expenseCategory().copy(workspaceId = wsId)
        val catRepo = FakeCategoryRepository(listOf(cat))
        val trxRepo = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = wsId), TODAY, NOW)
        assertIs<RepositoryResult.Success<EntityId>>(result)
    }

    @Test
    fun add_transaction_with_custom_category_in_different_workspace_fails_with_category_workspace_mismatch() = runTest {
        val cat = expenseCategory().copy(workspaceId = EntityId("ws-other"))
        val catRepo = FakeCategoryRepository(listOf(cat))
        val trxRepo = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = EntityId("ws-1")), TODAY, NOW)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_workspace_mismatch", failure.error.code)
    }

    @Test
    fun add_transaction_with_default_category_succeeds_in_any_workspace() = runTest {
        val defaultCat = expenseCategory().copy(ownerId = null, workspaceId = null, isDefault = true)
        val catRepo = FakeCategoryRepository(listOf(defaultCat))
        val trxRepo = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = EntityId("ws-any")), TODAY, NOW)
        assertIs<RepositoryResult.Success<EntityId>>(result)
    }

    @Test
    fun update_transaction_with_custom_category_in_same_workspace_succeeds() = runTest {
        val wsId = EntityId("ws-1")
        val cat = expenseCategory().copy(workspaceId = wsId)
        val existing = transaction().copy(workspaceId = wsId)
        val catRepo = FakeCategoryRepository(listOf(cat))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = wsId), TODAY)
        assertIs<RepositoryResult.Success<Unit>>(result)
    }

    @Test
    fun update_transaction_with_custom_category_in_different_workspace_fails_with_category_workspace_mismatch() = runTest {
        val cat = expenseCategory().copy(workspaceId = EntityId("ws-other"))
        val existing = transaction().copy(workspaceId = EntityId("ws-1"))
        val catRepo = FakeCategoryRepository(listOf(cat))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = EntityId("ws-1")), TODAY)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_workspace_mismatch", failure.error.code)
    }

    @Test
    fun update_transaction_with_default_category_succeeds_in_any_workspace() = runTest {
        val defaultCat = expenseCategory().copy(ownerId = null, workspaceId = null, isDefault = true)
        val existing = transaction().copy(workspaceId = EntityId("ws-any"))
        val catRepo = FakeCategoryRepository(listOf(defaultCat))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = EntityId("ws-any")), TODAY)
        assertIs<RepositoryResult.Success<Unit>>(result)
    }

    @Test
    fun add_transaction_creates_with_null_installment() = runTest {
        val catRepo = FakeCategoryRepository(listOf(expenseCategory()))
        val trxRepo = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand(), TODAY, NOW)
        assertIs<RepositoryResult.Success<EntityId>>(result)

        val created = trxRepo.observeTransaction(TRANSACTION_ID).first()
        assertNull(created?.installment)
    }

    @Test
    fun update_transaction_when_workspace_different_fails_with_transaction_workspace_immutable_and_does_not_call_repo() = runTest {
        val existing = transaction().copy(workspaceId = EntityId("ws-initial"))
        val catRepo = FakeCategoryRepository(listOf(expenseCategory().copy(workspaceId = EntityId("ws-initial"))))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand().copy(workspaceId = EntityId("ws-different")), TODAY)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_workspace_immutable", failure.error.code)

        // Verify repository update was not called
        val current = trxRepo.observeTransaction(TRANSACTION_ID).first()
        assertEquals(EntityId("ws-initial"), current?.workspaceId)
    }

    @Test
    fun update_transaction_preserves_installment_metadata_and_immutable_fields() = runTest {
        val instInfo = InstallmentInfo(1, 3, EntityId("grp-1"))
        val existing = transaction(description = "Eski").copy(
            installment = instInfo,
            workspaceId = EntityId("ws-1"),
        )
        val catRepo = FakeCategoryRepository(listOf(expenseCategory().copy(workspaceId = EntityId("ws-1"))))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand(description = "Yeni").copy(workspaceId = EntityId("ws-1")), TODAY)
        assertIs<RepositoryResult.Success<Unit>>(result)

        val updated = trxRepo.observeTransaction(TRANSACTION_ID).first()
        // Immutable fields preserved
        assertEquals(TRANSACTION_ID, updated?.id)
        assertEquals(USER_ID, updated?.ownerId)
        assertEquals(EntityId("ws-1"), updated?.workspaceId)
        assertEquals(NOW, updated?.createdAt)
        assertEquals(instInfo, updated?.installment)
        // Mutable fields updated
        assertEquals("Yeni", updated?.description)
    }

    @Test
    fun update_transaction_preserves_null_installment_for_regular_transaction() = runTest {
        val existing = transaction().copy(installment = null)
        val catRepo = FakeCategoryRepository(listOf(expenseCategory()))
        val trxRepo = FakeTransactionRepository(listOf(existing))
        val useCase = UpdateTransactionUseCase(authRepository(), catRepo, trxRepo)

        val result = useCase(transactionCommand(description = "Güncel"), TODAY)
        assertIs<RepositoryResult.Success<Unit>>(result)

        val updated = trxRepo.observeTransaction(TRANSACTION_ID).first()
        assertNull(updated?.installment)
        assertEquals("Güncel", updated?.description)
    }

    @Test
    fun observe_transactions_forwards_the_filter_to_repository() = runTest {
        val transactionRepository = FakeTransactionRepository(listOf(transaction()))
        val useCase = ObserveTransactionsUseCase(transactionRepository)
        val filter = TransactionFilter(type = TransactionType.EXPENSE, query = "market")

        val items = useCase(filter).first()

        assertEquals(filter, transactionRepository.lastFilter)
        assertEquals(1, items.size)
    }

    @Test
    fun add_category_trims_name_and_assigns_session_owner() = runTest {
        val categoryRepository = FakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository(), categoryRepository)

        val result = useCase(
            AddCategoryCommand(
                id = CATEGORY_ID,
                workspaceId = null,
                name = "  Market  ",
                type = TransactionType.EXPENSE,
                colorHex = "#0A7A55",
                icon = null,
            ),
            NOW,
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        val saved = categoryRepository.observeCategory(CATEGORY_ID).first()
        assertEquals("Market", saved?.name)
        assertEquals(USER_ID, saved?.ownerId)
    }

    @Test
    fun budget_progress_marks_an_exceeded_limit_and_calculates_remaining_amount() {
        val budget = Budget(
            id = EntityId("budget-1"),
            ownerId = USER_ID,
            workspaceId = null,
            categoryId = CATEGORY_ID,
            month = YearMonth("2026-08"),
            limit = Money(10_000, Currency.TRY),
            createdAt = NOW,
        )
        val progress = CalculateBudgetProgressUseCase()(
            budget,
            listOf(transaction(amountMinor = 12_000)),
        )

        assertEquals(BudgetHealth.EXCEEDED, progress.health)
        assertEquals(12_000, progress.spent.amountMinor)
        assertEquals(-2_000, progress.remaining.amountMinor)
        assertEquals(12_000, progress.usageRate.value)
    }

    @Test
    fun money_score_uses_the_declared_thirty_thirty_twenty_twenty_weights() {
        val score = CalculateMoneyScoreUseCase()(
            MoneyScoreInput(
                income = Money(10_000, Currency.TRY),
                expense = Money(7_500, Currency.TRY),
                budgets = emptyList(),
                transactions = emptyList(),
                debts = emptyList(),
                goals = emptyList(),
                today = TODAY,
            ),
        )

        assertEquals(75, score.total)
        assertEquals(MoneyScoreLevel.HEALTHY, score.level)
    }

    @Test
    fun dashboard_summary_is_emitted_from_the_local_repository_flow() = runTest {
        val income = transaction(
            id = EntityId("income-1"),
            amountMinor = 20_000,
            type = TransactionType.INCOME,
            categoryId = EntityId("salary-category"),
        )
        val expense = transaction(amountMinor = 7_500)
        val useCase = ObserveDashboardSummaryUseCase(
            FakeTransactionRepository(listOf(income, expense)),
        )

        val summary: DashboardSummary = useCase(YearMonth("2026-08"), Currency.TRY).first()

        assertEquals(20_000, summary.income.amountMinor)
        assertEquals(7_500, summary.expense.amountMinor)
        assertEquals(12_500, summary.balance.amountMinor)
        assertEquals(CATEGORY_ID, summary.topExpenseCategory?.categoryId)
    }

    private fun transactionCommand(
        description: String? = "Market",
        transactionDate: LocalDate = TODAY,
    ) = TransactionCommand(
        id = TRANSACTION_ID,
        workspaceId = null,
        amount = Money(1_000, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = CATEGORY_ID,
        description = description,
        paymentMethod = PaymentMethod.CASH,
        transactionDate = transactionDate,
        receiptPath = null,
    )

    private fun transaction(
        id: EntityId = TRANSACTION_ID,
        description: String? = "Market",
        amountMinor: Long = 1_000,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: EntityId = CATEGORY_ID,
    ) = Transaction(
        id = id,
        ownerId = USER_ID,
        workspaceId = null,
        amount = Money(amountMinor, Currency.TRY),
        type = type,
        categoryId = categoryId,
        description = description,
        paymentMethod = PaymentMethod.CASH,
        transactionDate = TODAY,
        receiptPath = null,
        installment = null,
        createdAt = NOW,
    )

    private fun expenseCategory() = Category(
        id = CATEGORY_ID,
        ownerId = USER_ID,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#0A7A55"),
        icon = null,
        isDefault = false,
        createdAt = NOW,
    )

    private fun authRepository(): AuthRepository = FakeAuthRepository(
        AuthSession(USER_ID, "user@feniqo.com", Instant.parse("2026-08-06T00:00:00Z")),
    )

    private companion object {
        val USER_ID = EntityId("user-1")
        val CATEGORY_ID = EntityId("category-1")
        val TRANSACTION_ID = EntityId("transaction-1")
        val TODAY = LocalDate(2026, 8, 5)
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
    }
}

private class FakeAuthRepository(session: AuthSession?) : AuthRepository {
    private val sessionFlow = MutableStateFlow(session)

    override fun observeSession(): Flow<AuthSession?> = sessionFlow
    override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, fullName: String?) =
        RepositoryResult.Success(EntityId("user-1"))
    override suspend fun refreshSession() = RepositoryResult.Success(Unit)
    override suspend fun signOut() = RepositoryResult.Success(Unit)
}

private class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val categories = MutableStateFlow(initial)

    override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categories
    override fun observeCategory(id: EntityId): Flow<Category?> = categories.map { list -> list.firstOrNull { it.id == id } }
    override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categories
    override suspend fun create(category: Category): RepositoryResult<EntityId> {
        categories.value = categories.value + category
        return RepositoryResult.Success(category.id)
    }
    override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

private class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    private val transactions = MutableStateFlow(initial)
    var lastFilter: TransactionFilter? = null
        private set
    var lastSoftDeletedId: EntityId? = null
        private set

    override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
        lastFilter = filter
        return transactions
    }

    override fun observeTransaction(id: EntityId): Flow<Transaction?> =
        transactions.map { list -> list.firstOrNull { it.id == id } }

    override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
        transactions.map { list -> list.filter { it.installment?.groupId == groupId } }

    override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
        transactions.value = transactions.value + transaction
        return RepositoryResult.Success(transaction.id)
    }

    override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> {
        this.transactions.value = this.transactions.value + transactions
        return RepositoryResult.Success(transactions.first().installment?.groupId ?: EntityId("group-1"))
    }

    override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
        transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
        lastSoftDeletedId = id
        transactions.value = transactions.value.filterNot { it.id == id }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
        transactions.value = transactions.value.filterNot { it.id in ids }
        return RepositoryResult.Success(Unit)
    }
}
