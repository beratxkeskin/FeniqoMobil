package com.feniqo.mobile.presentation.goal

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.common.FinanceUiMessage
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
    LaunchedEffect(Unit) { viewModel.events.collect { when(it) { GoalDetailEvent.Deleted -> onDeleted(); is GoalDetailEvent.Message -> onMessage(it.message) } } }
    GoalDetailScreen(state,onBack,onEdit,onAdd,onRemove,viewModel::requestDelete,modifier)
    if(state.showDeleteConfirmation) AlertDialog(
        onDismissRequest=viewModel::dismissDelete,
        title={Text("Hedef silinsin mi?")},
        text={Text("Hedef ve hareketleri silinmiş olarak işaretlenecek.")},
        confirmButton={TextButton(viewModel::confirmDelete,enabled=!state.isDeleting){Text(if(state.isDeleting) "Siliniyor…" else "Sil")}},
        dismissButton={TextButton(viewModel::dismissDelete,enabled=!state.isDeleting){Text("Vazgeç")}},
    )
}
