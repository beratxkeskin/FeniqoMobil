package com.feniqo.mobile.presentation.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.screen.CategoriesScreen

/**
 * Android Compose Navigation için Kategoriler ekranı rotası adaptörüdür.
 * Hilt CategoriesViewModel'e bağlanır, UI state'ini toplar ve stateless CategoriesScreen'e aktarır.
 */
@Composable
fun CategoriesScreenRoute(
    onAddCategory: (TransactionType) -> Unit,
    onEditCategory: (EntityId) -> Unit,
    onCategoryClick: (EntityId, YearMonth) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CategoriesScreen(
        state = state,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onPeriodPickerClick = viewModel::onPeriodPickerRequested,
        onPeriodSelect = viewModel::onYearMonthSelected,
        onDismissPeriodPicker = viewModel::onPeriodPickerDismissed,
        onFilterSelected = viewModel::onTypeFilterSelected,
        onCategoryClick = { categoryId ->
            onCategoryClick(categoryId, state.selectedYearMonth)
        },
        onAddCategory = onAddCategory,
        onEditCategory = onEditCategory,
        onDeleteCategory = viewModel::onDeleteClicked,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
        onDismissMessage = viewModel::onDismissMessage,
        modifier = modifier,
    )
}
