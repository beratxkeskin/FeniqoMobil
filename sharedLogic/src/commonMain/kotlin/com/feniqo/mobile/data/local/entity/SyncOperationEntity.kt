package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Sunucuya gönderilmeyi bekleyen, kalıcı ve sıralı yerel yazma işlemi. */
@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["status_code", "next_attempt_at_epoch_ms", "created_at_epoch_ms"]),
        Index(value = ["entity_type_code", "entity_id"]),
        Index(value = ["created_at_epoch_ms"]),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "entity_type_code")
    val entityTypeCode: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "operation_type_code")
    val operationTypeCode: String,
    @ColumnInfo(name = "base_version")
    val baseVersion: Long?,
    @ColumnInfo(name = "status_code")
    val statusCode: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    @ColumnInfo(name = "next_attempt_at_epoch_ms")
    val nextAttemptAtEpochMillis: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMillis: Long,
)
