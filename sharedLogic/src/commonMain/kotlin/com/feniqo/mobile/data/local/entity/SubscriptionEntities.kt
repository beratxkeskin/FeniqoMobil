package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["workspace_id"]),
        Index(value = ["category_id"]),
        Index(value = ["is_active"]),
        Index(value = ["next_renewal_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "frequency_code")
    val frequencyCode: String,
    @ColumnInfo(name = "interval")
    val interval: Int,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String?,
    @ColumnInfo(name = "next_renewal_date")
    val nextRenewalDate: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
