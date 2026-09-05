package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gönderilen veya sahiplenilen abonelik ödeme hatırlatıcılarının yerel idempotency makbuz kaydı.
 *
 * Her hatırlatıcı (subscriptionId, nextRenewalDate, reminderKind) bileşimi için
 * tek bir benzersiz `stable_key` ile atomik olarak kaydedilir.
 */
@Entity(
    tableName = "subscription_payment_reminder_receipts",
    indices = [
        Index(value = ["subscription_id"]),
    ],
)
data class SubscriptionPaymentReminderReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "stable_key")
    val stableKey: String,
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "next_renewal_date")
    val nextRenewalDate: String,
    @ColumnInfo(name = "reminder_kind")
    val reminderKind: String,
    @ColumnInfo(name = "claimed_at_epoch_millis")
    val claimedAtEpochMillis: Long,
)
