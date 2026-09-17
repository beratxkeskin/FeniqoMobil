package com.feniqo.mobile.presentation.goal

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.GoalDeleteDialog
import com.feniqo.mobile.presentation.screen.GoalDetailScreen

@Composable
fun GoalDetailScreenRoute(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDeleted: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                GoalDetailEvent.Deleted -> onDeleted()
                is GoalDetailEvent.Message -> onMessage(event.message)
            }
        }
    }

    GoalDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onAdd = onAdd,
        onRemove = onRemove,
        onDelete = viewModel::requestDelete,
        modifier = modifier,
    )

    if (state.showDeleteConfirmation) {
        GoalDeleteDialog(
            goalName = state.detail?.goal?.name,
            isSubmitting = state.isDeleting,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}
