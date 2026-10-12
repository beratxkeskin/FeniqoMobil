package com.feniqo.mobile.sync

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.R
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderKey
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import com.feniqo.mobile.util.localizedContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AndroidSubscriptionPaymentReminderNotifierTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var notifier: AndroidSubscriptionPaymentReminderNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as Application
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(notificationManager).setNotificationsEnabled(true)
        notificationManager.cancelAll()
        notifier = AndroidSubscriptionPaymentReminderNotifier(context)
    }

    private fun candidate(
        kind: SubscriptionReminderKind,
        name: String = "Netflix",
    ): SubscriptionPaymentReminderCandidate {
        val nextDate = LocalDate(2026, 10, 19)
        val id = EntityId("sub-1")
        return SubscriptionPaymentReminderCandidate(
            subscriptionId = id,
            subscriptionName = name,
            nextRenewalDate = nextDate,
            reminderKind = kind,
            key = SubscriptionPaymentReminderKey(
                subscriptionId = id,
                nextRenewalDate = nextDate,
                reminderKind = kind,
            ),
        )
    }

    @Test
    fun createNotificationChannel_localizesNameAndDescriptionInTrAndEn() {
        notifier.createNotificationChannel(AppLanguage.TR)
        val channelTr = notificationManager.getNotificationChannel(AndroidSubscriptionPaymentReminderNotifier.CHANNEL_ID)
        assertNotNull(channelTr)
        assertEquals("Abonelik Hatırlatıcıları", channelTr.name.toString())
        assertEquals("Yaklaşan ve vadesi gelen abonelik ödeme bildirimleri", channelTr.description)

        notifier.createNotificationChannel(AppLanguage.EN)
        val channelEn = notificationManager.getNotificationChannel(AndroidSubscriptionPaymentReminderNotifier.CHANNEL_ID)
        assertNotNull(channelEn)
        assertEquals("Subscription Reminders", channelEn.name.toString())
        assertEquals("Upcoming and due subscription payment notifications", channelEn.description)
    }

    @Test
    fun notifyReminder_inTurkish_upcoming_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.UPCOMING, "Spotify"), language = AppLanguage.TR)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Abonelik Hatırlatıcı", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("Spotify aboneliğiniz 7 gün sonra yenilenecek.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun notifyReminder_inTurkish_dueToday_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.DUE_TODAY, "Netflix"), language = AppLanguage.TR)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Abonelik Hatırlatıcı", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("Netflix aboneliğiniz bugün yenileniyor.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun notifyReminder_inTurkish_trialEndingSoon_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.TRIAL_ENDING_SOON, "YouTube Premium"), language = AppLanguage.TR)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Abonelik Hatırlatıcı", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("YouTube Premium deneme süreniz 3 gün sonra sona erecek.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun notifyReminder_inEnglish_upcoming_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.UPCOMING, "Spotify"), language = AppLanguage.EN)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Subscription Reminder", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("Your Spotify subscription will renew in 7 days.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun notifyReminder_inEnglish_dueToday_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.DUE_TODAY, "Netflix"), language = AppLanguage.EN)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Subscription Reminder", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("Your Netflix subscription renews today.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun notifyReminder_inEnglish_trialEndingSoon_localizesTitleAndContent() = runTest {
        val shadowNm = Shadows.shadowOf(notificationManager)
        notifier.notifyReminder(candidate(SubscriptionReminderKind.TRIAL_ENDING_SOON, "YouTube Premium"), language = AppLanguage.EN)
        val notif = shadowNm.allNotifications.firstOrNull()
        assertNotNull(notif)
        assertEquals("Subscription Reminder", notif?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("Your YouTube Premium trial will end in 3 days.", notif?.extras?.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun localizedContext_preservesPlaceholderSubstitutionAcrossLocales() {
        val trContext = context.localizedContext(AppLanguage.TR)
        val enContext = context.localizedContext(AppLanguage.EN)

        val trText = trContext.getString(R.string.notification_subscription_upcoming_content, "CustomService")
        val enText = enContext.getString(R.string.notification_subscription_upcoming_content, "CustomService")

        assertTrue(trText.contains("CustomService"))
        assertTrue(enText.contains("CustomService"))
        assertEquals("CustomService aboneliğiniz 7 gün sonra yenilenecek.", trText)
        assertEquals("Your CustomService subscription will renew in 7 days.", enText)
    }
}
