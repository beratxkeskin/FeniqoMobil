package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoriesSummaryUiModel
import com.feniqo.mobile.presentation.category.CategoriesUiState
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.category.CategorySpendingDisplayModel
import com.feniqo.mobile.presentation.category.CategoryTrend
import com.feniqo.mobile.presentation.category.TrendMovement
import com.feniqo.mobile.presentation.category.TrendSentiment
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CategoryAnalyticsListItem
import com.feniqo.mobile.presentation.component.CategoryDeleteDialog
import com.feniqo.mobile.presentation.component.CategoryEmptyFilterCard
import com.feniqo.mobile.presentation.component.CategoryFilterChips
import com.feniqo.mobile.presentation.component.CategoryInsightCard
import com.feniqo.mobile.presentation.component.CategoryMessageBanner
import com.feniqo.mobile.presentation.component.CategoryPeriodSelector
import com.feniqo.mobile.presentation.component.CategorySectionHeader
import com.feniqo.mobile.presentation.component.CategorySummaryCard
import com.feniqo.mobile.presentation.component.CreateCategoryActionCard
import com.feniqo.mobile.presentation.component.MonthYearPickerDialog
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens

/**
 * Kategoriler ekranının warm-luxury, durumsuz (stateless) ana Compose sunumudur.
 * 1'den 8'e kadar olan kesin görsel hiyerarşi sırasını takip eder:
 * 1. Başlık ve Açıklama + ActiveWorkspaceIndicator
 * 2. Dönem Seçici (Önceki / Seçili Ay / Sonraki)
 * 3. Özet Kartı (Kategori sayıları, en yüksek harcama, mini bar oranları)
 * 4. "Kategorilerin" Bölüm Başlığı
 * 5. Filtre Chip'leri (Tümü / Gider / Gelir)
 * 6. Kategori Analiz Listesi (ikon, ad, işlem sayısı, tutar, trend rozeti, overflow menü)
 * 7. Feniqo İçgörü Kartı
 * 8. "Yeni kategori oluştur" Aksiyon Kartı
 */
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPeriodPickerClick: () -> Unit,
    onPeriodSelect: (YearMonth) -> Unit,
    onDismissPeriodPicker: () -> Unit,
    onFilterSelected: (TransactionType?) -> Unit,
    onCategoryClick: (EntityId) -> Unit,
    onAddCategory: (TransactionType) -> Unit,
    onEditCategory: (EntityId) -> Unit,
    onDeleteCategory: (CategoryDisplayModel) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Large,
                vertical = FeniqoSpacing.Medium,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
        ) {
            // Genel mesaj banner'ı (varsa)
            if (state.generalMessage != null) {
                item(key = "message_banner") {
                    CategoryMessageBanner(
                        message = state.generalMessage,
                        onDismiss = onDismissMessage,
                        isDismissEnabled = !state.isDeleteInProgress,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // 1. “Kategoriler” başlığı, açıklaması ve ActiveWorkspaceIndicator
            item(key = "header_section") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Kategoriler",
                            style = FeniqoTypographyTokens.DisplayTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        ActiveWorkspaceIndicator(
                            workspaceName = state.activeWorkspaceName,
                            isCompact = true,
                        )
                    }
                    Text(
                        text = "Gelir ve giderlerinizi netlikle organize edin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 2. Kompakt harcama/gelir özet kartı
            item(key = "summary_card") {
                CategorySummaryCard(summary = state.summary)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. “Kategorilerin” bölüm başlığı ve kategori sayısı
            item(key = "categories_section_header") {
                CategorySectionHeader(
                    title = "Kategorilerin",
                    count = state.items.size,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 4. Modern dönem seçici
            item(key = "period_selector") {
                CategoryPeriodSelector(
                    selectedYearMonth = state.selectedYearMonth,
                    isNextMonthEnabled = true, // ViewModel kontrolleriyle senkron
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onPeriodPickerClick = onPeriodPickerClick,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 5. “Tümü / Gider / Gelir” filtreleri
            item(key = "filter_chips") {
                CategoryFilterChips(
                    selectedTypeFilter = state.selectedTypeFilter,
                    onFilterSelected = onFilterSelected,
                    enabled = !state.isDeleteInProgress,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 6. Kompakt kategori listesi
            if (state.isLoading) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FeniqoSpacing.ExtraLarge),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (state.items.isEmpty()) {
                item(key = "empty_state") {
                    CategoryEmptyFilterCard(
                        onCreateClick = {
                            onAddCategory(state.selectedTypeFilter ?: TransactionType.EXPENSE)
                        },
                    )
                }
            } else {
                items(
                    items = state.items,
                    key = { it.category.id.value },
                ) { item ->
                    CategoryAnalyticsListItem(
                        item = item,
                        onClick = { onCategoryClick(item.category.id) },
                        onEditClick = onEditCategory,
                        onDeleteClick = onDeleteCategory,
                        isActionsEnabled = !state.isDeleteInProgress,
                    )
                }
            }

            // 7. Feniqo İçgörü kartı
            if (state.summary.insightText != null) {
                item(key = "insight_card") {
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryInsightCard(insightText = state.summary.insightText)
                }
            }

            // 8. Yeni kategori oluştur kartı
            item(key = "create_category_card") {
                Spacer(modifier = Modifier.height(8.dp))
                CreateCategoryActionCard(
                    onClick = {
                        onAddCategory(state.selectedTypeFilter ?: TransactionType.EXPENSE)
                    },
                )
            }

            // Alt boşluk
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Screen))
            }
        }
    }

    // Ay / Yıl Seçici Dialog
    if (state.isPeriodPickerVisible) {
        MonthYearPickerDialog(
            selectedYearMonth = state.selectedYearMonth,
            maxYearMonth = YearMonth("2099-12"),
            onYearMonthSelected = onPeriodSelect,
            onDismiss = onDismissPeriodPicker,
        )
    }

    // Kategori Silme Onay Diyaloğu
    CategoryDeleteDialog(
        targetCategory = state.deleteTargetCategory,
        isDeleteInProgress = state.isDeleteInProgress,
        onConfirm = onConfirmDelete,
        onDismiss = onDismissDeleteDialog,
    )
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Kategoriler Ekranı - Veri Bulunan Durum", showBackground = true)
@Composable
private fun CategoriesScreenLoadedPreview() {
    val sampleSummary = CategoriesSummaryUiModel(
        totalCategoriesCount = 10,
        customCategoriesCount = 3,
        topCategoryName = "Yeme & İçme",
        formattedTopCategoryAmount = "₺5.110",
        topCategoryType = TransactionType.EXPENSE,
        topCategoryShareBasisPoints = 3800,
        miniBarProportionsBasisPoints = listOf(10_000, 6_400, 4_300, 3_900, 3_200),
        insightText = "Yeme & İçme bu ayki en yüksek harcaman. Toplam giderlerinin %38'ini oluşturuyor.",
        balanceMessage = "Harcamaların dengede.",
    )

    val sampleItems = listOf(
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-1"),
                name = "Yeme & İçme",
                type = TransactionType.EXPENSE,
                colorHex = "#10B981",
                iconKey = "utensils",
                isDefault = true,
            ),
            transactionCount = 48,
            currentPeriodAmount = Money(511000L, Currency.TRY),
            previousPeriodAmount = Money(456000L, Currency.TRY),
            formattedCurrentAmount = "₺5.110",
            trend = CategoryTrend.Changed(1200, TrendMovement.INCREASED, TrendSentiment.NEGATIVE),
        ),
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-2"),
                name = "Alışveriş",
                type = TransactionType.EXPENSE,
                colorHex = "#F59E0B",
                iconKey = "shopping-bag",
                isDefault = true,
            ),
            transactionCount = 28,
            currentPeriodAmount = Money(328000L, Currency.TRY),
            previousPeriodAmount = Money(312000L, Currency.TRY),
            formattedCurrentAmount = "₺3.280",
            trend = CategoryTrend.Changed(500, TrendMovement.INCREASED, TrendSentiment.NEGATIVE),
        ),
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-3"),
                name = "Ulaşım",
                type = TransactionType.EXPENSE,
                colorHex = "#3B82F6",
                iconKey = "directions-car",
                isDefault = true,
            ),
            transactionCount = 18,
            currentPeriodAmount = Money(219000L, Currency.TRY),
            previousPeriodAmount = Money(238000L, Currency.TRY),
            formattedCurrentAmount = "₺2.190",
            trend = CategoryTrend.Changed(800, TrendMovement.DECREASED, TrendSentiment.POSITIVE),
        ),
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-4"),
                name = "Maaş",
                type = TransactionType.INCOME,
                colorHex = "#10B981",
                iconKey = "briefcase",
                isDefault = true,
            ),
            transactionCount = 1,
            currentPeriodAmount = Money(4500000L, Currency.TRY),
            previousPeriodAmount = Money(3500000L, Currency.TRY),
            formattedCurrentAmount = "₺45.000",
            trend = CategoryTrend.Changed(2800, TrendMovement.INCREASED, TrendSentiment.POSITIVE),
        ),
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-5"),
                name = "Özel Hobiler",
                type = TransactionType.EXPENSE,
                colorHex = "#8B5CF6",
                iconKey = "heart-pulse",
                isDefault = false,
            ),
            transactionCount = 4,
            currentPeriodAmount = Money(98000L, Currency.TRY),
            previousPeriodAmount = Money(0L, Currency.TRY),
            formattedCurrentAmount = "₺980",
            trend = CategoryTrend.New,
        ),
    )

    FeniqoTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedYearMonth = YearMonth("2026-09"),
                selectedTypeFilter = null,
                summary = sampleSummary,
                items = sampleItems,
                activeWorkspaceName = "Kişisel",
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onPeriodPickerClick = {},
            onPeriodSelect = {},
            onDismissPeriodPicker = {},
            onFilterSelected = {},
            onCategoryClick = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Kategoriler Ekranı - Yükleniyor", showBackground = true)
@Composable
private fun CategoriesScreenLoadingPreview() {
    FeniqoTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = true,
                selectedYearMonth = YearMonth("2026-09"),
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onPeriodPickerClick = {},
            onPeriodSelect = {},
            onDismissPeriodPicker = {},
            onFilterSelected = {},
            onCategoryClick = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Kategoriler Ekranı - Sıfır İşlem", showBackground = true)
@Composable
private fun CategoriesScreenZeroTransactionsPreview() {
    val sampleSummary = CategoriesSummaryUiModel(
        totalCategoriesCount = 3,
        customCategoriesCount = 1,
        topCategoryName = null,
        formattedTopCategoryAmount = null,
        insightText = "Bu dönemde henüz işlem kaydı yok. Kategorilerinizi düzenleyerek harcamalarınızı kolayca takip edin.",
        balanceMessage = "Harcamaların dengede.",
    )

    val sampleItems = listOf(
        CategorySpendingDisplayModel(
            category = CategoryDisplayModel(
                id = EntityId("cat-1"),
                name = "Yeme & İçme",
                type = TransactionType.EXPENSE,
                colorHex = "#10B981",
                iconKey = "utensils",
                isDefault = true,
            ),
            transactionCount = 0,
            currentPeriodAmount = Money(0L, Currency.TRY),
            previousPeriodAmount = Money(0L, Currency.TRY),
            formattedCurrentAmount = "₺0",
            trend = CategoryTrend.None,
        ),
    )

    FeniqoTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedYearMonth = YearMonth("2026-09"),
                summary = sampleSummary,
                items = sampleItems,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onPeriodPickerClick = {},
            onPeriodSelect = {},
            onDismissPeriodPicker = {},
            onFilterSelected = {},
            onCategoryClick = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}
