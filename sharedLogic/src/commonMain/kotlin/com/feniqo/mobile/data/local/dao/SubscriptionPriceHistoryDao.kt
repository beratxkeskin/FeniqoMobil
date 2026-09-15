package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.SubscriptionPriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionPriceHistoryDao {

    @Query(
        """
        SELECT * FROM subscription_price_histories
        WHERE subscription_id = :subscriptionId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY changed_at_epoch_ms DESC, id DESC
        """,
    )
    fun observeBySubscriptionId(subscriptionId: String): Flow<List<SubscriptionPriceHistoryEntity>>

    @Query(
        """
        SELECT * FROM subscription_price_histories
        WHERE deleted_at_epoch_ms IS NULL
        ORDER BY changed_at_epoch_ms DESC, id DESC
        """,
    )
    fun observeAll(): Flow<List<SubscriptionPriceHistoryEntity>>

    @Query(
        """
        SELECT * FROM subscription_price_histories
        WHERE subscription_id = :subscriptionId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY changed_at_epoch_ms DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestBySubscriptionId(subscriptionId: String): SubscriptionPriceHistoryEntity?

    @Upsert
    suspend fun upsert(priceHistory: SubscriptionPriceHistoryEntity)

    @Upsert
    suspend fun upsertAll(priceHistories: List<SubscriptionPriceHistoryEntity>)
}
