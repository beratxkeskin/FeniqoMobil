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

    private val authRepository = FakeAuthRepository(userSession)
    private val categoryRepository = FakeCategoryRepository()
    private val observeCategoriesUseCase = ObserveCategoriesUseCase(categoryRepository)
    private val deleteCategoryUseCase = DeleteCategoryUseCase(authRepository, categoryRepository)
    private val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
    private val observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo)

    private fun createViewModel(): CategoriesViewModel =
        CategoriesViewModel(observeCategoriesUseCase, deleteCategoryUseCase, observeActiveWorkspaceUseCase)

    @Test
    fun initialState_observesExpenseType_andSeparatesSystemAndCustomCategories() = runTest {
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
        assertEquals(TransactionType.EXPENSE, state.selectedType)
        assertEquals(1, state.systemCategories.size)
        assertEquals("Market", state.systemCategories[0].name)
        assertEquals(true, state.systemCategories[0].isDefault)
        assertFalse(state.systemCategories[0].canEdit)
        assertFalse(state.systemCategories[0].canDelete)

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

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedType)
        assertEquals(1, viewModel.uiState.value.systemCategories.size)
        assertEquals("Market", viewModel.uiState.value.systemCategories[0].name)

        viewModel.onTypeSelected(TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, viewModel.uiState.value.selectedType)
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
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.selectedType)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.systemCategories.size)
        assertEquals("Market", viewModel.uiState.value.systemCategories[0].name)

        // INCOME gözlemini geciktirecek kilit kuruyoruz
        val incomeDeferred = CompletableDeferred<Unit>()
        categoryRepository.observeDeferred = incomeDeferred

        viewModel.onTypeSelected(TransactionType.INCOME)

        // Ara durum: INCOME seçili, loading true, eski liste temizlenmiş ve boş
        val intermediateState = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, intermediateState.selectedType)
        assertTrue(intermediateState.isLoading)
        assertTrue(intermediateState.systemCategories.isEmpty())
        assertTrue(intermediateState.customCategories.isEmpty())

        // INCOME emisyonunu tamamlıyoruz
        categoryRepository.observeDeferred = null
        incomeDeferred.complete(Unit)
        advanceUntilIdle()

        // Son durum: loading kapalı ve INCOME listesi yüklendi
        val finalState = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, finalState.selectedType)
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
}
