package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity

/** Uzak kaynaktan gelen V1 çekirdek verisini outbox üretmeden atomik olarak uygular. */
@Dao
interface RemoteSyncDao {
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileRow(id: String): UserProfileEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryRow(id: String): CategoryEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionRow(id: String): TransactionEntity?

    @Query(
        """
        SELECT operation_id FROM sync_operations
        WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId
        ORDER BY created_at_epoch_ms, operation_id
        LIMIT 1
        """,
    )
    suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String?

    @Upsert
    suspend fun upsertProfileRow(entity: UserProfileEntity)

    @Upsert
    suspend fun upsertCategoryRows(entities: List<CategoryEntity>)

    @Upsert
    suspend fun upsertTransactionRows(entities: List<TransactionEntity>)

    @Upsert
    suspend fun upsertConflictRow(conflict: SyncConflictEntity)

    @Upsert
    suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>)

    @Query("DELETE FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId")
    suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int

    @Query("UPDATE profiles SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markProfileConflict(entityId: String, error: String): Int

    @Query("UPDATE categories SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markCategoryConflict(entityId: String, error: String): Int

    @Query("UPDATE transactions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markTransactionConflict(entityId: String, error: String): Int

    @Query("DELETE FROM sync_operations WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId")
    suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int

    @Query(
        """
        DELETE FROM sync_operations
        WHERE entity_type_code = :entityTypeCode
          AND entity_id = :entityId
          AND operation_id <> :keptOperationId
        """,
    )
    suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int

    @Query(
        """
        UPDATE sync_operations
        SET status_code = 'PENDING',
            operation_type_code = :operationTypeCode,
            base_version = :remoteVersion,
            attempt_count = 0,
            last_error = NULL,
            next_attempt_at_epoch_ms = :nowEpochMillis,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun resetConflictOperation(
        operationId: String,
        operationTypeCode: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE profiles
        SET sync_status = 'PENDING_UPDATE', version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE categories
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE transactions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int

    @Transaction
    suspend fun applyProfileWrite(entity: UserProfileEntity) {
        upsertProfileRow(entity)
        deleteConflictRow("PROFILE", entity.id)
    }

    @Transaction
    suspend fun resolveProfileKeepRemote(entity: UserProfileEntity) {
        upsertProfileRow(entity)
        deleteOutboxRows("PROFILE", entity.id)
        deleteConflictRow("PROFILE", entity.id)
    }

    @Transaction
    suspend fun resolveCategoryKeepRemote(entity: CategoryEntity) {
        upsertCategoryRows(listOf(entity))
        deleteOutboxRows("CATEGORY", entity.id)
        deleteConflictRow("CATEGORY", entity.id)
    }

    @Transaction
    suspend fun resolveTransactionKeepRemote(entity: TransactionEntity) {
        upsertTransactionRows(listOf(entity))
        deleteOutboxRows("TRANSACTION", entity.id)
        deleteConflictRow("TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun resolveKeepLocal(
        conflict: SyncConflictEntity,
        operationTypeCode: String,
        nowEpochMillis: Long,
    ) {
        deleteOtherOutboxRows(conflict.entityTypeCode, conflict.entityId, conflict.operationId)
        check(
            resetConflictOperation(
                operationId = conflict.operationId,
                operationTypeCode = operationTypeCode,
                remoteVersion = conflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            ) == 1,
        ) { "Çakışmaya ait outbox işlemi bulunamadı." }

        val updated = when (conflict.entityTypeCode) {
            "PROFILE" -> rebaseProfileForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
            "CATEGORY" -> rebaseCategoryForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
            "TRANSACTION" -> rebaseTransactionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
            else -> error("Desteklenmeyen conflict entity türü: ${conflict.entityTypeCode}")
        }
        check(updated == 1) { "Çakışmanın yerel kaydı bulunamadı." }
        deleteConflictRow(conflict.entityTypeCode, conflict.entityId)
    }

    @Transaction
    suspend fun applyProfilePull(entity: UserProfileEntity, cursor: SyncCursorEntity) {
        upsertProfileRow(entity)
        deleteConflictRow("PROFILE", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyCategoryPull(entity: CategoryEntity, cursor: SyncCursorEntity) {
        upsertCategoryRows(listOf(entity))
        deleteConflictRow("CATEGORY", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyTransactionPull(entity: TransactionEntity, cursor: SyncCursorEntity) {
        upsertTransactionRows(listOf(entity))
        deleteConflictRow("TRANSACTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun advancePullCursor(cursor: SyncCursorEntity) {
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyCategoryWrite(entity: CategoryEntity) {
        upsertCategoryRows(listOf(entity))
        deleteConflictRow("CATEGORY", entity.id)
    }

    @Transaction
    suspend fun applyTransactionWrite(entity: TransactionEntity) {
        upsertTransactionRows(listOf(entity))
        deleteConflictRow("TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun recordProfileConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markProfileConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordProfilePullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordProfileConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordCategoryConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markCategoryConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordCategoryPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordCategoryConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordTransactionConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markTransactionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordTransactionPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordTransactionConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    /**
     * Kategoriler işlemlerden önce yazılır; böylece transaction.category_id foreign key'i
     * aynı senkronizasyon sınırı içinde her zaman geçerli kalır.
     */
    @Transaction
    suspend fun applyInitialSnapshot(
        profile: UserProfileEntity,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        cursors: List<SyncCursorEntity> = emptyList(),
    ) {
        upsertProfileRow(profile)
        if (categories.isNotEmpty()) upsertCategoryRows(categories)
        if (transactions.isNotEmpty()) upsertTransactionRows(transactions)
        if (cursors.isNotEmpty()) upsertCursorRows(cursors)
    }

    companion object {
        const val CONFLICT_ERROR = "Sunucudaki kayıt yerel baseVersion ile uyuşmuyor"
    }
}
