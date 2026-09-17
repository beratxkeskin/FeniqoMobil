package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import com.feniqo.mobile.presentation.screen.TransactionDetailScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.TransactionsScreen

/**
 * Android Jetpack Compose Navigation için Transactions rotası adaptörüdür.
 * Hilt ViewModel'e bağlanır, UI state'ini toplar ve stateless TransactionsScreen'e aktarır.
 */
@Composable
fun TransactionsScreenRoute(
    onAddTransaction: () -> Unit = {},
    onEditTransaction: (EntityId) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
    conflictViewModel: TransactionConflictViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val conflicts by conflictViewModel.conflicts.collectAsStateWithLifecycle()
    val resolving by conflictViewModel.resolving.collectAsStateWithLifecycle()
    val conflictError by conflictViewModel.error.collectAsStateWithLifecycle()
    var showConflict by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItem = state.groupedItems.flatMap { it.items }.find { it.id.value == selectedId }
    BackHandler(enabled = selectedItem != null) {
        selectedId = null
        showConflict = false
    }
    LaunchedEffect(selectedId) {
        showConflict = false
    }

    val userMessage = state.userMessage
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            val text = userMessage.toDisplayText()
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(message = text)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        TransactionsScreen(
            state = state,
            canAddTransaction = true,
            canEditTransaction = true,
            onAddTransactionClick = onAddTransaction,
            onTransactionClick = { item -> selectedId = item.id.value },
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onFilterClick = viewModel::openFilters,
            onFilterDismiss = viewModel::dismissFilters,
            onTypeFilterChanged = viewModel::onTypeFilterChanged,
            onCategoryFilterChanged = viewModel::onCategoryFilterChanged,
            onPaymentMethodFilterChanged = viewModel::onPaymentMethodFilterChanged,
            onPeriodPresetChanged = viewModel::onPeriodPresetChanged,
            onCustomPeriodChanged = viewModel::onCustomPeriodChanged,
            onSortOrderChanged = viewModel::onSortOrderChanged,
            onClearFilters = viewModel::clearFilters,
            onDeleteClicked = viewModel::onDeleteClicked,
            onDismissDeleteDialog = viewModel::dismissDeleteDialog,
            onConfirmSingleDelete = viewModel::confirmSingleDelete,
            onConfirmInstallmentDelete = viewModel::confirmInstallmentDelete,
            onRetryObservation = viewModel::retryObservation,
            modifier = Modifier.fillMaxSize(),
        )

        selectedItem?.let { item ->
            TransactionDetailScreen(
                item = item,
                onBack = { selectedId = null },
                onEdit = { onEditTransaction(item.id) },
                onDelete = { viewModel.onDeleteClicked(item) },
                onResolveConflict = if (conflicts.any { it.entityId == item.id }) ({ showConflict = true }) else null,
            )
        }

        if (showConflict) {
            conflicts.find { it.entityId.value == selectedId }?.let { conflict ->
                com.feniqo.mobile.presentation.component.TransactionConflictDialog(
                    conflict, resolving, conflictError,
                    onResolve = { conflictViewModel.resolve(conflict.entityId, it) },
                    onDismiss = { showConflict = false },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}
