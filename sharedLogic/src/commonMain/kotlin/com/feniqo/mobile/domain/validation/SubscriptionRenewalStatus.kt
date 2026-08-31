package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Subscription

/**
 * Bir aboneliğin güncel takvim gününe ve yaklaşan vade penceresine göre yenileme durumu.
 */
sealed interface SubscriptionRenewalStatus {
    /**
     * Abonelik pasiftir/duraklatılmıştır (isActive = false); vade ve uyarı üretmez.
     */
    data object Inactive : SubscriptionRenewalStatus

    /**
     * Abonelik aktiftir ve yenileme tarihi bugünden öncedir (nextRenewalDate < today).
     * @param daysOverdue Kaç gün geciktiği (pozitif tamsayı).
     */
    data class Overdue(val daysOverdue: Long) : SubscriptionRenewalStatus {
        init {
            require(daysOverdue > 0L) { "Gecikme gün sayısı sıfırdan büyük olmalıdır: $daysOverdue" }
        }
    }

    /**
     * Abonelik aktiftir ve yenileme tarihi bugündür (nextRenewalDate == today).
     */
    data object DueToday : SubscriptionRenewalStatus

    /**
     * Abonelik aktiftir ve yenileme tarihi yaklaşan gün penceresi içindedir (1 <= daysUntilRenewal <= upcomingWindowDays).
     * @param daysUntilRenewal Yenilemeye kalan gün sayısı.
     */
    data class Upcoming(val daysUntilRenewal: Long) : SubscriptionRenewalStatus {
        init {
            require(daysUntilRenewal > 0L) { "Yaklaşan gün sayısı sıfırdan büyük olmalıdır: $daysUntilRenewal" }
        }
    }

    /**
     * Abonelik aktiftir ve yenileme tarihi yaklaşan gün penceresinin ötesindedir (daysUntilRenewal > upcomingWindowDays).
     * @param daysUntilRenewal Yenilemeye kalan gün sayısı.
     */
    data class Scheduled(val daysUntilRenewal: Long) : SubscriptionRenewalStatus {
        init {
            require(daysUntilRenewal > 0L) { "Planlanan gün sayısı sıfırdan büyük olmalıdır: $daysUntilRenewal" }
        }
    }
}

/**
 * Abonelik yenileme durumunu hesaplayan platformdan bağımsız saf domain motoru.
 */
object SubscriptionRenewalStatusCalculator {

    const val DEFAULT_UPCOMING_WINDOW_DAYS = 7

    /**
     * Verilen aboneliğin [today] referans gününe ve [upcomingWindowDays] penceresine göre durumunu hesaplar.
     *
     * @param subscription İncelenen abonelik.
     * @param today Referans alınan güncel tarih.
     * @param upcomingWindowDays Yaklaşan kabul edilecek gün penceresi (>= 0).
     * @return [SubscriptionRenewalStatus]
     * @throws IllegalArgumentException [upcomingWindowDays] negatifse.
     */
    fun calculate(
        subscription: Subscription,
        today: LocalDate,
        upcomingWindowDays: Int = DEFAULT_UPCOMING_WINDOW_DAYS,
    ): SubscriptionRenewalStatus {
        require(upcomingWindowDays >= 0) {
            "Yaklaşan gün penceresi negatif olamaz: $upcomingWindowDays"
        }

        if (!subscription.isActive) {
            return SubscriptionRenewalStatus.Inactive
        }

        val daysDiff = subscription.nextRenewalDate.toEpochDays() - today.toEpochDays()
        val windowDaysLong = upcomingWindowDays.toLong()

        return when {
            daysDiff < 0L -> SubscriptionRenewalStatus.Overdue(daysOverdue = -daysDiff)
            daysDiff == 0L -> SubscriptionRenewalStatus.DueToday
            daysDiff <= windowDaysLong -> SubscriptionRenewalStatus.Upcoming(daysUntilRenewal = daysDiff)
            else -> SubscriptionRenewalStatus.Scheduled(daysUntilRenewal = daysDiff)
        }
    }
}
