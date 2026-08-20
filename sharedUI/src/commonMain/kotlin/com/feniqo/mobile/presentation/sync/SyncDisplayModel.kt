package com.feniqo.mobile.presentation.sync

enum class SyncDisplaySeverity {
    CONFLICT,
    ERROR,
    OFFLINE,
    CHECKING,
    SYNCING,
    PENDING,
    UP_TO_DATE,
}

enum class SyncDisplayActionType {
    MANUAL_SYNC,
    RETRY_FAILED,
}

data class SyncDisplayModel(
    val severity: SyncDisplaySeverity,
    val title: String,
    val message: String,
    val showProgress: Boolean = false,
    val actionType: SyncDisplayActionType? = null,
    val actionText: String? = null,
    val isActionEnabled: Boolean = false,
)

/**
 * SyncStatusUiState'i durum önceliğine göre görsel gösterim modeline dönüştürür.
 *
 * Öncelik Sırası:
 * 1. Çakışma (Conflict)
 * 2. Hata / Başarısız (Failed/Error)
 * 3. Çevrimdışı (Offline)
 * 4. Kontrol Ediliyor (Checking)
 * 5. Senkronize Ediliyor (Syncing)
 * 6. Bekleyen (Pending)
 * 7. Güncel / Son Başarılı Senkronizasyon (Up to date)
 */
fun resolveSyncDisplayModel(
    uiState: SyncStatusUiState,
    nowEpochMillis: Long = 0L,
): SyncDisplayModel {
    // 1. Çakışma (En yüksek öncelik - otomatik aksiyon gösterilmez)
    if (uiState.conflictCount > 0) {
        return SyncDisplayModel(
            severity = SyncDisplaySeverity.CONFLICT,
            title = "Çakışma",
            message = "${uiState.conflictCount} çakışma mevcut (yerel ve sunucu kopyaları korundu).",
            showProgress = false,
            actionType = null,
            actionText = null,
            isActionEnabled = false,
        )
    }

    // 2. Hata / Başarısız
    if (uiState.failedCount > 0 || uiState.errorType != null) {
        val errorMessage = when (uiState.errorType) {
            SyncErrorUiType.NETWORK -> "Ağ bağlantısı hatası oluştu."
            SyncErrorUiType.AUTHENTICATION -> "Oturum süresi doldu. Lütfen tekrar giriş yapın."
            SyncErrorUiType.CONFLICT -> "Veri çakışması tespit edildi."
            SyncErrorUiType.VALIDATION -> "Geçersiz veri nedeniyle senkronizasyon tamamlanamadı."
            SyncErrorUiType.STORAGE -> "Cihaz depolama hatası oluştu."
            SyncErrorUiType.UNKNOWN -> "Senkronizasyon sırasında beklenmeyen bir hata oluştu."
            null -> if (uiState.failedCount > 0) "${uiState.failedCount} işlem başarısız oldu." else "Senkronizasyon hatası."
        }

        // Aksiyon seçimi:
        // - failedCount > 0 && canRetryFailed -> RETRY_FAILED
        // - failedCount == 0 && (NETWORK veya UNKNOWN) && canManualSync -> MANUAL_SYNC
        // - Authentication, validation, storage, conflict -> aksiyon yok
        val (actionType, actionText, isActionEnabled) = when {
            uiState.failedCount > 0 && uiState.canRetryFailed -> {
                Triple(SyncDisplayActionType.RETRY_FAILED, "Yeniden Dene", true)
            }
            uiState.failedCount == 0 && (uiState.errorType == SyncErrorUiType.NETWORK || uiState.errorType == SyncErrorUiType.UNKNOWN) && uiState.canManualSync -> {
                Triple(SyncDisplayActionType.MANUAL_SYNC, "Yeniden Dene", true)
            }
            else -> {
                Triple(null, null, false)
            }
        }

        return SyncDisplayModel(
            severity = SyncDisplaySeverity.ERROR,
            title = "Hata",
            message = errorMessage,
            showProgress = false,
            actionType = actionType,
            actionText = actionText,
            isActionEnabled = isActionEnabled,
        )
    }

    // 3. Çevrimdışı
    if (uiState.connectionState == SyncConnectionUiState.OFFLINE) {
        return SyncDisplayModel(
            severity = SyncDisplaySeverity.OFFLINE,
            title = "Çevrimdışı",
            message = "Çevrimdışı — değişikliklerin cihazda güvende.",
            showProgress = false,
            actionType = null,
            actionText = null,
            isActionEnabled = false,
        )
    }

    // 4. Kontrol Ediliyor
    if (uiState.connectionState == SyncConnectionUiState.CHECKING) {
        return SyncDisplayModel(
            severity = SyncDisplaySeverity.CHECKING,
            title = "Bağlantı",
            message = "Bağlantı kontrol ediliyor...",
            showProgress = true,
            actionType = null,
            actionText = null,
            isActionEnabled = false,
        )
    }

    // 5. Senkronize Ediliyor
    if (uiState.isSyncing) {
        return SyncDisplayModel(
            severity = SyncDisplaySeverity.SYNCING,
            title = "Senkronizasyon",
            message = "Senkronize ediliyor...",
            showProgress = true,
            actionType = null,
            actionText = null,
            isActionEnabled = false,
        )
    }

    // 6. Bekleyen
    if (uiState.pendingCount > 0) {
        return SyncDisplayModel(
            severity = SyncDisplaySeverity.PENDING,
            title = "Bekleyen",
            message = "${uiState.pendingCount} değişiklik eşitlenmeyi bekliyor.",
            showProgress = false,
            actionType = if (uiState.canManualSync) SyncDisplayActionType.MANUAL_SYNC else null,
            actionText = if (uiState.canManualSync) "Eşitle" else null,
            isActionEnabled = uiState.canManualSync,
        )
    }

    // 7. Güncel / Son Başarılı Senkronizasyon
    val formattedTime = formatRelativeTime(uiState.lastSuccessfulSyncAtEpochMillis, nowEpochMillis)
    val syncText = if (formattedTime != null) "Son eşitleme: $formattedTime" else "Tüm veriler güncel."

    return SyncDisplayModel(
        severity = SyncDisplaySeverity.UP_TO_DATE,
        title = "Güncel",
        message = syncText,
        showProgress = false,
        actionType = if (uiState.canManualSync) SyncDisplayActionType.MANUAL_SYNC else null,
        actionText = if (uiState.canManualSync) "Şimdi Eşitle" else null,
        isActionEnabled = uiState.canManualSync,
    )
}

fun formatRelativeTime(epochMillis: Long?, nowEpochMillis: Long): String? {
    if (epochMillis == null) return null
    val diff = (nowEpochMillis - epochMillis).coerceAtLeast(0L)
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1 -> "Az önce"
        minutes < 60 -> "$minutes dk önce"
        hours < 24 -> "$hours sa önce"
        else -> "$days gün önce"
    }
}
