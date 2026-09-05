package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderKey
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatusCalculator

/**
 * Yaklaşan veya vadesi gelen abonelik ödemeleri için deterministik bildirim adayları üreten saf use case.
 *
 * Yalnızca aktif abonelikler için:
 * - Vadeye tam [upcomingWindowDays] gün (varsayılan 7 gün) kala UPCOMING hatırlatıcısı,
 * - Vade günü DUE_TODAY hatırlatıcısı üretir.
 *
 * Sonuçlar deterministik olarak artan nextRenewalDate ve ardından subscriptionId sıralamasıyla döner.
 */
class PlanSubscriptionPaymentRemindersUseCase {

    operator fun invoke(
        subscriptions: List<Subscription>,
        today: LocalDate,
        upcomingWindowDays: Int = DEFAULT_UPCOMING_WINDOW_DAYS,
    ): List<SubscriptionPaymentReminderCandidate> {
        require(upcomingWindowDays >= 0) {
            "upcomingWindowDays negatif olamaz: $upcomingWindowDays"
        }

        val candidates = mutableListOf<SubscriptionPaymentReminderCandidate>()
        val seenKeys = mutableSetOf<SubscriptionPaymentReminderKey>()

        for (subscription in subscriptions) {
            val status = SubscriptionRenewalStatusCalculator.calculate(
                subscription = subscription,
                today = today,
                upcomingWindowDays = upcomingWindowDays,
            )

            val reminderKind = when (status) {
                is SubscriptionRenewalStatus.DueToday -> SubscriptionReminderKind.DUE_TODAY
                is SubscriptionRenewalStatus.Upcoming -> {
                    if (status.daysUntilRenewal == upcomingWindowDays.toLong()) {
                        SubscriptionReminderKind.UPCOMING
                    } else {
                        null
                    }
                }
                is SubscriptionRenewalStatus.Inactive,
                is SubscriptionRenewalStatus.Overdue,
                is SubscriptionRenewalStatus.Scheduled -> null
            } ?: continue

            val key = SubscriptionPaymentReminderKey(
                subscriptionId = subscription.id,
                nextRenewalDate = subscription.nextRenewalDate,
                reminderKind = reminderKind,
            )

            require(seenKeys.add(key)) {
                "Mükerrer abonelik hatırlatıcı anahtarı tespit edildi: $key"
            }

            candidates.add(
                SubscriptionPaymentReminderCandidate(
                    subscriptionId = subscription.id,
                    subscriptionName = subscription.name,
                    nextRenewalDate = subscription.nextRenewalDate,
                    reminderKind = reminderKind,
                    key = key,
                )
            )
        }

        candidates.sortWith(
            compareBy<SubscriptionPaymentReminderCandidate> { it.nextRenewalDate }
                .thenBy { it.subscriptionId.value }
        )

        return candidates
    }

    companion object {
        const val DEFAULT_UPCOMING_WINDOW_DAYS = 7
    }
}
