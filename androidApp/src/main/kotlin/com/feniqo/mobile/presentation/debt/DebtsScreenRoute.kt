package com.feniqo.mobile.presentation.debt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.DebtsScreen

/**
 * Android Jetpack Compose Navigation için Debts rotası adaptörüdür.
 * Hilt DebtsViewModel'e bağlanır, UI state'ini toplar ve stateless DebtsScreen'e aktarır.
 */
@Composable
fun DebtsScreenRoute(
    onAddDebt: () -> Unit,
    onDebtClick: (EntityId) -> Unit,
    onNavigateToSnowballPlan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        DebtsScreen(
            state = state,
            onRetry = { viewModel.onIntent(DebtsIntent.Retry) },
            onAddDebt = onAddDebt,
            onDebtClick = onDebtClick,
            onNavigateToSnowballPlan = onNavigateToSnowballPlan,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

