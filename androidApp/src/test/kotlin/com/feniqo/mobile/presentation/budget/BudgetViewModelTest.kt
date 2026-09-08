package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.domain.usecase.CalculateBudgetProgressUseCase
import com.feniqo.mobile.domain.usecase.CopyBudgetsUseCase
import com.feniqo.mobile.domain.usecase.CreateBudgetUseCase
import com.feniqo.mobile.domain.usecase.DeleteBudgetUseCase
import com.feniqo.mobile.domain.usecase.ObserveBudgetsWithProgressUseCase
import com.feniqo.mobile.domain.usecase.UpdateBudgetUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate(2026, 8, 29)
    private val testDateProvider = CurrentDateProvider { fixedToday }

    private class FakeBudgetRepository : BudgetRepository {
        val budgetsFlow = MutableStateFlow<Map<YearMonth, List<Budget>>>(emptyMap())
        var shouldThrowOnObserve: Throwable? = null
        var shouldThrowOnCreate: Throwable? = null
        var shouldThrowOnDelete: Throwable? = null
        var onDeleteAction: (suspend () -> Unit)? = null
        var shouldThrowOnCopy: Throwable? = null
        var onCopyAction: (suspend () -> Unit)? = null
        var lastObservedMonth: YearMonth? = null

        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("b1"))
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var copyResult: RepositoryResult<CopyBudgetsResult> = RepositoryResult.Success(CopyBudgetsResult(2))

        var lastCreateCommand: CreateBudgetCommand? = null
        var lastUpdateCommand: UpdateBudgetCommand? = null
        var lastDeleteId: EntityId? = null
        var lastCopyCommand: CopyBudgetsCommand? = null

        val singleBudgetFlow = MutableStateFlow<Budget?>(null)
        var shouldThrowOnObserveSingle: Throwable? = null
        var lastObservedBudgetId: EntityId? = null

        override fun observeBudgets(month: YearMonth, workspaceId: EntityId?): Flow<List<Budget>> = flow {
            lastObservedMonth = month
            shouldThrowOnObserve?.let { throw it }
            budgetsFlow.collect { map ->
                emit(map[month] ?: emptyList())
            }
        }

        override fun observeBudget(id: EntityId): Flow<Budget?> = flow {
            lastObservedBudgetId = id
            shouldThrowOnObserveSingle?.let { throw it }
            singleBudgetFlow.collect { emit(it) }
        }

        override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> {
            shouldThrowOnCreate?.let { throw it }
            lastCreateCommand = command
            return createResult
        }

        override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            shouldThrowOnDelete?.let { throw it }
            onDeleteAction?.invoke()
            lastDeleteId = id
            return deleteResult
        }

        override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> {
            shouldThrowOnCopy?.let { throw it }
            onCopyAction?.invoke()
            lastCopyCommand = command
            return copyResult
        }
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactionsFlow
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("t1"))
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("grp1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override fun observeCategory(id: EntityId): Flow<Category?> = flow {
            val cat = categoriesFlow.value.find { it.id == id }
            emit(cat)
        }
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("c1"))
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private val fakeBudgetRepo = FakeBudgetRepository()
    private val fakeTransactionRepo = FakeTransactionRepository()
    private val fakeCategoryRepo = FakeCategoryRepository()

    private val observeBudgetsWithProgressUseCase = ObserveBudgetsWithProgressUseCase(
        budgetRepository = fakeBudgetRepo,
        transactionRepository = fakeTransactionRepo,
        categoryRepository = fakeCategoryRepo,
        calculator = CalculateBudgetProgressUseCase(),
    )
    private val observeBudgetUseCase = com.feniqo.mobile.domain.usecase.ObserveBudgetUseCase(fakeBudgetRepo)
    private val createBudgetUseCase = CreateBudgetUseCase(fakeCategoryRepo, fakeBudgetRepo)
    private val updateBudgetUseCase = UpdateBudgetUseCase(fakeBudgetRepo)
    private val deleteBudgetUseCase = DeleteBudgetUseCase(fakeBudgetRepo)
    private val copyBudgetsUseCase = CopyBudgetsUseCase(fakeBudgetRepo)

    private val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
    private val observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo)

    private fun createViewModel(): BudgetViewModel = BudgetViewModel(
        observeBudgetsWithProgressUseCase = observeBudgetsWithProgressUseCase,
        observeBudgetUseCase = observeBudgetUseCase,
        createBudgetUseCase = createBudgetUseCase,
        updateBudgetUseCase = updateBudgetUseCase,
        deleteBudgetUseCase = deleteBudgetUseCase,
        copyBudgetsUseCase = copyBudgetsUseCase,
        currentDateProvider = testDateProvider,
        observeActiveWorkspaceUseCase = observeActiveWorkspaceUseCase,
    )

    private val testOwnerId = EntityId("user-1")
    private val testInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(1724932800000L)

    @Test
    fun initialState_beforeCollection_hasValidSelectedMonthFromDateProviderAndLoadingIsTrue() {
        val viewModel = createViewModel()
        val initialUiState = viewModel.uiState.value

        assertEquals(YearMonth("2026-08"), viewModel.initialSelectedMonth)
        assertTrue(initialUiState.isLoading)
        assertEquals(YearMonth("2026-08"), initialUiState.selectedMonth)
        assertTrue(initialUiState.budgets.isEmpty())
        assertNull(initialUiState.observationError)
    }

    @Test
    fun initialState_usesInitialMonthFromDateProviderAndLoadsData() = runTest {
        val catId = EntityId("cat-1")
        val category = Category(
            id = catId,
            ownerId = testOwnerId,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = CategoryIcon("cart"),
            isDefault = false,
            createdAt = testInstant,
        )
        fakeCategoryRepo.categoriesFlow.value = listOf(category)

        val budgetAug = Budget(
            id = EntityId("b-aug"),
            ownerId = testOwnerId,
            workspaceId = null,
            categoryId = catId,
            month = YearMonth("2026-08"),
            limit = Money(100_000, Currency.TRY),
            createdAt = testInstant,
        )
        fakeBudgetRepo.budgetsFlow.value = mapOf(YearMonth("2026-08") to listOf(budgetAug))

        val tx = Transaction(
            id = EntityId("t-1"),
            ownerId = testOwnerId,
            workspaceId = null,
            amount = Money(40_000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = catId,
            description = "Harcama",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 8, 15),
            receiptPath = null,
            installment = null,
            createdAt = testInstant,
        )
        fakeTransactionRepo.transactionsFlow.value = listOf(tx)

        val viewModel = createViewModel()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(YearMonth("2026-08"), state.selectedMonth)
        assertEquals(1, state.budgets.size)
        assertEquals("Market", state.budgets[0].categoryName)
        assertEquals("1.000,00 ₺", state.budgets[0].formattedLimit)
        assertEquals("400,00 ₺", state.budgets[0].formattedSpent)
        assertEquals("600,00 ₺", state.budgets[0].formattedRemaining)
        assertFalse(state.isEmpty)
        assertNull(state.observationError)

        collectJob.cancel()
    }

    @Test
    fun selectMonth_reactivelySwitchesToNewMonth() = runTest {
        val catId = EntityId("cat-1")
        val category = Category(
            id = catId,
            ownerId = testOwnerId,
            workspaceId = null,
            name = "Giyim",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#3B82F6"),
            icon = null,
            isDefault = false,
            createdAt = testInstant,
        )
        fakeCategoryRepo.categoriesFlow.value = listOf(category)

        val budgetAug = Budget(
            id = EntityId("b-aug"),
            ownerId = testOwnerId,
            workspaceId = null,
            categoryId = catId,
            month = YearMonth("2026-08"),
            limit = Money(50_000, Currency.TRY),
            createdAt = testInstant,
        )
        val budgetSep = Budget(
            id = EntityId("b-sep"),
            ownerId = testOwnerId,
            workspaceId = null,
            categoryId = catId,
            month = YearMonth("2026-09"),
            limit = Money(80_000, Currency.TRY),
            createdAt = testInstant,
        )
        fakeBudgetRepo.budgetsFlow.value = mapOf(
            YearMonth("2026-08") to listOf(budgetAug),
            YearMonth("2026-09") to listOf(budgetSep),
        )

        val viewModel = createViewModel()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertEquals(YearMonth("2026-08"), viewModel.uiState.value.selectedMonth)
        assertEquals(50_000L, viewModel.uiState.value.budgets[0].limitMinor)

        viewModel.processIntent(BudgetIntent.SelectMonth(YearMonth("2026-09")))

        val newState = viewModel.uiState.value
        assertEquals(YearMonth("2026-09"), newState.selectedMonth)
        assertEquals(1, newState.budgets.size)
        assertEquals(80_000L, newState.budgets[0].limitMinor)
        assertEquals("800,00 ₺", newState.budgets[0].formattedLimit)

        collectJob.cancel()
    }

    @Test
    fun createBudget_success_emitsEventAndClearsSubmitting() = runTest {
        val catId = EntityId("cat-expense")
        val expenseCategory = Category(
            id = catId,
            ownerId = testOwnerId,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = testInstant,
        )
        fakeCategoryRepo.categoriesFlow.value = listOf(expenseCategory)

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.CreateBudget(
                categoryId = catId,
                month = YearMonth("2026-08"),
                limitInput = "1500,00",
                currency = Currency.TRY,
            ),
        )
        advanceUntilIdle()

        assertNotNull(fakeBudgetRepo.lastCreateCommand)
        assertEquals(catId, fakeBudgetRepo.lastCreateCommand?.categoryId)
        assertEquals(YearMonth("2026-08"), fakeBudgetRepo.lastCreateCommand?.month)
        assertEquals(150_000L, fakeBudgetRepo.lastCreateCommand?.limit?.amountMinor)

        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.BUDGET_SAVED, (events[0] as BudgetUiEvent.MutationSuccess).message)

        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertNull(viewModel.uiState.value.mutationState.categoryError)
        assertNull(viewModel.uiState.value.mutationState.amountError)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun createBudget_validationFailure_setsFieldErrorsAndDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.processIntent(
            BudgetIntent.CreateBudget(
                categoryId = null,
                month = null,
                limitInput = "",
            ),
        )
        advanceUntilIdle()

        assertNull(fakeBudgetRepo.lastCreateCommand)
        val mutationState = viewModel.uiState.value.mutationState
        assertEquals(BudgetFormFieldError.CATEGORY_REQUIRED, mutationState.categoryError)
        assertEquals(BudgetFormFieldError.MONTH_REQUIRED, mutationState.monthError)
        assertEquals(BudgetFormFieldError.AMOUNT_REQUIRED, mutationState.amountError)
        assertFalse(mutationState.isSubmitting)

        uiStateJob.cancel()
    }

    @Test
    fun createBudget_expenseInvariant_failsWhenCategoryIsIncome_emitsShowMessageOnly() = runTest {
        val incomeCatId = EntityId("cat-income")
        val incomeCategory = Category(
            id = incomeCatId,
            ownerId = testOwnerId,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = testInstant,
        )
        fakeCategoryRepo.categoriesFlow.value = listOf(incomeCategory)

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.CreateBudget(
                categoryId = incomeCatId,
                month = YearMonth("2026-08"),
                limitInput = "1000",
            ),
        )
        advanceUntilIdle()

        assertNull(fakeBudgetRepo.lastCreateCommand)
        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.ShowMessage)
        assertEquals(FinanceUiMessage.CATEGORY_TYPE_MISMATCH, (events[0] as BudgetUiEvent.ShowMessage).message)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun createBudget_cancellation_rethrowsAndDoesNotEmitGenericError() = runTest {
        val catId = EntityId("cat-expense")
        val expenseCategory = Category(
            id = catId,
            ownerId = testOwnerId,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = testInstant,
        )
        fakeCategoryRepo.categoriesFlow.value = listOf(expenseCategory)
        fakeBudgetRepo.shouldThrowOnCreate = CancellationException("Create cancelled")

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.CreateBudget(
                categoryId = catId,
                month = YearMonth("2026-08"),
                limitInput = "1000",
            ),
        )
        advanceUntilIdle()

        // CancellationException yakalanıp generic ShowMessage üretilmemeli
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun updateBudget_success_emitsEvent() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.UpdateBudget(
                id = EntityId("b-1"),
                limitInput = "2500,50",
            ),
        )
        advanceUntilIdle()

        assertNotNull(fakeBudgetRepo.lastUpdateCommand)
        assertEquals(EntityId("b-1"), fakeBudgetRepo.lastUpdateCommand?.id)
        assertEquals(250_050L, fakeBudgetRepo.lastUpdateCommand?.limit?.amountMinor)

        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.BUDGET_SAVED, (events[0] as BudgetUiEvent.MutationSuccess).message)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun updateBudget_invalidAmount_setsAmountError() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        // 1. Negatif / geçersiz format
        viewModel.processIntent(
            BudgetIntent.UpdateBudget(
                id = EntityId("b-1"),
                limitInput = "-50",
            ),
        )
        advanceUntilIdle()
        assertNull(fakeBudgetRepo.lastUpdateCommand)
        assertEquals(BudgetFormFieldError.AMOUNT_INVALID_FORMAT, viewModel.uiState.value.mutationState.amountError)

        // 2. Sıfır tutar
        viewModel.processIntent(
            BudgetIntent.UpdateBudget(
                id = EntityId("b-1"),
                limitInput = "0",
            ),
        )
        advanceUntilIdle()
        assertNull(fakeBudgetRepo.lastUpdateCommand)
        assertEquals(BudgetFormFieldError.AMOUNT_NON_POSITIVE, viewModel.uiState.value.mutationState.amountError)

        uiStateJob.cancel()
    }

    private val testProgressModel = BudgetProgressDisplayModel(
        id = EntityId("b-dialog-test"),
        categoryId = EntityId("cat-1"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-cart",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "1.000,00 ₺",
        limitMinor = 100_000L,
        formattedSpent = "500,00 ₺",
        spentMinor = 50_000L,
        formattedRemaining = "500,00 ₺",
        remainingMinor = 50_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 5_000,
        formattedUsageRate = "%50,00",
        usageProgressFraction = 0.5f,
        health = BudgetHealth.SAFE,
        excludedDifferentCurrencyTransactionCount = 0,
    )

    @Test
    fun requestDelete_and_dismissDelete_updatesDeleteConfirmationState_andDoesNotCallRepository() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertNull(viewModel.uiState.value.deleteConfirmation)
        assertNull(fakeBudgetRepo.lastDeleteId)

        viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel))
        assertEquals(testProgressModel, viewModel.uiState.value.deleteConfirmation?.target)
        assertFalse(viewModel.uiState.value.deleteConfirmation?.isDeleting == true)
        // RequestDelete aşamasında repository silme çağrısı yapılmamalıdır
        assertNull(fakeBudgetRepo.lastDeleteId)

        viewModel.processIntent(BudgetIntent.DismissDelete)
        assertNull(viewModel.uiState.value.deleteConfirmation)
        // Dismiss sonrasında da repository silme çağrısı yapılmamalıdır
        assertNull(fakeBudgetRepo.lastDeleteId)

        uiStateJob.cancel()
    }

    @Test
    fun confirmDelete_success_emitsMutationSuccess_andClearsDialog() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        // Başlangıçta ve Request sonrasında repository çağrılmamalıdır
        assertNull(fakeBudgetRepo.lastDeleteId)
        viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel))
        assertEquals(testProgressModel, viewModel.uiState.value.deleteConfirmation?.target)
        assertNull(fakeBudgetRepo.lastDeleteId)

        // Yalnız ConfirmDelete sonrasında repository çağrılmalıdır
        viewModel.processIntent(BudgetIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(EntityId("b-dialog-test"), fakeBudgetRepo.lastDeleteId)
        assertNull(viewModel.uiState.value.deleteConfirmation)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.BUDGET_DELETED, (events[0] as BudgetUiEvent.MutationSuccess).message)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmDelete_failure_emitsShowMessage_andClearsDialog() = runTest {
        fakeBudgetRepo.deleteResult = RepositoryResult.Failure(AppError.Storage("db_error"))
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel))
        viewModel.processIntent(BudgetIntent.ConfirmDelete)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteConfirmation)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.ShowMessage)
        assertEquals(FinanceUiMessage.STORAGE_ERROR, (events[0] as BudgetUiEvent.ShowMessage).message)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmDelete_cancellation_rethrowsAndDoesNotEmitGenericError() = runTest {
        fakeBudgetRepo.shouldThrowOnDelete = CancellationException("Confirm delete cancelled")
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel))
        viewModel.processIntent(BudgetIntent.ConfirmDelete)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmDelete_duplicateConfirmOrDismiss_ignoredWhileDeleting() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        var wasDeletingObserved = false
        var dismissAttemptedWhileDeleting = false
        fakeBudgetRepo.onDeleteAction = {
            wasDeletingObserved = viewModel.uiState.value.deleteConfirmation?.isDeleting == true
            // Silme sürerken duplicate confirm, dismiss veya başka request isteği etkisiz olmalı
            viewModel.processIntent(BudgetIntent.ConfirmDelete)
            viewModel.processIntent(BudgetIntent.DismissDelete)
            viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel.copy(id = EntityId("b-other"))))
            dismissAttemptedWhileDeleting = true
        }

        viewModel.processIntent(BudgetIntent.RequestDelete(testProgressModel))
        viewModel.processIntent(BudgetIntent.ConfirmDelete)
        advanceUntilIdle()

        assertTrue(wasDeletingObserved)
        assertTrue(dismissAttemptedWhileDeleting)
        assertEquals(EntityId("b-dialog-test"), fakeBudgetRepo.lastDeleteId)
        assertNull(viewModel.uiState.value.deleteConfirmation)

        uiStateJob.cancel()
    }

    @Test
    fun requestCopy_validMonths_opensDialogAndDoesNotCallRepository() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        assertNull(viewModel.uiState.value.copyConfirmation)
        assertNull(fakeBudgetRepo.lastCopyCommand)

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        val confirmation = viewModel.uiState.value.copyConfirmation
        assertNotNull(confirmation)
        assertEquals(YearMonth("2026-07"), confirmation?.sourceMonth)
        assertEquals(YearMonth("2026-08"), confirmation?.targetMonth)
        assertFalse(confirmation?.isCopying == true)
        // Request aşamasında use-case / repository çağrılmamalıdır
        assertNull(fakeBudgetRepo.lastCopyCommand)
        assertNull(viewModel.uiState.value.mutationState.copyError)

        uiStateJob.cancel()
    }

    @Test
    fun requestCopy_sameSourceAndTargetMonth_rejectedWithoutOpeningDialogOrCallingRepository() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-08"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.copyConfirmation)
        assertNull(fakeBudgetRepo.lastCopyCommand)
        assertEquals(BudgetFormFieldError.SOURCE_AND_TARGET_MONTH_SAME, viewModel.uiState.value.mutationState.copyError)
        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.ShowMessage)
        assertEquals(FinanceUiMessage.BUDGET_COPY_MONTHS_SAME, (events[0] as BudgetUiEvent.ShowMessage).message)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun changeCopySourceMonth_updatesConfirmationState_andSetsErrorIfSameMonth() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        assertEquals(YearMonth("2026-07"), viewModel.uiState.value.copyConfirmation?.sourceMonth)

        // Farklı geçerli bir aya geç
        viewModel.processIntent(BudgetIntent.ChangeCopySourceMonth(YearMonth("2026-06")))
        assertEquals(YearMonth("2026-06"), viewModel.uiState.value.copyConfirmation?.sourceMonth)
        assertNull(viewModel.uiState.value.mutationState.copyError)

        // Hedef ay ile aynı aya geç -> copyError set edilir
        viewModel.processIntent(BudgetIntent.ChangeCopySourceMonth(YearMonth("2026-08")))
        assertEquals(YearMonth("2026-08"), viewModel.uiState.value.copyConfirmation?.sourceMonth)
        assertEquals(BudgetFormFieldError.SOURCE_AND_TARGET_MONTH_SAME, viewModel.uiState.value.mutationState.copyError)

        uiStateJob.cancel()
    }

    @Test
    fun dismissCopy_clearsConfirmationStateAndError() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        assertNotNull(viewModel.uiState.value.copyConfirmation)

        viewModel.processIntent(BudgetIntent.DismissCopy)
        assertNull(viewModel.uiState.value.copyConfirmation)
        assertNull(viewModel.uiState.value.mutationState.copyError)

        uiStateJob.cancel()
    }

    @Test
    fun confirmCopy_success_emitsCopyCompletedEventAndClearsDialog() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        assertNull(fakeBudgetRepo.lastCopyCommand)

        viewModel.processIntent(BudgetIntent.ConfirmCopy)
        advanceUntilIdle()

        assertNotNull(fakeBudgetRepo.lastCopyCommand)
        assertEquals(YearMonth("2026-07"), fakeBudgetRepo.lastCopyCommand?.sourceMonth)
        assertEquals(YearMonth("2026-08"), fakeBudgetRepo.lastCopyCommand?.targetMonth)
        assertNull(viewModel.uiState.value.copyConfirmation)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)

        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.CopyCompleted)
        val copyEvent = events[0] as BudgetUiEvent.CopyCompleted
        assertEquals(2, copyEvent.copiedCount)
        assertEquals(0, copyEvent.skippedCount)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmCopy_withSkippedBudgets_emitsCopyCompletedWithSkippedCount() = runTest {
        fakeBudgetRepo.copyResult = RepositoryResult.Success(
            CopyBudgetsResult(
                copiedCount = 1,
                skippedCategoryIds = listOf(EntityId("c-1"), EntityId("c-2")),
            ),
        )

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        viewModel.processIntent(BudgetIntent.ConfirmCopy)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.CopyCompleted)
        val copyEvent = events[0] as BudgetUiEvent.CopyCompleted
        assertEquals(1, copyEvent.copiedCount)
        assertEquals(2, copyEvent.skippedCount)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmCopy_failure_emitsShowMessageAndClearsDialog() = runTest {
        fakeBudgetRepo.copyResult = RepositoryResult.Failure(AppError.Storage("disk_full"))

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        viewModel.processIntent(BudgetIntent.ConfirmCopy)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.copyConfirmation)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertEquals(1, events.size)
        assertTrue(events[0] is BudgetUiEvent.ShowMessage)
        assertEquals(FinanceUiMessage.STORAGE_ERROR, (events[0] as BudgetUiEvent.ShowMessage).message)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmCopy_cancellation_rethrowsAndDoesNotEmitGenericError() = runTest {
        fakeBudgetRepo.shouldThrowOnCopy = CancellationException("Copy cancelled")

        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }
        val events = mutableListOf<BudgetUiEvent>()
        val eventJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.toList(events)
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        viewModel.processIntent(BudgetIntent.ConfirmCopy)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)

        eventJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun confirmCopy_duplicateConfirmOrDismiss_ignoredWhileCopying() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        var wasCopyingObserved = false
        var dismissAttemptedWhileCopying = false
        fakeBudgetRepo.onCopyAction = {
            wasCopyingObserved = viewModel.uiState.value.copyConfirmation?.isCopying == true
            // Kopyalama sürerken duplicate confirm, dismiss, ay değiştirme veya request isteği etkisiz olmalı
            viewModel.processIntent(BudgetIntent.ConfirmCopy)
            viewModel.processIntent(BudgetIntent.DismissCopy)
            viewModel.processIntent(BudgetIntent.ChangeCopySourceMonth(YearMonth("2026-01")))
            viewModel.processIntent(BudgetIntent.RequestCopy(YearMonth("2026-05"), YearMonth("2026-08")))
            dismissAttemptedWhileCopying = true
        }

        viewModel.processIntent(
            BudgetIntent.RequestCopy(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )
        viewModel.processIntent(BudgetIntent.ConfirmCopy)
        advanceUntilIdle()

        assertTrue(wasCopyingObserved)
        assertTrue(dismissAttemptedWhileCopying)
        assertEquals(YearMonth("2026-07"), fakeBudgetRepo.lastCopyCommand?.sourceMonth)
        assertNull(viewModel.uiState.value.copyConfirmation)

        uiStateJob.cancel()
    }

    @Test
    fun clearFieldErrors_clearsAllFieldErrors() = runTest {
        val viewModel = createViewModel()
        val uiStateJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        viewModel.processIntent(
            BudgetIntent.CreateBudget(
                categoryId = null,
                month = null,
                limitInput = "",
            ),
        )
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.mutationState.categoryError)
        assertNotNull(viewModel.uiState.value.mutationState.monthError)
        assertNotNull(viewModel.uiState.value.mutationState.amountError)

        viewModel.processIntent(BudgetIntent.ClearFieldErrors)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.mutationState.categoryError)
        assertNull(viewModel.uiState.value.mutationState.monthError)
        assertNull(viewModel.uiState.value.mutationState.amountError)
        assertNull(viewModel.uiState.value.mutationState.copyError)

        uiStateJob.cancel()
    }

    @Test
    fun observationError_whenFlowFails_emitsErrorAndRetryRecovers() = runTest {
        fakeBudgetRepo.shouldThrowOnObserve = IllegalStateException("Simulated DB error")

        val viewModel = createViewModel()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, errorState.observationError)
        assertTrue(errorState.budgets.isEmpty())

        // Kurtarma: Hatayı kaldır ve Retry intent'i gönder
        fakeBudgetRepo.shouldThrowOnObserve = null
        val budget = Budget(
            id = EntityId("b-recovered"),
            ownerId = testOwnerId,
            workspaceId = null,
            categoryId = EntityId("cat-rec"),
            month = YearMonth("2026-08"),
            limit = Money(60_000, Currency.TRY),
            createdAt = testInstant,
        )
        fakeBudgetRepo.budgetsFlow.value = mapOf(YearMonth("2026-08") to listOf(budget))

        viewModel.processIntent(BudgetIntent.Retry)

        val recoveredState = viewModel.uiState.value
        assertNull(recoveredState.observationError)
        assertFalse(recoveredState.isLoading)
        assertEquals(1, recoveredState.budgets.size)
        assertEquals(60_000L, recoveredState.budgets[0].limitMinor)

        collectJob.cancel()
    }

    @Test
    fun observationCancellation_whenCancellationExceptionThrown_isNotSwallowed() = runTest {
        fakeBudgetRepo.shouldThrowOnObserve = CancellationException("Scope cancelled")

        val viewModel = createViewModel()

        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()
        collectJob.cancel()

        // CancellationException yakalanıp genel UI hatasına dönüştürülmemeli; state hata yerine ilk boş/korunan durumda kalmalı
        assertNull(viewModel.uiState.value.observationError)
        assertTrue(viewModel.uiState.value.budgets.isEmpty())
    }

    @Test
    fun loadBudgetForEdit_whenBudgetFound_emitsReadyWithFormattedSeed() = runTest {
        val sampleBudget = Budget(
            id = EntityId("budget-target"),
            ownerId = testOwnerId,
            workspaceId = null,
            categoryId = EntityId("cat-market"),
            month = YearMonth("2026-08"),
            limit = Money(12550L, Currency.TRY), // 125,50 TRY -> "125,5"
            createdAt = testInstant,
        )
        fakeBudgetRepo.singleBudgetFlow.value = sampleBudget

        val viewModel = createViewModel()
        val states = mutableListOf<BudgetEditLoadState>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.editLoadState.collect { states.add(it) }
        }

        viewModel.loadBudgetForEdit(EntityId("budget-target"))
        advanceUntilIdle()

        assertEquals(EntityId("budget-target"), fakeBudgetRepo.lastObservedBudgetId)
        val latest = states.last()
        assertTrue("Ready durumu bekleniyor, gelen: $latest", latest is BudgetEditLoadState.Ready)
        val seed = (latest as BudgetEditLoadState.Ready).seed
        assertEquals(EntityId("budget-target"), seed.budgetId)
        assertEquals(EntityId("cat-market"), seed.categoryId)
        assertEquals(YearMonth("2026-08"), seed.month)
        assertEquals("125,5", seed.limitInput)
        assertEquals(Currency.TRY, seed.currency)

        collectJob.cancel()
    }

    @Test
    fun loadBudgetForEdit_whenBudgetNotFoundOrNull_emitsNotFound() = runTest {
        fakeBudgetRepo.singleBudgetFlow.value = null

        val viewModel = createViewModel()
        val states = mutableListOf<BudgetEditLoadState>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.editLoadState.collect { states.add(it) }
        }

        viewModel.loadBudgetForEdit(EntityId("budget-404"))
        advanceUntilIdle()

        assertEquals(EntityId("budget-404"), fakeBudgetRepo.lastObservedBudgetId)
        val latest = states.last()
        assertEquals(BudgetEditLoadState.NotFound, latest)

        collectJob.cancel()
    }

    @Test
    fun loadBudgetForEdit_whenRepositoryThrowsException_emitsError() = runTest {
        fakeBudgetRepo.shouldThrowOnObserveSingle = IllegalStateException("Database failed")

        val viewModel = createViewModel()
        val states = mutableListOf<BudgetEditLoadState>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.editLoadState.collect { states.add(it) }
        }

        viewModel.loadBudgetForEdit(EntityId("budget-err"))
        advanceUntilIdle()

        val latest = states.last()
        assertTrue("Error durumu bekleniyor, gelen: $latest", latest is BudgetEditLoadState.Error)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, (latest as BudgetEditLoadState.Error).message)

        collectJob.cancel()
    }

    @Test
    fun loadBudgetForEdit_whenCancellationExceptionThrown_isNotSwallowed() = runTest {
        fakeBudgetRepo.shouldThrowOnObserveSingle = CancellationException("Coroutines cancel")

        val viewModel = createViewModel()
        val states = mutableListOf<BudgetEditLoadState>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.editLoadState.collect { states.add(it) }
        }

        viewModel.loadBudgetForEdit(EntityId("budget-cancel"))
        advanceUntilIdle()

        // CancellationException Error durumuna dönüştürülmemeli; Loading veya Idle kalmalı
        val latest = states.last()
        assertTrue("Cancellation generic Error'a dönüşmemeli, gelen: $latest", latest !is BudgetEditLoadState.Error)

        collectJob.cancel()
    }

    @Test
    fun setEditLoadInvalidId_emitsNotFound() = runTest {
        val viewModel = createViewModel()
        val states = mutableListOf<BudgetEditLoadState>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.editLoadState.collect { states.add(it) }
        }

        viewModel.setEditLoadInvalidId()
        advanceUntilIdle()

        assertEquals(BudgetEditLoadState.NotFound, states.last())

        collectJob.cancel()
    }
}
