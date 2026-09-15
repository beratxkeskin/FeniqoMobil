package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionPaymentDao {

    @Query(
        """
        SELECT * FROM subscription_payments
        WHERE subscription_id = :subscriptionId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY payment_date DESC, created_at_epoch_ms DESC, id DESC
        """,
    )
    fun observeBySubscriptionId(subscriptionId: String): Flow<List<SubscriptionPaymentEntity>>

    @Query(
        """
        SELECT * FROM subscription_payments
        WHERE deleted_at_epoch_ms IS NULL
        ORDER BY payment_date DESC, created_at_epoch_ms DESC, id DESC
        """,
    )
    fun observeAll(): Flow<List<SubscriptionPaymentEntity>>

    @Query(
        """
        SELECT * FROM subscription_payments
        WHERE subscription_id = :subscriptionId
          AND renewal_due_date = :renewalDueDate
          AND deleted_at_epoch_ms IS NULL
        LIMIT 1
        """,
    )
    suspend fun findBySubscriptionAndRenewalDue(
        subscriptionId: String,
        renewalDueDate: String,
    ): SubscriptionPaymentEntity?

    @Upsert
    suspend fun upsert(payment: SubscriptionPaymentEntity)

    @Upsert
    suspend fun upsertAll(payments: List<SubscriptionPaymentEntity>)
}
