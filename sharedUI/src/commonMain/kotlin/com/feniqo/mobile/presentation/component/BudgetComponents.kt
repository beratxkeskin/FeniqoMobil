package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetCopyConfirmationState
import com.feniqo.mobile.presentation.budget.BudgetDeleteConfirmationState
import com.feniqo.mobile.presentation.budget.BudgetMonthlySummaryDisplayModel
import com.feniqo.mobile.presentation.budget.BudgetProgressDisplayModel
import com.feniqo.mobile.presentation.budget.nextMonth
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

private val FeniqoSageGreen = Color(0xFF2D5A43)

/**
 * 01 Bütçe Ana Ekranı Üst Başlığı (Feniqo brand title, Bütçe başlığı, Dönem hapı ve Kopyala butonu).
 */
@Composable
fun BudgetHeader(
    selectedMonth: YearMonth?,
    onMonthSelected: (YearMonth) -> Unit,
    onOpenMonthPicker: () -> Unit = {},
    onCopyBudgets: () -> Unit = {},
    canCopy: Boolean = true,
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val formattedMonth = if (selectedMonth != null) {
        DateFormatter.formatYearMonth(selectedMonth)
    } else {
        "Dönem Seçin"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "feniqo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoSageGreen,
                )
                Text(
                    text = "Bütçe",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                // Dönem Seçici Hap Buton
                Surface(
                    onClick = onOpenMonthPicker,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = formattedMonth,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dönem seç",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Kopyala Butonu
                Surface(
                    onClick = onCopyBudgets,
                    enabled = canCopy && selectedMonth != null,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Bütçeleri kopyala",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Kopyala",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 01 Tasarımına uygun 3 sütunlu Aylık Bütçe Özet Kartı:
 * Bütçe (₺15.000) | Harcanan (₺12.150) | Kalan (₺2.850)
 * Altında: "Bütçelenen kategorilerde" ve %81 renkli ilerleme çubuğu.
 */
@Composable
fun BudgetMonthlyOverviewCard(
    summary: BudgetMonthlySummaryDisplayModel,
    modifier: Modifier = Modifier,
) {
    val progressColor = when (summary.health) {
        BudgetHealth.SAFE -> FeniqoSageGreen
        BudgetHealth.WARNING -> Color(0xFFF59E0B)
        BudgetHealth.EXCEEDED -> Color(0xFFEF4444)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // 3 Eşit Sütun
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewColumn(
                    label = "Bütçe",
                    amount = summary.prefixLimit,
                    modifier = Modifier.weight(1f),
                )
                OverviewColumn(
                    label = "Harcanan",
                    amount = summary.prefixSpent,
                    modifier = Modifier.weight(1f),
                )
                OverviewColumn(
                    label = "Kalan",
                    amount = summary.prefixRemaining,
                    modifier = Modifier.weight(1f),
                )
            }

            // Alt Satır: "Bütçelenen kategorilerde" + İlerleme Çubuğu ve %81
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                Text(
                    text = "Bütçelenen kategorilerde",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    LinearProgressIndicator(
                        progress = { summary.usageProgressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = summary.compactUsageRate,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = progressColor,
                    )
                }
            }

            // Farklı Para Birimi Notu (Varsa)
            if (summary.excludedTransactionCount > 0) {
                Text(
                    text = "${summary.excludedTransactionCount} işlem farklı para biriminde olduğu için toplama dahil edilmedi.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD97706),
                )
            }
        }
    }
}

@Composable
private fun OverviewColumn(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 01 Tasarımındaki Aşım Uyarısı Banner'ı:
 * "! 1 bütçe aşıldı >"
 */
@Composable
fun BudgetExceededBanner(
    exceededCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (exceededCount <= 0) return

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF3C7), // Açık amber
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFFF59E0B), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "$exceededCount bütçe aşıldı",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Aşılan bütçeleri gör",
                tint = Color(0xFF92400E),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 01 Tasarımındaki Yeşil Çizgili Başlık:
 * "▎Kategori bütçeleri"
 */
@Composable
fun BudgetSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(FeniqoSageGreen, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 01 Tasarımına uygun Kategori Bütçe Kartı:
 * CategoryTonalIcon | Market - ₺4.200 / ₺6.000 (altında ince progress bar) | ₺1.800 kaldı (%70) | >
 */
@Composable
fun BudgetProgressCard(
    budget: BudgetProgressDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(budget.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    val isExceeded = budget.health == BudgetHealth.EXCEEDED
    val progressColor = when (budget.health) {
        BudgetHealth.SAFE -> FeniqoSageGreen
        BudgetHealth.WARNING -> Color(0xFFF59E0B)
        BudgetHealth.EXCEEDED -> Color(0xFFEF4444)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // 1. Kategori Tonal İkonu
            CategoryTonalIcon(
                iconKey = budget.categoryIconKey,
                color = categoryColor,
                containerSize = 44.dp,
            )

            // 2. Kategori Adı, Harcanan / Limit ve İlerleme Çubuğu
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = budget.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${budget.prefixSpent} / ${budget.prefixLimit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { budget.usageProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
            }

            // 3. Sağ Taraf: Kalan/Aşılan Tutar ve Kullanım Yüzdesi
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = budget.statusSummaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = budget.compactUsageRate,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 4. Detaya Geçiş Chevron Oku
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "${budget.categoryName} bütçesini incele",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * B10 Tasarımına uygun Boş Dönem Ekranı:
 * Dairesel takvim ikonu, "Bu ay bütçe yok", "Yeni bütçe" ve "Önceki aydan kopyala" butonları.
 */
@Composable
fun BudgetEmptyState(
    onNewBudget: () -> Unit,
    onCopyFromPreviousMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

        Text(
            text = "Bu ay bütçe yok",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Text(
            text = "Kategori limitlerini belirleyerek başlayabilirsin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraLarge))

        Button(
            onClick = onNewBudget,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
        ) {
            Text(
                text = "Yeni bütçe",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        OutlinedButton(
            onClick = onCopyFromPreviousMonth,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(FeniqoRadius.Medium),
        ) {
            Text(
                text = "Önceki aydan kopyala",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * B07 Tasarımına uygun Kategori Seçim Modalı (Arama alanı + Gider kategorileri radyo listesi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetCategoryPickerSheet(
    categories: List<CategoryDisplayModel>,
    selectedCategoryId: EntityId?,
    onCategorySelected: (EntityId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large)
                .padding(bottom = FeniqoSpacing.Large),
        ) {
            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Kategori seç",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // Arama Alanı
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Kategori ara") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Kategori Listesi
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                items(filteredCategories, key = { it.id.value }) { category ->
                    val isSelected = category.id == selectedCategoryId
                    val catColor = ColorParser.parseHexColorOrNull(category.colorHex)
                        ?: MaterialTheme.colorScheme.primary

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FeniqoRadius.Medium))
                            .clickable {
                                onCategorySelected(category.id)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        CategoryTonalIcon(
                            iconKey = category.iconKey,
                            color = catColor,
                            containerSize = 40.dp,
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Gider kategorisi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onCategorySelected(category.id)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                        )
                    }
                }
            }
        }
    }
}

/**
 * B08 Tasarımına uygun Dönem ve Para Birimi Seçim Modalı:
 * Yıl gezinimi (< 2026 >), 3x4 ay ızgarası (Oca..Ara), Para birimi açılır menüsü ve "Seçimi uygula" butonu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetPeriodPickerSheet(
    initialMonth: YearMonth,
    initialCurrency: Currency,
    onApply: (YearMonth, Currency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parts = initialMonth.value.split("-")
    var selectedYear by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var selectedMonthIndex by remember { mutableStateOf((parts.getOrNull(1)?.toIntOrNull() ?: 9) - 1) }
    var selectedCurrency by remember { mutableStateOf(initialCurrency) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }

    val monthNames = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large)
                .padding(bottom = FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dönem ve para birimi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            // Dönem Seçin Başlığı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Dönem seçin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Yıl Gezinimi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedYear -= 1 }) {
                    Text("<", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.Large))
                Text(
                    text = selectedYear.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(FeniqoSpacing.Large))
                IconButton(onClick = { selectedYear += 1 }) {
                    Text(">", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }

            // 3x4 Ay Izgarası
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        for (col in 0..2) {
                            val monthIdx = row * 3 + col
                            val isSelected = monthIdx == selectedMonthIndex
                            Surface(
                                onClick = { selectedMonthIndex = monthIdx },
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                color = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = monthNames[monthIdx],
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Para Birimi Seçimi
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Text(
                        text = "§",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Para birimi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { currencyMenuExpanded = true },
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = FeniqoSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val currName = when (selectedCurrency) {
                                Currency.TRY -> "Türk lirası (TRY)"
                                Currency.USD -> "Amerikan doları (USD)"
                                Currency.EUR -> "Avro (EUR)"
                                Currency.GBP -> "İngiliz sterlini (GBP)"
                            }
                            Text(text = currName, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = currencyMenuExpanded,
                        onDismissRequest = { currencyMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Türk lirası (TRY)") },
                            onClick = { selectedCurrency = Currency.TRY; currencyMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Amerikan doları (USD)") },
                            onClick = { selectedCurrency = Currency.USD; currencyMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Avro (EUR)") },
                            onClick = { selectedCurrency = Currency.EUR; currencyMenuExpanded = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // Seçimi Uygula Butonu
            Button(
                onClick = {
                    val monthStr = (selectedMonthIndex + 1).toString().padStart(2, '0')
                    val selectedYM = YearMonth("$selectedYear-$monthStr")
                    onApply(selectedYM, selectedCurrency)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
            ) {
                Text(
                    text = "Seçimi uygula",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * B09 Tasarımına uygun Silme Onay Modalı:
 * Kategori ikonu, "Bütçeyi sil?", "Market · Eylül 2026", "Bütçe kaldırılır. Harcama kayıtların silinmez.", "Vazgeç" ve "Sil" butonları.
 */
@Composable
fun BudgetDeleteDialog(
    confirmation: BudgetDeleteConfirmationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDeleting = confirmation.isDeleting
    val target = confirmation.target
    val categoryColor = ColorParser.parseHexColorOrNull(target.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    val monthText = DateFormatter.formatYearMonth(target.month)

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = modifier,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FeniqoSpacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Kapatma butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss, enabled = !isDeleting) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                // Dairesel Tonal Kategori İkonu
                CategoryTonalIcon(
                    iconKey = target.categoryIconKey,
                    color = categoryColor,
                    containerSize = 64.dp,
                )

                Text(
                    text = "Bütçeyi sil?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "${target.categoryName} · $monthText",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Bütçe kaldırılır. Harcama kayıtların silinmez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                    ) {
                        Text("Vazgeç")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEE2E2), // Yumuşak kırmızı
                            contentColor = Color(0xFFDC2626),
                        ),
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFDC2626),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFFDC2626),
                                )
                                Text("Sil", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/**
 * B05 Tasarımına uygun Bütçeleri Kopyalama Diyaloğu / Kartı.
 */
@Composable
fun BudgetCopyDialog(
    confirmation: BudgetCopyConfirmationState,
    onSourceMonthChanged: (YearMonth) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCopying = confirmation.isCopying
    val sourceMonth = confirmation.sourceMonth
    val targetMonth = confirmation.targetMonth
    val isSameMonth = sourceMonth == targetMonth
    val formattedSource = DateFormatter.formatYearMonth(sourceMonth)
    val formattedTarget = DateFormatter.formatYearMonth(targetMonth)

    AlertDialog(
        onDismissRequest = { if (!isCopying) onDismiss() },
        modifier = modifier,
        title = {
            Text(
                text = "Bütçeleri kopyala",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Bilgi Kartı: Sadece bütçe tanımları kopyalanır
                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = Color(0xFFEFF6FF), // Soft blue
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = "Sadece bütçe tanımları kopyalanır.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                            )
                            Text(
                                text = "Harcamalar taşınmaz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1E40AF),
                            )
                        }
                    }
                }

                // Ay Eşleme Kartı: Kaynak Ay -> Hedef Ay
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Kaynak ay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSourceMonthChanged(sourceMonth.previousMonth()) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Önceki ay",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(formattedSource, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { onSourceMonthChanged(sourceMonth.nextMonth()) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Sonraki ay",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Hedef ay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formattedTarget, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Uyarı Kartı: Hedef aydaki bütçeler korunur
                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = Color(0xFFFEF3C7), // Soft amber
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = "Hedef aydaki mevcut bütçeler korunur.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                            )
                            Text(
                                text = "Aynı kategoride bütçe varsa üzerine yazılmaz, atlanır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                            )
                        }
                    }
                }

                if (isSameMonth) {
                    Text(
                        text = "Kaynak ve hedef ay aynı olamaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCopying && !isSameMonth,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
            ) {
                if (isCopying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Kopyala", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCopying) {
                Text("Vazgeç")
            }
        },
    )
}

/**
 * B12 Çevrimdışı Bilgilendirme Banner'ı.
 */
@Composable
fun BudgetOfflineBanner(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        color = Color(0xFFEFF6FF), // Açık mavi
    ) {
        Row(
            modifier = Modifier.padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = Color(0xFF1E40AF),
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = "Çevrimdışısın.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF),
                )
                Text(
                    text = "Değişiklikler bu cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1E40AF),
                )
            }
        }
    }
}
