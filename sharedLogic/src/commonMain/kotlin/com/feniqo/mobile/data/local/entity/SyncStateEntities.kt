package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Her uzak tablo için son başarıyla uygulanmış `(updated_at, id)` pull cursor'u. */
@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["sync_scope_key", "entity_type_code"],
)
data class SyncCursorEntity(
    @ColumnInfo(name = "sync_scope_key")
    val syncScopeKey: String,
    @ColumnInfo(name = "entity_type_code")
    val entityTypeCode: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
)

/** Kullanıcı karar verene kadar hem yerel hem uzak snapshot'ı kalıcı biçimde korur. */
@Entity(
    tableName = "sync_conflicts",
    primaryKeys = ["sync_scope_key", "entity_type_code", "entity_id"],
    indices = [Index(value = ["sync_scope_key", "detected_at_epoch_ms"])],
)
data class SyncConflictEntity(
    @ColumnInfo(name = "sync_scope_key")
    val syncScopeKey: String,
    @ColumnInfo(name = "entity_type_code")
    val entityTypeCode: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "local_version")
    val localVersion: Long,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: Long,
    @ColumnInfo(name = "local_payload_json")
    val localPayloadJson: String,
    @ColumnInfo(name = "remote_payload_json")
    val remotePayloadJson: String,
    @ColumnInfo(name = "detected_at_epoch_ms")
    val detectedAtEpochMillis: Long,
)
