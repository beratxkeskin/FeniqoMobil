package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo

/**
 * Her yerel kaydın sunucuyla ilişkisini taşır. Domain modeli bu teknik alanları bilmez.
 * Zamanlar UTC epoch-millis olarak saklanır; DTO/domain dönüşümü mapper katmanında yapılır.
 */
data class SyncMetadata(
    @ColumnInfo(name = "sync_status")
    val syncStatus: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "local_updated_at_epoch_ms")
    val localUpdatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMillis: Long?,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "base_version")
    val baseVersion: Long?,
    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String?,
)
