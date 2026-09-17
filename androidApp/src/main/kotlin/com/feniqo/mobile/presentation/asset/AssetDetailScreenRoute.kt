package com.feniqo.mobile.presentation.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.screen.AssetDetailScreen

@Composable
fun AssetDetailScreenRoute(
    assetId: EntityId,
    onBack: () -> Unit,
    onEdit: (EntityId) -> Unit,
    onAssetDeleted: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.loadAsset(assetId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

    AssetDetailScreen(
        state = state.copy(pendingDeleteConfirmation = pendingDelete),
        onBack = onBack,
        onEdit = { onEdit(assetId) },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = {
            viewModel.confirmDelete(
                onSuccess = {
                    onMessage(FinanceUiMessage.ASSET_DELETED)
                    onAssetDeleted()
                },
            )
        },
        onDismissDelete = viewModel::dismissDelete,
        onRetryPrice = viewModel::retryPriceRefresh,
        modifier = modifier,
    )
}
