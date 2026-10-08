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
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.subscription.SubscriptionActualSpendingUiModel
import com.feniqo.mobile.presentation.subscription.SubscriptionDisplayModel
import com.feniqo.mobile.presentation.subscription.SubscriptionEstimatedCostSummaryUiModel
import com.feniqo.mobile.presentation.subscription.SubscriptionInsightUiModel
import com.feniqo.mobile.presentation.subscription.resolveBadgeText
import com.feniqo.mobile.presentation.subscription.resolveTitleAndDescription
import com.feniqo.mobile.presentation.subscription.toLocalizedBadgeText
import com.feniqo.mobile.presentation.subscription.toLocalizedFilterLabel
import com.feniqo.mobile.presentation.subscription.toLocalizedFrequencySummary
import com.feniqo.mobile.presentation.subscription.toLocalizedRenewalStatusLabel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.subscription_active_count_plural
import feniqomobil.sharedui.generated.resources.subscription_actual_spending_title
import feniqomobil.sharedui.generated.resources.subscription_add_action
import feniqomobil.sharedui.generated.resources.subscription_advance_renewal_dialog_confirm
import feniqomobil.sharedui.generated.resources.subscription_advance_renewal_dialog_message
import feniqomobil.sharedui.generated.resources.subscription_advance_renewal_dialog_title
import feniqomobil.sharedui.generated.resources.subscription_close_button
import feniqomobil.sharedui.generated.resources.subscription_common_cancel
import feniqomobil.sharedui.generated.resources.subscription_common_error_retry
import feniqomobil.sharedui.generated.resources.subscription_delete_dialog_confirm
import feniqomobil.sharedui.generated.resources.subscription_delete_dialog_message
import feniqomobil.sharedui.generated.resources.subscription_delete_dialog_title
import feniqomobil.sharedui.generated.resources.subscription_detail_not_found_back
import feniqomobil.sharedui.generated.resources.subscription_detail_not_found_desc
import feniqomobil.sharedui.generated.resources.subscription_detail_not_found_title
import feniqomobil.sharedui.generated.resources.subscription_detail_price_change_increased
import feniqomobil.sharedui.generated.resources.subscription_empty_desc
import feniqomobil.sharedui.generated.resources.subscription_empty_title
import feniqomobil.sharedui.generated.resources.subscription_error_default
import feniqomobil.sharedui.generated.resources.subscription_error_title
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_grant
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_message
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_title
import feniqomobil.sharedui.generated.resources.subscription_frequency_monthly
import feniqomobil.sharedui.generated.resources.subscription_hero_estimated_monthly_cost
import feniqomobil.sharedui.generated.resources.subscription_hero_info_desc_semantics
import feniqomobil.sharedui.generated.resources.subscription_hero_yearly_approx
import feniqomobil.sharedui.generated.resources.subscription_overdue_count_plural
import feniqomobil.sharedui.generated.resources.subscription_payment_recorded_subtitle
import feniqomobil.sharedui.generated.resources.subscription_payment_recorded_title
import feniqomobil.sharedui.generated.resources.subscription_section_overdue_title
import feniqomobil.sharedui.generated.resources.subscription_section_upcoming_subtitle
import feniqomobil.sharedui.generated.resources.subscription_section_upcoming_title
import feniqomobil.sharedui.generated.resources.subscription_status_due_today
import feniqomobil.sharedui.generated.resources.subscription_status_upcoming_days_plural
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val FeniqoGraphite = Color(0xFF1E232A)
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
                    text = stringResource(Res.string.subscription_hero_estimated_monthly_cost),
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
                        contentDescription = stringResource(Res.string.subscription_hero_info_desc_semantics),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Büyük Tutar ve Para Birimi / Ay
            val perMonthSuffix = stringResource(Res.string.subscription_frequency_monthly)
            if (estimatedSummaries.isEmpty()) {
                val zeroAmount = Money(0L, Currency.TRY).toLocalizedFormatted()
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = zeroAmount,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "TRY $perMonthSuffix",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else {
                estimatedSummaries.forEach { summary ->
                    val monthlyCostText = summary.monthlyCost?.toLocalizedFormatted() ?: Money(0L, summary.currency).toLocalizedFormatted()
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = monthlyCostText,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics {
                                contentDescription = monthlyCostText
                            },
                        )
                        Text(
                            text = "${summary.currency.name} $perMonthSuffix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = stringResource(Res.string.subscription_hero_yearly_approx),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val yearlyText = estimatedSummaries.firstOrNull()?.yearlyCost?.toLocalizedFormatted()
                        ?: Money(0L, Currency.TRY).toLocalizedFormatted()
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
                        text = pluralStringResource(Res.plurals.subscription_active_count_plural, totalActiveCount, totalActiveCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val totalActual = actualSpendings.firstOrNull()?.currentMonthActual?.toLocalizedFormatted()
        ?: Money(0L, Currency.TRY).toLocalizedFormatted()
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
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
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
                    text = stringResource(Res.string.subscription_actual_spending_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = stringResource(Res.string.subscription_section_upcoming_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.subscription_section_upcoming_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        upcomingPayments.forEach { item ->
            val upcomingItemClickLabel = item.name
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = upcomingItemClickLabel,
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
                            SubscriptionRenewalStatus.DueToday -> stringResource(Res.string.subscription_status_due_today)
                            is SubscriptionRenewalStatus.Upcoming -> {
                                val days = s.daysUntilRenewal.toInt()
                                val daysText = pluralStringResource(Res.plurals.subscription_status_upcoming_days_plural, days, days)
                                "$daysText (${item.nextRenewalDate.toLocalizedReadableDate()})"
                            }
                            else -> item.nextRenewalDate.toLocalizedReadableDate()
                        }
                        val categoryPrefix = item.categoryName?.let { "$it • " } ?: ""
                        Text(
                            text = "$categoryPrefix$dueDateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.renewalStatus is SubscriptionRenewalStatus.DueToday) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                    Text(
                        text = item.amount.toLocalizedFormatted(),
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
                    text = pluralStringResource(Res.plurals.subscription_overdue_count_plural, overduePayments.size, overduePayments.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoExpenseRed,
                )
            }

            Text(
                text = stringResource(Res.string.subscription_section_overdue_title),
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
                            text = item.renewalStatus.toLocalizedRenewalStatusLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                    Text(
                        text = item.amount.toLocalizedFormatted(),
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    text = filter.toLocalizedFilterLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
    val (title, description) = insight.payload.resolveTitleAndDescription()
    val badgeText = insight.payload.resolveBadgeText()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
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
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = accentColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = badgeText,
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
                    text = description,
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
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
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
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
                    text = stringResource(Res.string.subscription_form_reminder_dialog_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.subscription_form_reminder_dialog_message),
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
                    text = stringResource(Res.string.subscription_form_reminder_dialog_grant),
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
            text = stringResource(Res.string.subscription_add_action),
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
    val statusDescription = item.lifecycleStatus.toLocalizedBadgeText()
    val (renewalStatusText, _) = resolveRenewalStatusVisuals(item)
    val frequencyText = toLocalizedFrequencySummary(item.frequency, 1)
    val cardClickLabel = item.name
    val cardContentDesc = "${item.name}, ${item.amount.toLocalizedFormatted()} $frequencyText, $renewalStatusText, $statusDescription"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = cardClickLabel,
                onClick = onClick,
            )
            .semantics {
                contentDescription = cardContentDesc
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (item.lifecycleStatus != SubscriptionLifecycleStatus.ACTIVE) {
                        SubscriptionStatusBadge(lifecycleStatus = item.lifecycleStatus)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val renewalDisplay = item.renewalStatus.toLocalizedRenewalStatusLabel()

                Text(
                    text = renewalDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.hasPriceIncrease) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SubscriptionPriceIncreaseBadge(
                        priceIncreaseFormatted = item.priceIncreaseAmount?.toLocalizedFormatted(),
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
                    text = "${item.amount.toLocalizedFormatted()} $frequencyText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoExpenseRed,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

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
    val text = lifecycleStatus.toLocalizedBadgeText()
    val (containerColor, contentColor) = when (lifecycleStatus) {
        SubscriptionLifecycleStatus.ACTIVE -> Pair(
            FeniqoSageGreen.copy(alpha = 0.15f),
            FeniqoSageGreen,
        )
        SubscriptionLifecycleStatus.PAUSED -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SubscriptionLifecycleStatus.TRIAL -> Pair(
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFD97706),
        )
        SubscriptionLifecycleStatus.CANCELLED -> Pair(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        SubscriptionLifecycleStatus.EXPIRED -> Pair(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = text
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
    val defaultPriceIncreaseText = stringResource(Res.string.subscription_detail_price_change_increased)
    val text = when {
        basisPoints != null && basisPoints > 0L -> {
            val pct = basisPoints / 100
            "+%$pct"
        }
        priceIncreaseFormatted != null -> priceIncreaseFormatted
        else -> defaultPriceIncreaseText
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = text
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
private fun resolveRenewalStatusVisuals(item: SubscriptionDisplayModel): Pair<String, Color> {
    val text = item.renewalStatus.toLocalizedRenewalStatusLabel()
    val color = when (item.renewalStatus) {
        SubscriptionRenewalStatus.Inactive -> MaterialTheme.colorScheme.onSurfaceVariant
        is SubscriptionRenewalStatus.Overdue -> FeniqoExpenseRed
        SubscriptionRenewalStatus.DueToday -> Color(0xFFD97706)
        is SubscriptionRenewalStatus.Upcoming -> MaterialTheme.colorScheme.primary
        is SubscriptionRenewalStatus.Scheduled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return text to color
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
    val dialogDescription = stringResource(Res.string.subscription_delete_dialog_title)
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = dialogDescription
        },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
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
                    text = stringResource(Res.string.subscription_delete_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.subscription_delete_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = stringResource(Res.string.subscription_delete_dialog_confirm),
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_common_cancel),
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
    amount: Money? = null,
    nextRenewalDate: LocalDate,
    calculatedFollowingRenewalDate: LocalDate? = null,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedCurrentDueDate = nextRenewalDate.toLocalizedReadableDate()
    val formattedNextDueDate = calculatedFollowingRenewalDate?.toLocalizedReadableDate()

    val displayAmount = when {
        amount == null -> ""
        amount.amountMinor <= 0L -> amount.toLocalizedFormatted()
        else -> "-${amount.toLocalizedFormatted()}"
    }

    val dialogDescription = stringResource(Res.string.subscription_advance_renewal_dialog_title)
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = dialogDescription
        },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
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
                    text = stringResource(Res.string.subscription_advance_renewal_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detay Bilgi Kutucuğu (Servis, Tarih, Kırmızı Tutar)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = formattedCurrentDueDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                val renewalExplainer = stringResource(
                    Res.string.subscription_advance_renewal_dialog_message,
                    formattedNextDueDate ?: formattedCurrentDueDate,
                )

                Text(
                    text = renewalExplainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = stringResource(Res.string.subscription_advance_renewal_dialog_confirm),
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_common_cancel),
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
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.subscription_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.subscription_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    text = stringResource(Res.string.subscription_add_action),
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
    message: String = stringResource(Res.string.subscription_error_default),
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
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
                    text = stringResource(Res.string.subscription_error_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_common_error_retry),
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
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.subscription_detail_not_found_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.subscription_detail_not_found_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_detail_not_found_back),
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
    message: String = stringResource(Res.string.subscription_payment_recorded_title),
    subtitle: String = stringResource(Res.string.subscription_payment_recorded_subtitle),
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
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(Res.string.subscription_close_button),
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
