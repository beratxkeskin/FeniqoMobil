package com.feniqo.mobile.presentation.asset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.AssetFormScreen

@Composable
fun AssetFormScreenRoute(
    initialAssetId: EntityId?,
    hasInvalidRouteId: Boolean,
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadState by viewModel.editLoadState.collectAsStateWithLifecycle()

    LaunchedEffect(initialAssetId, hasInvalidRouteId) {
        if (hasInvalidRouteId) viewModel.setInvalidRouteId()
        else if (initialAssetId != null) viewModel.loadForEdit(initialAssetId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AssetFormUiEvent.MutationSuccess -> {
                    onMessage(event.message)
                    onNavigateBack()
                }
                is AssetFormUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    BackHandler(enabled = !state.isSubmitting, onBack = onNavigateBack)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            initialAssetId != null && loadState == AssetEditLoadState.Loading ->
                LoadingContent(message = "Varlık yükleniyor...", modifier = Modifier.fillMaxSize())
            hasInvalidRouteId || loadState == AssetEditLoadState.NotFound ->
                ErrorState("Varlık Bulunamadı", "Düzenlemek istediğiniz varlık mevcut değil veya silinmiş.", onNavigateBack, Modifier.fillMaxSize())
            loadState is AssetEditLoadState.Error ->
                ErrorState("Varlık Yüklenemedi", (loadState as AssetEditLoadState.Error).message.toDisplayText(),
                    { initialAssetId?.let(viewModel::loadForEdit) }, Modifier.fillMaxSize())
            else -> AssetFormScreen(
                state = state,
                onBack = onNavigateBack,
                onInputChange = viewModel::updateInput,
                onSubmit = viewModel::submit,
                onRequestDelete = viewModel::requestDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onDismissDelete = viewModel::dismissDelete,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
