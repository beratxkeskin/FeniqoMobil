package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.LocalDate

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
// EKRAN 13: HARCAMA TAKVİMİ
// ==========================================
@Composable
fun SpendingCalendarReportScreen(
    monthLabel: String,
    days: List<CalendarDayUiModel>,
    selectedDay: CalendarDayUiModel?,
    dayTransactions: List<ReportTransactionUiItem>,
    onSelectDay: (CalendarDayUiModel) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
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
                        text = "Harcama Takvimi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                    )
                }

                // Ay Geçiş Kontrolleri
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Önceki Ay", tint = ColorDarkGraphite)
                    }
                    Text(
                        text = monthLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Sonraki Ay", tint = ColorDarkGraphite)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Takvim Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Gün Başlıkları (Pzt ... Paz)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                            ) {
                                listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach { dayName ->
                                    Text(
                                        text = dayName,
                                        fontSize = 11.sp,
                                        color = ColorMutedGray,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(36.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 7xN Gün Matrisi
                            val dayChunks = days.chunked(7)
                            dayChunks.forEach { week ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    week.forEach { day ->
                                        val isSelected = selectedDay?.date == day.date
                                        val heatColor = when (day.heatLevel) {
                                            0 -> Color(0xFFF2EFE9)
                                            1 -> Color(0xFFD3E2D8)
                                            2 -> Color(0xFFA5C5B1)
                                            3 -> Color(0xFF6B9B7E)
                                            else -> ColorSageGreen
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(heatColor)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(2.dp, ColorDarkGraphite, RoundedCornerShape(8.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable { onSelectDay(day) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = day.dayNumber.toString(),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (day.heatLevel >= 3) Color.White else ColorDarkGraphite,
                                            )
                                        }
                                    }
                                    // Eğer haftada 7 günden az varsa boşluk bırak
                                    for (i in week.size until 7) {
                                        Spacer(modifier = Modifier.size(38.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Isı Haritası Lejantı
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Az", fontSize = 11.sp, color = ColorMutedGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                listOf(
                                    Color(0xFFF2EFE9),
                                    Color(0xFFD3E2D8),
                                    Color(0xFFA5C5B1),
                                    Color(0xFF6B9B7E),
                                    ColorSageGreen,
                                ).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Çok Harcama", fontSize = 11.sp, color = ColorMutedGray)
                            }
                        }
                    }
                }

                // Seçilen Gün Detay Kartı
                if (selectedDay != null) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 16.dp,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            text = "${selectedDay.date.dayOfMonth} $monthLabel",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDarkGraphite,
                                        )
                                        Text(
                                            text = "${selectedDay.transactionCount} işlem",
                                            fontSize = 12.sp,
                                            color = ColorMutedGray,
                                        )
                                    }
                                    Text(
                                        text = selectedDay.expenseFormatted,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorRefinedRed,
                                    )
                                }
                            }
                        }
                    }
                }

                // Günün İşlemleri
                if (dayTransactions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Günün İşlemleri",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDarkGraphite,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    items(dayTransactions) { tx ->
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
                                        text = tx.categoryName,
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

// ==========================================
// EKRAN 14: BÜTÇE PERFORMANSI RAPORU
// ==========================================
@Composable
fun BudgetPerformanceReportScreen(
    periodLabel: String,
    totalBudgetFormatted: String,
    totalSpentFormatted: String,
    remainingFormatted: String,
    usagePercentageFormatted: String,
    usageRatio: Float,
    isExceeded: Boolean,
    budgetItems: List<BudgetPerformanceUiItem>,
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
                    text = "Bütçe Performansı",
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
                // Genel Bütçe Hero Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Toplam Bütçe Kullanımı",
                                    fontSize = 13.sp,
                                    color = ColorMutedGray,
                                    fontWeight = FontWeight.Medium,
                                )
                                Surface(
                                    color = if (isExceeded) ColorLightRed else ColorLightSage,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = usagePercentageFormatted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExceeded) ColorRefinedRed else ColorSageGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { usageRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (isExceeded) ColorRefinedRed else ColorSageGreen,
                                trackColor = ColorLightSage,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Harcanan", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(totalSpentFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Toplam Limit", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(totalBudgetFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorDarkGraphite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Kalan Bütçe", fontSize = 11.sp, color = ColorMutedGray)
                                    Text(
                                        text = remainingFormatted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExceeded) ColorRefinedRed else ColorSageGreen,
                                    )
                                }
                            }
                        }
                    }
                }

                // Kategori Bütçeleri Başlığı
                item {
                    Text(
                        text = "Kategori Bütçeleri",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // Kategori Bütçe Listesi
                items(budgetItems) { item ->
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
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
                                )
                                Text(
                                    text = item.usagePercentageFormatted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isExceeded) ColorRefinedRed else ColorSageGreen,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { item.usageRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (item.isExceeded) ColorRefinedRed else ColorSageGreen,
                                trackColor = ColorLightSage,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Harcanan: ${item.spentFormatted}",
                                    fontSize = 12.sp,
                                    color = ColorMutedGray,
                                )
                                Text(
                                    text = "Limit: ${item.budgetFormatted}",
                                    fontSize = 12.sp,
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
// EKRAN 15: ABONELİK ÖZETİ RAPORU
// ==========================================
@Composable
fun SubscriptionSummaryReportScreen(
    monthlyTotalFormatted: String,
    activeSubscriptionCount: Int,
    upcomingSubscriptions: List<SubscriptionReportUiItem>,
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
                    text = "Abonelik Özeti",
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
                                text = "Aylık Düzenli Abonelik Yükü",
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = monthlyTotalFormatted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDarkGraphite,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = ColorLightSage,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "$activeSubscriptionCount Aktif Abonelik",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorSageGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                // Yaklaşan Yenilemeler Başlığı
                item {
                    Text(
                        text = "Yaklaşan Yenilemeler",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (upcomingSubscriptions.isEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 14.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Kayıtlı aktif abonelik bulunmuyor.",
                                    fontSize = 14.sp,
                                    color = ColorMutedGray,
                                )
                            }
                        }
                    }
                } else {
                    items(upcomingSubscriptions) { sub ->
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
                                        text = sub.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorDarkGraphite,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Yenilenme: ${sub.renewalDateFormatted}",
                                        fontSize = 12.sp,
                                        color = ColorMutedGray,
                                    )
                                }

                                Text(
                                    text = sub.amountFormatted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDarkGraphite,
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
// EKRAN 16: BORÇ VE ALACAK ÖZETİ RAPORU
// ==========================================
@Composable
fun DebtSummaryReportScreen(
    totalDebtFormatted: String,
    totalReceivableFormatted: String,
    netBalanceFormatted: String,
    isNetPositive: Boolean,
    deadlines: List<DebtDeadlineUiItem>,
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
                    text = "Borç ve Alacak Özeti",
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
                // Net Durum Hero Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = ColorCardSurface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Net Pozisyon (Alacak - Borç)",
                                fontSize = 13.sp,
                                color = ColorMutedGray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = netBalanceFormatted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNetPositive) ColorSageGreen else ColorRefinedRed,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = ColorLightRed,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Toplam Borç", fontSize = 11.sp, color = ColorRefinedRed)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(totalDebtFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorRefinedRed)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = ColorLightSage,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Toplam Alacak", fontSize = 11.sp, color = ColorSageGreen)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(totalReceivableFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorSageGreen)
                                    }
                                }
                            }
                        }
                    }
                }

                // Yaklaşan Vadeler Başlığı
                item {
                    Text(
                        text = "Yaklaşan Vadeler",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDarkGraphite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (deadlines.isEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ColorCardSurface,
                            cornerRadius = 14.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Kayıtlı açık borç veya alacak bulunmuyor.",
                                    fontSize = 14.sp,
                                    color = ColorMutedGray,
                                )
                            }
                        }
                    }
                } else {
                    items(deadlines) { item ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDarkGraphite,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (item.isReceivable) ColorLightSage else ColorLightRed,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = if (item.isReceivable) "Alacak" else "Borç",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isReceivable) ColorSageGreen else ColorRefinedRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Vade: ${item.dueDateFormatted}",
                                            fontSize = 12.sp,
                                            color = ColorMutedGray,
                                        )
                                        if (item.isOverdue) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• Gecikmiş",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorRefinedRed,
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.amountFormatted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isReceivable) ColorSageGreen else ColorDarkGraphite,
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
