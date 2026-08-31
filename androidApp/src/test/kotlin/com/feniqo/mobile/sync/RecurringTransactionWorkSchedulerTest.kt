package com.feniqo.mobile.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurringTransactionWorkSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerRecurringTransactionScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerRecurringTransactionScheduler(workManager)
    }

    @Test
    fun `buildRecurringWorkRequest creates request with 24 hours interval, exponential backoff with 15s delay, and not_required network`() {
        val request = scheduler.buildRecurringWorkRequest()
        val workSpec = request.workSpec

        assertEquals(RecurringTransactionWorker::class.java.name, workSpec.workerClassName)
        assertEquals(TimeUnit.HOURS.toMillis(24), workSpec.intervalDuration)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(15), workSpec.backoffDelayDuration)
        assertTrue(request.tags.contains(WorkManagerRecurringTransactionScheduler.TAG_FENIQO_RECURRING))
        assertEquals(NetworkType.NOT_REQUIRED, workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `scheduleRecurringCheck enqueues unique periodic work without network constraint and with tag`() {
        scheduler.scheduleRecurringCheck()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerRecurringTransactionScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)

        val workInfo = workInfos.first()
        assertTrue(workInfo.tags.contains(WorkManagerRecurringTransactionScheduler.TAG_FENIQO_RECURRING))
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)

        assertEquals(
            NetworkType.NOT_REQUIRED,
            workInfo.constraints.requiredNetworkType,
        )
    }

    @Test
    fun `repeated scheduleRecurringCheck calls with KEEP policy do not duplicate periodic work`() {
        scheduler.scheduleRecurringCheck()
        scheduler.scheduleRecurringCheck()
        scheduler.scheduleRecurringCheck()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerRecurringTransactionScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `cancelRecurringWork cancels scheduled periodic work`() {
        scheduler.scheduleRecurringCheck()
        val beforeCancel = workManager.getWorkInfosForUniqueWork(WorkManagerRecurringTransactionScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, beforeCancel.size)

        scheduler.cancelRecurringWork()
        val afterCancel = workManager.getWorkInfosForUniqueWork(WorkManagerRecurringTransactionScheduler.UNIQUE_WORK_NAME).get()
        assertTrue(afterCancel.all { it.state == WorkInfo.State.CANCELLED })
    }
}
