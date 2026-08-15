package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Query("SELECT COUNT(*) FROM sync_operations WHERE status_code IN ('PENDING', 'IN_FLIGHT', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE status_code IN ('PENDING', 'FAILED')
          AND next_attempt_at_epoch_ms <= :nowEpochMillis
        ORDER BY created_at_epoch_ms, operation_id
        LIMIT :limit
        """,
    )
    suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE operation_id = :operationId LIMIT 1")
    suspend fun getById(operationId: String): SyncOperationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: SyncOperationEntity)

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'IN_FLIGHT', updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
          AND status_code IN ('PENDING', 'FAILED')
          AND next_attempt_at_epoch_ms <= :nowEpochMillis
        """,
    )
    suspend fun markInFlight(operationId: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'FAILED',
            attempt_count = :attemptCount,
            last_error = :lastError,
            next_attempt_at_epoch_ms = :nextAttemptAtEpochMillis,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun markFailed(
        operationId: String,
        attemptCount: Int,
        lastError: String,
        nextAttemptAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'CONFLICT',
            last_error = :lastError,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'FAILED',
            attempt_count = attempt_count + 1,
            last_error = :lastError,
            next_attempt_at_epoch_ms = :nowEpochMillis,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE status_code = 'IN_FLIGHT'
          AND updated_at_epoch_ms <= :staleBeforeEpochMillis
        """,
    )
    suspend fun recoverStaleInFlight(
        staleBeforeEpochMillis: Long,
        nowEpochMillis: Long,
        lastError: String,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'PENDING',
            last_error = NULL,
            next_attempt_at_epoch_ms = :nowEpochMillis,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE status_code = 'FAILED'
        """,
    )
    suspend fun retryAllFailed(nowEpochMillis: Long): Int

    @Query("DELETE FROM sync_operations WHERE operation_id = :operationId")
    suspend fun deleteCompleted(operationId: String): Int

    @Query(
        "DELETE FROM sync_operations WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int
}
