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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.debt.DebtDisplayModel
import com.feniqo.mobile.presentation.debt.DebtDueStatus
import com.feniqo.mobile.presentation.debt.DebtInsightType
import com.feniqo.mobile.presentation.debt.DebtInsightUiModel
import com.feniqo.mobile.presentation.debt.DebtsSummaryUiModel
import com.feniqo.mobile.presentation.theme.FeniqoExpense
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground
import com.feniqo.mobile.presentation.theme.PhoenixGold
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Referans görseldeki ↗ (sağ üst) kırmızı çapraz ok ikonu.
 * Compose Material Icons'ta AutoMirrored ArrowTopRight bulunmadığı için Canvas ile pürüzsüz çizilir.
 */
@Composable
fun ArrowUpRightIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val arrow = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.75f)
            lineTo(size.width * 0.75f, size.height * 0.25f)
            moveTo(size.width * 0.40f, size.height * 0.25f)
            lineTo(size.width * 0.75f, size.height * 0.25f)
            lineTo(size.width * 0.75f, size.height * 0.60f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Referans görseldeki ↙ (sol alt) yeşil çapraz ok ikonu.
 * Compose Material Icons'ta AutoMirrored ArrowBottomLeft bulunmadığı için Canvas ile pürüzsüz çizilir.
 */
@Composable
fun ArrowDownLeftIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val arrow = Path().apply {
            moveTo(size.width * 0.75f, size.height * 0.25f)
            lineTo(size.width * 0.25f, size.height * 0.75f)
            moveTo(size.width * 0.60f, size.height * 0.75f)
            lineTo(size.width * 0.25f, size.height * 0.75f)
            lineTo(size.width * 0.25f, size.height * 0.40f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Referans Görsel 01'deki grafit "Net durum" özet kartıdır.
 * #303536 koyu yüzey, net durum renklendirmesi (negatif kırmızı, pozitif yeşil, nötr beyaz),
 * Borçlarım ve Alacaklarım toplamları, kayıt sayıları ve döviz kodu (TRY) içerir.
 */
@Composable
fun DebtsGraphiteSummaryCard(
    summary: DebtsSummaryUiModel,
    modifier: Modifier = Modifier,
) {
    val netAmountColor = when {
        summary.isNetNegative -> Color(0xFFEF4444)
        summary.isNetPositive -> Color(0xFF10B981)
        else -> Color.White
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF303536),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Net Durum Başlığı ve Büyük Tutar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Net durum",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = summary.formattedNetBalance,
                        style = MaterialTheme.typography.headlineMedium.merge(FeniqoTabularNumberStyle),
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = netAmountColor,
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF434A4C),
                    thickness = 1.dp,
                )

                // İki Eşit Kolon: Borçlarım & Alacaklarım
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Sol Kolon: Borçlarım
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Borçlarım",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = summary.formattedTotalDebt,
                            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Toplam ${summary.activeDebtCount} kayıt",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
                    }

                    // Sağ Kolon: Alacaklarım
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Alacaklarım",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = summary.formattedTotalReceivable,
                            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Toplam ${summary.activeReceivableCount} kayıt",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                            )
                            Text(
                                text = summary.baseCurrency.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // Çoklu para birimi güvenliği uyarısı
        if (summary.excludedCurrenciesCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = Color(0xFFF1EDE6),
                border = BorderStroke(1.dp, Color(0xFFE2DDD4)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = FeniqoTextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "${summary.excludedCurrenciesCount} kayıt farklı para biriminde (${summary.excludedCurrencies.joinToString { it.name }}) olduğu için bu toplama dahil edilmedi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Referans görseldeki 3'lü finansal özet kartları bileşeni (Borcunuz, Alacağınız, Net durum).
 * Çoklu para birimi güvenliğini korur; hariç tutulan para birimleri varsa uyarı rozeti gösterir.
 */
@Composable
fun DebtSummaryCardsRow(
    summary: DebtsSummaryUiModel,
    modifier: Modifier = Modifier,
    onDebtSummaryClick: (() -> Unit)? = null,
    onReceivableSummaryClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Toplam Borç Kartı (You owe)
            DebtSummaryCard(
                title = "Borcunuz",
                amount = summary.formattedTotalDebt,
                countText = if (summary.activeDebtCount > 0) "${summary.activeDebtCount} kişi" else "0 kişi",
                icon = {
                    ArrowUpRightIcon(
                        color = Color(0xFFDC2626),
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = Color(0xFFFDE8E8),
                containerColor = Color(0xFFFFF5F5),
                borderColor = Color(0xFFFFE4E1),
                onClick = onDebtSummaryClick,
                modifier = Modifier.weight(1f),
            )

            // 2. Toplam Alacak Kartı (Owed to you)
            DebtSummaryCard(
                title = "Alacağınız",
                amount = summary.formattedTotalReceivable,
                countText = if (summary.activeReceivableCount > 0) "${summary.activeReceivableCount} kişi" else "0 kişi",
                icon = {
                    ArrowDownLeftIcon(
                        color = Color(0xFF16A34A),
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = Color(0xFFDCFCE7),
                containerColor = Color(0xFFF2FAF5),
                borderColor = Color(0xFFD7F0E3),
                onClick = onReceivableSummaryClick,
                modifier = Modifier.weight(1f),
            )

            // 3. Net Durum Kartı (Net position)
            val netAmountColor = when {
                summary.isNetNegative -> Color(0xFFDC2626)
                summary.isNetPositive -> Color(0xFF16A34A)
                else -> MaterialTheme.colorScheme.onSurface
            }

            DebtSummaryCard(
                title = "Net durum",
                amount = summary.formattedNetBalance,
                amountColor = netAmountColor,
                countText = summary.netStatusText,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Scale,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = Color(0xFFEDF2F7),
                containerColor = Color(0xFFF8FAFC),
                borderColor = Color(0xFFE2E8F0),
                onClick = null,
                modifier = Modifier.weight(1f),
            )
        }

        // Çoklu para birimi güvenliği uyarısı
        if (summary.excludedCurrenciesCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "${summary.excludedCurrenciesCount} kayıt farklı para biriminde (${summary.excludedCurrencies.joinToString { it.name }}) olduğu için bu toplama dahil edilmedi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtSummaryCard(
    title: String,
    amount: String,
    countText: String,
    icon: @Composable () -> Unit,
    iconBgColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(clickableModifier)
            .semantics {
                contentDescription = "$title, $amount, $countText"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // İkon Rozeti (Referans görseldeki gibi yuvarlatılmış kare)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            // Başlık
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Tutar
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Alt Bilgi (Sayaç + Chevron)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Referans görseldeki "Yaklaşan Vadeler" (Upcoming Payments) kartı bileşenidir.
 * Yalnız açık kayıtlardan ve sistem tarihine göre vadesi 7 gün içinde olan veya gecikmiş kayıtları listeler.
 */
@Composable
fun UpcomingPaymentsSection(
    upcomingItems: List<DebtDisplayModel>,
    onItemClick: (DebtDisplayModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (upcomingItems.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }
    val overdueCount = upcomingItems.count { it.dueStatus is DebtDueStatus.Overdue }
    val subtitle = if (overdueCount > 0) {
        "$overdueCount gecikmiş, ${upcomingItems.size - overdueCount} yaklaşan ödeme"
    } else {
        "Önümüzdeki 7 gün içinde ${upcomingItems.size} ödeme"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Large)),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color(0xFFECE7DE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
        ) {
            // Başlık Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column {
                        Text(
                            text = "Yaklaşan Vadeler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Referans görseldeki "See all >" kapsül hap butonu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F7),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .semantics {
                            contentDescription = if (isExpanded) "Yaklaşan vadeler listesini daralt" else "Yaklaşan vadeler listesini genişlet"
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (isExpanded && upcomingItems.size > 2) "Daha az" else "Tümünü gör",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (isExpanded && upcomingItems.size > 2) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Liste Elemanları
            val displayedItems = if (isExpanded || upcomingItems.size <= 2) upcomingItems else upcomingItems.take(2)
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                displayedItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Color(0xFFF3EFE8),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                        )
                    }
                    UpcomingPaymentItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingPaymentItemRow(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "Yaklaşan vade: ${item.title}, Kalan: ${item.formattedRemainingAmount}, Vade: ${item.formattedDueDate}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Takvim İkon Kutusu
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Yaklaşan Vade Etiketi ve Başlık
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Yaklaşan vade • ${item.formattedShortDueDate}",
                style = MaterialTheme.typography.bodySmall,
                color = FeniqoTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoTextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tutar
        Text(
            text = item.formattedRemainingAmount,
            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
            fontWeight = FontWeight.Bold,
            color = FeniqoTextPrimary,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp),
        )
    }
}


/**
 * Referans görseldeki "Your Debts" ve "Receivables" için tek bir beyaz kart içinde gruplanmış kart bileşenidir.
 * Kart içinde başlık satırı, ↗/↙ rozeti, "Tümünü gör >" hap butonu ve alt alta dizilmiş liste satırları yer alır.
 */
@Composable
fun DebtGroupedSectionCard(
    title: String,
    countText: String,
    isDebtSection: Boolean,
    items: List<DebtDisplayModel>,
    onItemClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Large)),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFECE7DE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
        ) {
            // Kart Başlığı Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    modifier = Modifier.weight(1f),
                ) {
                    val (iconBg, iconTint) = if (isDebtSection) {
                        Pair(Color(0xFFFDE8E8), Color(0xFFDC2626))
                    } else {
                        Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDebtSection) {
                            ArrowUpRightIcon(color = iconTint, modifier = Modifier.size(16.dp))
                        } else {
                            ArrowDownLeftIcon(color = iconTint, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // "Tümünü gör >" hap butonu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F7),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .semantics {
                            contentDescription = if (isExpanded) "$title listesini daralt" else "$title listesini genişlet"
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (isExpanded && items.size > 4) "Daha az" else "Tümünü gör",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (isExpanded && items.size > 4) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Liste Satırları (Referans görseldeki gibi tek kartın içine dividers ile gömülür)
            val displayedItems = if (isExpanded || items.size <= 4) items else items.take(4)
            Column(modifier = Modifier.fillMaxWidth()) {
                displayedItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Color(0xFFF3EFE8),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                        )
                    }
                    DebtGroupedItemRow(
                        item = item,
                        onClick = { onItemClick(item.id) },
                    )
                }
            }
        }
    }
}

/**
 * Referans görseldeki liste satırı (Grup kartı içinde temiz, minimal satır görünümü).
 */
@Composable
fun DebtGroupedItemRow(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "${item.title}, ${item.typeLabel}, Kalan: ${item.formattedRemainingAmount}, Vade: ${item.formattedDueDate}, Durum: ${item.formattedDueStatus}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // İkon (Borç ise Kredi Kartı, Alacak ise Kişi)
        val (iconBoxBg, iconTint) = if (item.type == DebtType.DEBT) {
            Pair(Color(0xFFF1EDE6), Color(0xFF475569))
        } else {
            Pair(Color(0xFFE8F1EC), FeniqoSageGreen)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBoxBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.type == DebtType.DEBT) Icons.Outlined.CreditCard else Icons.Outlined.Person,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Başlık ve Kalan Tutar
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoTextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtext = if (item.isSettled) {
                "Tamamlandı"
            } else if (item.type == DebtType.DEBT) {
                "Kalan: ${item.formattedRemainingAmount}"
            } else {
                item.formattedRemainingAmount
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isSettled) FeniqoTrendGreen else FeniqoTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Vade Tarihi veya Durum Rozeti
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (item.isSettled) {
                WarmDueStatusBadge(status = item.dueStatus, label = "Tamamlandı")
            } else if (item.dueStatus is DebtDueStatus.Overdue || item.dueStatus is DebtDueStatus.DueToday) {
                WarmDueStatusBadge(status = item.dueStatus, label = item.formattedDueStatus)
            } else {
                Text(
                    text = item.formattedShortDueDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoTextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için korunan eski bağımsız liste satırı kartı.
 */
@Composable
fun WarmDebtListItem(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${item.title}, ${item.typeLabel}, Kalan: ${item.formattedRemainingAmount}, Vade: ${item.formattedDueDate}, Durum: ${item.formattedDueStatus}"
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEDE7DE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        DebtGroupedItemRow(
            item = item,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 6.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için korunan bağımsız bölüm başlığı bileşeni.
 */
@Composable
fun DebtSectionHeader(
    title: String,
    countText: String,
    isDebtSection: Boolean,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            val (iconBg, iconTint) = if (isDebtSection) {
                Pair(Color(0xFFFDE8E8), Color(0xFFDC2626))
            } else {
                Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                if (isDebtSection) {
                    ArrowUpRightIcon(color = iconTint, modifier = Modifier.size(16.dp))
                } else {
                    ArrowDownLeftIcon(color = iconTint, modifier = Modifier.size(16.dp))
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (onSeeAllClick != null) {
            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier.heightIn(min = FeniqoTouchTarget.Minimum),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tümünü gör",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Referans görseldeki "Feniqo İçgörü" kartı.
 * Gerçek verilere dayanan, açıklanabilir finansal içgörü kartıdır.
 */
@Composable
fun DebtInsightCard(
    insight: DebtInsightUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(clickableModifier)
            .semantics {
                contentDescription = "${insight.title}: ${insight.message}"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFDF5),
        ),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                )
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B5563),
                    lineHeight = 18.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CompactTypeBadge(
    type: DebtType,
    label: String,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (type) {
        DebtType.DEBT -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626))
        DebtType.RECEIVABLE -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun WarmDueStatusBadge(
    status: DebtDueStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (status) {
        is DebtDueStatus.Overdue -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626)) // Kırmızı / Overdue
        is DebtDueStatus.DueToday -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706)) // Amber / Bugün
        is DebtDueStatus.DueSoon -> Pair(Color(0xFFFFF0E6), Color(0xFFD96B27)) // Turuncu / Due soon
        is DebtDueStatus.OnTime -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A)) // Yeşil / On time
        is DebtDueStatus.Settled -> Pair(Color(0xFFF1EDE6), Color(0xFF64748B)) // Nötr / Kapandı
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için korunan eski DebtCard bileşeni.
 * Yeni warm-luxury liste öğesi görünümüne yönlendirir.
 */
@Composable
fun DebtCard(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WarmDebtListItem(
        item = item,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun DebtDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    debtTitle: String = "",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Borç alacak silme onay diyaloğu"
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Kaydı silmek istiyor musun?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FeniqoTextPrimary,
                fontSize = 18.sp,
            )
        },
        text = {
            val descriptionText = if (debtTitle.isNotBlank()) {
                "$debtTitle kaydını silmek istediğine emin misin?"
            } else {
                "Bu borç / alacak kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz."
            }
            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = FeniqoTextSecondary,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(44.dp),
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sil", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Vazgeç", color = FeniqoTextPrimary, fontWeight = FontWeight.Medium)
            }
        },
    )
}

/**
 * Referans Görsel 01'deki "Borç kapatma planı" giriş kartı.
 * Grafik ikonu, başlık, rehber alt metin ve chevron içerir.
 */
@Composable
fun DebtSnowballEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Borç kapatma planı" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFECE7DE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE8F1EC)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Leaderboard,
                    contentDescription = null,
                    tint = FeniqoSageGreen,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Borç kapatma planı",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    fontSize = 15.sp,
                )
                Text(
                    text = "Borçlarını daha hızlı kapatmak için bir plan oluştur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoTextSecondary,
                    fontSize = 13.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Referans Görsel 09'daki takvim modal bottom sheet seçicisidir.
 * Vade tarihi seç, ay gezinimi, takvim ızgarası ve altta "Tarihi seç" butonu içerir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDatePickerSheet(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Vade tarihi seç",
    modifier: Modifier = Modifier,
) {
    val initialDate = selectedDate ?: LocalDate(2026, 9, 15)
    val initialUtcMillis = initialDate.toEpochDays() * 86_400_000L

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFCBD5E1),
            ) {}
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    fontSize = 18.sp,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Takvim
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = FeniqoSageGreen,
                    todayDateBorderColor = FeniqoSageGreen,
                    selectedDayContentColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Alt Satır: Seçili Tarih ve "Tarihi seç" butonu
            val currentMillis = datePickerState.selectedDateMillis
            val currentLocalDate = currentMillis?.let {
                LocalDate.fromEpochDays((it / 86_400_000L).toInt())
            } ?: initialDate

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DateFormatter.formatReadableDate(currentLocalDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    fontSize = 16.sp,
                )

                Button(
                    onClick = {
                        onDateSelected(currentLocalDate)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Text(
                        text = "Tarihi seç",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/**
 * Referans Görsel 10'daki para birimi modal bottom sheet seçicisidir.
 * TRY, USD, EUR seçenekleri, radyo ikonları ve para birimi sembolleri içerir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtCurrencyPickerSheet(
    selectedCurrency: Currency,
    onCurrencySelected: (Currency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFCBD5E1),
            ) {}
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Para birimi seç",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    fontSize = 18.sp,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Currency.entries.forEach { currency ->
                val isSelected = currency == selectedCurrency
                val symbol = when (currency) {
                    Currency.TRY -> "₺"
                    Currency.USD -> "$"
                    Currency.EUR -> "€"
                }
                val name = when (currency) {
                    Currency.TRY -> "Türk lirası"
                    Currency.USD -> "Amerikan doları"
                    Currency.EUR -> "Euro"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onCurrencySelected(currency)
                            onDismiss()
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFFE8F1EC) else Color.White,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) FeniqoSageGreen else Color(0xFFE2E8F0),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Radio dairesi
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (isSelected) FeniqoSageGreen else Color(0xFF94A3B8),
                                    ),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(FeniqoSageGreen),
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currency.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FeniqoTextPrimary,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = FeniqoTextSecondary,
                                fontSize = 13.sp,
                            )
                        }

                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) FeniqoSageGreen else FeniqoTextSecondary,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Referans Görsel 12'deki "Henüz kayıt yok" boş durum bileşeni.
 * Çift yönlü ok ikonu, açıklama ve "İlk kaydını oluştur" butonu içerir.
 */
@Composable
fun DebtEmptyState(
    onAddDebt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F1EC)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Henüz kayıt yok",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FeniqoTextPrimary,
            fontSize = 20.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Borçlarını ve alacaklarını tek yerde takip et.",
            style = MaterialTheme.typography.bodyMedium,
            color = FeniqoTextSecondary,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddDebt,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoSageGreen,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "İlk kaydını oluştur",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Referans Görsel 13'teki tamamlanmış borç / alacak bilgi banner'ı.
 */
@Composable
fun DebtSettledBanner(
    isDebt: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE8F1EC),
        border = BorderStroke(1.dp, Color(0xFFC7DEC4)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (isDebt) "Bu borcun tamamı ödendi." else "Bu alacağın tamamı tahsil edildi.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoSageGreen,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * Para birimi sembol uzantısı.
 */
fun Currency.symbol(): String = when (this) {
    Currency.TRY -> "₺"
    Currency.USD -> "$"
    Currency.EUR -> "€"
}

fun Currency.symbolName(): String = when (this) {
    Currency.TRY -> "Türk lirası"
    Currency.USD -> "Amerikan doları"
    Currency.EUR -> "Euro"
}
