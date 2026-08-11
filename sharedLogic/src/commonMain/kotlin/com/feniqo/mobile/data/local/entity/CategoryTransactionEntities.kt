package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
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
        Index(value = ["normalized_name"]),
        Index(value = ["slug"], unique = true),
        Index(value = ["scope_key", "type_code", "normalized_name"], unique = true),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String?,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String?,
    /** Nullable workspace kapsamlarında da unique kuralının çalışması için kararlı kapsam anahtarı. */
    @ColumnInfo(name = "scope_key")
    val scopeKey: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "slug")
    val slug: String?,
    @ColumnInfo(name = "type_code")
    val typeCode: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "icon_key")
    val iconKey: String?,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "transactions",
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
        Index(value = ["transaction_date"]),
        Index(value = ["type_code"]),
        Index(value = ["payment_method_code"]),
        Index(value = ["search_text"]),
        Index(value = ["owner_id", "transaction_date"]),
        Index(value = ["workspace_id", "transaction_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class TransactionEntity(
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
    @ColumnInfo(name = "search_text")
    val searchText: String,
    @ColumnInfo(name = "payment_method_code")
    val paymentMethodCode: String,
    /** Saat diliminden bağımsız ISO-8601 YYYY-MM-DD işlem günü. */
    @ColumnInfo(name = "transaction_date")
    val transactionDate: String,
    @ColumnInfo(name = "receipt_path")
    val receiptPath: String?,
    @ColumnInfo(name = "installment_number")
    val installmentNumber: Int?,
    @ColumnInfo(name = "total_installments")
    val totalInstallments: Int?,
    @ColumnInfo(name = "installment_group_id")
    val installmentGroupId: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
