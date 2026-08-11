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
