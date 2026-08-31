package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY created_at_epoch_ms DESC
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
    ): Flow<List<RecurringTransactionEntity>>

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    fun observeById(id: String): Flow<RecurringTransactionEntity?>

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getById(id: String): RecurringTransactionEntity?

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE id = :id
        """,
    )
    suspend fun getAnyById(id: String): RecurringTransactionEntity?


    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE is_active = 1
          AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getActiveRules(): List<RecurringTransactionEntity>

    @Query(
        """
        SELECT * FROM recurring_transaction_occurrences
        WHERE recurring_transaction_id = :recurringTransactionId
          AND due_date = :dueDate
        LIMIT 1
        """,
    )
    suspend fun getOccurrence(
        recurringTransactionId: String,
        dueDate: String,
    ): RecurringTransactionOccurrenceEntity?

    @Query(
        """
        SELECT * FROM recurring_transaction_occurrences
        WHERE recurring_transaction_id = :recurringTransactionId
        ORDER BY due_date ASC
        """,
    )
    fun observeOccurrencesForRecurring(
        recurringTransactionId: String,
    ): Flow<List<RecurringTransactionOccurrenceEntity>>

    @Upsert
    suspend fun upsert(entity: RecurringTransactionEntity)

    @Upsert
    suspend fun upsertOccurrence(entity: RecurringTransactionOccurrenceEntity)
}
