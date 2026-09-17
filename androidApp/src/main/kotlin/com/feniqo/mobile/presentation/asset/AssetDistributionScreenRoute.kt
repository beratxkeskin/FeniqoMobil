package com.feniqo.mobile.presentation.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.AssetDistributionScreen

@Composable
fun AssetDistributionScreenRoute(
    initialCurrencyCode: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetDistributionViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialCurrencyCode) {
        viewModel.setInitialCurrency(initialCurrencyCode)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AssetDistributionScreen(
        state = state,
        onBack = onBack,
        onSelectCurrency = viewModel::selectCurrency,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
