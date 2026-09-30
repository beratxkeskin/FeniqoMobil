package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.InstallmentTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SingleTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.TransactionConflictDialog
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen

/** Bağımsız işlem detay destination'ının Compose adaptörüdür. */
@Composable
fun TransactionDetailScreenRoute(
    onBack: () -> Unit,
    onEdit: (EntityId) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
    conflictViewModel: TransactionConflictViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val conflicts by conflictViewModel.conflicts.collectAsStateWithLifecycle()
    val resolving by conflictViewModel.resolving.collectAsStateWithLifecycle()
    val conflictError by conflictViewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConflict by rememberSaveable { mutableStateOf(false) }
    val activeConflict = state.item?.id?.let { itemId ->
        conflicts.find { it.entityId == itemId }
    }

    LaunchedEffect(activeConflict, showConflict) {
        if (showConflict && activeConflict == null) showConflict = false
    }
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionDetailEvent.Deleted -> onDeleted()
            }
        }
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(message.toDisplayText())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingContent(message = "İşlem ayrıntıları yükleniyor…")
            state.item != null -> {
                val item = checkNotNull(state.item)
                TransactionDetailScreen(
                    item = item,
                    onBack = onBack,
                    onEdit = { onEdit(item.id) },
                    onDelete = viewModel::requestDelete,
                    onResolveConflict = if (activeConflict != null) {
                        { showConflict = true }
                    } else {
                        null
                    },
                )
            }
            else -> ErrorState(
                title = "İşlem açılamadı",
                description = state.errorMessage ?: "İşlem ayrıntıları kullanılamıyor.",
                onRetry = onBack,
                actionLabel = "Geri dön",
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (val dialog = state.deleteDialog) {
            is TransactionDeleteDialogState.Single -> SingleTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = viewModel::confirmSingleDelete,
                onDismiss = viewModel::dismissDeleteDialog,
            )
            is TransactionDeleteDialogState.Installment -> InstallmentTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = viewModel::confirmInstallmentDelete,
                onDismiss = viewModel::dismissDeleteDialog,
            )
            null -> Unit
        }

        if (showConflict && activeConflict != null) {
            TransactionConflictDialog(
                conflict = activeConflict,
                resolving = resolving,
                error = conflictError,
                onResolve = { conflictViewModel.resolve(activeConflict.entityId, it) },
                onDismiss = { showConflict = false },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}
