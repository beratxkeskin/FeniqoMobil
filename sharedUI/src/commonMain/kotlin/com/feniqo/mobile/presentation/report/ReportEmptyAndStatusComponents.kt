package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground

import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_empty_filtered_action_change_period
import feniqomobil.sharedui.generated.resources.report_empty_filtered_action_clear
import feniqomobil.sharedui.generated.resources.report_empty_filtered_desc
import feniqomobil.sharedui.generated.resources.report_empty_filtered_illustration_desc
import feniqomobil.sharedui.generated.resources.report_empty_filtered_title
import feniqomobil.sharedui.generated.resources.report_empty_workspace_action_add
import feniqomobil.sharedui.generated.resources.report_empty_workspace_action_home
import feniqomobil.sharedui.generated.resources.report_empty_workspace_desc
import feniqomobil.sharedui.generated.resources.report_empty_workspace_illustration_desc
import feniqomobil.sharedui.generated.resources.report_empty_workspace_title
import feniqomobil.sharedui.generated.resources.report_error_action_retry
import feniqomobil.sharedui.generated.resources.report_error_default_message
import feniqomobil.sharedui.generated.resources.report_error_title
import feniqomobil.sharedui.generated.resources.report_filter_chip_remove_desc
import feniqomobil.sharedui.generated.resources.report_loading_desc
import feniqomobil.sharedui.generated.resources.report_loading_title
import feniqomobil.sharedui.generated.resources.report_offline_desc_synced
import feniqomobil.sharedui.generated.resources.report_offline_info_banner
import feniqomobil.sharedui.generated.resources.report_offline_pending_plural
import feniqomobil.sharedui.generated.resources.report_offline_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 24 Henüz rapor yok ekran görünümü.
 * Aktif çalışma alanında henüz raporlanabilir hiçbir işlem bulunmadığında gösterilir.
 */
@Composable
fun NoReportsEmptyView(
    onAddTransaction: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptyIllustrationCd = stringResource(Res.string.report_empty_workspace_illustration_desc)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Grafik ve yaprak çizimi
        Box(
            modifier = Modifier
                .size(160.dp)
                .semantics { contentDescription = emptyIllustrationCd },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                // Kavisli ark plan çizgisi
                val arcPath = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.8f)
                    quadraticTo(
                        size.width * 0.45f, size.height * 0.45f,
                        size.width * 0.85f, size.height * 0.35f,
                    )
                }
                drawPath(
                    path = arcPath,
                    color = Color(0xFFE2E8F0),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )

                // Çubuk grafik sütunları
                val barColor = Color(0xFFCBD5E1)
                val barWidth = 14.dp.toPx()
                val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                // 1. Sütun
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.55f),
                    size = Size(barWidth, size.height * 0.25f),
                    cornerRadius = cornerRadius,
                )
                // 2. Sütun
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(size.width * 0.42f, size.height * 0.42f),
                    size = Size(barWidth, size.height * 0.38f),
                    cornerRadius = cornerRadius,
                )
                // 3. Sütun
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(size.width * 0.56f, size.height * 0.32f),
                    size = Size(barWidth, size.height * 0.48f),
                    cornerRadius = cornerRadius,
                )

                // Büyüyen yeşil yaprak figürü
                val leafPath = Path().apply {
                    moveTo(size.width * 0.72f, size.height * 0.55f)
                    cubicTo(
                        size.width * 0.72f, size.height * 0.35f,
                        size.width * 0.90f, size.height * 0.32f,
                        size.width * 0.92f, size.height * 0.32f,
                    )
                    cubicTo(
                        size.width * 0.90f, size.height * 0.55f,
                        size.width * 0.76f, size.height * 0.58f,
                        size.width * 0.72f, size.height * 0.55f,
                    )
                }
                drawPath(path = leafPath, color = Color(0xFF4A7C59))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.report_empty_workspace_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.report_empty_workspace_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Eylemler
        Button(
            onClick = onAddTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E3A2F),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(Res.string.report_empty_workspace_action_add),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onNavigateToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = stringResource(Res.string.report_empty_workspace_action_home),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 25 Bu filtrelerde sonuç yok ekran görünümü.
 * Seçilen filtrelere uyan işlem bulunamadığında gösterilir; üstte kaldırılabilir çip çubuğu yer alır.
 */
@Composable
fun NoFilteredResultsView(
    activeFilters: List<ActiveFilterChipUiModel>,
    onRemoveFilter: (ActiveFilterChipUiModel) -> Unit,
    onClearFilters: () -> Unit,
    onChangePeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Üst Aktif Filtre Çipleri
        if (activeFilters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeFilters.forEach { chip ->
                    FilterChipRemovable(
                        chip = chip,
                        onRemove = { onRemoveFilter(chip) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Belge + Büyüteç + Yaprak İllüstrasyonu
        val filteredEmptyIllustrationCd = stringResource(Res.string.report_empty_filtered_illustration_desc)
        Box(
            modifier = Modifier
                .size(160.dp)
                .semantics { contentDescription = filteredEmptyIllustrationCd },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                // Belge arkalık
                val docPath = Path().apply {
                    val w = size.width * 0.46f
                    val h = size.height * 0.60f
                    val left = size.width * 0.24f
                    val top = size.height * 0.20f
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = left,
                            top = top,
                            right = left + w,
                            bottom = top + h,
                            radiusX = 8.dp.toPx(),
                            radiusY = 8.dp.toPx(),
                        )
                    )
                }
                drawPath(
                    path = docPath,
                    color = Color(0xFFE2E8F0),
                    style = Stroke(width = 2.dp.toPx()),
                )

                // Belge iç çizgileri
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(size.width * 0.32f, size.height * 0.35f),
                    end = Offset(size.width * 0.58f, size.height * 0.35f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(size.width * 0.32f, size.height * 0.45f),
                    end = Offset(size.width * 0.54f, size.height * 0.45f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Büyüteç
                val glassCenter = Offset(size.width * 0.55f, size.height * 0.58f)
                val glassRadius = 18.dp.toPx()
                drawCircle(
                    color = Color(0xFF1E3A2F),
                    center = glassCenter,
                    radius = glassRadius,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawLine(
                    color = Color(0xFF1E3A2F),
                    start = Offset(glassCenter.x + glassRadius * 0.7f, glassCenter.y + glassRadius * 0.7f),
                    end = Offset(glassCenter.x + glassRadius * 1.5f, glassCenter.y + glassRadius * 1.5f),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Yeşil yaprak
                val leafPath = Path().apply {
                    moveTo(size.width * 0.70f, size.height * 0.45f)
                    cubicTo(
                        size.width * 0.70f, size.height * 0.30f,
                        size.width * 0.86f, size.height * 0.28f,
                        size.width * 0.88f, size.height * 0.28f,
                    )
                    cubicTo(
                        size.width * 0.86f, size.height * 0.48f,
                        size.width * 0.74f, size.height * 0.50f,
                        size.width * 0.70f, size.height * 0.45f,
                    )
                }
                drawPath(path = leafPath, color = Color(0xFF4A7C59))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.report_empty_filtered_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.report_empty_filtered_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Eylemler
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onClearFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A2F),
                    contentColor = Color.White,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawLine(Color.White, Offset(0f, 3.dp.toPx()), Offset(size.width, 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White, Offset(3.dp.toPx(), 8.dp.toPx()), Offset(size.width - 3.dp.toPx(), 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White, Offset(6.dp.toPx(), 13.dp.toPx()), Offset(size.width - 6.dp.toPx(), 13.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.report_empty_filtered_action_clear),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            OutlinedButton(
                onClick = onChangePeriod,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawRoundRect(
                            color = Color(0xFF4B5563),
                            size = Size(size.width, size.height * 0.9f),
                            topLeft = Offset(0f, size.height * 0.1f),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                        drawLine(Color(0xFF4B5563), Offset(0f, size.height * 0.35f), Offset(size.width, size.height * 0.35f), 1.5.dp.toPx())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.report_empty_filtered_action_change_period),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF1E3A2F),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Kaldırılabilir tekil filtre çipi bileşeni.
 */
@Composable
private fun FilterChipRemovable(
    chip: ActiveFilterChipUiModel,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Küçük ikon göstergesi
            when (chip.type) {
                ActiveFilterType.PERIOD -> {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        drawRoundRect(
                            color = Color(0xFF4B5563),
                            size = Size(size.width, size.height * 0.9f),
                            topLeft = Offset(0f, size.height * 0.1f),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                            style = Stroke(1.2.dp.toPx()),
                        )
                    }
                }
                ActiveFilterType.TYPE -> {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        drawCircle(Color(0xFFEF4444), radius = 5.dp.toPx())
                    }
                }
                ActiveFilterType.CURRENCY -> {
                    Text(
                        text = "₺",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B5563),
                    )
                }
                ActiveFilterType.CATEGORY -> {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        drawRoundRect(
                            color = Color(0xFF4B5563),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                            style = Stroke(1.2.dp.toPx()),
                        )
                    }
                }
            }

            Text(
                text = chip.toLocalizedLabel(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 'x' butonu
            val removeFilterCd = stringResource(Res.string.report_filter_chip_remove_desc, chip.toLocalizedLabel())
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = removeFilterCd,
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 26 Sistem durumları: Yükleniyor durumu kartı.
 */
@Composable
fun ReportLoadingCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color(0xFF1E3A2F),
                strokeWidth = 3.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.report_loading_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.report_loading_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 26 Sistem durumları: Çevrimdışı ve Bekleyen Değişiklik Durumu Kartı ve Bilgilendirme Uyarısı.
 */
@Composable
fun ReportOfflineStatusSection(
    pendingCount: Int,
    onNavigateToSyncStatus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Çevrimdışısın Kartı
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onNavigateToSyncStatus),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Turuncu/amber çevrimdışı ikonu rozeti
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        // Wifi-off / kesikli çizgi
                        drawLine(
                            color = Color(0xFFD97706),
                            start = Offset(2.dp.toPx(), 2.dp.toPx()),
                            end = Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawArc(
                            color = Color(0xFFD97706),
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
                            size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.report_offline_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val pendingText = if (pendingCount > 0) {
                        pluralStringResource(Res.plurals.report_offline_pending_plural, pendingCount, pendingCount)
                    } else {
                        stringResource(Res.string.report_offline_desc_synced)
                    }
                    Text(
                        text = pendingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "›",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Bilgilendirme Uyarısı (Açık şeftali/amber kutu)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "i",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(Res.string.report_offline_info_banner),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 26 Sistem durumları: Hata Durumu Kartı.
 */
@Composable
fun ReportErrorCard(
    onRetry: () -> Unit,
    error: ReportUiError? = null,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val defaultErrorMessage = stringResource(Res.string.report_error_default_message)
    val displayErrorMessage = error?.toLocalizedMessage() ?: errorMessage ?: defaultErrorMessage

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Kırmızı ünlem rozeti
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.report_error_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF991B1B),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.report_error_action_retry),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
fun OfflineSyncWarningBanner(
    pendingOperationCount: Int,
    onSyncClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ReportOfflineStatusSection(
        pendingCount = pendingOperationCount,
        onNavigateToSyncStatus = onSyncClick,
        modifier = modifier,
    )
}

@Composable
fun ReportLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ReportLoadingCard(modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun EmptyWorkspaceReportView(
    onAddTransaction: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoReportsEmptyView(
        onAddTransaction = onAddTransaction,
        onNavigateToHome = onNavigateToHome,
        modifier = modifier,
    )
}
