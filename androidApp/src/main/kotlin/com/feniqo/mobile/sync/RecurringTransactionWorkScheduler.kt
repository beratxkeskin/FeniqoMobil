package com.feniqo.mobile.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RecurringTransactionWorkScheduler {
    fun scheduleRecurringCheck()
    fun cancelRecurringWork()
}

/**
 * Android WorkManager tabanlı tekrarlayan işlem planlayıcısı.
 * 24 saatlik periyotta, ağ kısıtı olmadan ve KEEP politikasıyla çalışır.
 */
@Singleton
class WorkManagerRecurringTransactionScheduler @Inject constructor(
    private val workManager: WorkManager,
) : RecurringTransactionWorkScheduler {

    override fun scheduleRecurringCheck() {
        val workRequest = buildRecurringWorkRequest()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    override fun cancelRecurringWork() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    internal fun buildRecurringWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(TAG_FENIQO_RECURRING)
            .build()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "feniqo_recurring_due_transactions"
        const val TAG_FENIQO_RECURRING = "feniqo_recurring_due"
        const val REPEAT_INTERVAL_HOURS = 24L
        const val BACKOFF_DELAY_SECONDS = 15L
    }
}
