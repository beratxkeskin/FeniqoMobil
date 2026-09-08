package com.feniqo.mobile.presentation.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.WorkspacePickerScreen

/**
 * Android Compose Navigation için Çalışma Alanı Seçici rota adaptörüdür.
 * WorkspacePickerViewModel'e bağlanır ve stateless WorkspacePickerScreen'e aktarır.
 */
@Composable
fun WorkspacePickerScreenRoute(
    onNavigateBack: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onJoinWorkspace: () -> Unit,
    onWorkspaceDetails: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspacePickerViewModel = hiltViewModel(),
) {

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WorkspacePickerScreen(
        state = state,
        onSelectPersonalMode = viewModel::selectPersonalMode,
        onSelectWorkspace = viewModel::selectWorkspace,
        onDismissError = viewModel::dismissError,
        onNavigateBack = onNavigateBack,
        onCreateWorkspace = onCreateWorkspace,
        onJoinWorkspace = onJoinWorkspace,
        onWorkspaceDetails = onWorkspaceDetails,
        modifier = modifier,
    )
}
