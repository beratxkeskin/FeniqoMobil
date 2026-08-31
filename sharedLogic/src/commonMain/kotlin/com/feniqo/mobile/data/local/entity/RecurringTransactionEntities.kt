package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
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
        Index(value = ["start_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class RecurringTransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "type_code")
    val typeCode: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "payment_method_code")
    val paymentMethodCode: String,
    @ColumnInfo(name = "frequency_code")
    val frequencyCode: String,
    @ColumnInfo(name = "interval")
    val interval: Int,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String?,
    @ColumnInfo(name = "last_generated_date")
    val lastGeneratedDate: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "recurring_transaction_occurrences",
    primaryKeys = ["recurring_transaction_id", "due_date"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["transaction_id"], unique = true),
        Index(value = ["recurring_transaction_id"]),
    ],
)
data class RecurringTransactionOccurrenceEntity(
    @ColumnInfo(name = "recurring_transaction_id")
    val recurringTransactionId: String,
    @ColumnInfo(name = "due_date")
    val dueDate: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
)
