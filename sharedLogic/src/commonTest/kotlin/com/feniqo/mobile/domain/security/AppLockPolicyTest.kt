package com.feniqo.mobile.domain.security

import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.domain.repository.DatabaseProtectionStatus
import com.feniqo.mobile.domain.repository.SecuritySettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPolicyTest {
    @Test
    fun disabledLock_neverRequiresAuthentication() {
        assertFalse(AppLockPolicy.shouldLock(settings(enabled = false), 1_000L, 99_000L))
    }

    @Test
    fun firstForeground_withoutBackgroundTimestamp_doesNotLock() {
        assertFalse(AppLockPolicy.shouldLock(settings(), null, 1_000L))
    }

    @Test
    fun immediateTimeout_locksAtSameTimestamp() {
        assertTrue(AppLockPolicy.shouldLock(settings(AutoLockTimeout.IMMEDIATELY), 1_000L, 1_000L))
    }

    @Test
    fun delayedTimeout_locksOnlyAtBoundary() {
        val settings = settings(AutoLockTimeout.AFTER_30_SECONDS)
        assertFalse(AppLockPolicy.shouldLock(settings, 1_000L, 30_999L))
        assertTrue(AppLockPolicy.shouldLock(settings, 1_000L, 31_000L))
    }

    @Test
    fun clockRollback_failsClosed() {
        assertTrue(AppLockPolicy.shouldLock(settings(), 2_000L, 1_999L))
    }

    private fun settings(
        timeout: AutoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
        enabled: Boolean = true,
    ) = SecuritySettings(enabled, timeout, DatabaseProtectionStatus.PROTECTED)
}
