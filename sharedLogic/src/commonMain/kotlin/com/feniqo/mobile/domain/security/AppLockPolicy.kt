package com.feniqo.mobile.domain.security

import com.feniqo.mobile.domain.repository.SecuritySettings

/** Platform yaşam döngüsü zamanlarından yeniden doğrulama gereksinimini hesaplar. */
object AppLockPolicy {
    fun shouldLock(
        settings: SecuritySettings,
        backgroundedAtEpochMillis: Long?,
        nowEpochMillis: Long,
    ): Boolean {
        if (!settings.biometricLockEnabled || backgroundedAtEpochMillis == null) return false
        if (nowEpochMillis < backgroundedAtEpochMillis) return true
        val timeoutMillis = settings.autoLockTimeout.seconds * 1_000L
        return nowEpochMillis - backgroundedAtEpochMillis >= timeoutMillis
    }
}
