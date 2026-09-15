package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory

/**
 * Abonelik filtre türü.
 */
enum class SubscriptionFilter {
    ALL,
    ACTIVE,
    UPCOMING,
    OVERDUE,
    PAUSED,
    CANCELLED,
    TRIAL,
}

/**
 * Tek bir para birimi için normalize tahmini abonelik maliyet özeti.
 */
data class SubscriptionEstimatedCostSummary(
    val currency: Currency,
    val monthlyEstimatedMinor: Long,
    val yearlyEstimatedMinor: Long,
    val activeSubscriptionCount: Int,
    val isOverflowOrUnavailable: Boolean = false,
)

/**
 * Tek bir para birimi için gerçekleşen dönem harcama ve trend sonucu.
 */
data class SubscriptionTrendResult(
    val currency: Currency,
    val currentMonthActualMinor: Long,
    val previousMonthActualMinor: Long,
    val diffMinor: Long,
    val percentageBasisPoints: Long?,
    val isPreviousMonthZero: Boolean,
    val isCurrentMonthIncomplete: Boolean = true,
)

/**
 * Gerçek veriye dayalı güvenli abonelik içgörüleri.
 */
sealed interface SubscriptionInsight {
    /** En yüksek tahmini aylık maliyetli abonelik */
    data class TopCost(
        val subscriptionName: String,
        val monthlyEstimated: Money,
    ) : SubscriptionInsight

    /** Deneme süresi yaklaşan abonelik */
    data class TrialEndingSoon(
        val subscriptionName: String,
        val daysRemaining: Long,
        val trialEndDate: LocalDate,
    ) : SubscriptionInsight

    /** Fiyatı artan servisler sayısı */
    data class PriceIncreases(
        val count: Int,
    ) : SubscriptionInsight

    /** Duraklatılmış aboneliklerden elde edilen tahmini aylık tasarruf */
    data class PausedSavings(
        val monthlyEstimatedSavings: Money,
        val pausedCount: Int,
    ) : SubscriptionInsight
}

/**
 * Abonelikler ekranı ve analitikleri için saf, taşma korumalı domain hesaplayıcısı.
 */
object SubscriptionAnalyticsCalculator {

    const val UPCOMING_WINDOW_DAYS = 7

    /**
     * Güvenli çarpma: taşma olursa null döner.
     */
    fun safeMultiply(a: Long, b: Long): Long? {
        if (a == 0L || b == 0L) return 0L
        val result = a * b
        return if (result / a != b) null else result
    }

    /**
     * Güvenli toplama: taşma olursa null döner.
     */
    fun safeAdd(a: Long, b: Long): Long? {
        val result = a + b
        // İki pozitif sayının toplamı negatif olamaz
        return if ((a > 0L && b > 0L && result <= 0L) || (a < 0L && b < 0L && result >= 0L)) {
            null
        } else {
            result
        }
    }

    /**
     * Tek bir abonelik için tahmini yıllık maliyet (minor unit) hesaplar.
     * Günlük: amount * 365 / interval
     * Haftalık: amount * 52 / interval
     * Aylık: amount * 12 / interval
     * Yıllık: amount / interval
     * Taşma durumunda null döner.
     */
    fun calculateYearlyEstimatedMinor(subscription: Subscription): Long? {
        val interval = subscription.renewalRule.interval
        if (interval <= 0) return null
        val amount = subscription.amount.amountMinor
        if (amount <= 0L) return 0L

        val baseMultiplied = when (subscription.renewalRule.frequency) {
            RecurrenceFrequency.DAILY -> safeMultiply(amount, 365L)
            RecurrenceFrequency.WEEKLY -> safeMultiply(amount, 52L)
            RecurrenceFrequency.MONTHLY -> safeMultiply(amount, 12L)
            RecurrenceFrequency.YEARLY -> amount
        } ?: return null

        return baseMultiplied / interval.toLong()
    }

    /**
     * Tek bir abonelik için tahmini aylık maliyet (minor unit) hesaplar.
     * Önce güvenli yıllık tahmin hesaplanır, ardından 12'ye bölünür.
     * Yuvarlama politikası: En yakın minor unit, tam yarıda yukarı yuvarlama: (yearly + 6) / 12.
     * Taşma durumunda null döner.
     */
    fun calculateMonthlyEstimatedMinor(subscription: Subscription): Long? {
        val yearly = calculateYearlyEstimatedMinor(subscription) ?: return null
        if (yearly == 0L) return 0L
        val withHalf = safeAdd(yearly, 6L) ?: return null
        return withHalf / 12L
    }

    /**
     * Aktif ve trial durumundaki aboneliklerin para birimi bazında tahmini normalize maliyetini hesaplar.
     * Farklı para birimleri asla birleştirilmez.
     */
    fun calculateEstimatedCosts(
        subscriptions: List<Subscription>,
    ): Map<Currency, SubscriptionEstimatedCostSummary> {
        val activeOrTrial = subscriptions.filter {
            it.lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE ||
                it.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL
        }

        val grouped = activeOrTrial.groupBy { it.amount.currency }
        val result = mutableMapOf<Currency, SubscriptionEstimatedCostSummary>()

        for ((currency, list) in grouped) {
            var totalYearly = 0L
            var totalMonthly = 0L
            var hasOverflow = false

            for (sub in list) {
                val subYearly = calculateYearlyEstimatedMinor(sub)
                val subMonthly = calculateMonthlyEstimatedMinor(sub)

                if (subYearly == null || subMonthly == null) {
                    hasOverflow = true
                    break
                }

                val addedYearly = safeAdd(totalYearly, subYearly)
                val addedMonthly = safeAdd(totalMonthly, subMonthly)

                if (addedYearly == null || addedMonthly == null) {
                    hasOverflow = true
                    break
                }

                totalYearly = addedYearly
                totalMonthly = addedMonthly
            }

            result[currency] = SubscriptionEstimatedCostSummary(
                currency = currency,
                monthlyEstimatedMinor = if (hasOverflow) 0L else totalMonthly,
                yearlyEstimatedMinor = if (hasOverflow) 0L else totalYearly,
                activeSubscriptionCount = list.size,
                isOverflowOrUnavailable = hasOverflow,
            )
        }

        return result
    }

    /**
     * Gerçek ödeme olaylarından belirli bir takvim ayı için gerçekleşen harcama toplamını hesaplar.
     * @param yearMonth Format: YYYY-MM
     */
    fun calculateActualMonthlySpending(
        payments: List<SubscriptionPayment>,
        yearMonth: String,
    ): Map<Currency, Long> {
        val filtered = payments.filter {
            val dateStr = it.paymentDate.toString()
            dateStr.startsWith(yearMonth)
        }

        val grouped = filtered.groupBy { it.amount.currency }
        val result = mutableMapOf<Currency, Long>()

        for ((currency, list) in grouped) {
            var sum = 0L
            for (p in list) {
                sum = safeAdd(sum, p.amount.amountMinor) ?: Long.MAX_VALUE
            }
            result[currency] = sum
        }

        return result
    }

    /**
     * Cari ay ile önceki tam ayın gerçekleşen ödemelerini karşılaştırarak trend hesaplar.
     * Önceki ay sıfırsa yüzde üretilmez (percentageBasisPoints = null).
     */
    fun calculateMonthlyTrend(
        payments: List<SubscriptionPayment>,
        currentYearMonth: String,
        previousYearMonth: String,
    ): Map<Currency, SubscriptionTrendResult> {
        val currentSpending = calculateActualMonthlySpending(payments, currentYearMonth)
        val previousSpending = calculateActualMonthlySpending(payments, previousYearMonth)

        val allCurrencies = (currentSpending.keys + previousSpending.keys)
        val result = mutableMapOf<Currency, SubscriptionTrendResult>()

        for (currency in allCurrencies) {
            val currentActual = currentSpending[currency] ?: 0L
            val previousActual = previousSpending[currency] ?: 0L
            val diff = currentActual - previousActual

            val (basisPoints, isPrevZero) = if (previousActual <= 0L) {
                Pair(null, true)
            } else {
                val scaled = safeMultiply(diff, 10_000L)
                if (scaled == null) {
                    Pair(null, false)
                } else {
                    Pair(scaled / previousActual, false)
                }
            }

            result[currency] = SubscriptionTrendResult(
                currency = currency,
                currentMonthActualMinor = currentActual,
                previousMonthActualMinor = previousActual,
                diffMinor = diff,
                percentageBasisPoints = basisPoints,
                isPreviousMonthZero = isPrevZero,
                isCurrentMonthIncomplete = true,
            )
        }

        return result
    }

    /**
     * Yaklaşan ödemeleri filtreler (bugün dahil, sonraki 7 gün dahil).
     * Yalnız ACTIVE ve TRIAL abonelikler dahil edilir.
     */
    fun filterUpcoming(
        subscriptions: List<Subscription>,
        today: LocalDate,
        windowDays: Int = UPCOMING_WINDOW_DAYS,
    ): List<Subscription> {
        val todayEpoch = today.toEpochDays()
        val endEpoch = todayEpoch + windowDays.toLong()

        return subscriptions
            .filter {
                (it.lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE ||
                    it.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL) &&
                    it.nextRenewalDate.toEpochDays() in todayEpoch..endEpoch
            }
            .sortedWith(compareBy<Subscription> { it.nextRenewalDate }.thenBy { it.id.value })
    }

    /**
     * Gecikmiş ödemeleri filtreler (nextRenewalDate < today).
     * Yalnız ACTIVE ve TRIAL abonelikler dahil edilir.
     */
    fun filterOverdue(
        subscriptions: List<Subscription>,
        today: LocalDate,
    ): List<Subscription> {
        val todayEpoch = today.toEpochDays()

        return subscriptions
            .filter {
                (it.lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE ||
                    it.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL) &&
                    it.nextRenewalDate.toEpochDays() < todayEpoch
            }
            .sortedWith(compareBy<Subscription> { it.nextRenewalDate }.thenBy { it.id.value })
    }

    /**
     * Seçili filtreye göre abonelik listesini süzer.
     */
    fun applyFilter(
        subscriptions: List<Subscription>,
        filter: SubscriptionFilter,
        today: LocalDate,
    ): List<Subscription> {
        return when (filter) {
            SubscriptionFilter.ALL -> subscriptions
            SubscriptionFilter.ACTIVE -> subscriptions.filter {
                it.lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE
            }
            SubscriptionFilter.UPCOMING -> filterUpcoming(subscriptions, today)
            SubscriptionFilter.OVERDUE -> filterOverdue(subscriptions, today)
            SubscriptionFilter.PAUSED -> subscriptions.filter {
                it.lifecycleStatus == SubscriptionLifecycleStatus.PAUSED
            }
            SubscriptionFilter.CANCELLED -> subscriptions.filter {
                it.lifecycleStatus == SubscriptionLifecycleStatus.CANCELLED
            }
            SubscriptionFilter.TRIAL -> subscriptions.filter {
                it.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL
            }
        }
    }

    /**
     * Gerçek verilere dayalı içgörüleri üretir.
     * Sahte veya uydurma veri üretilmez.
     */
    fun generateInsights(
        subscriptions: List<Subscription>,
        priceHistories: List<SubscriptionPriceHistory>,
        today: LocalDate,
    ): List<SubscriptionInsight> {
        val insights = mutableListOf<SubscriptionInsight>()

        // 1. Deneme süresi bitmek üzere olan abonelikler (7 gün içinde)
        val todayEpoch = today.toEpochDays()
        val trialEnding = subscriptions.firstOrNull { sub ->
            sub.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL &&
                sub.trialEndDate != null &&
                sub.trialEndDate.toEpochDays() >= todayEpoch &&
                sub.trialEndDate.toEpochDays() <= todayEpoch + 7L
        }
        if (trialEnding != null && trialEnding.trialEndDate != null) {
            val daysLeft = trialEnding.trialEndDate.toEpochDays() - todayEpoch
            insights.add(
                SubscriptionInsight.TrialEndingSoon(
                    subscriptionName = trialEnding.name,
                    daysRemaining = daysLeft,
                    trialEndDate = trialEnding.trialEndDate,
                )
            )
        }

        // 2. Fiyatı artan abonelik sayısı (en son fiyat kaydında artış olanlar)
        val latestPriceBySub = priceHistories
            .groupBy { it.subscriptionId }
            .mapValues { (_, histories) -> histories.maxByOrNull { it.changedAt } }

        val increasedCount = latestPriceBySub.values.count { it != null && it.isPriceIncreased }
        if (increasedCount > 0) {
            insights.add(SubscriptionInsight.PriceIncreases(count = increasedCount))
        }

        // 3. Duraklatılmış aboneliklerin tahmini tasarruf tutarı
        val paused = subscriptions.filter { it.lifecycleStatus == SubscriptionLifecycleStatus.PAUSED }
        if (paused.isNotEmpty()) {
            val byCurrency = paused.groupBy { it.amount.currency }
            for ((currency, list) in byCurrency) {
                var sum = 0L
                for (sub in list) {
                    val m = calculateMonthlyEstimatedMinor(sub) ?: 0L
                    sum = safeAdd(sum, m) ?: Long.MAX_VALUE
                }
                if (sum > 0L) {
                    insights.add(
                        SubscriptionInsight.PausedSavings(
                            monthlyEstimatedSavings = Money(sum, currency),
                            pausedCount = list.size,
                        )
                    )
                }
            }
        }

        // 4. En yüksek aylık tahmini maliyetli aktif abonelik
        val active = subscriptions.filter {
            it.lifecycleStatus == SubscriptionLifecycleStatus.ACTIVE ||
                it.lifecycleStatus == SubscriptionLifecycleStatus.TRIAL
        }
        val topSub = active.maxByOrNull { calculateMonthlyEstimatedMinor(it) ?: 0L }
        if (topSub != null) {
            val topMonthlyMinor = calculateMonthlyEstimatedMinor(topSub) ?: 0L
            if (topMonthlyMinor > 0L) {
                insights.add(
                    SubscriptionInsight.TopCost(
                        subscriptionName = topSub.name,
                        monthlyEstimated = Money(topMonthlyMinor, topSub.amount.currency),
                    )
                )
            }
        }

        return insights
    }
}
