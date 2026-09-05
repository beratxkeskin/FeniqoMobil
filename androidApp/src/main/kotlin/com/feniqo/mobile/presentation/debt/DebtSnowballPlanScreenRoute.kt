package com.feniqo.mobile.presentation.debt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.screen.DebtSnowballPlanScreen

/**
 * Android Navigation entegrasyonu sağlayan Snowball Plan ekran rotasıdır.
 */
@Composable
fun DebtSnowballPlanScreenRoute(
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebtSnowballPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DebtSnowballPlanUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    BackHandler(onBack = onNavigateBack)

    DebtSnowballPlanScreen(
        state = uiState,
        onBack = onNavigateBack,
        onCurrencySelect = { viewModel.selectCurrency(it) },
        onBudgetChange = { viewModel.updateBudgetInput(it) },
        onCalculate = { viewModel.calculatePlan() },
        onRetry = { viewModel.retry() },
        modifier = modifier.fillMaxSize(),
    )
}

