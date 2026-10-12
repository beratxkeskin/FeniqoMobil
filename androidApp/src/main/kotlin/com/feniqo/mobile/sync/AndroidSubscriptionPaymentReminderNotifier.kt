package com.feniqo.mobile.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.feniqo.mobile.R
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import com.feniqo.mobile.util.localizedContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android NotificationManager üzerinden abonelik ödeme hatırlatıcı bildirimlerini yayınlar.
 */
@Singleton
class AndroidSubscriptionPaymentReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SubscriptionPaymentReminderNotifier {

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    override fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override suspend fun notifyReminder(
        candidate: SubscriptionPaymentReminderCandidate,
        hideAmounts: Boolean,
        language: AppLanguage,
    ) {
        val manager = notificationManager ?: return
        if (!canPostNotifications()) {
            return
        }

        createNotificationChannel(language)

        val localized = context.localizedContext(language)
        val contentText = when (candidate.reminderKind) {
            SubscriptionReminderKind.UPCOMING ->
                localized.getString(
                    R.string.notification_subscription_upcoming_content,
                    candidate.subscriptionName,
                )
            SubscriptionReminderKind.DUE_TODAY ->
                localized.getString(
                    R.string.notification_subscription_due_today_content,
                    candidate.subscriptionName,
                )
            SubscriptionReminderKind.TRIAL_ENDING_SOON ->
                localized.getString(
                    R.string.notification_subscription_trial_ending_soon_content,
                    candidate.subscriptionName,
                )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(localized.getString(R.string.notification_subscription_reminder_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(candidate.stableKey, NOTIFICATION_ID, notification)
    }

    internal fun createNotificationChannel(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localized = context.localizedContext(language)
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.notification_subscription_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.notification_subscription_channel_description)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "subscription_payment_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
