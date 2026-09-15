package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.feniqo.mobile.presentation.dashboard.TopExpenseCategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoExpense
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoWarning
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * 1. Feniqo logosu, profil girişine bağlı avatar, kullanıcı adına göre selamlama,
 * "Ayına bir bakış" başlığı, aktif çalışma alanı hapı ve gösterilen ay.
 */
@Composable
fun DashboardHeader(
    formattedMonth: String,
    modifier: Modifier = Modifier,
    userName: String = "Kullanıcı",
    activeWorkspaceName: String? = null,
    onProfileClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // Üst Satır: Sans-serif "feniqo" logo ve Profil Avatarı
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "feniqo",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = FeniqoSageGreen,
            )

            // Profil Avatarı: 42dp yuvarlak, adaçayı zemin ve kullanıcı baş harfi
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Profil ve hesap",
                        onClick = onProfileClick,
                    )
                    .semantics { contentDescription = "Profil ve hesap" },
                shape = CircleShape,
                color = Color(0xFFE8F1EC),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initialLetter = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                    Text(
                        text = initialLetter,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FeniqoSageGreen,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Karşılama: "Merhaba, Ayşe"
        Text(
            text = "Merhaba, $userName",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = Color(0xFF64748B),
            ),
        )

        // Büyük Başlık: "Ayına bir bakış"
        Text(
            text = "Ayına bir bakış",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                letterSpacing = (-0.3).sp,
            ),
            color = Color(0xFF0F172A),
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Alt Satır: Çalışma alanı hapı ve Gösterilen Ay
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF334155),
                    )
                    Text(
                        text = activeWorkspaceName?.takeIf { it.isNotBlank() } ?: "Kişisel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = Color(0xFF334155),
                    )
                }
            }

            Text(
                text = formattedMonth,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569),
                ),
            )
        }
    }
}

/**
 * 3. Grafit Aylık Özet Kartı:
 * - "Dönem neti" ve gerçek aylık net tutar (tabular)
 * - "Bu ayın işlem özeti"
 * - Altında eşit genişlikte Gelir ve Gider kutucukları
 */
@Composable
fun GraphiteSummaryCard(
    summary: MonthlySummaryDisplayModel,
    summaryCurrencyCode: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF263238),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Sağ üst köşede zarif soyut dalga çizgileri
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val waveColor = Color.White.copy(alpha = 0.05f)
                val stroke = Stroke(width = 1.5.dp.toPx())
                drawCircle(
                    color = waveColor,
                    radius = w * 0.45f,
                    center = Offset(w * 0.95f, h * 0.1f),
                    style = stroke,
                )
                drawCircle(
                    color = waveColor,
                    radius = w * 0.65f,
                    center = Offset(w * 0.95f, h * 0.1f),
                    style = stroke,
                )
                drawCircle(
                    color = waveColor,
                    radius = w * 0.85f,
                    center = Offset(w * 0.95f, h * 0.1f),
                    style = stroke,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                // "Dönem neti" ve döviz kodu (TRY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dönem neti",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.75f),
                    )

                    Text(
                        text = summaryCurrencyCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }

                // Gerçek Dönem Net Tutarı
                Text(
                    text = summary.formattedBalance,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        letterSpacing = (-0.5).sp,
                    ).merge(FeniqoTabularNumberStyle),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Alt Başlık: "Bu ayın işlem özeti"
                Text(
                    text = "Bu ayın işlem özeti",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.65f),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Eşit Genişlikte Gelir ve Gider Alanları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Gelir Kutucuğu
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val mintGreen = Color(0xFF34D399)
                                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    // Gelen ok (aşağı doğru ok + tepsi)
                                    val tray = Path().apply {
                                        moveTo(size.width * 0.2f, size.height * 0.6f)
                                        lineTo(size.width * 0.2f, size.height * 0.85f)
                                        lineTo(size.width * 0.8f, size.height * 0.85f)
                                        lineTo(size.width * 0.8f, size.height * 0.6f)
                                    }
                                    drawPath(tray, color = mintGreen, style = stroke)
                                    val arrow = Path().apply {
                                        moveTo(size.width * 0.5f, size.height * 0.15f)
                                        lineTo(size.width * 0.5f, size.height * 0.65f)
                                        moveTo(size.width * 0.3f, size.height * 0.45f)
                                        lineTo(size.width * 0.5f, size.height * 0.65f)
                                        lineTo(size.width * 0.7f, size.height * 0.45f)
                                    }
                                    drawPath(arrow, color = mintGreen, style = stroke)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Gelir",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = summary.formattedIncome,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    ).merge(FeniqoTabularNumberStyle),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Gider Kutucuğu
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val coralColor = Color(0xFFFB923C)
                                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    // Giden ok (yukarı doğru ok + tepsi)
                                    val tray = Path().apply {
                                        moveTo(size.width * 0.2f, size.height * 0.6f)
                                        lineTo(size.width * 0.2f, size.height * 0.85f)
                                        lineTo(size.width * 0.8f, size.height * 0.85f)
                                        lineTo(size.width * 0.8f, size.height * 0.6f)
                                    }
                                    drawPath(tray, color = coralColor, style = stroke)
                                    val arrow = Path().apply {
                                        moveTo(size.width * 0.5f, size.height * 0.65f)
                                        lineTo(size.width * 0.5f, size.height * 0.15f)
                                        moveTo(size.width * 0.3f, size.height * 0.35f)
                                        lineTo(size.width * 0.5f, size.height * 0.15f)
                                        lineTo(size.width * 0.7f, size.height * 0.35f)
                                    }
                                    drawPath(arrow, color = coralColor, style = stroke)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Gider",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = summary.formattedExpense,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    ).merge(FeniqoTabularNumberStyle),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. Tasarruf oranı ve ince ilerleme göstergesi:
 * - "Tasarruf oranı" ve formattedSavingsRate
 * - İnce yeşil çubuk
 * - "Aylık gelire göre"
 */
@Composable
fun SavingsRateSection(
    summary: MonthlySummaryDisplayModel,
    modifier: Modifier = Modifier,
) {
    val progressRatio = if (summary.savingsRateBasisPoints > 0) {
        (summary.savingsRateBasisPoints / 10000f).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tasarruf oranı",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = Color(0xFF0F172A),
            )

            Text(
                text = summary.formattedSavingsRate,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ).merge(FeniqoTabularNumberStyle),
                color = Color(0xFF0F172A),
            )
        }

        // İnce İlerleme Çubuğu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE2E8F0)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressRatio)
                    .background(FeniqoSageGreen, RoundedCornerShape(3.dp)),
            )
        }

        Text(
            text = "Aylık gelire göre",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Color(0xFF64748B),
        )
    }
}

/**
 * 5. Bütçelerin Bölümü:
 * Kategori ikonları, harcanan/limit tutarları ve ilerleme çubuklarıyla bütçe özeti.
 */
@Composable
fun HomeBudgetsSection(
    items: List<DashboardBudgetProgressItem>,
    onViewAllBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Bütçelerin",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = Color(0xFF0F172A),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (items.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onViewAllBudgets),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Bu ay için henüz bütçe belirlenmedi",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                            )
                            Text(
                                text = "+ Bütçe Ekle",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoSageGreen,
                            )
                        }
                    }
                } else {
                    items.forEachIndexed { index, budget ->
                        HomeBudgetItemRow(
                            budget = budget,
                            onBudgetClick = onViewAllBudgets,
                        )
                        if (index < items.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBudgetItemRow(
    budget: DashboardBudgetProgressItem,
    onBudgetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(budget.categoryColorHex)
        ?: FeniqoSageGreen

    val isOverLimit = budget.progressRatio > 1.0f
    val isNearLimit = budget.progressRatio >= 0.8f && !isOverLimit

    val barColor = when {
        isOverLimit -> FeniqoExpense
        isNearLimit -> FeniqoWarning
        else -> categoryColor
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBudgetClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryTonalIcon(
                iconKey = budget.categoryIconKey,
                color = categoryColor,
                containerSize = 40.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = budget.categoryName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isNearLimit) {
                    Text(
                        text = "Limite yaklaşıyor",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = FeniqoWarning,
                    )
                } else if (isOverLimit) {
                    Text(
                        text = "Bütçe aşıldı",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = FeniqoExpense,
                    )
                }
            }

            Text(
                text = "${budget.formattedSpent} / ${budget.formattedLimit}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ).merge(FeniqoTabularNumberStyle),
                color = Color(0xFF0F172A),
            )
        }

        // İlerleme Çubuğu ve Sağda Yüzde
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE2E8F0)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = budget.progressRatio.coerceIn(0f, 1f))
                        .background(barColor, RoundedCornerShape(3.dp)),
                )
            }

            Text(
                text = "%${(budget.progressRatio * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                ).merge(FeniqoTabularNumberStyle),
            )
        }
    }
}

/**
 * 6. "Son işlemler" ve "Tümü" bağlantısı:
 * - Gider tutarları okunaklı kırmızı (`#DC2626` / `FeniqoExpense`), eksi işaretli: −₺486,50
 * - Gelir tutarları yeşil (`#16A34A`), artı işaretli: +₺45.000
 * - İşlem adı ve açıklamaları kırmızıya boyanmaz
 * - Kategori ikonları orijinal renklerini korur
 */
@Composable
fun HomeRecentTransactionsSection(
    recentTransactions: List<TransactionDisplayModel>,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (EntityId) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Son işlemler",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = Color(0xFF0F172A),
            )

            Text(
                text = "Tümü",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = FeniqoSageGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewAllTransactions)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (recentTransactions.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddTransaction),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Henüz bu aya ait işlem bulunmuyor",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                            )
                            Text(
                                text = "+ İlk İşlemini Ekle",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoSageGreen,
                            )
                        }
                    }
                } else {
                    val itemsToShow = recentTransactions.take(3)
                    itemsToShow.forEachIndexed { index, trx ->
                        HomeTransactionItemRow(
                            trx = trx,
                            onClick = { onTransactionClick(trx.id) },
                        )
                        if (index < itemsToShow.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTransactionItemRow(
    trx: TransactionDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(trx.categoryColorHex)
        ?: FeniqoSageGreen

    // Onaylı Renk Kuralı: Giderler okunaklı kırmızı, gelirler yeşil
    val isExpense = trx.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) Color(0xFFDC2626) else Color(0xFF16A34A)

    // Eksi veya artı işaretini güvenle hazırla
    val formattedDisplayAmount = if (isExpense) {
        val raw = trx.formattedAmount
        if (raw.startsWith("-")) {
            "−${raw.substring(1)}"
        } else if (!raw.startsWith("−")) {
            "−$raw"
        } else {
            raw
        }
    } else {
        val raw = trx.formattedAmount
        if (!raw.startsWith("+")) "+$raw" else raw
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTonalIcon(
            iconKey = trx.categoryIconKey,
            color = categoryColor,
            containerSize = 40.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = trx.description?.takeIf { it.isNotBlank() } ?: trx.categoryName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${trx.categoryName} • ${DateFormatter.formatReadableDate(trx.transactionDate)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = formattedDisplayAmount,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ).merge(FeniqoTabularNumberStyle),
            color = amountColor,
            maxLines = 1,
        )
    }
}

/**
 * 7. Gerçek veriden üretilen Feniqo İçgörü kartı:
 * Parıltı ikonu (✦ ✦) ve nane tonlu hafif zemin.
 */
@Composable
fun HomeInsightCard(
    insight: DashboardInsightModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // İki parıltılı yıldız ikonu
            Canvas(modifier = Modifier.size(24.dp)) {
                val sparkColor = Color(0xFF2D5A43)
                // Büyük parıltı
                val p1 = Path().apply {
                    val cx = size.width * 0.45f
                    val cy = size.height * 0.5f
                    val r = size.width * 0.38f
                    moveTo(cx, cy - r)
                    cubicTo(cx, cy - r * 0.3f, cx + r * 0.3f, cy, cx + r, cy)
                    cubicTo(cx + r * 0.3f, cy, cx, cy + r * 0.3f, cx, cy + r)
                    cubicTo(cx, cy + r * 0.3f, cx - r * 0.3f, cy, cx - r, cy)
                    cubicTo(cx - r * 0.3f, cy, cx, cy - r * 0.3f, cx, cy - r)
                }
                drawPath(p1, color = sparkColor)
                // Küçük parıltı (sağ üst)
                val p2 = Path().apply {
                    val cx = size.width * 0.82f
                    val cy = size.height * 0.22f
                    val r = size.width * 0.18f
                    moveTo(cx, cy - r)
                    cubicTo(cx, cy - r * 0.3f, cx + r * 0.3f, cy, cx + r, cy)
                    cubicTo(cx + r * 0.3f, cy, cx, cy + r * 0.3f, cx, cy + r)
                    cubicTo(cx, cy + r * 0.3f, cx - r * 0.3f, cy, cx - r, cy)
                    cubicTo(cx - r * 0.3f, cy, cx, cy - r * 0.3f, cx, cy - r)
                }
                drawPath(p2, color = sparkColor)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = Color(0xFF1B4332),
                )

                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = Color(0xFF1E293B),
                )
            }
        }
    }
}

/**
 * 8. Mevcut aktif aboneliklerden yaklaşan ödemeler:
 * Gün/Ay rozeti, makbuz ikonu, başlık, "Abonelik", tutar ve chevron `>`.
 */
@Composable
fun HomeUpcomingPaymentsSection(
    upcomingBills: List<DashboardUpcomingBillItem>,
    onSubscriptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Yaklaşan ödemeler",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = Color(0xFF0F172A),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (upcomingBills.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSubscriptionsClick),
                    ) {
                        Text(
                            text = "Yaklaşan ödeme bulunmuyor",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    upcomingBills.forEachIndexed { index, bill ->
                        HomeUpcomingBillRow(
                            bill = bill,
                            onClick = onSubscriptionsClick,
                        )
                        if (index < upcomingBills.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeUpcomingBillRow(
    bill: DashboardUpcomingBillItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tarih Rozeti: Gün + EYL
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFEDF4F0),
            modifier = Modifier.size(width = 38.dp, height = 44.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = bill.dayNumber.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ).merge(FeniqoTabularNumberStyle),
                    color = Color(0xFF0F172A),
                )
                Text(
                    text = bill.monthShort.ifBlank { "AY" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    ),
                    color = Color(0xFF475569),
                )
            }
        }

        // Fatura / Makbuz İkonu
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = Color(0xFF334155),
            modifier = Modifier.size(20.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = bill.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Abonelik",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFF64748B),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = bill.formattedAmount,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ).merge(FeniqoTabularNumberStyle),
                color = Color(0xFF0F172A),
            )

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 9. Mevcut veride varsa birikim hedefi kartı:
 * Hedef ikonu, hedef adı, tutarlar, yeşil ilerleme çubuğu ve % tamamlandı metni.
 */
@Composable
fun HomeSavingsGoalSection(
    goal: DashboardSavingsGoalItem,
    onGoalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Birikim hedefin",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = Color(0xFF0F172A),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGoalClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hedef / Bullseye ikonu
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFE8F5E9), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val stroke = Stroke(width = 2.dp.toPx())
                        val green = Color(0xFF2E7D32)
                        drawCircle(color = green, radius = size.width * 0.44f, style = stroke)
                        drawCircle(color = green, radius = size.width * 0.22f, style = stroke)
                        drawCircle(color = green, radius = size.width * 0.08f)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = goal.goalName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            text = "${goal.formattedCurrent} / ${goal.formattedTarget}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ).merge(FeniqoTabularNumberStyle),
                            color = Color(0xFF0F172A),
                        )
                    }

                    // Yeşil İlerleme Çubuğu
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE2E8F0)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = goal.progressRatio.coerceIn(0f, 1f))
                                .background(FeniqoSageGreen, RoundedCornerShape(3.dp)),
                        )
                    }

                    Text(
                        text = "%${(goal.progressRatio * 100).toInt()} tamamlandı",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF64748B),
                    )
                }
            }
        }
    }
}

/**
 * 10. MoneyScore ve görünür “Ön değerlendirme” açıklaması:
 * Sol tarafta dairesel arc göstergesi, sağda ön değerlendirme rozeti ve finansal görünüm metni.
 */
@Composable
fun HomeMoneyScoreSection(
    moneyScore: MoneyScoreDisplayModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "MoneyScore",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = Color(0xFF0F172A),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Dairesel Gauge Göstergesi
                    Box(
                        modifier = Modifier.size(105.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            val strokeWidth = 10.dp.toPx()
                            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            val startAngle = 150f
                            val sweepMax = 240f

                            // Gri Zemin Yayı
                            drawArc(
                                color = Color(0xFFE2E8F0),
                                startAngle = startAngle,
                                sweepAngle = sweepMax,
                                useCenter = false,
                                style = stroke,
                            )

                            // Yeşil İlerleme Yayı
                            val scoreFraction = (moneyScore.totalScore / 100f).coerceIn(0f, 1f)
                            if (scoreFraction > 0f) {
                                drawArc(
                                    color = Color(0xFF2D5A43),
                                    startAngle = startAngle,
                                    sweepAngle = sweepMax * scoreFraction,
                                    useCenter = false,
                                    style = stroke,
                                )
                            }
                        }

                        // Ortadaki Skor Sayısı
                        Text(
                            text = "${moneyScore.totalScore}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                            ).merge(FeniqoTabularNumberStyle),
                            color = Color(0xFF0F172A),
                        )
                    }

                    // Sağ Kolon: "Ön değerlendirme" hapı ve "Finansal görünümün"
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F1EC),
                        ) {
                            Text(
                                text = "Ön değerlendirme",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                color = Color(0xFF2D5A43),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        Text(
                            text = "Finansal görünümün",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = Color(0xFF0F172A),
                        )
                    }
                }

                // Açıklama Metni
                Text(
                    text = moneyScore.explanationText.takeIf { it.isNotBlank() }
                        ?: "Bu puan ön değerlendirmedir; bazı bileşenler nötr değerlerle hesaplanır.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    color = Color(0xFF64748B),
                )
            }
        }
    }
}

/**
 * 11. Para birimi kapsam bilgisi kartı:
 * Farklı para birimindeki işlemlerin TL özetine dahil edilmediğini açıklar.
 */
@Composable
fun CurrencyScopeNoticeCard(
    count: Int,
    currencyCode: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = "$count farklı para birimindeki işlem $currencyCode özetine dahil edilmedi.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                ),
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Geriye dönük uyumluluk için korunan bileşenler */
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
        shape = RoundedCornerShape(16.dp),
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

@Composable
fun TopExpenseCategorySection(
    topExpenseCategory: TopExpenseCategoryDisplayModel,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(topExpenseCategory.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
