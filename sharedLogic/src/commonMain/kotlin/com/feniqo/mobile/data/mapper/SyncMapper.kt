package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.domain.model.SyncStatus

fun SyncMetadata.toDomainSyncStatus(): SyncStatus = SyncStatus.valueOf(syncStatus)

fun newSyncMetadata(
    nowEpochMillis: Long,
    status: SyncStatus = SyncStatus.PENDING_CREATE,
): SyncMetadata = SyncMetadata(
    syncStatus = status.name,
    updatedAtEpochMillis = nowEpochMillis,
    localUpdatedAtEpochMillis = nowEpochMillis,
    deletedAtEpochMillis = null,
    version = 0,
    baseVersion = null,
    lastSyncError = null,
)

fun SyncMetadata.toPendingUpdate(nowEpochMillis: Long): SyncMetadata {
    val targetStatus = if (syncStatus == SyncStatus.PENDING_CREATE.name) {
        SyncStatus.PENDING_CREATE.name
    } else {
        SyncStatus.PENDING_UPDATE.name
    }
    return copy(
        syncStatus = targetStatus,
        localUpdatedAtEpochMillis = nowEpochMillis,
        deletedAtEpochMillis = null,
        lastSyncError = null,
    )
}

fun SyncMetadata.toPendingDelete(nowEpochMillis: Long): SyncMetadata {
    return copy(
        syncStatus = SyncStatus.PENDING_DELETE.name,
        localUpdatedAtEpochMillis = nowEpochMillis,
        deletedAtEpochMillis = nowEpochMillis,
        lastSyncError = null,
    )
}
