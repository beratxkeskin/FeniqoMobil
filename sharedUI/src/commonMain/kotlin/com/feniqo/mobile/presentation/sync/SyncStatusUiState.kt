package com.feniqo.mobile.presentation.sync

enum class SyncConnectionUiState {
    CHECKING,
    ONLINE,
    OFFLINE,
}

enum class SyncErrorUiType {
    NETWORK,
    AUTHENTICATION,
    CONFLICT,
    VALIDATION,
    STORAGE,
    UNKNOWN,
}

data class SyncStatusUiState(
    val connectionState: SyncConnectionUiState,
    val isSyncing: Boolean,
    val pendingCount: Int,
    val failedCount: Int,
    val conflictCount: Int,
    val lastSuccessfulSyncAtEpochMillis: Long?,
    val errorType: SyncErrorUiType?,
    val canManualSync: Boolean,
    val canRetryFailed: Boolean,
) {
    companion object {
        val Initial = SyncStatusUiState(
            connectionState = SyncConnectionUiState.CHECKING,
            isSyncing = false,
            pendingCount = 0,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = null,
            canManualSync = false,
            canRetryFailed = false,
        )
    }
}
