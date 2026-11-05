package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room v24: Kullanıcıların kişisel merchant doğrulama/engelleme kararları için kalıcı tablo.
 * C2'de yalnız kişisel kapsam (scope_code='PERSONAL', workspace_id=NULL) geçerlidir.
 * SQLite NULL unique semantiği nedeniyle tekillik (scope_key, normalized_alias) üzerinde kurulur.
 */
@Entity(
    tableName = "user_merchant_verifications",
    indices = [
        Index(value = ["scope_key", "normalized_alias"], unique = true),
        Index(value = ["owner_id"]),
        Index(value = ["workspace_id"]),
        Index(value = ["deleted_at_epoch_ms"]),
        Index(value = ["scope_key", "deleted_at_epoch_ms"]),
    ],
)
data class UserMerchantVerificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    @ColumnInfo(name = "scope_key")
    val scopeKey: String,
    @ColumnInfo(name = "normalized_alias")
    val normalizedAlias: String,
    @ColumnInfo(name = "decision_code")
    val decisionCode: String,
    @ColumnInfo(name = "merchant_id")
    val merchantId: String?,
    @ColumnInfo(name = "scope_code")
    val scopeCode: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
