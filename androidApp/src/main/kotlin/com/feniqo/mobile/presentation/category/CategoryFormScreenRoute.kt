package com.feniqo.mobile.presentation.category

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.CategoryFormScreen

/**
 * Android Compose Navigation için Kategori Formu rotası adaptörüdür.
 * Hilt CategoryFormViewModel'e bağlanır, UI state'ini toplar ve stateless CategoryFormScreen'e aktarır.
 */
@Composable
fun CategoryFormScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 1. Gönderim sırasında sistem geri hareketini engelleme
    BackHandler(enabled = state.isSubmitting) {
        // Form submit edilirken kazara sistem geri hareketini engeller
    }

    // 2. ViewModel tek seferlik olaylarını toplama
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                CategoryFormEvent.NavigateBack -> onBack()
            }
        }
    }

    // 3. Stateless Form Ekranı
    CategoryFormScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onTypeChanged = viewModel::onTypeChanged,
        onColorChanged = viewModel::onColorChanged,
        onIconChanged = viewModel::onIconChanged,
        onSubmit = viewModel::onSubmit,
        onDismissMessage = viewModel::onDismissMessage,
        modifier = modifier,
    )
}
