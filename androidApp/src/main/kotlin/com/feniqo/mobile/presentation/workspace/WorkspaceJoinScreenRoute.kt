package com.feniqo.mobile.presentation.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.WorkspaceJoinScreen

@Composable
fun WorkspaceJoinScreenRoute(
    onBack: () -> Unit,
    viewModel: WorkspaceJoinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.isSubmitting) {}
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { onBack() }
    }
    WorkspaceJoinScreen(
        state = state,
        onBack = onBack,
        onInviteCodeChanged = viewModel::onInviteCodeChanged,
        onSubmit = viewModel::submit,
        onDismissError = viewModel::dismissError,
    )
}
