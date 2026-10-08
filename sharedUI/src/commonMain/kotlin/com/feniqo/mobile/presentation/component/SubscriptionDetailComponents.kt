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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.presentation.common.localizedShortMonthName
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailUiState
import com.feniqo.mobile.presentation.subscription.SubscriptionMonthlyBarModel
import com.feniqo.mobile.presentation.subscription.SubscriptionRecentPaymentModel
import com.feniqo.mobile.presentation.subscription.toLocalizedBadgeText
import com.feniqo.mobile.presentation.subscription.toLocalizedFrequencySummary
import com.feniqo.mobile.presentation.theme.FeniqoEmerald
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.PhoenixOrange
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.subscription_amounts_disclaimer
import feniqomobil.sharedui.generated.resources.subscription_badge_cancelled
import feniqomobil.sharedui.generated.resources.subscription_detail_action_advance_renewal
import feniqomobil.sharedui.generated.resources.subscription_detail_action_cancel
import feniqomobil.sharedui.generated.resources.subscription_detail_action_manage
import feniqomobil.sharedui.generated.resources.subscription_detail_action_pause
import feniqomobil.sharedui.generated.resources.subscription_detail_action_resume
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_cycle
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_end_date
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_no_end_date
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_start_date
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_workspace
import feniqomobil.sharedui.generated.resources.subscription_detail_attr_workspace_personal
import feniqomobil.sharedui.generated.resources.subscription_detail_auto_renew_desc
import feniqomobil.sharedui.generated.resources.subscription_detail_auto_renew_title
import feniqomobil.sharedui.generated.resources.subscription_detail_chart_current_month
import feniqomobil.sharedui.generated.resources.subscription_detail_chart_no_payments
import feniqomobil.sharedui.generated.resources.subscription_detail_chart_subtitle
import feniqomobil.sharedui.generated.resources.subscription_detail_chart_title
import feniqomobil.sharedui.generated.resources.subscription_detail_edit_action
import feniqomobil.sharedui.generated.resources.subscription_detail_extra_notes
import feniqomobil.sharedui.generated.resources.subscription_detail_extra_title
import feniqomobil.sharedui.generated.resources.subscription_detail_extra_website
import feniqomobil.sharedui.generated.resources.subscription_detail_next_renewal
import feniqomobil.sharedui.generated.resources.subscription_detail_payment_auto
import feniqomobil.sharedui.generated.resources.subscription_detail_payment_manual
import feniqomobil.sharedui.generated.resources.subscription_detail_recent_no_payments
import feniqomobil.sharedui.generated.resources.subscription_detail_recent_payments_subtitle_plural
import feniqomobil.sharedui.generated.resources.subscription_detail_recent_payments_title
import feniqomobil.sharedui.generated.resources.subscription_detail_reminder_desc
import feniqomobil.sharedui.generated.resources.subscription_detail_reminder_title
import feniqomobil.sharedui.generated.resources.subscription_detail_summary_price_change_title
import feniqomobil.sharedui.generated.resources.subscription_detail_summary_total_paid_title
import feniqomobil.sharedui.generated.resources.subscription_detail_summary_yearly_title
import feniqomobil.sharedui.generated.resources.subscription_form_reminder
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val FeniqoBackgroundSand = Color(0xFFF7F5F0)
private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoExpenseRed = Color(0xFFE53935)
private val FeniqoIconBgGreen = Color(0xFFE8F5E9)

/**
 * Görsel 1 Onaylı Detay Hero Kartı:
 * Büyük açık yeşil yuvarlak içinde semantik kategori ikonu, servis adı, kategori,
 * durum rozeti, kırmızı büyük fiyat ve sonraki yenileme tarihi.
 */
@Composable
fun SubscriptionDetailHeroCard(
    uiState: SubscriptionDetailUiState,
    modifier: Modifier = Modifier,
) {
    val statusContainerColor = when (uiState.lifecycleStatus) {
        SubscriptionLifecycleStatus.ACTIVE -> Color(0xFFE8F5E9)
        SubscriptionLifecycleStatus.PAUSED -> Color(0xFFFFF3E0)
        SubscriptionLifecycleStatus.CANCELLED -> Color(0xFFFFEBEE)
        SubscriptionLifecycleStatus.TRIAL -> Color(0xFFE3F2FD)
        SubscriptionLifecycleStatus.EXPIRED -> Color(0xFFEEEEEE)
    }

    val statusContentColor = when (uiState.lifecycleStatus) {
        SubscriptionLifecycleStatus.ACTIVE -> Color(0xFF2D5A43)
        SubscriptionLifecycleStatus.PAUSED -> Color(0xFFD97706)
        SubscriptionLifecycleStatus.CANCELLED -> FeniqoExpenseRed
        SubscriptionLifecycleStatus.TRIAL -> Color(0xFF1976D2)
        SubscriptionLifecycleStatus.EXPIRED -> Color(0xFF757575)
    }

    val statusText = uiState.lifecycleStatus.toLocalizedBadgeText()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Büyük İkon Kapı
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(FeniqoIconBgGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategorySemanticIconResolver.resolve(uiState.categoryIconKey),
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Servis Adı
        Text(
            text = uiState.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 3. Kategori Adı
        uiState.categoryName?.let { catName ->
            Text(
                text = catName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 4. Durum Rozeti
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusContainerColor,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusContentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Büyük Kırmızı Plan Fiyatı
        val amountFormatted = uiState.subscription?.amount?.toLocalizedFormatted()
            ?: Money(0L, Currency.TRY).toLocalizedFormatted()
        val freqSummary = uiState.subscription?.renewalRule?.let { toLocalizedFrequencySummary(it.frequency, it.interval) } ?: ""
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = amountFormatted,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = FeniqoExpenseRed,
            )
            if (freqSummary.isNotBlank()) {
                Text(
                    text = " $freqSummary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = FeniqoExpenseRed,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 6. Sonraki Yenileme Tarihi
        val nextRenewalText = uiState.subscription?.nextRenewalDate?.toLocalizedReadableDate() ?: "—"
        Text(
            text = stringResource(Res.string.subscription_detail_next_renewal, nextRenewalText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Görsel 1: Nitelikler Kartı (Beyaz Kart, 4 Satır):
 * Döngü, Başlangıç, Bitiş, Alan.
 */
@Composable
fun SubscriptionDetailAttributesCard(
    uiState: SubscriptionDetailUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Satır 1: Döngü
            val cycleValue = uiState.subscription?.renewalRule?.let {
                toLocalizedFrequencySummary(it.frequency, it.interval)
            } ?: "—"
            AttributeRowItem(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(Res.string.subscription_detail_attr_cycle),
                value = cycleValue,
            )

            HorizontalDivider()

            // Satır 2: Başlangıç
            val startDateValue = uiState.subscription?.renewalRule?.startDate?.toLocalizedReadableDate() ?: "—"
            AttributeRowItem(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(Res.string.subscription_detail_attr_start_date),
                value = startDateValue,
            )

            HorizontalDivider()

            // Satır 3: Bitiş
            val endDateValue = uiState.subscription?.renewalRule?.endDate?.toLocalizedReadableDate()
                ?: stringResource(Res.string.subscription_detail_attr_no_end_date)
            AttributeRowItem(
                icon = Icons.Outlined.Repeat,
                label = stringResource(Res.string.subscription_detail_attr_end_date),
                value = endDateValue,
            )

            HorizontalDivider()

            // Satır 4: Alan
            val workspaceValue = uiState.activeWorkspaceName ?: stringResource(Res.string.subscription_detail_attr_workspace_personal)
            AttributeRowItem(
                icon = Icons.Outlined.LocalOffer,
                label = stringResource(Res.string.subscription_detail_attr_workspace),
                value = workspaceValue,
            )
        }
    }
}

@Composable
private fun AttributeRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * Görsel 1: Yenileme Takibi ve Hatırlatıcı Switch Kartı.
 */
@Composable
fun SubscriptionDetailSettingsCard(
    reminderEnabled: Boolean,
    isAutoRenewActive: Boolean,
    onToggleReminder: () -> Unit,
    onToggleAutoRenew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Yenileme Takibi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.subscription_detail_auto_renew_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.subscription_detail_auto_renew_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                androidx.compose.material3.Switch(
                    checked = isAutoRenewActive,
                    onCheckedChange = { onToggleAutoRenew() },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FeniqoSageGreen,
                    ),
                )
            }

            HorizontalDivider()

            // Hatırlatıcı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.subscription_detail_reminder_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.subscription_detail_reminder_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                androidx.compose.material3.Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { onToggleReminder() },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FeniqoSageGreen,
                    ),
                )
            }
        }
    }
}

/**
 * Görsel 2: 4'lü Hızlı Eylem Buton Sırası.
 * Düzenle, Duraklat/Devam, İptal Et, Hatırlatıcı.
 */
@Composable
fun SubscriptionDetailActionRow(
    lifecycleStatus: SubscriptionLifecycleStatus,
    reminderEnabled: Boolean,
    onEditClick: () -> Unit,
    onToggleLifecycleClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleReminderClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isPaused = lifecycleStatus == SubscriptionLifecycleStatus.PAUSED
    val isCancelled = lifecycleStatus == SubscriptionLifecycleStatus.CANCELLED

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // 1. Düzenle
        ActionButtonItem(
            icon = Icons.Outlined.Edit,
            label = stringResource(Res.string.subscription_detail_edit_action),
            onClick = onEditClick,
            isEnabled = isEnabled,
            modifier = Modifier.weight(1f),
        )

        // 2. Duraklat / Devam Ettir
        ActionButtonItem(
            icon = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            label = if (isPaused) {
                stringResource(Res.string.subscription_detail_action_resume)
            } else {
                stringResource(Res.string.subscription_detail_action_pause)
            },
            onClick = onToggleLifecycleClick,
            isEnabled = isEnabled && !isCancelled,
            accentColor = if (isPaused) FeniqoEmerald else Color(0xFFD97706),
            modifier = Modifier.weight(1f),
        )

        // 3. İptal Et
        ActionButtonItem(
            icon = Icons.Outlined.Cancel,
            label = if (isCancelled) {
                stringResource(Res.string.subscription_badge_cancelled)
            } else {
                stringResource(Res.string.subscription_detail_action_cancel)
            },
            onClick = onCancelClick,
            isEnabled = isEnabled && !isCancelled,
            accentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )

        // 4. Hatırlatıcı
        ActionButtonItem(
            icon = if (reminderEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
            label = stringResource(Res.string.subscription_form_reminder),
            onClick = onToggleReminderClick,
            isEnabled = isEnabled,
            accentColor = if (reminderEnabled) FeniqoEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButtonItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(enabled = isEnabled, onClick = onClick),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Görsel 2: Feniqo Insight Kartı.
 * Gerçek ödeme geçmişine dayalı özet mesajı sunar.
 */
@Composable
fun SubscriptionDetailInsightCard(
    insightMessage: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoSageGreen.copy(alpha = 0.08f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = FeniqoSageGreen.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(FeniqoSageGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = FeniqoSageGreen,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = insightMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Görsel 1: 2'li Özet Kartı (Tahmini Yıllık & Kaydedilen Toplam).
 */
@Composable
fun SubscriptionDetailSummaryCards(
    yearlyCost: Money?,
    totalPaid: Money?,
    modifier: Modifier = Modifier,
) {
    val yearlyText = yearlyCost?.toLocalizedFormatted() ?: Money(0L, Currency.TRY).toLocalizedFormatted()
    val totalPaidText = totalPaid?.toLocalizedFormatted() ?: Money(0L, Currency.TRY).toLocalizedFormatted()
    val isNegativePaid = totalPaidText.startsWith("-") || totalPaidText.startsWith("−")
    val displayPaid = if (isNegativePaid) totalPaidText else "-$totalPaidText"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Sol Kart: Tahmini Yıllık
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(FeniqoIconBgGreen, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column {
                    Text(
                        text = stringResource(Res.string.subscription_detail_summary_yearly_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = yearlyText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Sağ Kart: Kaydedilen Toplam (Kırmızı ve Eksi)
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = FeniqoExpenseRed,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column {
                    Text(
                        text = stringResource(Res.string.subscription_detail_summary_total_paid_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayPaid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoExpenseRed,
                    )
                }
            }
        }
    }
}

/**
 * Görsel 1: İlk Aksiyon Butonları ("Ödendi işaretle" ve "Hizmet sitesini aç").
 */
@Composable
fun SubscriptionDetailActionButtons(
    websiteUrl: String?,
    isSubmitting: Boolean,
    onAdvanceRenewal: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Ödendi işaretle
        Button(
            onClick = onAdvanceRenewal,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoSageGreen,
                contentColor = Color.White,
            ),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(Res.string.subscription_detail_action_advance_renewal),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 2. Hizmet sitesini aç (Varsa)
        if (!websiteUrl.isNullOrBlank()) {
            OutlinedButton(
                onClick = onManageSubscription,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FeniqoSageGreen),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FeniqoSageGreen,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = FeniqoSageGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.subscription_detail_action_manage),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // "Geçmiş için aşağı kaydır" ipucu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(Res.string.subscription_detail_chart_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Görsel 1: Aylık Ödemeler Grafiği (Monthly payments).
 * Gerçek ödeme kayıtları varsa açık yeşil çubuklar ve üstlerinde tutarlarıyla gösterilir.
 */
@Composable
fun SubscriptionMonthlyPaymentsChart(
    bars: List<SubscriptionMonthlyBarModel>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty() || bars.all { it.amount.amountMinor == 0L }) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.subscription_detail_chart_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { bar ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (bar.amount.amountMinor > 0L) {
                                Text(
                                    text = bar.amount.toLocalizedFormatted(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val barHeightRatio = if (bar.amount.amountMinor > 0L) bar.ratio.coerceIn(0.25f, 1f) else 0.05f
                            Canvas(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((60 * barHeightRatio).dp),
                            ) {
                                drawRoundRect(
                                    color = if (bar.amount.amountMinor > 0L) Color(0xFFA5D6A7) else Color(0xFFE0E0E0),
                                    topLeft = Offset.Zero,
                                    size = size,
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = localizedShortMonthName(bar.monthNumber),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (bar.isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (bar.isCurrentMonth) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Görsel 1: Son Ödemeler Listesi.
 * Kırmızı makbuz ikonu, "18 Ağustos 2026 - Kullanıcı onayıyla kaydedildi", "-₺499".
 */
@Composable
fun SubscriptionRecentPaymentsCard(
    payments: List<SubscriptionRecentPaymentModel>,
    totalPaymentsCount: Int,
    totalPaid: Money?,
    modifier: Modifier = Modifier,
) {
    if (payments.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.subscription_detail_recent_payments_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                payments.forEachIndexed { index, item ->
                    val amtFormatted = item.amount.toLocalizedFormatted()
                    val isNegative = amtFormatted.startsWith("-") || amtFormatted.startsWith("−")
                    val displayAmt = if (isNegative) amtFormatted else "-$amtFormatted"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreditCard,
                                    contentDescription = null,
                                    tint = FeniqoExpenseRed,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Column {
                                Text(
                                    text = item.paymentDate.toLocalizedReadableDate(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (item.isManual) {
                                        stringResource(Res.string.subscription_detail_payment_manual)
                                    } else {
                                        stringResource(Res.string.subscription_detail_payment_auto)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = displayAmt,
                                style = MaterialTheme.typography.bodyLarge,
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

                    if (index < payments.lastIndex) {
                        HorizontalDivider()
                    }
                }

                HorizontalDivider()

                // Özet Satırı: "6 ödeme kaydı • toplam ₺2.994"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    val totalPaidText = totalPaid?.toLocalizedFormatted() ?: "—"
                    Text(
                        text = pluralStringResource(
                            Res.plurals.subscription_detail_recent_payments_subtitle_plural,
                            totalPaymentsCount,
                            totalPaymentsCount,
                            totalPaidText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF616161),
                    )
                }
            }
        }
    }
}

/**
 * Görsel 1: Ek Bilgiler (Web Sitesi ve Notlar).
 */
@Composable
fun SubscriptionDetailExtraInfoCard(
    websiteUrl: String?,
    notes: String?,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (websiteUrl.isNullOrBlank() && notes.isNullOrBlank()) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.subscription_detail_extra_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Web Sitesi
                if (!websiteUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWebsite() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = Color(0xFF616161),
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(Res.string.subscription_detail_extra_website),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.subscription_detail_action_manage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = stringResource(Res.string.subscription_detail_action_manage),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (!websiteUrl.isNullOrBlank() && !notes.isNullOrBlank()) {
                    HorizontalDivider()
                }

                // Notlar
                if (!notes.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF616161),
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(Res.string.subscription_detail_extra_notes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

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
    }
}

/**
 * Görsel 1: Alt Aksiyon Butonları:
 * 1. "Aboneliği düzenle" (Koyu yeşil buton)
 * 2. "Takibi duraklat" / "Takibi devam ettir" (Yeşil border, || ikonu)
 * 3. "İptal olarak işaretle" / "Yeniden aktifleştir" (Kırmızı border, bayrak ikonu)
 * Dipnot: "Hizmet sağlayıcındaki abonelik iptal edilmez."
 */
@Composable
fun SubscriptionDetailBottomActionButtons(
    lifecycleStatus: SubscriptionLifecycleStatus,
    isSubmitting: Boolean,
    onEditClick: () -> Unit,
    onToggleLifecycleClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPaused = lifecycleStatus == SubscriptionLifecycleStatus.PAUSED
    val isCancelled = lifecycleStatus == SubscriptionLifecycleStatus.CANCELLED

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Aboneliği düzenle (Koyu yeşil buton)
        Button(
            onClick = onEditClick,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoSageGreen,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.subscription_detail_edit_action),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 2. Takibi duraklat / Takibi devam ettir (Yeşil border)
        OutlinedButton(
            onClick = onToggleLifecycleClick,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, FeniqoSageGreen),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = FeniqoSageGreen,
            ),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPaused) {
                    stringResource(Res.string.subscription_detail_action_resume)
                } else {
                    stringResource(Res.string.subscription_detail_action_pause)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 3. İptal olarak işaretle / Yeniden aktifleştir (Kırmızı border)
        OutlinedButton(
            onClick = onCancelClick,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, FeniqoExpenseRed),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = FeniqoExpenseRed,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Cancel,
                contentDescription = null,
                tint = FeniqoExpenseRed,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCancelled) {
                    stringResource(Res.string.subscription_detail_action_resume)
                } else {
                    stringResource(Res.string.subscription_detail_action_cancel)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoExpenseRed,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(Res.string.subscription_amounts_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
