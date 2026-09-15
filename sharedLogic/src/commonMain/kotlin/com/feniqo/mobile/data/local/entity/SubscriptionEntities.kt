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
        Index(value = ["lifecycle_status"]),
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
    @ColumnInfo(name = "lifecycle_status", defaultValue = "'ACTIVE'")
    val lifecycleStatus: String = "ACTIVE",
    @ColumnInfo(name = "trial_end_date")
    val trialEndDate: String? = null,
    @ColumnInfo(name = "cancellation_date")
    val cancellationDate: String? = null,
    @ColumnInfo(name = "access_end_date")
    val accessEndDate: String? = null,
    @ColumnInfo(name = "reminder_enabled", defaultValue = "1")
    val reminderEnabled: Boolean = true,
    @ColumnInfo(name = "website_url")
    val websiteUrl: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "subscription_price_histories",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscription_id"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class SubscriptionPriceHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "old_amount_minor")
    val oldAmountMinor: Long,
    @ColumnInfo(name = "new_amount_minor")
    val newAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "changed_at_epoch_ms")
    val changedAtEpochMs: Long,
    @Embedded
    val sync: SyncMetadata,
)

@Entity(
    tableName = "subscription_payments",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscription_id", "renewal_due_date"], unique = true),
        Index(value = ["subscription_id"]),
        Index(value = ["payment_date"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class SubscriptionPaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "payment_date")
    val paymentDate: String,
    @ColumnInfo(name = "renewal_due_date")
    val renewalDueDate: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @Embedded
    val sync: SyncMetadata,
)
