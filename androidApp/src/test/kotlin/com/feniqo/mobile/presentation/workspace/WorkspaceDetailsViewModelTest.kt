package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.usecase.ChangeWorkspaceMemberRoleUseCase
import com.feniqo.mobile.domain.usecase.CreateWorkspaceInviteUseCase
import com.feniqo.mobile.domain.usecase.LeaveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspacesUseCase
import com.feniqo.mobile.domain.usecase.RemoveWorkspaceMemberUseCase
import com.feniqo.mobile.domain.usecase.TransferWorkspaceOwnershipUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
class WorkspaceDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())
        val membersFlow = MutableStateFlow<List<WorkspaceMember>>(emptyList())

        var leaveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var leaveCalls = 0
        var lastLeaveWorkspaceId: EntityId? = null
        var leaveDeferred: CompletableDeferred<Unit>? = null

        var createInviteResult: RepositoryResult<WorkspaceInviteCode> =
            RepositoryResult.Success(WorkspaceInviteCode("INVITE-999"))
        var createInviteCalls = 0
        var lastCreateInviteWorkspaceId: EntityId? = null
        var createInviteDeferred: CompletableDeferred<Unit>? = null

        var changeMemberRoleResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var changeMemberRoleCalls = 0
        var lastChangeRoleWorkspaceId: EntityId? = null
        var lastChangeRoleUserId: EntityId? = null
        var lastChangeRoleRole: WorkspaceRole? = null
        var changeRoleDeferred: CompletableDeferred<Unit>? = null

        override fun observeWorkspaces(): Flow<List<Workspace>> = workspacesFlow
        override fun observeActiveWorkspace(): Flow<Workspace?> = MutableStateFlow(null)
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = membersFlow

        override suspend fun create(name: String): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("created"))

        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("created"))

        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> {
            createInviteCalls++
            lastCreateInviteWorkspaceId = workspaceId
            createInviteDeferred?.await()
            return createInviteResult
        }

        override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))

        override suspend fun changeMemberRole(
            workspaceId: EntityId,
            userId: EntityId,
            role: WorkspaceRole,
        ): RepositoryResult<Unit> {
            changeMemberRoleCalls++
            lastChangeRoleWorkspaceId = workspaceId
            lastChangeRoleUserId = userId
            lastChangeRoleRole = role
            changeRoleDeferred?.await()
            return changeMemberRoleResult
        }

        var transferOwnershipResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var transferOwnershipCalls = 0
        var lastTransferOwnershipWorkspaceId: EntityId? = null
        var lastTransferOwnershipTargetUserId: EntityId? = null
        var transferOwnershipDeferred: CompletableDeferred<Unit>? = null

        override suspend fun transferOwnership(
            workspaceId: EntityId,
            targetUserId: EntityId,
        ): RepositoryResult<Unit> {
            transferOwnershipCalls++
            lastTransferOwnershipWorkspaceId = workspaceId
            lastTransferOwnershipTargetUserId = targetUserId
            transferOwnershipDeferred?.await()
            return transferOwnershipResult
        }

        override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> {
            leaveCalls++
            lastLeaveWorkspaceId = workspaceId
            leaveDeferred?.await()
            return leaveResult
        }

        var removeMemberResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var removeMemberCalls = 0
        var lastRemoveMemberWorkspaceId: EntityId? = null
        var lastRemoveMemberUserId: EntityId? = null
        var removeMemberDeferred: CompletableDeferred<Unit>? = null

        override suspend fun removeMember(
            workspaceId: EntityId,
            userId: EntityId,
        ): RepositoryResult<Unit> {
            removeMemberCalls++
            lastRemoveMemberWorkspaceId = workspaceId
            lastRemoveMemberUserId = userId
            removeMemberDeferred?.await()
            return removeMemberResult
        }
    }

    private class FakeAuthRepository : AuthRepository {
        val sessionFlow = MutableStateFlow<AuthSession?>(null)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private val workspaceRepo = FakeWorkspaceRepository()
    private val authRepo = FakeAuthRepository()

    private val observeWorkspacesUseCase = ObserveWorkspacesUseCase(workspaceRepo)
    private val observeWorkspaceMembersUseCase = ObserveWorkspaceMembersUseCase(workspaceRepo)
    private val observeAuthSessionUseCase = ObserveAuthSessionUseCase(authRepo)
    private val leaveWorkspaceUseCase = LeaveWorkspaceUseCase(workspaceRepo)
    private val createWorkspaceInviteUseCase = CreateWorkspaceInviteUseCase(workspaceRepo)
    private val changeWorkspaceMemberRoleUseCase = ChangeWorkspaceMemberRoleUseCase(workspaceRepo)
    private val transferWorkspaceOwnershipUseCase = TransferWorkspaceOwnershipUseCase(workspaceRepo)
    private val removeWorkspaceMemberUseCase = RemoveWorkspaceMemberUseCase(workspaceRepo)

    private fun createViewModel(workspaceId: String?): WorkspaceDetailsViewModel {
        val handle = SavedStateHandle(if (workspaceId != null) mapOf("workspaceId" to workspaceId) else emptyMap())
        return WorkspaceDetailsViewModel(
            savedStateHandle = handle,
            observeWorkspacesUseCase = observeWorkspacesUseCase,
            observeWorkspaceMembersUseCase = observeWorkspaceMembersUseCase,
            observeAuthSessionUseCase = observeAuthSessionUseCase,
            leaveWorkspaceUseCase = leaveWorkspaceUseCase,
            createWorkspaceInviteUseCase = createWorkspaceInviteUseCase,
            changeWorkspaceMemberRoleUseCase = changeWorkspaceMemberRoleUseCase,
            transferWorkspaceOwnershipUseCase = transferWorkspaceOwnershipUseCase,
            removeWorkspaceMemberUseCase = removeWorkspaceMemberUseCase,
        )
    }

    @Test
    fun invalidWorkspaceId_failsClosedImmediately_withoutRepositoryCalls() = runTest {
        val vm = createViewModel(workspaceId = "   ")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.workspace)
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, state.errorMessage)

        vm.requestLeave()
        assertFalse(vm.uiState.value.showLeaveConfirmation)
        vm.confirmLeave()
        assertEquals(0, workspaceRepo.leaveCalls)
    }

    @Test
    fun workspaceAndMembers_loadedFromFlow_andUserRoleDerived() = runTest {
        val wsId = EntityId("ws-100")
        val currentUserId = EntityId("user-me")
        val otherUserId = EntityId("user-other")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "me@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        val ws = Workspace(
            id = wsId,
            name = "Grup Projesi",
            ownerId = otherUserId,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            type = WorkspaceType.SHARED,
            currency = Currency.TRY,
            description = "Ortak harcama havuzu",
        )
        workspaceRepo.workspacesFlow.value = listOf(ws)

        val members = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = otherUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )
        workspaceRepo.membersFlow.value = members

        val vm = createViewModel(workspaceId = "ws-100")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.workspace)
        assertEquals("Grup Projesi", state.workspace?.name)
        assertEquals(WorkspaceType.SHARED, state.workspace?.type)
        assertEquals(Currency.TRY, state.workspace?.currency)
        assertEquals("Ortak harcama havuzu", state.workspace?.description)
        assertEquals(2, state.members.size)
        assertEquals(WorkspaceRole.EDITOR, state.currentUserRole)
        assertFalse(state.isOwner)
        assertTrue(state.canLeave)

        val currentMemberUi = state.members.first { it.isCurrentUser }
        assertEquals("Siz", currentMemberUi.displayName)
        assertEquals(WorkspaceRole.EDITOR, currentMemberUi.role)

        collectJob.cancel()
    }

    @Test
    fun ownerUser_cannotLeave_requestLeaveIsBlocked() = runTest {
        val wsId = EntityId("ws-owner-test")
        val currentUserId = EntityId("user-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        val ws = Workspace(
            id = wsId,
            name = "Sahip Alanı",
            ownerId = currentUserId,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-owner-test")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOwner)
        assertFalse(vm.uiState.value.canLeave)

        // Alandan ayrıl isteği engellenmeli
        vm.requestLeave()
        assertFalse(vm.uiState.value.showLeaveConfirmation)

        // Doğrudan confirmLeave çağrılsa bile engellenmeli
        vm.confirmLeave()
        assertEquals(0, workspaceRepo.leaveCalls)

        collectJob.cancel()
    }

    @Test
    fun nonOwnerUser_confirmationFlow_andSuccessfulLeaveEmitsNavigateBack() = runTest {
        val wsId = EntityId("ws-member-leave")
        val currentUserId = EntityId("user-editor")
        val ownerUserId = EntityId("user-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "editor@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        val ws = Workspace(
            id = wsId,
            name = "Ayrılınacak Alan",
            ownerId = ownerUserId,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-member-leave")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canLeave)

        // 1. Onay diyaloğunu aç
        vm.requestLeave()
        assertTrue(vm.uiState.value.showLeaveConfirmation)

        // 2. Vazgeç
        vm.dismissLeaveConfirmation()
        assertFalse(vm.uiState.value.showLeaveConfirmation)

        // 3. Tekrar aç ve onayla
        vm.requestLeave()
        assertTrue(vm.uiState.value.showLeaveConfirmation)

        vm.confirmLeave()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.leaveCalls)
        assertEquals(wsId, workspaceRepo.lastLeaveWorkspaceId)
        assertFalse(vm.uiState.value.showLeaveConfirmation)
        assertFalse(vm.uiState.value.isLeaving)
        assertEquals(WorkspaceDetailsEvent.NavigateBack, vm.events.first())

        collectJob.cancel()
    }

    @Test
    fun failedLeave_keepsDialogState_andShowsMappedError() = runTest {
        val wsId = EntityId("ws-fail-test")
        val currentUserId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "viewer@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        val ws = Workspace(
            id = wsId,
            name = "Hata Testi",
            ownerId = EntityId("owner"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.leaveResult = RepositoryResult.Failure(AppError.Validation("cannot_leave"))

        val vm = createViewModel(workspaceId = "ws-fail-test")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        vm.requestLeave()
        vm.confirmLeave()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.leaveCalls)
        assertFalse(vm.uiState.value.isLeaving)
        assertNotNull(vm.uiState.value.errorMessage)

        vm.dismissError()
        assertNull(vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun doubleConfirm_triggersOnlyOneRepositoryCall() = runTest {
        val wsId = EntityId("ws-double-submit")
        val currentUserId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "viewer@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        val ws = Workspace(
            id = wsId,
            name = "Çift Submit",
            ownerId = EntityId("owner"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        workspaceRepo.workspacesFlow.value = listOf(ws)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val deferred = CompletableDeferred<Unit>()
        workspaceRepo.leaveDeferred = deferred

        val vm = createViewModel(workspaceId = "ws-double-submit")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        vm.requestLeave()
        vm.confirmLeave()
        assertTrue(vm.uiState.value.isLeaving)

        // İkinci confirm çağrısı engellenmeli
        vm.confirmLeave()
        assertEquals(1, workspaceRepo.leaveCalls)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLeaving)
        assertEquals(1, workspaceRepo.leaveCalls)

        collectJob.cancel()
    }

    @Test
    fun owner_can_create_invite_puts_transient_code_in_state() = runTest {
        val wsId = EntityId("ws-invite-1")
        val currentUserId = EntityId("user-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Davet Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-invite-1")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOwner)
        assertTrue(vm.uiState.value.canCreateInvite)

        vm.createInvite()
        advanceUntilIdle()

        assertEquals("INVITE-999", vm.uiState.value.generatedInviteCode)
        assertEquals(1, workspaceRepo.createInviteCalls)
        assertEquals(wsId, workspaceRepo.lastCreateInviteWorkspaceId)
        assertFalse(vm.uiState.value.isCreatingInvite)

        collectJob.cancel()
    }

    @Test
    fun non_owner_cannot_create_invite_use_case_not_called() = runTest {
        val wsId = EntityId("ws-invite-2")
        val currentUserId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "viewer@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "İzleyici Alanı",
                ownerId = EntityId("someone-else"),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-invite-2")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isOwner)
        assertFalse(vm.uiState.value.canCreateInvite)

        vm.createInvite()
        advanceUntilIdle()

        assertNull(vm.uiState.value.generatedInviteCode)
        assertEquals(0, workspaceRepo.createInviteCalls)

        collectJob.cancel()
    }

    @Test
    fun dismissing_invite_dialog_clears_code_from_state() = runTest {
        val wsId = EntityId("ws-invite-3")
        val currentUserId = EntityId("user-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Davet Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-invite-3")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        vm.createInvite()
        advanceUntilIdle()
        assertEquals("INVITE-999", vm.uiState.value.generatedInviteCode)

        vm.dismissInviteCodeDialog()
        assertNull(vm.uiState.value.generatedInviteCode)

        collectJob.cancel()
    }

    @Test
    fun owner_can_change_other_editor_or_viewer_member_role() = runTest {
        val wsId = EntityId("ws-role-1")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Rol Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-role-1")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val editorMember = vm.uiState.value.members.first { it.userId == targetUserId }
        assertEquals(WorkspaceRole.EDITOR, editorMember.role)
        assertFalse(editorMember.isCurrentUser)

        // Rol değişim isteği açılır
        vm.requestChangeMemberRole(editorMember, WorkspaceRole.VIEWER)
        val pending = vm.uiState.value.pendingRoleChange
        assertNotNull(pending)
        assertEquals(targetUserId, pending!!.member.userId)
        assertEquals(WorkspaceRole.VIEWER, pending.newRole)

        // Onaylanır
        vm.confirmMemberRoleChange()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.changeMemberRoleCalls)
        assertEquals(wsId, workspaceRepo.lastChangeRoleWorkspaceId)
        assertEquals(targetUserId, workspaceRepo.lastChangeRoleUserId)
        assertEquals(WorkspaceRole.VIEWER, workspaceRepo.lastChangeRoleRole)
        assertNull(vm.uiState.value.pendingRoleChange)
        assertFalse(vm.uiState.value.isChangingMemberRole)

        collectJob.cancel()
    }

    @Test
    fun owner_cannot_change_own_role() = runTest {
        val wsId = EntityId("ws-role-2")
        val currentUserId = EntityId("user-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Rol Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-role-2")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val meMember = vm.uiState.value.members.first { it.userId == currentUserId }
        assertTrue(meMember.isCurrentUser)

        vm.requestChangeMemberRole(meMember, WorkspaceRole.EDITOR)
        assertNull(vm.uiState.value.pendingRoleChange)
        assertEquals(0, workspaceRepo.changeMemberRoleCalls)

        collectJob.cancel()
    }

    @Test
    fun owner_cannot_assign_owner_role_to_target() = runTest {
        val wsId = EntityId("ws-role-3")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Rol Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-role-3")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val viewerMember = vm.uiState.value.members.first { it.userId == targetUserId }

        vm.requestChangeMemberRole(viewerMember, WorkspaceRole.OWNER)
        assertNull(vm.uiState.value.pendingRoleChange)
        assertEquals(0, workspaceRepo.changeMemberRoleCalls)

        collectJob.cancel()
    }

    @Test
    fun non_owner_cannot_change_roles() = runTest {
        val wsId = EntityId("ws-role-4")
        val currentUserId = EntityId("user-viewer")
        val otherUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "viewer@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Rol Alanı",
                ownerId = EntityId("someone-else"),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = otherUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-role-4")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isOwner)
        assertFalse(vm.uiState.value.canManageRoles)

        val editorMember = vm.uiState.value.members.first { it.userId == otherUserId }

        vm.requestChangeMemberRole(editorMember, WorkspaceRole.VIEWER)
        assertNull(vm.uiState.value.pendingRoleChange)
        vm.confirmMemberRoleChange()
        assertEquals(0, workspaceRepo.changeMemberRoleCalls)

        collectJob.cancel()
    }

    @Test
    fun role_change_error_and_double_submit_protection() = runTest {
        val wsId = EntityId("ws-role-5")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Rol Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val deferred = CompletableDeferred<Unit>()
        workspaceRepo.changeRoleDeferred = deferred
        workspaceRepo.changeMemberRoleResult = RepositoryResult.Failure(AppError.Validation("cannot_change_own_role"))

        val vm = createViewModel(workspaceId = "ws-role-5")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val editorMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestChangeMemberRole(editorMember, WorkspaceRole.VIEWER)
        assertNotNull(vm.uiState.value.pendingRoleChange)

        vm.confirmMemberRoleChange()
        assertTrue(vm.uiState.value.isChangingMemberRole)

        // İkinci submit engellenmeli (double submit protection)
        vm.confirmMemberRoleChange()
        assertEquals(1, workspaceRepo.changeMemberRoleCalls)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isChangingMemberRole)
        assertEquals(1, workspaceRepo.changeMemberRoleCalls)
        // Hata state'i dolmalı ve pending confirmation açık kalmalı
        assertEquals(FinanceUiMessage.WORKSPACE_CANNOT_CHANGE_OWN_ROLE, vm.uiState.value.errorMessage)
        assertNotNull(vm.uiState.value.pendingRoleChange)

        collectJob.cancel()
    }

    @Test
    fun owner_can_request_transfer_to_editor_or_viewer_and_dismiss() = runTest {
        val wsId = EntityId("ws-transfer-1")
        val currentUserId = EntityId("user-owner")
        val editorId = EntityId("user-editor")
        val viewerId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = editorId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = viewerId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(3000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-1")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val editorMember = vm.uiState.value.members.first { it.userId == editorId }
        val viewerMember = vm.uiState.value.members.first { it.userId == viewerId }

        // OWNER editor hedef seçebilir
        vm.requestOwnershipTransfer(editorMember)
        assertEquals(editorMember, vm.uiState.value.pendingOwnershipTransferTarget)

        // İptal edebilir
        vm.dismissOwnershipTransferConfirmation()
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)

        // OWNER viewer hedef seçebilir
        vm.requestOwnershipTransfer(viewerMember)
        assertEquals(viewerMember, vm.uiState.value.pendingOwnershipTransferTarget)

        collectJob.cancel()
    }

    @Test
    fun non_owner_cannot_request_or_confirm_transfer() = runTest {
        val wsId = EntityId("ws-transfer-2")
        val currentUserId = EntityId("user-editor-actor")
        val ownerId = EntityId("user-real-owner")
        val viewerId = EntityId("user-viewer")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "editor@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = ownerId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = ownerId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = viewerId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-2")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val viewerMember = vm.uiState.value.members.first { it.userId == viewerId }

        // Non-owner transfer isteği başlatamaz
        vm.requestOwnershipTransfer(viewerMember)
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)

        // Confirm çağrılsa bile repo çağrısı yapılmaz
        vm.confirmOwnershipTransfer()
        assertEquals(0, workspaceRepo.transferOwnershipCalls)

        collectJob.cancel()
    }

    @Test
    fun current_user_or_owner_cannot_be_transfer_target() = runTest {
        val wsId = EntityId("ws-transfer-3")
        val currentUserId = EntityId("user-owner")
        val otherOwnerId = EntityId("user-second-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = otherOwnerId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-3")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val currentUserMember = vm.uiState.value.members.first { it.userId == currentUserId }
        val otherOwnerMember = vm.uiState.value.members.first { it.userId == otherOwnerId }

        vm.requestOwnershipTransfer(currentUserMember)
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)

        vm.requestOwnershipTransfer(otherOwnerMember)
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)

        // Uygun hedef de yok
        assertTrue(vm.uiState.value.eligibleOwnershipTransferTargets.isEmpty())
        assertFalse(vm.uiState.value.canTransferOwnership)

        collectJob.cancel()
    }

    @Test
    fun only_editor_and_viewer_roles_can_be_transfer_targets() = runTest {
        val wsId = EntityId("ws-transfer-roles")
        val currentUserId = EntityId("user-owner")
        val editorId = EntityId("user-editor")
        val viewerId = EntityId("user-viewer")
        val anotherOwnerId = EntityId("user-another-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = editorId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = viewerId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(3000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = anotherOwnerId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(4000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-roles")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val eligible = vm.uiState.value.eligibleOwnershipTransferTargets
        assertEquals(2, eligible.size)
        assertTrue(eligible.any { it.userId == editorId && it.role == WorkspaceRole.EDITOR })
        assertTrue(eligible.any { it.userId == viewerId && it.role == WorkspaceRole.VIEWER })
        assertFalse(eligible.any { it.userId == currentUserId })
        assertFalse(eligible.any { it.userId == anotherOwnerId })

        collectJob.cancel()
    }

    @Test
    fun successful_transfer_calls_usecase_with_correct_ids_and_closes_pending_state() = runTest {
        val wsId = EntityId("ws-transfer-4")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-4")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestOwnershipTransfer(targetMember)
        assertEquals(targetMember, vm.uiState.value.pendingOwnershipTransferTarget)

        vm.confirmOwnershipTransfer()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.transferOwnershipCalls)
        assertEquals(wsId, workspaceRepo.lastTransferOwnershipWorkspaceId)
        assertEquals(targetUserId, workspaceRepo.lastTransferOwnershipTargetUserId)
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)
        assertFalse(vm.uiState.value.isTransferringOwnership)
        assertNull(vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun failed_transfer_sets_error_in_state_and_keeps_pending_target() = runTest {
        val wsId = EntityId("ws-transfer-5")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        workspaceRepo.transferOwnershipResult = RepositoryResult.Failure(
            AppError.Conflict("local_uncommitted_changes_prevent_ownership_transfer")
        )

        val vm = createViewModel(workspaceId = "ws-transfer-5")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestOwnershipTransfer(targetMember)

        vm.confirmOwnershipTransfer()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.transferOwnershipCalls)
        assertFalse(vm.uiState.value.isTransferringOwnership)
        assertEquals(targetMember, vm.uiState.value.pendingOwnershipTransferTarget)
        assertEquals(FinanceUiMessage.WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER, vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun double_confirm_executes_only_once() = runTest {
        val wsId = EntityId("ws-transfer-6")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val deferred = CompletableDeferred<Unit>()
        workspaceRepo.transferOwnershipDeferred = deferred

        val vm = createViewModel(workspaceId = "ws-transfer-6")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestOwnershipTransfer(targetMember)

        vm.confirmOwnershipTransfer()
        assertTrue(vm.uiState.value.isTransferringOwnership)

        // İkinci confirm çağrısı engellenmeli
        vm.confirmOwnershipTransfer()
        assertEquals(1, workspaceRepo.transferOwnershipCalls)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isTransferringOwnership)
        assertEquals(1, workspaceRepo.transferOwnershipCalls)
        assertNull(vm.uiState.value.pendingOwnershipTransferTarget)

        collectJob.cancel()
    }

    @Test
    fun room_flow_updating_owner_enables_leave_action_for_former_owner() = runTest {
        val wsId = EntityId("ws-transfer-7")
        val currentUserId = EntityId("user-former-owner")
        val targetUserId = EntityId("user-new-owner")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "former_owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        // Başlangıç: current user OWNER
        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-7")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOwner)
        assertFalse(vm.uiState.value.canLeave)

        // Room Flow yeni snapshot'ı yayar: targetUserId artık OWNER, currentUserId artık EDITOR
        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = targetUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )
        advanceUntilIdle()

        // Eski owner artık EDITOR olmuştur ve ayrılma aksiyonu etkinleşmiştir
        assertFalse(vm.uiState.value.isOwner)
        assertEquals(WorkspaceRole.EDITOR, vm.uiState.value.currentUserRole)
        assertTrue(vm.uiState.value.canLeave)

        // Alandan ayrılma isteği başlatılabilir
        vm.requestLeave()
        assertTrue(vm.uiState.value.showLeaveConfirmation)

        collectJob.cancel()
    }

    @Test
    fun confirm_transfer_when_target_disappears_from_flow_fails_closed_with_stale_error() = runTest {
        val wsId = EntityId("ws-transfer-8")
        val currentUserId = EntityId("user-owner")
        val targetUserId = EntityId("user-editor")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(5000L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Transfer Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-transfer-8")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestOwnershipTransfer(targetMember)
        assertEquals(targetMember, vm.uiState.value.pendingOwnershipTransferTarget)

        // Hedef üye Flow'dan kayboluyor (üyelik silinmiş / alandan ayrılmış)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        advanceUntilIdle()

        // Confirm çağrısı yapıldığında:
        vm.confirmOwnershipTransfer()

        // RPC çağrısı yapılmamalı
        assertEquals(0, workspaceRepo.transferOwnershipCalls)
        // İşlem state'i başlatılmamalı
        assertFalse(vm.uiState.value.isTransferringOwnership)
        // Pending hedef korunmalı
        assertEquals(targetMember, vm.uiState.value.pendingOwnershipTransferTarget)
        // Stale/güncel değil hatası gösterilmeli
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_VERSION_CONFLICT, vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun owner_can_request_and_confirm_member_removal_success() = runTest {
        val wsId = EntityId("ws-rem-1")
        val currentUserId = EntityId("user-owner-1")
        val targetUserId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-rem-1")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOwner)
        assertTrue(vm.uiState.value.canRemoveMembers)

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestMemberRemoval(targetMember)
        assertEquals(targetMember, vm.uiState.value.pendingMemberRemoval)

        vm.confirmMemberRemoval()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.removeMemberCalls)
        assertEquals(wsId, workspaceRepo.lastRemoveMemberWorkspaceId)
        assertEquals(targetUserId, workspaceRepo.lastRemoveMemberUserId)
        assertNull(vm.uiState.value.pendingMemberRemoval)
        assertFalse(vm.uiState.value.isRemovingMember)

        collectJob.cancel()
    }

    @Test
    fun owner_cannot_remove_self_or_another_owner_via_requestMemberRemoval() = runTest {
        val wsId = EntityId("ws-rem-2")
        val currentUserId = EntityId("user-owner-1")
        val anotherOwnerId = EntityId("user-owner-2")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = anotherOwnerId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-rem-2")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        // 1. Kendi kullanıcısı (isCurrentUser = true)
        val selfMember = vm.uiState.value.members.first { it.userId == currentUserId }
        vm.requestMemberRemoval(selfMember)
        assertNull(vm.uiState.value.pendingMemberRemoval)

        // 2. Başka OWNER
        val anotherOwner = vm.uiState.value.members.first { it.userId == anotherOwnerId }
        vm.requestMemberRemoval(anotherOwner)
        assertNull(vm.uiState.value.pendingMemberRemoval)

        collectJob.cancel()
    }

    @Test
    fun non_owner_cannot_request_member_removal() = runTest {
        val wsId = EntityId("ws-rem-3")
        val ownerId = EntityId("user-owner-1")
        val currentViewerId = EntityId("user-viewer-1")
        val targetEditorId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentViewerId,
            email = "viewer@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = ownerId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = ownerId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentViewerId,
                role = WorkspaceRole.VIEWER,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetEditorId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(3000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-rem-3")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isOwner)
        assertFalse(vm.uiState.value.canRemoveMembers)

        val targetMember = vm.uiState.value.members.first { it.userId == targetEditorId }
        vm.requestMemberRemoval(targetMember)
        assertNull(vm.uiState.value.pendingMemberRemoval)

        collectJob.cancel()
    }

    @Test
    fun confirm_removal_when_target_disappears_from_flow_fails_closed_with_stale_error() = runTest {
        val wsId = EntityId("ws-rem-4")
        val currentUserId = EntityId("user-owner-1")
        val targetUserId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-rem-4")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestMemberRemoval(targetMember)
        assertEquals(targetMember, vm.uiState.value.pendingMemberRemoval)

        // Hedef üye Flow'dan kayboluyor (silindi / ayrıldı)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        advanceUntilIdle()

        // Confirm çağrıldığında
        vm.confirmMemberRemoval()

        // Çağrı yapılmamalı
        assertEquals(0, workspaceRepo.removeMemberCalls)
        // isRemovingMember başlamamalı
        assertFalse(vm.uiState.value.isRemovingMember)
        // Hata gösterilmeli
        assertEquals(FinanceUiMessage.WORKSPACE_TARGET_MEMBER_NOT_FOUND, vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun confirm_removal_failure_preserves_dialog_and_shows_typed_error() = runTest {
        val wsId = EntityId("ws-rem-5")
        val currentUserId = EntityId("user-owner-1")
        val targetUserId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        workspaceRepo.removeMemberResult = RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))

        val vm = createViewModel(workspaceId = "ws-rem-5")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestMemberRemoval(targetMember)
        vm.confirmMemberRemoval()
        advanceUntilIdle()

        assertEquals(1, workspaceRepo.removeMemberCalls)
        assertFalse(vm.uiState.value.isRemovingMember)
        assertEquals(targetMember, vm.uiState.value.pendingMemberRemoval)
        assertEquals(FinanceUiMessage.WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE, vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun confirm_removal_double_click_guard_prevents_duplicate_execution() = runTest {
        val wsId = EntityId("ws-rem-6")
        val currentUserId = EntityId("user-owner-1")
        val targetUserId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val deferred = CompletableDeferred<Unit>()
        workspaceRepo.removeMemberDeferred = deferred

        val vm = createViewModel(workspaceId = "ws-rem-6")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestMemberRemoval(targetMember)
        vm.confirmMemberRemoval()

        assertTrue(vm.uiState.value.isRemovingMember)
        assertEquals(1, workspaceRepo.removeMemberCalls)

        // İkinci tıklama askıdayken engellenmeli
        vm.confirmMemberRemoval()
        assertEquals(1, workspaceRepo.removeMemberCalls)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRemovingMember)
        assertNull(vm.uiState.value.pendingMemberRemoval)

        collectJob.cancel()
    }

    @Test
    fun dismiss_member_removal_confirmation_clears_pending_target() = runTest {
        val wsId = EntityId("ws-rem-7")
        val currentUserId = EntityId("user-owner-1")
        val targetUserId = EntityId("user-editor-1")

        authRepo.sessionFlow.value = AuthSession(
            userId = currentUserId,
            email = "owner@test.com",
            expiresAt = Instant.fromEpochMilliseconds(999999L),
        )

        workspaceRepo.workspacesFlow.value = listOf(
            Workspace(
                id = wsId,
                name = "Test Alanı",
                ownerId = currentUserId,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(
                workspaceId = wsId,
                userId = currentUserId,
                role = WorkspaceRole.OWNER,
                joinedAt = Instant.fromEpochMilliseconds(1000L),
            ),
            WorkspaceMember(
                workspaceId = wsId,
                userId = targetUserId,
                role = WorkspaceRole.EDITOR,
                joinedAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val vm = createViewModel(workspaceId = "ws-rem-7")
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val targetMember = vm.uiState.value.members.first { it.userId == targetUserId }
        vm.requestMemberRemoval(targetMember)
        assertEquals(targetMember, vm.uiState.value.pendingMemberRemoval)

        vm.dismissMemberRemovalConfirmation()
        assertNull(vm.uiState.value.pendingMemberRemoval)

        collectJob.cancel()
    }
}
