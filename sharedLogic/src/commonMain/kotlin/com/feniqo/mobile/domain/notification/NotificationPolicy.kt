package com.feniqo.mobile.domain.notification

import com.feniqo.mobile.domain.model.NotificationPreferences

enum class ReminderCategory {
    SUBSCRIPTION,
    DEBT,
    RECURRING,
    BUDGET,
}

object NotificationPolicy {

    /**
     * Verilen yerel saatin sessiz saatler aralığında olup olmadığını belirler.
     * Gece yarısını aşan aralıkları (ör. 22:00 – 08:00) ve aynı gün içi aralıkları (ör. 13:00 – 15:00)
     * matematiksel olarak doğru biçimde hesaplar.
     */
    fun isWithinQuietHours(
        currentHour: Int,
        currentMinute: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ): Boolean {
        val currentMinutes = currentHour * 60 + currentMinute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes < endMinutes) {
            // Aynı gün içinde (ör. 01:00 – 06:00)
            currentMinutes in startMinutes until endMinutes
        } else if (startMinutes > endMinutes) {
            // Gece yarısını aşan (ör. 22:00 – 08:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        } else {
            // Başlangıç ve bitiş aynı saatte ise tüm gün sessiz kabul edilir
            true
        }
    }

    /**
     * Kullanıcı tercihlerine ve anlık saate göre anlık bildirim teslimatının yapılıp yapılamayacağını belirler.
     * Kategori kapalıysa veya sessiz saatler aktif ve aralık içindeyse false döner.
     */
    fun shouldDeliverImmediateReminder(
        preferences: NotificationPreferences,
        category: ReminderCategory,
        currentHour: Int,
        currentMinute: Int,
    ): Boolean {
        if (!preferences.enabled) return false

        val categoryEnabled = when (category) {
            ReminderCategory.SUBSCRIPTION -> preferences.remindSubscriptions
            ReminderCategory.DEBT -> preferences.remindDebts
            ReminderCategory.RECURRING -> preferences.remindRecurring
            ReminderCategory.BUDGET -> preferences.remindBudgets
        }
        if (!categoryEnabled) return false

        if (preferences.quietHoursEnabled) {
            val inQuietHours = isWithinQuietHours(
                currentHour = currentHour,
                currentMinute = currentMinute,
                startHour = preferences.quietHoursStartHour,
                startMinute = preferences.quietHoursStartMinute,
                endHour = preferences.quietHoursEndHour,
                endMinute = preferences.quietHoursEndMinute,
            )
            if (inQuietHours) return false
        }

        return true
    }

    /**
     * Bildirim içeriğindeki tutarı gizlilik tercihine göre maskeler.
     */
    fun maskNotificationAmount(amountFormatted: String, hideAmounts: Boolean): String {
        return if (hideAmounts) "••••" else amountFormatted
    }
}
