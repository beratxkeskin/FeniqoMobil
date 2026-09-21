package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.usecase.CalculateReportDateRangeUseCase
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary

/**
 * 21 Rapor filtreleri alt sayfası (Bottom Sheet / Modal).
 * Geçici bir draft state yönetir; "Raporu uygula" denene kadar ana rapor değişmez.
 * Kapatılırsa veya iptal edilirse değişiklikler uygulanmaz.
 */
@Composable
fun ReportFiltersSheetContent(
    initialFilter: ReportFilterUiState,
    referenceDate: LocalDate,
    onApplyFilter: (ReportFilterUiState) -> Unit,
    onNavigateToCustomDateRange: () -> Unit,
    onSelectCategory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Geçici draft state: Kullanıcı seçimleri burada tutulur
    var draftFilter by remember(initialFilter) { mutableStateOf(initialFilter) }

    val dateRangeCalculator = remember { CalculateReportDateRangeUseCase() }
    val currentRange = remember(draftFilter, referenceDate) {
        val customRange = if (draftFilter.customStartDate != null && draftFilter.customEndDate != null) {
            ReportDateRange(draftFilter.customStartDate!!, draftFilter.customEndDate!!)
        } else {
            null
        }
        dateRangeCalculator(draftFilter.periodPreset, customRange, referenceDate)
    }

    val summaryText = remember(draftFilter, currentRange) {
        ReportSummaryFormatter.formatFilterSummary(draftFilter, currentRange)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Çekme tutamacı (Drag handle)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE5E7EB)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Rapor filtreleri",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. DÖNEM BÖLÜMÜ
            Text(
                text = "Dönem",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FeniqoTextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterSelectionChip(
                    text = "Bu ay",
                    isSelected = draftFilter.periodPreset == ReportPeriodPreset.THIS_MONTH,
                    onClick = { draftFilter = draftFilter.copy(periodPreset = ReportPeriodPreset.THIS_MONTH) },
                    modifier = Modifier.weight(1f),
                )
                FilterSelectionChip(
                    text = "Geçen ay",
                    isSelected = draftFilter.periodPreset == ReportPeriodPreset.LAST_MONTH,
                    onClick = { draftFilter = draftFilter.copy(periodPreset = ReportPeriodPreset.LAST_MONTH) },
                    modifier = Modifier.weight(1f),
                )
                FilterSelectionChip(
                    text = "Son 3 ay",
                    isSelected = draftFilter.periodPreset == ReportPeriodPreset.LAST_3_MONTHS,
                    onClick = { draftFilter = draftFilter.copy(periodPreset = ReportPeriodPreset.LAST_3_MONTHS) },
                    modifier = Modifier.weight(1f),
                )
                FilterSelectionChip(
                    text = "Özel tarih",
                    isSelected = draftFilter.periodPreset == ReportPeriodPreset.CUSTOM,
                    onClick = {
                        draftFilter = draftFilter.copy(periodPreset = ReportPeriodPreset.CUSTOM)
                        onNavigateToCustomDateRange()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. İŞLEM TÜRÜ BÖLÜMÜ
            Text(
                text = "İşlem türü",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FeniqoTextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterSelectionChip(
                    text = "Tümü",
                    isSelected = draftFilter.typeFilter == ReportTypeFilter.ALL,
                    onClick = { draftFilter = draftFilter.copy(typeFilter = ReportTypeFilter.ALL) },
                    modifier = Modifier.weight(1f),
                )
                FilterSelectionChip(
                    text = "Gelir",
                    isSelected = draftFilter.typeFilter == ReportTypeFilter.INCOME,
                    onClick = { draftFilter = draftFilter.copy(typeFilter = ReportTypeFilter.INCOME) },
                    modifier = Modifier.weight(1f),
                )
                FilterSelectionChip(
                    text = "Gider",
                    isSelected = draftFilter.typeFilter == ReportTypeFilter.EXPENSE,
                    onClick = { draftFilter = draftFilter.copy(typeFilter = ReportTypeFilter.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. PARA BİRİMİ BÖLÜMÜ
            Text(
                text = "Para birimi",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FeniqoTextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Currency.entries.forEach { curr ->
                    FilterSelectionChip(
                        text = curr.code,
                        isSelected = draftFilter.currency == curr,
                        onClick = { draftFilter = draftFilter.copy(currency = curr) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. KATEGORİLER BÖLÜMÜ
            Text(
                text = "Kategoriler",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FeniqoTextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onSelectCategory),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF9FAFB),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawRoundRect(
                                color = Color(0xFF4B5563),
                                size = Size(8.dp.toPx(), 8.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            drawRoundRect(
                                color = Color(0xFF4B5563),
                                topLeft = Offset(11.dp.toPx(), 0f),
                                size = Size(8.dp.toPx(), 8.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            drawRoundRect(
                                color = Color(0xFF4B5563),
                                topLeft = Offset(0f, 11.dp.toPx()),
                                size = Size(8.dp.toPx(), 8.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            drawRoundRect(
                                color = Color(0xFF4B5563),
                                topLeft = Offset(11.dp.toPx(), 11.dp.toPx()),
                                size = Size(8.dp.toPx(), 8.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }

                        Text(
                            text = draftFilter.selectedCategoryName ?: "Tüm kategoriler",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = FeniqoTextPrimary,
                        )
                    }

                    Text(
                        text = "›",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF9CA3AF),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. AKTİF FİLTRELER ÖZET KARTI
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEDF5F0),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawLine(Color(0xFF1E3A2F), Offset(0f, 4.dp.toPx()), Offset(size.width, 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Color(0xFF1E3A2F), Offset(3.dp.toPx(), 10.dp.toPx()), Offset(size.width - 3.dp.toPx(), 10.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Color(0xFF1E3A2F), Offset(6.dp.toPx(), 16.dp.toPx()), Offset(size.width - 6.dp.toPx(), 16.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktif filtreler",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1E3A2F),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4B5563),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. ALT EYLEM BUTONLARI (Temizle & Raporu uygula)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        draftFilter = ReportFilterUiState.Default
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
                ) {
                    Text(
                        text = "Temizle",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF374151),
                    )
                }

                Button(
                    onClick = {
                        onApplyFilter(draftFilter)
                    },
                    modifier = Modifier
                        .weight(1.8f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E3A2F),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Raporu uygula",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Filtreleme seçim çipi.
 */
@Composable
private fun FilterSelectionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) Color(0xFF1E3A2F) else Color(0xFFF9FAFB)
    val textColor = if (isSelected) Color.White else Color(0xFF374151)
    val borderColor = if (isSelected) Color(0xFF1E3A2F) else Color(0xFFE5E7EB)

    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = textColor,
            )
        }
    }
}

/**
 * 21a Rapor doğrudan dönem seçimi alt sayfası (Bottom Sheet).
 * Tarih hapı tıklandığında açılır, tek dokunuşla dönem değiştirmeyi sağlar.
 */
@Composable
fun ReportPeriodPickerSheetContent(
    currentPreset: ReportPeriodPreset,
    onSelectPreset: (ReportPeriodPreset) -> Unit,
    onNavigateToCustomDateRange: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE5E7EB)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dönem seçin",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val presets = listOf(
                ReportPeriodPreset.THIS_MONTH to "Bu ay",
                ReportPeriodPreset.LAST_MONTH to "Geçen ay",
                ReportPeriodPreset.LAST_3_MONTHS to "Son 3 ay",
            )

            presets.forEach { (preset, label) ->
                val isSelected = currentPreset == preset
                Surface(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFFE8F1EC) else Color.Transparent,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) Color(0xFF2D5A43) else FeniqoTextPrimary,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color(0xFF2D5A43),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToCustomDateRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Özel tarih aralığı belirle")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
