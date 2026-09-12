package com.feniqo.mobile.presentation.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.WorkspaceSettlementScreen

@Composable
fun WorkspaceSettlementScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceSettlementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WorkspaceSettlementScreen(
        state = state,
        onBack = onBack,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
