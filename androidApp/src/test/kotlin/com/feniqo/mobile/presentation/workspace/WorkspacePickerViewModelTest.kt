package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspacesUseCase
import com.feniqo.mobile.domain.usecase.SetActiveWorkspaceUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
class WorkspacePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())
        val activeWorkspaceFlow = MutableStateFlow<Workspace?>(null)

        var setActiveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var setActiveDeferred: CompletableDeferred<Unit>? = null
        var lastSetActiveId: EntityId? = null
        var setActiveCallCount = 0

        override fun observeWorkspaces(): Flow<List<Workspace>> = workspacesFlow
        override fun observeActiveWorkspace(): Flow<Workspace?> = activeWorkspaceFlow
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = MutableStateFlow(emptyList())

        override suspend fun create(name: String): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("ws-created"))

        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("ws-created"))

        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> {
            setActiveCallCount++
            lastSetActiveId = workspaceId
            setActiveDeferred?.await()
            val result = setActiveResult
            if (result is RepositoryResult.Success) {
                val target = if (workspaceId == null) null else workspacesFlow.value.firstOrNull { it.id == workspaceId }
                activeWorkspaceFlow.value = target
            }
            return result
        }

        override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun changeMemberRole(
            workspaceId: EntityId,
            userId: EntityId,
            role: WorkspaceRole,
        ): RepositoryResult<Unit> = RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun transferOwnership(
            workspaceId: EntityId,
            targetUserId: EntityId,
        ): RepositoryResult<Unit> = RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun removeMember(
            workspaceId: EntityId,
            userId: EntityId,
        ): RepositoryResult<Unit> = RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
    }

    private val workspaceRepo = FakeWorkspaceRepository()
    private val observeWorkspacesUseCase = ObserveWorkspacesUseCase(workspaceRepo)
    private val observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(workspaceRepo)
    private val setActiveWorkspaceUseCase = SetActiveWorkspaceUseCase(workspaceRepo)

    private fun createViewModel(): WorkspacePickerViewModel = WorkspacePickerViewModel(
        observeWorkspacesUseCase = observeWorkspacesUseCase,
        observeActiveWorkspaceUseCase = observeActiveWorkspaceUseCase,
        setActiveWorkspaceUseCase = setActiveWorkspaceUseCase,
    )

    @Test
    fun initialState_loadsWorkspaces_andReflectsPersonalModeByDefault() = runTest {
        val sampleWorkspaces = listOf(
            Workspace(
                id = EntityId("ws-1"),
                name = "Ev Bütçesi",
                ownerId = EntityId("user-1"),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
            Workspace(
                id = EntityId("ws-2"),
                name = "Ofis Giderleri",
                ownerId = EntityId("user-2"),
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )
        workspaceRepo.workspacesFlow.value = sampleWorkspaces
        workspaceRepo.activeWorkspaceFlow.value = null

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isPersonalModeActive)
        assertNull(state.activeWorkspaceId)
        assertEquals(2, state.workspaces.size)
        assertEquals("ws-1", state.workspaces[0].id.value)
        assertFalse(state.workspaces[0].isActive)
        assertEquals("ws-2", state.workspaces[1].id.value)
        assertFalse(state.workspaces[1].isActive)
        assertFalse(state.isSelecting)
        assertNull(state.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun selectWorkspace_success_reactivelyUpdatesActiveState() = runTest {
        val ws1 = Workspace(
            id = EntityId("ws-1"),
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws1)
        workspaceRepo.activeWorkspaceFlow.value = null

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectWorkspace(EntityId("ws-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPersonalModeActive)
        assertEquals(EntityId("ws-1"), state.activeWorkspaceId)
        assertEquals(1, state.workspaces.size)
        assertTrue(state.workspaces[0].isActive)
        assertFalse(state.isSelecting)
        assertNull(state.errorMessage)
        assertEquals(1, workspaceRepo.setActiveCallCount)

        collectJob.cancel()
    }

    @Test
    fun selectPersonalMode_success_clearsActiveWorkspace() = runTest {
        val ws1 = Workspace(
            id = EntityId("ws-1"),
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws1)
        workspaceRepo.activeWorkspaceFlow.value = ws1

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Başlangıçta ws1 aktif
        assertFalse(viewModel.uiState.value.isPersonalModeActive)
        assertEquals(EntityId("ws-1"), viewModel.uiState.value.activeWorkspaceId)

        // Kişisel moda geçiş
        viewModel.selectPersonalMode()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPersonalModeActive)
        assertNull(state.activeWorkspaceId)
        assertFalse(state.workspaces[0].isActive)
        assertNull(state.errorMessage)
        assertEquals(1, workspaceRepo.setActiveCallCount)
        assertNull(workspaceRepo.lastSetActiveId)

        collectJob.cancel()
    }

    @Test
    fun selectWorkspace_failure_showsMappedError_andKeepsPreviousState() = runTest {
        val ws1 = Workspace(
            id = EntityId("ws-1"),
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws1)
        workspaceRepo.activeWorkspaceFlow.value = null
        workspaceRepo.setActiveResult = RepositoryResult.Failure(AppError.Validation("workspace_not_found"))

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectWorkspace(EntityId("ws-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPersonalModeActive)
        assertNull(state.activeWorkspaceId)
        assertFalse(state.isSelecting)
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, state.errorMessage)

        // Hata kapatma testi
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun selectWorkspace_doubleClickGuard_preventsDuplicateExecution() = runTest {
        val ws1 = Workspace(
            id = EntityId("ws-1"),
            name = "Ev Bütçesi",
            ownerId = EntityId("user-1"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws1)
        workspaceRepo.activeWorkspaceFlow.value = null

        val deferred = CompletableDeferred<Unit>()
        workspaceRepo.setActiveDeferred = deferred

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // İlk tıklama - işlem askıda
        viewModel.selectWorkspace(EntityId("ws-1"))
        assertTrue(viewModel.uiState.value.isSelecting)

        // İkinci tıklama (aynı veya farklı ID)
        viewModel.selectWorkspace(EntityId("ws-1"))
        viewModel.selectPersonalMode()

        // Sadece 1 kez çağrılmış olmalı
        assertEquals(1, workspaceRepo.setActiveCallCount)

        // İlk işlemi tamamla
        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelecting)
        assertEquals(1, workspaceRepo.setActiveCallCount)

        collectJob.cancel()
    }

    @Test
    fun removed_membership_workspace_does_not_appear_in_picker_list_and_resets_personal_mode() = runTest {
        val ws1 = Workspace(
            id = EntityId("ws-shared-active"),
            name = "Paylaşılan Alan",
            ownerId = EntityId("user-owner"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        // Başlangıçta kullanıcı bu alanda aktif ve listede mevcut
        workspaceRepo.workspacesFlow.value = listOf(ws1)
        workspaceRepo.activeWorkspaceFlow.value = ws1

        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.workspaces.size)
        assertEquals(EntityId("ws-shared-active"), viewModel.uiState.value.activeWorkspaceId)
        assertFalse(viewModel.uiState.value.isPersonalModeActive)

        // Kullanıcı alandan çıkarıldığında (inbound tombstone), Room Flow'u güncellenir:
        // workspaces listesinden çıkarılır ve activeWorkspace null olur
        workspaceRepo.workspacesFlow.value = emptyList()
        workspaceRepo.activeWorkspaceFlow.value = null
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workspaces.isEmpty())
        assertNull(viewModel.uiState.value.activeWorkspaceId)
        assertTrue(viewModel.uiState.value.isPersonalModeActive)

        collectJob.cancel()
    }
}
