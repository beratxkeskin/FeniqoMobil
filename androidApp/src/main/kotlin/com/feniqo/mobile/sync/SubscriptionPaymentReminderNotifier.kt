package com.feniqo.mobile.sync

import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate

/**
 * Android bildirim katmanı için test edilebilir abonelik ödeme hatırlatıcı bildirim sözleşmesi.
 */
interface SubscriptionPaymentReminderNotifier {

    /**
     * Uygulamanın bildirim gönderme yetkisi (Android 13+ POST_NOTIFICATIONS ve bildirim açık mı) kontrolü.
     */
    fun canPostNotifications(): Boolean

    /**
     * Vadesi gelen veya yaklaşan abonelik hatırlatıcısını sistem bildirim çubuğunda yayınlar.
     */
    suspend fun notifyReminder(candidate: SubscriptionPaymentReminderCandidate)
}
