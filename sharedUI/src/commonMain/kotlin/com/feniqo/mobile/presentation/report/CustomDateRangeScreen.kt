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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.usecase.CalculateReportDateRangeUseCase
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 22 Özel tarih aralığı seçim ekranı.
 * Başlangıç/bitiş kartları, dinamik ay navigasyonlu takvim ve hızlı seçim çipleri sunar.
 */
@Composable
fun CustomDateRangeScreen(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    referenceDate: LocalDate,
    onApplyRange: (LocalDate, LocalDate) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftStart by remember { mutableStateOf<LocalDate?>(initialStartDate ?: referenceDate) }
    var draftEnd by remember { mutableStateOf<LocalDate?>(initialEndDate ?: referenceDate) }

    // Takvimde görüntülenen geçerli ay ve yıl
    var viewYear by remember { mutableStateOf((draftEnd ?: referenceDate).year) }
    var viewMonth by remember { mutableStateOf((draftEnd ?: referenceDate).monthNumber) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = FeniqoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Özel tarih aralığı",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = {
                        val s = draftStart
                        val e = draftEnd
                        if (s != null && e != null && e >= s) {
                            onApplyRange(s, e)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = draftStart != null && draftEnd != null && draftEnd!! >= draftStart!!,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E3A2F),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFD1D5DB),
                        disabledContentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Tarihleri uygula",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // BAŞLANGIÇ VE BİTİŞ TARİH KARTLARI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateSummaryCard(
                    title = "Başlangıç",
                    dateText = draftStart?.let { ReportSummaryFormatter.formatDateDisplay(it) } ?: "Seçiniz",
                    modifier = Modifier.weight(1f),
                )
                DateSummaryCard(
                    title = "Bitiş",
                    dateText = draftEnd?.let { ReportSummaryFormatter.formatDateDisplay(it) } ?: "Seçiniz",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // İNTERAKTİF TAKVİM BİLEŞENİ
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFAFAFA),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // Ay ve Yıl Başlığı Navigasyonu (< Eylül 2026 >)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (viewMonth == 1) {
                                    viewMonth = 12
                                    viewYear -= 1
                                } else {
                                    viewMonth -= 1
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Önceki Ay",
                                tint = FeniqoTextPrimary,
                            )
                        }

                        Text(
                            text = "${ReportSummaryFormatter.monthName(viewMonth)} $viewYear",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = FeniqoTextPrimary,
                        )

                        IconButton(
                            onClick = {
                                if (viewMonth == 12) {
                                    viewMonth = 1
                                    viewYear += 1
                                } else {
                                    viewMonth += 1
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Sonraki Ay",
                                tint = FeniqoTextPrimary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gün Başlıkları (Pzt, Sal, Çar, Per, Cum, Cmt, Paz)
                    val daysOfWeek = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        daysOfWeek.forEach { dayName ->
                            Text(
                                text = dayName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                ),
                                color = Color(0xFF6B7280),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Takvim Günleri Izgarası
                    val daysInMonth = CalculateReportDateRangeUseCase.monthLength(viewYear, viewMonth)
                    val firstDayDate = LocalDate(viewYear, viewMonth, 1)
                    val leadingEmptyDays = (firstDayDate.dayOfWeek.ordinal) // 0 = Pazartesi, 6 = Pazar

                    val totalCells = leadingEmptyDays + daysInMonth
                    val rows = (totalCells + 6) / 7

                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNumber = cellIndex - leadingEmptyDays + 1

                                if (dayNumber in 1..daysInMonth) {
                                    val cellDate = LocalDate(viewYear, viewMonth, dayNumber)
                                    val isStart = draftStart == cellDate
                                    val isEnd = draftEnd == cellDate
                                    val isInRange = draftStart != null && draftEnd != null &&
                                            cellDate > draftStart!! && cellDate < draftEnd!!

                                    CalendarDayCell(
                                        dayNumber = dayNumber,
                                        isStart = isStart,
                                        isEnd = isEnd,
                                        isInRange = isInRange,
                                        onClick = {
                                            if (draftStart == null || (draftStart != null && draftEnd != null)) {
                                                draftStart = cellDate
                                                draftEnd = null
                                            } else if (cellDate >= draftStart!!) {
                                                draftEnd = cellDate
                                            } else {
                                                draftStart = cellDate
                                                draftEnd = null
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // HIZLI SEÇİM BÖLÜMÜ
            Text(
                text = "Hızlı seçim",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FeniqoTextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickSelectChip(
                    text = "Bu hafta",
                    onClick = {
                        val dayOfWeek = referenceDate.dayOfWeek.ordinal
                        val start = referenceDate.minus(dayOfWeek, DateTimeUnit.DAY)
                        val end = start.plus(6, DateTimeUnit.DAY)
                        draftStart = start
                        draftEnd = end
                        viewMonth = end.monthNumber
                        viewYear = end.year
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickSelectChip(
                    text = "Bu ay",
                    onClick = {
                        val start = LocalDate(referenceDate.year, referenceDate.monthNumber, 1)
                        val end = LocalDate(
                            referenceDate.year,
                            referenceDate.monthNumber,
                            CalculateReportDateRangeUseCase.monthLength(referenceDate.year, referenceDate.monthNumber)
                        )
                        draftStart = start
                        draftEnd = end
                        viewMonth = referenceDate.monthNumber
                        viewYear = referenceDate.year
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickSelectChip(
                    text = "Son 90 gün",
                    onClick = {
                        val start = referenceDate.minus(90, DateTimeUnit.DAY)
                        draftStart = start
                        draftEnd = referenceDate
                        viewMonth = referenceDate.monthNumber
                        viewYear = referenceDate.year
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DateSummaryCard(
    title: String,
    dateText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawRoundRect(
                    color = Color(0xFF1E3A2F),
                    size = Size(size.width, size.height * 0.9f),
                    topLeft = Offset(0f, size.height * 0.1f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
                drawLine(Color(0xFF1E3A2F), Offset(0f, size.height * 0.35f), Offset(size.width, size.height * 0.35f), 1.5.dp.toPx())
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoTextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isStart: Boolean,
    isEnd: Boolean,
    isInRange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Aralık arka plan bandı
        if (isInRange) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color(0xFFE8F1EB)),
            )
        }

        // Başlangıç veya Bitiş dairesi
        if (isStart || isEnd) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A2F)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = Color.White,
                )
            }
        } else {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isInRange) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                ),
                color = if (isInRange) Color(0xFF1E3A2F) else FeniqoTextPrimary,
            )
        }
    }
}

@Composable
private fun QuickSelectChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3F4F6),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = Color(0xFF374151),
            )
        }
    }
}
