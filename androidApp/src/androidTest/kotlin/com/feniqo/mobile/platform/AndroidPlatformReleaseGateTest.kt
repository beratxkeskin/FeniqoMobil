package com.feniqo.mobile.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.feniqo.mobile.presentation.settings.UserAvatarManager
import com.feniqo.mobile.sync.AndroidSubscriptionPaymentReminderNotifier
import com.feniqo.mobile.sync.WorkManagerSyncScheduler
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlatformReleaseGateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager by lazy { WorkManager.getInstance(context) }

    @After
    fun cleanup() {
        UserAvatarManager(context).apply {
            removeAvatar(USER_A)
            removeAvatar(USER_B)
            clearTempCameraFiles()
        }
        workManager.cancelUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).result.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun fileProvider_and_avatar_lifecycle_are_scoped_to_the_current_account() {
        val manager = UserAvatarManager(context)
        val (temporaryFile, contentUri) = requireNotNull(manager.createTempCameraFile())

        assertEquals("content", contentUri.scheme)
        assertEquals("${context.packageName}.fileprovider", contentUri.authority)
        val provider = requireNotNull(
            context.packageManager.resolveContentProvider(contentUri.authority!!, PackageManager.MATCH_ALL),
        )
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)

        temporaryFile.writeBytes(TEST_IMAGE_BYTES)
        assertTrue(manager.saveAvatarFromUri(USER_A, contentUri))
        assertTrue(requireNotNull(manager.getAvatarFile(USER_A)).readBytes().contentEquals(TEST_IMAGE_BYTES))
        assertNull(manager.getAvatarFile(USER_B))

        assertTrue(manager.removeAvatar(USER_A))
        assertNull(manager.getAvatarFile(USER_A))
        manager.clearTempCameraFiles()
        assertFalse(temporaryFile.exists())
    }

    @Test
    fun workManager_unique_sync_survives_scheduler_recreation_without_duplicate_work() {
        workManager.cancelUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME).result.get(10, TimeUnit.SECONDS)

        WorkManagerSyncScheduler(workManager).scheduleInitialSync()
        WorkManagerSyncScheduler(workManager).scheduleInitialSync()

        val work = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK_NAME)
            .get(10, TimeUnit.SECONDS)
        assertEquals(1, work.size)
        assertTrue(work.single().tags.contains(WorkManagerSyncScheduler.TAG_FENIQO_SYNC))
    }

    @Test
    fun notification_permission_and_channel_are_declared_without_posting_financial_data() {
        val requestedPermissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()
        assertTrue(requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))

        AndroidSubscriptionPaymentReminderNotifier(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(AndroidSubscriptionPaymentReminderNotifier.CHANNEL_ID)
            assertEquals(AndroidSubscriptionPaymentReminderNotifier.CHANNEL_NAME, channel.name.toString())
        }
    }

    private companion object {
        const val USER_A = "release-gate-user-a"
        const val USER_B = "release-gate-user-b"
        val TEST_IMAGE_BYTES = "synthetic-avatar-content".encodeToByteArray()
    }
}
