package com.feniqo.mobile.domain.notification

import com.feniqo.mobile.domain.model.NotificationPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPolicyTest {

    @Test
    fun isWithinQuietHours_overnightRange_detectsCorrectly() {
        // 22:00 – 08:00
        val startH = 22
        val startM = 0
        val endH = 8
        val endM = 0

        // 23:30 is in quiet hours
        assertTrue(NotificationPolicy.isWithinQuietHours(23, 30, startH, startM, endH, endM))

        // 03:15 is in quiet hours
        assertTrue(NotificationPolicy.isWithinQuietHours(3, 15, startH, startM, endH, endM))

        // 07:59 is in quiet hours
        assertTrue(NotificationPolicy.isWithinQuietHours(7, 59, startH, startM, endH, endM))

        // 08:00 is outside quiet hours
        assertFalse(NotificationPolicy.isWithinQuietHours(8, 0, startH, startM, endH, endM))

        // 14:00 is outside quiet hours
        assertFalse(NotificationPolicy.isWithinQuietHours(14, 0, startH, startM, endH, endM))

        // 21:59 is outside quiet hours
        assertFalse(NotificationPolicy.isWithinQuietHours(21, 59, startH, startM, endH, endM))

        // 22:00 is in quiet hours
        assertTrue(NotificationPolicy.isWithinQuietHours(22, 0, startH, startM, endH, endM))
    }

    @Test
    fun isWithinQuietHours_sameDayRange_detectsCorrectly() {
        // 13:00 – 15:00
        val startH = 13
        val startM = 0
        val endH = 15
        val endM = 0

        assertFalse(NotificationPolicy.isWithinQuietHours(12, 59, startH, startM, endH, endM))
        assertTrue(NotificationPolicy.isWithinQuietHours(13, 0, startH, startM, endH, endM))
        assertTrue(NotificationPolicy.isWithinQuietHours(14, 30, startH, startM, endH, endM))
        assertFalse(NotificationPolicy.isWithinQuietHours(15, 0, startH, startM, endH, endM))
    }

    @Test
    fun shouldDeliverImmediateReminder_respectsCategoriesAndQuietHours() {
        val prefs = NotificationPreferences(
            enabled = true,
            remindSubscriptions = true,
            remindDebts = false,
            quietHoursEnabled = true,
            quietHoursStartHour = 22,
            quietHoursStartMinute = 0,
            quietHoursEndHour = 8,
            quietHoursEndMinute = 0,
        )

        // Subscriptions enabled, daytime -> should deliver
        assertTrue(
            NotificationPolicy.shouldDeliverImmediateReminder(
                preferences = prefs,
                category = ReminderCategory.SUBSCRIPTION,
                currentHour = 10,
                currentMinute = 0,
            ),
        )

        // Debts disabled -> should not deliver
        assertFalse(
            NotificationPolicy.shouldDeliverImmediateReminder(
                preferences = prefs,
                category = ReminderCategory.DEBT,
                currentHour = 10,
                currentMinute = 0,
            ),
        )

        // Subscriptions enabled, but nighttime (23:00) -> quiet hours blocks delivery
        assertFalse(
            NotificationPolicy.shouldDeliverImmediateReminder(
                preferences = prefs,
                category = ReminderCategory.SUBSCRIPTION,
                currentHour = 23,
                currentMinute = 0,
            ),
        )

        // Global notifications disabled -> blocks everything
        val disabledPrefs = prefs.copy(enabled = false)
        assertFalse(
            NotificationPolicy.shouldDeliverImmediateReminder(
                preferences = disabledPrefs,
                category = ReminderCategory.SUBSCRIPTION,
                currentHour = 10,
                currentMinute = 0,
            ),
        )
    }

    @Test
    fun maskNotificationAmount_masksWhenEnabled() {
        assertEquals("••••", NotificationPolicy.maskNotificationAmount("150,00 ₺", hideAmounts = true))
        assertEquals("150,00 ₺", NotificationPolicy.maskNotificationAmount("150,00 ₺", hideAmounts = false))
    }
}
