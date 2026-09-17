package com.feniqo.mobile.presentation.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.AssetsScreen

@Composable
fun AssetsScreenRoute(
    onBack: () -> Unit,
    onAddAsset: () -> Unit,
    onAssetClick: (EntityId) -> Unit,
    onDistributionClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetsScreen(
        state = state,
        onBack = onBack,
        onRetry = { viewModel.onIntent(AssetsIntent.Retry) },
        onRefreshPrices = { viewModel.onIntent(AssetsIntent.RefreshPrices) },
        onAddAsset = onAddAsset,
        onAssetClick = onAssetClick,
        onDistributionClick = onDistributionClick,
        modifier = modifier,
    )
}
