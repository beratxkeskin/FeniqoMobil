package com.feniqo.mobile.presentation.category

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddCategoryCommand
import com.feniqo.mobile.domain.usecase.AddCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoryUseCase
import com.feniqo.mobile.domain.usecase.UpdateCategoryCommand
import com.feniqo.mobile.domain.usecase.UpdateCategoryUseCase
import com.feniqo.mobile.navigation.CategoryFormRoute
import com.feniqo.mobile.presentation.common.CurrentInstantProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
class CategoryFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = EntityId("user-100")
    private val userSession = AuthSession(userId, "user@feniqo.com", Instant.parse("2026-08-21T00:00:00Z"))
    private val fixedInstant = Instant.parse("2026-08-24T12:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class CountingInstantProvider(val fixed: Instant) : CurrentInstantProvider {
        var callCount = 0
        override fun now(): Instant {
            callCount++
            return fixed
        }
    }

    private class FakeIdGenerator(var currentSequence: Long = 1000L) : EntityIdGenerator {
        override fun nextId(): EntityId {
            currentSequence++
            return EntityId("gen-$currentSequence")
        }
    }

    private class FakeAuthRepository(var session: AuthSession?) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = MutableStateFlow<Map<EntityId, Category>>(emptyMap())
        var shouldThrowOnObserve: Throwable? = null
        var lastCreatedCategory: Category? = null
        var lastUpdatedCategory: Category? = null
        var createResult: RepositoryResult<EntityId>? = null
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var createThrowable: Throwable? = null
        var updateThrowable: Throwable? = null
        var createInvocationCount = 0
        var updateInvocationCount = 0
        var submitDeferred: CompletableDeferred<Unit>? = null
        var observeDeferred: CompletableDeferred<Unit>? = null

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(categories.value.values.toList())

        override fun observeCategory(id: EntityId): Flow<Category?> {
            return flow {
                val error = shouldThrowOnObserve
                if (error != null) {
                    throw error
                }
                observeDeferred?.await()
                emit(categories.value[id])
            }
        }

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(categories.value.values.toList())

        override suspend fun create(category: Category): RepositoryResult<EntityId> {
            createInvocationCount++
            lastCreatedCategory = category
            submitDeferred?.await()
            val throwable = createThrowable
            if (throwable != null) throw throwable
            val result = createResult ?: RepositoryResult.Success(category.id)
            if (result is RepositoryResult.Success) {
                categories.value = categories.value + (category.id to category)
            }
            return result
        }

        override suspend fun update(category: Category): RepositoryResult<Unit> {
            updateInvocationCount++
            lastUpdatedCategory = category
            submitDeferred?.await()
            val throwable = updateThrowable
            if (throwable != null) throw throwable
            val result = updateResult
            if (result is RepositoryResult.Success) {
                categories.value = categories.value + (category.id to category)
            }
            return result
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private val authRepo = FakeAuthRepository(userSession)
    private val catRepo = FakeCategoryRepository()
    private val countingInstantProvider = CountingInstantProvider(fixedInstant)
    private val idGenerator = FakeIdGenerator()

    private fun createViewModel(route: CategoryFormRoute?): CategoryFormViewModel {
        val map = mutableMapOf<String, Any?>()
        if (route != null) {
            if (route.categoryId != null) map["categoryId"] = route.categoryId
            map["initialTypeCode"] = route.initialTypeCode
        }
        val handle = SavedStateHandle(map)
        return CategoryFormViewModel(
            addCategoryUseCase = AddCategoryUseCase(authRepo, catRepo),
            updateCategoryUseCase = UpdateCategoryUseCase(authRepo, catRepo),
            observeCategoryUseCase = ObserveCategoryUseCase(catRepo),
            currentInstantProvider = countingInstantProvider,
            entityIdGenerator = idGenerator,
            savedStateHandle = handle,
        )
    }

    private fun createViewModelWithHandle(handle: SavedStateHandle): CategoryFormViewModel {
        return CategoryFormViewModel(
            addCategoryUseCase = AddCategoryUseCase(authRepo, catRepo),
            updateCategoryUseCase = UpdateCategoryUseCase(authRepo, catRepo),
            observeCategoryUseCase = ObserveCategoryUseCase(catRepo),
            currentInstantProvider = countingInstantProvider,
            entityIdGenerator = idGenerator,
            savedStateHandle = handle,
        )
    }

    @Test
    fun createMode_withNullCategoryId_initializesWithExpenseByDefault() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        val state = viewModel.uiState.value

        assertFalse(state.isEditMode)
        assertFalse(state.isLoadingInitialData)
        assertNull(state.loadError)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("", state.name)
        assertTrue(state.isFormEnabled)
        assertTrue(state.isTypeEditable)
    }

    @Test
    fun createMode_withIncomeInitialTypeCode_initializesWithIncome() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null, "income"))
        val state = viewModel.uiState.value

        assertFalse(state.isEditMode)
        assertEquals(TransactionType.INCOME, state.type)
        assertTrue(state.isTypeEditable)
    }

    @Test
    fun createMode_withInvalidInitialTypeCode_fallsBackToExpense() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null, "UNKNOWN_TYPE"))
        val state = viewModel.uiState.value

        assertEquals(TransactionType.EXPENSE, state.type)
    }

    @Test
    fun route_withBlankCategoryId_setsInvalidRouteError() = runTest {
        val viewModel = createViewModel(CategoryFormRoute("   "))
        val state = viewModel.uiState.value

        assertEquals(CategoryFormLoadError.INVALID_ROUTE, state.loadError)
        assertFalse(state.isLoadingInitialData)
        assertFalse(state.isFormEnabled)
    }

    @Test
    fun route_withMalformedRoute_setsInvalidRouteError() = runTest {
        // SavedStateHandle with invalid types / malformed state
        val handle = SavedStateHandle(mapOf("categoryId" to 12345))
        val viewModel = createViewModelWithHandle(handle)
        val state = viewModel.uiState.value

        assertEquals(CategoryFormLoadError.INVALID_ROUTE, state.loadError)
        assertFalse(state.isFormEnabled)
    }

    @Test
    fun editMode_loadingState_formIsDisabled() = runTest {
        val catId = EntityId("cat-100")
        val deferred = CompletableDeferred<Unit>()
        catRepo.observeDeferred = deferred

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        val state = viewModel.uiState.value

        assertTrue(state.isEditMode)
        assertTrue(state.isLoadingInitialData)
        assertFalse(state.isFormEnabled)
        assertFalse(state.canSubmit)

        deferred.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun editMode_loadsCustomCategory_populatesFieldsAndClearsLoading() = runTest {
        val catId = EntityId("cat-100")
        val category = Category(
            id = catId,
            ownerId = userId,
            workspaceId = null,
            name = "Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#3B82F6"),
            icon = CategoryIcon("home"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to category)

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertFalse(state.isLoadingInitialData)
        assertNull(state.loadError)
        assertEquals("Kira", state.name)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("#3B82F6", state.colorHex)
        assertEquals("home", state.iconKey)
        assertTrue(state.isFormEnabled)
        assertFalse(state.isTypeEditable)
    }

    @Test
    fun editMode_loadedTypeOverridesRouteType() = runTest {
        val catId = EntityId("cat-100")
        val category = Category(
            id = catId,
            ownerId = userId,
            workspaceId = null,
            name = "Freelance",
            type = TransactionType.INCOME,
            color = CategoryColor("#34D399"),
            icon = CategoryIcon("laptop"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to category)

        // Route specifies EXPENSE but loaded category is INCOME
        val viewModel = createViewModel(CategoryFormRoute(catId.value, "EXPENSE"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, state.type)
        assertFalse(state.isTypeEditable)
    }

    @Test
    fun editMode_onTypeChangedIsIgnored() = runTest {
        val catId = EntityId("cat-100")
        val category = Category(
            id = catId,
            ownerId = userId,
            workspaceId = null,
            name = "Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#3B82F6"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to category)

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        viewModel.onTypeChanged(TransactionType.INCOME)
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.type)
    }

    @Test
    fun editMode_whenCategoryNotFound_setsCategoryNotFoundError() = runTest {
        val catId = EntityId("non-existing")
        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CategoryFormLoadError.CATEGORY_NOT_FOUND, state.loadError)
        assertFalse(state.isLoadingInitialData)
        assertFalse(state.isFormEnabled)
    }

    @Test
    fun editMode_whenDefaultCategory_setsDefaultCategoryReadOnlyError() = runTest {
        val sysId = EntityId("sys-market")
        val sysCategory = Category(
            id = sysId,
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(sysId to sysCategory)

        val viewModel = createViewModel(CategoryFormRoute(sysId.value))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY, state.loadError)
        assertFalse(state.isLoadingInitialData)
        assertFalse(state.isFormEnabled)
    }

    @Test
    fun editMode_whenOwnerIdNull_setsDefaultCategoryReadOnlyError() = runTest {
        val catId = EntityId("null-owner")
        val cat = Category(
            id = catId,
            ownerId = null,
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to cat)

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY, state.loadError)
        assertFalse(state.isFormEnabled)
    }

    @Test
    fun editMode_whenLoadThrowsRuntimeException_setsLoadFailedError_withoutExposingRawMessage() = runTest {
        val catId = EntityId("cat-crash")
        catRepo.shouldThrowOnObserve = RuntimeException("Database error with sensitive path")

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CategoryFormLoadError.LOAD_FAILED, state.loadError)
        assertFalse(state.isLoadingInitialData)
        assertFalse(state.isFormEnabled)
        assertNull(state.generalMessage)
    }

    @Test
    fun editMode_whenLoadThrowsCancellationException_rethrowsException() = runTest {
        val catId = EntityId("cat-cancel")
        catRepo.shouldThrowOnObserve = CancellationException("Observation cancelled")

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun formFields_doNotUpdate_whenFormIsDisabled() = runTest {
        val viewModel = createViewModel(CategoryFormRoute("   ")) // INVALID_ROUTE -> disabled
        advanceUntilIdle()

        viewModel.onNameChanged("Yeni İsim")
        viewModel.onColorChanged("#FF0000")
        viewModel.onIconChanged("star")
        viewModel.onTypeChanged(TransactionType.INCOME)

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("#10B981", state.colorHex)
        assertNull(state.iconKey)
        assertEquals(TransactionType.EXPENSE, state.type)
    }

    @Test
    fun createMode_typeCanBeChanged() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.type)
        viewModel.onTypeChanged(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.type)
    }

    @Test
    fun onSubmit_withBlankName_setsNameRequiredError_andDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("   ")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(CategoryFormFieldError.NAME_REQUIRED, viewModel.uiState.value.nameError)
        assertEquals(0, catRepo.createInvocationCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun onSubmit_withNameOver50Chars_setsNameTooLongError_andDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("A".repeat(51))
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(CategoryFormFieldError.NAME_TOO_LONG, viewModel.uiState.value.nameError)
        assertEquals(0, catRepo.createInvocationCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun onSubmit_withInvalidColor_setsColorInvalidError_andDoesNotCallUseCase() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Market")
        viewModel.onColorChanged("INVALID_COLOR")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(CategoryFormFieldError.COLOR_INVALID, viewModel.uiState.value.colorError)
        assertEquals(0, catRepo.createInvocationCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun createMode_onSubmit_createsCategoryWithGeneratedId_nullWorkspace_andEmitsNavigateBack() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")
        viewModel.onColorChanged("#10B981")
        viewModel.onIconChanged("trending-up")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, catRepo.createInvocationCount)
        val created = catRepo.lastCreatedCategory
        assertNotNull(created)
        assertEquals(EntityId("gen-1001"), created?.id)
        assertNull(created?.workspaceId)
        assertEquals("Tasarruf", created?.name)
        assertEquals(TransactionType.EXPENSE, created?.type)
        assertEquals("#10B981", created?.color?.hex)
        assertEquals("trending-up", created?.icon?.key)
        assertEquals(CategoryFormEvent.NavigateBack, receivedEvent)
        assertFalse(viewModel.uiState.value.isSubmitting)

        eventJob.cancel()
    }

    @Test
    fun createMode_onSubmit_usesCurrentInstantProviderTime() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, countingInstantProvider.callCount)
        assertEquals(fixedInstant, catRepo.lastCreatedCategory?.createdAt)
    }

    @Test
    fun createMode_onSubmit_whenUseCaseFails_showsSafeErrorMessage_andDoesNotEmitNavigateBack() = runTest {
        catRepo.createResult = RepositoryResult.Failure(AppError.Validation("category_duplicate_name"))

        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, catRepo.createInvocationCount)
        assertNull(receivedEvent)
        assertEquals(FinanceUiMessage.CATEGORY_DUPLICATE_NAME, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)

        eventJob.cancel()
    }

    @Test
    fun createMode_onSubmit_duplicateCall_doesNotExecuteSecondMutation() = runTest {
        val deferred = CompletableDeferred<Unit>()
        catRepo.submitDeferred = deferred

        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")

        viewModel.onSubmit()
        assertTrue(viewModel.uiState.value.isSubmitting)

        // İkinci submit engellenmeli
        viewModel.onSubmit()

        deferred.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, catRepo.createInvocationCount)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun editMode_onSubmit_updatesCategoryWithExistingId_preservesType_andEmitsNavigateBack() = runTest {
        val catId = EntityId("cat-100")
        val existing = Category(
            id = catId,
            ownerId = userId,
            workspaceId = null,
            name = "Eski Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#3B82F6"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to existing)

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        viewModel.onNameChanged("Yeni Kira")
        viewModel.onColorChanged("#10B981")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, catRepo.updateInvocationCount)
        val updated = catRepo.lastUpdatedCategory
        assertNotNull(updated)
        assertEquals(catId, updated?.id)
        assertEquals("Yeni Kira", updated?.name)
        assertEquals("#10B981", updated?.color?.hex)
        assertEquals(TransactionType.EXPENSE, updated?.type)
        assertEquals(CategoryFormEvent.NavigateBack, receivedEvent)
        assertFalse(viewModel.uiState.value.isSubmitting)

        eventJob.cancel()
    }

    @Test
    fun editMode_onSubmit_whenUseCaseFails_showsSafeErrorMessage_andDoesNotEmitNavigateBack() = runTest {
        val catId = EntityId("cat-100")
        val existing = Category(
            id = catId,
            ownerId = userId,
            workspaceId = null,
            name = "Eski Kira",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#3B82F6"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        catRepo.categories.value = mapOf(catId to existing)
        catRepo.updateResult = RepositoryResult.Failure(AppError.Storage("disk_full"))

        val viewModel = createViewModel(CategoryFormRoute(catId.value))
        advanceUntilIdle()

        viewModel.onNameChanged("Yeni Kira")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, catRepo.updateInvocationCount)
        assertNull(receivedEvent)
        assertEquals(FinanceUiMessage.STORAGE_ERROR, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)

        eventJob.cancel()
    }

    @Test
    fun events_afterBeingConsumed_areNotReplayedToNewCollector() = runTest {
        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Yatırım")

        var firstReceived: CategoryFormEvent? = null
        val firstJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { firstReceived = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(CategoryFormEvent.NavigateBack, firstReceived)
        firstJob.cancel()

        var secondReceived: CategoryFormEvent? = null
        val secondJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { secondReceived = it }
        }
        advanceUntilIdle()

        assertNull(secondReceived)
        secondJob.cancel()
    }

    @Test
    fun onSubmit_whenCancellationExceptionThrown_doesNotEmitNavigateBack_cleansUpProgress_andAllowsSubsequentSubmit() = runTest {
        catRepo.createThrowable = CancellationException("Submit cancelled")

        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertNull(receivedEvent)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.generalMessage)

        // Tekrar submit yapılabilir
        catRepo.createThrowable = null
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(2, catRepo.createInvocationCount)
        assertEquals(CategoryFormEvent.NavigateBack, receivedEvent)
        assertFalse(viewModel.uiState.value.isSubmitting)

        eventJob.cancel()
    }

    @Test
    fun onSubmit_whenRuntimeExceptionThrown_setsGenericError_cleansUpProgress_andAllowsSubsequentSubmit() = runTest {
        catRepo.createThrowable = RuntimeException("Unexpected submit crash")

        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")

        var receivedEvent: CategoryFormEvent? = null
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { receivedEvent = it }
        }

        viewModel.onSubmit()
        advanceUntilIdle()

        assertNull(receivedEvent)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.generalMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)

        // Tekrar submit yapılabilir
        catRepo.createThrowable = null
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(2, catRepo.createInvocationCount)
        assertEquals(CategoryFormEvent.NavigateBack, receivedEvent)

        eventJob.cancel()
    }

    @Test
    fun onDismissMessage_clearsGeneralMessage() = runTest {
        catRepo.createResult = RepositoryResult.Failure(AppError.Validation("category_duplicate_name"))

        val viewModel = createViewModel(CategoryFormRoute(null))
        viewModel.onNameChanged("Tasarruf")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.CATEGORY_DUPLICATE_NAME, viewModel.uiState.value.generalMessage)
        viewModel.onDismissMessage()
        assertNull(viewModel.uiState.value.generalMessage)
    }
}
