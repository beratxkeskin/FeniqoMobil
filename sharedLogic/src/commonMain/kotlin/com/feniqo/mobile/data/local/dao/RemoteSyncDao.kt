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

    @Query("SELECT COUNT(*) FROM sync_operations WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId")
    suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int

    @Upsert
    suspend fun upsertProfileRow(entity: UserProfileEntity)

    @Upsert
    suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>)

    @Upsert
    suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)


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
        }
        if (cursors.isNotEmpty()) {
            upsertCursorRows(cursors)
        }
    }

    companion object {

        const val CONFLICT_ERROR = "Sunucudaki kayıt yerel baseVersion ile uyuşmuyor"
    }
}
