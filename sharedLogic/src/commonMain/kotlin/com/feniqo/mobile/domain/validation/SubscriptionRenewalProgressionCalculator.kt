package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription

/**
 * Abonelik yenileme ilerletme hesaplama sonucu kapalı sözleşmesidir.
 */
sealed interface SubscriptionRenewalProgressionResult {
    /**
     * Vade başarıyla tek bir periyot ilerletildi.
     */
    data class Advanced(val nextRenewalDate: LocalDate) : SubscriptionRenewalProgressionResult

    /**
     * Kuralın bitiş tarihine ulaşıldı; kural kapsamında başka yeni vade bulunmuyor.
     */
    data object Completed : SubscriptionRenewalProgressionResult

    /**
     * Abonelik pasif durumda olduğu için ilerletme reddedildi (fail-closed).
     */
    data object InactiveSubscription : SubscriptionRenewalProgressionResult
}

/**
 * Abonelik yenilemesini deterministik ve fail-closed ilerleten saf domain hesaplayıcısı.
 */
object SubscriptionRenewalProgressionCalculator {

    /**
     * Verilen [Subscription] için bir sonraki yenileme durumunu hesaplar.
     */
    fun calculateNextRenewal(subscription: Subscription): SubscriptionRenewalProgressionResult {
        return calculateNextRenewal(
            rule = subscription.renewalRule,
            currentNextRenewalDate = subscription.nextRenewalDate,
            isActive = subscription.isActive,
        )
    }

    /**
     * Verilen tekrarlama kuralı, mevcut sonraki yenileme tarihi ve aktiflik durumuna göre
     * sonraki yenileme sonucunu hesaplar.
     */
    fun calculateNextRenewal(
        rule: RecurrenceRule,
        currentNextRenewalDate: LocalDate,
        isActive: Boolean = true,
    ): SubscriptionRenewalProgressionResult {
        if (!isActive) {
            return SubscriptionRenewalProgressionResult.InactiveSubscription
        }

        val nextDate = RecurrenceScheduleCalculator.nextOccurrenceAfter(
            rule = rule,
            lastGeneratedDate = currentNextRenewalDate,
        )

        return if (nextDate == null) {
            SubscriptionRenewalProgressionResult.Completed
        } else {
            SubscriptionRenewalProgressionResult.Advanced(nextRenewalDate = nextDate)
        }
    }
}

