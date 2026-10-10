package com.feniqo.mobile.presentation.report

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
import androidx.compose.material3.MaterialTheme
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
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.localizedMonthName
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_action_back
import feniqomobil.sharedui.generated.resources.report_category_deleted
import feniqomobil.sharedui.generated.resources.report_forecast_action_view_detail
import feniqomobil.sharedui.generated.resources.report_forecast_active_recurring_plural
import feniqomobil.sharedui.generated.resources.report_forecast_assumption_actual_vs_expected
import feniqomobil.sharedui.generated.resources.report_forecast_assumption_daily_avg
import feniqomobil.sharedui.generated.resources.report_forecast_assumptions_title
import feniqomobil.sharedui.generated.resources.report_forecast_badge
import feniqomobil.sharedui.generated.resources.report_forecast_detail_title
import feniqomobil.sharedui.generated.resources.report_forecast_disclaimer
import feniqomobil.sharedui.generated.resources.report_forecast_expected_income
import feniqomobil.sharedui.generated.resources.report_forecast_fixed_expense
import feniqomobil.sharedui.generated.resources.report_forecast_hero_title
import feniqomobil.sharedui.generated.resources.report_forecast_month_point_label
import feniqomobil.sharedui.generated.resources.report_forecast_monthly_projection_title
import feniqomobil.sharedui.generated.resources.report_forecast_overview_title
import feniqomobil.sharedui.generated.resources.report_forecast_point_flow
import feniqomobil.sharedui.generated.resources.report_forecast_sources_empty
import feniqomobil.sharedui.generated.resources.report_forecast_sources_title
import feniqomobil.sharedui.generated.resources.report_forecast_variable_expense
import feniqomobil.sharedui.generated.resources.report_insights_analysis_tx_count_plural
import feniqomobil.sharedui.generated.resources.report_insights_change_rate
import feniqomobil.sharedui.generated.resources.report_insights_contributing_transactions_title
import feniqomobil.sharedui.generated.resources.report_insights_detail_screen_title
import feniqomobil.sharedui.generated.resources.report_insights_empty
import feniqomobil.sharedui.generated.resources.report_insights_header_subtitle
import feniqomobil.sharedui.generated.resources.report_insights_how_calculated
import feniqomobil.sharedui.generated.resources.report_insights_no_transactions
import feniqomobil.sharedui.generated.resources.report_insights_screen_title
import feniqomobil.sharedui.generated.resources.report_insights_total_amount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Tasarım Sistemi Renkleri
private val ColorSageGreen = Color(0xFF2D5A43)
private val ColorRefinedRed = Color(0xFFC04D43)

// ==========================================
// EKRAN 17: GELECEK AY TAHMİNİ
// ==========================================
@Composable
fun ForecastOverviewReportScreen(
    forecastMonth: YearMonth,
    projectedNet: MoneyDelta,
    isProjectedNetPositive: Boolean,
    projectedIncome: Money,
    fixedExpense: Money,
    variableExpense: Money,
    sources: List<ForecastSourceUiItem>,
    maskAmounts: Boolean = false,
    onViewDetailClick: () -> Unit,
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
                        text = stringResource(Res.string.report_forecast_overview_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
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
                            text = stringResource(Res.string.report_forecast_badge),
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
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(
                                    Res.string.report_forecast_hero_title,
                                    localizedMonthName(forecastMonth.monthNumber),
                                ),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = projectedNet.toLocalizedMaskedText(maskAmounts, showPositiveSign = true),
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
                                    Text(
                                        text = stringResource(Res.string.report_forecast_expected_income),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = projectedIncome.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSageGreen,
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(Res.string.report_forecast_fixed_expense),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = fixedExpense.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(Res.string.report_forecast_variable_expense),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = variableExpense.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorRefinedRed,
                                    )
                                }
                            }
                        }
                    }
                }

                // Tahmini Oluşturan Kaynaklar Başlığı
                item {
                    Text(
                        text = stringResource(Res.string.report_forecast_sources_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (sources.isEmpty()) {
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
                                    text = stringResource(Res.string.report_forecast_sources_empty),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(sources) { source ->
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
                                        text = source.type.toLocalizedTitle(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = source.amount.toLocalizedMaskedText(maskAmounts),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (source.percentageBasisPoints / 10000f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = ColorSageGreen,
                                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                                )
                            }
                        }
                    }
                }

                // Bilgilendirme ve Güvenlik Feragati
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(Res.string.report_forecast_disclaimer),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                text = stringResource(Res.string.report_forecast_action_view_detail),
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
    projectionPoints: List<ForecastProjectionPoint>,
    dailyAverage: Money,
    activeRecurringCount: Int,
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
                    text = stringResource(Res.string.report_forecast_detail_title),
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
                // Model Varsayımları Kartı
                item {
                    ReportCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        cornerRadius = 20.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(Res.string.report_forecast_assumptions_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(
                                    Res.string.report_forecast_assumption_daily_avg,
                                    dailyAverage.toLocalizedMaskedText(maskAmounts),
                                ),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.report_forecast_active_recurring_plural,
                                    activeRecurringCount,
                                    activeRecurringCount,
                                ),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(Res.string.report_forecast_assumption_actual_vs_expected),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Projeksiyon Noktaları Listesi
                item {
                    Text(
                        text = stringResource(Res.string.report_forecast_monthly_projection_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(projectionPoints) { point ->
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(
                                            Res.string.report_forecast_month_point_label,
                                            localizedMonthName(point.yearMonth.monthNumber),
                                            point.yearMonth.year,
                                        ),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (point.isForecast) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.report_forecast_badge),
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
                                    text = stringResource(
                                        Res.string.report_forecast_point_flow,
                                        point.income.toLocalizedMaskedText(maskAmounts),
                                        point.expense.toLocalizedMaskedText(maskAmounts),
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val netDelta = MoneyDelta.between(point.income, point.expense)
                            Text(
                                text = netDelta.toLocalizedMaskedText(maskAmounts, showPositiveSign = true),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netDelta.amountMinor >= 0L) ColorSageGreen else ColorRefinedRed,
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
    maskAmounts: Boolean = false,
    onInsightClick: (FinancialInsightUiItem) -> Unit,
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
                    text = stringResource(Res.string.report_insights_screen_title),
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
                item {
                    Text(
                        text = stringResource(Res.string.report_insights_header_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (insights.isEmpty()) {
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
                                    text = stringResource(Res.string.report_insights_empty),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(insights) { item ->
                        ReportCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInsightClick(item) },
                            backgroundColor = MaterialTheme.colorScheme.surface,
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
                                            color = if (item.isPositive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
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
                                                text = item.resolveLocalizedTitle(),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = item.resolveLocalizedSubtitle(),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = item.currentPeriodAmount.toLocalizedMaskedText(item.maskAmounts || maskAmounts),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = item.delta.toLocalizedMaskedText(item.maskAmounts || maskAmounts, showPositiveSign = true),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isPositive) ColorSageGreen else ColorRefinedRed,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = item.resolveLocalizedExplanation(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp,
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
// EKRAN 20: İÇGÖRÜ DETAYI
// ==========================================
@Composable
fun InsightDetailReportScreen(
    insight: FinancialInsightUiItem,
    transactions: List<ReportTransactionUiItem> = insight.contributingTransactions,
    maskAmounts: Boolean = false,
    onViewTransactionsClick: () -> Unit = {},
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
                    text = stringResource(Res.string.report_insights_detail_screen_title),
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
                                text = insight.resolveLocalizedTitle(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = insight.resolveLocalizedSubtitle(),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(Res.string.report_insights_total_amount),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = insight.currentPeriodAmount.toLocalizedMaskedText(insight.maskAmounts || maskAmounts),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(Res.string.report_insights_change_rate),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = insight.delta.toLocalizedMaskedText(insight.maskAmounts || maskAmounts, showPositiveSign = true),
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
                        backgroundColor = MaterialTheme.colorScheme.surface,
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
                                    text = stringResource(Res.string.report_insights_how_calculated),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = insight.resolveLocalizedExplanation(),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.report_insights_analysis_tx_count_plural,
                                    insight.transactionCount,
                                    insight.transactionCount,
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (transactions.isEmpty()) {
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
                                    text = stringResource(Res.string.report_insights_no_transactions),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(Res.string.report_insights_contributing_transactions_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    items(transactions) { tx ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.resolveLocalizedTitle(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val catLabel = if (tx.isCategoryMissing) {
                                        stringResource(Res.string.report_category_deleted)
                                    } else {
                                        tx.categoryName
                                    }
                                    if (!catLabel.isNullOrBlank()) {
                                        Text(
                                            text = catLabel,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = tx.date.toLocalizedReadableDate(),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val isExpense = tx.type == TransactionType.EXPENSE
                                Text(
                                    text = (if (isExpense) "-" else "+") + tx.amount.toLocalizedMaskedText(tx.maskAmounts || maskAmounts),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpense) MaterialTheme.colorScheme.onSurface else ColorSageGreen,
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
