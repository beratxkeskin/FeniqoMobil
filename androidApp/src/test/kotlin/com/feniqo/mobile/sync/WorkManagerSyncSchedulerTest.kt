package com.feniqo.mobile.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(workManager)
    }

    @Test
    fun `scheduleInitialSync enqueues unique work with network constraint and tag`() {
        scheduler.scheduleInitialSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)

        val workInfo = workInfos.first()
        assertTrue(workInfo.tags.contains(WorkManagerSyncScheduler.TAG_FENIQO_SYNC))
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertEquals(
            NetworkType.CONNECTED,
            workInfo.constraints.requiredNetworkType,
        )
    }

    @Test
    fun `repeated scheduleInitialSync calls with KEEP policy do not duplicate work`() {
        scheduler.scheduleInitialSync()
        scheduler.scheduleInitialSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `scheduleOutboxSync enqueues work with APPEND_OR_REPLACE policy`() {
        scheduler.scheduleOutboxSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertTrue(workInfos.isNotEmpty())

        val workInfo = workInfos.first()
        assertTrue(workInfo.tags.contains(WorkManagerSyncScheduler.TAG_FENIQO_SYNC))
        assertEquals(
            NetworkType.CONNECTED,
            workInfo.constraints.requiredNetworkType,
        )
    }

    @Test
    fun `scheduleOutboxSync while work exists preserves chain and enqueues second distinct work request`() {
        scheduler.scheduleInitialSync()
        val initialInfos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, initialInfos.size)
        val initialId = initialInfos.first().id

        scheduler.scheduleOutboxSync()
        val chainedInfos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()

        // APPEND_OR_REPLACE zincirine ikinci farklı iş eklenir ve ilk iş bitene kadar BLOCKED olarak bekler
        assertEquals(2, chainedInfos.size)
        val secondWork = chainedInfos.first { it.id != initialId }
        assertNotNull(secondWork)
        assertTrue(secondWork.tags.contains(WorkManagerSyncScheduler.TAG_FENIQO_SYNC))
        assertEquals(WorkInfo.State.BLOCKED, secondWork.state)
    }

    @Test
    fun `cancelSyncWork cancels scheduled work`() {
        scheduler.scheduleInitialSync()
        val beforeCancel = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, beforeCancel.size)

        scheduler.cancelSyncWork()

        val afterCancel = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).get()
        assertTrue(afterCancel.all { it.state == WorkInfo.State.CANCELLED })
    }
}
