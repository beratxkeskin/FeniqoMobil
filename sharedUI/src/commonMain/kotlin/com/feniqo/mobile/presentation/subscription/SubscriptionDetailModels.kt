package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Aylık ödemeler çubuk grafiğinde bir ayı temsil eden UI modeli.
 */
data class SubscriptionMonthlyBarModel(
    val monthLabel: String,
    val year: Int,
    val monthNumber: Int,
    val amountMinor: Long,
    val formattedAmount: String,
    val isCurrentMonth: Boolean,
    val ratio: Float, // 0f..1f (en yüksek aya oran)
)

/**
 * Son gerçekleşen ödemeler listesindeki bir ödeme satırı modeli.
 */
data class SubscriptionRecentPaymentModel(
    val id: String,
    val formattedDate: String,
    val formattedAmount: String,
    val isManual: Boolean,
)

/**
 * Görsel 2'deki Abonelik Detay Ekranı'nın reaktif ve zengin UI durum modeli.
 */
data class SubscriptionDetailUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val subscription: Subscription? = null,
    val category: Category? = null,
    val priceHistories: List<SubscriptionPriceHistory> = emptyList(),
    val payments: List<SubscriptionPayment> = emptyList(),
    val monthlyChartBars: List<SubscriptionMonthlyBarModel> = emptyList(),
    val recentPayments: List<SubscriptionRecentPaymentModel> = emptyList(),
    val activeWorkspaceName: String? = null,
    val isActionSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val name: String get() = subscription?.name ?: ""
    val formattedAmount: String
        get() = subscription?.let { MoneyFormatter.format(it.amount) } ?: ""

    val frequencyUnitText: String
        get() = when (subscription?.renewalRule?.frequency) {
            RecurrenceFrequency.DAILY -> "/ gün"
            RecurrenceFrequency.WEEKLY -> "/ hafta"
            RecurrenceFrequency.MONTHLY -> "/ ay"
            RecurrenceFrequency.YEARLY -> "/ yıl"
            null -> ""
        }

    val cycleLabel: String
        get() = when (subscription?.renewalRule?.frequency) {
            RecurrenceFrequency.DAILY -> "Günlük"
            RecurrenceFrequency.WEEKLY -> "Haftalık"
            RecurrenceFrequency.MONTHLY -> "Aylık"
            RecurrenceFrequency.YEARLY -> "Yıllık"
            null -> "—"
        }

    val startDateFormatted: String
        get() = subscription?.renewalRule?.startDate?.let { DateFormatter.formatReadableDate(it) } ?: "—"

    val nextRenewalDateFormatted: String
        get() = subscription?.nextRenewalDate?.let { DateFormatter.formatReadableDate(it) } ?: "—"

    val lifecycleStatus: SubscriptionLifecycleStatus
        get() = subscription?.lifecycleStatus ?: SubscriptionLifecycleStatus.ACTIVE

    val isAutoRenewActive: Boolean
        get() = lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE || lifecycleStatus == SubscriptionLifecycleStatus.TRIAL

    val reminderEnabled: Boolean
        get() = subscription?.reminderEnabled ?: true

    val websiteUrl: String?
        get() = subscription?.websiteUrl?.ifBlank { null }

    val notes: String?
        get() = subscription?.notes?.ifBlank { null }

    val categoryName: String
        get() = category?.name ?: "Kategori Belirtilmedi"

    val categoryIconKey: String?
        get() = category?.icon?.key

    val categoryColorHex: String?
        get() = category?.color?.hex

    val workspaceText: String
        get() = activeWorkspaceName ?: "Yalnızca ben"

    /**
     * Yıllık normalize edilmiş tahmini maliyet.
     */
    val yearlyCostFormatted: String
        get() {
            val sub = subscription ?: return "—"
            val interval = sub.renewalRule.interval.coerceAtLeast(1)
            val yearlyMinor = when (sub.renewalRule.frequency) {
                RecurrenceFrequency.MONTHLY -> (sub.amount.amountMinor * 12) / interval
                RecurrenceFrequency.YEARLY -> sub.amount.amountMinor / interval
                RecurrenceFrequency.WEEKLY -> (sub.amount.amountMinor * 52) / interval
                RecurrenceFrequency.DAILY -> (sub.amount.amountMinor * 365) / interval
            }
            return MoneyFormatter.format(Money(yearlyMinor, sub.amount.currency))
        }

    /**
     * Gerçek fiyat geçmişine dayalı değişim oranı metni.
     */
    val priceChangeText: String
        get() {
            if (priceHistories.isEmpty()) return "Değişiklik yok"
            val latestHistory = priceHistories.maxByOrNull { it.changedAt } ?: return "Değişiklik yok"
            val bp = latestHistory.increaseBasisPoints
            return if (bp != null && bp > 0L) {
                val pct = bp / 100
                "+%$pct"
            } else if (latestHistory.isPriceIncreased) {
                "Fiyat arttı"
            } else {
                "Değişiklik yok"
            }
        }

    /**
     * Gerçekleştirilmiş toplam ödeme adedi.
     */
    val totalPaymentsCount: Int
        get() = payments.size

    /**
     * Şimdiye kadar bu aboneliğe yapılmış gerçek kümülatif ödeme tutarı.
     */
    val totalPaidFormatted: String
        get() {
            if (payments.isEmpty()) return "0 ₺"
            val currency = subscription?.amount?.currency ?: payments.first().amount.currency
            val totalMinor = payments.sumOf { it.amount.amountMinor }
            return MoneyFormatter.format(Money(totalMinor, currency))
        }

    /**
     * Feniqo Insight bilgilendirme cümlesi.
     */
    val insightMessage: String
        get() {
            return if (payments.isNotEmpty()) {
                val totalMinor = payments.sumOf { it.amount.amountMinor }
                val currency = subscription?.amount?.currency ?: payments.first().amount.currency
                val formatted = MoneyFormatter.format(Money(totalMinor, currency))
                "Bu abonelik için toplam $totalPaymentsCount ödeme ile $formatted harcama gerçekleştirdiniz."
            } else {
                "Bu servis için henüz gerçekleşen ödeme kaydedilmedi. Vadesi geldiğinde ödendi işaretleyerek harcama geçmişinizi oluşturabilirsiniz."
            }
        }
}
