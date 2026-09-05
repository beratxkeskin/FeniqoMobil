package com.feniqo.mobile.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface SubscriptionPaymentReminderWorkScheduler {
    fun schedulePeriodicReminders()
    fun cancelPeriodicReminders()
}

/**
 * Android WorkManager tabanlı abonelik ödeme hatırlatıcı periyodik planlayıcısı.
 * 24 saatlik periyotta, ağ kısıtı olmadan ve KEEP politikasıyla çalışır.
 */
@Singleton
class WorkManagerSubscriptionPaymentReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SubscriptionPaymentReminderWorkScheduler {

    override fun schedulePeriodicReminders() {
        val workRequest = buildReminderWorkRequest()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    override fun cancelPeriodicReminders() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    internal fun buildReminderWorkRequest(): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        return PeriodicWorkRequestBuilder<SubscriptionPaymentReminderWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(TAG_SUBSCRIPTION_PAYMENT_REMINDERS)
            .build()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "feniqo_subscription_payment_reminders_periodic"
        const val TAG_SUBSCRIPTION_PAYMENT_REMINDERS = "feniqo_subscription_payment_reminders"
        const val REPEAT_INTERVAL_HOURS = 24L
        const val BACKOFF_DELAY_SECONDS = 15L
    }
}
