package com.feniqo.mobile.presentation.sync

import com.feniqo.mobile.presentation.common.FinanceUiMessage

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

/**
 * Presentation katmanına özel, immutable Workspace çakışma görünüm modeli.
 */
data class WorkspaceConflictUiModel(
    val entityId: String,
    val localVersion: Long,
    val remoteVersion: Long,
)

/**
 * Kullanıcı tarafından açılmış ve sabitlenmiş Workspace çakışma diyaloğu durumu.
 * null olması diyaloğun kapalı olduğunu belirtir.
 */
data class WorkspaceConflictDialogState(
    val conflict: WorkspaceConflictUiModel,
    val isResolving: Boolean = false,
    val error: FinanceUiMessage? = null,
)

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
    val hasResolvableWorkspaceConflict: Boolean = false,
    val activeConflictDialog: WorkspaceConflictDialogState? = null,
) {
    val isConflictDialogVisible: Boolean
        get() = activeConflictDialog != null

    val isResolvingConflict: Boolean
        get() = activeConflictDialog?.isResolving == true

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
            hasResolvableWorkspaceConflict = false,
            activeConflictDialog = null,
        )
    }
}
