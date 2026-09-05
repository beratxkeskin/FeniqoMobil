package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    foreignKeys = [
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
        Index(value = ["target_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "target_amount_minor")
    val targetAmountMinor: Long,
    @ColumnInfo(name = "current_amount_minor")
    val currentAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "target_date")
    val targetDate: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "icon_key")
    val iconKey: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["goal_id"]),
        Index(value = ["occurred_on"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class GoalContributionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "goal_id")
    val goalId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "direction_code")
    val directionCode: String,
    @ColumnInfo(name = "occurred_on")
    val occurredOn: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
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
        Index(value = ["type_code"]),
        Index(value = ["status_code"]),
        Index(value = ["due_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class DebtEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "type_code")
    val typeCode: String,
    @ColumnInfo(name = "due_date")
    val dueDate: String,
    @ColumnInfo(name = "status_code")
    val statusCode: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["debt_id"]),
        Index(value = ["paid_on"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class DebtPaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "paid_on")
    val paidOn: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
