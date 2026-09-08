package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ChangeWorkspaceMemberRoleUseCase
import com.feniqo.mobile.domain.usecase.CreateWorkspaceInviteUseCase
import com.feniqo.mobile.domain.usecase.LeaveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspacesUseCase
import com.feniqo.mobile.domain.usecase.RemoveWorkspaceMemberUseCase
import com.feniqo.mobile.domain.usecase.TransferWorkspaceOwnershipUseCase
import com.feniqo.mobile.navigation.WorkspaceDetailsRouteIdResult
import com.feniqo.mobile.navigation.parseWorkspaceDetailsRouteId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WorkspaceDetailsEvent {
    data object NavigateBack : WorkspaceDetailsEvent
}

@HiltViewModel
class WorkspaceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWorkspacesUseCase: ObserveWorkspacesUseCase,
    observeWorkspaceMembersUseCase: ObserveWorkspaceMembersUseCase,
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val leaveWorkspaceUseCase: LeaveWorkspaceUseCase,
    private val createWorkspaceInviteUseCase: CreateWorkspaceInviteUseCase,
    private val changeWorkspaceMemberRoleUseCase: ChangeWorkspaceMemberRoleUseCase,
    private val transferWorkspaceOwnershipUseCase: TransferWorkspaceOwnershipUseCase,
    private val removeWorkspaceMemberUseCase: RemoveWorkspaceMemberUseCase,
) : ViewModel() {

    private val targetWorkspaceId: EntityId?

    private val _uiState = MutableStateFlow(WorkspaceDetailsUiState())
    val uiState: StateFlow<WorkspaceDetailsUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkspaceDetailsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var leaveJob: Job? = null
    private var inviteJob: Job? = null
    private var roleChangeJob: Job? = null
    private var transferOwnershipJob: Job? = null
    private var removeMemberJob: Job? = null

    init {
        val rawId = savedStateHandle.get<String>("workspaceId")
        when (val parseResult = parseWorkspaceDetailsRouteId(rawId)) {
            is WorkspaceDetailsRouteIdResult.InvalidId -> {
                targetWorkspaceId = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workspace = null,
                        errorMessage = FinanceUiMessage.WORKSPACE_NOT_FOUND,
                    )
                }
            }
            is WorkspaceDetailsRouteIdResult.ValidId -> {
                val validId = parseResult.id
                targetWorkspaceId = validId

                viewModelScope.launch {
                    combine(
                        observeWorkspacesUseCase(),
                        observeWorkspaceMembersUseCase(validId),
                        observeAuthSessionUseCase(),
                    ) { workspaces, members, session ->
                        Triple(workspaces, members, session)
                    }.collect { (workspaces, members, session) ->
                        val workspace = workspaces.firstOrNull { it.id == validId }
                        if (workspace == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    workspace = null,
                                    members = emptyList(),
                                    currentUserRole = null,
                                    errorMessage = FinanceUiMessage.WORKSPACE_NOT_FOUND,
                                )
                            }
                        } else {
                            val currentUserId = session?.userId
                            val memberUiModels = members.map { member ->
                                val isCurrentUser = currentUserId != null && member.userId == currentUserId
                                val displayName = if (isCurrentUser) {
                                    "Siz"
                                } else {
                                    "Kullanıcı ${member.userId.value.take(8)}"
                                }
                                WorkspaceMemberUiModel(
                                    userId = member.userId,
                                    displayName = displayName,
                                    role = member.role,
                                    isCurrentUser = isCurrentUser,
                                )
                            }
                            val currentUserRole = members.find { it.userId == currentUserId }?.role
                                ?: if (workspace.ownerId == currentUserId) WorkspaceRole.OWNER else null

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    workspace = WorkspaceDetailsUiModel(
                                        id = workspace.id,
                                        name = workspace.name,
                                        type = workspace.type,
                                        currency = workspace.currency,
                                        description = workspace.description,
                                        ownerId = workspace.ownerId,
                                    ),
                                    members = memberUiModels,
                                    currentUserRole = currentUserRole,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun requestLeave() {
        val state = _uiState.value
        if (targetWorkspaceId == null || state.workspace == null || state.isOwner || state.currentUserRole == null || state.isLeaving) {
            return
        }
        _uiState.update { it.copy(showLeaveConfirmation = true) }
    }

    fun dismissLeaveConfirmation() {
        if (_uiState.value.isLeaving) return
        _uiState.update { it.copy(showLeaveConfirmation = false) }
    }

    fun confirmLeave() {
        val state = _uiState.value
        val workspaceId = targetWorkspaceId ?: return
        if (state.workspace == null || state.isOwner || state.currentUserRole == null || state.isLeaving || leaveJob?.isActive == true) {
            return
        }

        _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
        leaveJob = viewModelScope.launch {
            try {
                when (val result = leaveWorkspaceUseCase(workspaceId)) {
                    is RepositoryResult.Success -> {
                        _uiState.update { it.copy(showLeaveConfirmation = false) }
                        _events.send(WorkspaceDetailsEvent.NavigateBack)
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isLeaving = false) }
                leaveJob = null
            }
        }
    }

    fun createInvite() {
        val state = _uiState.value
        val workspaceId = targetWorkspaceId ?: return
        if (!state.isOwner || state.isCreatingInvite || inviteJob?.isActive == true) {
            return
        }

        _uiState.update { it.copy(isCreatingInvite = true, errorMessage = null) }
        inviteJob = viewModelScope.launch {
            try {
                when (val result = createWorkspaceInviteUseCase(workspaceId)) {
                    is RepositoryResult.Success -> {
                        _uiState.update { it.copy(generatedInviteCode = result.value.value) }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isCreatingInvite = false) }
                inviteJob = null
            }
        }
    }

    fun dismissInviteCodeDialog() {
        _uiState.update { it.copy(generatedInviteCode = null) }
    }

    fun requestChangeMemberRole(member: WorkspaceMemberUiModel, newRole: WorkspaceRole) {
        val state = _uiState.value
        if (!state.isOwner || member.isCurrentUser || member.role == WorkspaceRole.OWNER || newRole == WorkspaceRole.OWNER || member.role == newRole || state.isChangingMemberRole) {
            return
        }

        _uiState.update {
            it.copy(
                pendingRoleChange = PendingRoleChange(
                    member = member,
                    newRole = newRole,
                ),
            )
        }
    }

    fun selectPendingRole(newRole: WorkspaceRole) {
        if (newRole == WorkspaceRole.OWNER || _uiState.value.isChangingMemberRole) return
        val currentPending = _uiState.value.pendingRoleChange ?: return
        if (currentPending.member.role == newRole) return
        _uiState.update { it.copy(pendingRoleChange = currentPending.copy(newRole = newRole)) }
    }

    fun dismissRoleChangeConfirmation() {
        if (_uiState.value.isChangingMemberRole) return
        _uiState.update { it.copy(pendingRoleChange = null) }
    }

    fun confirmMemberRoleChange() {
        val state = _uiState.value
        val workspaceId = targetWorkspaceId ?: return
        val pending = state.pendingRoleChange ?: return
        if (!state.isOwner || state.isChangingMemberRole || roleChangeJob?.isActive == true) {
            return
        }
        if (pending.member.isCurrentUser || pending.member.role == WorkspaceRole.OWNER || pending.newRole == WorkspaceRole.OWNER) {
            _uiState.update { it.copy(pendingRoleChange = null) }
            return
        }

        _uiState.update { it.copy(isChangingMemberRole = true, errorMessage = null) }
        roleChangeJob = viewModelScope.launch {
            try {
                when (val result = changeWorkspaceMemberRoleUseCase(workspaceId, pending.member.userId, pending.newRole)) {
                    is RepositoryResult.Success -> {
                        _uiState.update { it.copy(pendingRoleChange = null) }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isChangingMemberRole = false) }
                roleChangeJob = null
            }
        }
    }

    fun requestOwnershipTransfer(member: WorkspaceMemberUiModel) {
        val state = _uiState.value
        if (!state.isOwner ||
            member.isCurrentUser ||
            member.role !in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER) ||
            state.isTransferringOwnership
        ) {
            return
        }

        _uiState.update {
            it.copy(pendingOwnershipTransferTarget = member)
        }
    }

    fun dismissOwnershipTransferConfirmation() {
        if (_uiState.value.isTransferringOwnership) return
        _uiState.update { it.copy(pendingOwnershipTransferTarget = null) }
    }

    fun confirmOwnershipTransfer() {
        val state = _uiState.value
        val workspaceId = targetWorkspaceId ?: return
        val pending = state.pendingOwnershipTransferTarget ?: return

        if (!state.isOwner || state.isTransferringOwnership || transferOwnershipJob?.isActive == true) {
            return
        }

        // Flow yarış durumu: pending hedef hâlâ güncel eligibleOwnershipTransferTargets listesinde mi doğrula
        val isStillEligible = state.eligibleOwnershipTransferTargets.any { it.userId == pending.userId }
        if (!isStillEligible) {
            _uiState.update {
                it.copy(errorMessage = FinanceUiMessage.WORKSPACE_TRANSFER_VERSION_CONFLICT)
            }
            return
        }

        _uiState.update { it.copy(isTransferringOwnership = true, errorMessage = null) }
        transferOwnershipJob = viewModelScope.launch {
            try {
                when (val result = transferWorkspaceOwnershipUseCase(workspaceId, pending.userId)) {
                    is RepositoryResult.Success -> {
                        _uiState.update { it.copy(pendingOwnershipTransferTarget = null) }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isTransferringOwnership = false) }
                transferOwnershipJob = null
            }
        }
    }

    fun requestMemberRemoval(member: WorkspaceMemberUiModel) {
        val state = _uiState.value
        if (!state.isOwner ||
            member.isCurrentUser ||
            member.role !in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER) ||
            state.isRemovingMember
        ) {
            return
        }

        _uiState.update {
            it.copy(pendingMemberRemoval = member)
        }
    }

    fun dismissMemberRemovalConfirmation() {
        if (_uiState.value.isRemovingMember) return
        _uiState.update { it.copy(pendingMemberRemoval = null) }
    }

    fun confirmMemberRemoval() {
        val state = _uiState.value
        val workspaceId = targetWorkspaceId ?: return
        val pending = state.pendingMemberRemoval ?: return

        if (!state.isOwner || state.isRemovingMember || removeMemberJob?.isActive == true) {
            return
        }

        // Flow yarış durumu: pending hedef hâlâ güncel eligibleMemberRemovalTargets listesinde mi doğrula
        val isStillEligible = state.eligibleMemberRemovalTargets.any { it.userId == pending.userId }
        if (!isStillEligible) {
            _uiState.update {
                it.copy(errorMessage = FinanceUiMessage.WORKSPACE_TARGET_MEMBER_NOT_FOUND)
            }
            return
        }

        _uiState.update { it.copy(isRemovingMember = true, errorMessage = null) }
        removeMemberJob = viewModelScope.launch {
            try {
                when (val result = removeWorkspaceMemberUseCase(workspaceId, pending.userId)) {
                    is RepositoryResult.Success -> {
                        _uiState.update { it.copy(pendingMemberRemoval = null) }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isRemovingMember = false) }
                removeMemberJob = null
            }
        }
    }
}
