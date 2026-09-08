package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.WorkspaceRole


/** Uzak kaynaktan gelen V1/V2 çekirdek verisini outbox üretmeden atomik olarak uygular. */
@Dao
interface RemoteSyncDao {
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileRow(id: String): UserProfileEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryRow(id: String): CategoryEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionRow(id: String): TransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE id = :id LIMIT 1")
    suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity?

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionRow(id: String): SubscriptionEntity?

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalRow(id: String): GoalEntity?

    @Query("SELECT * FROM goal_contributions WHERE id = :id LIMIT 1")
    suspend fun getGoalContributionRow(id: String): GoalContributionEntity?

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtRow(id: String): DebtEntity?

    @Query("SELECT * FROM debt_payments WHERE id = :id LIMIT 1")
    suspend fun getDebtPaymentRow(id: String): DebtPaymentEntity?

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceRow(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspace_members WHERE workspace_id = :workspaceId AND user_id = :userId LIMIT 1")
    suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): WorkspaceMemberEntity?

    @Query("SELECT * FROM workspace_members WHERE workspace_id = :workspaceId ORDER BY role_code, user_id")
    suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity>

    @Query("SELECT id FROM workspaces WHERE deleted_at_epoch_ms IS NULL ORDER BY id ASC")
    suspend fun getAllKnownLiveWorkspaceIds(): List<String>

    @Query(
        """
        SELECT operation_id FROM sync_operations
        WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId
        ORDER BY created_at_epoch_ms, operation_id
        LIMIT 1
        """,
    )
    suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
          AND protocol_version = 2
          AND status_code IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')
        ORDER BY created_at_epoch_ms DESC, operation_id DESC
        LIMIT 1
        """,
    )
    suspend fun getActiveWorkspaceTailOperation(workspaceId: String): SyncOperationEntity?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
        ORDER BY created_at_epoch_ms ASC, operation_id ASC
        """,
    )
    suspend fun getAllWorkspaceOperations(workspaceId: String): List<SyncOperationEntity>

    @Query(
        """
        DELETE FROM sync_operations
        WHERE entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
          AND operation_id IN (:operationIds)
        """,
    )
    suspend fun deleteSpecificWorkspaceOperations(workspaceId: String, operationIds: List<String>): Int

    @Query(
        """
        UPDATE workspaces
        SET sync_status = :syncStatus,
            version = :remoteVersion,
            base_version = :remoteVersion,
            last_sync_error = NULL,
            local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :workspaceId
        """,
    )
    suspend fun rebaseWorkspaceForRetry(
        workspaceId: String,
        syncStatus: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = :operationTypeCode,
            payload_json = :payloadJson,
            base_version = :remoteVersion,
            is_blocked = 0,
            predecessor_operation_id = NULL,
            status_code = 'PENDING',
            attempt_count = 0,
            last_error = NULL,
            next_attempt_at_epoch_ms = :nowEpochMillis,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun resetWorkspaceConflictOperation(
        operationId: String,
        operationTypeCode: String,
        payloadJson: String?,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId LIMIT 1")
    suspend fun getConflictRow(entityTypeCode: String, entityId: String): SyncConflictEntity?

    @Query("SELECT COUNT(*) FROM sync_operations WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId")
    suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int

    @Query("UPDATE workspaces SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markWorkspaceConflict(entityId: String, error: String): Int

    @Upsert
    suspend fun upsertProfileRow(entity: UserProfileEntity)

    @Upsert
    suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>)

    @Upsert
    suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)

    @Query("UPDATE profiles SET active_workspace_id = NULL WHERE id = :profileId AND active_workspace_id = :workspaceId")
    suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int


    @Upsert
    suspend fun upsertCategoryRows(entities: List<CategoryEntity>)

    @Upsert
    suspend fun upsertTransactionRows(entities: List<TransactionEntity>)

    @Upsert
    suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>)

    @Upsert
    suspend fun upsertSubscriptionRows(entities: List<SubscriptionEntity>)

    @Upsert
    suspend fun upsertGoalRows(entities: List<GoalEntity>)

    @Upsert
    suspend fun upsertGoalContributionRows(entities: List<GoalContributionEntity>)

    @Upsert
    suspend fun upsertDebtRows(entities: List<DebtEntity>)

    @Upsert
    suspend fun upsertDebtPaymentRows(entities: List<DebtPaymentEntity>)

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

    @Query("UPDATE recurring_transactions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int

    @Query("UPDATE subscriptions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markSubscriptionConflict(entityId: String, error: String): Int

    @Query("UPDATE goals SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markGoalConflict(entityId: String, error: String): Int

    @Query("UPDATE goal_contributions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markGoalContributionConflict(entityId: String, error: String): Int

    @Query("UPDATE debts SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markDebtConflict(entityId: String, error: String): Int

    @Query("UPDATE debt_payments SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markDebtPaymentConflict(entityId: String, error: String): Int




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

    @Query(
        """
        UPDATE recurring_transactions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE subscriptions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int

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
    suspend fun resolveRecurringTransactionKeepRemote(entity: RecurringTransactionEntity) {
        upsertRecurringTransactionRows(listOf(entity))
        deleteOutboxRows("RECURRING_TRANSACTION", entity.id)
        deleteConflictRow("RECURRING_TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun resolveSubscriptionKeepRemote(entity: SubscriptionEntity) {
        upsertSubscriptionRows(listOf(entity))
        deleteOutboxRows("SUBSCRIPTION", entity.id)
        deleteConflictRow("SUBSCRIPTION", entity.id)
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
            "RECURRING_TRANSACTION" -> rebaseRecurringTransactionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
            "SUBSCRIPTION" -> rebaseSubscriptionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
            else -> error("Desteklenmeyen conflict entity türü: ${conflict.entityTypeCode}")
        }
        check(updated == 1) { "Çakışmanın yerel kaydı bulunamadı." }
        deleteConflictRow(conflict.entityTypeCode, conflict.entityId)
    }

    @Transaction
    suspend fun resolveWorkspaceKeepRemote(
        precondition: WorkspaceResolutionPrecondition,
        remoteEntity: WorkspaceEntity,
    ) {
        val workspaceId = precondition.expectedConflict.entityId

        val liveConflict = getConflictRow("WORKSPACE", workspaceId)
            ?: throw WorkspaceConflictStaleResolutionException("Çakışma satırı canlı veritabanında bulunamadı: $workspaceId")
        val expConflict = precondition.expectedConflict
        if (liveConflict.operationId != expConflict.operationId ||
            liveConflict.remoteVersion != expConflict.remoteVersion ||
            liveConflict.localVersion != expConflict.localVersion ||
            liveConflict.detectedAtEpochMillis != expConflict.detectedAtEpochMillis ||
            liveConflict.localPayloadJson != expConflict.localPayloadJson ||
            liveConflict.remotePayloadJson != expConflict.remotePayloadJson
        ) {
            throw WorkspaceConflictStaleResolutionException("Çakışma satırı canlı veritabanında değişmiş: $workspaceId")
        }

        val liveWs = getWorkspaceRow(workspaceId)
            ?: throw WorkspaceConflictStaleResolutionException("Workspace canlı veritabanında bulunamadı: $workspaceId")
        val expWs = precondition.expectedWorkspace
        if (liveWs.sync.syncStatus != expWs.syncStatus ||
            liveWs.sync.version != expWs.version ||
            liveWs.sync.baseVersion != expWs.baseVersion ||
            liveWs.sync.deletedAtEpochMillis != expWs.deletedAtEpochMillis ||
            liveWs.sync.localUpdatedAtEpochMillis != expWs.localUpdatedAtEpochMillis ||
            liveWs.name != expWs.name ||
            liveWs.typeCode != expWs.typeCode ||
            liveWs.currencyCode != expWs.currencyCode ||
            liveWs.description != expWs.description
        ) {
            throw WorkspaceConflictStaleResolutionException("Workspace yerel durumu snapshot ile uyuşmuyor: $workspaceId")
        }

        val liveOps = getAllWorkspaceOperations(workspaceId)
        if (liveOps.size != precondition.expectedOperations.size) {
            throw WorkspaceConflictStaleResolutionException(
                "Workspace outbox satır sayısı uyuşmuyor: beklenen ${precondition.expectedOperations.size}, canlı ${liveOps.size}",
            )
        }
        for (i in liveOps.indices) {
            val live = liveOps[i]
            val exp = precondition.expectedOperations[i]
            if (live.operationId != exp.operationId ||
                live.operationTypeCode != exp.operationTypeCode ||
                live.statusCode != exp.statusCode ||
                live.isBlocked != exp.isBlocked ||
                live.predecessorOperationId != exp.predecessorOperationId ||
                live.payloadJson != exp.payloadJson ||
                live.baseVersion != exp.baseVersion ||
                live.updatedAtEpochMillis != exp.updatedAtEpochMillis
            ) {
                throw WorkspaceConflictStaleResolutionException(
                    "Workspace outbox operasyonu (${live.operationId}) canlı veritabanında değişmiş.",
                )
            }
        }

        val liveTail = validateWorkspaceChain(precondition.expectedOperations)
            ?: throw WorkspaceConflictStaleResolutionException("KEEP_REMOTE için outbox zinciri boş olamaz.")
        if (liveTail.operationId != precondition.expectedConflict.operationId) {
            throw WorkspaceConflictStaleResolutionException(
                "Conflict operationId (${precondition.expectedConflict.operationId}) doğrulanmış zincir tail'i (${liveTail.operationId}) ile eşleşmiyor.",
            )
        }

        upsertWorkspaceRows(listOf(remoteEntity))

        if (precondition.expectedOperations.isNotEmpty()) {
            val allIds = precondition.expectedOperations.map { it.operationId }
            val deleted = deleteSpecificWorkspaceOperations(workspaceId, allIds)
            check(deleted == allIds.size) {
                "Silinen outbox satır sayısı ($deleted) beklenenle (${allIds.size}) eşleşmiyor."
            }
        }

        deleteConflictRow("WORKSPACE", workspaceId)
    }

    @Transaction
    suspend fun resolveWorkspaceKeepLocal(
        precondition: WorkspaceResolutionPrecondition,
        retainedOperationId: String,
        targetOperationTypeCode: String,
        targetPayloadJson: String?,
        nowEpochMillis: Long,
    ) {
        val workspaceId = precondition.expectedConflict.entityId

        val liveConflict = getConflictRow("WORKSPACE", workspaceId)
            ?: throw WorkspaceConflictStaleResolutionException("Çakışma satırı canlı veritabanında bulunamadı: $workspaceId")
        val expConflict = precondition.expectedConflict
        if (liveConflict.operationId != expConflict.operationId ||
            liveConflict.remoteVersion != expConflict.remoteVersion ||
            liveConflict.localVersion != expConflict.localVersion ||
            liveConflict.detectedAtEpochMillis != expConflict.detectedAtEpochMillis ||
            liveConflict.localPayloadJson != expConflict.localPayloadJson ||
            liveConflict.remotePayloadJson != expConflict.remotePayloadJson
        ) {
            throw WorkspaceConflictStaleResolutionException("Çakışma satırı canlı veritabanında değişmiş: $workspaceId")
        }

        val liveWs = getWorkspaceRow(workspaceId)
            ?: throw WorkspaceConflictStaleResolutionException("Workspace canlı veritabanında bulunamadı: $workspaceId")
        val expWs = precondition.expectedWorkspace
        if (liveWs.sync.syncStatus != expWs.syncStatus ||
            liveWs.sync.version != expWs.version ||
            liveWs.sync.baseVersion != expWs.baseVersion ||
            liveWs.sync.deletedAtEpochMillis != expWs.deletedAtEpochMillis ||
            liveWs.sync.localUpdatedAtEpochMillis != expWs.localUpdatedAtEpochMillis ||
            liveWs.name != expWs.name ||
            liveWs.typeCode != expWs.typeCode ||
            liveWs.currencyCode != expWs.currencyCode ||
            liveWs.description != expWs.description
        ) {
            throw WorkspaceConflictStaleResolutionException("Workspace yerel durumu snapshot ile uyuşmuyor: $workspaceId")
        }

        val liveOps = getAllWorkspaceOperations(workspaceId)
        if (liveOps.size != precondition.expectedOperations.size) {
            throw WorkspaceConflictStaleResolutionException(
                "Workspace outbox satır sayısı uyuşmuyor: beklenen ${precondition.expectedOperations.size}, canlı ${liveOps.size}",
            )
        }
        for (i in liveOps.indices) {
            val live = liveOps[i]
            val exp = precondition.expectedOperations[i]
            if (live.operationId != exp.operationId ||
                live.operationTypeCode != exp.operationTypeCode ||
                live.statusCode != exp.statusCode ||
                live.isBlocked != exp.isBlocked ||
                live.predecessorOperationId != exp.predecessorOperationId ||
                live.payloadJson != exp.payloadJson ||
                live.baseVersion != exp.baseVersion ||
                live.updatedAtEpochMillis != exp.updatedAtEpochMillis
            ) {
                throw WorkspaceConflictStaleResolutionException(
                    "Workspace outbox operasyonu (${live.operationId}) canlı veritabanında değişmiş.",
                )
            }
        }

        val liveTail = validateWorkspaceChain(precondition.expectedOperations)
            ?: throw WorkspaceConflictStaleResolutionException("KEEP_LOCAL için outbox zinciri boş olamaz.")

        if (liveTail.operationId != precondition.expectedConflict.operationId) {
            throw WorkspaceConflictStaleResolutionException(
                "Conflict operationId (${precondition.expectedConflict.operationId}) doğrulanmış zincir tail'i (${liveTail.operationId}) ile eşleşmiyor.",
            )
        }

        if (retainedOperationId != liveTail.operationId) {
            throw WorkspaceConflictStaleResolutionException(
                "retainedOperationId ($retainedOperationId) doğrulanmış zincir tail'i (${liveTail.operationId}) ile eşleşmiyor.",
            )
        }

        if (liveTail.operationTypeCode == targetOperationTypeCode) {
            if (liveTail.payloadJson != targetPayloadJson) {
                throw WorkspaceConflictStaleResolutionException("UPDATE/DELETE için payloadJson byte-for-byte korunmalıdır.")
            }
        } else if (liveTail.operationTypeCode == "CREATE" && targetOperationTypeCode == "UPDATE") {
            // Geçerli CREATE -> UPDATE dönüşümü
        } else {
            throw WorkspaceConflictStaleResolutionException(
                "Geçersiz işlem türü dönüşümü: ${liveTail.operationTypeCode} -> $targetOperationTypeCode",
            )
        }

        val nonTailIds = precondition.expectedOperations
            .map { it.operationId }
            .filter { it != liveTail.operationId }

        if (nonTailIds.isNotEmpty()) {
            val deleted = deleteSpecificWorkspaceOperations(workspaceId, nonTailIds)
            check(deleted == nonTailIds.size) {
                "Silinen predecessor satır sayısı ($deleted) beklenenle (${nonTailIds.size}) eşleşmiyor."
            }
        }

        val resetCount = resetWorkspaceConflictOperation(
            operationId = liveTail.operationId,
            operationTypeCode = targetOperationTypeCode,
            payloadJson = targetPayloadJson,
            remoteVersion = precondition.expectedConflict.remoteVersion,
            nowEpochMillis = nowEpochMillis,
        )
        check(resetCount == 1) { "Tail outbox işlemi resetlenemedi: ${liveTail.operationId}" }

        val targetSyncStatus = if (targetOperationTypeCode == "DELETE") "PENDING_DELETE" else "PENDING_UPDATE"
        val rebaseCount = rebaseWorkspaceForRetry(
            workspaceId = workspaceId,
            syncStatus = targetSyncStatus,
            remoteVersion = precondition.expectedConflict.remoteVersion,
            nowEpochMillis = nowEpochMillis,
        )
        check(rebaseCount == 1) { "Workspace ($workspaceId) rebase edilemedi." }

        deleteConflictRow("WORKSPACE", workspaceId)
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
    suspend fun applyRecurringTransactionPull(entity: RecurringTransactionEntity, cursor: SyncCursorEntity) {
        upsertRecurringTransactionRows(listOf(entity))
        deleteConflictRow("RECURRING_TRANSACTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applySubscriptionPull(entity: SubscriptionEntity, cursor: SyncCursorEntity) {
        upsertSubscriptionRows(listOf(entity))
        deleteConflictRow("SUBSCRIPTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyGoalPull(entity: GoalEntity, cursor: SyncCursorEntity) {
        upsertGoalRows(listOf(entity))
        deleteConflictRow("GOAL", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyGoalContributionPull(entity: GoalContributionEntity, cursor: SyncCursorEntity) {
        upsertGoalContributionRows(listOf(entity))
        deleteConflictRow("GOAL_CONTRIBUTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyDebtPull(entity: DebtEntity, cursor: SyncCursorEntity) {
        upsertDebtRows(listOf(entity))
        deleteConflictRow("DEBT", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyDebtPaymentPull(entity: DebtPaymentEntity, cursor: SyncCursorEntity) {
        upsertDebtPaymentRows(listOf(entity))
        deleteConflictRow("DEBT_PAYMENT", entity.id)
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
    suspend fun applyRecurringTransactionWrite(entity: RecurringTransactionEntity) {
        upsertRecurringTransactionRows(listOf(entity))
        deleteConflictRow("RECURRING_TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun applySubscriptionWrite(entity: SubscriptionEntity) {
        upsertSubscriptionRows(listOf(entity))
        deleteConflictRow("SUBSCRIPTION", entity.id)
    }

    @Transaction
    suspend fun applyGoalWrite(entity: GoalEntity) {
        upsertGoalRows(listOf(entity))
        deleteConflictRow("GOAL", entity.id)
    }

    @Transaction
    suspend fun applyGoalContributionWrite(entity: GoalContributionEntity) {
        upsertGoalContributionRows(listOf(entity))
        deleteConflictRow("GOAL_CONTRIBUTION", entity.id)
    }

    @Transaction
    suspend fun applyDebtWrite(entity: DebtEntity) {
        upsertDebtRows(listOf(entity))
        deleteConflictRow("DEBT", entity.id)
    }

    @Transaction
    suspend fun applyDebtPaymentWrite(entity: DebtPaymentEntity) {
        upsertDebtPaymentRows(listOf(entity))
        deleteConflictRow("DEBT_PAYMENT", entity.id)
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

    @Transaction
    suspend fun recordRecurringTransactionConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markRecurringTransactionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordRecurringTransactionPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordRecurringTransactionConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordSubscriptionConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markSubscriptionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordSubscriptionPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordSubscriptionConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordGoalConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markGoalConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordGoalPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordGoalConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordGoalContributionConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markGoalContributionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordGoalContributionPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordGoalContributionConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordDebtConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markDebtConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordDebtPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordDebtConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordDebtPaymentConflict(conflict: SyncConflictEntity) {
        upsertConflictRow(conflict)
        check(markDebtPaymentConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordDebtPaymentPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        recordDebtPaymentConflict(conflict)
        upsertCursorRows(listOf(cursor))
    }

    /**
     * Kategoriler işlemlerden, recurring kurallarından ve aboneliklerden önce yazılır; böylece
     * category_id foreign key'i her zaman geçerli kalır.
     * Goals ve Debts child'larından önce yazılır.
     */
    @Transaction
    suspend fun applyInitialSnapshot(
        profile: UserProfileEntity,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        cursors: List<SyncCursorEntity> = emptyList(),
        recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
        subscriptions: List<SubscriptionEntity> = emptyList(),
        goals: List<GoalEntity> = emptyList(),
        goalContributions: List<GoalContributionEntity> = emptyList(),
        debts: List<DebtEntity> = emptyList(),
        debtPayments: List<DebtPaymentEntity> = emptyList(),
    ) {
        upsertProfileRow(profile)
        if (categories.isNotEmpty()) upsertCategoryRows(categories)
        if (recurringTransactions.isNotEmpty()) upsertRecurringTransactionRows(recurringTransactions)
        if (subscriptions.isNotEmpty()) upsertSubscriptionRows(subscriptions)
        if (goals.isNotEmpty()) upsertGoalRows(goals)
        if (goalContributions.isNotEmpty()) upsertGoalContributionRows(goalContributions)
        if (debts.isNotEmpty()) upsertDebtRows(debts)
        if (debtPayments.isNotEmpty()) upsertDebtPaymentRows(debtPayments)
        if (transactions.isNotEmpty()) upsertTransactionRows(transactions)
        if (cursors.isNotEmpty()) upsertCursorRows(cursors)
    }

    /**
     * Workspace ve WorkspaceMember pull sonuçlarını tek bir Room transaction'ında atomik olarak uygular.
     * Snapshot içindeki member satırlarının workspace_id değeri, snapshot'taki veya veritabanındaki
     * geçerli bir workspace ile eşleşmelidir. Eşleşmeyen yetim member bulunursa fail-closed reddedilir
     * ve transaction rollback edilir.
     * Outbox üretilmez, mevcut outbox satırları korunur. Tombstone satırları normal snapshot olarak uygulanır.
     */
    @Transaction
    suspend fun applyWorkspaceSnapshot(
        workspaces: List<WorkspaceEntity>,
        members: List<WorkspaceMemberEntity>,
        cursors: List<SyncCursorEntity> = emptyList(),
    ) {
        if (members.isNotEmpty()) {
            val snapshotWorkspaceIds = workspaces.mapTo(mutableSetOf()) { it.id }
            for (member in members) {
                if (member.workspaceId !in snapshotWorkspaceIds) {
                    val existingWs = getWorkspaceRow(member.workspaceId)
                    requireNotNull(existingWs) {
                        "WorkspaceMember workspace_id (${member.workspaceId}) snapshot veya yerel veritabanında bulunamadı."
                    }
                }
            }
        }

        if (workspaces.isNotEmpty()) {
            upsertWorkspaceRows(workspaces)
        }
        if (members.isNotEmpty()) {
            upsertWorkspaceMemberRows(members)
            for (member in members) {
                if (member.sync.deletedAtEpochMillis != null) {
                    clearActiveWorkspaceIfMatches(member.userId, member.workspaceId)
                }
            }
        }
        if (cursors.isNotEmpty()) {
            upsertCursorRows(cursors)
        }
    }

    /**
     * Davet kodu ile katılım sonucunda dönen remote Workspace ve WorkspaceMember snapshot'larını
     * tek bir Room transaction'ında outbox veya local mutation üretmeden atomik olarak yazar.
     */
    @Transaction
    suspend fun applyRedeemedWorkspaceMembershipSnapshot(
        workspace: WorkspaceEntity,
        member: WorkspaceMemberEntity,
    ) {
        require(member.workspaceId == workspace.id) {
            "WorkspaceMember workspaceId (${member.workspaceId}) workspace id (${workspace.id}) ile eşleşmiyor."
        }
        upsertWorkspaceRows(listOf(workspace))
        upsertWorkspaceMemberRows(listOf(member))
    }

    /**
     * Sahiplik devri sonucunda dönen remote Workspace ve iki WorkspaceMember (eski sahip EDITOR, yeni sahip OWNER)
     * snapshot'ını tek bir Room transaction'ında outbox veya yerel mutasyon üretmeden atomik olarak yazar.
     * Transaction öncesi canlı satırlar hâlâ SYNCED ve beklenen versiyonlarda değilse fail-closed hata fırlatır
     * ve transaction rollback edilir.
     */
    @Transaction
    suspend fun applyOwnershipTransferSnapshot(
        workspace: WorkspaceEntity,
        actorMember: WorkspaceMemberEntity,
        targetMember: WorkspaceMemberEntity,
        expectedWorkspaceVersion: Long,
        expectedActorMemberVersion: Long,
        expectedTargetMemberVersion: Long,
    ) {
        require(actorMember.workspaceId == workspace.id) {
            "Actor member workspaceId (${actorMember.workspaceId}) workspace id (${workspace.id}) ile eşleşmiyor."
        }
        require(targetMember.workspaceId == workspace.id) {
            "Target member workspaceId (${targetMember.workspaceId}) workspace id (${workspace.id}) ile eşleşmiyor."
        }
        require(actorMember.userId != targetMember.userId) {
            "Actor member (${actorMember.userId}) ile target member aynı olamaz."
        }
        require(actorMember.roleCode.equals(WorkspaceRole.EDITOR.name, ignoreCase = true)) {
            "Actor member rolü EDITOR olmalıdır: ${actorMember.roleCode}"
        }
        require(targetMember.roleCode.equals(WorkspaceRole.OWNER.name, ignoreCase = true)) {
            "Target member rolü OWNER olmalıdır: ${targetMember.roleCode}"
        }
        require(workspace.ownerId == targetMember.userId) {
            "Workspace ownerId (${workspace.ownerId}) target userId (${targetMember.userId}) ile eşleşmiyor."
        }

        val liveWs = getWorkspaceRow(workspace.id)
            ?: throw WorkspaceOwnershipTransferPreconditionException("Workspace (${workspace.id}) yerelde bulunamadı.")
        val liveActor = getWorkspaceMemberRow(workspace.id, actorMember.userId)
            ?: throw WorkspaceOwnershipTransferPreconditionException("Actor member (${actorMember.userId}) yerelde bulunamadı.")
        val liveTarget = getWorkspaceMemberRow(workspace.id, targetMember.userId)
            ?: throw WorkspaceOwnershipTransferPreconditionException("Target member (${targetMember.userId}) yerelde bulunamadı.")

        if (liveWs.sync.deletedAtEpochMillis != null ||
            liveActor.sync.deletedAtEpochMillis != null ||
            liveTarget.sync.deletedAtEpochMillis != null
        ) {
            throw WorkspaceOwnershipTransferPreconditionException("Yerel kayıtlar silinmiş (tombstone) durumdadır.")
        }

        if (liveWs.sync.syncStatus != SyncStatus.SYNCED.name ||
            liveActor.sync.syncStatus != SyncStatus.SYNCED.name ||
            liveTarget.sync.syncStatus != SyncStatus.SYNCED.name
        ) {
            throw WorkspaceOwnershipTransferPreconditionException("Yerel kayıtlarda eşzamanlanmamış değişiklikler var (SYNCED değil).")
        }

        if (liveWs.sync.version != expectedWorkspaceVersion ||
            liveActor.sync.version != expectedActorMemberVersion ||
            liveTarget.sync.version != expectedTargetMemberVersion
        ) {
            throw WorkspaceOwnershipTransferPreconditionException(
                "Yerel versiyonlar uyuşmuyor: WS beklenen=$expectedWorkspaceVersion güncel=${liveWs.sync.version}, " +
                    "Actor beklenen=$expectedActorMemberVersion güncel=${liveActor.sync.version}, " +
                    "Target beklenen=$expectedTargetMemberVersion güncel=${liveTarget.sync.version}"
            )
        }

        upsertWorkspaceRows(listOf(workspace))
        upsertWorkspaceMemberRows(listOf(actorMember, targetMember))
    }

    /**
     * Inbound incremental remote sync sırasında yerel değişikliklerin ezilmesini engelleyen atomik işlem.
     * Tüm precondition'lar transaction içinde yeniden doğrulanır; herhangi bir uyuşmazlıkta
     * [WorkspaceSyncStalePlanException] fırlatılarak transaction rollback edilir.
     */
    @Transaction
    suspend fun applyWorkspaceIncrementalPlan(plan: WorkspaceIncrementalPlan) {
        // 1. Kümelerin karşılıklı dışlayıcılığı ve duplicate kontrolü
        val applyIds = plan.applyItems.map { it.entity.id }
        val conflictIds = plan.conflictItems.map { it.conflict.entityId }
        val preserveIds = plan.preserveItems.map { it.workspaceId }

        val allIds = applyIds + conflictIds + preserveIds
        require(allIds.size == allIds.toSet().size) {
            "Plan gruplarındaki workspace ID'leri karşılıklı dışlayıcı olmalı ve yinelenmemelidir."
        }

        // 2. Precondition doğrulaması (tüm öğeler için transaction-içi kontrol)
        for (item in plan.applyItems) {
            verifyWorkspacePrecondition(item.entity.id, item.precondition)
        }
        for (item in plan.conflictItems) {
            verifyWorkspacePrecondition(item.conflict.entityId, item.precondition)
        }
        for (item in plan.preserveItems) {
            verifyWorkspacePrecondition(item.workspaceId, item.precondition)
        }

        // 3. Atomik yazma (tüm precondition'lar başarıyla geçtikten sonra)
        if (plan.conflictItems.isNotEmpty()) {
            for (item in plan.conflictItems) {
                upsertConflictRow(item.conflict)
                val updated = markWorkspaceConflict(item.conflict.entityId, CONFLICT_ERROR)
                check(updated == 1) {
                    "Workspace conflict durumu güncellenemedi: ${item.conflict.entityId}"
                }
            }
        }

        if (plan.applyItems.isNotEmpty()) {
            upsertWorkspaceRows(plan.applyItems.map { it.entity })
        }

        if (plan.memberRows.isNotEmpty()) {
            val effectiveWsIds = (plan.applyItems.map { it.entity.id } +
                plan.conflictItems.map { it.conflict.entityId } +
                plan.preserveItems.map { it.workspaceId }).toMutableSet()

            for (member in plan.memberRows) {
                if (member.workspaceId !in effectiveWsIds) {
                    val existingWs = getWorkspaceRow(member.workspaceId)
                    requireNotNull(existingWs) {
                        "WorkspaceMember workspace_id (${member.workspaceId}) planda veya yerel veritabanında bulunamadı."
                    }
                    effectiveWsIds.add(member.workspaceId)
                }
            }
            upsertWorkspaceMemberRows(plan.memberRows)
            for (member in plan.memberRows) {
                if (member.sync.deletedAtEpochMillis != null) {
                    clearActiveWorkspaceIfMatches(member.userId, member.workspaceId)
                }
            }
        }

        if (plan.cursorsToPersist.isNotEmpty()) {
            upsertCursorRows(plan.cursorsToPersist)
        }
    }

    private suspend fun verifyWorkspacePrecondition(workspaceId: String, precondition: WorkspacePrecondition) {
        val localWs = getWorkspaceRow(workspaceId)
        val localOp = getActiveWorkspaceTailOperation(workspaceId)

        if (precondition.expectedPresence) {
            if (localWs == null) {
                throw WorkspaceSyncStalePlanException("Workspace ($workspaceId) yerelde bekleniyordu ancak bulunamadı.")
            }
            if (precondition.expectedSyncStatus != null && localWs.sync.syncStatus != precondition.expectedSyncStatus) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) syncStatus uyuşmuyor: beklenen ${precondition.expectedSyncStatus}, güncel ${localWs.sync.syncStatus}",
                )
            }
            if (precondition.expectedVersion != null && localWs.sync.version != precondition.expectedVersion) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) version uyuşmuyor: beklenen ${precondition.expectedVersion}, güncel ${localWs.sync.version}",
                )
            }
            if (precondition.expectedBaseVersion != localWs.sync.baseVersion) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) baseVersion uyuşmuyor: beklenen ${precondition.expectedBaseVersion}, güncel ${localWs.sync.baseVersion}",
                )
            }
            if (precondition.expectedDeletedAtEpochMillis != localWs.sync.deletedAtEpochMillis) {
                throw WorkspaceSyncStalePlanException("Workspace ($workspaceId) deletedAtEpochMillis uyuşmuyor.")
            }
            if (precondition.expectedLocalUpdatedAtEpochMillis != null &&
                localWs.sync.localUpdatedAtEpochMillis != precondition.expectedLocalUpdatedAtEpochMillis
            ) {
                throw WorkspaceSyncStalePlanException("Workspace ($workspaceId) localUpdatedAtEpochMillis uyuşmuyor.")
            }
        } else {
            if (localWs != null) {
                throw WorkspaceSyncStalePlanException("Workspace ($workspaceId) yerelde bulunmaması gerekirken mevcut.")
            }
        }

        // Outbox tail doğrulaması
        if (precondition.expectedActiveOperationId != null) {
            if (localOp == null || localOp.operationId != precondition.expectedActiveOperationId) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) aktif tail operation uyuşmuyor: beklenen ${precondition.expectedActiveOperationId}, güncel ${localOp?.operationId}",
                )
            }
            if (precondition.expectedOperationUpdatedAtEpochMillis != null &&
                localOp.updatedAtEpochMillis != precondition.expectedOperationUpdatedAtEpochMillis
            ) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) outbox operation updatedAtEpochMillis uyuşmuyor.",
                )
            }
        } else {
            if (localOp != null) {
                throw WorkspaceSyncStalePlanException(
                    "Workspace ($workspaceId) için outbox operasyonu beklenmiyordu ancak bulundu: ${localOp.operationId}",
                )
            }
        }
    }

    companion object {

        const val CONFLICT_ERROR = "Sunucudaki kayıt yerel baseVersion ile uyuşmuyor"
    }
}

/**
 * Transaction içinde doğrulanacak yerel durum ve outbox önkoşulları.
 */
data class WorkspacePrecondition(
    val expectedPresence: Boolean,
    val expectedSyncStatus: String? = null,
    val expectedVersion: Long? = null,
    val expectedBaseVersion: Long? = null,
    val expectedDeletedAtEpochMillis: Long? = null,
    val expectedLocalUpdatedAtEpochMillis: Long? = null,
    val expectedActiveOperationId: String? = null,
    val expectedOperationUpdatedAtEpochMillis: Long? = null,
)

data class WorkspaceApplyItem(
    val entity: WorkspaceEntity,
    val precondition: WorkspacePrecondition,
)

data class WorkspaceConflictItem(
    val conflict: SyncConflictEntity,
    val precondition: WorkspacePrecondition,
)

data class WorkspacePreserveItem(
    val workspaceId: String,
    val precondition: WorkspacePrecondition,
)

data class WorkspaceIncrementalPlan(
    val applyItems: List<WorkspaceApplyItem> = emptyList(),
    val conflictItems: List<WorkspaceConflictItem> = emptyList(),
    val preserveItems: List<WorkspacePreserveItem> = emptyList(),
    val memberRows: List<WorkspaceMemberEntity> = emptyList(),
    val cursorsToPersist: List<SyncCursorEntity> = emptyList(),
)

class WorkspaceSyncStalePlanException(message: String) : IllegalStateException(message)

class WorkspaceOwnershipTransferPreconditionException(message: String) : IllegalStateException(message)

data class ExpectedConflictSnapshot(
    val entityTypeCode: String = "WORKSPACE",
    val entityId: String,
    val operationId: String,
    val remoteVersion: Long,
    val localVersion: Long,
    val detectedAtEpochMillis: Long,
    val localPayloadJson: String,
    val remotePayloadJson: String,
)

data class ExpectedWorkspaceSnapshot(
    val id: String,
    val syncStatus: String = "CONFLICT",
    val version: Long,
    val baseVersion: Long?,
    val deletedAtEpochMillis: Long?,
    val localUpdatedAtEpochMillis: Long,
    val name: String,
    val typeCode: String,
    val currencyCode: String,
    val description: String?,
)

data class ExpectedOperationSnapshot(
    val operationId: String,
    val operationTypeCode: String,
    val statusCode: String,
    val isBlocked: Boolean,
    val predecessorOperationId: String?,
    val payloadJson: String?,
    val baseVersion: Long?,
    val updatedAtEpochMillis: Long,
)

data class WorkspaceResolutionPrecondition(
    val expectedConflict: ExpectedConflictSnapshot,
    val expectedWorkspace: ExpectedWorkspaceSnapshot,
    val expectedOperations: List<ExpectedOperationSnapshot>,
)

class WorkspaceConflictStaleResolutionException(message: String) : IllegalStateException(message)

fun SyncConflictEntity.toSnapshot(): ExpectedConflictSnapshot = ExpectedConflictSnapshot(
    entityTypeCode = entityTypeCode,
    entityId = entityId,
    operationId = operationId,
    remoteVersion = remoteVersion,
    localVersion = localVersion,
    detectedAtEpochMillis = detectedAtEpochMillis,
    localPayloadJson = localPayloadJson,
    remotePayloadJson = remotePayloadJson,
)

fun WorkspaceEntity.toSnapshot(): ExpectedWorkspaceSnapshot = ExpectedWorkspaceSnapshot(
    id = id,
    syncStatus = sync.syncStatus,
    version = sync.version,
    baseVersion = sync.baseVersion,
    deletedAtEpochMillis = sync.deletedAtEpochMillis,
    localUpdatedAtEpochMillis = sync.localUpdatedAtEpochMillis,
    name = name,
    typeCode = typeCode,
    currencyCode = currencyCode,
    description = description,
)

fun SyncOperationEntity.toSnapshot(): ExpectedOperationSnapshot = ExpectedOperationSnapshot(
    operationId = operationId,
    operationTypeCode = operationTypeCode,
    statusCode = statusCode,
    isBlocked = isBlocked,
    predecessorOperationId = predecessorOperationId,
    payloadJson = payloadJson,
    baseVersion = baseVersion,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun validateWorkspaceChain(operations: List<ExpectedOperationSnapshot>): ExpectedOperationSnapshot? {
    if (operations.isEmpty()) return null

    val roots = operations.filter { it.predecessorOperationId == null }
    if (roots.size != 1) {
        throw WorkspaceConflictStaleResolutionException(
            "Geçersiz zincir: Tam olarak 1 root beklenirken ${roots.size} root bulundu.",
        )
    }
    val root = roots.first()

    val opMap = operations.associateBy { it.operationId }
    val successorMap = mutableMapOf<String, ExpectedOperationSnapshot>()
    for (op in operations) {
        val predId = op.predecessorOperationId ?: continue
        if (predId !in opMap) {
            throw WorkspaceConflictStaleResolutionException(
                "Geçersiz zincir: '${op.operationId}' işleminin predecessor'ı ($predId) snapshot içinde yok.",
            )
        }
        val existing = successorMap.put(predId, op)
        if (existing != null) {
            throw WorkspaceConflictStaleResolutionException(
                "Geçersiz zincir: Predecessor '$predId' birden fazla ardıla sahip (dallanma tespit edildi).",
            )
        }
    }

    val visited = mutableSetOf<String>()
    var current: ExpectedOperationSnapshot? = root
    var tail: ExpectedOperationSnapshot = root

    while (current != null) {
        if (!visited.add(current.operationId)) {
            throw WorkspaceConflictStaleResolutionException(
                "Geçersiz zincir: Döngü (cycle) tespit edildi: '${current.operationId}'.",
            )
        }
        tail = current
        current = successorMap[current.operationId]
    }

    if (visited.size != operations.size) {
        throw WorkspaceConflictStaleResolutionException(
            "Geçersiz zincir: Kopuk alt-zincir veya ulaşılamayan operasyon tespit edildi (${visited.size}/${operations.size}).",
        )
    }

    return tail
}
