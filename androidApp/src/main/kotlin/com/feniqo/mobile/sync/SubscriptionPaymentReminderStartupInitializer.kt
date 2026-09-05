package com.feniqo.mobile.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama başlangıcında abonelik ödeme hatırlatıcı periyodik kontrollerini başlatan test edilebilir adaptör.
 */
@Singleton
class SubscriptionPaymentReminderStartupInitializer @Inject constructor(
    private val scheduler: SubscriptionPaymentReminderWorkScheduler,
) {
    fun onAppCreate() {
        scheduler.schedulePeriodicReminders()
    }
}
