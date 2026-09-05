package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddGoalContributionUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
class GoalContributionFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate(2026, 9, 3)

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeGoalRepository : GoalRepository {
        val goalsFlow = MutableStateFlow<Map<EntityId, Goal>>(emptyMap())
        var addContributionResult: RepositoryResult<EntityId>? = null
        var lastAddContributionCommand: AddGoalContributionCommand? = null
        var throwCancellationOnAdd: Boolean = false

        override fun observeGoals(): Flow<List<Goal>> = flowOf(goalsFlow.value.values.toList())

        override fun observeGoal(id: EntityId): Flow<Goal?> = flow {
            goalsFlow.collect { map ->
                emit(map[id])
            }
        }

        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = flowOf(emptyList())

        override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> {
            throw NotImplementedError()
        }

        override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> {
            throw NotImplementedError()
        }

        override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> {
            if (throwCancellationOnAdd) {
                throw CancellationException("Simulated coroutine cancellation")
            }
            lastAddContributionCommand = command
            return addContributionResult ?: RepositoryResult.Success(EntityId("gc-100"))
        }


        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            throw NotImplementedError()
        }
    }

    private lateinit var repository: FakeGoalRepository
    private lateinit var viewModel: GoalContributionFormViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGoalRepository()
        viewModel = GoalContributionFormViewModel(
            observeGoalUseCase = ObserveGoalUseCase(repository),
            addGoalContributionUseCase = AddGoalContributionUseCase(repository),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialMovementState_isCorrect() {
        val state = viewModel.uiState.value
        assertEquals(fixedToday, state.input.occurredOn)
        assertEquals(GoalContributionDirection.ADD, state.input.direction)
        assertEquals("", state.input.amountInput)
        assertEquals("", state.input.noteInput)
        assertFalse(state.isSubmitting)
        assertEquals(GoalContributionParentLoadState.Idle, viewModel.parentLoadState.value)
    }

    @Test
    fun loadParentGoal_readyPopulatesGoal() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Yaz Tatili",
            targetAmount = Money(30_000_00L, Currency.TRY),
            currentAmount = Money(10_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 7, 1),
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)

        viewModel.loadParentGoal(goalId)
        advanceUntilIdle()

        val loadState = viewModel.parentLoadState.value
        assertTrue(loadState is GoalContributionParentLoadState.Ready)
        assertEquals("Yaz Tatili", (loadState as GoalContributionParentLoadState.Ready).goal.name)
    }

    @Test
    fun loadParentGoal_notFound_setsNotFound() = runTest {
        viewModel.loadParentGoal(EntityId("non-existent"))
        advanceUntilIdle()

        assertEquals(GoalContributionParentLoadState.NotFound, viewModel.parentLoadState.value)
    }

    @Test
    fun setParentLoadInvalidId_setsNotFound() {
        viewModel.setParentLoadInvalidId()
        assertEquals(GoalContributionParentLoadState.NotFound, viewModel.parentLoadState.value)
    }

    @Test
    fun loadParentGoal_raceConditionProtection_ignoresOlderObservation() = runTest {
        val slowGoalId = EntityId("g-slow")
        val fastGoalId = EntityId("g-fast")

        val slowFlow = MutableSharedFlow<Goal?>()
        val fastGoal = Goal(
            id = fastGoalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hızlı Hedef",
            targetAmount = Money(5000_00L, Currency.TRY),
            currentAmount = Money(1000_00L, Currency.TRY),
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
        val customViewModel = GoalContributionFormViewModel(
            observeGoalUseCase = ObserveGoalUseCase(customRepo),
            addGoalContributionUseCase = AddGoalContributionUseCase(customRepo),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )

        customViewModel.loadParentGoal(slowGoalId)
        customViewModel.loadParentGoal(fastGoalId)
        advanceUntilIdle()

        assertEquals(GoalContributionParentLoadState.Ready(fastGoal), customViewModel.parentLoadState.value)

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

        assertEquals("Hızlı Hedef", (customViewModel.parentLoadState.value as GoalContributionParentLoadState.Ready).goal.name)
    }

    @Test
    fun submit_addContribution_successful() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(20_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 1, 1),
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        viewModel.loadParentGoal(goalId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "1500",
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 3),
                noteInput = "Prim",
            )
        }

        val emittedEvents = mutableListOf<GoalContributionFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(GoalContributionFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_CONTRIBUTION_ADDED), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        val cmd = repository.lastAddContributionCommand
        assertTrue(cmd != null)
        assertEquals(goalId, cmd?.goalId)
        assertEquals(Money(1_500_00L, Currency.TRY), cmd?.amount)
        assertEquals(GoalContributionDirection.ADD, cmd?.direction)
        assertEquals("Prim", cmd?.note)

        job.cancel()
    }

    @Test
    fun submit_removeContribution_exceedsCurrentAmount_failsClosed() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(20_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 1, 1),
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        viewModel.loadParentGoal(goalId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "6000", // Mevcut 5000'i aşıyor
                direction = GoalContributionDirection.REMOVE,
                occurredOn = LocalDate(2026, 9, 3),
            )
        }

        val emittedEvents = mutableListOf<GoalContributionFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errors.hasErrors)
        assertEquals(GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT, viewModel.uiState.value.errors.amountError)
        assertEquals(0, emittedEvents.size)
        assertNull(repository.lastAddContributionCommand)

        job.cancel()
    }

    @Test
    fun submit_failure_emitsShowMessage() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(20_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 1, 1),
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.addContributionResult = RepositoryResult.Failure(AppError.Storage("hata"))
        viewModel.loadParentGoal(goalId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "100",
                occurredOn = LocalDate(2026, 9, 3),
            )
        }

        val emittedEvents = mutableListOf<GoalContributionFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(GoalContributionFormUiEvent.ShowMessage(FinanceUiMessage.STORAGE_ERROR), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        job.cancel()
    }

    @Test
    fun submit_whenCancelled_resetsIsSubmittingWithoutEmittingEventsAndPreservesState() = runTest {
        val goalId = EntityId("g-1")
        val sampleGoal = Goal(
            id = goalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(20_000_00L, Currency.TRY),
            currentAmount = Money(5_000_00L, Currency.TRY),
            targetDate = LocalDate(2027, 1, 1),
            color = CategoryColor("#1976D2"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.goalsFlow.value = mapOf(goalId to sampleGoal)
        repository.throwCancellationOnAdd = true
        viewModel.loadParentGoal(goalId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "1000",
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 3),
                noteInput = "Prim",
            )
        }

        val emittedEvents = mutableListOf<GoalContributionFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        // 1. Hiçbir event yayılmamalı
        assertEquals(0, emittedEvents.size)
        // 2. isSubmitting kilidi kapatılmış olmalı (false)
        assertFalse(viewModel.uiState.value.isSubmitting)
        // 3. Kullanıcı girdileri ve parent load state korunmalı
        assertEquals("1000", viewModel.uiState.value.input.amountInput)
        assertEquals("Prim", viewModel.uiState.value.input.noteInput)
        assertEquals(GoalContributionDirection.ADD, viewModel.uiState.value.input.direction)
        assertEquals(LocalDate(2026, 9, 3), viewModel.uiState.value.input.occurredOn)
        assertTrue(viewModel.parentLoadState.value is GoalContributionParentLoadState.Ready)

        job.cancel()
    }
}

