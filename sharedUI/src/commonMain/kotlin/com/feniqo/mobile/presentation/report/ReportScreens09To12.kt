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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId

// Tasarım Sistemi Renkleri
private val ColorWarmBg = Color(0xFFF7F5F0)
private val ColorSageGreen = Color(0xFF2D5A43)
private val ColorLightSage = Color(0xFFE8EFEA)
private val ColorDarkGraphite = Color(0xFF303536)
private val ColorRefinedRed = Color(0xFFC04D43)
private val ColorLightRed = Color(0xFFFDECEB)
private val ColorMutedGray = Color(0xFF888E90)
private val ColorCardSurface = Color(0xFFFFFFFF)

// ==========================================
// EKRAN 09: NAKİT AKIŞI RAPORU
// ==========================================
@Composable
fun CashFlowReportScreen(
    monthlyPoints: List<CashFlowMonthUiItem>,
    totalIncomeFormatted: String,
    totalExpenseFormatted: String,
    netDifferenceFormatted: String,
    isNetPositive: Boolean,
    averageIncomeFormatted: String,
    averageExpenseFormatted: String,
    strongestMonthLabel: String,
    weakestMonthLabel: String,
    selectedMonthCount: Int = 6,
    onSelectMonthRange: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ColorWarmBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = ColorDarkGraphite,
                    )
                }
                Text(
                    text = "Nakit Akışı",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorDarkGraphite,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Ay Seçim Segmenti (3 Ay / 6 Ay / 12 Ay)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(3 to "3 Ay", 6 to "6 Ay", 12 to "1 Yıl").forEach { (months, label) ->
                            val isSelected = selectedMonthCount == months
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectMonthRange(months) },
                                color = if (isSelected) ColorSageGreen else Color.White,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else ColorDarkGraphite,
                                    )
                                }
                            }
                        }
                    }
                }

                // Net Nakit Akışı Hero Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Dönem Nakit Akışı",
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = netDifferenceFormatted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNetPositive) ColorSageGreen else ColorRefinedRed,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Toplam Gelir", fontSize = 12.sp, color = ColorMutedGray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = totalIncomeFormatted,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSageGreen,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Toplam Gider", fontSize = 12.sp, color = ColorMutedGray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = totalExpenseFormatted,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorRefinedRed,
                                    )
                                }
                            }
                        }
                    }
                }

                // İki Yönlü Grafik Kartı
                if (monthlyPoints.isNotEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 20.dp,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Gelir ve Gider Dengesi",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                BidirectionalCashFlowChart(
                                    points = monthlyPoints,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // İstatistik Karşılaştırma Izgarası
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ReportCard(
                            modifier = Modifier.weight(1f),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 16.dp,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Aylık Ort. Gelir", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = averageIncomeFormatted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorSageGreen,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("En Güçlü: $strongestMonthLabel", fontSize = 11.sp, color = ColorDarkGraphite)
                            }
                        }

                        ReportCard(
                            modifier = Modifier.weight(1f),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 16.dp,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Aylık Ort. Gider", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = averageExpenseFormatted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorRefinedRed,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("En Zayıf: $weakestMonthLabel", fontSize = 11.sp, color = ColorDarkGraphite)
                            }
                        }
                    }
                }

                // Aylık Detay Listesi
                item {
                    Text(
                        text = "Aylık Dağılım",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(monthlyPoints) { month ->
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 14.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = month.monthLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "+${month.incomeFormatted}",
                                        fontSize = 12.sp,
                                        color = ColorSageGreen,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "-${month.expenseFormatted}",
                                        fontSize = 12.sp,
                                        color = ColorRefinedRed,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = month.netFormatted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (month.isNetPositive) ColorSageGreen else ColorRefinedRed,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (month.isNetPositive) "Net Fazla" else "Net Açık",
                                    fontSize = 11.sp,
                                    color = ColorMutedGray,
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ==========================================
// EKRAN 10: DÖNEM KARŞILAŞTIRMA RAPORU
// ==========================================
@Composable
fun PeriodComparisonReportScreen(
    currentPeriodLabel: String,
    previousPeriodLabel: String,
    currentNetFormatted: String,
    previousNetFormatted: String,
    netDifferenceFormatted: String,
    isNetImproved: Boolean,
    incomeDeltaFormatted: String,
    expenseDeltaFormatted: String,
    savingsRateDeltaFormatted: String,
    categoryComparisons: List<CategoryComparisonUiItem>,
    onSelectPeriodClick: () -> Unit,
    onCategoryClick: (CategoryComparisonUiItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ColorWarmBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = ColorDarkGraphite,
                        )
                    }
                    Text(
                        text = "Dönem Karşılaştırma",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                    )
                }

                Surface(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectPeriodClick() },
                    color = ColorLightSage,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = ColorSageGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Değiştir",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorSageGreen,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Dönem Başlık Rozeti
                item {
                    Text(
                        text = "$currentPeriodLabel  vs  $previousPeriodLabel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorMutedGray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Net Sonuç Karşılaştırma Hero Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Net Fark Değişimi",
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = netDifferenceFormatted,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNetImproved) ColorSageGreen else ColorRefinedRed,
                                )
                                Surface(
                                    color = if (isNetImproved) ColorLightSage else ColorLightRed,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (isNetImproved) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = if (isNetImproved) ColorSageGreen else ColorRefinedRed,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = if (isNetImproved) "İyileşme" else "Azalma",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isNetImproved) ColorSageGreen else ColorRefinedRed,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(currentPeriodLabel, fontSize = 11.sp, color = ColorMutedGray)
                                    Text(currentNetFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(previousPeriodLabel, fontSize = 11.sp, color = ColorMutedGray)
                                    Text(previousNetFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                            }
                        }
                    }
                }

                // 3 Metrik Kartı
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ReportCard(
                            modifier = Modifier.weight(1f),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 14.dp,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Gelir Değişimi", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(incomeDeltaFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                            }
                        }

                        ReportCard(
                            modifier = Modifier.weight(1f),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 14.dp,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Gider Değişimi", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(expenseDeltaFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                            }
                        }

                        ReportCard(
                            modifier = Modifier.weight(1f),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 14.dp,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Tasarruf", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(savingsRateDeltaFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                            }
                        }
                    }
                }

                // Kategori Değişimleri Başlığı
                item {
                    Text(
                        text = "Kategori Bazında Değişimler",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // Kategori Değişim Listesi
                items(categoryComparisons) { item ->
                    ReportCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(item) },
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 14.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.previousFormatted} → ${item.currentFormatted}",
                                    fontSize = 12.sp,
                                    color = ColorMutedGray,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = item.deltaFormatted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isIncreased) ColorRefinedRed else ColorSageGreen,
                                    )
                                    Text(
                                        text = item.percentageFormatted,
                                        fontSize = 11.sp,
                                        color = if (item.isIncreased) ColorRefinedRed else ColorSageGreen,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = ColorMutedGray,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ==========================================
// EKRAN 11: KARŞILAŞTIRMA DETAYI (NELER DEĞİŞTİ?)
// ==========================================
@Composable
fun ComparisonDetailScreen(
    categoryName: String,
    period1Label: String,
    period2Label: String,
    period1AmountFormatted: String,
    period2AmountFormatted: String,
    deltaAmountFormatted: String,
    percentageFormatted: String,
    isIncrease: Boolean,
    transactions: List<ReportTransactionUiItem>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ColorWarmBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = ColorDarkGraphite,
                    )
                }
                Text(
                    text = "$categoryName Değişimi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorDarkGraphite,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = if (isIncrease) "Bu Kategoride Harcama Arttı" else "Bu Kategoride Harcama Düştü",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncrease) ColorRefinedRed else ColorSageGreen,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fark: $deltaAmountFormatted ($percentageFormatted)",
                                fontSize = 14.sp,
                                color = ColorDarkGraphite,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(period2Label, fontSize = 12.sp, color = ColorMutedGray)
                                    Text(period2AmountFormatted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(period1Label, fontSize = 12.sp, color = ColorMutedGray)
                                    Text(period1AmountFormatted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Etkileyen İşlemler",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(transactions) { tx ->
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 14.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = tx.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tx.dateFormatted,
                                    fontSize = 12.sp,
                                    color = ColorMutedGray,
                                )
                            }
                            Text(
                                text = (if (tx.isExpense) "-" else "+") + tx.amountFormatted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.isExpense) ColorDarkGraphite else ColorSageGreen,
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ==========================================
// EKRAN 12: DÖNEM SEÇ (KARŞILAŞTIRMA SEÇİMİ SHEET)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectComparisonPeriodSheet(
    onDismissRequest: () -> Unit,
    onApplyPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedOption by remember { mutableStateOf("this_vs_last_month") }

    val options = listOf(
        "this_vs_last_month" to "Bu Ay  vs  Geçen Ay",
        "last_month_vs_two_months_ago" to "Geçen Ay  vs  Önceki Ay",
        "this_quarter_vs_last" to "Bu Çeyrek  vs  Geçen Çeyrek",
        "this_year_vs_last" to "Bu Yıl  vs  Geçen Yıl",
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ColorWarmBg,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Karşılaştırma Dönemi Seç",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorDarkGraphite,
            )
            Spacer(modifier = Modifier.height(16.dp))

            options.forEach { (key, label) ->
                val isSelected = selectedOption == key
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedOption = key },
                    color = if (isSelected) ColorLightSage else Color.White,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ColorSageGreen else ColorDarkGraphite,
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedOption = key },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ColorSageGreen,
                                unselectedColor = ColorMutedGray,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onApplyPreset(selectedOption)
                        onDismissRequest()
                    },
                color = ColorSageGreen,
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Karşılaştırmayı Uygula",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
