package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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
) {
    val visibleFilterCount = state.filter.visibleFilterCount()
    var isSearchExpanded by remember { mutableStateOf(state.searchQuery.isNotBlank()) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Üst Başlık (Serif Başlık, Slogan, Dairesel Arama ve Filtre Butonları)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "İşlemler",
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Normal,
                                fontSize = 30.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        ActiveWorkspaceIndicator(
                            workspaceName = state.activeWorkspaceName,
                            isCompact = true,
                        )
                    }
                    Text(
                        text = "Paranızı takip edin, geleceğinizi şekillendirin.",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Dairesel Arama Butonu
                    Surface(
                        onClick = {
                            if (isSearchExpanded && state.searchQuery.isNotBlank()) {
                                onSearchQueryChanged("")
                            }
                            isSearchExpanded = !isSearchExpanded
                        },
                        shape = CircleShape,
                        color = if (isSearchExpanded || state.searchQuery.isNotBlank()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = if (isSearchExpanded) "Aramayı kapat" else "İşlemlerde ara",
                                tint = if (isSearchExpanded || state.searchQuery.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Dairesel Filtre Butonu
                    Box {
                        Surface(
                            onClick = onFilterClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Detaylı filtreleri aç",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (visibleFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                    }
                }
            }

            // 2. Açılır Arama Çubuğu (Yalnızca arama açıkken veya sorgu doluyken görünür)
            if (isSearchExpanded || state.searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                ZenSearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    onCloseSearch = {
                        onSearchQueryChanged("")
                        isSearchExpanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. İşlem Türü Segmentleri (Tümü, Gider, Gelir - Transferler kaldırıldı)
            ZenTransactionTypeTabs(
                selectedType = state.filter.type,
                onTypeFilterChanged = onTypeFilterChanged,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Hızlı Filtre Satırı (Dönem, Kategori, Sıralama - Tek dönem kontrolü)
            ZenQuickFiltersRow(
                periodPreset = state.filter.periodPreset,
                periodLabel = state.periodChipLabel,
                categoryId = state.filter.categoryId,
                availableCategories = state.availableCategories,
                sortOrder = state.filter.sortOrder,
                onPeriodPresetChanged = onPeriodPresetChanged,
                onCategoryFilterChanged = onCategoryFilterChanged,
                onSortOrderChanged = onSortOrderChanged,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Dönem Özet Kartı ve Gerçek Veriden Üretilen Mini Günlük Grafik
            ZenPeriodSummaryCard(
                summary = state.summary,
                selectedType = state.filter.type,
            )

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

            // 7. Gruplanmış İşlem Listesi
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                when {
                    state.isLoading -> {
                        TransactionsLoadingContent(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.observationError != null -> {
                        TransactionsErrorContent(
                            message = state.observationError.toDisplayText(),
                            onRetry = onRetryObservation,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.groupedItems.isEmpty() && state.searchQuery.isBlank() && visibleFilterCount == 0 -> {
                        TransactionsEmptyContent(
                            canAddTransaction = canAddTransaction,
                            onAddTransactionClick = onAddTransactionClick,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.groupedItems.isEmpty() -> {
                        TransactionsSearchEmptyContent(
                            onClearFilters = onClearFilters,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            state.groupedItems.forEach { group ->
                                item(key = "header_${group.date}") {
                                    val secondaryDateText = "${group.date.day} ${group.date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }} ${group.date.year}"
                                    TransactionDateGroupHeader(
                                        formattedDate = group.formattedDate,
                                        secondaryDateText = secondaryDateText,
                                        dailyNetFormatted = group.dailyNetFormatted,
                                        isDailyNetNegative = group.isDailyNetNegative,
                                    )
                                }

                                items(
                                    items = group.items,
                                    key = { "item_${it.id.value}" },
                                ) { item ->
                                    TransactionListItem(
                                        item = item,
                                        canEditTransaction = canEditTransaction,
                                        isDeleteInProgress = state.isDeleteInProgress,
                                        onTransactionClick = onTransactionClick,
                                        onDeleteClicked = onDeleteClicked,
                                    )
                                }
                            }
                        }
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sol Taraf: Rakamlar
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = summary.periodTitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (selectedType) {
                    TransactionType.EXPENSE -> {
                        Text(
                            text = summary.totalSpendingFormatted,
                            style = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Toplam Gider",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = FeniqoStatusColor.Error,
                        )
                    }
                    TransactionType.INCOME -> {
                        Text(
                            text = summary.totalIncomeFormatted,
                            style = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = FeniqoStatusColor.Success,
                        )
                        Text(
                            text = "Toplam Gelir",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = FeniqoStatusColor.Success,
                        )
                    }
                    null -> {
                        Text(
                            text = summary.netFormatted,
                            style = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = if (summary.isNetPositive) FeniqoStatusColor.Success else MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Gider: ${summary.totalSpendingFormatted}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = FeniqoStatusColor.Error,
                            )
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Text(
                                text = "Gelir: ${summary.totalIncomeFormatted}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = FeniqoStatusColor.Success,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Sağ Taraf: Mini Günlük Sütun Grafik
            DailyMiniBarChart(
                bars = summary.dailyBars,
                periodTitle = summary.periodTitle,
                modifier = Modifier
                    .weight(0.85f)
                    .height(56.dp),
            )
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
    if (bars.isEmpty()) {
        Box(
            modifier = modifier.semantics {
                contentDescription = "$periodTitle için işlem aktivitesi grafiği: Veri bulunmuyor"
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "İşlem verisi yok",
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

    Column(
        modifier = modifier.semantics {
            contentDescription = "$periodTitle için ${bars.size} günlük işlem aktivitesi grafiği"
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
                style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            if (displayBars.size > 2) {
                Text(
                    text = displayBars[displayBars.size / 2].dayLabel,
                    style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            Text(
                text = displayBars.last().dayLabel,
                style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
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
            null to "Tümü",
            TransactionType.EXPENSE to "Gider",
            TransactionType.INCOME to "Gelir",
        )
        tabs.forEach { (type, label) ->
            val isSelected = selectedType == type
            Surface(
                onClick = { onTypeFilterChanged(type) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.height(32.dp),
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
                        contentDescription = "Dönem seç",
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
                    text = { Text("Bu Ay") },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_MONTH)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Bu Hafta") },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_WEEK)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Bugün") },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.TODAY)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Son 30 Gün") },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.LAST_30_DAYS)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Bu Yıl") },
                    onClick = {
                        onPeriodPresetChanged(TransactionPeriodPreset.THIS_YEAR)
                        isPeriodMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tüm Zamanlar") },
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
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedCategoryName ?: "Tüm kategoriler",
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Kategori seç",
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
                    text = { Text("Tüm Kategoriler") },
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
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = sortOrder.toDisplayText(),
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Sıralama seç",
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
                        text = { Text(order.toDisplayText()) },
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
 * Beyaz yuvarlatılmış lüks arama çubuğu (Temizle ve kapat butonlu).
 */
@Composable
private fun ZenSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
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
                        text = "İşlem veya kategori ara...",
                        style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
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
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Temizle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            IconButton(
                onClick = onCloseSearch,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Aramayı kapat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
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
            text = "İşlemler yükleniyor…",
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
            Text("Yeniden Dene", color = MaterialTheme.colorScheme.onPrimary)
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
            Text(
                text = "🌱",
                fontSize = 40.sp,
            )
            Text(
                text = "Henüz bir işlem kaydı yok",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Harcama ve gelirlerinizi kaydederek finansal durumunuzu anlık olarak takip edin.",
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
                    Text("+ Yeni İşlem Ekle", color = MaterialTheme.colorScheme.onPrimary)
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
            text = "Sonuç bulunamadı",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Arama veya filtrelerinizi değiştirmeyi deneyin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
        OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Text("Filtreleri Temizle")
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
                    categoryColorHex = "#10B981",
                    categoryIconKey = null,
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
                    categoryColorHex = "#059669",
                    categoryIconKey = null,
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
