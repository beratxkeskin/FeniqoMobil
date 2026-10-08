package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory

/**
 * Aylık ödemeler çubuk grafiğinde bir ayı temsil eden saf UI modeli.
 */
data class SubscriptionMonthlyBarModel(
    val year: Int,
    val monthNumber: Int,
    val amount: Money,
    val isCurrentMonth: Boolean,
    val ratio: Float, // 0f..1f (ratio to maximum month)
)

/**
 * Son gerçekleşen ödemeler listesindeki bir ödeme satırı modeli.
 */
data class SubscriptionRecentPaymentModel(
    val id: String,
    val paymentDate: LocalDate,
    val amount: Money,
    val isManual: Boolean,
)

/**
 * Abonelik Detay Ekranı'nın reaktif ve saf UI durum modeli.
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

    val categoryName: String?
        get() = category?.name

    val categoryIconKey: String?
        get() = category?.icon?.key

    val categoryColorHex: String?
        get() = category?.color?.hex

    /**
     * Yıllık normalize edilmiş tahmini maliyet.
     */
    val yearlyCost: Money?
        get() {
            val sub = subscription ?: return null
            val interval = sub.renewalRule.interval.coerceAtLeast(1)
            val yearlyMinor =
                when (sub.renewalRule.frequency) {
                    RecurrenceFrequency.MONTHLY -> (sub.amount.amountMinor * 12) / interval
                    RecurrenceFrequency.YEARLY -> sub.amount.amountMinor / interval
                    RecurrenceFrequency.WEEKLY -> (sub.amount.amountMinor * 52) / interval
                    RecurrenceFrequency.DAILY -> (sub.amount.amountMinor * 365) / interval
                }
            return Money(yearlyMinor, sub.amount.currency)
        }

    /**
     * Gerçekleştirilmiş toplam ödeme adedi.
     */
    val totalPaymentsCount: Int
        get() = payments.size

    /**
     * Şimdiye kadar bu aboneliğe yapılmış gerçek kümülatif ödeme tutarı.
     */
    val totalPaid: Money?
        get() {
            if (payments.isEmpty()) return null
            val currency = subscription?.amount?.currency ?: payments.first().amount.currency
            val totalMinor = payments.sumOf { it.amount.amountMinor }
            return Money(totalMinor, currency)
        }

    val latestPriceIncreaseBasisPoints: Long?
        get() = priceHistories.maxByOrNull { it.changedAt }?.increaseBasisPoints

    val isPriceIncreased: Boolean
        get() = priceHistories.maxByOrNull { it.changedAt }?.isPriceIncreased == true
}
