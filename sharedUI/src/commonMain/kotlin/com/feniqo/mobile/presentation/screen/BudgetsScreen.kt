package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetOverview
import com.feniqo.mobile.presentation.budget.BudgetProgressDisplayModel
import com.feniqo.mobile.presentation.budget.BudgetsUiState
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.BudgetCopyDialog
import com.feniqo.mobile.presentation.component.BudgetDeleteDialog
import com.feniqo.mobile.presentation.component.BudgetEmptyState
import com.feniqo.mobile.presentation.component.BudgetExceededBanner
import com.feniqo.mobile.presentation.component.BudgetHeader
import com.feniqo.mobile.presentation.component.BudgetMonthlyOverviewCard
import com.feniqo.mobile.presentation.component.BudgetPeriodPickerSheet
import com.feniqo.mobile.presentation.component.BudgetProgressCard
import com.feniqo.mobile.presentation.component.BudgetSectionHeader
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoBackgroundSand = Color(0xFFF7F5F0)

/**
 * 01 Bütçeler listesi ve dönem özeti ekranının durumsuz (stateless) Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; Room tek okuma kaynağıdır.
 */
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onRetry: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit = {},
    onAddBudget: () -> Unit = {},
    onEditBudget: (id: EntityId, month: YearMonth) -> Unit = { _, _ -> },
    onBudgetClick: (id: EntityId, month: YearMonth) -> Unit = onEditBudget,
    onRequestDelete: (BudgetProgressDisplayModel) -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onRequestCopy: (sourceMonth: YearMonth, targetMonth: YearMonth) -> Unit = { _, _ -> },
    onChangeCopySourceMonth: (YearMonth) -> Unit = {},
    onDismissCopy: () -> Unit = {},
    onConfirmCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPeriodPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoBackgroundSand,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 1. Üst Başlık ve Dönem / Kopyala Gezinme Göstergesi
            BudgetHeader(
                selectedMonth = state.selectedMonth,
                onMonthSelected = onMonthSelected,
                onOpenMonthPicker = { showPeriodPicker = true },
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
                        BudgetEmptyState(
                            onNewBudget = onAddBudget,
                            onCopyFromPreviousMonth = {
                                state.selectedMonth?.let { target ->
                                    onRequestCopy(target.previousMonth(), target)
                                }
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            // 3 Sütunlu Aylık Bütçe Özeti (Bütçe, Harcanan, Kalan)
                            when (val overview = state.overview) {
                                is BudgetOverview.Ready -> {
                                    items(overview.summaries, key = { "summary-${it.currency.name}" }) { summary ->
                                        BudgetMonthlyOverviewCard(summary)
                                    }
                                }
                                BudgetOverview.UnsafeTotal -> item {
                                    ErrorState(
                                        title = "Bütçe özeti gösterilemiyor",
                                        description = "Toplam güvenli biçimde hesaplanamadı. Kategori bütçeleri aşağıda korunuyor.",
                                        onRetry = onRetry,
                                    )
                                }
                                BudgetOverview.None -> Unit
                            }

                            // Aşım Varsa Aşım Uyarı Banner'ı (! 1 bütçe aşıldı >)
                            if (state.exceededBudgetsCount > 0) {
                                item {
                                    BudgetExceededBanner(
                                        exceededCount = state.exceededBudgetsCount,
                                        onClick = {
                                            state.firstExceededBudget?.let {
                                                onBudgetClick(it.id, it.month)
                                            }
                                        },
                                    )
                                }
                            }

                            // Yeşil Dikey Çizgili "▎Kategori bütçeleri" Başlığı
                            item {
                                BudgetSectionHeader("Kategori bütçeleri")
                            }

                            // Kategori Bütçe Kartları
                            items(
                                items = state.budgets,
                                key = { it.id.value },
                            ) { budget ->
                                BudgetProgressCard(
                                    budget = budget,
                                    onClick = { onBudgetClick(budget.id, budget.month) },
                                    onEdit = { onEditBudget(budget.id, budget.month) },
                                    onDelete = { onRequestDelete(budget) },
                                )
                            }
                        }
                    }
                }

                // 3. Altta Sabit "Yeni bütçe" Butonu (Yalnız liste boş değilken altta sabitlenir)
                if (!state.isLoading && state.observationError == null && !state.isEmpty) {
                    Button(
                        onClick = onAddBudget,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = FeniqoSpacing.Medium)
                            .defaultMinSize(minHeight = 50.dp),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    ) {
                        Text(
                            text = "Yeni bütçe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 4. Dönem ve Para Birimi Seçici Modal (B08)
        if (showPeriodPicker && state.selectedMonth != null) {
            BudgetPeriodPickerSheet(
                initialMonth = state.selectedMonth,
                initialCurrency = Currency.TRY,
                onApply = { newMonth, _ ->
                    onMonthSelected(newMonth)
                },
                onDismiss = { showPeriodPicker = false },
            )
        }

        // 5. Silme Onay Diyaloğu (B09)
        state.deleteConfirmation?.let { confirmation ->
            BudgetDeleteDialog(
                confirmation = confirmation,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDelete,
            )
        }

        // 6. Kopyalama Onay Diyaloğu (B05)
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
        categoryColorHex = "#EF4444",
        categoryIconKey = "shopping-cart",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "6.000,00 ₺",
        limitMinor = 600_000L,
        formattedSpent = "4.200,00 ₺",
        spentMinor = 420_000L,
        formattedRemaining = "1.800,00 ₺",
        remainingMinor = 180_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 7_000,
        formattedUsageRate = "%70,00",
        usageProgressFraction = 0.7f,
        health = BudgetHealth.SAFE,
        excludedDifferentCurrencyTransactionCount = 0,
    ),
    BudgetProgressDisplayModel(
        id = EntityId("b-2"),
        categoryId = EntityId("c-restoran"),
        categoryName = "Yeme & İçme",
        categoryColorHex = "#F59E0B",
        categoryIconKey = "utensils",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "3.000,00 ₺",
        limitMinor = 300_000L,
        formattedSpent = "2.550,00 ₺",
        spentMinor = 255_000L,
        formattedRemaining = "450,00 ₺",
        remainingMinor = 45_000L,
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
        categoryColorHex = "#3B82F6",
        categoryIconKey = "car",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "2.000,00 ₺",
        limitMinor = 200_000L,
        formattedSpent = "2.200,00 ₺",
        spentMinor = 220_000L,
        formattedRemaining = "-200,00 ₺",
        remainingMinor = -20_000L,
        isRemainingNegative = true,
        usageRateBasisPoints = 11_000,
        formattedUsageRate = "%110,00",
        usageProgressFraction = 1.0f,
        health = BudgetHealth.EXCEEDED,
        excludedDifferentCurrencyTransactionCount = 0,
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
