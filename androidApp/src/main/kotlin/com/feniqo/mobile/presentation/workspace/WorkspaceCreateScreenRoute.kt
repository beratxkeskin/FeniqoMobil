package com.feniqo.mobile.presentation.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.WorkspaceCreateScreen

@Composable
fun WorkspaceCreateScreenRoute(
    onBack: () -> Unit,
    viewModel: WorkspaceCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.isSubmitting) {}
    LaunchedEffect(viewModel.events) { viewModel.events.collect { onBack() } }
    WorkspaceCreateScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onTypeChanged = viewModel::onTypeChanged,
        onCurrencyChanged = viewModel::onCurrencyChanged,
        onSubmit = viewModel::submit,
        onDismissError = viewModel::dismissError,
    )
}
