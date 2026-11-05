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
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.entity.isDefinitiveRejection
import com.feniqo.mobile.data.mapper.mergePreservingLocalMerchant
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
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

    @Query("SELECT * FROM user_merchant_verifications WHERE id = :id LIMIT 1")
    suspend fun getUserMerchantVerificationRow(id: String): UserMerchantVerificationEntity?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'USER_MERCHANT_VERIFICATION'
          AND entity_id = :entityId
          AND operation_id = :operationId
        LIMIT 1
        """,
    )
    suspend fun getUserMerchantVerificationOutboxRow(
        syncScopeKey: String,
        entityId: String,
        operationId: String,
    ): SyncOperationEntity?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'USER_MERCHANT_VERIFICATION'
          AND entity_id = :entityId
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE sync_scope_key = :syncScopeKey
                AND predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveMerchantVerificationTailCandidates(
        syncScopeKey: String,
        entityId: String,
    ): List<SyncOperationEntity>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceRow(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspace_members WHERE workspace_id = :workspaceId AND user_id = :userId LIMIT 1")
    suspend fun getWorkspaceMemberRow(
        workspaceId: String,
        userId: String,
    ): WorkspaceMemberEntity?

    @Query("SELECT * FROM workspace_members WHERE workspace_id = :workspaceId ORDER BY role_code, user_id")
    suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity>

    @Query("SELECT id FROM workspaces WHERE deleted_at_epoch_ms IS NULL ORDER BY id ASC")
    suspend fun getAllKnownLiveWorkspaceIds(): List<String>

    @Query(
        """
        SELECT operation_id FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId
        ORDER BY created_at_epoch_ms, operation_id
        LIMIT 1
        """,
    )
    suspend fun getFirstOutboxOperationId(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): String?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
          AND protocol_version = 2
          AND status_code IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')
        ORDER BY created_at_epoch_ms DESC, operation_id DESC
        LIMIT 1
        """,
    )
    suspend fun getActiveWorkspaceTailOperation(
        syncScopeKey: String,
        workspaceId: String,
    ): SyncOperationEntity?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
        ORDER BY created_at_epoch_ms ASC, operation_id ASC
        """,
    )
    suspend fun getAllWorkspaceOperations(
        syncScopeKey: String,
        workspaceId: String,
    ): List<SyncOperationEntity>

    @Query(
        """
        DELETE FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'WORKSPACE'
          AND entity_id = :workspaceId
          AND operation_id IN (:operationIds)
        """,
    )
    suspend fun deleteSpecificWorkspaceOperations(
        syncScopeKey: String,
        workspaceId: String,
        operationIds: List<String>,
    ): Int

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
        WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId
        """,
    )
    suspend fun resetWorkspaceConflictOperation(
        syncScopeKey: String,
        operationId: String,
        operationTypeCode: String,
        payloadJson: String?,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'USER_MERCHANT_VERIFICATION'
          AND entity_id = :verificationId
        ORDER BY created_at_epoch_ms ASC, operation_id ASC
        """,
    )
    suspend fun getAllUserMerchantVerificationOperations(
        syncScopeKey: String,
        verificationId: String,
    ): List<SyncOperationEntity>

    @Query(
        """
        DELETE FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = 'USER_MERCHANT_VERIFICATION'
          AND entity_id = :verificationId
          AND operation_id IN (:operationIds)
        """,
    )
    suspend fun deleteSpecificUserMerchantVerificationOperations(
        syncScopeKey: String,
        verificationId: String,
        operationIds: List<String>,
    ): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET sync_status = :syncStatus,
            version = :remoteVersion,
            base_version = :remoteVersion,
            last_sync_error = NULL,
            local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :verificationId AND (scope_key = :syncScopeKey OR scope_key = 'user:' || substr(:syncScopeKey, 6) OR owner_id = substr(:syncScopeKey, 6))
        """,
    )
    suspend fun rebaseUserMerchantVerificationForRetry(
        verificationId: String,
        syncScopeKey: String,
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
        WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId AND entity_type_code = 'USER_MERCHANT_VERIFICATION'
        """,
    )
    suspend fun resetUserMerchantVerificationConflictOperation(
        syncScopeKey: String,
        operationId: String,
        operationTypeCode: String,
        payloadJson: String?,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId LIMIT 1",
    )
    suspend fun getConflictRow(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): SyncConflictEntity?

    @Query(
        "SELECT COUNT(*) FROM sync_operations WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun countOutboxRows(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): Int

    @Query("UPDATE workspaces SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markWorkspaceConflict(
        entityId: String,
        error: String,
    ): Int

    @Upsert
    suspend fun upsertProfileRow(entity: UserProfileEntity)

    @Upsert
    suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>)

    @Upsert
    suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)

    @Query("UPDATE profiles SET active_workspace_id = NULL WHERE id = :profileId AND active_workspace_id = :workspaceId")
    suspend fun clearActiveWorkspaceIfMatches(
        profileId: String,
        workspaceId: String,
    ): Int

    @Upsert
    suspend fun upsertCategoryRows(entities: List<CategoryEntity>)

    @Transaction
    suspend fun ensureDefaultCategoryRows(categories: List<CategoryEntity>) {
        val reconciled =
            categories.map { candidate ->
                val existing = getCategoryRow(candidate.id)
                if (existing == null) {
                    candidate
                } else {
                    require(existing.ownerId == null && existing.workspaceId == null && existing.isDefault) {
                        "Canonical sistem kategori kimliği farklı kapsamda kullanılıyor: ${candidate.id}"
                    }
                    if (existing.sync.deletedAtEpochMillis != null) {
                        candidate
                    } else {
                        candidate.copy(
                            createdAtEpochMillis = existing.createdAtEpochMillis,
                            sync = existing.sync,
                        )
                    }
                }
            }
        if (reconciled.isNotEmpty()) upsertCategoryRows(reconciled)
    }

    @Transaction
    suspend fun hideLegacyDefaultCategoryRows(
        ids: List<String>,
        hiddenAtEpochMillis: Long,
    ) {
        val rowsToHide =
            ids.mapNotNull { id ->
                getCategoryRow(id)?.let { existing ->
                    require(existing.ownerId == null && existing.workspaceId == null && existing.isDefault) {
                        "Eski sistem kategori kimliği farklı kapsamda kullanılıyor: $id"
                    }
                    if (existing.sync.deletedAtEpochMillis == null) {
                        existing.copy(
                            sync =
                                existing.sync.copy(
                                    localUpdatedAtEpochMillis = hiddenAtEpochMillis,
                                    deletedAtEpochMillis = hiddenAtEpochMillis,
                                ),
                        )
                    } else {
                        null
                    }
                }
            }
        if (rowsToHide.isNotEmpty()) upsertCategoryRows(rowsToHide)
    }

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
    suspend fun upsertUserMerchantVerificationRows(entities: List<UserMerchantVerificationEntity>)

    @Upsert
    suspend fun upsertUserMerchantVerificationRow(entity: UserMerchantVerificationEntity)

    @Upsert
    suspend fun upsertConflictRow(conflict: SyncConflictEntity)

    @Upsert
    suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>)

    @Query(
        "DELETE FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteConflictRow(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): Int

    @Query("UPDATE profiles SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markProfileConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE categories SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markCategoryConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE transactions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markTransactionConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE recurring_transactions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markRecurringTransactionConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE subscriptions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markSubscriptionConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE goals SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markGoalConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE goal_contributions SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markGoalContributionConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE debts SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markDebtConflict(
        entityId: String,
        error: String,
    ): Int

    @Query("UPDATE debt_payments SET sync_status = 'CONFLICT', last_sync_error = :error WHERE id = :entityId")
    suspend fun markDebtPaymentConflict(
        entityId: String,
        error: String,
    ): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET sync_status = 'CONFLICT',
            last_sync_error = :error
        WHERE id = :entityId AND scope_key = :scopeKey
        """,
    )
    suspend fun markUserMerchantVerificationConflict(
        scopeKey: String,
        entityId: String,
        error: String,
    ): Int

    @Query(
        "DELETE FROM sync_operations WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteOutboxRows(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): Int

    @Query(
        """
        DELETE FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = :entityTypeCode
          AND entity_id = :entityId
          AND operation_id <> :keptOperationId
        """,
    )
    suspend fun deleteOtherOutboxRows(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
        keptOperationId: String,
    ): Int

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
        WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId
        """,
    )
    suspend fun resetConflictOperation(
        syncScopeKey: String,
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
    suspend fun rebaseProfileForRetry(
        entityId: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE categories
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseCategoryForRetry(
        entityId: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE transactions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseTransactionForRetry(
        entityId: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE recurring_transactions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseRecurringTransactionForRetry(
        entityId: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE subscriptions
        SET sync_status = CASE WHEN deleted_at_epoch_ms IS NULL THEN 'PENDING_UPDATE' ELSE 'PENDING_DELETE' END,
            version = :remoteVersion, base_version = :remoteVersion,
            last_sync_error = NULL, local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :entityId
        """,
    )
    suspend fun rebaseSubscriptionForRetry(
        entityId: String,
        remoteVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun applyProfileWrite(
        syncScopeKey: String,
        entity: UserProfileEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertProfileRow(entity)
        deleteConflictRow(syncScopeKey, "PROFILE", entity.id)
    }

    @Transaction
    suspend fun resolveProfileKeepRemote(
        syncScopeKey: String,
        entity: UserProfileEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertProfileRow(entity)
        deleteOutboxRows(syncScopeKey, "PROFILE", entity.id)
        deleteConflictRow(syncScopeKey, "PROFILE", entity.id)
    }

    @Transaction
    suspend fun resolveCategoryKeepRemote(
        syncScopeKey: String,
        entity: CategoryEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertCategoryRows(listOf(entity))
        deleteOutboxRows(syncScopeKey, "CATEGORY", entity.id)
        deleteConflictRow(syncScopeKey, "CATEGORY", entity.id)
    }

    @Transaction
    suspend fun resolveTransactionKeepRemote(
        syncScopeKey: String,
        entity: TransactionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val existing = getTransactionRow(entity.id)
        upsertTransactionRows(listOf(entity.mergePreservingLocalMerchant(existing)))
        deleteOutboxRows(syncScopeKey, "TRANSACTION", entity.id)
        deleteConflictRow(syncScopeKey, "TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun resolveRecurringTransactionKeepRemote(
        syncScopeKey: String,
        entity: RecurringTransactionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertRecurringTransactionRows(listOf(entity))
        deleteOutboxRows(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
        deleteConflictRow(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun resolveSubscriptionKeepRemote(
        syncScopeKey: String,
        entity: SubscriptionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertSubscriptionRows(listOf(entity))
        deleteOutboxRows(syncScopeKey, "SUBSCRIPTION", entity.id)
        deleteConflictRow(syncScopeKey, "SUBSCRIPTION", entity.id)
    }

    @Transaction
    suspend fun resolveKeepLocal(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        operationTypeCode: String,
        nowEpochMillis: Long,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        deleteOtherOutboxRows(syncScopeKey, conflict.entityTypeCode, conflict.entityId, conflict.operationId)
        check(
            resetConflictOperation(
                syncScopeKey = syncScopeKey,
                operationId = conflict.operationId,
                operationTypeCode = operationTypeCode,
                remoteVersion = conflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            ) == 1,
        ) { "Çakışmaya ait outbox işlemi bulunamadı." }

        val updated =
            when (conflict.entityTypeCode) {
                "PROFILE" -> rebaseProfileForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
                "CATEGORY" -> rebaseCategoryForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
                "TRANSACTION" -> rebaseTransactionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
                "RECURRING_TRANSACTION" -> rebaseRecurringTransactionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
                "SUBSCRIPTION" -> rebaseSubscriptionForRetry(conflict.entityId, conflict.remoteVersion, nowEpochMillis)
                else -> error("Desteklenmeyen conflict entity türü: ${conflict.entityTypeCode}")
            }
        check(updated == 1) { "Çakışmanın yerel kaydı bulunamadı." }
        deleteConflictRow(syncScopeKey, conflict.entityTypeCode, conflict.entityId)
    }

    @Transaction
    suspend fun resolveWorkspaceKeepRemote(
        syncScopeKey: String,
        precondition: WorkspaceResolutionPrecondition,
        remoteEntity: WorkspaceEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val workspaceId = precondition.expectedConflict.entityId

        val liveConflict =
            getConflictRow(syncScopeKey, "WORKSPACE", workspaceId)
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

        val liveWs =
            getWorkspaceRow(workspaceId)
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

        val liveOps = getAllWorkspaceOperations(syncScopeKey, workspaceId)
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

        val liveTail =
            validateWorkspaceChain(precondition.expectedOperations)
                ?: throw WorkspaceConflictStaleResolutionException("KEEP_REMOTE için outbox zinciri boş olamaz.")
        if (liveTail.operationId != precondition.expectedConflict.operationId) {
            throw WorkspaceConflictStaleResolutionException(
                "Conflict operationId (${precondition.expectedConflict.operationId}) doğrulanmış zincir tail'i (${liveTail.operationId}) ile eşleşmiyor.",
            )
        }

        upsertWorkspaceRows(listOf(remoteEntity))

        if (precondition.expectedOperations.isNotEmpty()) {
            val allIds = precondition.expectedOperations.map { it.operationId }
            val deleted = deleteSpecificWorkspaceOperations(syncScopeKey, workspaceId, allIds)
            check(deleted == allIds.size) {
                "Silinen outbox satır sayısı ($deleted) beklenenle (${allIds.size}) eşleşmiyor."
            }
        }

        deleteConflictRow(syncScopeKey, "WORKSPACE", workspaceId)
    }

    @Transaction
    suspend fun resolveWorkspaceKeepLocal(
        syncScopeKey: String,
        precondition: WorkspaceResolutionPrecondition,
        retainedOperationId: String,
        targetOperationTypeCode: String,
        targetPayloadJson: String?,
        nowEpochMillis: Long,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val workspaceId = precondition.expectedConflict.entityId

        val liveConflict =
            getConflictRow(syncScopeKey, "WORKSPACE", workspaceId)
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

        val liveWs =
            getWorkspaceRow(workspaceId)
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

        val liveOps = getAllWorkspaceOperations(syncScopeKey, workspaceId)
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

        val liveTail =
            validateWorkspaceChain(precondition.expectedOperations)
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

        val nonTailIds =
            precondition.expectedOperations
                .map { it.operationId }
                .filter { it != liveTail.operationId }

        if (nonTailIds.isNotEmpty()) {
            val deleted = deleteSpecificWorkspaceOperations(syncScopeKey, workspaceId, nonTailIds)
            check(deleted == nonTailIds.size) {
                "Silinen predecessor satır sayısı ($deleted) beklenenle (${nonTailIds.size}) eşleşmiyor."
            }
        }

        val resetCount =
            resetWorkspaceConflictOperation(
                syncScopeKey = syncScopeKey,
                operationId = liveTail.operationId,
                operationTypeCode = targetOperationTypeCode,
                payloadJson = targetPayloadJson,
                remoteVersion = precondition.expectedConflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            )
        check(resetCount == 1) { "Tail outbox işlemi resetlenemedi: ${liveTail.operationId}" }

        val targetSyncStatus = if (targetOperationTypeCode == "DELETE") "PENDING_DELETE" else "PENDING_UPDATE"
        val rebaseCount =
            rebaseWorkspaceForRetry(
                workspaceId = workspaceId,
                syncStatus = targetSyncStatus,
                remoteVersion = precondition.expectedConflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            )
        check(rebaseCount == 1) { "Workspace ($workspaceId) rebase edilemedi." }

        deleteConflictRow(syncScopeKey, "WORKSPACE", workspaceId)
    }

    @Transaction
    suspend fun resolveUserMerchantVerificationKeepRemote(
        syncScopeKey: String,
        precondition: UserMerchantVerificationResolutionPrecondition,
        remoteEntity: UserMerchantVerificationEntity,
    ) {
        val tailOp = validateMerchantVerificationConflictPrecondition(syncScopeKey, precondition)
        val actorScope = SyncScopeKey.requireUserScope(syncScopeKey)
        val verificationId = precondition.expectedConflict.entityId

        validateRemoteUserMerchantVerification(syncScopeKey, actorScope.userId, remoteEntity)
        if (remoteEntity.id != verificationId ||
            remoteEntity.ownerId != actorScope.userId ||
            remoteEntity.workspaceId != null ||
            remoteEntity.scopeCode != "PERSONAL" ||
            remoteEntity.sync.version != precondition.expectedConflict.remoteVersion ||
            remoteEntity.sync.version < 1L
        ) {
            throw UserMerchantVerificationConflictStaleResolutionException("Uzak varlık invariant hatası.")
        }

        val decodedRemoteSnapshot =
            UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(
                precondition.expectedConflict.remotePayloadJson,
            )
        try {
            UserMerchantVerificationPayloadCodec.validateRemoteSnapshotMatchesEntity(decodedRemoteSnapshot, remoteEntity)
        } catch (e: Exception) {
            throw UserMerchantVerificationConflictStaleResolutionException("Remote snapshot entity ile eşleşmiyor: ${e.message}")
        }

        upsertUserMerchantVerificationRows(listOf(remoteEntity))

        if (precondition.expectedOperations.isNotEmpty()) {
            val allIds = precondition.expectedOperations.map { it.operationId }
            val deleted = deleteSpecificUserMerchantVerificationOperations(syncScopeKey, verificationId, allIds)
            check(deleted == allIds.size) {
                "Silinen outbox satır sayısı ($deleted) beklenenle (${allIds.size}) eşleşmiyor."
            }
        }

        deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", verificationId)
    }

    @Transaction
    suspend fun resolveUserMerchantVerificationKeepLocal(
        syncScopeKey: String,
        precondition: UserMerchantVerificationResolutionPrecondition,
        retainedOperationId: String,
        targetOperationTypeCode: String,
        targetPayloadJson: String?,
        nowEpochMillis: Long,
    ) {
        val tailOp = validateMerchantVerificationConflictPrecondition(syncScopeKey, precondition)
        val actorScope = SyncScopeKey.requireUserScope(syncScopeKey)
        val verificationId = precondition.expectedConflict.entityId
        val liveEntity = precondition.expectedLocalEntity

        if (retainedOperationId != tailOp.operationId) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "retainedOperationId ($retainedOperationId) doğrulanmış tail (${tailOp.operationId}) ile eşleşmiyor.",
            )
        }

        val expectedTargetOperationTypeCode = if (liveEntity.sync.deletedAtEpochMillis != null) "DELETE" else "UPDATE"
        if (targetOperationTypeCode != expectedTargetOperationTypeCode) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "targetOperationTypeCode ($targetOperationTypeCode) yerel durum ($expectedTargetOperationTypeCode) ile eşleşmiyor.",
            )
        }

        val expectedPayloadJson =
            if (liveEntity.sync.deletedAtEpochMillis != null) {
                UserMerchantVerificationPayloadCodec.encodeDelete(liveEntity)
            } else {
                UserMerchantVerificationPayloadCodec.encodeUpdate(liveEntity)
            }
        if (targetPayloadJson != expectedPayloadJson) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "targetPayloadJson güncel yerel karardan üretilen kanonik payload ile eşleşmiyor.",
            )
        }

        if (tailOp.operationTypeCode == "CREATE" && targetOperationTypeCode != "UPDATE") {
            throw UserMerchantVerificationConflictStaleResolutionException("CREATE operasyonu ancak UPDATE retry'a dönüşebilir.")
        }
        if (tailOp.operationTypeCode == "UPDATE" && targetOperationTypeCode != "UPDATE") {
            throw UserMerchantVerificationConflictStaleResolutionException("UPDATE operasyonu ancak UPDATE retry olabilir.")
        }
        if (tailOp.operationTypeCode == "DELETE" && targetOperationTypeCode != "DELETE") {
            throw UserMerchantVerificationConflictStaleResolutionException("DELETE operasyonu ancak DELETE retry olabilir.")
        }

        val nonTailIds =
            precondition.expectedOperations
                .map { it.operationId }
                .filter { it != tailOp.operationId }

        if (nonTailIds.isNotEmpty()) {
            val deleted = deleteSpecificUserMerchantVerificationOperations(syncScopeKey, verificationId, nonTailIds)
            check(deleted == nonTailIds.size) {
                "Silinen predecessor satır sayısı ($deleted) beklenenle (${nonTailIds.size}) eşleşmiyor."
            }
        }

        val resetCount =
            resetUserMerchantVerificationConflictOperation(
                syncScopeKey = syncScopeKey,
                operationId = tailOp.operationId,
                operationTypeCode = targetOperationTypeCode,
                payloadJson = targetPayloadJson,
                remoteVersion = precondition.expectedConflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            )
        check(resetCount == 1) { "Tail outbox işlemi resetlenemedi: ${tailOp.operationId}" }

        val targetSyncStatus = if (targetOperationTypeCode == "DELETE") "PENDING_DELETE" else "PENDING_UPDATE"
        val rebaseCount =
            rebaseUserMerchantVerificationForRetry(
                verificationId = verificationId,
                syncScopeKey = syncScopeKey,
                syncStatus = targetSyncStatus,
                remoteVersion = precondition.expectedConflict.remoteVersion,
                nowEpochMillis = nowEpochMillis,
            )
        check(rebaseCount == 1) { "Merchant verification rebase edilemedi: $verificationId" }

        deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", verificationId)
    }

    private suspend fun validateMerchantVerificationConflictPrecondition(
        syncScopeKey: String,
        precondition: UserMerchantVerificationResolutionPrecondition,
    ): SyncOperationEntity {
        val actorScope =
            try {
                SyncScopeKey.requireUserScope(syncScopeKey)
            } catch (e: Exception) {
                throw UserMerchantVerificationConflictStaleResolutionException("Geçersiz syncScopeKey: $syncScopeKey")
            }
        val actorUserId = actorScope.userId
        if (!UuidHelper.isCanonicalUuid(actorUserId)) {
            throw UserMerchantVerificationConflictStaleResolutionException("Aktör userId canonical UUID değil: $actorUserId")
        }

        val expConflict = precondition.expectedConflict
        if (expConflict.entityTypeCode != "USER_MERCHANT_VERIFICATION") {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "Conflict entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${expConflict.entityTypeCode}",
            )
        }
        val verificationId = expConflict.entityId
        if (!UuidHelper.isCanonicalUuid(verificationId)) {
            throw UserMerchantVerificationConflictStaleResolutionException("Geçersiz verificationId: $verificationId")
        }

        val liveConflict =
            getConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", verificationId)
                ?: throw UserMerchantVerificationConflictStaleResolutionException(
                    "Çakışma satırı canlı veritabanında bulunamadı: $verificationId",
                )

        if (liveConflict.syncScopeKey != syncScopeKey ||
            liveConflict.entityTypeCode != "USER_MERCHANT_VERIFICATION" ||
            liveConflict.entityId != verificationId ||
            liveConflict.operationId != expConflict.operationId ||
            liveConflict.remoteVersion != expConflict.remoteVersion ||
            liveConflict.localVersion != expConflict.localVersion ||
            liveConflict.detectedAtEpochMillis != expConflict.detectedAtEpochMillis ||
            liveConflict.localPayloadJson != expConflict.localPayloadJson ||
            liveConflict.remotePayloadJson != expConflict.remotePayloadJson
        ) {
            throw UserMerchantVerificationConflictStaleResolutionException("Çakışma satırı canlı veritabanında değişmiş: $verificationId")
        }

        if (expConflict.remoteVersion < 1L) {
            throw UserMerchantVerificationConflictStaleResolutionException("Remote version >= 1 olmalıdır: ${expConflict.remoteVersion}")
        }

        val decodedLocalSnapshot =
            try {
                UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(expConflict.localPayloadJson)
            } catch (e: Exception) {
                throw UserMerchantVerificationConflictStaleResolutionException("Bozuk local conflict snapshot: ${e.message}")
            }

        val decodedRemoteSnapshot =
            try {
                UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(expConflict.remotePayloadJson)
            } catch (e: Exception) {
                throw UserMerchantVerificationConflictStaleResolutionException("Bozuk remote conflict snapshot: ${e.message}")
            }

        if (decodedRemoteSnapshot.version == null ||
            decodedRemoteSnapshot.version < 1L ||
            decodedRemoteSnapshot.version != expConflict.remoteVersion
        ) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "Remote snapshot version uyuşmuyor veya < 1: ${decodedRemoteSnapshot.version}",
            )
        }
        if (decodedLocalSnapshot.version != expConflict.localVersion) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "Local snapshot version uyuşmuyor: ${decodedLocalSnapshot.version} vs ${expConflict.localVersion}",
            )
        }

        if (decodedLocalSnapshot.userId != actorUserId || decodedRemoteSnapshot.userId != actorUserId) {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot userId ($actorUserId) ile uyuşmuyor.")
        }
        if (decodedLocalSnapshot.workspaceId != null || decodedRemoteSnapshot.workspaceId != null) {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot workspaceId null olmalıdır.")
        }
        if (decodedLocalSnapshot.scopeCode != "PERSONAL" || decodedRemoteSnapshot.scopeCode != "PERSONAL") {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot scopeCode PERSONAL olmalıdır.")
        }
        if (decodedLocalSnapshot.id != verificationId || decodedRemoteSnapshot.id != verificationId) {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot id ($verificationId) ile uyuşmuyor.")
        }

        val localCanonicalAlias = TurkishDescriptionNormalizer.normalize(decodedLocalSnapshot.normalizedAlias)
        val remoteCanonicalAlias = TurkishDescriptionNormalizer.normalize(decodedRemoteSnapshot.normalizedAlias)
        if (decodedLocalSnapshot.normalizedAlias != localCanonicalAlias || decodedRemoteSnapshot.normalizedAlias != remoteCanonicalAlias) {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot normalizedAlias kanonik değil.")
        }
        if (decodedLocalSnapshot.normalizedAlias in DISALLOWED_STANDALONE_GENERIC_TOKENS ||
            decodedRemoteSnapshot.normalizedAlias in DISALLOWED_STANDALONE_GENERIC_TOKENS
        ) {
            throw UserMerchantVerificationConflictStaleResolutionException("Snapshot alias yasaklı sözcük içeriyor.")
        }

        val expectedLocalDeterministicId =
            UuidHelper.deterministicMerchantVerificationUuid(
                actorUserId,
                decodedLocalSnapshot.normalizedAlias,
            )
        val expectedRemoteDeterministicId =
            UuidHelper.deterministicMerchantVerificationUuid(
                actorUserId,
                decodedRemoteSnapshot.normalizedAlias,
            )
        if (verificationId != expectedLocalDeterministicId || verificationId != expectedRemoteDeterministicId) {
            throw UserMerchantVerificationConflictStaleResolutionException("Deterministik ID eşleşmiyor.")
        }

        val liveEntity =
            getUserMerchantVerificationRow(verificationId)
                ?: throw UserMerchantVerificationConflictStaleResolutionException(
                    "Yerel merchant verification canlı veritabanında bulunamadı: $verificationId",
                )

        val expLocalEntity = precondition.expectedLocalEntity
        if (liveEntity != expLocalEntity) {
            throw UserMerchantVerificationConflictStaleResolutionException("Yerel merchant verification snapshot ile uyuşmuyor (TOCTOU).")
        }

        if (liveEntity.ownerId != actorUserId ||
            liveEntity.scopeKey != "user:$actorUserId" ||
            liveEntity.workspaceId != null ||
            liveEntity.scopeCode != "PERSONAL" ||
            liveEntity.id != verificationId
        ) {
            throw UserMerchantVerificationConflictStaleResolutionException("Yerel entity scope/owner uyuşmuyor.")
        }
        if (liveEntity.sync.version != expConflict.localVersion) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "Yerel entity version (${liveEntity.sync.version}) conflict localVersion (${expConflict.localVersion}) ile uyuşmuyor.",
            )
        }

        val liveOps = getAllUserMerchantVerificationOperations(syncScopeKey, verificationId)
        if (liveOps.size != precondition.expectedOperations.size) {
            throw UserMerchantVerificationConflictStaleResolutionException(
                "Outbox satır sayısı uyuşmuyor: beklenen ${precondition.expectedOperations.size}, canlı ${liveOps.size}",
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
                live.updatedAtEpochMillis != exp.updatedAtEpochMillis ||
                live.protocolVersion != exp.protocolVersion ||
                live.attemptCount != exp.attemptCount
            ) {
                throw UserMerchantVerificationConflictStaleResolutionException(
                    "Outbox operasyonu (${live.operationId}) canlı veritabanında değişmiş.",
                )
            }
            if (live.protocolVersion != 2) {
                throw UserMerchantVerificationConflictStaleResolutionException("Outbox protocolVersion != 2: ${live.operationId}")
            }
            if (live.statusCode == "FAILED" || live.isDefinitiveRejection()) {
                throw UserMerchantVerificationConflictStaleResolutionException("Outbox operasyonu FAILED/definitive: ${live.operationId}")
            }
            if (live.payloadJson.isNullOrBlank()) {
                throw UserMerchantVerificationConflictStaleResolutionException("Outbox payloadJson boş: ${live.operationId}")
            }
        }

        if (liveOps.isEmpty()) {
            throw UserMerchantVerificationConflictStaleResolutionException("Outbox operasyonları boş olamaz.")
        }

        val roots = liveOps.filter { it.predecessorOperationId == null }
        if (roots.size != 1) {
            throw UserMerchantVerificationConflictStaleResolutionException("Tam olarak 1 root operasyon beklenirken ${roots.size} bulundu.")
        }
        val root = roots.first()
        if (root.isBlocked) {
            throw UserMerchantVerificationConflictStaleResolutionException("Root operasyon isBlocked olamaz.")
        }

        val tailCandidate: SyncOperationEntity
        when (liveOps.size) {
            1 -> {
                tailCandidate = root
                if (tailCandidate.operationId != expConflict.operationId) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Conflict operationId (${expConflict.operationId}) root operasyon (${tailCandidate.operationId}) ile eşleşmiyor.",
                    )
                }
                val allowedStatuses = setOf("PENDING", "IN_FLIGHT", "CONFLICT")
                if (tailCandidate.statusCode !in allowedStatuses) {
                    throw UserMerchantVerificationConflictStaleResolutionException("Tail statusCode geçersiz: ${tailCandidate.statusCode}")
                }
                try {
                    UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = tailCandidate.payloadJson!!,
                        operation = tailCandidate.operationTypeCode,
                        baseVersion = tailCandidate.baseVersion,
                    )
                } catch (e: Exception) {
                    throw UserMerchantVerificationConflictStaleResolutionException("Outbox payloadJson geçersiz: ${e.message}")
                }
                if (tailCandidate.operationTypeCode == "CREATE") {
                    if (tailCandidate.baseVersion != null || liveEntity.sync.baseVersion != null || liveEntity.sync.version != 0L) {
                        throw UserMerchantVerificationConflictStaleResolutionException(
                            "CREATE operasyonu baseVersion/version invariant hatası.",
                        )
                    }
                } else if (tailCandidate.operationTypeCode in setOf("UPDATE", "DELETE")) {
                    val opBaseVer = tailCandidate.baseVersion
                    val entityBaseVer = liveEntity.sync.baseVersion
                    if (opBaseVer == null || opBaseVer < 1L || entityBaseVer == null || entityBaseVer < 1L || opBaseVer != entityBaseVer) {
                        throw UserMerchantVerificationConflictStaleResolutionException(
                            "UPDATE/DELETE operasyonu baseVersion invariant hatası.",
                        )
                    }
                    if (entityBaseVer > liveEntity.sync.version) {
                        throw UserMerchantVerificationConflictStaleResolutionException("Yerel baseVersion > version olamaz.")
                    }
                } else {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Desteklenmeyen operationTypeCode: ${tailCandidate.operationTypeCode}",
                    )
                }
            }
            2 -> {
                if (root.operationTypeCode != "CREATE") {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Predecessor CREATE olmalıdır: ${root.operationTypeCode}",
                    )
                }
                if (root.statusCode != "CONFLICT") {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Predecessor statusCode CONFLICT olmalıdır: ${root.statusCode}",
                    )
                }
                if (root.baseVersion != null) {
                    throw UserMerchantVerificationConflictStaleResolutionException("Predecessor baseVersion null olmalıdır.")
                }
                if (expConflict.operationId != root.operationId) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Conflict operationId (${expConflict.operationId}) predecessor CREATE (${root.operationId}) ile eşleşmeli.",
                    )
                }
                try {
                    UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = root.payloadJson!!,
                        operation = "CREATE",
                        baseVersion = null,
                    )
                } catch (e: Exception) {
                    throw UserMerchantVerificationConflictStaleResolutionException("Predecessor payloadJson geçersiz: ${e.message}")
                }

                val successor =
                    liveOps.firstOrNull { it.predecessorOperationId == root.operationId }
                        ?: throw UserMerchantVerificationConflictStaleResolutionException("Successor root'a bağlı değil.")

                if (successor.operationTypeCode != "UPDATE") {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor operationTypeCode UPDATE olmalıdır: ${successor.operationTypeCode}",
                    )
                }
                if (successor.statusCode != "PENDING") {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor statusCode PENDING olmalıdır: ${successor.statusCode}",
                    )
                }
                if (!successor.isBlocked) {
                    throw UserMerchantVerificationConflictStaleResolutionException("Successor isBlocked true olmalıdır.")
                }
                if (successor.attemptCount != 0) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor attemptCount 0 olmalıdır: ${successor.attemptCount}",
                    )
                }
                if (successor.protocolVersion != 2) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor protocolVersion 2 olmalıdır: ${successor.protocolVersion}",
                    )
                }
                if (successor.baseVersion != null) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor baseVersion null olmalıdır: ${successor.baseVersion}",
                    )
                }

                val succPayload =
                    try {
                        UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = successor.payloadJson!!,
                            operation = "CREATE",
                            baseVersion = null,
                        )
                    } catch (e: Exception) {
                        throw UserMerchantVerificationConflictStaleResolutionException("Successor payloadJson geçersiz: ${e.message}")
                    }

                if (succPayload.id != liveEntity.id ||
                    succPayload.userId != liveEntity.ownerId ||
                    succPayload.workspaceId != liveEntity.workspaceId ||
                    succPayload.scopeCode != liveEntity.scopeCode ||
                    succPayload.normalizedAlias != liveEntity.normalizedAlias ||
                    succPayload.decisionCode != liveEntity.decisionCode ||
                    succPayload.merchantId != liveEntity.merchantId
                ) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Successor payload'ı güncel yerel entity'nin yedi iş alanıyla eşleşmiyor.",
                    )
                }

                if (liveEntity.sync.version != 0L || liveEntity.sync.baseVersion != null) {
                    throw UserMerchantVerificationConflictStaleResolutionException(
                        "Predecessor CREATE zincirinde yerel entity version=0 ve baseVersion=null olmalıdır.",
                    )
                }

                tailCandidate = successor
            }
            else -> {
                throw UserMerchantVerificationConflictStaleResolutionException("Beklenmeyen outbox zincir uzunluğu: ${liveOps.size}")
            }
        }

        return tailCandidate
    }

    @Transaction
    suspend fun applyProfilePull(
        syncScopeKey: String,
        entity: UserProfileEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertProfileRow(entity)
        deleteConflictRow(syncScopeKey, "PROFILE", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyCategoryPull(
        syncScopeKey: String,
        entity: CategoryEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertCategoryRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "CATEGORY", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyTransactionPull(
        syncScopeKey: String,
        entity: TransactionEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        val existing = getTransactionRow(entity.id)
        upsertTransactionRows(listOf(entity.mergePreservingLocalMerchant(existing)))
        deleteConflictRow(syncScopeKey, "TRANSACTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyRecurringTransactionPull(
        syncScopeKey: String,
        entity: RecurringTransactionEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertRecurringTransactionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applySubscriptionPull(
        syncScopeKey: String,
        entity: SubscriptionEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertSubscriptionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "SUBSCRIPTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyGoalPull(
        syncScopeKey: String,
        entity: GoalEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertGoalRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "GOAL", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyGoalContributionPull(
        syncScopeKey: String,
        entity: GoalContributionEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertGoalContributionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "GOAL_CONTRIBUTION", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyDebtPull(
        syncScopeKey: String,
        entity: DebtEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertDebtRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "DEBT", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyDebtPaymentPull(
        syncScopeKey: String,
        entity: DebtPaymentEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertDebtPaymentRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "DEBT_PAYMENT", entity.id)
        upsertCursorRows(listOf(cursor))
    }

    private fun validateRemoteUserMerchantVerification(
        syncScopeKey: String,
        actorUserId: String,
        remoteEntity: UserMerchantVerificationEntity,
    ) {
        require(UuidHelper.isCanonicalUuid(remoteEntity.ownerId)) {
            "Geçersiz owner UUID: ${remoteEntity.ownerId}"
        }
        require(remoteEntity.ownerId == actorUserId) {
            "Remote ownerId (${remoteEntity.ownerId}) actor userId ($actorUserId) ile eşleşmelidir."
        }
        require(remoteEntity.scopeKey == "user:$actorUserId") {
            "Remote scopeKey (${remoteEntity.scopeKey}) beklenen (user:$actorUserId) ile eşleşmelidir."
        }
        require(remoteEntity.workspaceId == null) {
            "Kişisel kapsamda workspaceId null olmalıdır: ${remoteEntity.workspaceId}"
        }
        require(remoteEntity.scopeCode == "PERSONAL") {
            "scopeCode PERSONAL olmalıdır: ${remoteEntity.scopeCode}"
        }

        val normalizedAlias = remoteEntity.normalizedAlias
        require(
            normalizedAlias.isNotBlank() && normalizedAlias.length <= com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH,
        ) {
            "Geçersiz alias uzunluğu: $normalizedAlias"
        }
        require(TurkishDescriptionNormalizer.normalize(normalizedAlias) == normalizedAlias) {
            "Alias normalize edilmiş (canonical) olmalıdır: $normalizedAlias"
        }
        require(normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Genel sözcükler merchant alias'ı olamaz: $normalizedAlias"
        }

        require(UuidHelper.isCanonicalUuid(remoteEntity.id)) {
            "Geçersiz ID UUID: ${remoteEntity.id}"
        }
        val expectedId = UuidHelper.deterministicMerchantVerificationUuid(remoteEntity.ownerId, normalizedAlias)
        require(remoteEntity.id == expectedId) {
            "Remote ID (${remoteEntity.id}) deterministik UUID ($expectedId) ile eşleşmiyor."
        }

        when (remoteEntity.decisionCode) {
            "ASSIGN" -> {
                require(!remoteEntity.merchantId.isNullOrBlank()) {
                    "ASSIGN kararı için merchantId boş olamaz."
                }
            }
            "BLOCK" -> {
                require(remoteEntity.merchantId == null) {
                    "BLOCK kararı için merchantId null olmalıdır: ${remoteEntity.merchantId}"
                }
            }
            else -> throw IllegalArgumentException("Geçersiz decisionCode: ${remoteEntity.decisionCode}")
        }

        require(remoteEntity.sync.syncStatus == "SYNCED") {
            "Remote entity syncStatus SYNCED olmalıdır: ${remoteEntity.sync.syncStatus}"
        }
        require(remoteEntity.sync.version >= 1L) {
            "Remote entity version en az 1 olmalıdır: ${remoteEntity.sync.version}"
        }
        require(remoteEntity.sync.baseVersion == remoteEntity.sync.version) {
            "Remote entity baseVersion (${remoteEntity.sync.baseVersion}) version (${remoteEntity.sync.version}) ile eşleşmelidir."
        }
        require(remoteEntity.sync.lastSyncError == null) {
            "Remote entity lastSyncError null olmalıdır: ${remoteEntity.sync.lastSyncError}"
        }

        require(remoteEntity.createdAtEpochMillis > 0L) {
            "createdAtEpochMillis pozitif olmalıdır: ${remoteEntity.createdAtEpochMillis}"
        }
        require(remoteEntity.sync.updatedAtEpochMillis >= remoteEntity.createdAtEpochMillis) {
            "updatedAtEpochMillis (${remoteEntity.sync.updatedAtEpochMillis}) createdAtEpochMillis (${remoteEntity.createdAtEpochMillis}) değerinden önce olamaz."
        }
        if (remoteEntity.sync.deletedAtEpochMillis != null) {
            require(remoteEntity.sync.deletedAtEpochMillis >= remoteEntity.createdAtEpochMillis) {
                "deletedAtEpochMillis createdAtEpochMillis değerinden önce olamaz."
            }
            require(remoteEntity.sync.deletedAtEpochMillis <= remoteEntity.sync.updatedAtEpochMillis) {
                "deletedAtEpochMillis updatedAtEpochMillis değerinden sonra olamaz."
            }
        }
    }

    @Transaction
    suspend fun applyUserMerchantVerificationPull(
        syncScopeKey: String,
        remoteEntity: UserMerchantVerificationEntity,
        cursor: SyncCursorEntity,
    ) {
        val actorScope = SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        require(cursor.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Cursor entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${cursor.entityTypeCode}"
        }
        require(cursor.entityId == remoteEntity.id) {
            "Cursor entityId (${cursor.entityId}) ile remote entity id (${remoteEntity.id}) eşleşmelidir."
        }
        require(cursor.updatedAtEpochMillis == remoteEntity.sync.updatedAtEpochMillis) {
            "Cursor zamanı (${cursor.updatedAtEpochMillis}) remote entity updatedAtEpochMillis (${remoteEntity.sync.updatedAtEpochMillis}) ile eşleşmelidir."
        }

        // Remote entity fail-closed doğrulaması (ortak helper)
        validateRemoteUserMerchantVerification(syncScopeKey, actorScope.userId, remoteEntity)

        // Yerel kayıt durumu denetimi
        val local = getUserMerchantVerificationRow(remoteEntity.id)
        if (local != null) {
            require(local.scopeKey == remoteEntity.scopeKey) {
                "Yerel kayıt scopeKey mismatch: local=${local.scopeKey}, remote=${remoteEntity.scopeKey}"
            }
            require(local.sync.syncStatus == "SYNCED") {
                "Yerel pending/conflict kayıt üzerine pull uygulanamaz: id=${remoteEntity.id}, status=${local.sync.syncStatus}"
            }
            if (remoteEntity.sync.version < local.sync.version) {
                throw IllegalArgumentException(
                    "Daha eski remote version (${remoteEntity.sync.version}) yerel version (${local.sync.version}) üzerine uygulanamaz: id=${remoteEntity.id}",
                )
            }
            if (remoteEntity.sync.version == local.sync.version) {
                val isAuthoritativeEqual =
                    local.id == remoteEntity.id &&
                        local.ownerId == remoteEntity.ownerId &&
                        local.workspaceId == remoteEntity.workspaceId &&
                        local.scopeKey == remoteEntity.scopeKey &&
                        local.normalizedAlias == remoteEntity.normalizedAlias &&
                        local.decisionCode == remoteEntity.decisionCode &&
                        local.merchantId == remoteEntity.merchantId &&
                        local.scopeCode == remoteEntity.scopeCode &&
                        local.createdAtEpochMillis == remoteEntity.createdAtEpochMillis &&
                        local.sync.syncStatus == remoteEntity.sync.syncStatus &&
                        local.sync.version == remoteEntity.sync.version &&
                        local.sync.baseVersion == remoteEntity.sync.baseVersion &&
                        local.sync.updatedAtEpochMillis == remoteEntity.sync.updatedAtEpochMillis &&
                        local.sync.deletedAtEpochMillis == remoteEntity.sync.deletedAtEpochMillis &&
                        local.sync.lastSyncError == remoteEntity.sync.lastSyncError

                if (!isAuthoritativeEqual) {
                    throw IllegalArgumentException(
                        "Aynı version (${remoteEntity.sync.version}) fakat farklı authoritative alan taşıyan remote kayıt reddedildi: id=${remoteEntity.id}",
                    )
                }

                // Aynı version ve gerçekten eşdeğer replay durumunda entity'yi gereksiz yere yeniden yazma;
                // yalnız gerekli conflict temizliği/cursor işlemini atomik yap.
                deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteEntity.id)
                upsertCursorRows(listOf(cursor))
                return
            }
        }

        upsertUserMerchantVerificationRow(remoteEntity)
        deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteEntity.id)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun advanceUserMerchantVerificationPullCursor(
        syncScopeKey: String,
        remoteEntity: UserMerchantVerificationEntity,
        cursor: SyncCursorEntity,
    ) {
        val actorScope = SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        require(cursor.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Cursor entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${cursor.entityTypeCode}"
        }
        require(cursor.entityId == remoteEntity.id) {
            "Cursor entityId (${cursor.entityId}) ile remote entity id (${remoteEntity.id}) eşleşmelidir."
        }
        require(cursor.updatedAtEpochMillis == remoteEntity.sync.updatedAtEpochMillis) {
            "Cursor zamanı (${cursor.updatedAtEpochMillis}) remote entity updatedAtEpochMillis (${remoteEntity.sync.updatedAtEpochMillis}) ile eşleşmelidir."
        }

        // 1. Ortak validateRemoteUserMerchantVerification doğrulamasını kullan
        validateRemoteUserMerchantVerification(syncScopeKey, actorScope.userId, remoteEntity)

        // 2. Yerel entity doğrulaması
        val local = getUserMerchantVerificationRow(remoteEntity.id)
        requireNotNull(local) {
            "Yerel merchant verification bulunamadı: id=${remoteEntity.id}"
        }
        require(local.scopeKey == "user:${actorScope.userId}") {
            "Yerel scopeKey (${local.scopeKey}) user:${actorScope.userId} ile eşleşmelidir."
        }
        val allowedStatuses = setOf("PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE", "CONFLICT")
        require(local.sync.syncStatus in allowedStatuses) {
            "advanceUserMerchantVerificationPullCursor yalnız pending veya conflict durumundaki kayıtlar için geçerlidir: id=${remoteEntity.id}, status=${local.sync.syncStatus}"
        }

        // 3. remoteVersion <= localBaseVersion koşulu
        val localBaseVersion = local.sync.baseVersion ?: 0L
        require(remoteEntity.sync.version <= localBaseVersion) {
            "Remote version (${remoteEntity.sync.version}) yerel baseVersion ($localBaseVersion) değerinden büyük olamaz."
        }

        // 4. Canlı aktif tail outbox ve topoloji doğrulaması (ortak helper, expectedOperationId = null)
        validateLiveUserMerchantVerificationOutbox(syncScopeKey, local)

        // 5. Entity, outbox veya conflict snapshot'ını değiştirmeden yalnız cursor ilerlet
        upsertCursorRows(listOf(cursor))
    }

    private suspend fun validateLiveUserMerchantVerificationOutbox(
        syncScopeKey: String,
        local: UserMerchantVerificationEntity,
        expectedOperationId: String? = null,
    ): SyncOperationEntity {
        val tailCandidates = getActiveMerchantVerificationTailCandidates(syncScopeKey, local.id)
        require(tailCandidates.isNotEmpty()) {
            "Canlı outbox işlemi bulunamadı: id=${local.id}"
        }
        require(tailCandidates.size == 1) {
            "Birden fazla aktif tail outbox işlemi bulundu: id=${local.id}, count=${tailCandidates.size}"
        }
        val liveOp = tailCandidates.single()

        if (expectedOperationId != null) {
            require(liveOp.operationId == expectedOperationId) {
                "Beklenen operationId ($expectedOperationId) ile aktif tail operationId (${liveOp.operationId}) eşleşmiyor."
            }
        }

        require(liveOp.syncScopeKey == syncScopeKey) {
            "Outbox syncScopeKey mismatch: ${liveOp.syncScopeKey}"
        }
        require(liveOp.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Outbox entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${liveOp.entityTypeCode}"
        }
        require(liveOp.entityId == local.id) {
            "Outbox entityId (${liveOp.entityId}) yerel id (${local.id}) ile eşleşmiyor."
        }
        require(liveOp.protocolVersion == 2) {
            "Outbox protocolVersion 2 olmalıdır: ${liveOp.protocolVersion}"
        }
        require(!liveOp.isDefinitiveRejection() && liveOp.statusCode != "FAILED") {
            "Definitive veya FAILED outbox operasyonu canlı kabul edilemez: status=${liveOp.statusCode}, errorClassification=${liveOp.errorClassification}"
        }

        // Topoloji invariantları (Root tail vs Successor tail)
        if (liveOp.predecessorOperationId == null) {
            require(!liveOp.isBlocked) {
                "Root tail operasyonun isBlocked değeri false olmalıdır: ${liveOp.operationId}"
            }
        } else {
            require(liveOp.isBlocked) {
                "Predecessor'ı olan successor tail operasyonun isBlocked değeri true olmalıdır: ${liveOp.operationId}"
            }
            val predecessor = getUserMerchantVerificationOutboxRow(syncScopeKey, local.id, liveOp.predecessorOperationId)
            requireNotNull(predecessor) {
                "Orphan successor tail; predecessor operasyon aynı scope ve entity altında bulunamadı: successor=${liveOp.operationId}, predecessor=${liveOp.predecessorOperationId}"
            }
            require(predecessor.protocolVersion == 2) {
                "Predecessor protocolVersion == 2 olmalıdır: predecessor=${predecessor.operationId}, version=${predecessor.protocolVersion}"
            }
            require(predecessor.statusCode == "IN_FLIGHT") {
                "Predecessor statusCode == IN_FLIGHT olmalıdır: predecessor=${predecessor.operationId}, status=${predecessor.statusCode}"
            }
            require(!predecessor.isBlocked) {
                "Predecessor operasyonun isBlocked değeri false olmalıdır: ${predecessor.operationId}"
            }
            require(!predecessor.isDefinitiveRejection() && predecessor.statusCode != "FAILED") {
                "Predecessor terminal/FAILED olamaz: status=${predecessor.statusCode}, classification=${predecessor.errorClassification}"
            }
        }

        val allowedStatusCodes =
            when (local.sync.syncStatus) {
                "PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE" -> setOf("PENDING", "IN_FLIGHT")
                "CONFLICT" -> setOf("PENDING", "IN_FLIGHT", "CONFLICT")
                else -> emptySet()
            }
        require(liveOp.statusCode in allowedStatusCodes) {
            "Canlı outbox statusCode (${liveOp.statusCode}) yerel sync durumu (${local.sync.syncStatus}) ile uyumlu değil: ${local.id}"
        }

        when (local.sync.syncStatus) {
            "PENDING_CREATE" ->
                require(liveOp.operationTypeCode == "CREATE") {
                    "PENDING_CREATE için operationTypeCode CREATE olmalıdır: ${liveOp.operationTypeCode}"
                }
            "PENDING_UPDATE" ->
                require(liveOp.operationTypeCode == "UPDATE") {
                    "PENDING_UPDATE için operationTypeCode UPDATE olmalıdır: ${liveOp.operationTypeCode}"
                }
            "PENDING_DELETE" ->
                require(liveOp.operationTypeCode == "DELETE") {
                    "PENDING_DELETE için operationTypeCode DELETE olmalıdır: ${liveOp.operationTypeCode}"
                }
            "CONFLICT" ->
                require(liveOp.operationTypeCode in setOf("CREATE", "UPDATE", "DELETE")) {
                    "CONFLICT için operationTypeCode korunmuş canlı mutasyon türü olmalıdır: ${liveOp.operationTypeCode}"
                }
        }

        when (liveOp.operationTypeCode) {
            "CREATE" -> {
                require(local.sync.syncStatus == "PENDING_CREATE" || local.sync.syncStatus == "CONFLICT") {
                    "CREATE operasyonu için yerel durum PENDING_CREATE veya CONFLICT olmalıdır: ${local.sync.syncStatus}"
                }
                require(liveOp.baseVersion == null) {
                    "CREATE operasyonu için outbox baseVersion null olmalıdır: ${liveOp.baseVersion}"
                }
                require(local.sync.baseVersion == null) {
                    "CREATE operasyonu için yerel baseVersion null olmalıdır: ${local.sync.baseVersion}"
                }
                require(local.sync.version == 0L) {
                    "CREATE operasyonu için yerel version 0 olmalıdır: ${local.sync.version}"
                }
            }
            "UPDATE", "DELETE" -> {
                val liveBaseVersion =
                    requireNotNull(liveOp.baseVersion) {
                        "${liveOp.operationTypeCode} operasyonu için outbox baseVersion zorunludur."
                    }
                require(liveBaseVersion >= 1L) {
                    "${liveOp.operationTypeCode} operasyonu için outbox baseVersion >= 1 olmalıdır: $liveBaseVersion"
                }
                val localBaseVer =
                    requireNotNull(local.sync.baseVersion) {
                        "${liveOp.operationTypeCode} operasyonu için yerel baseVersion zorunludur: ${local.sync.baseVersion}"
                    }
                require(localBaseVer >= 1L) {
                    "${liveOp.operationTypeCode} operasyonu için yerel baseVersion >= 1 olmalıdır: $localBaseVer"
                }
                require(liveBaseVersion == localBaseVer) {
                    "Outbox baseVersion ($liveBaseVersion) yerel baseVersion ($localBaseVer) ile eşleşmelidir."
                }
                require(local.sync.version >= 1L) {
                    "${liveOp.operationTypeCode} operasyonu için yerel version >= 1 olmalıdır: ${local.sync.version}"
                }
                require(localBaseVer <= local.sync.version) {
                    "Yerel baseVersion ($localBaseVer) yerel version (${local.sync.version}) değerinden büyük olamaz."
                }
            }
            else -> error("Desteklenmeyen outbox operasyon türü: ${liveOp.operationTypeCode}")
        }

        require(!liveOp.payloadJson.isNullOrBlank()) {
            "Canlı outbox payloadJson boş olamaz: ${local.id}"
        }
        val validatedPayload =
            UserMerchantVerificationPayloadCodec.parseAndValidate(
                jsonString = liveOp.payloadJson,
                operation = liveOp.operationTypeCode,
                baseVersion = liveOp.baseVersion,
            )
        require(validatedPayload.id == local.id) {
            "Payload id (${validatedPayload.id}) yerel id (${local.id}) ile eşleşmiyor."
        }
        require(validatedPayload.userId == local.ownerId) {
            "Payload user_id (${validatedPayload.userId}) yerel ownerId (${local.ownerId}) ile eşleşmiyor."
        }
        require(validatedPayload.normalizedAlias == local.normalizedAlias) {
            "Payload normalized_alias (${validatedPayload.normalizedAlias}) yerel normalizedAlias (${local.normalizedAlias}) ile eşleşmiyor."
        }
        require(validatedPayload.decisionCode == local.decisionCode) {
            "Payload decision_code (${validatedPayload.decisionCode}) yerel decisionCode (${local.decisionCode}) ile eşleşmiyor."
        }
        require(validatedPayload.merchantId == local.merchantId) {
            "Payload merchant_id (${validatedPayload.merchantId}) yerel merchantId (${local.merchantId}) ile eşleşmiyor."
        }
        require(validatedPayload.scopeCode == local.scopeCode) {
            "Payload scope_code (${validatedPayload.scopeCode}) yerel scopeCode (${local.scopeCode}) ile eşleşmiyor."
        }
        require(validatedPayload.workspaceId == local.workspaceId) {
            "Payload workspace_id (${validatedPayload.workspaceId}) yerel workspaceId (${local.workspaceId}) ile eşleşmiyor."
        }

        return liveOp
    }

    @Transaction
    suspend fun advancePullCursor(
        syncScopeKey: String,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun applyCategoryWrite(
        syncScopeKey: String,
        entity: CategoryEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertCategoryRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "CATEGORY", entity.id)
    }

    @Transaction
    suspend fun applyTransactionWrite(
        syncScopeKey: String,
        entity: TransactionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val existing = getTransactionRow(entity.id)
        upsertTransactionRows(listOf(entity.mergePreservingLocalMerchant(existing)))
        deleteConflictRow(syncScopeKey, "TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun applyRecurringTransactionWrite(
        syncScopeKey: String,
        entity: RecurringTransactionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertRecurringTransactionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
    }

    @Transaction
    suspend fun applySubscriptionWrite(
        syncScopeKey: String,
        entity: SubscriptionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertSubscriptionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "SUBSCRIPTION", entity.id)
    }

    @Transaction
    suspend fun applyGoalWrite(
        syncScopeKey: String,
        entity: GoalEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertGoalRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "GOAL", entity.id)
    }

    @Transaction
    suspend fun applyGoalContributionWrite(
        syncScopeKey: String,
        entity: GoalContributionEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertGoalContributionRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "GOAL_CONTRIBUTION", entity.id)
    }

    @Transaction
    suspend fun applyDebtWrite(
        syncScopeKey: String,
        entity: DebtEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertDebtRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "DEBT", entity.id)
    }

    @Transaction
    suspend fun applyDebtPaymentWrite(
        syncScopeKey: String,
        entity: DebtPaymentEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        upsertDebtPaymentRows(listOf(entity))
        deleteConflictRow(syncScopeKey, "DEBT_PAYMENT", entity.id)
    }

    @Transaction
    suspend fun recordProfileConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markProfileConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordProfilePullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordProfileConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordCategoryConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markCategoryConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordCategoryPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordCategoryConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordTransactionConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markTransactionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordTransactionPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordTransactionConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordRecurringTransactionConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markRecurringTransactionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordRecurringTransactionPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordRecurringTransactionConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordSubscriptionConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markSubscriptionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordSubscriptionPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordSubscriptionConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordGoalConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markGoalConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordGoalPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordGoalConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordGoalContributionConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markGoalContributionConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordGoalContributionPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordGoalContributionConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordDebtConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markDebtConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordDebtPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordDebtConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordDebtPaymentConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        upsertConflictRow(conflict)
        check(markDebtPaymentConflict(conflict.entityId, CONFLICT_ERROR) == 1)
    }

    @Transaction
    suspend fun recordDebtPaymentPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        cursor: SyncCursorEntity,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        recordDebtPaymentConflict(syncScopeKey, conflict)
        upsertCursorRows(listOf(cursor))
    }

    @Transaction
    suspend fun recordUserMerchantVerificationPullConflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        remoteEntity: UserMerchantVerificationEntity,
        cursor: SyncCursorEntity,
        expectedLocalEntity: UserMerchantVerificationEntity,
    ) {
        val actorScope = SyncScopeKey.requireUserScope(syncScopeKey)

        // 1. Remote entity invariant doğrulaması (applyUserMerchantVerificationPull ile ortak)
        validateRemoteUserMerchantVerification(syncScopeKey, actorScope.userId, remoteEntity)

        // 2. Cursor doğrulamaları
        require(cursor.syncScopeKey == syncScopeKey) {
            "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
        }
        require(cursor.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Cursor entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${cursor.entityTypeCode}"
        }
        require(cursor.entityId == remoteEntity.id) {
            "Cursor entityId (${cursor.entityId}) ile remote entity id (${remoteEntity.id}) eşleşmelidir."
        }
        require(cursor.updatedAtEpochMillis == remoteEntity.sync.updatedAtEpochMillis) {
            "Cursor zamanı (${cursor.updatedAtEpochMillis}) remote entity updatedAtEpochMillis (${remoteEntity.sync.updatedAtEpochMillis}) ile eşleşmelidir."
        }

        // 3. Conflict temel alan ve version doğrulamaları
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict scope mismatch: conflict=${conflict.syncScopeKey}, actor=$syncScopeKey"
        }
        require(conflict.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Conflict entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${conflict.entityTypeCode}"
        }
        require(conflict.entityId == remoteEntity.id) {
            "Conflict entityId (${conflict.entityId}) remote entity id (${remoteEntity.id}) ile eşleşmelidir."
        }
        require(conflict.localPayloadJson.isNotBlank()) {
            "conflict.localPayloadJson boş olamaz: ${conflict.entityId}"
        }
        require(conflict.remotePayloadJson.isNotBlank()) {
            "conflict.remotePayloadJson boş olamaz: ${conflict.entityId}"
        }

        // 4. Yerel entity doğrulaması ve allow-list
        val local = getUserMerchantVerificationRow(conflict.entityId)
        requireNotNull(local) {
            "Yerel merchant verification bulunamadı: ${conflict.entityId}"
        }
        require(local.scopeKey == "user:${actorScope.userId}") {
            "Yerel kayıt scopeKey mismatch: ${local.scopeKey}"
        }
        val allowedLocalStatuses = setOf("PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE", "CONFLICT")
        require(local.sync.syncStatus in allowedLocalStatuses) {
            "Geçersiz yerel sync durumu; pull conflict kaydedilemez: id=${conflict.entityId}, status=${local.sync.syncStatus}"
        }

        // 5. TOCTOU: Beklenen yerel entity eşitliği (localUpdatedAtEpochMillis dahil tam eşitlik)
        require(local == expectedLocalEntity) {
            "Yerel merchant verification transaction içinde değişmiş (TOCTOU/coalesce yarışı): id=${conflict.entityId}"
        }

        // 6. Version sözleşmesi
        require(conflict.localVersion == local.sync.version) {
            "conflict.localVersion (${conflict.localVersion}) yerel version (${local.sync.version}) ile eşleşmelidir: ${conflict.entityId}"
        }
        require(conflict.remoteVersion == remoteEntity.sync.version) {
            "conflict.remoteVersion (${conflict.remoteVersion}) remoteEntity version (${remoteEntity.sync.version}) ile eşleşmelidir: ${conflict.entityId}"
        }
        val localBaseVersion = local.sync.baseVersion ?: 0L
        require(conflict.remoteVersion > localBaseVersion) {
            "conflict.remoteVersion (${conflict.remoteVersion}) yerel baseVersion ($localBaseVersion) değerinden büyük olmalıdır: ${conflict.entityId}"
        }

        // 7. Conflict snapshot'larının parse ve semantik doğrulaması
        val decodedLocalSnapshot = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(conflict.localPayloadJson)
        UserMerchantVerificationPayloadCodec.validateLocalSnapshotMatchesEntity(decodedLocalSnapshot, local)

        val decodedRemoteSnapshot = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(conflict.remotePayloadJson)
        UserMerchantVerificationPayloadCodec.validateRemoteSnapshotMatchesEntity(decodedRemoteSnapshot, remoteEntity)

        // 8. Canlı outbox operasyonu doğrulaması (ortak helper)
        validateLiveUserMerchantVerificationOutbox(syncScopeKey, local, conflict.operationId)

        // 9. Atomik yazma
        upsertConflictRow(conflict)
        val updated = markUserMerchantVerificationConflict(local.scopeKey, conflict.entityId, CONFLICT_ERROR)
        check(updated == 1) {
            "Merchant verification conflict durumu güncellenemedi: ${conflict.entityId}"
        }
        upsertCursorRows(listOf(cursor))
    }

    /**
     * Kategoriler işlemlerden, recurring kurallarından ve aboneliklerden önce yazılır; böylece
     * category_id foreign key'i her zaman geçerli kalır.
     * Goals ve Debts child'larından önce yazılır.
     */
    @Transaction
    suspend fun applyInitialSnapshot(
        syncScopeKey: String,
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
        merchantVerificationCommands: List<InitialMerchantVerificationCommand> = emptyList(),
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        for (cursor in cursors) {
            require(cursor.syncScopeKey == syncScopeKey) {
                "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
            }
        }
        val existingProfile = getProfileRow(profile.id)
        if (existingProfile == null || existingProfile.sync.syncStatus == "SYNCED") {
            upsertProfileRow(profile)
        }

        val safeCategories =
            categories.filter { row ->
                val existing = getCategoryRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeCategories.isNotEmpty()) upsertCategoryRows(safeCategories)

        val safeRecurring =
            recurringTransactions.filter { row ->
                val existing = getRecurringTransactionRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeRecurring.isNotEmpty()) upsertRecurringTransactionRows(safeRecurring)

        val safeSubscriptions =
            subscriptions.filter { row ->
                val existing = getSubscriptionRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeSubscriptions.isNotEmpty()) upsertSubscriptionRows(safeSubscriptions)

        val safeGoals =
            goals.filter { row ->
                val existing = getGoalRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeGoals.isNotEmpty()) upsertGoalRows(safeGoals)

        val safeContributions =
            goalContributions.filter { row ->
                val existing = getGoalContributionRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeContributions.isNotEmpty()) upsertGoalContributionRows(safeContributions)

        val safeDebts =
            debts.filter { row ->
                val existing = getDebtRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeDebts.isNotEmpty()) upsertDebtRows(safeDebts)

        val safePayments =
            debtPayments.filter { row ->
                val existing = getDebtPaymentRow(row.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safePayments.isNotEmpty()) upsertDebtPaymentRows(safePayments)

        val safeTransactions =
            transactions.mapNotNull { row ->
                val existing = getTransactionRow(row.id)
                if (existing == null || existing.sync.syncStatus == "SYNCED") {
                    row.mergePreservingLocalMerchant(existing)
                } else {
                    null
                }
            }
        if (safeTransactions.isNotEmpty()) upsertTransactionRows(safeTransactions)

        for (command in merchantVerificationCommands) {
            when (command) {
                is InitialMerchantVerificationCommand.Apply -> {
                    applyUserMerchantVerificationPull(syncScopeKey, command.remoteEntity, command.cursor)
                }
                is InitialMerchantVerificationCommand.Conflict -> {
                    recordUserMerchantVerificationPullConflict(
                        syncScopeKey = syncScopeKey,
                        conflict = command.conflict,
                        remoteEntity = command.remoteEntity,
                        cursor = command.cursor,
                        expectedLocalEntity = command.expectedLocalEntity,
                    )
                }
                is InitialMerchantVerificationCommand.Preserve -> {
                    advanceUserMerchantVerificationPullCursor(syncScopeKey, command.remoteEntity, command.cursor)
                }
            }
        }

        if (cursors.isNotEmpty()) upsertCursorRows(cursors)
    }

    /**
     * Workspace ve WorkspaceMember pull sonuçlarını tek bir Room transaction'ında atomik olarak uygular.
     * Snapshot içindeki member satırlarının workspace_id değeri, snapshot'taki veya veritabanındaki
     * geçerli bir workspace ile eşleşmelidir. Eşleşmeyen yetim member bulunursa fail-closed reddedilir
     * ve transaction rollback edilir.
     */
    @Transaction
    suspend fun applyWorkspaceSnapshot(
        syncScopeKey: String,
        workspaces: List<WorkspaceEntity>,
        members: List<WorkspaceMemberEntity>,
        cursors: List<SyncCursorEntity> = emptyList(),
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        for (cursor in cursors) {
            require(cursor.syncScopeKey == syncScopeKey) {
                "Cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
            }
        }
        applyWorkspaceSnapshotInternal(workspaces, members, cursors)
    }

    private suspend fun applyWorkspaceSnapshotInternal(
        workspaces: List<WorkspaceEntity>,
        members: List<WorkspaceMemberEntity>,
        cursors: List<SyncCursorEntity>,
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

        val safeWorkspaces =
            workspaces.filter { ws ->
                val existing = getWorkspaceRow(ws.id)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeWorkspaces.isNotEmpty()) {
            upsertWorkspaceRows(safeWorkspaces)
        }

        val safeMembers =
            members.filter { m ->
                val existing = getWorkspaceMemberRow(m.workspaceId, m.userId)
                existing == null || existing.sync.syncStatus == "SYNCED"
            }
        if (safeMembers.isNotEmpty()) {
            upsertWorkspaceMemberRows(safeMembers)
            for (member in safeMembers) {
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

        val liveWs =
            getWorkspaceRow(workspace.id)
                ?: throw WorkspaceOwnershipTransferPreconditionException("Workspace (${workspace.id}) yerelde bulunamadı.")
        val liveActor =
            getWorkspaceMemberRow(workspace.id, actorMember.userId)
                ?: throw WorkspaceOwnershipTransferPreconditionException("Actor member (${actorMember.userId}) yerelde bulunamadı.")
        val liveTarget =
            getWorkspaceMemberRow(workspace.id, targetMember.userId)
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
                    "Target beklenen=$expectedTargetMemberVersion güncel=${liveTarget.sync.version}",
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
    suspend fun applyWorkspaceIncrementalPlan(
        syncScopeKey: String,
        plan: WorkspaceIncrementalPlan,
    ) {
        SyncScopeKey.requireUserScope(syncScopeKey)
        for (item in plan.conflictItems) {
            require(item.conflict.syncScopeKey == syncScopeKey) {
                "Workspace conflict scope mismatch: conflict=${item.conflict.syncScopeKey}, actor=$syncScopeKey"
            }
        }
        for (cursor in plan.cursorsToPersist) {
            require(cursor.syncScopeKey == syncScopeKey) {
                "Workspace cursor scope mismatch: cursor=${cursor.syncScopeKey}, actor=$syncScopeKey"
            }
        }

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
            verifyWorkspacePrecondition(syncScopeKey, item.entity.id, item.precondition)
        }
        for (item in plan.conflictItems) {
            verifyWorkspacePrecondition(syncScopeKey, item.conflict.entityId, item.precondition)
        }
        for (item in plan.preserveItems) {
            verifyWorkspacePrecondition(syncScopeKey, item.workspaceId, item.precondition)
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
            val effectiveWsIds =
                (
                    plan.applyItems.map { it.entity.id } +
                        plan.conflictItems.map { it.conflict.entityId } +
                        plan.preserveItems.map { it.workspaceId }
                ).toMutableSet()

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

    private suspend fun verifyWorkspacePrecondition(
        syncScopeKey: String,
        workspaceId: String,
        precondition: WorkspacePrecondition,
    ) {
        val localWs = getWorkspaceRow(workspaceId)
        val localOp = getActiveWorkspaceTailOperation(syncScopeKey, workspaceId)

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

class WorkspaceSyncStalePlanException(
    message: String,
) : IllegalStateException(message)

class WorkspaceOwnershipTransferPreconditionException(
    message: String,
) : IllegalStateException(message)

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
    val protocolVersion: Int = 2,
    val attemptCount: Int = 0,
)

data class WorkspaceResolutionPrecondition(
    val expectedConflict: ExpectedConflictSnapshot,
    val expectedWorkspace: ExpectedWorkspaceSnapshot,
    val expectedOperations: List<ExpectedOperationSnapshot>,
)

class WorkspaceConflictStaleResolutionException(
    message: String,
) : IllegalStateException(message)

data class UserMerchantVerificationResolutionPrecondition(
    val expectedConflict: ExpectedConflictSnapshot,
    val expectedLocalEntity: UserMerchantVerificationEntity,
    val expectedOperations: List<ExpectedOperationSnapshot>,
)

class UserMerchantVerificationConflictStaleResolutionException(
    message: String,
) : IllegalStateException(message)

fun SyncConflictEntity.toSnapshot(): ExpectedConflictSnapshot =
    ExpectedConflictSnapshot(
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationId = operationId,
        remoteVersion = remoteVersion,
        localVersion = localVersion,
        detectedAtEpochMillis = detectedAtEpochMillis,
        localPayloadJson = localPayloadJson,
        remotePayloadJson = remotePayloadJson,
    )

fun WorkspaceEntity.toSnapshot(): ExpectedWorkspaceSnapshot =
    ExpectedWorkspaceSnapshot(
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

fun SyncOperationEntity.toSnapshot(): ExpectedOperationSnapshot =
    ExpectedOperationSnapshot(
        operationId = operationId,
        operationTypeCode = operationTypeCode,
        statusCode = statusCode,
        isBlocked = isBlocked,
        predecessorOperationId = predecessorOperationId,
        payloadJson = payloadJson,
        baseVersion = baseVersion,
        updatedAtEpochMillis = updatedAtEpochMillis,
        protocolVersion = protocolVersion,
        attemptCount = attemptCount,
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

sealed interface InitialMerchantVerificationCommand {
    data class Apply(
        val remoteEntity: UserMerchantVerificationEntity,
        val cursor: SyncCursorEntity,
    ) : InitialMerchantVerificationCommand

    data class Conflict(
        val conflict: SyncConflictEntity,
        val remoteEntity: UserMerchantVerificationEntity,
        val cursor: SyncCursorEntity,
        val expectedLocalEntity: UserMerchantVerificationEntity,
    ) : InitialMerchantVerificationCommand

    data class Preserve(
        val remoteEntity: UserMerchantVerificationEntity,
        val cursor: SyncCursorEntity,
    ) : InitialMerchantVerificationCommand
}
