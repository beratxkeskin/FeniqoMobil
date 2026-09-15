package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.subscription.SubscriptionActualSpendingUiModel
import com.feniqo.mobile.presentation.subscription.SubscriptionDisplayModel
import com.feniqo.mobile.presentation.subscription.SubscriptionEstimatedCostSummaryUiModel
import com.feniqo.mobile.presentation.subscription.SubscriptionInsightUiModel
import com.feniqo.mobile.presentation.subscription.displayName
import com.feniqo.mobile.presentation.theme.FeniqoEmerald
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
private val FeniqoGraphite = Color(0xFF1E232A)
private val FeniqoCardBackground = Color.White
private val FeniqoExpenseRed = Color(0xFFE53935)
private val FeniqoIconReceiptBg = Color(0xFFFFEBEE)

/**
 * Abonelikler ekranı için Görsel 1 Onaylı Grafit Hero Özet Kartı.
 * Koyu grafit arka plan (#1E232A), tahmini aylık maliyet, bilgi ikonu,
 * yıllık yaklaşık maliyet ve aktif abonelik sayısını 2 sütunlu düzende sunar.
 */
@Composable
fun SubscriptionHeroCard(
    estimatedSummaries: List<SubscriptionEstimatedCostSummaryUiModel>,
    totalActiveCount: Int,
    upcomingCount: Int,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoGraphite,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            // 1. Üst Başlık ve (i) Bilgi İkonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tahmini aylık maliyet",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE0E0E0),
                    fontWeight = FontWeight.Normal,
                )

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onInfoClick != null) { onInfoClick?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = "Bilgi",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Büyük Tutar ve Para Birimi / Ay
            if (estimatedSummaries.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "₺0",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "TRY / ay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBDBDBD),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else {
                estimatedSummaries.forEach { summary ->
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = summary.formattedMonthlyCost,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics {
                                contentDescription = "Tahmini aylık maliyet: ${summary.formattedMonthlyCost}"
                            },
                        )
                        Text(
                            text = "${summary.currency.name} / ay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFBDBDBD),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. İnce Yatay Ayırıcı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. İki Sütunlu Alt Göstergeler (Yıllık yaklaşık | Aktif abonelik)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sol Sütun: Yıllık yaklaşık
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Yıllık yaklaşık",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val yearlyText = estimatedSummaries.firstOrNull()?.formattedYearlyCost ?: "₺0"
                    Text(
                        text = yearlyText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                // Dikey Ayırıcı Çizgi
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )

                // Sağ Sütun: Aktif abonelik sayısı
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp),
                ) {
                    Text(
                        text = "$totalActiveCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "aktif abonelik",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                    )
                }
            }
        }
    }
}

/**
 * Görsel 1: Ayrı beyaz yüzeyde "Bu ay kaydedilen ödemeler" kartı.
 * Kırmızı makbuz ikonu, kesin gider tutarı (-₺798) ve chevron sunar.
 */
@Composable
fun SubscriptionActualSpendingCard(
    actualSpendings: List<SubscriptionActualSpendingUiModel>,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val totalActual = actualSpendings.firstOrNull()?.currentMonthActualFormatted ?: "₺0"
    val isNegativeFormatted = totalActual.startsWith("-") || totalActual.startsWith("−")
    val displayAmount = if (isNegativeFormatted) totalActual else "-$totalActual"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoCardBackground,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFEBEBEB),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sol: Pembe/kırmızımsı arka planlı makbuz ikonu
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(FeniqoIconReceiptBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = FeniqoExpenseRed,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Orta: Metin ve Tutar
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bu ay kaydedilen ödemeler",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF424242),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoExpenseRed,
                )
            }

            // Sağ: Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Yaklaşan Ödemeler Bölümü (Bugün dahil sonraki 7 gün).
 */
@Composable
fun SubscriptionUpcomingSection(
    upcomingPayments: List<SubscriptionDisplayModel>,
    onSubscriptionClick: (com.feniqo.mobile.domain.model.EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (upcomingPayments.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Yaklaşan Ödemeler",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Sonraki 7 gün (${upcomingPayments.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        upcomingPayments.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "${item.name} yaklaşan ödeme detayı",
                        onClick = { onSubscriptionClick(item.id) },
                    ),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubscriptionCategoryAvatar(
                        iconKey = item.categoryIconKey,
                        colorHex = item.categoryColorHex,
                        isCategoryMissing = item.isCategoryMissing,
                        isCategoryUnassigned = item.isCategoryUnassigned,
                    )

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val dueDateText = when (val s = item.renewalStatus) {
                            SubscriptionRenewalStatus.DueToday -> "Bugün yenileniyor"
                            is SubscriptionRenewalStatus.Upcoming -> "${s.daysUntilRenewal} gün kaldı (${item.formattedNextRenewalDate})"
                            else -> item.formattedNextRenewalDate
                        }
                        Text(
                            text = "${item.categoryName} • $dueDateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.renewalStatus is SubscriptionRenewalStatus.DueToday) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                    Text(
                        text = item.formattedAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.width(FeniqoSpacing.ExtraSmall))

                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Gecikmiş Ödemeler Uyarı Bölümü.
 */
@Composable
fun SubscriptionOverdueSection(
    overduePayments: List<SubscriptionDisplayModel>,
    onSubscriptionClick: (com.feniqo.mobile.domain.model.EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (overduePayments.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoExpenseRed.copy(alpha = 0.08f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = FeniqoExpenseRed.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = FeniqoExpenseRed,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Gecikmiş Ödemeler (${overduePayments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoExpenseRed,
                )
            }

            Text(
                text = "Aşağıdaki aboneliklerin yenileme tarihi geçmiş durumda. Ödendiğini onaylayarak bir sonraki vadeye ilerletebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            overduePayments.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FeniqoRadius.Small))
                        .clickable { onSubscriptionClick(item.id) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${item.formattedNextRenewalDate} tarihinden beri gecikti",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                    Text(
                        text = item.formattedAmount,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Filtre Çipleri Satırı. Görsel 1 onaylı stili: Koyu seçili çip, açık yuvarlatılmış haplar.
 */
@Composable
fun SubscriptionFilterRow(
    selectedFilter: SubscriptionFilter,
    onFilterSelected: (SubscriptionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubscriptionFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onFilterSelected(filter) },
                shape = CircleShape,
                color = if (isSelected) Color(0xFF1E232A) else Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) Color(0xFF1E232A) else Color(0xFFE0E0E0),
                ),
            ) {
                Text(
                    text = filter.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFF424242),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Feniqo Gerçek Veriye Dayalı İçgörü Kartı.
 */
@Composable
fun SubscriptionInsightCard(
    insight: SubscriptionInsightUiModel,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (insight.isWarning) Color(0xFFD97706) else FeniqoSageGreen

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFEBEBEB),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    insight.badgeText?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = accentColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Bildirim İzni Uyarı Bandı.
 */
@Composable
fun SubscriptionNotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFEBEBEB),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF2D5A43),
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Abonelik Hatırlatıcıları",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Yenilemeden 7 gün önce ve yenileme günü bildirim al.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D5A43),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "İzin ver",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Yeni Abonelik Ekleme Eylem Kartı.
 */
@Composable
fun SubscriptionAddActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2D5A43),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Abonelik ekle",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Görsel 1 Onaylı Abonelik Kartı.
 * Kategori semantik ikonu, abonelik adı, yenileme tarihi, kırmızı plan fiyatı ve chevron.
 */
@Composable
fun SubscriptionCard(
    item: SubscriptionDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusDescription = item.lifecycleStatus.name
    val (renewalStatusText, _) = resolveRenewalStatusVisuals(item)

    // Periyot metni: "/ ay", "/ yıl", "/ hafta"
    val frequencyText = when (item.frequency) {
        com.feniqo.mobile.domain.model.RecurrenceFrequency.MONTHLY -> "/ ay"
        com.feniqo.mobile.domain.model.RecurrenceFrequency.YEARLY -> "/ yıl"
        com.feniqo.mobile.domain.model.RecurrenceFrequency.WEEKLY -> "/ hf"
        com.feniqo.mobile.domain.model.RecurrenceFrequency.DAILY -> "/ gün"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.name} aboneliği",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.name}, ${item.categoryName}, ${item.formattedAmount}$frequencyText, $renewalStatusText, $statusDescription"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoCardBackground,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFEBEBEB),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Sol: Kategori Vektör / Avatar İkonu
            SubscriptionCategoryAvatar(
                iconKey = item.categoryIconKey,
                colorHex = item.categoryColorHex,
                isCategoryMissing = item.isCategoryMissing,
                isCategoryUnassigned = item.isCategoryUnassigned,
                modifier = Modifier.size(42.dp),
            )

            Spacer(modifier = Modifier.width(14.dp))

            // 2. Orta: İsim ve Yenileme Tarihi
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (item.lifecycleStatus != SubscriptionLifecycleStatus.ACTIVE) {
                        SubscriptionStatusBadge(lifecycleStatus = item.lifecycleStatus)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val renewalDisplay = if (item.renewalStatus is SubscriptionRenewalStatus.DueToday) {
                    "Bugün yenileniyor"
                } else {
                    "Yenileme: ${item.formattedNextRenewalDate}"
                }

                Text(
                    text = renewalDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.hasPriceIncrease) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SubscriptionPriceIncreaseBadge(
                        priceIncreaseFormatted = item.priceIncreaseFormatted,
                        basisPoints = item.priceIncreaseBasisPoints,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Sağ: Kırmızı Fiyat ve Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${item.formattedAmount} $frequencyText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoExpenseRed,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

            // Sağ Metin Bloğu: Tutar ve Ok



/**
 * Kategoriye ait yüzde 12 tonal renkli daire avatar ve semantik simgesidir.
 * Asla harici marka logosu veya tahmini logo servisi kullanmaz.
 */
@Composable
fun SubscriptionCategoryAvatar(
    iconKey: String?,
    colorHex: String?,
    isCategoryMissing: Boolean,
    isCategoryUnassigned: Boolean,
    modifier: Modifier = Modifier,
) {
    val categoryColor = when {
        isCategoryMissing -> MaterialTheme.colorScheme.outline
        isCategoryUnassigned -> MaterialTheme.colorScheme.surfaceVariant
        else -> ColorParser.parseHexColorOrNull(colorHex) ?: MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .background(categoryColor.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategorySemanticIconResolver.resolve(iconKey),
            contentDescription = null,
            tint = categoryColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Yaşam döngüsü durumuna göre renkli rozet (Aktif, Duraklatıldı, Deneme, İptal Edildi, Süresi Doldu).
 */
@Composable
fun SubscriptionStatusBadge(
    lifecycleStatus: SubscriptionLifecycleStatus,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, text) = when (lifecycleStatus) {
        SubscriptionLifecycleStatus.ACTIVE -> Triple(
            FeniqoSageGreen.copy(alpha = 0.15f),
            FeniqoSageGreen,
            "Aktif",
        )
        SubscriptionLifecycleStatus.PAUSED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Duraklatıldı",
        )
        SubscriptionLifecycleStatus.TRIAL -> Triple(
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFD97706),
            "Deneme",
        )
        SubscriptionLifecycleStatus.CANCELLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            "İptal",
        )
        SubscriptionLifecycleStatus.EXPIRED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            "Süresi Doldu",
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Durum: $text"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
        )
    }
}

/**
 * Fiyat artış etiketi rozeti.
 */
@Composable
fun SubscriptionPriceIncreaseBadge(
    priceIncreaseFormatted: String?,
    basisPoints: Long?,
    modifier: Modifier = Modifier,
) {
    val text = when {
        basisPoints != null && basisPoints > 0L -> {
            val pct = basisPoints / 100
            "+%$pct"
        }
        priceIncreaseFormatted != null -> priceIncreaseFormatted
        else -> "Fiyat Arttı"
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Fiyat artışı: $text"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = Color(0xFF10B981).copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669),
            )
        }
    }
}

@Composable
private fun resolveRenewalStatusVisuals(item: SubscriptionDisplayModel): Pair<String, Color> =
    when (val status = item.renewalStatus) {
        SubscriptionRenewalStatus.Inactive -> {
            "Duraklatıldı" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        is SubscriptionRenewalStatus.Overdue -> {
            "${status.daysOverdue} gün gecikti" to FeniqoExpenseRed
        }
        SubscriptionRenewalStatus.DueToday -> {
            "Bugün yenileniyor" to Color(0xFFD97706)
        }
        is SubscriptionRenewalStatus.Upcoming -> {
            "${status.daysUntilRenewal} gün kaldı" to MaterialTheme.colorScheme.primary
        }
        is SubscriptionRenewalStatus.Scheduled -> {
            "Sonraki: ${item.formattedNextRenewalDate}" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

/**
 * Görsel 13 Onaylı Silme Diyaloğu:
 * Pembe yuvarlak içinde kırmızı çöp kutusu, açıklama, "Kaydı sil" ve "Vazgeç" butonları.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Abonelik silme onay diyaloğu"
        },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Kırmızı çöp kutusu ikonu
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(FeniqoIconReceiptBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = FeniqoExpenseRed,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Kaydı silmek istiyor musun?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Feniqo'daki yenileme takibi durdurulur. Hizmet sağlayıcındaki abonelik iptal edilmez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buton 1: Kaydı sil (Kırmızı)
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoExpenseRed,
                        contentColor = Color.White,
                    ),
                ) {
                    if (isSubmitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Kaydı sil",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Buton 2: Vazgeç (Gri)
                Button(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF424242),
                    ),
                ) {
                    Text(
                        text = "Vazgeç",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Görsel 11 Onaylı Ödendi İşaretleme Onayı:
 * Kırmızı makbuz ikonu, servis ve vade kartı, kırmızı tutar, açıklama,
 * "Ödendi olarak kaydet" ve "Vazgeç" butonları.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionAdvanceRenewalDialog(
    subscriptionName: String = "",
    amountFormatted: String = "",
    nextRenewalDate: LocalDate,
    calculatedFollowingRenewalDate: LocalDate? = null,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedCurrentDueDate = DateFormatter.formatReadableDate(nextRenewalDate)
    val formattedNextDueDate = calculatedFollowingRenewalDate?.let { DateFormatter.formatReadableDate(it) }

    val isNegativeFormatted = amountFormatted.startsWith("-") || amountFormatted.startsWith("−")
    val displayAmount = if (amountFormatted.isBlank()) "" else if (isNegativeFormatted) amountFormatted else "-$amountFormatted"

    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Abonelik ödendi onay diyaloğu"
        },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Kırmızı makbuz ikonu
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(FeniqoIconReceiptBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = FeniqoExpenseRed,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Ödendi olarak kaydedilsin mi?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detay Bilgi Kutucuğu (Servis, Tarih, Kırmızı Tutar)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF9F9F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            if (subscriptionName.isNotBlank()) {
                                Text(
                                    text = subscriptionName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121),
                                )
                            }
                            Text(
                                text = formattedCurrentDueDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF757575),
                            )
                        }

                        if (displayAmount.isNotBlank()) {
                            Text(
                                text = displayAmount,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FeniqoExpenseRed,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val renewalExplainer = if (formattedNextDueDate != null) {
                    "Ödeme kaydı eklenir; sonraki yenileme $formattedNextDueDate olur. Bankadan para gönderilmez."
                } else {
                    "Ödeme kaydı eklenir ve vade bir sonraki döneme ilerletilir. Bankadan para gönderilmez."
                }

                Text(
                    text = renewalExplainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buton 1: Ödendi olarak kaydet (Yeşil)
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2D5A43),
                        contentColor = Color.White,
                    ),
                ) {
                    if (isSubmitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Ödendi olarak kaydet",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Buton 2: Vazgeç (Gri)
                Button(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF424242),
                    ),
                ) {
                    Text(
                        text = "Vazgeç",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Görsel 14 Onaylı Boş Liste Durumu:
 * Belge ikonu, "Henüz aboneliğin yok", "Takibini yapmak için ilk aboneliğini ekle.", "+ Abonelik ekle" butonu.
 */
@Composable
fun SubscriptionEmptyState(
    onAddSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBEBEB)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFFF5F5F5), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = Color(0xFF424242),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Henüz aboneliğin yok",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Takibini yapmak için ilk aboneliğini ekle.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF757575),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddSubscription,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D5A43),
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Abonelik ekle",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Görsel 15 Onaylı Yüklenemedi Durumu.
 */
@Composable
fun SubscriptionErrorCard(
    message: String = "Bir hata oluştu. Lütfen tekrar dene.",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBEBEB)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = FeniqoExpenseRed,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Yüklenemedi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF424242),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = "Tekrar dene",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Görsel 15 Onaylı Abonelik Bulunamadı Durumu.
 */
@Composable
fun SubscriptionNotFoundCard(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBEBEB)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFF5F5F5), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFF757575),
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Abonelik bulunamadı",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                )
                Text(
                    text = "Aradığın abonelik listede yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF424242),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = "Listeye dön",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Görsel 15 Onaylı Başarı Bildirim Kartı (Ödeme kaydedildi).
 */
@Composable
fun SubscriptionSuccessBanner(
    message: String = "Ödeme kaydedildi",
    subtitle: String = "Kayıt başarıyla eklendi.",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE8F5E9),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF2D5A43), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Add, // Close ikonu veya dönüştürülmüş
                    contentDescription = "Kapat",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
