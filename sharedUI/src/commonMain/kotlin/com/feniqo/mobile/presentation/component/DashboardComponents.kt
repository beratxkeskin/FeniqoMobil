package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.dashboard.BudgetAlertDisplayModel
import com.feniqo.mobile.presentation.dashboard.DashboardBudgetProgressItem
import com.feniqo.mobile.presentation.dashboard.DashboardInsightModel
import com.feniqo.mobile.presentation.dashboard.DashboardSavingsGoalItem
import com.feniqo.mobile.presentation.dashboard.DashboardUpcomingBillItem
import com.feniqo.mobile.presentation.dashboard.MoneyScoreDisplayModel
import com.feniqo.mobile.presentation.dashboard.MonthlySummaryDisplayModel
import com.feniqo.mobile.presentation.dashboard.NetBalanceStatus
import com.feniqo.mobile.presentation.dashboard.TopExpenseCategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * Dashboard üst başlık ve selamlama bileşeni (Zen / Modern Estetik).
 */
@Composable
fun DashboardHeader(
    formattedMonth: String,
    modifier: Modifier = Modifier,
    userName: String = "Sarah",
    activeWorkspaceName: String? = null,
    onProfileClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // 1. Logo ve Üst Aksiyonlar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Şık Serif Feniqo Logo
            Text(
                text = "Feniqo",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                if (activeWorkspaceName != null) {
                    ActiveWorkspaceIndicator(workspaceName = activeWorkspaceName, isCompact = true)
                }

                // Bildirim Zili (Kırmızı rozetli)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Bildirimler", onClick = onNotificationClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val stroke = Stroke(width = 1.8.dp.toPx())
                        val bellPath = Path().apply {
                            moveTo(size.width * 0.5f, size.height * 0.15f)
                            cubicTo(size.width * 0.35f, size.height * 0.15f, size.width * 0.22f, size.height * 0.35f, size.width * 0.22f, size.height * 0.55f)
                            lineTo(size.width * 0.15f, size.height * 0.72f)
                            lineTo(size.width * 0.85f, size.height * 0.72f)
                            lineTo(size.width * 0.78f, size.height * 0.55f)
                            cubicTo(size.width * 0.78f, size.height * 0.35f, size.width * 0.65f, size.height * 0.15f, size.width * 0.5f, size.height * 0.15f)
                        }
                        drawPath(bellPath, color = Color(0xFF1E293B), style = stroke)
                        drawCircle(color = Color(0xFF1E293B), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.84f))
                    }
                    // Kırmızı bildirim noktası
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                            .background(Color(0xFFEF4444), CircleShape),
                    )
                }

                // Profil Avatarı
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Profil ve hesap", onClick = onProfileClick)
                        .semantics { contentDescription = "Profil ve hesap" },
                    shape = CircleShape,
                    color = Color(0xFFE2E8F0),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(36.dp)) {
                            drawCircle(color = Color(0xFF94A3B8).copy(alpha = 0.3f))
                            drawCircle(color = Color(0xFF64748B), radius = size.width * 0.22f, center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.38f))
                            val shoulders = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.95f)
                                cubicTo(size.width * 0.2f, size.height * 0.65f, size.width * 0.8f, size.height * 0.65f, size.width * 0.85f, size.height * 0.95f)
                            }
                            drawPath(shoulders, color = Color(0xFF64748B))
                        }
                    }
                }
            }
        }

        // 2. Selamlama ve Motto
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Günaydın, $userName ☀️",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Küçük adımlar. Daha aydınlık bir yarın.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Beyaz yuvarlatılmış Toplam Bakiye & Bézier Alan Grafiği Kartı (Zen Mockup).
 */
@Composable
fun TotalBalanceCard(
    summary: MonthlySummaryDisplayModel,
    modifier: Modifier = Modifier,
    trendPercentage: String = "+%12",
    trendDifference: String = "+₺460 geçen aydan bu yana",
    onTimeframeClick: () -> Unit = {},
) {
    var isBalanceHidden by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.LargePlus),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            // Başlık & Göz İkonu + Dönem Seçici
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Text(
                        text = "Toplam bakiye",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Bakiye Gizle/Göster Butonu
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = if (isBalanceHidden) "Bakiyeyi göster" else "Bakiyeyi gizle") {
                                isBalanceHidden = !isBalanceHidden
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val stroke = Stroke(width = 1.6.dp.toPx())
                            val eyePath = Path().apply {
                                moveTo(size.width * 0.1f, size.height * 0.5f)
                                cubicTo(size.width * 0.3f, size.height * 0.15f, size.width * 0.7f, size.height * 0.15f, size.width * 0.9f, size.height * 0.5f)
                                cubicTo(size.width * 0.7f, size.height * 0.85f, size.width * 0.3f, size.height * 0.85f, size.width * 0.1f, size.height * 0.5f)
                            }
                            drawPath(eyePath, color = Color(0xFF64748B), style = stroke)
                            drawCircle(color = Color(0xFF64748B), radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f))
                        }
                    }
                }

                // "Bu ay ∨" Seçici Hapı
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F2),
                    modifier = Modifier.clickable(onClick = onTimeframeClick),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Bu ay",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155),
                        )
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val arrow = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.35f)
                                lineTo(size.width * 0.5f, size.height * 0.7f)
                                lineTo(size.width * 0.8f, size.height * 0.35f)
                            }
                            drawPath(arrow, color = Color(0xFF64748B), style = Stroke(width = 1.5.dp.toPx()))
                        }
                    }
                }
            }

            // Bakiye Tutarı
            Text(
                text = if (isBalanceHidden) "••••••••" else summary.formattedBalance,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Trend Rozeti & Geçen Aya Göre Fark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDCFCE7),
                ) {
                    Text(
                        text = "↑ $trendPercentage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = trendDifference,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }

            Spacer(Modifier.height(FeniqoSpacing.Small))

            // Bézier Alan Grafiği (Sparkline + Tooltip)
            ZenBalanceAreaChart(
                tooltipAmount = if (isBalanceHidden) "••••" else summary.formattedBalance,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )

            // X Ekseni Tarihleri
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("1 Ağu", "8 Ağu", "15 Ağu", "22 Ağu", "31 Ağu").forEach { dateText ->
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF94A3B8),
                    )
                }
            }
        }
    }
}

/**
 * Bézier eğrisi ve altı degrade dolgulu alan grafiği.
 */
@Composable
private fun ZenBalanceAreaChart(
    tooltipAmount: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bottom = h - 8.dp.toPx()

            // Dalgalı çizgi rotası
            val linePath = Path().apply {
                moveTo(0f, bottom * 0.85f)
                cubicTo(w * 0.15f, bottom * 0.82f, w * 0.22f, bottom * 0.65f, w * 0.35f, bottom * 0.60f)
                cubicTo(w * 0.45f, bottom * 0.55f, w * 0.52f, bottom * 0.35f, w * 0.62f, bottom * 0.45f)
                cubicTo(w * 0.72f, bottom * 0.55f, w * 0.80f, bottom * 0.18f, w, bottom * 0.15f)
            }

            // Alt degrade dolgu
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(w, bottom)
                lineTo(0f, bottom)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D5A43).copy(alpha = 0.22f),
                        Color(0xFF2D5A43).copy(alpha = 0.02f),
                    ),
                    startY = 0f,
                    endY = bottom,
                ),
            )

            // Ana çizgi
            drawPath(
                path = linePath,
                color = Color(0xFF386641),
                style = Stroke(width = 2.4.dp.toPx()),
            )

            // Uç tepe noktası dairesi
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(w, bottom * 0.15f),
            )
            drawCircle(
                color = Color(0xFF386641),
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(w, bottom * 0.15f),
            )
        }

        // Sağ uçtaki Tooltip Balonu
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF386641),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-4).dp),
        ) {
            Text(
                text = tooltipAmount,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 3'lü Özet Kartları: Gelir, Harcama, Birikim (Zen Mockup uyumlu).
 */
@Composable
fun MonthlySummaryGrid(
    summary: MonthlySummaryDisplayModel,
    modifier: Modifier = Modifier,
    incomeTrend: String = "+%8",
    expenseTrend: String = "-%14",
    savedTrend: String = "+%22",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // 1. Gelir Kartı
        ZenSummaryCard(
            title = "Gelir",
            amount = summary.formattedIncome,
            trendText = "↑ $incomeTrend",
            trendColor = Color(0xFF16A34A),
            iconBgColor = Color(0xFFDCFCE7),
            iconContent = {
                Canvas(Modifier.size(16.dp)) {
                    val arrow = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.2f, size.height * 0.45f)
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.8f, size.height * 0.45f)
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.5f, size.height * 0.85f)
                    }
                    drawPath(arrow, color = Color(0xFF16A34A), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
            },
            modifier = Modifier.weight(1f),
        )

        // 2. Harcama Kartı
        ZenSummaryCard(
            title = "Harcama",
            amount = summary.formattedExpense,
            trendText = "↓ $expenseTrend",
            trendColor = Color(0xFFDC2626),
            iconBgColor = Color(0xFFFEE2E2),
            iconContent = {
                Canvas(Modifier.size(16.dp)) {
                    val arrow = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.85f)
                        lineTo(size.width * 0.2f, size.height * 0.55f)
                        moveTo(size.width * 0.5f, size.height * 0.85f)
                        lineTo(size.width * 0.8f, size.height * 0.55f)
                        moveTo(size.width * 0.5f, size.height * 0.85f)
                        lineTo(size.width * 0.5f, size.height * 0.15f)
                    }
                    drawPath(arrow, color = Color(0xFFDC2626), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
            },
            modifier = Modifier.weight(1f),
        )

        // 3. Birikim Kartı
        ZenSummaryCard(
            title = "Birikim",
            amount = summary.formattedBalance,
            trendText = "↑ $savedTrend",
            trendColor = Color(0xFF16A34A),
            iconBgColor = Color(0xFFF3E8FF),
            iconContent = {
                Canvas(Modifier.size(16.dp)) {
                    // Sevimli kumbara / tasarruf ikonu
                    val stroke = Stroke(width = 1.8.dp.toPx())
                    drawCircle(color = Color(0xFF9333EA), radius = size.width * 0.38f, center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.52f), style = stroke)
                    drawLine(color = Color(0xFF9333EA), start = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.38f), end = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.38f), strokeWidth = 1.8.dp.toPx())
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Zen stili beyaz metrik kartı.
 */
@Composable
private fun ZenSummaryCard(
    title: String,
    amount: String,
    trendText: String,
    trendColor: Color,
    iconBgColor: Color,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                iconContent()
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = trendText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                ),
                color = trendColor,
            )
        }
    }
}

/**
 * Bütçeler Bölümü: Kategori ilerleme çubukları ile bütçe durumu (Zen Mockup uyumlu).
 */
@Composable
fun ZenBudgetsSection(
    items: List<DashboardBudgetProgressItem>,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // Başlık ve "Tümünü gör >"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bütçeler",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    color = Color(0xFF0F172A),
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onViewAllClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Tümünü gör",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF64748B),
                    )
                    Canvas(Modifier.size(12.dp)) {
                        val arrow = Path().apply {
                            moveTo(size.width * 0.35f, size.height * 0.2f)
                            lineTo(size.width * 0.65f, size.height * 0.5f)
                            lineTo(size.width * 0.35f, size.height * 0.8f)
                        }
                        drawPath(arrow, color = Color(0xFF64748B), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }

            // Bütçe Satırları
            if (items.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAF9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onViewAllClick),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Bu ay için henüz bütçe belirlenmedi",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                        Text(
                            text = "+ Bütçe Ekle",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                for (budget in items) {
                    ZenBudgetItemRow(budget = budget)
                }
            }
        }
    }
}

@Composable
private fun ZenBudgetItemRow(
    budget: DashboardBudgetProgressItem,
    modifier: Modifier = Modifier,
) {
    val barColor = ColorParser.parseHexColorOrNull(budget.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        // Projedeki hazır Semantik Kategori İkonu
        CategoryTonalIcon(
            iconKey = budget.categoryIconKey,
            color = barColor,
            containerSize = 36.dp,
        )

        // Kategori Adı
        Text(
            text = budget.categoryName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF1E293B),
            modifier = Modifier.width(105.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // İnce Renkli İlerleme Çubuğu (Bar)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(Color(0xFFF1F5F2)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = budget.progressRatio.coerceIn(0f, 1f))
                    .background(barColor, RoundedCornerShape(3.5.dp)),
            )
        }

        // Tutar Metni (Örn: ₺320 / ₺500)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = budget.formattedSpent,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A),
            )
            Text(
                text = " / ${budget.formattedLimit}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
            )
        }
    }
}

/**
 * Feniqo İçgörü Banner Kartı (Zen Mockup uyumlu).
 */
@Composable
fun ZenInsightBanner(
    modifier: Modifier = Modifier,
    title: String = "Feniqo İçgörü",
    message: String = "Bu ay yeme-içmeye %18 daha az harcadın. Harika ilerleme!",
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFBF8F2),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Parıltı İkonu (✦)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFFEF3C7), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        val sparkPath = Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            cubicTo(size.width * 0.5f, size.height * 0.35f, size.width * 0.65f, size.height * 0.5f, size.width, size.height * 0.5f)
                            cubicTo(size.width * 0.65f, size.height * 0.5f, size.width * 0.5f, size.height * 0.65f, size.width * 0.5f, size.height)
                            cubicTo(size.width * 0.5f, size.height * 0.65f, size.width * 0.35f, size.height * 0.5f, 0f, size.height * 0.5f)
                            cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.5f, size.height * 0.35f, size.width * 0.5f, 0f)
                        }
                        drawPath(sparkPath, color = Color(0xFFD97706))
                    }
                }

                // Başlık & Tavsiye Metni
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706),
                        ),
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
                    )
                }
            }

            // Sağ Taraf: Botanik Dal Motifi ve Ok Butonu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                // Minimalist Botanik Yaprak Çizimi
                Canvas(Modifier.size(36.dp)) {
                    val leafGreen = Color(0xFF4A7C59).copy(alpha = 0.45f)
                    val stem = Path().apply {
                        moveTo(size.width * 0.8f, size.height * 0.9f)
                        cubicTo(size.width * 0.5f, size.height * 0.7f, size.width * 0.4f, size.height * 0.4f, size.width * 0.3f, size.height * 0.1f)
                    }
                    drawPath(stem, color = leafGreen, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                    drawOval(color = leafGreen, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.15f), size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 7.dp.toPx()))
                    drawOval(color = leafGreen, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.4f), size = androidx.compose.ui.geometry.Size(11.dp.toPx(), 6.dp.toPx()))
                }

                // Sağ Ok Yuvarlak Buton
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF1EDE4),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(10.dp)) {
                            val arrow = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.15f)
                                lineTo(size.width * 0.7f, size.height * 0.5f)
                                lineTo(size.width * 0.3f, size.height * 0.85f)
                            }
                            drawPath(arrow, color = Color(0xFFD97706), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

/**
 * İki Sütunlu / Yan Yana Alt Bölüm: Son İşlemler & Yaklaşan Faturalar / Hedef (Zen Mockup uyumlu).
 */
@Composable
fun ZenRecentAndUpcomingSection(
    recentTransactions: List<TransactionDisplayModel>,
    upcomingBills: List<DashboardUpcomingBillItem>,
    savingsGoal: DashboardSavingsGoalItem?,
    onViewAllTransactions: () -> Unit,
    onViewAllBills: () -> Unit,
    onTransactionClick: (EntityId) -> Unit,
    onGoalClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        // Sol Sütun: Son İşlemler
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Son işlemler",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Tümünü gör >",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onViewAllTransactions),
                    )
                }

                val itemsToShow = if (recentTransactions.isNotEmpty()) {
                    recentTransactions.take(3)
                } else {
                    emptyList()
                }

                if (recentTransactions.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onViewAllTransactions),
                    ) {
                        Text(
                            text = "Henüz işlem kaydedilmedi",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(FeniqoSpacing.Medium),
                        )
                    }
                } else {
                    val itemsToShow = recentTransactions.take(3)
                    for (trx in itemsToShow) {
                        val categoryColor = ColorParser.parseHexColorOrNull(trx.categoryColorHex)
                            ?: MaterialTheme.colorScheme.primary

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTransactionClick(trx.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                CategoryTonalIcon(
                                    iconKey = trx.categoryIconKey,
                                    color = categoryColor,
                                    containerSize = 32.dp,
                                )
                                Column {
                                    Text(
                                        text = trx.categoryName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = trx.description?.takeIf { it.isNotBlank() }
                                            ?: trx.formattedAmount,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = trx.formattedAmount,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (trx.type == TransactionType.INCOME) Color(0xFF16A34A) else Color(0xFF0F172A),
                            )
                        }
                    }
                }
            }
        }

        // Sağ Sütun: Yaklaşan Faturalar & Birikim Hedefi
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Yaklaşan faturalar",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Tümünü gör >",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onViewAllBills),
                    )
                }

                // Fatura Satırları (Gerçek Abonelik / Tekrarlayan Verileri)
                if (upcomingBills.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onViewAllBills),
                    ) {
                        Text(
                            text = "Yaklaşan fatura bulunmuyor",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(FeniqoSpacing.Medium),
                        )
                    }
                } else {
                    for (bill in upcomingBills) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CategoryTonalIcon(
                                    iconKey = bill.iconKey ?: "subscriptions",
                                    color = Color(0xFF9D7AE2),
                                    containerSize = 32.dp,
                                )
                                Column {
                                    Text(
                                        text = bill.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = bill.formattedDueDate,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color(0xFF64748B),
                                    )
                                }
                            }
                            Text(
                                text = bill.formattedAmount,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A),
                            )
                        }
                    }
                }

                // Birikim Hedefi Kartı (Gerçek Hedef veya Hedef Ekleme Boş Durumu)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF2F9F4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onGoalClick),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            CategoryTonalIcon(
                                iconKey = "travel",
                                color = Color(0xFF16A34A),
                                containerSize = 28.dp,
                            )
                            Column {
                                Text(
                                    text = if (savingsGoal != null) "Birikim Hedefi" else "Hedef Belirle",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF64748B),
                                )
                                Text(
                                    text = savingsGoal?.goalName ?: "+ Yeni Hedef Ekle",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (savingsGoal != null) {
                                    Text(
                                        text = "${savingsGoal.formattedCurrent} / ${savingsGoal.formattedTarget}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A),
                                        ),
                                    )
                                }
                            }
                        }
                        Canvas(Modifier.size(10.dp)) {
                            val arrow = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.15f)
                                lineTo(size.width * 0.7f, size.height * 0.5f)
                                lineTo(size.width * 0.3f, size.height * 0.85f)
                            }
                            drawPath(arrow, color = Color(0xFF16A34A), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}


/**
 * Ayın en yüksek harcama kategorisi kartı.
 */
@Composable
fun TopExpenseCategorySection(
    topExpenseCategory: TopExpenseCategoryDisplayModel,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(topExpenseCategory.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(categoryColor.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(categoryColor, CircleShape),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "En Yüksek Gider",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = topExpenseCategory.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = topExpenseCategory.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = topExpenseCategory.formattedTransactionCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * MoneyScore Finansal Sağlık Puanı kartı.
 */
@Composable
fun MoneyScoreSection(
    moneyScore: MoneyScoreDisplayModel,
    modifier: Modifier = Modifier,
) {
    val levelBadgeColor = when (moneyScore.level) {
        MoneyScoreLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MoneyScoreLevel.HEALTHY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MoneyScoreLevel.EXCELLENT -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // Başlık ve Seviye Rozeti
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "MoneyScore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Finansal Sağlık Durumu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (moneyScore.isProvisional) {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "Ön değerlendirme",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 4.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(FeniqoRadius.Small),
                        color = levelBadgeColor.first,
                    ) {
                        Text(
                            text = moneyScore.formattedLevel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = levelBadgeColor.second,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 4.dp),
                        )
                    }
                }
            }

            // Toplam Skor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${moneyScore.totalScore} / 100",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Alt Skor Çubukları
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                ScoreProgressRow(label = "Tasarruf", current = moneyScore.savingsScore, max = 30)
                ScoreProgressRow(label = "Bütçe Disiplini", current = moneyScore.budgetScore, max = 30)
                ScoreProgressRow(label = "Borç Yönetimi", current = moneyScore.debtScore, max = 20)
                ScoreProgressRow(label = "Hedefler", current = moneyScore.goalScore, max = 20)
            }

            if (moneyScore.explanationText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = moneyScore.explanationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(FeniqoSpacing.Small),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreProgressRow(
    label: String,
    current: Int,
    max: Int,
    modifier: Modifier = Modifier,
) {
    val progress = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$current / $max",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * Bütçe aşım / uyarı banner bileşenidir.
 */
@Composable
fun BudgetAlertBanner(
    alert: BudgetAlertDisplayModel,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (alert.isExceeded) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (alert.isExceeded) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
        ) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}

/**
 * Dashboard için tekil işlem satırı bileşenidir.
 */
@Composable
fun DashboardTransactionItem(
    item: TransactionDisplayModel,
    onClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(item.categoryColorHex)
        ?: MaterialTheme.colorScheme.surfaceVariant

    val amountColor = if (item.type == TransactionType.INCOME) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.categoryName} işlemini incele",
            ) {
                onClick(item.id)
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(categoryColor, CircleShape),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                item.installment?.let { inst ->
                    InstallmentBadge(badgeText = inst.badgeText)
                }

                Text(
                    text = item.formattedAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
            }
        }
    }
}
