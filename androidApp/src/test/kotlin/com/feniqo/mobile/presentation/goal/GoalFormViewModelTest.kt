package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateGoalUseCase
import com.feniqo.mobile.domain.usecase.DeleteGoalUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.domain.usecase.UpdateGoalUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate(2026, 9, 3)

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeGoalRepository : GoalRepository {
        val goalsFlow = MutableStateFlow<Map<EntityId, Goal>>(emptyMap())
        val contributionsFlow = MutableStateFlow<Map<EntityId, List<GoalContribution>>>(emptyMap())
        var observeContributionsError: Exception? = null
        var createResult: RepositoryResult<EntityId>? = null
        var updateResult: RepositoryResult<Unit>? = null
        var deleteResult: RepositoryResult<Unit>? = null

        var lastCreateCommand: CreateGoalCommand? = null
        var lastUpdateCommand: UpdateGoalCommand? = null
        var lastDeletedId: EntityId? = null

        override fun observeGoals(): Flow<List<Goal>> = flowOf(goalsFlow.value.values.toList())

        override fun observeGoal(id: EntityId): Flow<Goal?> = flow {
            goalsFlow.collect { map ->
                emit(map[id])
            }
        }

        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = flow {
            val err = observeContributionsError
            if (err != null) throw err
            contributionsFlow.collect { map ->
                emit(map[goalId] ?: emptyList())
            }
        }

        override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> {
            lastCreateCommand = command
            return createResult ?: RepositoryResult.Success(EntityId("goal-created"))
        }

        override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult ?: RepositoryResult.Success(Unit)
        }

        override suspend fun addContribution(command: com.feniqo.mobile.domain.model.AddGoalContributionCommand): RepositoryResult<EntityId> {
            throw NotImplementedError()
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return deleteResult ?: RepositoryResult.Success(Unit)
        }
    }


    private lateinit var repository: FakeGoalRepository
    private lateinit var viewModel: GoalFormViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGoalRepository()
        viewModel = GoalFormViewModel(
            observeGoalUseCase = ObserveGoalUseCase(repository),
            observeGoalContributionsUseCase = ObserveGoalContributionsUseCase(repository),
            createGoalUseCase = CreateGoalUseCase(repository),
            updateGoalUseCase = UpdateGoalUseCase(repository),
            deleteGoalUseCase = DeleteGoalUseCase(repository),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )
    }


    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialCreateState_isCorrect() {
        val state = viewModel.uiState.value
        assertTrue(state.input.isCreateMode)
        assertEquals(fixedToday, state.input.targetDate)
        assertEquals(Currency.TRY, state.input.currency)
        assertEquals(GoalEditLoadState.Idle, viewModel.editLoadState.value)
        assertFalse(state.isSubmitting)
        assertFalse(state.pendingDeleteConfirmation)
    }

    @Test
    fun loadGoalForEdit_setsReadyAndPopulatesForm() = runTest {
        val goalId = EntityId("g-10")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Yeni Laptop",
            targetAmount = Money(60_000_00L, Currency.TRY),
            currentAmount = Money(15_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 1, 15),
            color = CategoryColor("#1976D2"),
            icon = CategoryIcon("laptop"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)

        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        val loadState = viewModel.editLoadState.value
        assertTrue(loadState is GoalEditLoadState.Ready)
        val ready = loadState as GoalEditLoadState.Ready
        assertEquals("Yeni Laptop", ready.draft.name)
        assertEquals(Money(15_000_00L, Currency.TRY), ready.currentAmount)

        val formState = viewModel.uiState.value
        assertTrue(formState.input.isEditMode)
        assertEquals(goalId, formState.input.goalId)
        assertEquals("Yeni Laptop", formState.input.nameInput)
        assertEquals("60000", formState.input.targetAmountInput)
        assertEquals(Currency.TRY, formState.input.currency)
        assertEquals("", formState.input.initialAmountInput) // Editte boş olmalı
        assertEquals(LocalDate(2027, 1, 15), formState.input.targetDate)
        assertEquals("#1976D2", formState.input.colorHex)
        assertEquals("laptop", formState.input.iconKey)
    }

    @Test
    fun loadGoalForEdit_notFound_setsNotFound() = runTest {
        viewModel.loadGoalForEdit(EntityId("non-existent"))
        advanceUntilIdle()

        assertEquals(GoalEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun setEditLoadInvalidId_setsNotFound() {
        viewModel.setEditLoadInvalidId()
        assertEquals(GoalEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun loadGoalForEdit_raceConditionProtection_ignoresOlderObservation() = runTest {
        val slowGoalId = EntityId("g-slow")
        val fastGoalId = EntityId("g-fast")

        val slowFlow = MutableSharedFlow<Goal?>()
        val fastGoal = Goal(
            id = fastGoalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hızlı Hedef",
            targetAmount = Money(1000_00L, Currency.TRY),
            currentAmount = Money(0, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val customRepo = object : GoalRepository by repository {
            override fun observeGoal(id: EntityId): Flow<Goal?> {
                return if (id == slowGoalId) slowFlow else flowOf(fastGoal)
            }
        }
        val customViewModel = GoalFormViewModel(
            observeGoalUseCase = ObserveGoalUseCase(customRepo),
            observeGoalContributionsUseCase = ObserveGoalContributionsUseCase(customRepo),
            createGoalUseCase = CreateGoalUseCase(customRepo),
            updateGoalUseCase = UpdateGoalUseCase(customRepo),
            deleteGoalUseCase = DeleteGoalUseCase(customRepo),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )


        // 1. Önce yavaş olanı başlat
        customViewModel.loadGoalForEdit(slowGoalId)
        // 2. Ardından hızlı olanı başlat (token artar)
        customViewModel.loadGoalForEdit(fastGoalId)
        advanceUntilIdle()

        assertEquals(GoalEditLoadState.Ready(GoalFormDraft.fromDomain(fastGoal), fastGoal.currentAmount), customViewModel.editLoadState.value)

        // 3. Eski yavaş akış gecikmeli olarak emit etse bile yeni state ezilmemelidir
        val slowGoal = Goal(
            id = slowGoalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Eski Yavaş",
            targetAmount = Money(5000_00L, Currency.TRY),
            currentAmount = Money(0, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        slowFlow.emit(slowGoal)
        advanceUntilIdle()

        // State hızlı hedefin state'i olarak kalmalı
        assertEquals("Hızlı Hedef", (customViewModel.editLoadState.value as GoalEditLoadState.Ready).draft.name)
    }

    @Test
    fun updateInput_preservesLockedCurrencyAndGoalIdInEditMode() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Mevcut",
            targetAmount = Money(1000_00L, Currency.USD),
            currentAmount = Money(0, Currency.USD),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                nameInput = "Yeni İsim",
                currency = Currency.EUR, // Editte değiştirilmeye çalışılsa bile kilitli kalmalı
                goalId = EntityId("other-id"), // ID değiştirilemez
            )
        }

        val state = viewModel.uiState.value
        assertEquals("Yeni İsim", state.input.nameInput)
        assertEquals(Currency.USD, state.input.currency)
        assertEquals(goalId, state.input.goalId)
    }

    @Test
    fun submit_create_successful_emitsMutationSuccess() = runTest {
        viewModel.updateInput {
            it.copy(
                nameInput = "Araba",
                targetAmountInput = "300000",
                currency = Currency.TRY,
                initialAmountInput = "10000",
                targetDate = LocalDate(2027, 5, 1),
            )
        }

        val emittedEvents = mutableListOf<GoalFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(GoalFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_SAVED), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        val cmd = repository.lastCreateCommand
        assertTrue(cmd != null)
        assertEquals("Araba", cmd?.name)
        assertEquals(Money(30_000_000L, Currency.TRY), cmd?.targetAmount)
        assertEquals(Money(1_000_000L, Currency.TRY), cmd?.initialAmount)

        job.cancel()
    }

    @Test
    fun submit_validationError_doesNotInvokeUseCase_andPopulatesErrors() = runTest {
        viewModel.updateInput {
            it.copy(
                nameInput = "", // Hatalı
                targetAmountInput = "0", // Hatalı
            )
        }

        val emittedEvents = mutableListOf<GoalFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errors.hasErrors)
        assertEquals(GoalFormFieldError.NAME_REQUIRED, viewModel.uiState.value.errors.nameError)
        assertEquals(GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE, viewModel.uiState.value.errors.targetAmountError)
        assertEquals(0, emittedEvents.size)
        assertNull(repository.lastCreateCommand)

        job.cancel()
    }

    @Test
    fun submit_failure_emitsShowMessage() = runTest {
        repository.createResult = RepositoryResult.Failure(AppError.Storage("hata"))
        viewModel.updateInput {
            it.copy(
                nameInput = "Hedef",
                targetAmountInput = "5000",
                targetDate = LocalDate(2027, 1, 1),
            )
        }

        val emittedEvents = mutableListOf<GoalFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(GoalFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        job.cancel()
    }

    @Test
    fun deleteFlow_requiresConfirmation_andDeletesOnConfirm() = runTest {
        val goalId = EntityId("g-del")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Silinecek",
            targetAmount = Money(1000_00L, Currency.TRY),
            currentAmount = Money(0, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        // 1. Request delete
        viewModel.requestDelete()
        assertTrue(viewModel.uiState.value.pendingDeleteConfirmation)

        // 2. Dismiss delete
        viewModel.dismissDelete()
        assertFalse(viewModel.uiState.value.pendingDeleteConfirmation)
        assertNull(repository.lastDeletedId)

        // 3. Request delete again & Confirm
        viewModel.requestDelete()
        val emittedEvents = mutableListOf<GoalFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(goalId, repository.lastDeletedId)
        assertFalse(viewModel.uiState.value.pendingDeleteConfirmation)
        assertEquals(1, emittedEvents.size)
        assertEquals(GoalFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_DELETED), emittedEvents.first())

        job.cancel()
    }

    @Test
    fun loadGoalForEdit_observesContributionsAndPopulatesSortedHistory() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Ev Peşinatı",
            targetAmount = Money(100_000_00L, Currency.TRY),
            currentAmount = Money(25_000_00L, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val contributions = listOf(
            GoalContribution(
                id = EntityId("gc-1"),
                goalId = goalId,
                amount = Money(10_000_00L, Currency.TRY),
                direction = com.feniqo.mobile.domain.model.GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 8, 1),
                note = "Maaş",
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
            GoalContribution(
                id = EntityId("gc-2"),
                goalId = goalId,
                amount = Money(15_000_00L, Currency.TRY),
                direction = com.feniqo.mobile.domain.model.GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 1),
                note = "Prim",
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.contributionsFlow.value = mapOf(goalId to contributions)

        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        val history = viewModel.uiState.value.contributionsHistory
        assertEquals(2, history.size)
        // 2026-09-01 (gc-2) first, then 2026-08-01 (gc-1)
        assertEquals(EntityId("gc-2"), history[0].id)
        assertEquals(EntityId("gc-1"), history[1].id)
        assertEquals("Prim", history[0].note)
    }

    @Test
    fun loadGoalForEdit_whenGoalNotFound_clearsContributionsHistory() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(10_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.contributionsFlow.value = mapOf(
            goalId to listOf(
                GoalContribution(
                    id = EntityId("gc-1"),
                    goalId = goalId,
                    amount = Money(5_000_00L, Currency.TRY),
                    direction = com.feniqo.mobile.domain.model.GoalContributionDirection.ADD,
                    occurredOn = LocalDate(2026, 9, 1),
                    note = null,
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
        )

        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.contributionsHistory.size)

        // Hedef silindiğinde
        repository.goalsFlow.value = emptyMap()
        advanceUntilIdle()

        assertEquals(GoalEditLoadState.NotFound, viewModel.editLoadState.value)
        assertTrue(viewModel.uiState.value.contributionsHistory.isEmpty())
    }

    @Test
    fun loadGoalForEdit_whenObservationErrors_clearsContributionsHistoryAndEmitsError() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(10_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.observeContributionsError = RuntimeException("Database error")

        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        assertEquals(GoalEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR), viewModel.editLoadState.value)
        assertTrue(viewModel.uiState.value.contributionsHistory.isEmpty())
    }

    @Test
    fun loadGoalForEdit_switchingParent_clearsOldContributionsHistoryImmediately() = runTest {
        val goal1 = Goal(
            id = EntityId("g-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef 1",
            targetAmount = Money(10_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val goal2 = Goal(
            id = EntityId("g-2"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef 2",
            targetAmount = Money(20_000_00L, Currency.TRY),
            currentAmount = Money(0, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.goalsFlow.value = mapOf(EntityId("g-1") to goal1, EntityId("g-2") to goal2)
        repository.contributionsFlow.value = mapOf(
            EntityId("g-1") to listOf(
                GoalContribution(
                    id = EntityId("gc-1"),
                    goalId = EntityId("g-1"),
                    amount = Money(5_000_00L, Currency.TRY),
                    direction = com.feniqo.mobile.domain.model.GoalContributionDirection.ADD,
                    occurredOn = LocalDate(2026, 9, 1),
                    note = null,
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
            EntityId("g-2") to emptyList(),
        )

        viewModel.loadGoalForEdit(EntityId("g-1"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.contributionsHistory.size)

        // Hedef 2'ye geçildiğinde eski geçmiş hemen temizlenmeli
        viewModel.loadGoalForEdit(EntityId("g-2"))
        assertTrue(viewModel.uiState.value.contributionsHistory.isEmpty())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.contributionsHistory.isEmpty())
    }

    @Test
    fun loadGoalForEdit_whenCancelled_doesNotMaskCancellationAsGenericErrorAndClearsHistory() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(10_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = fixedToday,
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.observeContributionsError = CancellationException("Observation cancelled")

        viewModel.loadGoalForEdit(goalId)
        advanceUntilIdle()

        // CancellationException generic hataya dönüşmemeli
        assertFalse(viewModel.editLoadState.value is GoalEditLoadState.Error)
        // Geçmiş listesi generic hata veya eski veri altında kalmamalı
        assertTrue(viewModel.uiState.value.contributionsHistory.isEmpty())
    }
}


