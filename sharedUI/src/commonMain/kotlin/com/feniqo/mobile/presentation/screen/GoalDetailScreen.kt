package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.presentation.component.*
import com.feniqo.mobile.presentation.goal.*
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Hedef Detay Ekranı.
 * 02 Hedef detayı, 03 Detay (aşağı kaydırılmış) ve 12 Tamamlanmış hedef detayı panellerine tam uyumludur.
 */
@Composable
fun GoalDetailScreen(
    state: GoalDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FeniqoWarmStoneBackground,
        bottomBar = {
            if (state.detail != null) {
                Surface(
                    color = FeniqoWarmStoneBackground,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeniqoGoalSageGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Para ekle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = "Hedef yükleniyor…",
            )
            state.isNotFound -> ErrorState(
                title = "Hedef Bulunamadı",
                description = "Hedef silinmiş veya bu çalışma alanında bulunmuyor.",
                onRetry = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            state.observationError != null && state.detail == null -> ErrorState(
                title = "Hedef Yüklenemedi",
                description = state.observationError.toDisplayText(),
                onRetry = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            state.detail != null -> DetailContent(
                d = state.detail,
                onBack = onBack,
                onEdit = onEdit,
                onRemove = onRemove,
                onDelete = onDelete,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    d: GoalDetailDisplayModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var contributionsExpanded by remember { mutableStateOf(false) }
    val isCompleted = d.goal.currentAmount.amountMinor >= d.goal.targetAmount.amountMinor

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Üst Bar: Geri & Üç Nokta Menüsü
        item("top-bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = FeniqoTextPrimary,
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Hedef menüsü",
                            tint = FeniqoTextPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = Color.White,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Hedefi düzenle", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = FeniqoTextPrimary) },
                        )
                        HorizontalDivider(color = FeniqoGoalCardBorder)
                        DropdownMenuItem(
                            text = { Text("Hedefi sil", color = FeniqoGoalExpenseRed, fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = FeniqoGoalExpenseRed) },
                        )
                    }
                }
            }
        }

        // 2. Hedef Başlık Alanı (Panel 02 & Panel 12 Tamamlanmış)
        item("header") {
            if (isCompleted) {
                // Panel 12 Tamamlanmış Hedef Başlığı
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(FeniqoGoalSageGreenLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = FeniqoGoalSageGreen,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = d.goal.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoTextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = FeniqoGoalSageGreenLight,
                    ) {
                        Text(
                            text = "Tamamlandı",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoGoalSageGreen,
                        )
                    }
                }
            } else {
                // Panel 02 Aktif Hedef Başlığı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FeniqoGoalSageGreenLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CategorySemanticIconResolver.resolve(d.goal.icon?.key),
                            contentDescription = null,
                            tint = FeniqoGoalSageGreen,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = d.goal.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FeniqoTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = FeniqoGoalSageGreenLight,
                    ) {
                        Text(
                            text = d.statusLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoGoalSageGreen,
                        )
                    }
                }
            }
        }

        // 3. Grafit Ana Kart (#303536)
        item("hero-card") {
            DetailHeroCard(d = d, isCompleted = isCompleted)
        }

        // 4. Hedef Tarihi Kartı
        item("target-date") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onEdit),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = FeniqoTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hedef tarihi",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoTextSecondary,
                        )
                        Text(
                            text = DateFormatter.formatReadableDate(d.goal.targetDate),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextPrimary,
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FeniqoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 5. İkili Hızlı Eylem Butonları (Para çıkar / Düzenle)
        item("actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Para Çıkar Butonu
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onRemove),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = FeniqoTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Para çıkar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextPrimary,
                        )
                    }
                }

                // Düzenle Butonu
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onEdit),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = FeniqoTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Düzenle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextPrimary,
                        )
                    }
                }
            }
        }

        // 6. Birikim İlerlemesi Grafiği (Panel 02 & 03)
        item("chart") {
            DetailChartCard(d = d)
        }

        // 7. Destekleyici Metrikler / İçgörü
        d.insight?.let { insightText ->
            item("insight") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FeniqoGoalSageGreenLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = FeniqoGoalSageGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Feniqo İçgörü",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = FeniqoTextPrimary,
                            )
                            Text(
                                text = insightText,
                                style = MaterialTheme.typography.bodySmall,
                                color = FeniqoTextSecondary,
                            )
                        }
                    }
                }
            }
        }

        // 8. Hareketler Bölümü (Panel 03)
        item("contributions-section") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FeniqoGoalCardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Bölüm Başlığı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Hareketler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FeniqoTextPrimary,
                        )

                        if (d.recentContributions.size > 3) {
                            TextButton(
                                onClick = { contributionsExpanded = !contributionsExpanded },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = if (contributionsExpanded) "Daralt" else "Tümünü gör >",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FeniqoTextSecondary,
                                )
                            }
                        }
                    }

                    if (d.recentContributions.isEmpty()) {
                        Text(
                            text = "Henüz bir hareket kaydı bulunmuyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        val itemsToShow = if (contributionsExpanded) d.recentContributions else d.recentContributions.take(3)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsToShow.forEach { contribution ->
                                MovementRow(contribution)
                            }
                        }
                    }
                }
            }
        }

        item("bottom-spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Detay ekranı Grafit Ana Kartı (#303536).
 */
@Composable
private fun DetailHeroCard(
    d: GoalDetailDisplayModel,
    isCompleted: Boolean,
) {
    val progressRatio = d.progress.value.coerceIn(0, 10_000).toFloat() / 10_000f
    val progressPercent = d.progress.value / 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoGoalGraphite,
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Biriken & Hedef Tutarları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = d.formattedCurrent,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isCompleted) "Mevcut birikim" else "Biriken",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B8BA),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(
                        text = d.formattedTarget,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isCompleted) "Hedef tutarı" else "Hedef",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B8BA),
                    )
                }
            }

            // İlerleme Çubuğu ve Yüzde
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LinearProgressIndicator(
                    progress = { if (isCompleted) 1f else progressRatio },
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF34D399),
                    trackColor = Color(0xFF454B4D),
                )
                Text(
                    text = "%$progressPercent",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E0E0),
                )
            }

            // Kalan Tutar Bilgisi
            if (!isCompleted) {
                Text(
                    text = "Kalan: ${d.formattedRemaining}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B8BA),
                )
            }
        }
    }
}

/**
 * 02 ve 03 panellerine uygun Birikim İlerlemesi Çizgi Grafiği.
 */
@Composable
private fun DetailChartCard(d: GoalDetailDisplayModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Üst Satır: Başlık ve Hedef Referansı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Birikim ilerlemesi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                )

                Text(
                    text = "Hedef: ${d.formattedTarget}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = FeniqoTextSecondary,
                )
            }

            val points = d.chart
            if (points.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Henüz ilerleme hareketi yok.\nPara eklediğinde gelişimini burada görebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val maxVal = maxOf(d.goal.targetAmount.amountMinor, points.maxOf { it.amount.amountMinor }, 1L)
                val midVal = maxVal / 2
                val gridColor = Color(0xFFF1F5F9)
                val lineColor = FeniqoGoalSageGreen
                val targetDashedColor = Color(0xFFCBD5E1)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    // Y Ekseni Etiketleri
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = MoneyFormatter.format(Money(maxVal, d.goal.targetAmount.currency)).take(7),
                            style = MaterialTheme.typography.labelSmall,
                            color = FeniqoTextSecondary,
                        )
                        Text(
                            text = MoneyFormatter.format(Money(midVal, d.goal.targetAmount.currency)).take(7),
                            style = MaterialTheme.typography.labelSmall,
                            color = FeniqoTextSecondary,
                        )
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.labelSmall,
                            color = FeniqoTextSecondary,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Çizim Alanı ve X Ekseni
                    Column(modifier = Modifier.weight(1f)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .semantics {
                                    contentDescription = "${points.size} tarih noktası. Son birikim ${d.formattedCurrent}, hedef ${d.formattedTarget}."
                                },
                        ) {
                            fun yPos(value: Long): Float {
                                val ratio = value.toFloat() / maxVal.toFloat()
                                return size.height - (ratio * (size.height - 16f)) - 8f
                            }

                            // Yatay Kılavuz Çizgileri
                            listOf(0L, midVal, maxVal).forEach { v ->
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, yPos(v)),
                                    end = Offset(size.width, yPos(v)),
                                    strokeWidth = 1.5f,
                                )
                            }

                            // Hedef Kesikli Çizgi
                            drawLine(
                                color = targetDashedColor,
                                start = Offset(0f, yPos(d.goal.targetAmount.amountMinor)),
                                end = Offset(size.width, yPos(d.goal.targetAmount.amountMinor)),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                            )

                            // Veri Noktaları ve Birleşim Çizgileri
                            val divisor = (points.size - 1).coerceAtLeast(1)
                            points.zipWithNext().forEachIndexed { index, pair ->
                                val x1 = (size.width * index) / divisor
                                val y1 = yPos(pair.first.amount.amountMinor)
                                val x2 = (size.width * (index + 1)) / divisor
                                val y2 = yPos(pair.second.amount.amountMinor)

                                drawLine(
                                    color = lineColor,
                                    start = Offset(x1, y1),
                                    end = Offset(x2, y2),
                                    strokeWidth = 3.5f,
                                )
                            }

                            points.forEachIndexed { index, point ->
                                val x = (size.width * index) / divisor
                                val y = yPos(point.amount.amountMinor)
                                drawCircle(color = Color.White, radius = 5f, center = Offset(x, y))
                                drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
                            }
                        }

                        // X Ekseni Tarihleri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${points.first().date.day} ${formatShortMonth(points.first().date.monthNumber)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FeniqoTextSecondary,
                            )
                            if (points.size > 2) {
                                val midPoint = points[points.size / 2]
                                Text(
                                    text = "${midPoint.date.day} ${formatShortMonth(midPoint.date.monthNumber)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FeniqoTextSecondary,
                                )
                            }
                            Text(
                                text = "${points.last().date.day} ${formatShortMonth(points.last().date.monthNumber)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FeniqoTextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 03 Paneline uygun Hareket satırı.
 * Para ekleme yeşil `+`, para çıkarma kırmızı `↑`.
 */
@Composable
private fun MovementRow(c: GoalContribution) {
    val isAdd = c.direction == GoalContributionDirection.ADD
    val amountFormatted = MoneyFormatter.format(c.amount)
    val label = c.note?.takeIf { it.isNotBlank() } ?: if (isAdd) "Birikim eklendi" else "Para çıkarıldı"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // İkon Alanı
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isAdd) FeniqoGoalSageGreenLight else Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isAdd) Icons.Outlined.Add else Icons.Outlined.ArrowUpward,
                contentDescription = null,
                tint = if (isAdd) FeniqoGoalSageGreen else FeniqoGoalExpenseRed,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DateFormatter.formatReadableDate(c.occurredOn),
                style = MaterialTheme.typography.bodySmall,
                color = FeniqoTextSecondary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = FeniqoTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = (if (isAdd) "+" else "−") + amountFormatted,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isAdd) FeniqoTrendGreen else FeniqoGoalExpenseRed,
        )
    }
}

private fun formatShortMonth(month: Int): String = when (month) {
    1 -> "Oca"
    2 -> "Şub"
    3 -> "Mar"
    4 -> "Nis"
    5 -> "May"
    6 -> "Haz"
    7 -> "Tem"
    8 -> "Ağu"
    9 -> "Eyl"
    10 -> "Eki"
    11 -> "Kas"
    12 -> "Ara"
    else -> ""
}
