package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.common.FinanceUiMessage

data class WorkspaceMemberUiModel(
    val userId: EntityId,
    val displayName: String,
    val role: WorkspaceRole,
    val isCurrentUser: Boolean,
)

data class WorkspaceDetailsUiModel(
    val id: EntityId,
    val name: String,
    val type: WorkspaceType,
    val currency: Currency,
    val description: String?,
    val ownerId: EntityId,
)

data class PendingRoleChange(
    val member: WorkspaceMemberUiModel,
    val newRole: WorkspaceRole,
)

data class WorkspaceDetailsUiState(
    val isLoading: Boolean = true,
    val workspace: WorkspaceDetailsUiModel? = null,
    val members: List<WorkspaceMemberUiModel> = emptyList(),
    val currentUserRole: WorkspaceRole? = null,
    val showLeaveConfirmation: Boolean = false,
    val isLeaving: Boolean = false,
    val isCreatingInvite: Boolean = false,
    val generatedInviteCode: String? = null,
    val isChangingMemberRole: Boolean = false,
    val pendingRoleChange: PendingRoleChange? = null,
    val pendingOwnershipTransferTarget: WorkspaceMemberUiModel? = null,
    val isTransferringOwnership: Boolean = false,
    val pendingMemberRemoval: WorkspaceMemberUiModel? = null,
    val isRemovingMember: Boolean = false,
    val errorMessage: FinanceUiMessage? = null,
) {
    val isOwner: Boolean get() = currentUserRole == WorkspaceRole.OWNER
    val canLeave: Boolean get() = !isOwner && currentUserRole != null && !isLeaving
    val canManageRoles: Boolean get() = isOwner && !isLoading && !isChangingMemberRole
    val canCreateInvite: Boolean get() = isOwner && !isLoading && !isCreatingInvite
    val eligibleOwnershipTransferTargets: List<WorkspaceMemberUiModel>
        get() = members.filter { !it.isCurrentUser && it.role in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER) }
    val canTransferOwnership: Boolean
        get() = isOwner && !isLoading && !isTransferringOwnership && eligibleOwnershipTransferTargets.isNotEmpty()
    val eligibleMemberRemovalTargets: List<WorkspaceMemberUiModel>
        get() = members.filter { !it.isCurrentUser && it.role in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER) }
    val canRemoveMembers: Boolean
        get() = isOwner && !isLoading && !isRemovingMember
}
