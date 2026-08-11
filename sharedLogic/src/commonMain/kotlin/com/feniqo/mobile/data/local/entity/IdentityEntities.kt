package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["active_workspace_id"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "full_name")
    val fullName: String?,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "theme_code")
    val themeCode: String,
    @ColumnInfo(name = "language_code")
    val languageCode: String,
    @ColumnInfo(name = "active_workspace_id")
    val activeWorkspaceId: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "workspaces",
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["normalized_name"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class WorkspaceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "workspace_members",
    primaryKeys = ["workspace_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workspace_id"]),
        Index(value = ["user_id"]),
        Index(value = ["workspace_id", "role_code"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class WorkspaceMemberEntity(
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "role_code")
    val roleCode: String,
    @ColumnInfo(name = "joined_at_epoch_ms")
    val joinedAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
