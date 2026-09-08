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
import com.feniqo.mobile.domain.usecase.JoinWorkspaceUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
class WorkspaceJoinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : WorkspaceRepository {
        var joinResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("joined-ws-1"))
        var joinCalls = 0
        var lastInviteCode: WorkspaceInviteCode? = null
        var joinDeferred: CompletableDeferred<Unit>? = null

        override fun observeWorkspaces(): Flow<List<Workspace>> = emptyFlow()
        override fun observeActiveWorkspace(): Flow<Workspace?> = emptyFlow()
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = emptyFlow()
        override suspend fun create(name: String) = RepositoryResult.Success(EntityId("created"))
        override suspend fun createWorkspace(command: CreateWorkspaceCommand) = RepositoryResult.Success(EntityId("created"))
        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand) = RepositoryResult.Success(Unit)
        override suspend fun deleteWorkspace(id: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun setActive(workspaceId: EntityId?) = RepositoryResult.Success(Unit)
        override suspend fun createInvite(workspaceId: EntityId) = RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> {
            joinCalls++
            lastInviteCode = inviteCode
            joinDeferred?.await()
            return joinResult
        }

        override suspend fun changeMemberRole(workspaceId: EntityId, userId: EntityId, role: WorkspaceRole) =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId) =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId) =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun leave(workspaceId: EntityId) =
            RepositoryResult.Failure(AppError.Validation("stub"))
    }

    @Test
    fun blank_code_is_rejected_without_repository_call() = runTest {
        val repo = FakeRepository()
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("   ")
        vm.submit()
        advanceUntilIdle()

        assertEquals(0, repo.joinCalls)
        assertNotNull(vm.uiState.value.codeError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun valid_code_is_passed_to_use_case_and_emits_navigation_on_success() = runTest {
        val repo = FakeRepository()
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("  FENIQO-2026  ")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals(WorkspaceInviteCode("FENIQO-2026"), repo.lastInviteCode)
        assertFalse(vm.uiState.value.isSubmitting)
        assertNull(vm.uiState.value.codeError)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(WorkspaceJoinEvent.NavigateBack, vm.events.first())
    }

    @Test
    fun failed_join_keeps_user_input_and_shows_mapped_error() = runTest {
        val repo = FakeRepository().apply {
            joinResult = RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("INVALID-CODE")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals("INVALID-CODE", vm.uiState.value.inviteCode)
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)

        vm.dismissError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun double_submit_triggers_only_one_repository_call() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val repo = FakeRepository().apply {
            joinDeferred = deferred
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("CODE-ABC")
        vm.submit()
        assertTrue(vm.uiState.value.isSubmitting)

        // İkinci submit çağrısı (çift tıklama koruması)
        vm.submit()
        assertEquals(1, repo.joinCalls)

        // İlk çağrıyı tamamla
        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(1, repo.joinCalls)
    }

    @Test
    fun invitation_expired_error_keeps_input_and_shows_expired_message() = runTest {
        val repo = FakeRepository().apply {
            joinResult = RepositoryResult.Failure(AppError.Validation("workspace_invitation_expired"))
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("EXPIRED-CODE")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals("EXPIRED-CODE", vm.uiState.value.inviteCode)
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_EXPIRED, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun invitation_limit_reached_error_keeps_input_and_shows_limit_message() = runTest {
        val repo = FakeRepository().apply {
            joinResult = RepositoryResult.Failure(AppError.Validation("workspace_invitation_limit_reached"))
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("LIMIT-CODE")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals("LIMIT-CODE", vm.uiState.value.inviteCode)
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_LIMIT_REACHED, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun invitation_not_found_error_keeps_input_and_shows_not_found_message() = runTest {
        val repo = FakeRepository().apply {
            joinResult = RepositoryResult.Failure(AppError.Validation("workspace_invitation_not_found"))
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("UNKNOWN-CODE")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals("UNKNOWN-CODE", vm.uiState.value.inviteCode)
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_NOT_FOUND, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun network_connection_error_keeps_input_and_shows_network_error_message() = runTest {
        val repo = FakeRepository().apply {
            joinResult = RepositoryResult.Failure(AppError.Network("network_unavailable"))
        }
        val vm = WorkspaceJoinViewModel(JoinWorkspaceUseCase(repo))

        vm.onInviteCodeChanged("NET-CODE")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.joinCalls)
        assertEquals("NET-CODE", vm.uiState.value.inviteCode)
        assertEquals(FinanceUiMessage.NETWORK_ERROR, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }
}
