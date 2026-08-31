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
import com.feniqo.mobile.presentation.screen.BudgetsScreen

/**
 * Android Compose Navigation için Bütçeler ekranı rotası adaptörüdür.
 * Hilt BudgetViewModel'e bağlanır, UI durumunu toplar ve stateless BudgetsScreen'e aktarır.
 */
@Composable
fun BudgetScreenRoute(
    onAddBudget: (YearMonth) -> Unit,
    onEditBudget: (EntityId, YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
    onMonthSelected: (YearMonth) -> Unit = { viewModel.processIntent(BudgetIntent.SelectMonth(it)) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is BudgetUiEvent.MutationSuccess -> {
                    snackbarHostState.showSnackbar(event.message.toDisplayText())
                }
                is BudgetUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message.toDisplayText())
                }
                is BudgetUiEvent.CopyCompleted -> {
                    val message = formatCopyResultMessage(event.copiedCount, event.skippedCount)
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        BudgetsScreen(
            state = state,
            onRetry = { viewModel.processIntent(BudgetIntent.Retry) },
            onMonthSelected = onMonthSelected,
            onAddBudget = {
                state.selectedMonth?.let { onAddBudget(it) }
            },
            onEditBudget = onEditBudget,
            onRequestDelete = { budget ->
                viewModel.processIntent(BudgetIntent.RequestDelete(budget))
            },
            onDismissDelete = {
                viewModel.processIntent(BudgetIntent.DismissDelete)
            },
            onConfirmDelete = {
                viewModel.processIntent(BudgetIntent.ConfirmDelete)
            },
            onRequestCopy = { source, target ->
                viewModel.processIntent(BudgetIntent.RequestCopy(source, target))
            },
            onChangeCopySourceMonth = { month ->
                viewModel.processIntent(BudgetIntent.ChangeCopySourceMonth(month))
            },
            onDismissCopy = {
                viewModel.processIntent(BudgetIntent.DismissCopy)
            },
            onConfirmCopy = {
                viewModel.processIntent(BudgetIntent.ConfirmCopy)
            },
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
