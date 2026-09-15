package com.feniqo.mobile.presentation.budget

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
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.component.BudgetDeleteDialog
import com.feniqo.mobile.presentation.screen.BudgetDetailScreen

/**
 * Android Compose Navigation için Bütçe Detayı ekran rotası adaptörüdür.
 * 03 ve B06 tasarımlarını bağlar.
 */
@Composable
fun BudgetDetailScreenRoute(
    budgetId: EntityId,
    month: YearMonth,
    onBack: () -> Unit,
    onEditBudget: (EntityId, YearMonth) -> Unit,
    onViewAllTransactions: (categoryId: EntityId, month: YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.budgetDetailUiState.collectAsStateWithLifecycle()
    val parentState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(budgetId, month) {
        viewModel.loadBudgetDetail(budgetId, month)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is BudgetUiEvent.MutationSuccess -> {
                    onBack()
                }
                is BudgetUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message.toDisplayText())
                }
                is BudgetUiEvent.CopyCompleted -> Unit
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BudgetDetailScreen(
            state = state,
            onBack = onBack,
            onEditBudget = onEditBudget,
            onDeleteBudget = {
                state.budget?.let {
                    viewModel.processIntent(BudgetIntent.RequestDelete(it))
                }
            },
            onViewAllTransactions = onViewAllTransactions,
            modifier = Modifier.fillMaxSize(),
        )

        // Silme Onay Diyaloğu (B09)
        parentState.deleteConfirmation?.let { confirmation ->
            BudgetDeleteDialog(
                confirmation = confirmation,
                onConfirm = { viewModel.processIntent(BudgetIntent.ConfirmDelete) },
                onDismiss = { viewModel.processIntent(BudgetIntent.DismissDelete) },
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
