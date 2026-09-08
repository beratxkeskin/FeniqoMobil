package com.feniqo.mobile.presentation.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.WorkspaceDetailsScreen

@Composable
fun WorkspaceDetailsScreenRoute(
    onBack: () -> Unit,
    viewModel: WorkspaceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.isLeaving || state.isTransferringOwnership || state.isRemovingMember) {}

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect {
            onBack()
        }
    }

    WorkspaceDetailsScreen(
        state = state,
        onBack = onBack,
        onRequestLeave = viewModel::requestLeave,
        onDismissLeaveConfirmation = viewModel::dismissLeaveConfirmation,
        onConfirmLeave = viewModel::confirmLeave,
        onDismissError = viewModel::dismissError,
        onCreateInvite = viewModel::createInvite,
        onDismissInviteCodeDialog = viewModel::dismissInviteCodeDialog,
        onRequestChangeMemberRole = viewModel::requestChangeMemberRole,
        onSelectPendingRole = viewModel::selectPendingRole,
        onDismissRoleChangeConfirmation = viewModel::dismissRoleChangeConfirmation,
        onConfirmRoleChange = viewModel::confirmMemberRoleChange,
        onRequestOwnershipTransfer = viewModel::requestOwnershipTransfer,
        onDismissOwnershipTransferConfirmation = viewModel::dismissOwnershipTransferConfirmation,
        onConfirmOwnershipTransfer = viewModel::confirmOwnershipTransfer,
        onRequestRemoveMember = viewModel::requestMemberRemoval,
        onDismissMemberRemovalConfirmation = viewModel::dismissMemberRemovalConfirmation,
        onConfirmMemberRemoval = viewModel::confirmMemberRemoval,
    )
}
