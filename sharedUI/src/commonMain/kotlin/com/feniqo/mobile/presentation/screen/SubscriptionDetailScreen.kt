package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SubscriptionDetailActionButtons
import com.feniqo.mobile.presentation.component.SubscriptionDetailAttributesCard
import com.feniqo.mobile.presentation.component.SubscriptionDetailBottomActionButtons
import com.feniqo.mobile.presentation.component.SubscriptionDetailExtraInfoCard
import com.feniqo.mobile.presentation.component.SubscriptionDetailHeroCard
import com.feniqo.mobile.presentation.component.SubscriptionDetailInsightCard
import com.feniqo.mobile.presentation.component.SubscriptionDetailSettingsCard
import com.feniqo.mobile.presentation.component.SubscriptionDetailSummaryCards
import com.feniqo.mobile.presentation.component.SubscriptionMonthlyPaymentsChart
import com.feniqo.mobile.presentation.component.SubscriptionNotFoundCard
import com.feniqo.mobile.presentation.component.SubscriptionRecentPaymentsCard
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailUiState
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.subscription_common_back
import feniqomobil.sharedui.generated.resources.subscription_detail_edit_action
import feniqomobil.sharedui.generated.resources.subscription_detail_insight_empty
import feniqomobil.sharedui.generated.resources.subscription_detail_insight_with_payments_plural
import feniqomobil.sharedui.generated.resources.subscription_detail_loading_message
import feniqomobil.sharedui.generated.resources.subscription_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Görsel 1 Onaylı Abonelik Detay Ekranı.
 * Durumsuz (stateless) Compose ekranıdır. ViewModel, Hilt veya navigasyon bağımlılığı taşımaz.
 * Doğal dikey kaydırma (vertical scroll) ile üst ve alt paneller tek akışta sunulur.
 */
@Composable
fun SubscriptionDetailScreen(
    uiState: SubscriptionDetailUiState,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onToggleLifecycleClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleReminderClick: () -> Unit,
    onAdvanceRenewal: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            uiState.isLoading -> {
                LoadingContent(
                    message = stringResource(Res.string.subscription_detail_loading_message),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            uiState.isNotFound || uiState.subscription == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SubscriptionNotFoundCard(onNavigateBack = onBack)
                }
            }
            else -> {
                val isEnabled = !uiState.isActionSubmitting

                // 1. Üst Bar: Geri Dönüş ("Abonelikler") ve Düzenle Butonu (Kalem)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            enabled = isEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(Res.string.subscription_common_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = stringResource(Res.string.subscription_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    IconButton(
                        onClick = onEditClick,
                        enabled = isEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(Res.string.subscription_detail_edit_action),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // 2. Doğal Dikey Kaydırılabilir Gövde (Görsel 1'deki Detay ve Aşağı Kaydırılmış Devamı)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Hero Kartı: İkon, Ad, Kategori, Durum, Büyük Kırmızı Fiyat ve Sonraki Yenileme
                    SubscriptionDetailHeroCard(uiState = uiState)

                    // 2. Nitelikler Kartı: Döngü, Başlangıç, Bitiş, Alan
                    SubscriptionDetailAttributesCard(uiState = uiState)

                    // 3. Yenileme Takibi ve Hatırlatıcı Switch Kartı
                    SubscriptionDetailSettingsCard(
                        reminderEnabled = uiState.reminderEnabled,
                        isAutoRenewActive = uiState.isAutoRenewActive,
                        onToggleReminder = onToggleReminderClick,
                        onToggleAutoRenew = onToggleLifecycleClick,
                    )

                    // 4. 2'li Özet Kartları: Tahmini Yıllık & Kaydedilen Toplam
                    SubscriptionDetailSummaryCards(
                        yearlyCost = uiState.yearlyCost,
                        totalPaid = uiState.totalPaid,
                    )

                    // 5. Üst Aksiyon Butonları: "Ödendi işaretle" ve "Hizmet sitesini aç"
                    SubscriptionDetailActionButtons(
                        websiteUrl = uiState.websiteUrl,
                        isSubmitting = uiState.isActionSubmitting,
                        onAdvanceRenewal = onAdvanceRenewal,
                        onManageSubscription = onManageSubscription,
                    )

                    // Varsa İçgörü Kartı
                    val insightMessage = if (uiState.totalPaymentsCount > 0 && uiState.totalPaid != null) {
                        pluralStringResource(
                            Res.plurals.subscription_detail_insight_with_payments_plural,
                            uiState.totalPaymentsCount,
                            uiState.totalPaymentsCount,
                            uiState.totalPaid?.toLocalizedFormatted() ?: "—",
                        )
                    } else {
                        stringResource(Res.string.subscription_detail_insight_empty)
                    }
                    SubscriptionDetailInsightCard(insightMessage = insightMessage)

                    // 6. Aylık Ödemeler Çubuk Grafiği (Aynı ekranda aşağı kaydırınca)
                    SubscriptionMonthlyPaymentsChart(bars = uiState.monthlyChartBars)

                    // 7. Son Ödemeler Listesi
                    SubscriptionRecentPaymentsCard(
                        payments = uiState.recentPayments,
                        totalPaymentsCount = uiState.recentPayments.size,
                        totalPaid = uiState.totalPaid,
                    )

                    // 8. Ek Bilgiler: Web sitesi ve Notlar
                    SubscriptionDetailExtraInfoCard(
                        websiteUrl = uiState.websiteUrl,
                        notes = uiState.notes,
                        onOpenWebsite = onManageSubscription,
                    )

                    // 9. Alt Aksiyon Butonları: "Aboneliği düzenle", "Takibi duraklat", "İptal olarak işaretle"
                    SubscriptionDetailBottomActionButtons(
                        lifecycleStatus = uiState.lifecycleStatus,
                        isSubmitting = uiState.isActionSubmitting,
                        onEditClick = onEditClick,
                        onToggleLifecycleClick = onToggleLifecycleClick,
                        onCancelClick = onCancelClick,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
