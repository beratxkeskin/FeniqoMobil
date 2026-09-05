package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY next_renewal_date ASC, id ASC
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
    ): Flow<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    fun observeById(id: String): Flow<SubscriptionEntity?>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getById(id: String): SubscriptionEntity?

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE id = :id
        """,
    )
    suspend fun getAnyById(id: String): SubscriptionEntity?

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE is_active = 1
          AND deleted_at_epoch_ms IS NULL
        ORDER BY next_renewal_date ASC, id ASC
        """,
    )
    suspend fun getActiveSubscriptions(): List<SubscriptionEntity>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Upsert
    suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)
}
