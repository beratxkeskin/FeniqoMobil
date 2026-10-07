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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.resolveLocalizedText
import com.feniqo.mobile.presentation.screen.TransactionsScreen

/** İşlem listesi rotası yalnız liste, filtre ve liste kaynaklı silme davranışlarını yönetir. */
@Composable
fun TransactionsScreenRoute(
    onAddTransaction: () -> Unit = {},
    onTransactionClick: (EntityId) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(message.resolveLocalizedText())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TransactionsScreen(
            state = state,
            canAddTransaction = true,
            canEditTransaction = true,
            onAddTransactionClick = onAddTransaction,
            onTransactionClick = { item -> onTransactionClick(item.id) },
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}
