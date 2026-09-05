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
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
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

    init {
        createNotificationChannel()
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

    override suspend fun notifyReminder(candidate: SubscriptionPaymentReminderCandidate) {
        val manager = notificationManager ?: return
        if (!canPostNotifications()) {
            return
        }

        createNotificationChannel()

        val contentText = when (candidate.reminderKind) {
            SubscriptionReminderKind.UPCOMING ->
                "${candidate.subscriptionName} aboneliğiniz 7 gün sonra yenilenecek."
            SubscriptionReminderKind.DUE_TODAY ->
                "${candidate.subscriptionName} aboneliğiniz bugün yenileniyor."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Abonelik Hatırlatıcı")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(candidate.stableKey, NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "subscription_payment_reminders"
        const val CHANNEL_NAME = "Abonelik Hatırlatıcıları"
        const val CHANNEL_DESCRIPTION = "Yaklaşan ve vadesi gelen abonelik ödeme bildirimleri"
        const val NOTIFICATION_ID = 1001
    }
}
