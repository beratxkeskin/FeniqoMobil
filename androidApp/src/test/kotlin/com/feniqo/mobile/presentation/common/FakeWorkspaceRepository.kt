package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWorkspaceRepository(
    initialActiveWorkspace: Workspace? = null,
) : WorkspaceRepository {
    val activeWorkspaceFlow = MutableStateFlow<Workspace?>(initialActiveWorkspace)
    val workspacesFlow = MutableStateFlow<List<Workspace>>(
        initialActiveWorkspace?.let { listOf(it) } ?: emptyList(),
    )
    val membersFlow = MutableStateFlow<List<WorkspaceMember>>(emptyList())

    override fun observeWorkspaces(): Flow<List<Workspace>> = workspacesFlow
    override fun observeActiveWorkspace(): Flow<Workspace?> = activeWorkspaceFlow
    override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = membersFlow

    override suspend fun create(name: String): RepositoryResult<EntityId> =
        RepositoryResult.Success(EntityId("ws-1"))

    override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
        RepositoryResult.Success(EntityId("ws-1"))

    override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> =
        RepositoryResult.Success(WorkspaceInviteCode("INV123"))

    override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
        RepositoryResult.Success(EntityId("ws-1"))

    override suspend fun changeMemberRole(
        workspaceId: EntityId,
        userId: EntityId,
        role: WorkspaceRole,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun transferOwnership(
        workspaceId: EntityId,
        targetUserId: EntityId,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun removeMember(
        workspaceId: EntityId,
        userId: EntityId,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}
