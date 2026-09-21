package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import kotlinx.coroutines.launch

/**
 * 21–26 ekranlarının ana stateless Compose ekranı.
 * Yalnızca state alır ve kullanıcı niyetlerini callback'lere yönlendirir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsScreenState,
    referenceDate: LocalDate,
    onApplyFilter: (ReportFilterUiState) -> Unit,
    onRemoveFilterChip: (ActiveFilterChipUiModel) -> Unit,
    onClearFilters: () -> Unit,
    onNavigateToCustomDateRange: () -> Unit,
    onNavigateToMultiCurrency: () -> Unit,
    onNavigateToSystemStatuses: () -> Unit = {},
    onSelectCategory: () -> Unit,
    onAddTransaction: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSyncStatus: () -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPeriodSummary: () -> Unit = {},
    onNavigateToAllReportsHub: () -> Unit = {},
    onNavigateToCategoryBreakdown: () -> Unit = {},
    onNavigateToCashFlow: () -> Unit = {},
    onNavigateToPeriodComparison: () -> Unit = {},
    onNavigateToSpendingCalendar: () -> Unit = {},
    onNavigateToBudgetPerformance: () -> Unit = {},
    onNavigateToSubscriptionSummary: () -> Unit = {},
    onNavigateToDebtSummary: () -> Unit = {},
    onNavigateToForecast: () -> Unit = {},
    onNavigateToFinancialInsights: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isPeriodSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val periodSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F5F0), // Sıcak kırık beyaz
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F5F0)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Geri dön",
                            tint = Color(0xFF303536),
                        )
                    }

                    Text(
                        text = "Raporlar",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = Color(0xFF303536),
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 1. Tarih Hapı (Yalnızca Dönem Seçimi Sheet'ini açar)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { isPeriodSheetOpen = true },
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF303536),
                            )
                            val presetText = ReportSummaryFormatter.formatPeriodPreset(state.filterState.periodPreset)
                            Text(
                                text = presetText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF303536),
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dönem seç",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF6B7280),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2. Filtre Butonu (Gelişmiş Filtreleri açar)
                    IconButton(
                        onClick = { isFilterSheetOpen = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Filtreleri aç",
                            tint = Color(0xFF2D5A43),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Çevrimdışı Uyarı Banner'ı
                if (state.connectionState is ReportConnectionState.Offline) {
                    OfflineSyncWarningBanner(
                        pendingOperationCount = state.connectionState.pendingOperationCount,
                        onSyncClick = onNavigateToSyncStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // İÇERİK DURUMU (Loading, EmptyWorkspace, EmptyFiltered, Success, Error)
            when (val content = state.contentState) {
                is ReportsContentState.Loading -> {
                    ReportLoadingView()
                }

                is ReportsContentState.EmptyWorkspace -> {
                    EmptyWorkspaceReportView(
                        onAddTransaction = onAddTransaction,
                        onNavigateToHome = onNavigateToHome,
                    )
                }

                is ReportsContentState.EmptyFiltered -> {
                    NoFilteredResultsView(
                        activeFilters = content.activeFilters,
                        onRemoveFilter = onRemoveFilterChip,
                        onClearFilters = onClearFilters,
                        onChangePeriod = onNavigateToCustomDateRange,
                    )
                }

                is ReportsContentState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReportErrorCard(
                            onRetry = onRetry,
                            errorMessage = content.message,
                        )
                    }
                }

                is ReportsContentState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item("active_chips") {
                            if (content.activeFilters.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    content.activeFilters.forEach { chip ->
                                        Surface(
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                                            color = Color.White,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = chip.label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                    color = Color(0xFF1F2937),
                                                )
                                                Text(
                                                    text = "×",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF6B7280),
                                                    modifier = Modifier.clickable { onRemoveFilterChip(chip) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 01 Net Sonuç Hero Kartı (Grafit #303536, dekoratif kavisler)
                        item("net_summary_card") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(role = Role.Button, onClick = onNavigateToPeriodSummary),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF303536),
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // Dekoratif kavisler
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val stroke = Stroke(width = 1.dp.toPx())
                                        drawCircle(
                                            color = Color(0x1AFFFFFF),
                                            radius = 120.dp.toPx(),
                                            center = Offset(size.width * 0.9f, size.height * 0.2f),
                                            style = stroke,
                                        )
                                        drawCircle(
                                            color = Color(0x12FFFFFF),
                                            radius = 180.dp.toPx(),
                                            center = Offset(size.width * 0.9f, size.height * 0.2f),
                                            style = stroke,
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                    ) {
                                        Text(
                                            text = "Net sonuç",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF94A3B8),
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = content.netFormatted,
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 32.sp,
                                            ),
                                            color = if (content.isNetPositive) Color(0xFF4ADE80) else Color(0xFFF87171),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val statusNote = if (content.isNetPositive) {
                                            "Gelirleriniz giderlerinizden ${content.netFormatted} fazla."
                                        } else {
                                            "Giderleriniz gelirlerinizden ${content.netFormatted} fazla."
                                        }
                                        Text(
                                            text = statusNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFCBD5E1),
                                        )
                                    }
                                }
                            }
                        }

                        // 01 Gelir, Gider ve Tasarruf Oranı 3'lü Eşit Kart
                        item("metrics_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MetricCard(
                                    title = "Gelir",
                                    value = content.incomeFormatted,
                                    valueColor = Color(0xFF2D5A43),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricCard(
                                    title = "Gider",
                                    value = content.expenseFormatted,
                                    valueColor = Color(0xFFDC2626),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricCard(
                                    title = "Tasarruf oranı",
                                    value = content.savingsRateFormatted,
                                    valueColor = Color(0xFF2D5A43),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 01 Aylık Trend (Son 6 Ay) Çizgi Grafiği Kartı
                        if (content.monthlyTrend.isNotEmpty()) {
                            item("trend_chart_card") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Aylık trend (son 6 ay)",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                ),
                                                color = Color(0xFF303536),
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2D5A43)))
                                                    Text(text = "Gelir", fontSize = 11.sp, color = Color(0xFF6B7280))
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFDC2626)))
                                                    Text(text = "Gider", fontSize = 11.sp, color = Color(0xFF6B7280))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        TrendDualLineChart(
                                            points = content.monthlyTrend,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }

                        // 01 Hızlı Raporlar 4'lü Izgara
                        item("quick_reports_title") {
                            Text(
                                text = "Hızlı raporlar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = Color(0xFF303536),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        item("quick_reports_grid") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    QuickReportTile(
                                        icon = Icons.Outlined.Schedule,
                                        title = "Kategoriler",
                                        subtitle = "Harcamalarınızı inceleyin",
                                        onClick = onNavigateToCategoryBreakdown,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickReportTile(
                                        icon = Icons.Outlined.BarChart,
                                        title = "Nakit akışı",
                                        subtitle = "Gelir ve gider hareketi",
                                        onClick = onNavigateToCashFlow,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    QuickReportTile(
                                        icon = Icons.Outlined.CompareArrows,
                                        title = "Dönem karşılaştırma",
                                        subtitle = "Önceki dönemle kıyasla",
                                        onClick = onNavigateToPeriodComparison,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickReportTile(
                                        icon = Icons.Outlined.CalendarMonth,
                                        title = "Harcama takvimi",
                                        subtitle = "Gün gün harcamalar",
                                        onClick = onNavigateToSpendingCalendar,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // 01 En Yüksek Gider Kartı
                        if (content.topCategory != null) {
                            item("top_category_card") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(role = Role.Button, onClick = onNavigateToCategoryBreakdown),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = CircleShape,
                                                color = Color(0xFFE8F5E9),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                imageVector = Icons.Outlined.BarChart,
                                                contentDescription = null,
                                                tint = Color(0xFF2D5A43),
                                                modifier = Modifier.size(20.dp),
                                            )
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = "En yüksek gider: ${content.topCategory.name}",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF303536),
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Bu ay en fazla harcamanız bu kategoride.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF6B7280),
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = content.topCategory.amountFormatted,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF303536),
                                            )
                                            Text(text = "›", fontSize = 20.sp, color = Color(0xFF9CA3AF))
                                        }
                                    }
                                }
                            }
                        }

                        // 03 Genel Bakış • Kategori Dağılımı Donut Grafiği & Legend
                        if (content.categoryBreakdown.isNotEmpty()) {
                            item("category_donut_card") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Kategori dağılımı",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF303536),
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            val donutColors = listOf(
                                                Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
                                                Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFF7CB342),
                                                Color(0xFFFDD835), Color(0xFFFB8C00), Color(0xFF6D4C41),
                                            )
                                            val slices = content.categoryBreakdown.map { it.name to it.shareRatio }

                                            ReportDonutChart(
                                                slices = slices,
                                                colors = donutColors,
                                                centerText = content.expenseFormatted,
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                content.categoryBreakdown.take(5).forEachIndexed { index, item ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(donutColors.getOrElse(index) { Color(0xFF2D5A43) }),
                                                        )
                                                        Text(
                                                            text = item.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color(0xFF303536),
                                                            modifier = Modifier.width(80.dp),
                                                        )
                                                        Text(
                                                            text = item.sharePercentageFormatted,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Color(0xFF6B7280),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 03 Finansal Ritim Kartları
                        if (content.financialRhythm != null) {
                            item("financial_rhythm_row") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                    ) {
                                         Column(modifier = Modifier.padding(12.dp)) {
                                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                 Icon(
                                                     imageVector = Icons.Outlined.CalendarToday,
                                                     contentDescription = null,
                                                     tint = Color(0xFF6B7280),
                                                     modifier = Modifier.size(13.dp),
                                                 )
                                                 Text(text = "En yoğun gün", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                             }
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Text(text = content.financialRhythm.busiestDayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                             Text(text = "En çok harcama yapılan gün", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Color(0xFF9CA3AF))
                                         }
                                     }

                                     Surface(
                                         modifier = Modifier.weight(1f),
                                         shape = RoundedCornerShape(14.dp),
                                         color = Color.White,
                                         border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                     ) {
                                         Column(modifier = Modifier.padding(12.dp)) {
                                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                 Icon(
                                                     imageVector = Icons.Outlined.BarChart,
                                                     contentDescription = null,
                                                     tint = Color(0xFF6B7280),
                                                     modifier = Modifier.size(13.dp),
                                                 )
                                                 Text(text = "En düşük hafta", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                             }
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Text(text = content.financialRhythm.lowestExpenseWeekLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                             Text(text = "En az harcama yapılan hafta", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Color(0xFF9CA3AF))
                                         }
                                     }
                                 }
                             }
                         }

                         // 03 Feniqo İçgörü Kartı
                         if (content.feniqoInsightText.isNotBlank()) {
                             item("insight_card") {
                                 Surface(
                                     modifier = Modifier.fillMaxWidth(),
                                     shape = RoundedCornerShape(16.dp),
                                     color = Color(0xFFF0FDF4),
                                     border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                 ) {
                                     Row(
                                         modifier = Modifier.padding(16.dp),
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(12.dp),
                                     ) {
                                         Icon(
                                             imageVector = Icons.Outlined.Lightbulb,
                                             contentDescription = null,
                                             tint = Color(0xFF166534),
                                             modifier = Modifier.size(22.dp),
                                         )
                                         Text(
                                             text = content.feniqoInsightText,
                                             style = MaterialTheme.typography.bodySmall.copy(
                                                 fontSize = 13.sp,
                                                 lineHeight = 18.sp,
                                             ),
                                             color = Color(0xFF166534),
                                         )
                                     }
                                 }
                             }
                         }

                         // 04 Rapor Merkezi Link Kartı
                         item("all_reports_link") {
                             Surface(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clip(RoundedCornerShape(16.dp))
                                     .clickable(role = Role.Button, onClick = onNavigateToAllReportsHub),
                                 shape = RoundedCornerShape(16.dp),
                                 color = Color(0xFF2D5A43),
                             ) {
                                 Row(
                                     modifier = Modifier.padding(16.dp),
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                 ) {
                                     Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(10.dp),
                                     ) {
                                         Icon(
                                             imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                             contentDescription = null,
                                             tint = Color.White,
                                             modifier = Modifier.size(22.dp),
                                         )
                                         Column {
                                             Text(
                                                 text = "Tüm Raporlar Merkezi",
                                                 style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                 color = Color.White,
                                             )
                                             Text(
                                                 text = "Bütçe, abonelik, borç, tahmin ve nakit akışı",
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = Color(0xFFD1FAE5),
                                             )
                                         }
                                     }

                                     Text(text = "›", fontSize = 24.sp, color = Color.White)
                                 }
                             }
                         }

                        // 23 Çoklu Para Birimi Geçiş Kartı
                        if (content.multiCurrencySummaries.size > 1) {
                            item("multi_currency_entry") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(role = Role.Button, onClick = onNavigateToMultiCurrency),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column {
                                            Text(
                                                text = "Çoklu para birimi",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF303536),
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${content.multiCurrencySummaries.size} farklı para biriminde işlem kaydı var.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF6B7280),
                                            )
                                        }

                                        Text(
                                            text = "›",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Light,
                                            color = Color(0xFF9CA3AF),
                                        )
                                    }
                                }
                            }
                        }

                        item("bottom_spacer") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    // 21 Rapor Filtreleri Sheet'i
    if (isFilterSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isFilterSheetOpen = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = null,
        ) {
            ReportFiltersSheetContent(
                initialFilter = state.filterState,
                referenceDate = referenceDate,
                onApplyFilter = { newFilter ->
                    onApplyFilter(newFilter)
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        isFilterSheetOpen = false
                    }
                },
                onNavigateToCustomDateRange = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        isFilterSheetOpen = false
                        onNavigateToCustomDateRange()
                    }
                },
                onSelectCategory = onSelectCategory,
                onDismiss = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        isFilterSheetOpen = false
                    }
                },
            )
        }
    }

    // 21a Doğrudan Dönem Seçimi Sheet'i
    if (isPeriodSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isPeriodSheetOpen = false },
            sheetState = periodSheetState,
            containerColor = Color.White,
        ) {
            ReportPeriodPickerSheetContent(
                currentPreset = state.filterState.periodPreset,
                onSelectPreset = { preset ->
                    onApplyFilter(state.filterState.copy(periodPreset = preset, customStartDate = null, customEndDate = null))
                    coroutineScope.launch { periodSheetState.hide() }.invokeOnCompletion {
                        isPeriodSheetOpen = false
                    }
                },
                onNavigateToCustomDateRange = {
                    coroutineScope.launch { periodSheetState.hide() }.invokeOnCompletion {
                        isPeriodSheetOpen = false
                        onNavigateToCustomDateRange()
                    }
                },
                onDismiss = {
                    coroutineScope.launch { periodSheetState.hide() }.invokeOnCompletion {
                        isPeriodSheetOpen = false
                    }
                },
            )
        }
    }
}

@Composable
private fun QuickReportTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2D5A43),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                color = Color(0xFF303536),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color(0xFF6B7280),
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = valueColor,
            )
        }
    }
}
