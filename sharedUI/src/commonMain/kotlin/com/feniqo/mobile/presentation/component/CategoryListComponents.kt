@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoriesSummaryUiModel
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.category.CategoryInsight
import com.feniqo.mobile.presentation.category.CategorySpendingDisplayModel
import com.feniqo.mobile.presentation.category.CategoryTrend
import com.feniqo.mobile.presentation.category.TrendMovement
import com.feniqo.mobile.presentation.category.TrendSentiment
import com.feniqo.mobile.presentation.category.toLocalizedCategoryPeriod
import com.feniqo.mobile.presentation.category.toLocalizedText
import com.feniqo.mobile.presentation.category.toMonthResource
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Adaçayı yeşili warm-luxury tonu */
private val SageGreen = Color(0xFF2D5A43)
private val SageGreenLight = Color(0xFFEAF2EC)
private val WarmIvory = Color(0xFFFAF8F5)
private val SoftPurple = Color(0xFFF3E8FF)
private val PurpleText = Color(0xFF7E22CE)
private val SoftExpenseRed = Color(0xFFFEE2E2)
private val ExpenseRedText = Color(0xFFDC2626)
private val SoftIncomeGreen = Color(0xFFDCFCE7)
private val IncomeGreenText = Color(0xFF16A34A)

/**
 * 2. Dönem seçici bileşeni.
 * Filtrelerin hemen üstünde yer alan, modern ve kompakt tek bir kapsül/surface olarak tasarlanmıştır.
 * Sol ok, ortada seçili ay ve sağ ok aynı kapsül içinde dengeli yerleşir.
 * Gelecek aylarda ileri butonu pasifleşir.
 */
@Composable
fun CategoryPeriodSelector(
    selectedYearMonth: YearMonth,
    isNextMonthEnabled: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPeriodPickerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val localizedPeriod = selectedYearMonth.toLocalizedCategoryPeriod()
    val previousMonthDescription = stringResource(Res.string.categories_previous_month)
    val selectedPeriodDescription = stringResource(Res.string.categories_selected_period_semantics, localizedPeriod)
    val nextMonthDescription = stringResource(
        if (isNextMonthEnabled) Res.string.categories_next_month else Res.string.categories_future_month_unavailable,
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            Color(0xFFF4F2ED)
        },
        border = BorderStroke(
            1.dp,
            if (isDark) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            } else {
                Color(0xFFE5E2DA)
            },
        ),
        shadowElevation = 0.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = previousMonthDescription
                        role = Role.Button
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        onClick = onPeriodPickerClick,
                        role = Role.Button,
                    )
                    .semantics {
                        contentDescription = selectedPeriodDescription
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = localizedPeriod,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onNextMonth,
                enabled = isNextMonthEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = nextMonthDescription
                        role = Role.Button
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isNextMonthEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Ay ve yıl seçimi için modal dialog bileşeni.
 * Gelecek ayların seçilmesini engeller.
 */
@Composable
fun MonthYearPickerDialog(
    selectedYearMonth: YearMonth,
    maxYearMonth: YearMonth,
    onYearMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedParts = selectedYearMonth.value.split("-")
    var currentYear by remember { mutableStateOf(selectedParts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var tempSelectedMonth by remember { mutableStateOf(selectedParts.getOrNull(1)?.toIntOrNull() ?: 9) }

    val maxParts = maxYearMonth.value.split("-")
    val maxYear = maxParts.getOrNull(0)?.toIntOrNull() ?: 2026
    val maxMonth = maxParts.getOrNull(1)?.toIntOrNull() ?: 12

    val months = (1..12).map { month -> month to stringResource(month.toMonthResource()).take(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.categories_period_picker_close),
                        )
                    }
                    Text(
                        text = stringResource(Res.string.categories_period_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Yıl seçici < 2026 >
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { currentYear-- },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.categories_previous_year),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "$currentYear",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = { if (currentYear < maxYear) currentYear++ },
                        enabled = currentYear < maxYear,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.categories_next_year),
                            tint = if (currentYear < maxYear) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in months.chunked(4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for ((mNum, mName) in row) {
                            val isFuture = currentYear > maxYear || (currentYear == maxYear && mNum > maxMonth)
                            val isSelected = tempSelectedMonth == mNum

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSelected -> SageGreen
                                    isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clickable(
                                        enabled = !isFuture,
                                        onClick = {
                                            tempSelectedMonth = mNum
                                        },
                                    ),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Text(
                                        text = mName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetYM = YearMonth("$currentYear-${tempSelectedMonth.toString().padStart(2, '0')}")
                    onYearMonthSelected(targetYM)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(Res.string.categories_apply),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        },
        dismissButton = null,
    )
}

/**
 * 3. Warm-Luxury Özet Kartı.
 * Toplam kategori, özel kategori, en yüksek harcama/gelir ve baz puanlardan üretilmiş gerçek mini bar chart gösterir.
 * Dar ekranlarda güvenli, harf harf bölünmeyen kompakt responsive hiyerarşi sunar.
 */
@Composable
fun CategorySummaryCard(
    summary: CategoriesSummaryUiModel,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else WarmIvory,
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color(0xFFE8E4DD),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val isNarrow = maxWidth < 320.dp
            val hasChartData = summary.miniBarProportionsBasisPoints.isNotEmpty()

            if (isNarrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryStatsRow(
                        totalCount = summary.totalCategoriesCount,
                        customCount = summary.customCategoriesCount,
                        isDark = isDark,
                    )

                    SummaryTopCategorySection(
                        summary = summary,
                        isDark = isDark,
                    )

                    if (hasChartData) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            SummaryMiniBarChart(
                                bars = summary.miniBarProportionsBasisPoints,
                                isDark = isDark,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (hasChartData) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatsRow(
                            totalCount = summary.totalCategoriesCount,
                            customCount = summary.customCategoriesCount,
                            isDark = isDark,
                        )

                        SummaryTopCategorySection(
                            summary = summary,
                            isDark = isDark,
                        )
                    }

                    if (hasChartData) {
                        SummaryMiniBarChart(
                            bars = summary.miniBarProportionsBasisPoints,
                            isDark = isDark,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsRow(
    totalCount: Int,
    customCount: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Toplam kategori kutusu
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isDark) SageGreen.copy(alpha = 0.3f) else SageGreenLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = SageGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = stringResource(Res.string.categories_total_count, totalCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.categories_total_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Özel kategori kutusu
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isDark) SageGreen.copy(alpha = 0.3f) else SageGreenLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = SageGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = stringResource(Res.string.categories_custom_count, customCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.categories_custom_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SummaryTopCategorySection(
    summary: CategoriesSummaryUiModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val isExpense = summary.topCategoryType == TransactionType.EXPENSE
    val iconColor = if (isExpense) ExpenseRedText else IncomeGreenText
    val iconBgColor = if (isExpense) SoftExpenseRed else SoftIncomeGreen

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isDark) iconColor.copy(alpha = 0.25f) else iconBgColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isExpense) Icons.Outlined.ShoppingCart else Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val labelPrefix = stringResource(
                if (isExpense) Res.string.categories_top_expense else Res.string.categories_top_income,
            )
            Text(
                text = labelPrefix,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            if (summary.topCategoryName != null && summary.formattedTopCategoryAmount != null) {
                Text(
                    text = summary.topCategoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val prefix = if (isExpense) "−" else "+"
                Text(
                    text = "$prefix${summary.formattedTopCategoryAmount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpense) ExpenseRedText else IncomeGreenText,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isExpense) Res.string.categories_no_expense else Res.string.categories_no_income,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SummaryMiniBarChart(
    bars: List<Int>,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isDark) MaterialTheme.colorScheme.primary else SageGreen
    val chartDescription = stringResource(Res.string.categories_chart_semantics)
    val singleBar = bars.size == 1
    val barWidth = if (singleBar) 16.dp else 7.dp
    val spacing = 5.dp

    Box(
        modifier = modifier
            .widthIn(min = 68.dp, max = 92.dp)
            .height(56.dp)
            .semantics {
                contentDescription = chartDescription
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { basisPoints ->
                val fraction = (basisPoints / 10_000f).coerceIn(0.15f, 1f)
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height((48.dp * fraction).coerceAtLeast(8.dp))
                        .background(
                            color = barColor.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(
                                topStart = 3.dp,
                                topEnd = 3.dp,
                                bottomStart = 1.dp,
                                bottomEnd = 1.dp,
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * 4. "Kategorilerin" bölüm başlığı bileşeni.
 */
@Composable
fun CategorySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = stringResource(Res.string.categories_total_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 5. Kategori Filtre Kapsülleri (Tümü, Gider, Gelir).
 * Transfer filtresi içermez; proje sözleşmesine göre transfer kategori değildir.
 * Eşit genişlikte 3 filtre ve kompakt ~48dp yükseklik sunar.
 */
@Composable
fun CategoryFilterChips(
    selectedTypeFilter: TransactionType?,
    onFilterSelected: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val allDescription = stringResource(Res.string.categories_filter_all_semantics)
    val expenseDescription = stringResource(Res.string.categories_filter_expense_semantics)
    val incomeDescription = stringResource(Res.string.categories_filter_income_semantics)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // Tümü
        FilterChip(
            selected = selectedTypeFilter == null,
            onClick = { onFilterSelected(null) },
            label = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.categories_filter_all),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTypeFilter == null) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .semantics {
                    contentDescription = allDescription
                },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                labelColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF374151),
                selectedContainerColor = SageGreen,
                selectedLabelColor = Color.White,
            ),
            border = BorderStroke(
                1.dp,
                if (selectedTypeFilter == null) {
                    SageGreen
                } else if (isDark) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                } else {
                    Color(0xFFE2E8F0)
                },
            ),
        )

        // Gider
        FilterChip(
            selected = selectedTypeFilter == TransactionType.EXPENSE,
            onClick = { onFilterSelected(TransactionType.EXPENSE) },
            label = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.categories_filter_expense),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTypeFilter == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .semantics {
                    contentDescription = expenseDescription
                },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                labelColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF374151),
                selectedContainerColor = SageGreen,
                selectedLabelColor = Color.White,
            ),
            border = BorderStroke(
                1.dp,
                if (selectedTypeFilter == TransactionType.EXPENSE) {
                    SageGreen
                } else if (isDark) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                } else {
                    Color(0xFFE2E8F0)
                },
            ),
        )

        // Gelir
        FilterChip(
            selected = selectedTypeFilter == TransactionType.INCOME,
            onClick = { onFilterSelected(TransactionType.INCOME) },
            label = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.categories_filter_income),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTypeFilter == TransactionType.INCOME) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .semantics {
                    contentDescription = incomeDescription
                },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                labelColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF374151),
                selectedContainerColor = SageGreen,
                selectedLabelColor = Color.White,
            ),
            border = BorderStroke(
                1.dp,
                if (selectedTypeFilter == TransactionType.INCOME) {
                    SageGreen
                } else if (isDark) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                } else {
                    Color(0xFFE2E8F0)
                },
            ),
        )
    }
}

/**
 * Gelir ve Gider türü arasında geçiş yapılmasını sağlayan seçici bileşendir (Formlar için).
 */
@Composable
fun CategoryTypeSelector(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val expenseDescription = stringResource(Res.string.categories_filter_expense_semantics)
    val incomeDescription = stringResource(Res.string.categories_filter_income_semantics)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        FilterChip(
            selected = selectedType == TransactionType.EXPENSE,
            onClick = { onTypeSelected(TransactionType.EXPENSE) },
            label = {
                Text(
                    text = stringResource(Res.string.categories_filter_expense),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = expenseDescription
                },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        FilterChip(
            selected = selectedType == TransactionType.INCOME,
            onClick = { onTypeSelected(TransactionType.INCOME) },
            label = {
                Text(
                    text = stringResource(Res.string.categories_filter_income),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.INCOME) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = incomeDescription
                },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}

/**
 * 6. Kategori Analiz Satırı.
 * Semantik ikon (44dp), kategori adı, işlem adedi, formatlanmış tutar, kompakt trend rozeti ve erişilebilir yönetim sunar.
 * Kart yüksekliği yaklaşık 56–60dp seviyesinde sıkılaştırılmıştır.
 */
@Composable
fun CategoryAnalyticsListItem(
    item: CategorySpendingDisplayModel,
    onClick: () -> Unit,
    onEditClick: (EntityId) -> Unit,
    onDeleteClick: (CategoryDisplayModel) -> Unit,
    modifier: Modifier = Modifier,
    isActionsEnabled: Boolean = true,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var isMenuExpanded by remember { mutableStateOf(false) }
    val rowDescription = stringResource(
        Res.string.categories_row_semantics,
        item.category.name,
        item.transactionCount,
        item.formattedCurrentAmount,
    )
    val actionsDescription = stringResource(Res.string.categories_actions_semantics, item.category.name)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                contentDescription = rowDescription
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val categoryColor = ColorParser.parseHexColorOrNull(item.category.colorHex)
                ?: MaterialTheme.colorScheme.primary

            CategoryTonalIcon(
                iconKey = item.category.iconKey,
                color = categoryColor,
                containerSize = 44.dp,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Özel kategori rozeti (Görsel 01 & 07)
                    if (!item.category.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDark) PurpleText.copy(alpha = 0.25f) else SoftPurple,
                        ) {
                            Text(
                                text = stringResource(Res.string.categories_custom_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFD8B4FE) else PurpleText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.categories_transaction_count, item.transactionCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tutar ve Trend rozeti
            val isZero = item.currentPeriodAmount.amountMinor == 0L
            val isExpense = item.category.type == TransactionType.EXPENSE
            val amountColor = when {
                isZero -> MaterialTheme.colorScheme.onSurfaceVariant
                isExpense -> ExpenseRedText
                else -> IncomeGreenText
            }
            val amountPrefix = when {
                isZero -> ""
                isExpense -> "−"
                else -> "+"
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "$amountPrefix${item.formattedCurrentAmount}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                )

                when (val trend = item.trend) {
                    is CategoryTrend.New -> {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else SageGreenLight,
                            modifier = Modifier.padding(top = 1.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.categories_trend_new),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else SageGreen,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    is CategoryTrend.Changed -> {
                        val pct = trend.changeBasisPoints / 100
                        val arrow = if (trend.movement == TrendMovement.INCREASED) "↑" else "↓"
                        val trendColor = when (trend.sentiment) {
                            TrendSentiment.POSITIVE -> FeniqoStatusColor.Success
                            TrendSentiment.NEGATIVE -> FeniqoStatusColor.Error
                            TrendSentiment.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = "$arrow %$pct",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = trendColor,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    is CategoryTrend.None -> {
                        // Trend rozeti yok
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Sağ aksiyon: Özel kategoriler için üç nokta menüsü, sistem kategorileri için chevron ok
            if (item.category.isDefault) {
                Box(
                    modifier = Modifier.size(width = 24.dp, height = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        enabled = isActionsEnabled,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = actionsDescription
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // 07 Özel Kategori Menüsü
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(stringResource(Res.string.categories_edit))
                                }
                            },
                            onClick = {
                                isMenuExpanded = false
                                onEditClick(item.category.id)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(Res.string.categories_delete),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            },
                            onClick = {
                                isMenuExpanded = false
                                onDeleteClick(item.category)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. Feniqo İçgörü Kartı.
 * Gerçek işlem verisinden türetilen dinamik finansal içgörüyü adaçayı yeşili zeminle sunar.
 */
@Composable
fun CategoryInsightCard(
    insight: CategoryInsight,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else SageGreenLight,
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else Color(0xFFD3E4D7),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = if (isDark) MaterialTheme.colorScheme.primary else SageGreen,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.categories_insight_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.primary else SageGreen,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.toLocalizedText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF2E382E),
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * 8. Yeni Kategori Oluştur Aksiyon Kartı.
 */
@Composable
fun CreateCategoryActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createDescription = stringResource(Res.string.categories_create_semantics)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                contentDescription = createDescription
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = SageGreen, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.categories_create_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.categories_create_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Genel bilgi/hata mesajlarını gösteren satır içi banner bileşenidir.
 */
@Composable
fun CategoryMessageBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissEnabled: Boolean = true,
) {
    val isError = message.isError
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val closeDescription = stringResource(Res.string.categories_message_close_semantics)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message.toLocalizedText(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = onDismiss,
                enabled = isDismissEnabled,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = closeDescription
                    },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = contentColor,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.categories_close),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 8. Silme Onayı Diyaloğu (Görsel 08).
 */
@Composable
fun CategoryDeleteDialog(
    targetCategory: CategoryDisplayModel?,
    isDeleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (targetCategory == null) return

    AlertDialog(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Kırmızı çöp kutusu ikonu (Görsel 08)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(SoftExpenseRed, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ExpenseRedText,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Text(
                    text = stringResource(Res.string.categories_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Text(
                text = stringResource(Res.string.categories_delete_body, targetCategory.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = !isDeleteInProgress && !targetCategory.isDefault,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRedText,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isDeleteInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.categories_deleting),
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.categories_delete_confirm),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isDeleteInProgress,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.categories_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

/**
 * 10. Boş Filtre Durumu Kartı (Görsel 10).
 * Seçili filtreye uygun kategori bulunamadığında kullanıcıyı yönlendirir.
 */
@Composable
fun CategoryEmptyFilterCard(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFF9F8F6),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else Color(0xFFE8E5DD)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFEAF2EC),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalOffer,
                    contentDescription = null,
                    tint = SageGreen,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.categories_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.categories_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(Res.string.categories_create_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
