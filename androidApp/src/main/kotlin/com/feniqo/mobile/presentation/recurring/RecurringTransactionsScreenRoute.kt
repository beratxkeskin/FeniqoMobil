package com.feniqo.mobile.presentation.recurring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.RecurringTransactionsScreen

/**
 * Android Jetpack Compose Navigation için RecurringTransactions rotası adaptörüdür.
 * Hilt RecurringTransactionsViewModel'e bağlanır, UI state'ini toplar ve stateless RecurringTransactionsScreen'e aktarır.
 */
@Composable
fun RecurringTransactionsScreenRoute(
    onAddRecurringTransaction: () -> Unit,
    onEditRecurringTransaction: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringTransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        RecurringTransactionsScreen(
            state = state,
            onRetry = { viewModel.onIntent(RecurringTransactionsIntent.Retry) },
            onAddRecurringTransaction = onAddRecurringTransaction,
            onRecurringTransactionClick = onEditRecurringTransaction,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
