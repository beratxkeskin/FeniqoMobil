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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_action_back
import feniqomobil.sharedui.generated.resources.report_budget_perf_categories_title
import feniqomobil.sharedui.generated.resources.report_budget_perf_empty
import feniqomobil.sharedui.generated.resources.report_budget_perf_hero_title
import feniqomobil.sharedui.generated.resources.report_budget_perf_item_limit
import feniqomobil.sharedui.generated.resources.report_budget_perf_item_spent
import feniqomobil.sharedui.generated.resources.report_budget_perf_limit_label
import feniqomobil.sharedui.generated.resources.report_budget_perf_remaining_label
import feniqomobil.sharedui.generated.resources.report_budget_perf_spent_label
import feniqomobil.sharedui.generated.resources.report_budget_perf_title
import feniqomobil.sharedui.generated.resources.report_calendar_day_cell_semantics
import feniqomobil.sharedui.generated.resources.report_calendar_day_transactions_title
import feniqomobil.sharedui.generated.resources.report_calendar_heat_high
import feniqomobil.sharedui.generated.resources.report_calendar_heat_low
import feniqomobil.sharedui.generated.resources.report_calendar_next_month
import feniqomobil.sharedui.generated.resources.report_calendar_no_transactions
import feniqomobil.sharedui.generated.resources.report_calendar_prev_month
import feniqomobil.sharedui.generated.resources.report_calendar_title
import feniqomobil.sharedui.generated.resources.report_calendar_tx_count_plural
import feniqomobil.sharedui.generated.resources.report_category_deleted
import feniqomobil.sharedui.generated.resources.report_day_short_fri
import feniqomobil.sharedui.generated.resources.report_day_short_mon
import feniqomobil.sharedui.generated.resources.report_day_short_sat
import feniqomobil.sharedui.generated.resources.report_day_short_sun
import feniqomobil.sharedui.generated.resources.report_day_short_thu
import feniqomobil.sharedui.generated.resources.report_day_short_tue
import feniqomobil.sharedui.generated.resources.report_day_short_wed
import feniqomobil.sharedui.generated.resources.report_sub_summary_active_count_plural
import feniqomobil.sharedui.generated.resources.report_sub_summary_empty
import feniqomobil.sharedui.generated.resources.report_sub_summary_hero_title
import feniqomobil.sharedui.generated.resources.report_sub_summary_renewal_date
import feniqomobil.sharedui.generated.resources.report_sub_summary_title
import feniqomobil.sharedui.generated.resources.report_sub_summary_upcoming_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Tasarım Sistemi Renkleri
private val ColorSageGreen = Color(0xFF2D5A43)
private val ColorRefinedRed = Color(0xFFC04D43)

// ==========================================
// EKRAN 13: HARCAMA TAKVİMİ
// ==========================================
@Composable
fun SpendingCalendarReportScreen(
    currentMonth: YearMonth,
    days: List<CalendarDayUiModel>,
    selectedDay: CalendarDayUiModel?,
    dayTransactions: List<ReportTransactionUiItem>,
    maskAmounts: Boolean = false,
    onSelectDay: (CalendarDayUiModel) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthName = com.feniqo.mobile.presentation.common.localizedMonthName(currentMonth.monthNumber)
    val monthLabel = "$monthName ${currentMonth.year}"

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
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
                            contentDescription = stringResource(Res.string.report_action_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.report_calendar_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Ay Geçiş Kontrolleri
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(Res.string.report_calendar_prev_month),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = monthLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(Res.string.report_calendar_next_month),
                            tint = MaterialTheme.colorScheme.onSurface,
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
                // Takvim Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Gün Başlıkları (Pzt ... Paz / Mon ... Sun)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                            ) {
                                listOf(
                                    stringResource(Res.string.report_day_short_mon),
                                    stringResource(Res.string.report_day_short_tue),
                                    stringResource(Res.string.report_day_short_wed),
                                    stringResource(Res.string.report_day_short_thu),
                                    stringResource(Res.string.report_day_short_fri),
                                    stringResource(Res.string.report_day_short_sat),
                                    stringResource(Res.string.report_day_short_sun),
                                ).forEach { dayName ->
                                    Text(
                                        text = dayName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                                        val dayExpenseStr = day.expense.toLocalizedMaskedText(day.maskAmounts || maskAmounts)
                                        val dayA11y = stringResource(
                                            Res.string.report_calendar_day_cell_semantics,
                                            day.date.toLocalizedReadableDate(),
                                            day.transactionCount,
                                            dayExpenseStr,
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(heatColor)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .semantics {
                                                    contentDescription = dayA11y
                                                }
                                                .clickable { onSelectDay(day) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = day.dayNumber.toString(),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (day.heatLevel >= 3) Color.White else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
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
                                Text(
                                    text = stringResource(Res.string.report_calendar_heat_low),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                                Text(
                                    text = stringResource(Res.string.report_calendar_heat_high),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Seçilen Gün Detay Kartı
                if (selectedDay != null) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
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
                                            text = selectedDay.date.toLocalizedReadableDate(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = pluralStringResource(
                                                Res.plurals.report_calendar_tx_count_plural,
                                                selectedDay.transactionCount,
                                                selectedDay.transactionCount,
                                            ),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = selectedDay.expense.toLocalizedMaskedText(selectedDay.maskAmounts || maskAmounts),
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
                if (selectedDay != null) {
                    item {
                        Text(
                            text = stringResource(Res.string.report_calendar_day_transactions_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    if (dayTransactions.isEmpty()) {
                        item {
                            ReportCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                cornerRadius = 14.dp,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.report_calendar_no_transactions),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(dayTransactions) { tx ->
                            val catTitle = if (tx.isCategoryMissing || tx.categoryName == null) {
                                stringResource(Res.string.report_category_deleted)
                            } else {
                                tx.categoryName
                            }
                            ReportCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = MaterialTheme.colorScheme.surface,
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
                                            text = tx.resolveLocalizedTitle(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = catTitle,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val isExpense = tx.type == TransactionType.EXPENSE
                                    val sign = if (isExpense) "-" else "+"
                                    Text(
                                        text = sign + tx.amount.toLocalizedMaskedText(tx.maskAmounts || maskAmounts),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpense) MaterialTheme.colorScheme.onSurface else ColorSageGreen,
                                    )
                                }
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
    currentMonth: YearMonth,
    totalBudget: Money,
    totalSpent: Money,
    remaining: MoneyDelta,
    usageBasisPoints: Int,
    isExceeded: Boolean,
    budgetItems: List<BudgetPerformanceUiItem>,
    maskAmounts: Boolean = false,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usageRatio = (usageBasisPoints / 10_000f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
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
                        contentDescription = stringResource(Res.string.report_action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(Res.string.report_budget_perf_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.report_budget_perf_hero_title),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Surface(
                                    color = if (isExceeded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = formatLocalizedRateBasisPoints(usageBasisPoints),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExceeded) ColorRefinedRed else ColorSageGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { usageRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (isExceeded) ColorRefinedRed else ColorSageGreen,
                                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(Res.string.report_budget_perf_spent_label),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = totalSpent.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(Res.string.report_budget_perf_limit_label),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = totalBudget.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(Res.string.report_budget_perf_remaining_label),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = remaining.toLocalizedMaskedText(maskAmounts),
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
                        text = stringResource(Res.string.report_budget_perf_categories_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (budgetItems.isEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            cornerRadius = 14.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.report_budget_perf_empty),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(budgetItems) { item ->
                        val catTitle = if (item.isCategoryMissing || item.name == null) {
                            stringResource(Res.string.report_category_deleted)
                        } else {
                            item.name
                        }
                        val itemRatio = (item.usageBasisPoints / 10_000f).coerceIn(0f, 1f)

                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            cornerRadius = 14.dp,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = catTitle,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = formatLocalizedRateBasisPoints(item.usageBasisPoints),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isExceeded) ColorRefinedRed else ColorSageGreen,
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { itemRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (item.isExceeded) ColorRefinedRed else ColorSageGreen,
                                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(
                                            Res.string.report_budget_perf_item_spent,
                                            item.spent.toLocalizedMaskedText(item.maskAmounts || maskAmounts),
                                        ),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(
                                            Res.string.report_budget_perf_item_limit,
                                            item.budget.toLocalizedMaskedText(item.maskAmounts || maskAmounts),
                                        ),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
    monthlyTotal: Money,
    activeSubscriptionCount: Int,
    upcomingSubscriptions: List<SubscriptionReportUiItem>,
    maskAmounts: Boolean = false,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
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
                        contentDescription = stringResource(Res.string.report_action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(Res.string.report_sub_summary_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(Res.string.report_sub_summary_hero_title),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = monthlyTotal.toLocalizedMaskedText(maskAmounts),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = pluralStringResource(
                                        Res.plurals.report_sub_summary_active_count_plural,
                                        activeSubscriptionCount,
                                        activeSubscriptionCount,
                                    ),
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
                        text = stringResource(Res.string.report_sub_summary_upcoming_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (upcomingSubscriptions.isEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            cornerRadius = 14.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.report_sub_summary_empty),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(upcomingSubscriptions) { sub ->
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(
                                            Res.string.report_sub_summary_renewal_date,
                                            sub.renewalDate.toLocalizedReadableDate(),
                                        ),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Text(
                                    text = sub.amount.toLocalizedMaskedText(sub.maskAmounts || maskAmounts),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
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
        color = MaterialTheme.colorScheme.background,
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
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Borç ve Alacak Özeti",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Net Pozisyon (Alacak - Borç)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.errorContainer,
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
                                    color = MaterialTheme.colorScheme.secondaryContainer,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (deadlines.isEmpty()) {
                    item {
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(deadlines) { item ->
                        ReportCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (item.isReceivable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = if (item.isReceivable) ColorSageGreen else MaterialTheme.colorScheme.onSurface,
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
