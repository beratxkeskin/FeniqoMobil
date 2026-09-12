package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.AddGoalContributionCommand
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
import com.feniqo.mobile.domain.usecase.ObserveGoalsUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeGoalRepository : GoalRepository {
        val goalsFlow = MutableStateFlow<List<Goal>>(emptyList())
        var shouldThrowInFlow: Throwable? = null
        val observeCallCount = AtomicInteger(0)

        override fun observeGoals(): Flow<List<Goal>> = flow {
            observeCallCount.incrementAndGet()
            shouldThrowInFlow?.let { throw it }
            goalsFlow.collect { emit(it) }
        }

        override fun observeGoal(id: EntityId): Flow<Goal?> = emptyFlow()
        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = emptyFlow()
        override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> = error("Not needed")
        override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = error("Not needed")
    }

    private fun sampleGoal(
        id: String,
        name: String = "Hedef",
        targetAmountMinor: Long = 100_000L,
        currentAmountMinor: Long = 20_000L,
        targetDate: LocalDate = LocalDate(2026, 12, 31),
    ): Goal = Goal(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        targetAmount = Money(targetAmountMinor, Currency.TRY),
        currentAmount = Money(currentAmountMinor, Currency.TRY),
        targetDate = targetDate,
        color = CategoryColor("#2E7D32"),
        icon = CategoryIcon("savings"),
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun createViewModel(
        repository: GoalRepository,
        fakeWorkspaceRepo: com.feniqo.mobile.presentation.common.FakeWorkspaceRepository = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository(),
    ): GoalsViewModel {
        return GoalsViewModel(
            observeGoalsUseCase = ObserveGoalsUseCase(repository),
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo),
            currentDateProvider = com.feniqo.mobile.presentation.common.CurrentDateProvider { LocalDate(2026, 9, 12) },
        )
    }

    @Test
    fun initialState_isLoadingTrueAndEmpty() {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.visibleGoals.isEmpty())
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
    }

    @Test
    fun successfulObservation_mapsDisplayModelsAndClearsLoading() = runTest {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val g1 = sampleGoal("g-1", "Tatil Fonu", 100_000L, 25_000L)
        val g2 = sampleGoal("g-2", "Ev Peşinatı", 500_000L, 500_000L)
        repo.goalsFlow.value = listOf(g1, g2)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(2, state.visibleGoals.size)
        assertEquals("g-1", state.visibleGoals[0].id.value)
        assertEquals("g-2", state.visibleGoals[1].id.value)
        assertFalse(state.isEmpty)
    }

    @Test
    fun emptySuccessfulList_setsIsEmptyTrue() = runTest {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        repo.goalsFlow.value = emptyList()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertTrue(state.visibleGoals.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun observationError_setsGenericErrorAndSafeEmptyList() = runTest {
        val repo = FakeGoalRepository()
        repo.shouldThrowInFlow = RuntimeException("Database error")
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)
        assertTrue(state.visibleGoals.isEmpty())
        assertFalse(state.isEmpty)
    }

    @Test
    fun retry_recoversAfterFailure() = runTest {
        val repo = FakeGoalRepository()
        repo.shouldThrowInFlow = RuntimeException("Database error")
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        var state = viewModel.uiState.value
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)

        // Fix repo error and trigger retry
        repo.shouldThrowInFlow = null
        repo.goalsFlow.value = listOf(sampleGoal("g-1", "Tatil"))
        viewModel.onIntent(GoalsIntent.Retry)

        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(1, state.visibleGoals.size)
        assertEquals("g-1", state.visibleGoals[0].id.value)
    }

    @Test
    fun cancellationException_isNotSwallowedAsGenericError() = runTest {
        val repo = FakeGoalRepository()
        repo.shouldThrowInFlow = CancellationException("Coroutines cancelled")
        val viewModel = createViewModel(repo)

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Cancellation should cancel the job / collector, not emit a state with GENERIC_ERROR
        val state = viewModel.uiState.value
        assertNull(state.observationError)
        job.cancel()
    }

    @Test
    fun multipleRetries_doNotCreateParallelCollectors() = runTest {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(1, repo.observeCallCount.get())

        // Multiple consecutive retries
        viewModel.onIntent(GoalsIntent.Retry)
        viewModel.onIntent(GoalsIntent.Retry)
        viewModel.onIntent(GoalsIntent.Retry)

        // With flatMapLatest, previous subscriptions are cancelled and only the latest is active
        repo.goalsFlow.value = listOf(sampleGoal("g-1"))
        val state = viewModel.uiState.value
        assertEquals(1, state.visibleGoals.size)
    }

    @Test
    fun filterChange_updatesVisibleGoalsWithoutRestartingRoomObservation() = runTest {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        repo.goalsFlow.value = listOf(sampleGoal("active", currentAmountMinor = 20_000L), sampleGoal("done", currentAmountMinor = 100_000L))

        viewModel.onIntent(GoalsIntent.SelectFilter(GoalStatusFilter.ACHIEVED))

        assertEquals(GoalStatusFilter.ACHIEVED, viewModel.uiState.value.selectedFilter)
        assertEquals(listOf("done"), viewModel.uiState.value.visibleGoals.map { it.id.value })
    }

    @Test
    fun workspaceName_updatesFromActiveWorkspaceFlow() = runTest {
        val repo = FakeGoalRepository()
        val workspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
        val viewModel = createViewModel(repo, workspaceRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        workspaceRepo.activeWorkspaceFlow.value = com.feniqo.mobile.domain.model.Workspace(
            id = EntityId("workspace"), name = "Ev Planı", ownerId = EntityId("owner"), createdAt = Instant.DISTANT_PAST,
        )

        assertEquals("Ev Planı", viewModel.uiState.value.activeWorkspaceName)
    }

    @Test
    fun summaryOverflow_keepsVisibleGoalsAvailable() = runTest {
        val repo = FakeGoalRepository()
        val viewModel = createViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        repo.goalsFlow.value = listOf(
            sampleGoal("a", targetAmountMinor = Money.MAX_AMOUNT_MINOR, currentAmountMinor = 1L),
            sampleGoal("b", targetAmountMinor = Money.MAX_AMOUNT_MINOR, currentAmountMinor = 1L),
        )

        assertTrue(viewModel.uiState.value.isSummaryCalculationError)
        assertEquals(2, viewModel.uiState.value.visibleGoals.size)
    }
}
