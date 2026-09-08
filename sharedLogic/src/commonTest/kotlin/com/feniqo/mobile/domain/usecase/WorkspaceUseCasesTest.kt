package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceUseCasesTest {

    private class FakeWorkspaceRepository(
        var workspaces: List<Workspace> = emptyList(),
        var activeWorkspace: Workspace? = null,
        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-created")),
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        var setActiveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        var joinResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-joined")),
        var members: List<WorkspaceMember> = emptyList(),
        var leaveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        var createInviteResult: RepositoryResult<WorkspaceInviteCode> =
            RepositoryResult.Success(WorkspaceInviteCode("TEST-INVITE-CODE")),
        var changeMemberRoleResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    ) : WorkspaceRepository {
        var lastCreateInviteWorkspaceId: EntityId? = null
        var lastChangeMemberRoleWorkspaceId: EntityId? = null
        var lastChangeMemberRoleUserId: EntityId? = null
        var lastChangeMemberRoleRole: WorkspaceRole? = null
        var lastCreateCommand: CreateWorkspaceCommand? = null
        var lastUpdateCommand: UpdateWorkspaceCommand? = null
        var lastDeletedId: EntityId? = null
        var lastActiveWorkspaceId: EntityId? = null
        var lastJoinInviteCode: WorkspaceInviteCode? = null
        var lastObservedMembersWorkspaceId: EntityId? = null
        var lastLeaveWorkspaceId: EntityId? = null
        var setActiveCallCount = 0

        override fun observeWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces)
        override fun observeActiveWorkspace(): Flow<Workspace?> = flowOf(activeWorkspace)
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> {
            lastObservedMembersWorkspaceId = workspaceId
            return flowOf(members)
        }
        override suspend fun create(name: String): RepositoryResult<EntityId> = createResult
        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> {
            lastCreateCommand = command
            return createResult
        }
        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }
        override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return deleteResult
        }
        override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> {
            lastActiveWorkspaceId = workspaceId
            setActiveCallCount++
            return setActiveResult
        }
        override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> {
            lastCreateInviteWorkspaceId = workspaceId
            return createInviteResult
        }
        override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> {
            lastJoinInviteCode = inviteCode
            return joinResult
        }
        override suspend fun changeMemberRole(
            workspaceId: EntityId,
            userId: EntityId,
            role: WorkspaceRole,
        ): RepositoryResult<Unit> {
            lastChangeMemberRoleWorkspaceId = workspaceId
            lastChangeMemberRoleUserId = userId
            lastChangeMemberRoleRole = role
            return changeMemberRoleResult
        }
        var lastTransferWorkspaceId: EntityId? = null
        var lastTransferTargetUserId: EntityId? = null
        var transferOwnershipResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> {
            lastLeaveWorkspaceId = workspaceId
            return leaveResult
        }
        override suspend fun transferOwnership(
            workspaceId: EntityId,
            targetUserId: EntityId,
        ): RepositoryResult<Unit> {
            lastTransferWorkspaceId = workspaceId
            lastTransferTargetUserId = targetUserId
            return transferOwnershipResult
        }

        var lastRemoveWorkspaceId: EntityId? = null
        var lastRemoveUserId: EntityId? = null
        var removeMemberResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun removeMember(
            workspaceId: EntityId,
            userId: EntityId,
        ): RepositoryResult<Unit> {
            lastRemoveWorkspaceId = workspaceId
            lastRemoveUserId = userId
            return removeMemberResult
        }
    }

    @Test
    fun observe_workspaces_use_case_delegates_to_repository() = runTest {
        val sample = listOf(
            Workspace(
                id = EntityId("ws-1"),
                name = "Aile Bütçesi",
                ownerId = EntityId("user-1"),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        val repo = FakeWorkspaceRepository(workspaces = sample)
        val useCase = ObserveWorkspacesUseCase(repo)

        val result = useCase().first()
        assertEquals(sample, result)
    }

    @Test
    fun observe_active_workspace_use_case_delegates_to_repository() = runTest {
        val active = Workspace(
            id = EntityId("ws-active"),
            name = "Aktif Alan",
            ownerId = EntityId("user-1"),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        val repo = FakeWorkspaceRepository(activeWorkspace = active)
        val useCase = ObserveActiveWorkspaceUseCase(repo)

        val result = useCase().first()
        assertEquals(active, result)
    }

    @Test
    fun create_workspace_use_case_delegates_command_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = CreateWorkspaceUseCase(repo)
        val command = CreateWorkspaceCommand(
            name = "İş Ortaklığı",
            type = WorkspaceType.SHARED,
            currency = Currency.USD,
            description = "Ortak proje bütçesi",
        )

        val result = useCase(command)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-created"), result.value)
        assertEquals(command, repo.lastCreateCommand)
    }

    @Test
    fun update_workspace_use_case_delegates_command_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = UpdateWorkspaceUseCase(repo)
        val command = UpdateWorkspaceCommand(
            id = EntityId("ws-1"),
            name = "Güncellenmiş Ad",
            type = WorkspaceType.SHARED,
            currency = Currency.TRY,
            description = "Açıklama",
        )

        val result = useCase(command)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(command, repo.lastUpdateCommand)
    }

    @Test
    fun delete_workspace_use_case_delegates_id_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = DeleteWorkspaceUseCase(repo)

        val result = useCase(EntityId("ws-delete-me"))
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-delete-me"), repo.lastDeletedId)
    }

    @Test
    fun set_active_workspace_use_case_delegates_target_and_null_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = SetActiveWorkspaceUseCase(repo)

        // Belirli bir ID ile setActive
        val setResult = useCase(EntityId("ws-target"))
        assertTrue(setResult is RepositoryResult.Success)
        assertEquals(EntityId("ws-target"), repo.lastActiveWorkspaceId)
        assertEquals(1, repo.setActiveCallCount)

        // null ile kişisel moda dönüş
        val clearResult = useCase(null)
        assertTrue(clearResult is RepositoryResult.Success)
        assertNull(repo.lastActiveWorkspaceId)
        assertEquals(2, repo.setActiveCallCount)
    }

    @Test
    fun join_workspace_use_case_delegates_invite_code_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = JoinWorkspaceUseCase(repo)
        val inviteCode = WorkspaceInviteCode("FENIQO-INVITE-123")

        val result = useCase(inviteCode)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-joined"), result.value)
        assertEquals(inviteCode, repo.lastJoinInviteCode)
    }

    @Test
    fun observe_workspace_members_use_case_delegates_workspace_id_to_repository() = runTest {
        val sampleMembers = listOf(
            WorkspaceMember(
                workspaceId = EntityId("ws-target"),
                userId = EntityId("user-1"),
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        val repo = FakeWorkspaceRepository(members = sampleMembers)
        val useCase = ObserveWorkspaceMembersUseCase(repo)

        val result = useCase(EntityId("ws-target")).first()
        assertEquals(sampleMembers, result)
        assertEquals(EntityId("ws-target"), repo.lastObservedMembersWorkspaceId)
    }

    @Test
    fun leave_workspace_use_case_delegates_workspace_id_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = LeaveWorkspaceUseCase(repo)

        val result = useCase(EntityId("ws-leave-me"))
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-leave-me"), repo.lastLeaveWorkspaceId)
    }

    @Test
    fun create_workspace_invite_use_case_delegates_to_repository() = runTest {
        val repo = FakeWorkspaceRepository(
            createInviteResult = RepositoryResult.Success(WorkspaceInviteCode("ABC-123-XYZ")),
        )
        val useCase = CreateWorkspaceInviteUseCase(repo)

        val result = useCase(EntityId("ws-invite-target"))
        assertTrue(result is RepositoryResult.Success)
        assertEquals(WorkspaceInviteCode("ABC-123-XYZ"), result.value)
        assertEquals(EntityId("ws-invite-target"), repo.lastCreateInviteWorkspaceId)
    }

    @Test
    fun change_workspace_member_role_use_case_delegates_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = ChangeWorkspaceMemberRoleUseCase(repo)

        val result = useCase(
            workspaceId = EntityId("ws-role-target"),
            userId = EntityId("user-target"),
            role = WorkspaceRole.VIEWER,
        )
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-role-target"), repo.lastChangeMemberRoleWorkspaceId)
        assertEquals(EntityId("user-target"), repo.lastChangeMemberRoleUserId)
        assertEquals(WorkspaceRole.VIEWER, repo.lastChangeMemberRoleRole)
    }

    @Test
    fun transfer_workspace_ownership_use_case_delegates_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = TransferWorkspaceOwnershipUseCase(repo)

        val result = useCase(
            workspaceId = EntityId("ws-transfer-target"),
            targetUserId = EntityId("user-new-owner"),
        )
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-transfer-target"), repo.lastTransferWorkspaceId)
        assertEquals(EntityId("user-new-owner"), repo.lastTransferTargetUserId)
    }

    @Test
    fun remove_workspace_member_use_case_delegates_to_repository() = runTest {
        val repo = FakeWorkspaceRepository()
        val useCase = RemoveWorkspaceMemberUseCase(repo)

        val result = useCase(
            workspaceId = EntityId("ws-remove-target"),
            userId = EntityId("user-to-remove"),
        )
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-remove-target"), repo.lastRemoveWorkspaceId)
        assertEquals(EntityId("user-to-remove"), repo.lastRemoveUserId)
    }
}
