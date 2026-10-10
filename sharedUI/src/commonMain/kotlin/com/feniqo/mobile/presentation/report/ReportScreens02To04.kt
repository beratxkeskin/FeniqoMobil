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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_action_all_categories
import feniqomobil.sharedui.generated.resources.report_action_back
import feniqomobil.sharedui.generated.resources.report_action_compare_period
import feniqomobil.sharedui.generated.resources.report_category_breakdown_title
import feniqomobil.sharedui.generated.resources.report_category_deleted
import feniqomobil.sharedui.generated.resources.report_hub_title
import feniqomobil.sharedui.generated.resources.report_indicator_daily_avg_sub
import feniqomobil.sharedui.generated.resources.report_indicator_daily_avg_title
import feniqomobil.sharedui.generated.resources.report_indicator_savings_rate_sub
import feniqomobil.sharedui.generated.resources.report_indicator_tx_count_sub
import feniqomobil.sharedui.generated.resources.report_indicator_tx_count_title
import feniqomobil.sharedui.generated.resources.report_metric_expense
import feniqomobil.sharedui.generated.resources.report_metric_income
import feniqomobil.sharedui.generated.resources.report_metric_net
import feniqomobil.sharedui.generated.resources.report_metric_savings_rate
import feniqomobil.sharedui.generated.resources.report_nav_next_month
import feniqomobil.sharedui.generated.resources.report_nav_previous_month
import feniqomobil.sharedui.generated.resources.report_period_summary_key_indicators
import feniqomobil.sharedui.generated.resources.report_period_summary_title
import feniqomobil.sharedui.generated.resources.report_period_summary_top_categories
import feniqomobil.sharedui.generated.resources.report_period_summary_weekly_title
import feniqomobil.sharedui.generated.resources.report_rhythm_busiest_day_sub
import feniqomobil.sharedui.generated.resources.report_rhythm_busiest_day_title
import feniqomobil.sharedui.generated.resources.report_rhythm_lowest_week_sub
import feniqomobil.sharedui.generated.resources.report_rhythm_lowest_week_title
import feniqomobil.sharedui.generated.resources.report_rhythm_week_format
import org.jetbrains.compose.resources.stringResource

/**
 * 02 ve 03 numaralı onaylı tasarım ekranları: Dönem özeti ve kaydırılmış içerik.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSummaryScreen(
    currentMonth: YearMonth,
    income: Money,
    expense: Money,
    net: MoneyDelta,
    isNetPositive: Boolean = net.amountMinor >= 0L,
    savingsRateBasisPoints: Int,
    transactionCount: Int,
    dailyAverageExpense: Money,
    weeklyPoints: List<WeeklyDualBarUiPoint> = emptyList(),
    topCategories: List<CategoryBreakdownUiItem> = emptyList(),
    financialRhythm: FinancialRhythmUiModel? = null,
    insightPayload: ReportInsightPayload = ReportInsightPayload.None,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigateToCategoryBreakdown: () -> Unit,
    onNavigateToPeriodComparison: () -> Unit,
    onNavigateBack: () -> Unit,
    maskAmounts: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                        contentDescription = stringResource(Res.string.report_action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(Res.string.report_period_summary_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { innerPadding ->
        val localizedInsight = insightPayload.toLocalizedText()

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
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = stringResource(Res.string.report_nav_previous_month),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = currentMonth.toLocalizedMonthYear(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onNextMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(Res.string.report_nav_next_month),
                                tint = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(text = stringResource(Res.string.report_metric_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = income.toLocalizedMaskedText(maskAmounts), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text(text = stringResource(Res.string.report_metric_expense), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = expense.toLocalizedMaskedText(maskAmounts), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                        }
                        Column {
                            Text(text = stringResource(Res.string.report_metric_net), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = net.toLocalizedMaskedText(maskAmounts), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (isNetPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Haftalık Gelir ve Gider Bar Grafiği
            item("weekly_chart") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = stringResource(Res.string.report_period_summary_weekly_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Text(text = stringResource(Res.string.report_metric_income), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFDC2626)))
                                    Text(text = stringResource(Res.string.report_metric_expense), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        WeeklyDualBarChart(weeklyPoints = weeklyPoints)
                    }
                }
            }

            // Temel Göstergeler
            item("key_indicators") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = stringResource(Res.string.report_period_summary_key_indicators), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)

                        IndicatorRow(
                            iconVector = Icons.Outlined.Percent,
                            title = stringResource(Res.string.report_metric_savings_rate),
                            subtitle = stringResource(Res.string.report_indicator_savings_rate_sub),
                            value = formatLocalizedRateBasisPoints(savingsRateBasisPoints),
                        )
                        IndicatorRow(
                            iconVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = stringResource(Res.string.report_indicator_tx_count_title),
                            subtitle = stringResource(Res.string.report_indicator_tx_count_sub),
                            value = transactionCount.toString(),
                        )
                        IndicatorRow(
                            iconVector = Icons.Outlined.CalendarToday,
                            title = stringResource(Res.string.report_indicator_daily_avg_title),
                            subtitle = stringResource(Res.string.report_indicator_daily_avg_sub),
                            value = dailyAverageExpense.toLocalizedMaskedText(maskAmounts),
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
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(Res.string.report_period_summary_top_categories), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)

                            topCategories.take(3).forEachIndexed { index, item ->
                                val categoryDisplayName = if (item.isCategoryMissing || item.name == null) {
                                    stringResource(Res.string.report_category_deleted)
                                } else {
                                    item.name
                                }
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
                                                    Text(text = (index + 1).toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                            Text(text = categoryDisplayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(text = item.amount.toLocalizedMaskedText(maskAmounts || item.maskAmounts), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = formatLocalizedRateBasisPoints(item.shareBasisPoints), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (topCategories.isNotEmpty()) {
                item("donut_section") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(Res.string.report_category_breakdown_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val colors = listOf(Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF1E88E5), Color(0xFF00897B))
                                ReportDonutChart(slices = topCategories, colors = colors, centerText = expense.toLocalizedMaskedText(maskAmounts))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    topCategories.take(5).forEachIndexed { i, item ->
                                        val sliceName = if (item.isCategoryMissing || item.name == null) {
                                            stringResource(Res.string.report_category_deleted)
                                        } else {
                                            item.name
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.getOrElse(i) { Color(0xFF2D5A43) }))
                                            Text(
                                                text = sliceName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.width(75.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(text = formatLocalizedRateBasisPoints(item.shareBasisPoints), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(text = stringResource(Res.string.report_rhythm_busiest_day_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = financialRhythm.busiestDay?.toLocalizedDayName().orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text(text = stringResource(Res.string.report_rhythm_busiest_day_sub), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.BarChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(text = stringResource(Res.string.report_rhythm_lowest_week_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = stringResource(Res.string.report_rhythm_week_format, financialRhythm.lowestExpenseWeekNumber), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text(text = stringResource(Res.string.report_rhythm_lowest_week_sub), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Feniqo İçgörü Kartı (Ekran 03)
            if (localizedInsight.isNotBlank()) {
                item("feniqo_insight") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(text = localizedInsight, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.PieChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(text = stringResource(Res.string.report_action_all_categories), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onNavigateToPeriodComparison),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.CompareArrows,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(text = stringResource(Res.string.report_action_compare_period), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
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
        containerColor = MaterialTheme.colorScheme.background,
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
                        contentDescription = stringResource(Res.string.report_action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(Res.string.report_hub_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
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
                val hubCards = listOf(
                    Triple(AllReportsHubItem.CATEGORY_BREAKDOWN, Icons.Outlined.PieChart, onNavigateToCategoryBreakdown),
                    Triple(AllReportsHubItem.CASH_FLOW, Icons.Outlined.BarChart, onNavigateToCashFlow),
                    Triple(AllReportsHubItem.PERIOD_COMPARISON, Icons.Outlined.CompareArrows, onNavigateToPeriodComparison),
                    Triple(AllReportsHubItem.SPENDING_CALENDAR, Icons.Outlined.CalendarMonth, onNavigateToSpendingCalendar),
                    Triple(AllReportsHubItem.BUDGET_PERFORMANCE, Icons.Outlined.TrackChanges, onNavigateToBudgetPerformance),
                    Triple(AllReportsHubItem.SUBSCRIPTION_SUMMARY, Icons.Outlined.Autorenew, onNavigateToSubscriptionSummary),
                    Triple(AllReportsHubItem.DEBT_SUMMARY, Icons.AutoMirrored.Outlined.ReceiptLong, onNavigateToDebtSummary),
                    Triple(AllReportsHubItem.FORECAST, Icons.Outlined.AutoGraph, onNavigateToForecast),
                    Triple(AllReportsHubItem.FINANCIAL_INSIGHTS, Icons.Outlined.Lightbulb, onNavigateToFinancialInsights),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    hubCards.forEach { (item, icon, onClick) ->
                        ReportHubCard(
                            icon = icon,
                            title = stringResource(item.toLocalizedTitleRes()),
                            subtitle = stringResource(item.toLocalizedSubtitleRes()),
                            onClick = onClick,
                        )
                    }
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
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
