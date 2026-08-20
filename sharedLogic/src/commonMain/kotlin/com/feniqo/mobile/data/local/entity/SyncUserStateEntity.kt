package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Kullanıcı bazında son başarılı senkronizasyon zamanı ve metadata'sını kalıcı olarak saklar. */
@Entity(tableName = "sync_user_states", primaryKeys = ["user_id"])
data class SyncUserStateEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "last_successful_sync_at_epoch_ms")
    val lastSuccessfulSyncAtEpochMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMillis: Long,
)
