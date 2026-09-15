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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.feniqo.mobile.presentation.util.DateFormatter
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailUiState
import com.feniqo.mobile.presentation.subscription.SubscriptionMonthlyBarModel
import com.feniqo.mobile.presentation.subscription.SubscriptionRecentPaymentModel
import com.feniqo.mobile.presentation.theme.FeniqoEmerald
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.PhoenixOrange

private val FeniqoBackgroundSand = Color(0xFFF7F5F0)
private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoExpenseRed = Color(0xFFE53935)
private val FeniqoCardBackground = Color.White
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

    val statusText = when (uiState.lifecycleStatus) {
        SubscriptionLifecycleStatus.ACTIVE -> "Aktif"
        SubscriptionLifecycleStatus.PAUSED -> "Duraklatıldı"
        SubscriptionLifecycleStatus.CANCELLED -> "İptal Edildi"
        SubscriptionLifecycleStatus.TRIAL -> "Deneme Süresi"
        SubscriptionLifecycleStatus.EXPIRED -> "Süresi Doldu"
    }

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
            color = Color(0xFF1E232A),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 3. Kategori Adı
        Text(
            text = uiState.categoryName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

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
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = uiState.formattedAmount,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = FeniqoExpenseRed,
            )
            Text(
                text = " ${uiState.frequencyUnitText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = FeniqoExpenseRed,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 6. Sonraki Yenileme Tarihi
        Text(
            text = "Sonraki yenileme: ${uiState.nextRenewalDateFormatted}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF757575),
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
            containerColor = FeniqoCardBackground,
        ),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Satır 1: Döngü
            AttributeRowItem(
                icon = Icons.Outlined.CalendarMonth,
                label = "Döngü",
                value = uiState.cycleLabel,
            )

            HorizontalDivider()

            // Satır 2: Başlangıç
            AttributeRowItem(
                icon = Icons.Outlined.CalendarMonth,
                label = "Başlangıç",
                value = uiState.startDateFormatted,
            )

            HorizontalDivider()

            // Satır 3: Bitiş
            AttributeRowItem(
                icon = Icons.Outlined.Repeat,
                label = "Bitiş",
                value = uiState.subscription?.renewalRule?.endDate?.let { DateFormatter.formatReadableDate(it) } ?: "Süresiz",
            )

            HorizontalDivider()

            // Satır 4: Alan
            AttributeRowItem(
                icon = Icons.Outlined.LocalOffer,
                label = "Alan",
                value = uiState.workspaceText,
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
                tint = Color(0xFF757575),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF424242),
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121),
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF0F0F0)),
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
            containerColor = FeniqoCardBackground,
        ),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
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
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Yenileme takibi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF212121),
                        )
                        Text(
                            text = "Yenileme tarihini takip et.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
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
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Hatırlatıcı",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF212121),
                        )
                        Text(
                            text = "Yenilemeden önce hatırlat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
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
            label = "Düzenle",
            onClick = onEditClick,
            isEnabled = isEnabled,
            modifier = Modifier.weight(1f),
        )

        // 2. Duraklat / Devam Ettir
        ActionButtonItem(
            icon = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            label = if (isPaused) "Devam Et" else "Duraklat",
            onClick = onToggleLifecycleClick,
            isEnabled = isEnabled && !isCancelled,
            accentColor = if (isPaused) FeniqoEmerald else Color(0xFFD97706),
            modifier = Modifier.weight(1f),
        )

        // 3. İptal Et
        ActionButtonItem(
            icon = Icons.Outlined.Cancel,
            label = if (isCancelled) "İptal Edildi" else "İptal Et",
            onClick = onCancelClick,
            isEnabled = isEnabled && !isCancelled,
            accentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )

        // 4. Hatırlatıcı
        ActionButtonItem(
            icon = if (reminderEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
            label = if (reminderEnabled) "Bildirim Açık" else "Bildirim Kapalı",
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
    yearlyCost: String,
    totalPaid: String,
    modifier: Modifier = Modifier,
) {
    val isNegativePaid = totalPaid.startsWith("-") || totalPaid.startsWith("−")
    val displayPaid = if (totalPaid == "0 ₺" || totalPaid == "₺0") "₺0" else if (isNegativePaid) totalPaid else "-$totalPaid"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Sol Kart: Tahmini Yıllık
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FeniqoCardBackground,
            ),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
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
                        text = "Tahmini yıllık",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF757575),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = yearlyCost,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121),
                    )
                }
            }
        }

        // Sağ Kart: Kaydedilen Toplam (Kırmızı ve Eksi)
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FeniqoCardBackground,
            ),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
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
                        .background(Color(0xFFFFEBEE), CircleShape),
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
                        text = "Kaydedilen toplam",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF757575),
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
                text = "Ödendi işaretle",
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
                    text = "Hizmet sitesini aç",
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
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Geçmiş için aşağı kaydır",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9E9E9E),
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
    if (bars.isEmpty() || bars.all { it.amountMinor == 0L }) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Aylık ödemeler",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E232A),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FeniqoCardBackground,
            ),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
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
                            if (bar.amountMinor > 0L) {
                                Text(
                                    text = bar.formattedAmount,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242),
                                    maxLines = 1,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val barHeightRatio = if (bar.amountMinor > 0L) bar.ratio.coerceIn(0.25f, 1f) else 0.05f
                            Canvas(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((60 * barHeightRatio).dp),
                            ) {
                                drawRoundRect(
                                    color = if (bar.amountMinor > 0L) Color(0xFFA5D6A7) else Color(0xFFE0E0E0),
                                    topLeft = Offset.Zero,
                                    size = size,
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = bar.monthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (bar.isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (bar.isCurrentMonth) FeniqoSageGreen else Color(0xFF757575),
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
    totalPaidFormatted: String,
    modifier: Modifier = Modifier,
) {
    if (payments.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Son ödemeler",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E232A),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FeniqoCardBackground,
            ),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                payments.forEachIndexed { index, item ->
                    val isNegative = item.formattedAmount.startsWith("-") || item.formattedAmount.startsWith("−")
                    val displayAmt = if (isNegative) item.formattedAmount else "-${item.formattedAmount}"

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
                                    .background(Color(0xFFFFEBEE), CircleShape),
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
                                    text = item.formattedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121),
                                )
                                Text(
                                    text = if (item.isManual) "Kullanıcı onayıyla kaydedildi" else "Otomatik yenileme kaydı",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575),
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
                                tint = Color(0xFF9E9E9E),
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
                    Text(
                        text = "$totalPaymentsCount ödeme kaydı • toplam $totalPaidFormatted",
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
            text = "Ek bilgiler",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E232A),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FeniqoCardBackground,
            ),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
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
                                    text = "Web sitesi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF212121),
                                )
                                Text(
                                    text = "Kayıtlı hizmet bağlantısı",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575),
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = "Aç",
                            tint = Color(0xFF757575),
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
                                    text = "Notlar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF212121),
                                )
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575),
                                )
                            }
                        }

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
                text = "Aboneliği düzenle",
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
                text = if (isPaused) "Takibi devam ettir" else "Takibi duraklat",
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
                text = if (isCancelled) "Yeniden aktifleştir" else "İptal olarak işaretle",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoExpenseRed,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Hizmet sağlayıcındaki abonelik iptal edilmez.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9E9E9E),
            textAlign = TextAlign.Center,
        )
    }
}
