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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
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
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import com.feniqo.mobile.presentation.theme.PhoenixGold

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
                contentDescription = "${item.title}, ${item.typeLabel}, Kalan: ${item.formattedRemainingAmount}, Vade: ${item.formattedDueDate}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar Yuvarlağı (38dp, nötr açık zemin)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFF2EFE9)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Başlık, Tür Rozeti ve Açıklama
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactTypeBadge(type = item.type, label = item.typeLabel)
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

        Spacer(modifier = Modifier.width(8.dp))

        // Tutar ve Vade (Borç ise kırmızı, alacak ise yeşil)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val amountColor = if (item.type == DebtType.DEBT) Color(0xFFDC2626) else Color(0xFF16A34A)
            Text(
                text = item.formattedRemainingAmount,
                style = MaterialTheme.typography.bodyMedium.merge(FeniqoTabularNumberStyle),
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Text(
                text = item.formattedShortDueDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
        // Avatar (Baş harf, 38dp nötr açık yuvarlak)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFF2EFE9)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Başlık ve Açıklama
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (!item.description.isNullOrBlank()) item.description else "${item.typeLabel} kaydı",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tutar, Durum Rozeti ve Vade Tarihi
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val amountColor = if (item.dueStatus is DebtDueStatus.Overdue) {
                    Color(0xFFDC2626)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = item.formattedRemainingAmount,
                    style = MaterialTheme.typography.bodyMedium.merge(FeniqoTabularNumberStyle),
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
                WarmDueStatusBadge(status = item.dueStatus, label = item.formattedDueStatus)
            }

            Text(
                text = item.formattedShortDueDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
        title = {
            Text(
                text = "Kaydı Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bu borç / alacak kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sil")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("Vazgeç")
            }
        },
    )
}
