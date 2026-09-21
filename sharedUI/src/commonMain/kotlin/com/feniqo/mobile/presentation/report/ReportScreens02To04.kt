package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.YearMonth

/**
 * 02 ve 03 numaralı onaylı tasarım ekranları: Dönem özeti ve kaydırılmış içerik.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSummaryScreen(
    currentMonth: YearMonth,
    incomeFormatted: String,
    expenseFormatted: String,
    netFormatted: String,
    isNetPositive: Boolean,
    savingsRateFormatted: String,
    transactionCount: Int,
    dailyAverageExpenseFormatted: String,
    weeklyData: List<Pair<String, Pair<Long, Long>>>,
    topCategories: List<CategoryBreakdownUiItem>,
    donutSlices: List<Pair<String, Float>>,
    financialRhythm: FinancialRhythmUiModel?,
    insightText: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigateToCategoryBreakdown: () -> Unit,
    onNavigateToPeriodComparison: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F5F0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF303536),
                    )
                }
                Text(
                    text = "Dönem özeti",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = Color(0xFF303536),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Ay Seçici
            item("month_selector") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Önceki Ay",
                                tint = Color(0xFF303536),
                            )
                        }
                        Text(
                            text = "${ReportSummaryFormatter.monthName(currentMonth.month)} ${currentMonth.year}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF303536),
                        )
                        IconButton(onClick = onNextMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Sonraki Ay",
                                tint = Color(0xFF303536),
                            )
                        }
                    }
                }
            }

            // 3 Sütunlu Gelir / Gider / Net Kartı
            item("summary_3_col") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(text = "Gelir", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = incomeFormatted, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2D5A43))
                        }
                        Column {
                            Text(text = "Gider", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = expenseFormatted, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                        }
                        Column {
                            Text(text = "Net", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = netFormatted, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (isNetPositive) Color(0xFF2D5A43) else Color(0xFFDC2626))
                        }
                    }
                }
            }

            // Haftalık Gelir ve Gider Bar Grafiği
            item("weekly_chart") {
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
                            Text(text = "Gelir ve gider (haftalık)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        WeeklyDualBarChart(weeklyData = weeklyData)
                    }
                }
            }

            // Temel Göstergeler
            item("key_indicators") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "Temel göstergeler", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))

                        IndicatorRow(
                            iconVector = Icons.Outlined.Percent,
                            title = "Tasarruf oranı",
                            subtitle = "Gelirinizin bu kadarını biriktirdiniz.",
                            value = savingsRateFormatted,
                        )
                        IndicatorRow(
                            iconVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = "İşlem sayısı",
                            subtitle = "Bu dönemdeki toplam işlem.",
                            value = transactionCount.toString(),
                        )
                        IndicatorRow(
                            iconVector = Icons.Outlined.CalendarToday,
                            title = "Günlük ortalama gider",
                            subtitle = "Toplam giderin günlük ortalaması.",
                            value = dailyAverageExpenseFormatted,
                        )
                    }
                }
            }

            // En Çok Harcama Yapılan Kategoriler (1, 2, 3)
            if (topCategories.isNotEmpty()) {
                item("top_categories") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "En çok harcama yapılan kategoriler", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))

                            topCategories.take(3).forEachIndexed { index, item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(24.dp),
                                                shape = CircleShape,
                                                color = Color(0xFFF3F4F6),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(text = (index + 1).toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF303536))
                                                }
                                            }
                                            Text(text = item.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF303536))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(text = item.amountFormatted, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                            Text(text = item.sharePercentageFormatted, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { item.shareRatio.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFFE53935),
                                        trackColor = Color(0xFFF3F4F6),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Kategori Dağılımı Donut Grafiği (Ekran 03)
            if (donutSlices.isNotEmpty()) {
                item("donut_section") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Kategori dağılımı", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val colors = listOf(Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF1E88E5), Color(0xFF00897B))
                                ReportDonutChart(slices = donutSlices, colors = colors, centerText = expenseFormatted)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    donutSlices.take(5).forEachIndexed { i, (name, ratio) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.getOrElse(i) { Color(0xFF2D5A43) }))
                                            Text(text = name, style = MaterialTheme.typography.bodySmall, color = Color(0xFF303536), modifier = Modifier.width(75.dp))
                                            Text(text = "%${(ratio * 100).toInt()}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF6B7280))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Finansal Ritim (Ekran 03)
            if (financialRhythm != null) {
                item("rhythm_section") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF6B7280),
                                    )
                                    Text(text = "En yoğun gün", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = financialRhythm.busiestDayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                Text(text = "Bu ayın en fazla harcama günü", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Color(0xFF9CA3AF))
                            }
                        }
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.BarChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF6B7280),
                                    )
                                    Text(text = "En düşük hafta", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = financialRhythm.lowestExpenseWeekLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                Text(text = "En az harcama yapılan hafta", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Color(0xFF9CA3AF))
                            }
                        }
                    }
                }
            }

            // Feniqo İçgörü Kartı (Ekran 03)
            if (insightText.isNotBlank()) {
                item("feniqo_insight") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0FDF4),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF166534),
                            )
                            Text(text = insightText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = Color(0xFF166534))
                        }
                    }
                }
            }

            // Alt Navigasyon Linkleri (Ekran 03)
            item("action_links") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onNavigateToCategoryBreakdown),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.PieChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF303536),
                                )
                                Text(text = "Tüm kategoriler", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF9CA3AF),
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onNavigateToPeriodComparison),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.CompareArrows,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF303536),
                                )
                                Text(text = "Dönemi karşılaştır", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF9CA3AF),
                            )
                        }
                    }
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun IndicatorRow(
    iconVector: ImageVector,
    title: String,
    subtitle: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFFF3F4F6)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF303536),
                    )
                }
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Color(0xFF6B7280))
            }
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
    }
}

/**
 * 04 Numaralı onaylı tasarım ekranı: Rapor merkezi (Tüm raporlar).
 */
@Composable
fun AllReportsHubScreen(
    onNavigateToCategoryBreakdown: () -> Unit,
    onNavigateToCashFlow: () -> Unit,
    onNavigateToPeriodComparison: () -> Unit,
    onNavigateToSpendingCalendar: () -> Unit,
    onNavigateToBudgetPerformance: () -> Unit,
    onNavigateToSubscriptionSummary: () -> Unit,
    onNavigateToDebtSummary: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToFinancialInsights: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F5F0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF303536),
                    )
                }
                Text(
                    text = "Tüm raporlar",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = Color(0xFF303536),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hub_items") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportHubCard(
                        icon = Icons.Outlined.PieChart,
                        title = "Harcama analizi & Kategori dağılımı",
                        subtitle = "Giderlerinizi ve gelirlerinizi kategorilere göre görün",
                        onClick = onNavigateToCategoryBreakdown,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.BarChart,
                        title = "Nakit akışı",
                        subtitle = "Gelir ve gider hareketlerinizi aylık takip edin",
                        onClick = onNavigateToCashFlow,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.CompareArrows,
                        title = "Dönem karşılaştırma",
                        subtitle = "Farklı dönemleri detaylı karşılaştırın",
                        onClick = onNavigateToPeriodComparison,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "Harcama takvimi",
                        subtitle = "Gün gün harcamalarınızı takvimde görün",
                        onClick = onNavigateToSpendingCalendar,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.TrackChanges,
                        title = "Bütçe performansı",
                        subtitle = "Bütçenize göre gerçekleşmeleri izleyin",
                        onClick = onNavigateToBudgetPerformance,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.Autorenew,
                        title = "Abonelik özeti",
                        subtitle = "Düzenli ödemelerinizi ve trendi görüntüleyin",
                        onClick = onNavigateToSubscriptionSummary,
                    )
                    ReportHubCard(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "Borç ve alacak özeti",
                        subtitle = "Size ait borç ve alacakları takip edin",
                        onClick = onNavigateToDebtSummary,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.AutoGraph,
                        title = "Gelecek dönem tahmini",
                        subtitle = "Gelecek ayki tahmini nakit farkınızı inceleyin",
                        onClick = onNavigateToForecast,
                    )
                    ReportHubCard(
                        icon = Icons.Outlined.Lightbulb,
                        title = "Finansal içgörüler",
                        subtitle = "Tasarruf ve harcama değişim analizleri",
                        onClick = onNavigateToFinancialInsights,
                    )
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReportHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF3F4F6),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color(0xFF303536),
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = Color(0xFF303536),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF6B7280),
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF9CA3AF),
            )
        }
    }
}
