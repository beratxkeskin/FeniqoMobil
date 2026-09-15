package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
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
import kotlinx.coroutines.flow.flowOf
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
class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userId = EntityId("user-1")
    private val userSession = AuthSession(userId, "user@feniqo.com", Instant.parse("2026-08-21T00:00:00Z"))

    private class FakeAuthRepository(var session: AuthSession?) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        var shouldThrowOnObserve: Throwable? = null
        var lastObservedType: TransactionType? = null
        var lastDeletedId: EntityId? = null
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var deleteThrowable: Throwable? = null
        var deleteInvocationCount = 0
        var deleteDeferred: CompletableDeferred<Unit>? = null
        var observeDeferred: CompletableDeferred<Unit>? = null

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> {
            lastObservedType = type
            return flow {
                val error = shouldThrowOnObserve
                if (error != null) {
                    throw error
                }
                observeDeferred?.await()
                categoriesFlow.collect { emit(it.filter { cat -> type == null || cat.type == type }) }
            }
        }

        override fun observeCategory(id: EntityId): Flow<Category?> =
            flowOf(categoriesFlow.value.firstOrNull { it.id == id })

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(categoriesFlow.value)

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            deleteInvocationCount++
            lastDeletedId = id
            deleteDeferred?.await()
            val throwable = deleteThrowable
            if (throwable != null) {
                throw throwable
            }
            val result = deleteResult
            if (result is RepositoryResult.Success) {
                categoriesFlow.value = categoriesFlow.value.filterNot { it.id == id }
            }
            return result
        }
    }

    private class FakeTransactionRepo : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var lastFilter: TransactionFilter? = null
        val requestedFilters = mutableListOf<TransactionFilter>()

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
            lastFilter = filter
            requestedFilters.add(filter)
            return flow {
                transactionsFlow.collect { list ->
                    emit(list.filter { tx -> filter.type == null || tx.type == filter.type })
                }
            }
        }

        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun create(transaction: Transaction) = RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>) = RepositoryResult.Success(EntityId("grp-1"))
        override suspend fun update(transaction: Transaction) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>) = RepositoryResult.Success(Unit)
    }

    private val authRepository = FakeAuthRepository(userSession)
    private val categoryRepository = FakeCategoryRepository()
    private val transactionRepository = FakeTransactionRepo()
    private val observeCategoriesUseCase = ObserveCategoriesUseCase(categoryRepository)
    private val deleteCategoryUseCase = DeleteCategoryUseCase(authRepository, categoryRepository)
    private val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
    private val observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo)
    private val observeTransactionsUseCase = ObserveTransactionsUseCase(transactionRepository)
    private val testToday = LocalDate(2026, 9, 15)
    private val testDateProvider = CurrentDateProvider { testToday }

    private fun createViewModel(
        dateProvider: CurrentDateProvider = testDateProvider,
    ): CategoriesViewModel =
        CategoriesViewModel(
            observeCategoriesUseCase = observeCategoriesUseCase,
            deleteCategoryUseCase = deleteCategoryUseCase,
            observeActiveWorkspaceUseCase = observeActiveWorkspaceUseCase,
            observeTransactionsUseCase = observeTransactionsUseCase,
            currentDateProvider = dateProvider,
        )

    @Test
    fun initialState_observesAllCategories_andSeparatesSystemAndCustomCategories() = runTest {
        val sysExpense = Category(
            id = EntityId("sys-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = CategoryIcon("shopping-cart"),
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val customExpense = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Kişisel Gider",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = CategoryIcon("tag"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val sysIncome = Category(
            id = EntityId("sys-maas"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = CategoryIcon("briefcase"),
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(sysExpense, customExpense, sysIncome)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.selectedTypeFilter)
        assertEquals(2, state.systemCategories.size)
        assertTrue(state.systemCategories.any { it.name == "Market" })
        assertTrue(state.systemCategories.any { it.name == "Maaş" })

        assertEquals(1, state.customCategories.size)
        assertEquals("Kişisel Gider", state.customCategories[0].name)
        assertEquals(false, state.customCategories[0].isDefault)
        assertTrue(state.customCategories[0].canEdit)
        assertTrue(state.customCategories[0].canDelete)

        collector.cancel()
    }

    @Test
    fun onTypeSelected_whenIncomeSelected_cancelsPreviousObservation_andObservesIncome() = runTest {
        val sysExpense = Category(
            id = EntityId("sys-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val sysIncome = Category(
            id = EntityId("sys-maas"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(sysExpense, sysIncome)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onTypeFilterSelected(TransactionType.EXPENSE)
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedTypeFilter)
        assertEquals(1, viewModel.uiState.value.systemCategories.size)
        assertEquals("Market", viewModel.uiState.value.systemCategories[0].name)

        viewModel.onTypeSelected(TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, viewModel.uiState.value.selectedTypeFilter)
        assertEquals(TransactionType.INCOME, categoryRepository.lastObservedType)
        assertEquals(1, viewModel.uiState.value.systemCategories.size)
        assertEquals("Maaş", viewModel.uiState.value.systemCategories[0].name)

        collector.cancel()
    }

    @Test
    fun onTypeSelected_whenSameTypeSelected_doesNotRecreateObservation() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedType)
        viewModel.onTypeSelected(TransactionType.EXPENSE)
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedType)
        collector.cancel()
    }

    @Test
    fun onDeleteClicked_onSystemCategory_doesNotOpenDialog_andShowsDefaultCategoryImmutableMessage() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val systemCategory = CategoryDisplayModel(
            id = EntityId("sys-1"),
            name = "Market",
            type = TransactionType.EXPENSE,
            colorHex = "#EF4444",
            isDefault = true,
        )

        viewModel.onDeleteClicked(systemCategory)

        assertNull(viewModel.uiState.value.deleteTargetCategory)
        assertEquals(FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE, viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun onDeleteClicked_onCustomCategory_setsDeleteTargetCategory() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val customCategory = CategoryDisplayModel(
            id = EntityId("cust-1"),
            name = "Özel",
            type = TransactionType.EXPENSE,
            colorHex = "#10B981",
            isDefault = false,
        )

        viewModel.onDeleteClicked(customCategory)

        assertEquals(customCategory, viewModel.uiState.value.deleteTargetCategory)
        assertNull(viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun onDismissDeleteDialog_clearsDeleteTargetCategory() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val customCategory = CategoryDisplayModel(
            id = EntityId("cust-1"),
            name = "Özel",
            type = TransactionType.EXPENSE,
            colorHex = "#10B981",
            isDefault = false,
        )

        viewModel.onDeleteClicked(customCategory)
        assertEquals(customCategory, viewModel.uiState.value.deleteTargetCategory)

        viewModel.onDismissDeleteDialog()
        assertNull(viewModel.uiState.value.deleteTargetCategory)

        collector.cancel()
    }

    @Test
    fun onConfirmDelete_success_invokesUseCase_clearsTarget_andSetsCategoryDeletedMessage() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(1, categoryRepository.deleteInvocationCount)
        assertEquals(EntityId("cust-1"), categoryRepository.lastDeletedId)
        assertNull(viewModel.uiState.value.deleteTargetCategory)
        assertEquals(FinanceUiMessage.CATEGORY_DELETED, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)

        collector.cancel()
    }

    @Test
    fun onConfirmDelete_failure_setsErrorMessage_andPreservesTarget() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)
        categoryRepository.deleteResult = RepositoryResult.Failure(AppError.Storage("disk_full"))

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(1, categoryRepository.deleteInvocationCount)
        assertNotNull(viewModel.uiState.value.deleteTargetCategory)
        assertEquals(FinanceUiMessage.STORAGE_ERROR, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)

        collector.cancel()
    }

    @Test
    fun onConfirmDelete_duplicateCall_doesNotExecuteSecondMutation() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)
        val deferred = CompletableDeferred<Unit>()
        categoryRepository.deleteDeferred = deferred

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)

        viewModel.onConfirmDelete()
        assertTrue(viewModel.uiState.value.isDeleteInProgress)

        // İkinci çağrı yapılmak istendiğinde engellenmeli
        viewModel.onConfirmDelete()

        deferred.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, categoryRepository.deleteInvocationCount)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)

        collector.cancel()
    }

    @Test
    fun whileDeleteInProgress_typeSelectionAndDismissAreIgnored() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)
        val deferred = CompletableDeferred<Unit>()
        categoryRepository.deleteDeferred = deferred

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)
        viewModel.onConfirmDelete()
        assertTrue(viewModel.uiState.value.isDeleteInProgress)

        // Type değişimi ve dismiss istekleri silme sırasında yoksayılır
        viewModel.onTypeSelected(TransactionType.INCOME)
        viewModel.onDismissDeleteDialog()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedType)
        assertNotNull(viewModel.uiState.value.deleteTargetCategory)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        collector.cancel()
    }

    @Test
    fun observationException_emitsGenericError_withoutExposingRawExceptionMessage() = runTest {
        categoryRepository.shouldThrowOnObserve = RuntimeException("Database corruption secret detail")

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun onDismissMessage_clearsGeneralMessage() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val systemCategory = CategoryDisplayModel(
            id = EntityId("sys-1"),
            name = "Market",
            type = TransactionType.EXPENSE,
            colorHex = "#EF4444",
            isDefault = true,
        )
        viewModel.onDeleteClicked(systemCategory)
        assertEquals(FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE, viewModel.uiState.value.generalMessage)

        viewModel.onDismissMessage()
        assertNull(viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun onTypeSelected_emitsIntermediateLoadingState_clearingPreviousList_beforeEmittingNewType() = runTest {
        val sysExpense = Category(
            id = EntityId("sys-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val sysIncome = Category(
            id = EntityId("sys-maas"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(sysExpense, sysIncome)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Başlangıç EXPENSE listesi yüklendi
        viewModel.onTypeFilterSelected(TransactionType.EXPENSE)
        advanceUntilIdle()
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedTypeFilter)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.systemCategories.size)
        assertEquals("Market", viewModel.uiState.value.systemCategories[0].name)

        // INCOME gözlemini geciktirecek kilit kuruyoruz
        val incomeDeferred = CompletableDeferred<Unit>()
        categoryRepository.observeDeferred = incomeDeferred

        viewModel.onTypeSelected(TransactionType.INCOME)

        // Ara durum: INCOME seçili, loading true, eski liste temizlenmiş ve boş
        val intermediateState = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, intermediateState.selectedTypeFilter)
        assertTrue(intermediateState.isLoading)
        assertTrue(intermediateState.systemCategories.isEmpty())
        assertTrue(intermediateState.customCategories.isEmpty())

        // INCOME emisyonunu tamamlıyoruz
        categoryRepository.observeDeferred = null
        incomeDeferred.complete(Unit)
        advanceUntilIdle()

        // Son durum: loading kapalı ve INCOME listesi yüklendi
        val finalState = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, finalState.selectedTypeFilter)
        assertFalse(finalState.isLoading)
        assertEquals(1, finalState.systemCategories.size)
        assertEquals("Maaş", finalState.systemCategories[0].name)

        collector.cancel()
    }

    @Test
    fun onConfirmDelete_whenCancellationExceptionThrown_rethrowsAndCleansUpState_allowingSubsequentDelete() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)
        categoryRepository.deleteThrowable = CancellationException("Coroutines cancelled")

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        // Cancellation sonrası:
        // isDeleteInProgress temizlenmiş (false)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        // target silinmiş gibi temizlenmemiş
        assertNotNull(viewModel.uiState.value.deleteTargetCategory)
        // CATEGORY_DELETED veya GENERIC_ERROR üretilmemiş
        assertNull(viewModel.uiState.value.generalMessage)

        // Kilit temizlendiği için tekrar silme denenebilir
        categoryRepository.deleteThrowable = null
        categoryRepository.deleteResult = RepositoryResult.Success(Unit)

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(2, categoryRepository.deleteInvocationCount)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        assertNull(viewModel.uiState.value.deleteTargetCategory)
        assertEquals(FinanceUiMessage.CATEGORY_DELETED, viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun onConfirmDelete_whenUnexpectedExceptionThrown_emitsGenericError_preservesTarget_andCleansUpProgress() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)
        categoryRepository.deleteThrowable = RuntimeException("Unexpected IO crash")

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val target = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = customCategory.color.hex,
            isDefault = false,
        )
        viewModel.onDeleteClicked(target)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        // Ham mesaj sızdırılmaz, GENERIC_ERROR gösterilir
        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        assertNotNull(viewModel.uiState.value.deleteTargetCategory)

        // İkinci deneme yapılabilir
        categoryRepository.deleteThrowable = null
        categoryRepository.deleteResult = RepositoryResult.Success(Unit)

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(2, categoryRepository.deleteInvocationCount)
        assertFalse(viewModel.uiState.value.isDeleteInProgress)
        assertNull(viewModel.uiState.value.deleteTargetCategory)
        assertEquals(FinanceUiMessage.CATEGORY_DELETED, viewModel.uiState.value.generalMessage)

        collector.cancel()
    }

    @Test
    fun periodNavigation_previousMonth_decrementsMonth() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)
        viewModel.onPreviousMonth()
        assertEquals(YearMonth("2026-08"), viewModel.uiState.value.selectedYearMonth)

        collector.cancel()
    }

    @Test
    fun periodNavigation_nextMonth_doesNotExceedCurrentMonth() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)
        // Gelecek aya gitmeyi dene (2026-10) -> engellenmeli
        viewModel.onNextMonth()
        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)

        // Önce geriye git, sonra ileri gel -> çalışmalı
        viewModel.onPreviousMonth()
        assertEquals(YearMonth("2026-08"), viewModel.uiState.value.selectedYearMonth)
        viewModel.onNextMonth()
        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)

        collector.cancel()
    }

    @Test
    fun periodPicker_dialogRequestDismiss_andValidPastMonthSelection() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertFalse(viewModel.uiState.value.isPeriodPickerVisible)
        viewModel.onPeriodPickerRequested()
        assertTrue(viewModel.uiState.value.isPeriodPickerVisible)

        viewModel.onPeriodPickerDismissed()
        assertFalse(viewModel.uiState.value.isPeriodPickerVisible)

        // Geçmiş geçerli ay seçimi
        viewModel.onPeriodPickerRequested()
        viewModel.onYearMonthSelected(YearMonth("2026-05"))
        assertEquals(YearMonth("2026-05"), viewModel.uiState.value.selectedYearMonth)
        assertFalse(viewModel.uiState.value.isPeriodPickerVisible)

        // Gelecek ay seçimi reddedilmeli
        viewModel.onYearMonthSelected(YearMonth("2026-11"))
        assertEquals(YearMonth("2026-05"), viewModel.uiState.value.selectedYearMonth)

        collector.cancel()
    }

    @Test
    fun typeFilter_updatesFilter_correctly() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertNull(viewModel.uiState.value.selectedTypeFilter)

        viewModel.onTypeFilterSelected(TransactionType.EXPENSE)
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedTypeFilter)

        viewModel.onTypeFilterSelected(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.selectedTypeFilter)

        viewModel.onTypeFilterSelected(null)
        assertNull(viewModel.uiState.value.selectedTypeFilter)

        collector.cancel()
    }

    @Test
    fun filterChange_andPeriodChange_resetsDeleteTargetCategorySafely() = runTest {
        val customCategory = Category(
            id = EntityId("cust-1"),
            ownerId = userId,
            workspaceId = null,
            name = "Kişisel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(customCategory)

        val customDisplayModel = CategoryDisplayModel(
            id = customCategory.id,
            name = customCategory.name,
            type = customCategory.type,
            colorHex = "#10B981",
            iconKey = null,
            isDefault = false,
        )

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onDeleteClicked(customDisplayModel)
        assertEquals(EntityId("cust-1"), viewModel.uiState.value.deleteTargetCategory?.id)

        // Filtre değişince silme hedefi sıfırlanmalı
        viewModel.onTypeFilterSelected(TransactionType.INCOME)
        assertNull(viewModel.uiState.value.deleteTargetCategory)

        // Tekrar hedef seçilsin
        viewModel.onDeleteClicked(customDisplayModel)
        assertEquals(EntityId("cust-1"), viewModel.uiState.value.deleteTargetCategory?.id)

        // Ay değişince silme hedefi sıfırlanmalı
        viewModel.onPreviousMonth()
        assertNull(viewModel.uiState.value.deleteTargetCategory)

        collector.cancel()
    }

    @Test
    fun futureMonth_isBlockedByBothNextMonthAndYearMonthSelected() = runTest {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // testDateProvider 2026-09-15 döner; maxAllowedMonth = 2026-09
        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)

        // onNextMonth geleceğe geçmemeli
        viewModel.onNextMonth()
        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)

        // onYearMonthSelected geleceği reddetmeli
        viewModel.onYearMonthSelected(YearMonth("2026-10"))
        assertEquals(YearMonth("2026-09"), viewModel.uiState.value.selectedYearMonth)

        collector.cancel()
    }

    @Test
    fun multiCurrencyTransactions_areExcludedWithoutFailingScreen() = runTest {
        val expenseCat = Category(
            id = EntityId("sys-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(expenseCat)

        val usdTx = Transaction(
            id = EntityId("tx-usd"),
            ownerId = userId,
            workspaceId = EntityId("ws-1"),
            type = TransactionType.EXPENSE,
            amount = Money(5000, Currency.USD),
            categoryId = EntityId("sys-market"),
            description = "Test USD",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 8, 10),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        transactionRepository.transactionsFlow.value = listOf(usdTx)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertNull(viewModel.uiState.value.generalMessage)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(0L, viewModel.uiState.value.items.single().currentPeriodAmount.amountMinor)

        collector.cancel()
    }

    @Test
    fun allFilter_displaysBothIncomeAndExpenseCategoriesWithoutAddingThemTogether() = runTest {
        val marketCat = Category(
            id = EntityId("sys-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val salaryCat = Category(
            id = EntityId("sys-salary"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(marketCat, salaryCat)

        val expenseTx = Transaction(
            id = EntityId("tx-1"),
            ownerId = userId,
            workspaceId = EntityId("ws-1"),
            type = TransactionType.EXPENSE,
            amount = Money(150_00, Currency.TRY),
            categoryId = EntityId("sys-market"),
            description = "Market harcaması",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 8, 10),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val incomeTx = Transaction(
            id = EntityId("tx-2"),
            ownerId = userId,
            workspaceId = EntityId("ws-1"),
            type = TransactionType.INCOME,
            amount = Money(500_00, Currency.TRY),
            categoryId = EntityId("sys-salary"),
            description = "Maaş girişi",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 8, 11),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        transactionRepository.transactionsFlow.value = listOf(expenseTx, incomeTx)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertNull(state.selectedTypeFilter)
        assertEquals(2, state.items.size)

        val marketItem = state.items.first { it.category.id == EntityId("sys-market") }
        val salaryItem = state.items.first { it.category.id == EntityId("sys-salary") }

        assertEquals(150_00L, marketItem.currentPeriodAmount.amountMinor)
        assertEquals(500_00L, salaryItem.currentPeriodAmount.amountMinor)

        // Özet kartı en yüksek gideri göstermeli
        assertEquals("Market", state.summary.topExpenseCategoryName)
        assertEquals("150,00 ₺", state.summary.formattedTopExpenseAmount)

        collector.cancel()
    }

    @Test
    fun typeFilter_narrowsTransactionFilters_andSeparatesIncomeAndExpenseScope() = runTest {
        val marketCat = Category(
            id = EntityId("cat-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val salaryCat = Category(
            id = EntityId("cat-salary"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#10B981"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        categoryRepository.categoriesFlow.value = listOf(marketCat, salaryCat)

        val expenseTx = Transaction(
            id = EntityId("tx-1"),
            ownerId = userId,
            workspaceId = EntityId("ws-1"),
            type = TransactionType.EXPENSE,
            amount = Money(200_00, Currency.TRY),
            categoryId = EntityId("cat-market"),
            description = "Market",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 9, 10),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        val incomeTx = Transaction(
            id = EntityId("tx-2"),
            ownerId = userId,
            workspaceId = EntityId("ws-1"),
            type = TransactionType.INCOME,
            amount = Money(1000_00, Currency.TRY),
            categoryId = EntityId("cat-salary"),
            description = "Maaş",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 9, 10),
            receiptPath = null,
            installment = null,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        transactionRepository.transactionsFlow.value = listOf(expenseTx, incomeTx)

        val viewModel = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // 1. Tümü filtresi
        assertEquals(null, viewModel.uiState.value.selectedTypeFilter)
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals("Market", viewModel.uiState.value.summary.topExpenseCategoryName)
        assertEquals("200,00 ₺", viewModel.uiState.value.summary.formattedTopExpenseAmount)

        // 2. Gider filtresine geçiş
        viewModel.onTypeFilterSelected(TransactionType.EXPENSE)
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedTypeFilter)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("Market", viewModel.uiState.value.items[0].category.name)
        // Repository TransactionType.EXPENSE ile sorgulanmış olmalı
        assertTrue(transactionRepository.requestedFilters.any { it.type == TransactionType.EXPENSE })
        assertEquals("Market", viewModel.uiState.value.summary.topExpenseCategoryName)

        // 3. Gelir filtresine geçiş
        viewModel.onTypeFilterSelected(TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, viewModel.uiState.value.selectedTypeFilter)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("Maaş", viewModel.uiState.value.items[0].category.name)
        // Repository TransactionType.INCOME ile sorgulanmış olmalı
        assertTrue(transactionRepository.requestedFilters.any { it.type == TransactionType.INCOME })
        // Gelir görünümünde en yüksek kategori gelir olmalı
        assertEquals("Maaş", viewModel.uiState.value.summary.topCategoryName)
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.summary.topCategoryType)
        assertEquals("1.000,00 ₺", viewModel.uiState.value.summary.formattedTopCategoryAmount)
        assertTrue(viewModel.uiState.value.summary.insightText?.contains("en yüksek gelir") == true)

        collector.cancel()
    }

    @Test
    fun initialState_selectedYearMonth_reflectsSystemDateAcrossMultipleDates() = runTest {
        // 1. Sistem Tarihi: 2026-04-15 -> İlk frame dahil 2026-04
        val date1 = LocalDate(2026, 4, 15)
        val provider1 = CurrentDateProvider { date1 }
        val vm1 = createViewModel(dateProvider = provider1)
        assertEquals(YearMonth("2026-04"), vm1.uiState.value.selectedYearMonth)

        // 2. Sistem Tarihi: 2025-12-01 -> İlk frame dahil 2025-12
        val date2 = LocalDate(2025, 12, 1)
        val provider2 = CurrentDateProvider { date2 }
        val vm2 = createViewModel(dateProvider = provider2)
        assertEquals(YearMonth("2025-12"), vm2.uiState.value.selectedYearMonth)
    }
}
