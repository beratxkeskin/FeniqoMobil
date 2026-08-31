package com.feniqo.mobile.presentation.dashboard

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.DashboardScreen

/**
 * Android Jetpack Compose Navigation için Dashboard rotası adaptörüdür.
 * Hilt ViewModel'e bağlanır, UI state'ini toplar ve stateless DashboardScreen'e aktarır.
 */
@Composable
fun DashboardScreenRoute(
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val userMessage = state.userMessage
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            val text = userMessage.toDisplayText()
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(message = text)
        }
    }

    DashboardScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::retry,
        onAddTransaction = onAddTransaction,
        onViewAllTransactions = onViewAllTransactions,
        onTransactionClick = onTransactionClick,
        modifier = modifier,
    )
}
