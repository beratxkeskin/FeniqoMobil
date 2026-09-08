package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetProgressDisplayModel
import com.feniqo.mobile.presentation.budget.BudgetsUiState
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.BudgetCopyDialog
import com.feniqo.mobile.presentation.component.BudgetDeleteDialog
import com.feniqo.mobile.presentation.component.BudgetHeader
import com.feniqo.mobile.presentation.component.BudgetProgressCard
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Bütçeler listesi ekranının durumsuz (stateless) ana Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; ViewModel, DAO veya Supabase bağımlılığı içermez.
 */
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onRetry: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit = {},
    onAddBudget: () -> Unit = {},
    onEditBudget: (id: EntityId, month: YearMonth) -> Unit = { _, _ -> },
    onRequestDelete: (BudgetProgressDisplayModel) -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onRequestCopy: (sourceMonth: YearMonth, targetMonth: YearMonth) -> Unit = { _, _ -> },
    onChangeCopySourceMonth: (YearMonth) -> Unit = {},
    onDismissCopy: () -> Unit = {},
    onConfirmCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 1. Üst Başlık ve Seçili Ay Gezinme Göstergesi
            BudgetHeader(
                selectedMonth = state.selectedMonth,
                onMonthSelected = onMonthSelected,
                onAddBudget = onAddBudget,
                onCopyBudgets = {
                    state.selectedMonth?.let { target ->
                        onRequestCopy(target.previousMonth(), target)
                    }
                },
                canCopy = !state.isLoading && state.observationError == null,
                activeWorkspaceName = state.activeWorkspaceName,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Ana Liste veya Durum Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> {
                        LoadingContent(
                            message = "Bütçeler yükleniyor...",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.observationError != null -> {
                        ErrorState(
                            title = "Bütçeler Yüklenemedi",
                            description = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.isEmpty -> {
                        EmptyState(
                            title = "Henüz bütçe bulunmuyor.",
                            description = "Bu ay için henüz bütçe tanımlanmadı.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                            contentPadding = PaddingValues(bottom = FeniqoSpacing.Screen),
                        ) {
                            items(
                                items = state.budgets,
                                key = { it.id.value },
                            ) { budget ->
                                BudgetProgressCard(
                                    budget = budget,
                                    onEdit = { onEditBudget(budget.id, budget.month) },
                                    onDelete = { onRequestDelete(budget) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Silme Onay Diyaloğu
        state.deleteConfirmation?.let { confirmation ->
            BudgetDeleteDialog(
                confirmation = confirmation,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDelete,
            )
        }

        // 4. Kopyalama Onay Diyaloğu
        state.copyConfirmation?.let { confirmation ->
            BudgetCopyDialog(
                confirmation = confirmation,
                onSourceMonthChanged = onChangeCopySourceMonth,
                onConfirm = onConfirmCopy,
                onDismiss = onDismissCopy,
            )
        }
    }
}

// -------------------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------------------

private val previewBudgets = listOf(
    BudgetProgressDisplayModel(
        id = EntityId("b-1"),
        categoryId = EntityId("c-market"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-cart",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "2.000,00 ₺",
        limitMinor = 200_000L,
        formattedSpent = "1.000,00 ₺",
        spentMinor = 100_000L,
        formattedRemaining = "1.000,00 ₺",
        remainingMinor = 100_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 5_000,
        formattedUsageRate = "%50,00",
        usageProgressFraction = 0.5f,
        health = BudgetHealth.SAFE,
        excludedDifferentCurrencyTransactionCount = 0,
    ),
    BudgetProgressDisplayModel(
        id = EntityId("b-2"),
        categoryId = EntityId("c-restoran"),
        categoryName = "Restoran & Kafe",
        categoryColorHex = "#F59E0B",
        categoryIconKey = "utensils",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "1.000,00 ₺",
        limitMinor = 100_000L,
        formattedSpent = "850,00 ₺",
        spentMinor = 85_000L,
        formattedRemaining = "150,00 ₺",
        remainingMinor = 15_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 8_500,
        formattedUsageRate = "%85,00",
        usageProgressFraction = 0.85f,
        health = BudgetHealth.WARNING,
        excludedDifferentCurrencyTransactionCount = 0,
    ),
    BudgetProgressDisplayModel(
        id = EntityId("b-3"),
        categoryId = EntityId("c-ulasim"),
        categoryName = "Ulaşım",
        categoryColorHex = "#EF4444",
        categoryIconKey = "car",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "500,00 ₺",
        limitMinor = 50_000L,
        formattedSpent = "650,00 ₺",
        spentMinor = 65_000L,
        formattedRemaining = "150,00 ₺",
        remainingMinor = -15_000L,
        isRemainingNegative = true,
        usageRateBasisPoints = 13_000,
        formattedUsageRate = "%130,00",
        usageProgressFraction = 1.0f,
        health = BudgetHealth.EXCEEDED,
        excludedDifferentCurrencyTransactionCount = 1,
    ),
)

@Preview(name = "Budgets Screen - Full Light", showBackground = true)
@Composable
private fun BudgetsScreenFullPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        BudgetsScreen(
            state = BudgetsUiState(
                isLoading = false,
                selectedMonth = YearMonth("2026-08"),
                budgets = previewBudgets,
            ),
            onRetry = {},
            onMonthSelected = {},
        )
    }
}

@Preview(name = "Budgets Screen - Full Dark", showBackground = true)
@Composable
private fun BudgetsScreenFullPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        BudgetsScreen(
            state = BudgetsUiState(
                isLoading = false,
                selectedMonth = YearMonth("2026-08"),
                budgets = previewBudgets,
            ),
            onRetry = {},
            onMonthSelected = {},
        )
    }
}

@Preview(name = "Budgets Screen - Empty Light", showBackground = true)
@Composable
private fun BudgetsScreenEmptyPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        BudgetsScreen(
            state = BudgetsUiState(
                isLoading = false,
                selectedMonth = YearMonth("2026-08"),
                budgets = emptyList(),
            ),
            onRetry = {},
            onMonthSelected = {},
        )
    }
}

@Preview(name = "Budgets Screen - Loading Light", showBackground = true)
@Composable
private fun BudgetsScreenLoadingPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        BudgetsScreen(
            state = BudgetsUiState(
                isLoading = true,
                selectedMonth = YearMonth("2026-08"),
            ),
            onRetry = {},
            onMonthSelected = {},
        )
    }
}

@Preview(name = "Budgets Screen - Error Light", showBackground = true)
@Composable
private fun BudgetsScreenErrorPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        BudgetsScreen(
            state = BudgetsUiState(
                isLoading = false,
                selectedMonth = YearMonth("2026-08"),
                observationError = FinanceUiMessage.GENERIC_ERROR,
            ),
            onRetry = {},
            onMonthSelected = {},
        )
    }
}
