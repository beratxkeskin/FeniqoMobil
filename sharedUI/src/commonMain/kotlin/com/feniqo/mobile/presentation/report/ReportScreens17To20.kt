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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.ForecastProjectionPoint

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
// EKRAN 17: GELECEK AY TAHMİNİ
// ==========================================
@Composable
fun ForecastOverviewReportScreen(
    forecastMonthLabel: String,
    projectedNetFormatted: String,
    isProjectedNetPositive: Boolean,
    projectedIncomeFormatted: String,
    fixedExpenseFormatted: String,
    variableExpenseFormatted: String,
    sources: List<ForecastSourceUiItem>,
    onViewDetailClick: () -> Unit,
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
                        text = "Gelecek Ay Tahmini",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                    )
                }

                Surface(
                    color = ColorLightSage,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ColorSageGreen,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tahmin",
                            fontSize = 11.sp,
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
                // Hero Tahmin Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "$forecastMonthLabel Tahmini Net Bakiye",
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = projectedNetFormatted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProjectedNetPositive) ColorSageGreen else ColorRefinedRed,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Beklenen Gelir", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(projectedIncomeFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorSageGreen)
                                }
                                Column {
                                    Text("Sabit Gider", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(fixedExpenseFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Değişken Gider", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(variableExpenseFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorRefinedRed)
                                }
                            }
                        }
                    }
                }

                // Tahmini Oluşturan Kaynaklar Başlığı
                item {
                    Text(
                        text = "Tahmini Oluşturan Kalemler",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(sources) { source ->
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 14.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = source.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Text(
                                    text = source.amountFormatted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { source.ratio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ColorSageGreen,
                                trackColor = ColorLightSage,
                            )
                        }
                    }
                }

                // Bilgilendirme ve Güvenlik Feragati
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF2EFE9),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ColorMutedGray,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Tahminler geçmiş harcama ritminiz ve kayıtlı düzenli ödemelerinize dayanır; kesin sonuç veya yatırım tavsiyesi teşkil etmez.",
                                fontSize = 12.sp,
                                color = ColorMutedGray,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // Detaylı Tahmini İncele Butonu
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onViewDetailClick() },
                        color = ColorSageGreen,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Detaylı Projeksiyonu Gör",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
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
// EKRAN 18: TAHMİN DETAYI
// ==========================================
@Composable
fun ForecastDetailReportScreen(
    forecastMonthLabel: String,
    projectedNetFormatted: String,
    isProjectedNetPositive: Boolean,
    projectionPoints: List<ForecastProjectionPoint>,
    dailyAverageFormatted: String,
    activeRecurringCount: Int,
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
                    text = "Projeksiyon Detayı",
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
                // Model Varsayımları Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Hesaplama Modeli ve Varsayımlar",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDarkGraphite,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "• Geçmiş 90 günlük günlük değişken harcama ortalaması: $dailyAverageFormatted",
                                fontSize = 13.sp,
                                color = ColorDarkGraphite,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Aktif yinelenen işlem ve abonelik sayısı: $activeRecurringCount",
                                fontSize = 13.sp,
                                color = ColorDarkGraphite,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Gerçekleşen günler gerçek işlemlerden, kalan günler ise beklenen akıştan hesaplanmıştır.",
                                fontSize = 13.sp,
                                color = ColorDarkGraphite,
                            )
                        }
                    }
                }

                // Projeksiyon Noktaları Listesi
                item {
                    Text(
                        text = "Aylık Projeksiyon İlerlemesi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(projectionPoints) { point ->
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${point.yearMonth.monthNumber}. Ay (${point.yearMonth.year})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorDarkGraphite,
                                    )
                                    if (point.isForecast) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = ColorLightSage,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = "Tahmin",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorSageGreen,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Gelir: +₺${point.income.amountMinor / 100} • Gider: -₺${point.expense.amountMinor / 100}",
                                    fontSize = 12.sp,
                                    color = ColorMutedGray,
                                )
                            }

                            val netMinor = point.income.amountMinor - point.expense.amountMinor
                            Text(
                                text = "${if (netMinor >= 0) "+" else ""}₺${netMinor / 100}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netMinor >= 0) ColorSageGreen else ColorRefinedRed,
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
// EKRAN 19: FİNANSAL İÇGÖRÜLER
// ==========================================
@Composable
fun FinancialInsightsReportScreen(
    insights: List<FinancialInsightUiItem>,
    onInsightClick: (FinancialInsightUiItem) -> Unit,
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
                    text = "Finansal İçgörüler",
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
                    Text(
                        text = "Verilerinize Dayalı Önemli Değişimler",
                        fontSize = 13.sp,
                        color = ColorMutedGray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                items(insights) { item ->
                    ReportCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInsightClick(item) },
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 16.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Surface(
                                        color = if (item.isPositive) ColorLightSage else ColorLightRed,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (item.isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = if (item.isPositive) ColorSageGreen else ColorRefinedRed,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(16.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDarkGraphite,
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 12.sp,
                                            color = ColorMutedGray,
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = item.amountFormatted,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDarkGraphite,
                                        )
                                        Text(
                                            text = item.deltaFormatted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.isPositive) ColorSageGreen else ColorRefinedRed,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = ColorMutedGray,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = item.explanation,
                                fontSize = 12.sp,
                                color = ColorDarkGraphite,
                                lineHeight = 16.sp,
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
// EKRAN 20: İÇGÖRÜ DETAYI
// ==========================================
@Composable
fun InsightDetailReportScreen(
    insight: FinancialInsightUiItem,
    transactions: List<ReportTransactionUiItem> = emptyList(),
    onViewTransactionsClick: () -> Unit = {},
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
                    text = "İçgörü Detayı",
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
                // Hero Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = insight.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDarkGraphite,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = insight.subtitle,
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Toplam Tutar", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(insight.amountFormatted, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Değişim Oranı", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(
                                        text = insight.deltaFormatted,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (insight.isPositive) ColorSageGreen else ColorRefinedRed,
                                    )
                                }
                            }
                        }
                    }
                }

                // Nasıl Hesaplandı Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 16.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = ColorSageGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nasıl Hesaplandı?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = insight.explanation,
                                fontSize = 13.sp,
                                color = ColorDarkGraphite,
                                lineHeight = 18.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Bu analiz ${insight.transactionCount} adet kayıtlı işlem üzerinden hesaplanmıştır.",
                                fontSize = 12.sp,
                                color = ColorMutedGray,
                            )
                        }
                    }
                }

                if (transactions.isNotEmpty()) {
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
                            cornerRadius = 12.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = tx.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorDarkGraphite,
                                    )
                                    Text(
                                        text = tx.dateFormatted,
                                        fontSize = 12.sp,
                                        color = ColorMutedGray,
                                    )
                                }
                                Text(
                                    text = (if (tx.isExpense) "-" else "+") + tx.amountFormatted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.isExpense) ColorDarkGraphite else ColorSageGreen,
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
