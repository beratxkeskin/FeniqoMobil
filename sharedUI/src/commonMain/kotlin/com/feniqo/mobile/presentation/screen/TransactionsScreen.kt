package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ActiveTransactionFilterChips
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.InstallmentTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.SingleTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.TransactionDateGroupHeader
import com.feniqo.mobile.presentation.component.TransactionFilterSheet
import com.feniqo.mobile.presentation.component.TransactionListItem
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens
import com.feniqo.mobile.presentation.transaction.CategoryFilterOptionUiModel
import com.feniqo.mobile.presentation.transaction.DailyTransactionBarUiModel
import com.feniqo.mobile.presentation.transaction.DateGroupedTransactionsDisplayModel
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionDeleteDialogState
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionFilterUiModel
import com.feniqo.mobile.presentation.transaction.TransactionPeriodPreset
import com.feniqo.mobile.presentation.transaction.TransactionSortOrder
import com.feniqo.mobile.presentation.transaction.TransactionSummaryUiModel
import com.feniqo.mobile.presentation.transaction.TransactionsUiState
import com.feniqo.mobile.presentation.transaction.localizedTransactionPeriodText
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.transactions_add_new
import feniqomobil.sharedui.generated.resources.transactions_all
import feniqomobil.sharedui.generated.resources.transactions_all_categories
import feniqomobil.sharedui.generated.resources.transactions_category_select
import feniqomobil.sharedui.generated.resources.transactions_chart_empty
import feniqomobil.sharedui.generated.resources.transactions_chart_empty_semantics
import feniqomobil.sharedui.generated.resources.transactions_chart_semantics
import feniqomobil.sharedui.generated.resources.transactions_clear
import feniqomobil.sharedui.generated.resources.transactions_clear_filters
import feniqomobil.sharedui.generated.resources.transactions_count
import feniqomobil.sharedui.generated.resources.transactions_currency_excluded
import feniqomobil.sharedui.generated.resources.transactions_currency_excluded_semantics
import feniqomobil.sharedui.generated.resources.transactions_empty_body
import feniqomobil.sharedui.generated.resources.transactions_empty_title
import feniqomobil.sharedui.generated.resources.transactions_expense
import feniqomobil.sharedui.generated.resources.transactions_filters_count
import feniqomobil.sharedui.generated.resources.transactions_income
import feniqomobil.sharedui.generated.resources.transactions_loading
import feniqomobil.sharedui.generated.resources.transactions_load_error
import feniqomobil.sharedui.generated.resources.transactions_no_results
import feniqomobil.sharedui.generated.resources.transactions_no_results_body
import feniqomobil.sharedui.generated.resources.transactions_period_details
import feniqomobil.sharedui.generated.resources.transactions_period_net
import feniqomobil.sharedui.generated.resources.transactions_period_net_explanation
import feniqomobil.sharedui.generated.resources.transactions_period_select
import feniqomobil.sharedui.generated.resources.transactions_retry
import feniqomobil.sharedui.generated.resources.transactions_search
import feniqomobil.sharedui.generated.resources.transactions_sort_select
import feniqomobil.sharedui.generated.resources.transactions_title
import org.jetbrains.compose.resources.stringResource

/**
 * İşlemler ekranının Modern Zen / Warm Luxury tasarımına sahip ana Compose sunumudur.
 * Yalnızca UI state'i ve etkileşim callback'lerini tüketir; ViewModel veya platform bağımlılığı içermez.
 */
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    modifier: Modifier = Modifier,
    canAddTransaction: Boolean = false,
    canEditTransaction: Boolean = false,
    onSearchQueryChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    onFilterDismiss: () -> Unit,
    onTypeFilterChanged: (TransactionType?) -> Unit,
    onCategoryFilterChanged: (EntityId?) -> Unit,
    onPaymentMethodFilterChanged: (PaymentMethod?) -> Unit,
    onPeriodPresetChanged: (TransactionPeriodPreset?) -> Unit,
    onSortOrderChanged: (TransactionSortOrder) -> Unit = {},
    onClearFilters: () -> Unit,
    onDeleteClicked: (TransactionDisplayModel) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmSingleDelete: () -> Unit,
    onConfirmInstallmentDelete: (InstallmentDeleteScope) -> Unit,
    onRetryObservation: () -> Unit,
    onAddTransactionClick: () -> Unit = {},
    onTransactionClick: (TransactionDisplayModel) -> Unit = {},
    onCustomPeriodChanged: (com.feniqo.mobile.domain.model.ReportPeriod?) -> Unit = {},
) {
    val visibleFilterCount = state.filter.visibleFilterCount()
    val periodLabel = localizedTransactionPeriodText(
        preset = state.filter.periodPreset,
        hasCustomPeriod = state.filter.customPeriod != null,
        customDateRangeText = state.summary.dateRangeText,
    )
    val filtersDescription = stringResource(Res.string.transactions_filters_count, visibleFilterCount)
    val currencyExcludedDescription = stringResource(
        Res.string.transactions_currency_excluded_semantics,
        state.summary.excludedDifferentCurrencyCount,
        state.summary.summaryCurrencyCode,
    )


    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "controls") {
            Spacer(modifier = Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("feniqo", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.transactions_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                ActiveWorkspaceIndicator(workspaceName = state.activeWorkspaceName, isCompact = true)
            }
            Spacer(Modifier.height(12.dp))
            ZenQuickFiltersRow(
                periodPreset = state.filter.periodPreset, periodLabel = periodLabel,
                categoryId = state.filter.categoryId, availableCategories = state.availableCategories,
                sortOrder = state.filter.sortOrder, onPeriodPresetChanged = onPeriodPresetChanged,
                onCategoryFilterChanged = onCategoryFilterChanged, onSortOrderChanged = onSortOrderChanged)
            Spacer(Modifier.height(12.dp))
            ZenPeriodSummaryCard(summary = state.summary, selectedType = state.filter.type, periodTitle = periodLabel)
            ZenTransactionTypeTabs(selectedType = state.filter.type, onTypeFilterChanged = onTypeFilterChanged)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ZenSearchField(query = state.searchQuery, onQueryChange = onSearchQueryChanged,
                    modifier = Modifier.weight(1f))
                Surface(onClick = onFilterClick, shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tune, filtersDescription, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            if (state.summary.excludedDifferentCurrencyCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .semantics {
                            contentDescription = currencyExcludedDescription
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            Res.string.transactions_currency_excluded,
                            state.summary.excludedDifferentCurrencyCount,
                            state.summary.summaryCurrencyCode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // 6. Aktif Filtre Çipleri
            if (visibleFilterCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ActiveTransactionFilterChips(
                        filter = state.filter,
                        availableCategories = state.availableCategories,
                        onTypeFilterChanged = onTypeFilterChanged,
                        onCategoryFilterChanged = onCategoryFilterChanged,
                        onPaymentMethodFilterChanged = onPaymentMethodFilterChanged,
                        onPeriodPresetChanged = onPeriodPresetChanged,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            }
            when {
                state.isLoading -> item { TransactionsLoadingContent(Modifier.fillMaxWidth().padding(24.dp)) }
                state.observationError != null -> item {
                    TransactionsErrorContent(
                        message = stringResource(Res.string.transactions_load_error),
                        onRetry = onRetryObservation,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                state.groupedItems.isEmpty() && state.searchQuery.isBlank() && visibleFilterCount == 0 -> item {
                    TransactionsEmptyContent(canAddTransaction, onAddTransactionClick, Modifier.fillMaxWidth().padding(24.dp))
                }
                state.groupedItems.isEmpty() -> item {
                    TransactionsSearchEmptyContent(onClearFilters, Modifier.fillMaxWidth().padding(24.dp))
                }
                else -> state.groupedItems.forEach { group ->
                    item(key = "header_${group.date}") {
                        TransactionDateGroupHeader(formattedDate = group.formattedDate,
                            dailyNetFormatted = group.dailyNetFormatted, isDailyNetNegative = group.isDailyNetNegative,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(group.items, key = { "item_${it.id.value}" }) { item ->
                        TransactionListItem(item, canEditTransaction, state.isDeleteInProgress,
                            onTransactionClick, onDeleteClicked, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
    // Filtre Alt Sayfası
    if (state.isFilterExpanded) {
        TransactionFilterSheet(
            filter = state.filter,
            availableCategories = state.availableCategories,
            onTypeFilterChanged = onTypeFilterChanged,
            onCategoryFilterChanged = onCategoryFilterChanged,
            onPaymentMethodFilterChanged = onPaymentMethodFilterChanged,
            onPeriodPresetChanged = onPeriodPresetChanged,
            onClearFilters = onClearFilters,
            onFilterDismiss = onFilterDismiss,
            onSortOrderChanged = onSortOrderChanged,
            onCustomPeriodChanged = onCustomPeriodChanged,
        )
    }

    // Silme Onay Diyalogları
    when (val dialog = state.deleteDialog) {
        is TransactionDeleteDialogState.Single -> {
            SingleTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = onConfirmSingleDelete,
                onDismiss = onDismissDeleteDialog,
            )
        }
        is TransactionDeleteDialogState.Installment -> {
            InstallmentTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = onConfirmInstallmentDelete,
                onDismiss = onDismissDeleteDialog,
            )
        }
        null -> Unit
    }
}

/**
 * Referans görsele uygun tekil dönem özeti ve gerçek veriden üretilen mini sütun grafik kartı.
 * Tümü görünümünde gelir, gider ve net açıkça ayrılır.
 */
@Composable
private fun ZenPeriodSummaryCard(
    summary: TransactionSummaryUiModel,
    selectedType: TransactionType?,
    periodTitle: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val useStackedSummary = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.3f
            val metrics = listOf(
                stringResource(Res.string.transactions_income) to summary.totalIncomeFormatted,
                stringResource(Res.string.transactions_expense) to summary.totalSpendingFormatted,
                stringResource(Res.string.transactions_count) to summary.transactionCount.toString(),
            )
            if (useStackedSummary) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    metrics.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(vertical = 16.dp)) {
                    metrics.forEachIndexed { index, (label, value) ->
                        if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.transactions_period_net), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(summary.netFormatted, fontWeight = FontWeight.Bold,
                color = if (summary.isNetPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.transactions_period_details))
        }
        if (expanded) {
            Text(stringResource(Res.string.transactions_period_net_explanation),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            DailyMiniBarChart(summary.dailyBars, periodTitle,
                Modifier.fillMaxWidth().height(72.dp).padding(16.dp))
        }
    }
}
/**
 * Gerçek veriden üretilen mini günlük sütun grafik bileşeni.
 * Float yalnızca Compose çizim anında ölçeklendirme için kullanılır.
 */
@Composable
private fun DailyMiniBarChart(
    bars: List<DailyTransactionBarUiModel>,
    periodTitle: String,
    modifier: Modifier = Modifier,
) {
    val emptyChartDescription = stringResource(Res.string.transactions_chart_empty_semantics, periodTitle)
    if (bars.isEmpty()) {
        Box(
            modifier = modifier.semantics {
                contentDescription = emptyChartDescription
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.transactions_chart_empty),
                style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        return
    }

    val displayBars = if (bars.size > 14) {
        val step = bars.size / 14f
        (0 until 14).map { i -> bars[(i * step).toInt().coerceIn(0, bars.lastIndex)] }
    } else {
        bars
    }

    val chartDescription = stringResource(Res.string.transactions_chart_semantics, periodTitle, bars.size)
    Column(
        modifier = modifier.semantics {
            contentDescription = chartDescription
        },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            displayBars.forEach { bar ->
                val heightPercent = if (bar.heightRatioBps > 0) {
                    (bar.heightRatioBps / 10000f).coerceIn(0.12f, 1f)
                } else {
                    0.04f
                }
                val barColor = if (bar.isDominantIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(fraction = heightPercent)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(barColor),
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Alt gün etiketleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayBars.first().dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (displayBars.size > 2) {
                Text(
                    text = displayBars[displayBars.size / 2].dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = displayBars.last().dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 3 Ana İşlem Türü Filtresi (Tümü, Gider, Gelir - Transferler sekmesi kaldırılmıştır).
 */
@Composable
private fun ZenTransactionTypeTabs(
    selectedType: TransactionType?,
    onTypeFilterChanged: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tabs = listOf(
            null to stringResource(Res.string.transactions_all),
            TransactionType.EXPENSE to stringResource(Res.string.transactions_expense),
            TransactionType.INCOME to stringResource(Res.string.transactions_income),
        )
        tabs.forEach { (type, label) ->
            val isSelected = selectedType == type
            Surface(
                onClick = { onTypeFilterChanged(type) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Hızlı Filtre Satırı (Dönem Seçici, Kategori Seçici ve Gerçek Sıralama Kontrolü).
 */
@Composable
private fun ZenQuickFiltersRow(
    periodPreset: TransactionPeriodPreset?,
    periodLabel: String,
    categoryId: EntityId?,
    availableCategories: List<CategoryFilterOptionUiModel>,
    sortOrder: TransactionSortOrder,
    onPeriodPresetChanged: (TransactionPeriodPreset?) -> Unit,
    onCategoryFilterChanged: (EntityId?) -> Unit,
    onSortOrderChanged: (TransactionSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPeriodMenuOpen by remember { mutableStateOf(false) }
    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Dönem Seçici Hapı (Ekranda TEK bir dönem kontrolü)
        Box {
            Surface(
                onClick = { isPeriodMenuOpen = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(Res.string.transactions_period_select),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = isPeriodMenuOpen,
                onDismissRequest = { isPeriodMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(TransactionPeriodPreset.THIS_MONTH.toLocalizedText()) },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_MONTH)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(TransactionPeriodPreset.THIS_WEEK.toLocalizedText()) },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_WEEK)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(TransactionPeriodPreset.TODAY.toLocalizedText()) },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.TODAY)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(TransactionPeriodPreset.LAST_30_DAYS.toLocalizedText()) },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.LAST_30_DAYS)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(TransactionPeriodPreset.THIS_YEAR.toLocalizedText()) },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_YEAR)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(localizedTransactionPeriodText(null, false, "")) },
                    onClick = {
                        onPeriodPresetChanged(null)
                        isPeriodMenuOpen = false
                    },
                )
            }
        }

        // 2. Kategori Seçici Hapı
        val selectedCategoryName = availableCategories.firstOrNull { it.id == categoryId }?.name
        Box {
            Surface(
                onClick = { isCategoryMenuOpen = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedCategoryName ?: stringResource(Res.string.transactions_all_categories),
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(Res.string.transactions_category_select),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = isCategoryMenuOpen,
                onDismissRequest = { isCategoryMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.transactions_all_categories)) },
                    onClick = {
                        onCategoryFilterChanged(null)
                        isCategoryMenuOpen = false
                    },
                )
                availableCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onCategoryFilterChanged(category.id)
                            isCategoryMenuOpen = false
                        },
                    )
                }
            }
        }

        // 3. Sıralama Hapı (Gerçek sıralama seçimi)
        Box {
            Surface(
                onClick = { isSortMenuOpen = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = sortOrder.toLocalizedText(),
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(Res.string.transactions_sort_select),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = isSortMenuOpen,
                onDismissRequest = { isSortMenuOpen = false },
            ) {
                TransactionSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.toLocalizedText()) },
                        onClick = {
                            onSortOrderChanged(order)
                            isSortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Tema yüzeyine uyumlu yuvarlatılmış arama çubuğu.
 */
@Composable
private fun ZenSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.transactions_search),
                        style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Res.string.transactions_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun TransactionFilterUiModel.visibleFilterCount(): Int {
    var count = 0
    if (type != null) count++
    if (categoryId != null) count++
    if (paymentMethod != null) count++
    if (periodPreset != null) count++
    return count
}

@Composable
private fun TransactionsLoadingContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(Res.string.transactions_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionsErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(Res.string.transactions_retry), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun TransactionsEmptyContent(
    canAddTransaction: Boolean,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.transactions_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.transactions_empty_body),
                style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (canAddTransaction) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onAddTransactionClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(Res.string.transactions_add_new), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun TransactionsSearchEmptyContent(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = stringResource(Res.string.transactions_no_results),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.transactions_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
        OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Text(stringResource(Res.string.transactions_clear_filters))
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun TransactionsScreenLoadingPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(isLoading = true),
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onSortOrderChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenEmptyPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(isLoading = false, groupedItems = emptyList()),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onSortOrderChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenListLightPreview() {
    val sampleItems = createSampleGroupedItems()
    FeniqoTheme(darkTheme = false) {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = sampleItems,
                    summary = createSampleSummary(),
                    availableCategories = listOf(
                        CategoryFilterOptionUiModel(
                            id = EntityId("cat-1"),
                            name = "Market",
                            type = TransactionType.EXPENSE,
                            colorHex = "#10B981",
                        ),
                    ),
                ),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onSortOrderChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenListDarkPreview() {
    val sampleItems = createSampleGroupedItems()
    FeniqoTheme(darkTheme = true) {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = sampleItems,
                    summary = createSampleSummary(),
                ),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onSortOrderChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenObservationErrorPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = emptyList(),
                    observationError = FinanceUiMessage.NETWORK_ERROR,
                ),
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onSortOrderChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun InstallmentDeleteDialogPreview() {
    val targetItem = TransactionDisplayModel(
        id = EntityId("trx-inst"),
        amount = Money(300000L, Currency.TRY),
        formattedAmount = "-3.000,00 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-elec"),
        categoryName = "Elektronik",
        categoryColorHex = "#3B82F6",
        categoryIconKey = null,
        description = "Laptop Taksiti",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 8, 23),
        installment = InstallmentDisplayModel(number = 1, total = 3, badgeText = "1/3"),
        hasReceipt = true,
    )
    FeniqoTheme {
        Surface {
            InstallmentTransactionDeleteDialog(
                dialog = TransactionDeleteDialogState.Installment(target = targetItem),
                isDeleteInProgress = false,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

private fun createSampleSummary(): TransactionSummaryUiModel {
    return TransactionSummaryUiModel(
        totalSpendingFormatted = "₺18.420,00",
        totalIncomeFormatted = "₺25.000,00",
        netFormatted = "+₺6.580,00",
        isNetPositive = true,
        transactionCount = 14,
        dateRangeText = "1 Ağu – 31 Ağu",
        periodTitle = "Bu Ay",
        dailyBars = listOf(
            DailyTransactionBarUiModel(LocalDate(2026, 8, 1), "1", 12000L, 0L, 2500, false),
            DailyTransactionBarUiModel(LocalDate(2026, 8, 5), "5", 45000L, 0L, 5000, false),
            DailyTransactionBarUiModel(LocalDate(2026, 8, 15), "15", 0L, 250000L, 10000, true),
            DailyTransactionBarUiModel(LocalDate(2026, 8, 22), "22", 30000L, 0L, 4000, false),
            DailyTransactionBarUiModel(LocalDate(2026, 8, 31), "31", 54290L, 0L, 6500, false),
        ),
    )
}

private fun createSampleGroupedItems(): List<DateGroupedTransactionsDisplayModel> {
    return listOf(
        DateGroupedTransactionsDisplayModel(
            date = LocalDate(2026, 8, 23),
            formattedDate = "Bugün",
            dailyNetFormatted = "-₺150,00",
            isDailyNetNegative = true,
            items = listOf(
                TransactionDisplayModel(
                    id = EntityId("t1"),
                    amount = Money(15000L, Currency.TRY),
                    formattedAmount = "-150,00 ₺",
                    type = TransactionType.EXPENSE,
                    categoryId = EntityId("c1"),
                    categoryName = "Market",
                    categoryColorHex = "#EF4444",
                    categoryIconKey = "groceries",
                    description = "Haftalık mutfak alışverişi",
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    transactionDate = LocalDate(2026, 8, 23),
                    installment = null,
                    hasReceipt = true,
                ),
            ),
        ),
        DateGroupedTransactionsDisplayModel(
            date = LocalDate(2026, 8, 22),
            formattedDate = "Dün",
            dailyNetFormatted = "+₺3.800,00",
            isDailyNetNegative = false,
            items = listOf(
                TransactionDisplayModel(
                    id = EntityId("t2"),
                    amount = Money(500000L, Currency.TRY),
                    formattedAmount = "+5.000,00 ₺",
                    type = TransactionType.INCOME,
                    categoryId = EntityId("c2"),
                    categoryName = "Maaş",
                    categoryColorHex = "#16A34A",
                    categoryIconKey = "salary",
                    description = "Ağustos Ek Ödeme",
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    transactionDate = LocalDate(2026, 8, 22),
                    installment = null,
                    hasReceipt = false,
                ),
                TransactionDisplayModel(
                    id = EntityId("t3"),
                    amount = Money(120000L, Currency.TRY),
                    formattedAmount = "-1.200,00 ₺",
                    type = TransactionType.EXPENSE,
                    categoryId = EntityId("c3"),
                    categoryName = "Teknoloji",
                    categoryColorHex = "#3B82F6",
                    categoryIconKey = null,
                    description = "Monitör Alımı",
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    transactionDate = LocalDate(2026, 8, 22),
                    installment = InstallmentDisplayModel(number = 1, total = 3, badgeText = "1/3"),
                    hasReceipt = false,
                ),
            ),
        ),
    )
}
