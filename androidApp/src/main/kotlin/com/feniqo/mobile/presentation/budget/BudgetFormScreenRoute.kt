package com.feniqo.mobile.presentation.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.BudgetFormScreen

/**
 * Android Compose Navigation için Bütçe Formu rotası adaptörüdür.
 * Form taslağını Compose state ile yönetir, harcama kategorilerini filtreler,
 * ViewModel mutationState, editLoadState ve one-off event akışını bağlar.
 */
@Composable
fun BudgetFormScreenRoute(
    availableCategories: List<CategoryDisplayModel>,
    viewModel: BudgetViewModel,
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    editLoadState: BudgetEditLoadState = BudgetEditLoadState.Idle,
    initialBudgetId: EntityId? = null,
    initialCategoryId: EntityId? = null,
    initialCategoryName: String? = null,
    initialCategoryColorHex: String? = null,
    initialCategoryIconKey: String? = null,
    initialMonth: YearMonth? = null,
    initialLimitInput: String = "",
    initialCurrency: Currency = Currency.TRY,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var draft by remember {
        mutableStateOf(
            BudgetFormDraft(
                budgetId = initialBudgetId,
                selectedCategoryId = initialCategoryId,
                selectedCategoryName = initialCategoryName,
                selectedCategoryColorHex = initialCategoryColorHex,
                selectedCategoryIconKey = initialCategoryIconKey,
                selectedMonth = initialMonth,
                limitInput = initialLimitInput,
                currency = initialCurrency,
            ),
        )
    }

    var isEditSeedApplied by remember { mutableStateOf(false) }

    val effectiveEditLoadState = remember(initialBudgetId, editLoadState) {
        resolveEffectiveBudgetEditLoadState(initialBudgetId, editLoadState)
    }

    LaunchedEffect(effectiveEditLoadState) {
        if (effectiveEditLoadState is BudgetEditLoadState.Ready && !isEditSeedApplied) {
            val seed = effectiveEditLoadState.seed
            draft = draft.copy(
                budgetId = seed.budgetId,
                selectedCategoryId = seed.categoryId,
                selectedMonth = seed.month,
                limitInput = seed.limitInput,
                currency = seed.currency,
            )
            isEditSeedApplied = true
        }
    }

    // 1. Gönderim sırasında sistem geri hareketini engelleme
    BackHandler(enabled = uiState.mutationState.isSubmitting) {
        // Form submit edilirken yanlışlıkla geri çıkılmasını önler
    }

    // 2. ViewModel tek seferlik olaylarını toplama
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is BudgetUiEvent.MutationSuccess -> onNavigateBack()
                is BudgetUiEvent.ShowMessage -> onMessage(event.message)
                is BudgetUiEvent.CopyCompleted -> Unit
            }
        }
    }

    // 3. Duruma Göre Ekran Sunumu (Efektif Edit Durumu ile)
    when (effectiveEditLoadState) {
        BudgetEditLoadState.Loading -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LoadingContent(
                    message = "Bütçe yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        BudgetEditLoadState.NotFound -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "Bütçe Bulunamadı",
                    description = "Düzenlemek istediğiniz bütçe bulunamadı veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        is BudgetEditLoadState.Error -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "Bütçe Yüklenemedi",
                    description = effectiveEditLoadState.message.toDisplayText(),
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        BudgetEditLoadState.Idle, is BudgetEditLoadState.Ready -> {
            val formUiState = remember(draft, availableCategories, uiState.mutationState) {
                draft.toUiState(
                    categories = availableCategories,
                    mutationState = uiState.mutationState,
                )
            }

            BudgetFormScreen(
                state = formUiState,
                onBack = onNavigateBack,
                onCategorySelected = { categoryId ->
                    if (formUiState.isCategoryEditable) {
                        draft = draft.copy(selectedCategoryId = categoryId)
                    }
                },
                onMonthSelected = { month ->
                    if (formUiState.isMonthEditable) {
                        draft = draft.copy(selectedMonth = month)
                    }
                },
                onLimitChanged = { limit ->
                    if (formUiState.isFormEnabled) {
                        draft = draft.copy(limitInput = limit)
                    }
                },
                onCurrencySelected = { currency ->
                    if (formUiState.isFormEnabled) {
                        draft = draft.copy(currency = currency)
                    }
                },
                onSubmit = {
                    if (!formUiState.isSubmitting) {
                        viewModel.processIntent(draft.toSubmitIntent())
                    }
                },
                modifier = modifier,
            )
        }
    }
}
