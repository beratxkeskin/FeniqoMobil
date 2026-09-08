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
import com.feniqo.mobile.domain.usecase.CreateWorkspaceUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceCreateViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : WorkspaceRepository {
        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("created"))
        var createCalls = 0
        var command: CreateWorkspaceCommand? = null
        override fun observeWorkspaces(): Flow<List<Workspace>> = emptyFlow()
        override fun observeActiveWorkspace(): Flow<Workspace?> = emptyFlow()
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = emptyFlow()
        override suspend fun create(name: String) = RepositoryResult.Success(EntityId("created"))
        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> { createCalls++; this.command = command; return createResult }
        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand) = RepositoryResult.Success(Unit)
        override suspend fun deleteWorkspace(id: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun setActive(workspaceId: EntityId?) = RepositoryResult.Success(Unit)
        override suspend fun createInvite(workspaceId: EntityId) = RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun join(inviteCode: WorkspaceInviteCode) = RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun changeMemberRole(workspaceId: EntityId, userId: EntityId, role: WorkspaceRole) = RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId) = RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId) = RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun leave(workspaceId: EntityId) = RepositoryResult.Failure(AppError.Validation("stub"))
    }

    @Test fun blank_name_is_rejected_without_repository_call() = runTest {
        val repo = FakeRepository(); val vm = WorkspaceCreateViewModel(CreateWorkspaceUseCase(repo))
        vm.submit(); advanceUntilIdle()
        assertEquals(0, repo.createCalls); assertTrue(vm.uiState.value.nameError != null)
    }

    @Test fun successful_submit_normalizes_description_and_emits_navigation() = runTest {
        val repo = FakeRepository(); val vm = WorkspaceCreateViewModel(CreateWorkspaceUseCase(repo))
        vm.onNameChanged("  Ev  "); vm.onDescriptionChanged("   "); vm.submit(); advanceUntilIdle()
        assertEquals(1, repo.createCalls); assertEquals("Ev", repo.command?.name); assertNull(repo.command?.description); assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(WorkspaceCreateEvent.NavigateBack, vm.events.first())
    }

    @Test fun failed_submit_keeps_user_input_and_shows_mapped_error() = runTest {
        val repo = FakeRepository().apply { createResult = RepositoryResult.Failure(AppError.Validation("workspace_not_found")) }
        val vm = WorkspaceCreateViewModel(CreateWorkspaceUseCase(repo))
        vm.onNameChanged("Takım"); vm.onDescriptionChanged("Açıklama"); vm.submit(); advanceUntilIdle()
        assertEquals("Takım", vm.uiState.value.name); assertEquals("Açıklama", vm.uiState.value.description)
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, vm.uiState.value.errorMessage); assertFalse(vm.uiState.value.isSubmitting)
    }
}
