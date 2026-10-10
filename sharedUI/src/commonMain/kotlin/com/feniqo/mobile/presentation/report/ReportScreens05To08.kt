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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_action_back
import feniqomobil.sharedui.generated.resources.report_category_breakdown_empty
import feniqomobil.sharedui.generated.resources.report_category_breakdown_title
import feniqomobil.sharedui.generated.resources.report_category_breakdown_total_expense
import feniqomobil.sharedui.generated.resources.report_category_breakdown_total_income
import feniqomobil.sharedui.generated.resources.report_category_comparison_calculating
import feniqomobil.sharedui.generated.resources.report_category_comparison_decreased
import feniqomobil.sharedui.generated.resources.report_category_comparison_increased
import feniqomobil.sharedui.generated.resources.report_category_deleted
import feniqomobil.sharedui.generated.resources.report_category_detail_merchant_title
import feniqomobil.sharedui.generated.resources.report_category_detail_period_share
import feniqomobil.sharedui.generated.resources.report_category_detail_subtitle
import feniqomobil.sharedui.generated.resources.report_category_detail_total_spending
import feniqomobil.sharedui.generated.resources.report_category_detail_view_transactions
import feniqomobil.sharedui.generated.resources.report_category_detail_weekly_trend
import feniqomobil.sharedui.generated.resources.report_category_transaction_count_plural
import feniqomobil.sharedui.generated.resources.report_filter_type_expense
import feniqomobil.sharedui.generated.resources.report_filter_type_income
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 05 ve 08 Numaralı onaylı tasarım ekranları: Kategori dağılımı (Gider ve Gelir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBreakdownReportScreen(
    periodPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    customDateRange: ReportDateRange? = null,
    totalExpense: Money,
    totalIncome: Money,
    expenseTransactionCount: Int,
    incomeTransactionCount: Int,
    expenseCategories: List<CategoryBreakdownUiItem>,
    incomeCategories: List<CategoryBreakdownUiItem>,
    onSelectCategory: (EntityId?, String) -> Unit,
    onChangePeriod: () -> Unit,
    onNavigateBack: () -> Unit,
    maskAmounts: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Gider, 1: Gelir
    val isExpense = selectedTab == 0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
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
                        text = stringResource(Res.string.report_category_breakdown_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Dönem Seçici Çipi
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChangePeriod() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatLocalizedPeriod(periodPreset, customDateRange),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Gider / Gelir Sekmesi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(3.dp),
                ) {
                    val activeBg = Color(0xFF2D5A43)
                    val activeText = Color.White
                    val inactiveText = Color(0xFF6B7280)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isExpense) activeBg else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.report_filter_type_expense),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isExpense) activeText else inactiveText,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isExpense) activeBg else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.report_filter_type_income),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (!isExpense) activeText else inactiveText,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        },
    ) { innerPadding ->
        val currentCategories = if (isExpense) expenseCategories else incomeCategories
        val totalAmount = if (isExpense) totalExpense else totalIncome
        val txCount = if (isExpense) expenseTransactionCount else incomeTransactionCount

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Dark Hero Kartı (Donut chart ile)
            item("hero_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF273130),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = if (isExpense) stringResource(Res.string.report_category_breakdown_total_expense) else stringResource(Res.string.report_category_breakdown_total_income),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = totalAmount.toLocalizedMaskedText(maskAmounts),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                ),
                                color = if (isExpense) Color(0xFFF87171) else Color(0xFF4ADE80),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pluralStringResource(Res.plurals.report_category_transaction_count_plural, txCount, txCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                            )
                        }

                        val colors = if (isExpense) {
                            listOf(Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF1E88E5), Color(0xFF00897B))
                        } else {
                            listOf(Color(0xFF2D5A43), Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7))
                        }

                        ReportDonutChart(
                            slices = currentCategories,
                            colors = colors,
                            centerText = pluralStringResource(Res.plurals.report_category_transaction_count_plural, txCount, txCount),
                            modifier = Modifier.size(100.dp),
                        )
                    }
                }
            }

            if (currentCategories.isEmpty()) {
                item("empty_categories") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = stringResource(Res.string.report_category_breakdown_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                // Kategori Listesi
                items(currentCategories) { item ->
                    val displayName = if (item.isCategoryMissing || item.name == null) {
                        stringResource(Res.string.report_category_deleted)
                    } else {
                        item.name
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onSelectCategory(item.categoryId, item.name.orEmpty()) },
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CategoryTonalIcon(
                                    iconKey = item.name.orEmpty(),
                                    color = if (isExpense) Color(0xFFDC2626) else Color(0xFF16A34A),
                                    containerSize = 40.dp,
                                )

                                Column {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${pluralStringResource(Res.plurals.report_category_transaction_count_plural, item.transactionCount, item.transactionCount)} • ${formatLocalizedRateBasisPoints(item.shareBasisPoints)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = item.amount.toLocalizedMaskedText(maskAmounts || item.maskAmounts),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    ),
                                    color = if (isExpense) Color(0xFFDC2626) else Color(0xFF2D5A43),
                                )
                                Text(text = "›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * 06 ve 07 Numaralı onaylı tasarım ekranları: Kategori Detayı ve Kategori İşlemleri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailReportScreen(
    categoryName: String?,
    isCategoryMissing: Boolean = false,
    periodPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    customDateRange: ReportDateRange? = null,
    totalSpending: Money,
    transactionCount: Int,
    periodShareBasisPoints: Int,
    weeklyTrend: List<WeeklyDualBarUiPoint> = emptyList(),
    comparisonState: CategoryDetailComparisonUiState = CategoryDetailComparisonUiState.Unavailable,
    merchantBreakdown: List<MerchantBreakdownUiItem> = emptyList(),
    transactions: List<Transaction> = emptyList(),
    onNavigateToTransactions: () -> Unit,
    onNavigateBack: () -> Unit,
    maskAmounts: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val displayName = if (isCategoryMissing || categoryName == null) {
        stringResource(Res.string.report_category_deleted)
    } else {
        categoryName
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.report_action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.report_category_detail_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onNavigateToTransactions,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D5A43)),
                    ) {
                        Text(
                            text = stringResource(Res.string.report_category_detail_view_transactions),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                    }
                }
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
            // Dark Hero Kartı
            item("hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF273130),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(Res.string.report_category_detail_total_spending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = totalSpending.toLocalizedMaskedText(maskAmounts),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                            color = Color(0xFFF87171),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val txText = pluralStringResource(Res.plurals.report_category_transaction_count_plural, transactionCount, transactionCount)
                        val shareText = stringResource(Res.string.report_category_detail_period_share, formatLocalizedRateBasisPoints(periodShareBasisPoints))
                        Text(
                            text = "$txText • $shareText",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                }
            }

            // Haftalık Harcama Trendi Bar Grafiği
            item("weekly_chart") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.report_category_detail_weekly_trend),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        WeeklyDualBarChart(weeklyPoints = weeklyTrend)
                    }
                }
            }

            // Geçen Aya Göre Kıyas Kartı
            when (comparisonState) {
                CategoryDetailComparisonUiState.Calculating -> {
                    item("comparison_calculating") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(Res.string.report_category_comparison_calculating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                is CategoryDetailComparisonUiState.Available -> {
                    item("comparison_available") {
                        val isDecreased = comparisonState.isDecreased
                        val absPct = kotlin.math.abs(comparisonState.changePercentageBasisPoints)
                        val comparisonText = if (isDecreased) {
                            stringResource(Res.string.report_category_comparison_decreased, formatLocalizedRateBasisPoints(absPct))
                        } else {
                            stringResource(Res.string.report_category_comparison_increased, formatLocalizedRateBasisPoints(absPct))
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDecreased) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isDecreased) Color(0xFF2D5A43) else Color(0xFFDC2626),
                                )
                                Text(
                                    text = comparisonText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                CategoryDetailComparisonUiState.Unavailable -> {
                    // Veri henüz yoksa kart gösterilmez
                }
            }

            // İş Yeri Kırılımı (Merchant breakdown)
            if (merchantBreakdown.isNotEmpty()) {
                item("merchant_title") {
                    Text(
                        text = stringResource(Res.string.report_category_detail_merchant_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                items(merchantBreakdown) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Storefront,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Column {
                                    Text(text = item.merchantName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        text = "${pluralStringResource(Res.plurals.report_category_transaction_count_plural, item.transactionCount, item.transactionCount)} • ${formatLocalizedRateBasisPoints(item.shareBasisPoints)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = item.amount.toLocalizedMaskedText(maskAmounts || item.maskAmounts),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFDC2626),
                            )
                        }
                    }
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
