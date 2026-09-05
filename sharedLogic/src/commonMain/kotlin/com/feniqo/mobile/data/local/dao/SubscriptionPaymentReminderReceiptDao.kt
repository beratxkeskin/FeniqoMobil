package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentReminderReceiptEntity

/**
 * Abonelik ödeme hatırlatıcılarının atomik claim ve sorgulama işlemlerini yöneten DAO.
 */
@Dao
interface SubscriptionPaymentReminderReceiptDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SubscriptionPaymentReminderReceiptEntity): Long

    /**
     * Hatırlatıcıyı atomik olarak sahiplenir (claim-before-dispatch).
     *
     * @param entity Eklenecek makbuz kaydı.
     * @return İlk claim başarılı olduğunda `true`, stable_key zaten mevcutsa `false` döner.
     */
    suspend fun claim(entity: SubscriptionPaymentReminderReceiptEntity): Boolean {
        return insertIgnore(entity) != -1L
    }

    @Query(
        """
        SELECT * FROM subscription_payment_reminder_receipts
        WHERE stable_key = :stableKey
        LIMIT 1
        """,
    )
    suspend fun getByStableKey(stableKey: String): SubscriptionPaymentReminderReceiptEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM subscription_payment_reminder_receipts
            WHERE stable_key = :stableKey
        )
        """,
    )
    suspend fun isClaimed(stableKey: String): Boolean
}
