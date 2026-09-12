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
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoriesSummaryUiModel
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.category.CategorySpendingDisplayModel
import com.feniqo.mobile.presentation.category.CategoryTrend
import com.feniqo.mobile.presentation.category.TrendMovement
import com.feniqo.mobile.presentation.category.TrendSentiment
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.util.ColorParser

/** Adaçayı yeşili warm-luxury tonu */
private val SageGreen = Color(0xFF2D5A43)
private val SageGreenLight = Color(0xFFEAF2EC)
private val WarmIvory = Color(0xFFFAF8F5)

/**
 * Türkçe ay ve yıl formatlayıcısı.
 */
fun formatYearMonthTurkish(yearMonth: YearMonth): String {
    val parts = yearMonth.value.split("-")
    if (parts.size != 2) return yearMonth.value
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return yearMonth.value
    val monthName = when (month) {
        1 -> "Ocak"
        2 -> "Şubat"
        3 -> "Mart"
        4 -> "Nisan"
        5 -> "Mayıs"
        6 -> "Haziran"
        7 -> "Temmuz"
        8 -> "Ağustos"
        9 -> "Eylül"
        10 -> "Ekim"
        11 -> "Kasım"
        12 -> "Aralık"
        else -> ""
    }
    return "$monthName $year"
}

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
                        contentDescription = "Önceki aya git"
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
                        contentDescription = "Seçili dönem: ${formatYearMonthTurkish(selectedYearMonth)}. Ay değiştirmek için tıklayın."
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = formatYearMonthTurkish(selectedYearMonth),
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
                        contentDescription = if (isNextMonthEnabled) "Sonraki aya git" else "Gelecek aylar seçilemez"
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
    val maxParts = maxYearMonth.value.split("-")
    val maxYear = maxParts.getOrNull(0)?.toIntOrNull() ?: 2026
    val maxMonth = maxParts.getOrNull(1)?.toIntOrNull() ?: 12

    val months = listOf(
        1 to "Ocak", 2 to "Şubat", 3 to "Mart", 4 to "Nisan",
        5 to "Mayıs", 6 to "Haziran", 7 to "Temmuz", 8 to "Ağustos",
        9 to "Eylül", 10 to "Ekim", 11 to "Kasım", 12 to "Aralık"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { currentYear-- },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki yıl")
                }
                Text(
                    text = "$currentYear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { if (currentYear < maxYear) currentYear++ },
                    enabled = currentYear < maxYear,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Sonraki yıl",
                        tint = if (currentYear < maxYear) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                for (row in months.chunked(3)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        for ((mNum, mName) in row) {
                            val isFuture = currentYear > maxYear || (currentYear == maxYear && mNum > maxMonth)
                            val isSelected = selectedParts.getOrNull(0)?.toIntOrNull() == currentYear &&
                                    selectedParts.getOrNull(1)?.toIntOrNull() == mNum

                            val targetYM = YearMonth("$currentYear-${mNum.toString().padStart(2, '0')}")

                            Surface(
                                shape = RoundedCornerShape(FeniqoRadius.Small),
                                color = when {
                                    isSelected -> SageGreen
                                    isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clickable(
                                        enabled = !isFuture,
                                        onClick = {
                                            onYearMonthSelected(targetYM)
                                        },
                                    ),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                ) {
                                    Text(
                                        text = mName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = "Kapat")
            }
        },
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "$totalCount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
            )
            Text(
                text = "Toplam kategori",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "$customCount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
            )
            Text(
                text = "Özel kategori",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryTopCategorySection(
    summary: CategoriesSummaryUiModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val labelPrefix = if (summary.topCategoryType == TransactionType.INCOME) {
            "Bu ay en yüksek gelir"
        } else {
            "Bu ay en yüksek gider"
        }

        Text(
            text = labelPrefix,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (summary.topCategoryName != null && summary.formattedTopCategoryAmount != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = summary.topCategoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = summary.formattedTopCategoryAmount,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF374151),
                    maxLines = 1,
                )
            }
        } else {
            val emptyTitle = if (summary.topCategoryType == TransactionType.INCOME) "Gelir Yok" else "Harcama Yok"
            Text(
                text = emptyTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1F2937),
                maxLines = 1,
            )
            Text(
                text = if (summary.topCategoryType == TransactionType.INCOME) {
                    "Bu dönemde gelir kaydı bulunmuyor"
                } else {
                    "Bu dönemde gider kaydı bulunmuyor"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (summary.balanceMessage.isNotBlank()) {
            Text(
                text = summary.balanceMessage,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) MaterialTheme.colorScheme.primary else SageGreen,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
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
    val singleBar = bars.size == 1
    val barWidth = if (singleBar) 16.dp else 7.dp
    val spacing = 5.dp

    Box(
        modifier = modifier
            .widthIn(min = 68.dp, max = 92.dp)
            .height(56.dp)
            .semantics {
                contentDescription = "Kategori harcama dağılımı görseli"
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
                text = "$count kategori",
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
                        text = "Tümü",
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
                    contentDescription = "Tüm kategorileri göster"
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
                        text = "Gider",
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
                    contentDescription = "Gider kategorilerini filtrele"
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
                        text = "Gelir",
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
                    contentDescription = "Gelir kategorilerini filtrele"
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        FilterChip(
            selected = selectedType == TransactionType.EXPENSE,
            onClick = { onTypeSelected(TransactionType.EXPENSE) },
            label = {
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Gider kategorilerini filtrele"
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
                    text = "Gelir",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.INCOME) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Gelir kategorilerini filtrele"
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                contentDescription = "${item.category.name}, ${item.transactionCount} işlem, seçili dönem tutarı ${item.formattedCurrentAmount}."
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
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.transactionCount} işlem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tutar ve Trend rozeti
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.formattedCurrentAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                                text = "Yeni",
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

            // Sağ aksiyon: Özel kategoriler için taşma menüsü, sistem kategorileri için ok ikonu
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
                                contentDescription = "${item.category.name} kategorisi işlem seçenekleri"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Düzenle") },
                            onClick = {
                                isMenuExpanded = false
                                onEditClick(item.category.id)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Sil",
                                    color = MaterialTheme.colorScheme.error,
                                )
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
    insightText: String,
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
                    text = "Feniqo İçgörü",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.primary else SageGreen,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insightText,
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                contentDescription = "Yeni kategori oluştur. Özel bir kategori eklemek için tıklayın."
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
                    text = "Yeni kategori oluştur",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Harcamalarınıza uygun özel bir kategori ekleyin.",
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
                text = message.toDisplayText(),
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
                        contentDescription = "Mesajı kapat"
                    },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = contentColor,
                ),
            ) {
                Text(
                    text = "Kapat",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Kategori silme onay diyaloğudur.
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
            Text(
                text = "Kategoriyi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "“${targetCategory.name}” kategorisini silmek istediğinize emin misiniz? Geçmiş işlemlerde kategori adı korunur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleteInProgress && !targetCategory.isDefault,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isDeleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(text = "Siliniyor...")
                } else {
                    Text(text = "Sil")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleteInProgress,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = "İptal")
            }
        },
    )
}
