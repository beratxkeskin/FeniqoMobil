package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.data.util.WorkspaceInvitationCrypto
import com.feniqo.mobile.data.util.WorkspaceMemberEntityId
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.domain.model.SyncStatus


import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.toRemoteSyncMetadata

enum class V2EnqueueDecision {
    INSERTED,
    COALESCED,
    HARD_DELETED,
    CONVERTED_TO_DELETE,
    CONVERTED_TO_UPDATE,
}

data class V2EnqueueResult(
    val operationId: String,
    val decision: V2EnqueueDecision,
)

/** Yerel entity ile outbox kaydını aynı Room transaction içinde yazar. */
@Dao
interface LocalMutationDao {
    @Upsert suspend fun upsertProfileRow(entity: UserProfileEntity)
    @Upsert suspend fun upsertWorkspaceRow(entity: WorkspaceEntity)
    @Upsert suspend fun upsertWorkspaceMemberRow(entity: WorkspaceMemberEntity)
    @Upsert suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)
    @Upsert suspend fun upsertWorkspaceInvitationRow(entity: WorkspaceInvitationEntity)
    @Upsert suspend fun upsertCategoryRow(entity: CategoryEntity)
    @Upsert suspend fun upsertBudgetRow(entity: BudgetEntity)
    @Upsert suspend fun upsertTransactionRow(entity: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransactionRow(entity: TransactionEntity)
    @Upsert suspend fun upsertTagRows(entities: List<TagEntity>)
    @Upsert suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>)
    @Upsert suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity)
    @Upsert suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity)
    @Upsert suspend fun upsertSubscriptionRow(entity: SubscriptionEntity)
    @Upsert suspend fun upsertGoalRow(entity: GoalEntity)
    @Upsert suspend fun upsertGoalContributionRow(entity: GoalContributionEntity)
    @Upsert suspend fun upsertDebtRow(entity: DebtEntity)
    @Upsert suspend fun upsertDebtPaymentRow(entity: DebtPaymentEntity)



    @Query(
        """
        SELECT * FROM recurring_transaction_occurrences
        WHERE recurring_transaction_id = :recurringTransactionId
          AND due_date = :dueDate
        LIMIT 1
        """
    )
    suspend fun getOccurrence(
        recurringTransactionId: String,
        dueDate: String,
    ): RecurringTransactionOccurrenceEntity?

    @Query("SELECT * FROM recurring_transactions WHERE id = :id LIMIT 1")
    suspend fun getRecurringTransactionById(id: String): RecurringTransactionEntity?

    @Query(
        """
        UPDATE recurring_transactions
        SET last_generated_date = :newDueDate,
            local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :recurringTransactionId
          AND (
            (:expectedPreviousLastGeneratedDate IS NULL AND last_generated_date IS NULL) OR
            last_generated_date = :expectedPreviousLastGeneratedDate
          )
        """
    )
    suspend fun advanceRecurringLastGeneratedDate(
        recurringTransactionId: String,
        expectedPreviousLastGeneratedDate: String?,
        newDueDate: String,
        nowEpochMillis: Long,
    ): Int

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun deleteTransactionTagRows(transactionId: String): Int

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileRow(id: String): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryRow(id: String): Int

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetRow(id: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionRow(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxRow(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE operation_id = :operationId")
    suspend fun deleteOutboxRow(operationId: String): Int

    @Query("SELECT * FROM sync_operations WHERE operation_id = :operationId LIMIT 1")
    suspend fun getOutboxById(operationId: String): SyncOperationEntity?

    @Query("SELECT * FROM sync_operations WHERE predecessor_operation_id = :predecessorOperationId LIMIT 2")
    suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity>

    @Query("DELETE FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId")
    suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int

    @Query(
        """
        UPDATE profiles
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE categories
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE transactions
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE categories
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markCategorySyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE transactions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE budgets
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE budgets
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markBudgetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteRecurringTransactionRow(id: String): Int

    @Query(
        """
        UPDATE recurring_transactions
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE recurring_transactions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markRecurringTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionRow(id: String): Int

    @Query(
        """
        UPDATE subscriptions
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseSubscriptionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE subscriptions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markSubscriptionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int


    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspaceRow(id: String): Int

    @Query("DELETE FROM workspace_members WHERE workspace_id = :workspaceId")
    suspend fun deleteWorkspaceMemberRows(workspaceId: String): Int

    @Query("DELETE FROM workspace_invitations WHERE id = :id")
    suspend fun deleteWorkspaceInvitationRow(id: String): Int

    @Query("SELECT * FROM workspace_invitations WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceInvitationById(id: String): WorkspaceInvitationEntity?

    @Query("SELECT * FROM workspace_invitations WHERE token_hash = :tokenHash AND deleted_at_epoch_ms IS NULL LIMIT 1")
    suspend fun getWorkspaceInvitationByTokenHash(tokenHash: String): WorkspaceInvitationEntity?

    @Query("SELECT * FROM workspace_members WHERE workspace_id = :workspaceId AND user_id = :userId LIMIT 1")
    suspend fun getWorkspaceMember(workspaceId: String, userId: String): WorkspaceMemberEntity?

    @Query("UPDATE profiles SET active_workspace_id = NULL WHERE id = :profileId AND active_workspace_id = :workspaceId")
    suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalRow(id: String): Int

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteGoalContributionRow(id: String): Int

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtRow(id: String): Int

    @Query("DELETE FROM debt_payments WHERE id = :id")
    suspend fun deleteDebtPaymentRow(id: String): Int

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: String): GoalEntity?

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: String): DebtEntity?

    @Query("SELECT * FROM goal_contributions WHERE id = :id LIMIT 1")
    suspend fun getGoalContributionById(id: String): GoalContributionEntity?

    @Query("SELECT * FROM debt_payments WHERE id = :id LIMIT 1")
    suspend fun getDebtPaymentById(id: String): DebtPaymentEntity?

    @Query("SELECT * FROM goal_contributions WHERE goal_id = :goalId AND deleted_at_epoch_ms IS NULL")
    suspend fun getActiveGoalContributions(goalId: String): List<GoalContributionEntity>

    @Query("SELECT * FROM debt_payments WHERE debt_id = :debtId AND deleted_at_epoch_ms IS NULL")
    suspend fun getActiveDebtPayments(debtId: String): List<DebtPaymentEntity>

    @Query(
        """
        UPDATE goals
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseGoalVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE goal_contributions
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseGoalContributionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE debts
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseDebtVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE debt_payments
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseDebtPaymentVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE workspaces
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseWorkspaceVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE workspaces
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markWorkspaceSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE workspace_members
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE workspace_id = :workspaceId AND user_id = :userId
        """,
    )
    suspend fun rebaseWorkspaceMemberVersion(workspaceId: String, userId: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE workspace_invitations
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseWorkspaceInvitationVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE workspace_members
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE workspace_id = :workspaceId AND user_id = :userId AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markWorkspaceMemberSyncedIfDeleted(workspaceId: String, userId: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE goals
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markGoalSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE debts
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markDebtSyncedIfDeleted(id: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE goal_contributions
        SET deleted_at_epoch_ms = :deletedAtEpochMillis,
            local_updated_at_epoch_ms = :nowEpochMillis,
            sync_status = 'SYNCED'
        WHERE goal_id = :goalId AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun tombstoneGoalContributionsForDeletedGoal(goalId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE debt_payments
        SET deleted_at_epoch_ms = :deletedAtEpochMillis,
            local_updated_at_epoch_ms = :nowEpochMillis,
            sync_status = 'SYNCED'
        WHERE debt_id = :debtId AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun tombstoneDebtPaymentsForDeletedDebt(debtId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE (
            (entity_type_code = 'GOAL' AND entity_id = :goalId)
            OR
            (entity_type_code = 'GOAL_CONTRIBUTION' AND entity_id IN (SELECT id FROM goal_contributions WHERE goal_id = :goalId))
        )
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveGoalAggregateTailCandidates(goalId: String): List<SyncOperationEntity>

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE (
            (entity_type_code = 'DEBT' AND entity_id = :debtId)
            OR
            (entity_type_code = 'DEBT_PAYMENT' AND entity_id IN (SELECT id FROM debt_payments WHERE debt_id = :debtId))
        )
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveDebtAggregateTailCandidates(debtId: String): List<SyncOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE (
            (entity_type_code = 'GOAL' AND entity_id = :goalId AND operation_id <> :operationId)
            OR
            (entity_type_code = 'GOAL_CONTRIBUTION' AND entity_id IN (SELECT id FROM goal_contributions WHERE goal_id = :goalId) AND operation_id <> :operationId)
        )
        """,
    )
    suspend fun countPendingGoalAggregateOperations(goalId: String, operationId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE (
            (entity_type_code = 'DEBT' AND entity_id = :debtId AND operation_id <> :operationId)
            OR
            (entity_type_code = 'DEBT_PAYMENT' AND entity_id IN (SELECT id FROM debt_payments WHERE debt_id = :debtId) AND operation_id <> :operationId)
        )
        """,
    )
    suspend fun countPendingDebtAggregateOperations(debtId: String, operationId: String): Int

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE entity_type_code = :entityTypeCode
        AND entity_id = :entityId
        AND operation_id NOT IN (

              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity>

    @Query(
        """
        UPDATE sync_operations
        SET payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = 'DELETE',
            payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = 'UPDATE',
            payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET base_version = :appliedVersion,
            is_blocked = 0,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE operation_id = :operationId
          AND predecessor_operation_id = :predecessorOperationId
          AND is_blocked = 1
          AND attempt_count = 0
          AND status_code = 'PENDING'
          AND protocol_version = 2
        """,
    )
    suspend fun unblockSuccessor(
        operationId: String,
        predecessorOperationId: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun resolvePredecessorSuccessor(
        predecessorOperationId: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Boolean {
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(predecessorOperationId)
        requireNotNull(predecessor) { "Predecessor operasyon bulunamadı: $predecessorOperationId" }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Predecessor IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Predecessor attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }

        val successors = getSuccessors(predecessorOperationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $predecessorOperationId" }

        val successor = successors.firstOrNull()
        if (successor != null) {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == predecessor.entityTypeCode) { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == predecessor.entityId) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }
        }

        val deleted = deleteOutboxRow(predecessorOperationId)
        check(deleted == 1) { "Predecessor outbox silinemedi: $predecessorOperationId" }

        if (successor != null) {
            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = predecessorOperationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Upsert
    suspend fun upsertConflictRow(entity: SyncConflictEntity)

    @Query("UPDATE sync_operations SET status_code = 'CONFLICT', updated_at_epoch_ms = :nowEpochMillis WHERE operation_id = :operationId")
    suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int

    @Query("UPDATE profiles SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE categories SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE transactions SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE budgets SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE goals SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setGoalSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE goal_contributions SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setGoalContributionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE debts SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setDebtSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Query("UPDATE debt_payments SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setDebtPaymentSyncStatus(id: String, status: String, nowEpochMillis: Long): Int

    @Transaction
    suspend fun recordV2Conflict(conflict: SyncConflictEntity, nowEpochMillis: Long): Boolean {
        val predecessor = getOutboxById(conflict.operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: ${conflict.operationId}" }
        check(predecessor.protocolVersion == 2) {
            "V2 conflict kaydı yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == conflict.entityTypeCode) {
            "entityTypeCode uyuşmuyor: beklenen ${predecessor.entityTypeCode}, gelen ${conflict.entityTypeCode}"
        }
        check(predecessor.entityId == conflict.entityId) {
            "entityId uyuşmuyor: beklenen ${predecessor.entityId}, gelen ${conflict.entityId}"
        }

        upsertConflictRow(conflict)
        val updated = setOutboxStatusConflict(conflict.operationId, nowEpochMillis)
        check(updated == 1) { "Outbox durumu CONFLICT olarak güncellenemedi: ${conflict.operationId}" }

        val successors = getSuccessors(conflict.operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: ${conflict.operationId}" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val updatedCount = when (conflict.entityTypeCode) {
                "PROFILE" -> setProfileSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "CATEGORY" -> setCategorySyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "TRANSACTION" -> setTransactionSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "BUDGET" -> setBudgetSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "GOAL" -> setGoalSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "GOAL_CONTRIBUTION" -> setGoalContributionSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "DEBT" -> setDebtSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                "DEBT_PAYMENT" -> setDebtPaymentSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                else -> error("Bilinmeyen entityTypeCode: ${conflict.entityTypeCode}")
            }
            check(updatedCount == 1) {
                "Entity sync_status güncellenemedi veya entity bulunamadı: ${conflict.entityTypeCode}:${conflict.entityId}"
            }
        }
        return true
    }


    @Transaction
    suspend fun ackProfileWriteV2(operationId: String, record: ProfileDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak profil kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "PROFILE") {
            "Outbox operasyonu entityTypeCode PROFILE olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertProfileRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("PROFILE", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "PROFILE") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseProfileVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Profil sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackCategoryWriteV2(operationId: String, record: CategoryDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak kategori kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "CATEGORY") {
            "Outbox operasyonu entityTypeCode CATEGORY olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertCategoryRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis), slug = record.slug))
            deleteConflictRow("CATEGORY", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "CATEGORY") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseCategoryVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Kategori sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackTransactionWriteV2(operationId: String, record: TransactionDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "TRANSACTION") {
            "Outbox operasyonu entityTypeCode TRANSACTION olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertTransactionRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("TRANSACTION", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseTransactionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "İşlem sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackBudgetWriteV2(operationId: String, record: BudgetDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak bütçe kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "BUDGET") {
            "Outbox operasyonu entityTypeCode BUDGET olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertBudgetRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("BUDGET", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "BUDGET") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseBudgetVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Bütçe sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackRecurringTransactionWriteV2(
        operationId: String,
        record: RecurringTransactionDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak tekrarlayan işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "RECURRING_TRANSACTION") {
            "Outbox operasyonu entityTypeCode RECURRING_TRANSACTION olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertRecurringTransactionRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("RECURRING_TRANSACTION", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "RECURRING_TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseRecurringTransactionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Tekrarlayan işlem sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackSubscriptionWriteV2(
        operationId: String,
        record: SubscriptionDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak abonelik kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "SUBSCRIPTION") {
            "Outbox operasyonu entityTypeCode SUBSCRIPTION olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertSubscriptionRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("SUBSCRIPTION", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "SUBSCRIPTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseSubscriptionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Abonelik sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackGoalWriteV2(operationId: String, record: GoalDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak hedef kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "GOAL") {
            "Outbox operasyonu entityTypeCode GOAL olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingGoalAggregateOperations(record.id, operationId)

        if (successor == null && pendingAggregateCount == 0) {
            upsertGoalRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("GOAL", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseGoalVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Hedef sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow("GOAL", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked = unblockSuccessor(
                    operationId = successor.operationId,
                    predecessorOperationId = operationId,
                    appliedVersion = appliedVersion,
                    nowEpochMillis = nowEpochMillis,
                )
                check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
            }
        }
        return true
    }

    @Transaction
    suspend fun ackGoalContributionWriteV2(
        operationId: String,
        record: GoalContributionSyncRecordDto,
        nowEpochMillis: Long,
    ): Boolean {
        val contribDto = record.contribution
        val goalDto = record.goal
        val appliedContribVersion = contribDto.version
        val appliedGoalVersion = goalDto.version
        requireNotNull(appliedContribVersion) { "Uzak katkı kaydı version taşımıyor: $operationId" }
        requireNotNull(appliedGoalVersion) { "Uzak hedef kaydı version taşımıyor: $operationId" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "GOAL_CONTRIBUTION") {
            "Outbox operasyonu entityTypeCode GOAL_CONTRIBUTION olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == contribDto.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${contribDto.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingGoalAggregateOperations(goalDto.id, operationId)

        upsertGoalContributionRow(contribDto.toDomain().toEntity(contribDto.toRemoteSyncMetadata(nowEpochMillis)))
        deleteConflictRow("GOAL_CONTRIBUTION", contribDto.id)

        if (successors.isEmpty() && pendingAggregateCount == 0) {
            upsertGoalRow(goalDto.toDomain().toEntity(goalDto.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("GOAL", goalDto.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseGoalVersion(goalDto.id, appliedGoalVersion, nowEpochMillis)
            check(rebased == 1) { "Hedef sürümü rebase edilemedi: ${goalDto.id}" }

            deleteConflictRow("GOAL", goalDto.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked = unblockSuccessor(
                    operationId = successor.operationId,
                    predecessorOperationId = operationId,
                    appliedVersion = appliedGoalVersion,
                    nowEpochMillis = nowEpochMillis,
                )
                check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
            }
        }
        return true
    }

    @Transaction
    suspend fun ackDebtWriteV2(operationId: String, record: DebtDto, nowEpochMillis: Long): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak borç kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "DEBT") {
            "Outbox operasyonu entityTypeCode DEBT olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingDebtAggregateOperations(record.id, operationId)

        if (successor == null && pendingAggregateCount == 0) {
            upsertDebtRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("DEBT", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseDebtVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Borç sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow("DEBT", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked = unblockSuccessor(
                    operationId = successor.operationId,
                    predecessorOperationId = operationId,
                    appliedVersion = appliedVersion,
                    nowEpochMillis = nowEpochMillis,
                )
                check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
            }
        }
        return true
    }

    @Transaction
    suspend fun ackDebtPaymentWriteV2(
        operationId: String,
        record: DebtPaymentSyncRecordDto,
        nowEpochMillis: Long,
    ): Boolean {
        val paymentDto = record.payment
        val debtDto = record.debt
        val appliedPaymentVersion = paymentDto.version
        val appliedDebtVersion = debtDto.version
        requireNotNull(appliedPaymentVersion) { "Uzak ödeme kaydı version taşımıyor: $operationId" }
        requireNotNull(appliedDebtVersion) { "Uzak borç kaydı version taşımıyor: $operationId" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "DEBT_PAYMENT") {
            "Outbox operasyonu entityTypeCode DEBT_PAYMENT olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == paymentDto.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${paymentDto.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingDebtAggregateOperations(debtDto.id, operationId)

        upsertDebtPaymentRow(paymentDto.toDomain().toEntity(paymentDto.toRemoteSyncMetadata(nowEpochMillis)))
        deleteConflictRow("DEBT_PAYMENT", paymentDto.id)

        if (successors.isEmpty() && pendingAggregateCount == 0) {
            upsertDebtRow(debtDto.toDomain().toEntity(debtDto.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("DEBT", debtDto.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseDebtVersion(debtDto.id, appliedDebtVersion, nowEpochMillis)
            check(rebased == 1) { "Borç sürümü rebase edilemedi: ${debtDto.id}" }

            deleteConflictRow("DEBT", debtDto.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked = unblockSuccessor(
                    operationId = successor.operationId,
                    predecessorOperationId = operationId,
                    appliedVersion = appliedDebtVersion,
                    nowEpochMillis = nowEpochMillis,
                )
                check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
            }
        }
        return true
    }

    @Transaction
    suspend fun ackWorkspaceWriteV2(
        operationId: String,
        record: WorkspaceDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "WORKSPACE") {
            "Outbox operasyonu entityTypeCode WORKSPACE olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        when (predecessor.operationTypeCode) {
            "CREATE", "UPDATE" -> {
                check(record.deletedAt == null) {
                    "${predecessor.operationTypeCode} ACK için record.deletedAt null olmalıdır: ${record.deletedAt}"
                }
            }
            "DELETE" -> {
                check(record.deletedAt != null) {
                    "DELETE ACK için record.deletedAt zorunludur: id=${record.id}"
                }
            }
            else -> error("Bilinmeyen veya desteklenmeyen outbox operasyon türü: ${predecessor.operationTypeCode}")
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertWorkspaceRow(record.toEntity(nowEpochMillis))
            deleteConflictRow("WORKSPACE", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Workspace sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow("WORKSPACE", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackWorkspaceMemberWriteV2(
        operationId: String,
        record: WorkspaceMemberDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "WORKSPACE_MEMBER") {
            "Outbox operasyonu entityTypeCode WORKSPACE_MEMBER olmalıdır: ${predecessor.entityTypeCode}"
        }
        val canonicalEntityId = WorkspaceMemberEntityId.encode(record.workspaceId, record.userId)
        check(predecessor.entityId == canonicalEntityId) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile canonical id ($canonicalEntityId) eşleşmelidir."
        }

        when (predecessor.operationTypeCode) {
            "CREATE", "UPDATE" -> {
                check(record.deletedAt == null) {
                    "${predecessor.operationTypeCode} ACK için record.deletedAt null olmalıdır: ${record.deletedAt}"
                }
            }
            "DELETE" -> {
                check(record.deletedAt != null) {
                    "DELETE ACK için record.deletedAt zorunludur: id=$canonicalEntityId"
                }
            }
            else -> error("Bilinmeyen veya desteklenmeyen outbox operasyon türü: ${predecessor.operationTypeCode}")
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertWorkspaceMemberRow(record.toEntity(nowEpochMillis))
            deleteConflictRow("WORKSPACE_MEMBER", canonicalEntityId)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE_MEMBER") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == canonicalEntityId) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceMemberVersion(record.workspaceId, record.userId, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "WorkspaceMember sürümü rebase edilemedi: $canonicalEntityId" }

            deleteConflictRow("WORKSPACE_MEMBER", canonicalEntityId)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackWorkspaceInvitationWriteV2(
        operationId: String,
        record: WorkspaceInvitationDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == "WORKSPACE_INVITATION") {
            "Outbox operasyonu entityTypeCode WORKSPACE_INVITATION olmalıdır: ${predecessor.entityTypeCode}"
        }
        check(predecessor.entityId == record.id) {
            "Outbox operasyonu entityId (${predecessor.entityId}) ile uzak kayıt id (${record.id}) eşleşmelidir."
        }

        when (predecessor.operationTypeCode) {
            "CREATE", "UPDATE" -> {
                check(record.deletedAt == null) {
                    "${predecessor.operationTypeCode} ACK için record.deletedAt null olmalıdır: ${record.deletedAt}"
                }
            }
            "DELETE" -> {
                check(record.deletedAt != null) {
                    "DELETE ACK için record.deletedAt zorunludur: id=${record.id}"
                }
            }
            else -> error("Bilinmeyen veya desteklenmeyen outbox operasyon türü: ${predecessor.operationTypeCode}")
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val existing = getWorkspaceInvitationById(record.id)
            val preservedTokenHash = existing?.tokenHash
            upsertWorkspaceInvitationRow(record.toEntity(nowEpochMillis).copy(tokenHash = preservedTokenHash))
            deleteConflictRow("WORKSPACE_INVITATION", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE_INVITATION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceInvitationVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "WorkspaceInvitation sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow("WORKSPACE_INVITATION", record.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun ackMissingDeleteV2(
        operationId: String,
        entityTypeCode: String,
        entityId: String,
        nowEpochMillis: Long,
    ): Boolean {
        val predecessor = getOutboxById(operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.protocolVersion == 2) {
            "V2 ACK yalnız protocolVersion=2 için geçerlidir: ${predecessor.protocolVersion}"
        }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Outbox operasyonu IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Outbox operasyonu attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }
        check(predecessor.entityTypeCode == entityTypeCode) {
            "Outbox operasyonu entityTypeCode ($entityTypeCode) eşleşmelidir."
        }
        check(predecessor.entityId == entityId) {
            "Outbox operasyonu entityId ($entityId) eşleşmelidir."
        }
        check(predecessor.operationTypeCode == "DELETE") {
            "MissingDelete ACK yalnızca DELETE işlemleri için geçerlidir: ${predecessor.operationTypeCode}"
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor != null) {
            error("DELETE NOT_FOUND durumunda successor varken güvenli rebase sürümü mevcut değildir.")
        }

        when (entityTypeCode) {
            "CATEGORY" -> markCategorySyncedIfDeleted(entityId, nowEpochMillis)
            "TRANSACTION" -> markTransactionSyncedIfDeleted(entityId, nowEpochMillis)
            "BUDGET" -> markBudgetSyncedIfDeleted(entityId, nowEpochMillis)
            "RECURRING_TRANSACTION" -> markRecurringTransactionSyncedIfDeleted(entityId, nowEpochMillis)
            "SUBSCRIPTION" -> markSubscriptionSyncedIfDeleted(entityId, nowEpochMillis)
            "GOAL" -> markGoalSyncedIfDeleted(entityId, nowEpochMillis)
            "DEBT" -> markDebtSyncedIfDeleted(entityId, nowEpochMillis)
            "WORKSPACE" -> {
                val marked = markWorkspaceSyncedIfDeleted(entityId, nowEpochMillis)
                check(marked == 1) { "Silinmiş yerel Workspace kaydı bulunamadı veya güncellenemedi: $entityId" }
            }
            "WORKSPACE_MEMBER" -> {
                val (wsId, uId) = WorkspaceMemberEntityId.decode(entityId)
                val marked = markWorkspaceMemberSyncedIfDeleted(wsId, uId, nowEpochMillis)
                check(marked == 1) { "Silinmiş yerel WorkspaceMember kaydı bulunamadı veya güncellenemedi: $entityId" }
            }
        }
        deleteConflictRow(entityTypeCode, entityId)
        val deleted = deleteOutboxRow(operationId)
        check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        return true
    }



    @Transaction
    suspend fun ackTransactionGroupV2(
        units: List<Pair<String, TransactionDto>>,
        nowEpochMillis: Long,
    ): Boolean {
        for ((opId, dto) in units) {
            ackTransactionWriteV2(opId, dto, nowEpochMillis)
        }
        return true
    }

    @Transaction
    suspend fun recoverEquivalentCategory(
        operationId: String,
        remoteRecord: CategoryDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = remoteRecord.version
        requireNotNull(appliedVersion) { "Uzak kategori kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val op = getOutboxById(operationId)
        requireNotNull(op) { "Outbox operasyonu bulunamadı: $operationId" }
        check(op.protocolVersion == 1 || op.protocolVersion == 2) {
            "Desteklenmeyen protokol sürümü: ${op.protocolVersion}"
        }
        check(remoteRecord.deletedAt == null) {
            "Silinmiş uzak kayıt eşdeğer kabul edilemez: $operationId"
        }
        check(op.statusCode == "CONFLICT") {
            "Recovery yalnız CONFLICT durumundaki operasyonlar için geçerlidir: ${op.statusCode}"
        }
        check(op.operationTypeCode == "CREATE") {
            "Recovery yalnız CREATE operasyonları için geçerlidir: ${op.operationTypeCode}"
        }
        check(op.entityTypeCode == "CATEGORY") {
            "entityTypeCode CATEGORY olmalıdır: ${op.entityTypeCode}"
        }
        check(op.entityId == remoteRecord.id) {
            "entityId (${op.entityId}) ile uzak kayıt id (${remoteRecord.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertCategoryRow(remoteRecord.toDomain().toEntity(remoteRecord.toRemoteSyncMetadata(nowEpochMillis), slug = remoteRecord.slug))
            deleteConflictRow("CATEGORY", remoteRecord.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == op.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "CATEGORY") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == remoteRecord.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseCategoryVersion(remoteRecord.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Kategori sürümü rebase edilemedi: ${remoteRecord.id}" }

            deleteConflictRow("CATEGORY", remoteRecord.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun recoverEquivalentTransaction(
        operationId: String,
        remoteRecord: TransactionDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = remoteRecord.version
        requireNotNull(appliedVersion) { "Uzak işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val op = getOutboxById(operationId)
        requireNotNull(op) { "Outbox operasyonu bulunamadı: $operationId" }
        check(op.protocolVersion == 1 || op.protocolVersion == 2) {
            "Desteklenmeyen protokol sürümü: ${op.protocolVersion}"
        }
        check(remoteRecord.deletedAt == null) {
            "Silinmiş uzak kayıt eşdeğer kabul edilemez: $operationId"
        }
        check(op.statusCode == "CONFLICT") {
            "Recovery yalnız CONFLICT durumundaki operasyonlar için geçerlidir: ${op.statusCode}"
        }
        check(op.operationTypeCode == "CREATE") {
            "Recovery yalnız CREATE operasyonları için geçerlidir: ${op.operationTypeCode}"
        }
        check(op.entityTypeCode == "TRANSACTION") {
            "entityTypeCode TRANSACTION olmalıdır: ${op.entityTypeCode}"
        }
        check(op.entityId == remoteRecord.id) {
            "entityId (${op.entityId}) ile uzak kayıt id (${remoteRecord.id}) eşleşmelidir."
        }

        val successors = getSuccessors(operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertTransactionRow(remoteRecord.toDomain().toEntity(remoteRecord.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow("TRANSACTION", remoteRecord.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == op.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == remoteRecord.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseTransactionVersion(remoteRecord.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "İşlem sürümü rebase edilemedi: ${remoteRecord.id}" }

            deleteConflictRow("TRANSACTION", remoteRecord.id)
            val deleted = deleteOutboxRow(operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked = unblockSuccessor(
                operationId = successor.operationId,
                predecessorOperationId = operationId,
                appliedVersion = appliedVersion,
                nowEpochMillis = nowEpochMillis,
            )
            check(unblocked == 1) { "Successor unblock edilemedi: ${successor.operationId}" }
        }
        return true
    }

    @Transaction
    suspend fun recoverEquivalentTransactionGroup(
        units: List<Pair<String, TransactionDto>>,
        nowEpochMillis: Long,
    ): Boolean {
        for ((opId, dto) in units) {
            recoverEquivalentTransaction(opId, dto, nowEpochMillis)
        }
        return true
    }

    @Transaction
    suspend fun ackV2Execution(
        operationId: String,
        result: OutboxExecutionResult,
        nowEpochMillis: Long,
    ): Boolean {
        when (result) {
            is OutboxExecutionResult.V1Completed -> return deleteOutboxRow(operationId) == 1
            is OutboxExecutionResult.ProfileApplied -> ackProfileWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.CategoryApplied -> ackCategoryWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.TransactionApplied -> ackTransactionWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.BudgetApplied -> ackBudgetWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.RecurringTransactionApplied -> ackRecurringTransactionWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.SubscriptionApplied -> ackSubscriptionWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.GoalApplied -> ackGoalWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.GoalContributionApplied -> ackGoalContributionWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.DebtApplied -> ackDebtWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.DebtPaymentApplied -> ackDebtPaymentWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.WorkspaceApplied -> ackWorkspaceWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.WorkspaceMemberApplied -> ackWorkspaceMemberWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.WorkspaceInvitationApplied -> ackWorkspaceInvitationWriteV2(operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.MissingDeleteAcknowledged -> {

                val op = checkNotNull(getOutboxById(operationId)) { "Outbox işlemi bulunamadı: $operationId" }
                ackMissingDeleteV2(operationId, op.entityTypeCode, op.entityId, nowEpochMillis)
            }
            is OutboxExecutionResult.ConflictDetected -> recordV2Conflict(result.conflict, nowEpochMillis)
        }
        return true
    }


    @Transaction
    suspend fun mutateProfileV2(
        entity: UserProfileEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(type != OutboxOperationType.DELETE) { "Profil silme işlemi desteklenmemektedir." }

        val tailCandidates = getActiveTailCandidates("PROFILE", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertProfileRow(entity)
                    val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                }
            }

            upsertProfileRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "PROFILE",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertProfileRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "PROFILE",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateCategoryV2(
        entity: CategoryEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("CATEGORY", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertCategoryRow(entity)
                    val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteCategoryRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertCategoryRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertCategoryRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "CATEGORY",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertCategoryRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "CATEGORY",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateTransactionV2(
        entity: TransactionEntity,
        tags: List<TagEntity>,
        tagLinks: List<TransactionTagCrossRef>,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertTransactionRow(entity)
                    if (tags.isNotEmpty()) upsertTagRows(tags)
                    deleteTransactionTagRows(entity.id)
                    if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
                    val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteTransactionTagRows(entity.id)
                        deleteTransactionRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertTransactionRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertTransactionRow(entity)
            if (tags.isNotEmpty()) upsertTagRows(tags)
            deleteTransactionTagRows(entity.id)
            if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "TRANSACTION",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertTransactionRow(entity)
        if (tags.isNotEmpty()) upsertTagRows(tags)
        deleteTransactionTagRows(entity.id)
        if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "TRANSACTION",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateTransactionKeepingTagsV2(
        entity: TransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertTransactionRow(entity)
                    val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteTransactionTagRows(entity.id)
                        deleteTransactionRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertTransactionRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertTransactionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "TRANSACTION",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertTransactionRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "TRANSACTION",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateBudgetV2(
        entity: BudgetEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("BUDGET", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertBudgetRow(entity)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertBudgetRow(entity)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteBudgetRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertBudgetRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertBudgetRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "BUDGET",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertBudgetRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "BUDGET",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateRecurringTransactionV2(
        entity: RecurringTransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("RECURRING_TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertRecurringTransactionRow(entity)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertRecurringTransactionRow(entity)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteRecurringTransactionRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertRecurringTransactionRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertRecurringTransactionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "RECURRING_TRANSACTION",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertRecurringTransactionRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "RECURRING_TRANSACTION",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateSubscriptionV2(
        entity: SubscriptionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveTailCandidates("SUBSCRIPTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertSubscriptionRow(entity)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertSubscriptionRow(entity)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteSubscriptionRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertSubscriptionRow(entity)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertSubscriptionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "SUBSCRIPTION",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertSubscriptionRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "SUBSCRIPTION",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateGoalV2(
        entity: GoalEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveGoalAggregateTailCandidates(entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING" && tail.entityTypeCode == "GOAL") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertGoalRow(entity)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertGoalRow(entity)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        tombstoneGoalContributionsForDeletedGoal(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        deleteGoalRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertGoalRow(entity)
                        tombstoneGoalContributionsForDeletedGoal(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertGoalRow(entity)
            if (type == OutboxOperationType.DELETE) {
                tombstoneGoalContributionsForDeletedGoal(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
            }
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "GOAL",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertGoalRow(entity)
        if (type == OutboxOperationType.DELETE) {
            tombstoneGoalContributionsForDeletedGoal(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
        }
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "GOAL",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateGoalContributionV2(
        entity: GoalContributionEntity,
        updatedGoal: GoalEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(updatedGoal.currentAmountMinor >= 0) { "Hedef tutarı negatif olamaz." }

        val tailCandidates = getActiveGoalAggregateTailCandidates(entity.goalId)
        check(tailCandidates.size <= 1) { "Birden fazla aktif hedef kuyruk sonu tespit edildi: ${entity.goalId}" }
        val tail = tailCandidates.firstOrNull()

        upsertGoalContributionRow(entity)
        upsertGoalRow(updatedGoal)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)

        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "GOAL_CONTRIBUTION",
            entityId = entity.id,
            operationTypeCode = OutboxOperationType.CREATE.name,
            baseVersion = if (tail != null) null else updatedGoal.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateDebtV2(
        entity: DebtEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveDebtAggregateTailCandidates(entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif borç kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING" && tail.entityTypeCode == "DEBT") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertDebtRow(entity)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertDebtRow(entity)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        deleteDebtRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertDebtRow(entity)
                        tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertDebtRow(entity)
            if (type == OutboxOperationType.DELETE) {
                tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
            }
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "DEBT",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertDebtRow(entity)
        if (type == OutboxOperationType.DELETE) {
            tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
        }
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "DEBT",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateDebtPaymentV2(
        entity: DebtPaymentEntity,
        updatedDebt: DebtEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val tailCandidates = getActiveDebtAggregateTailCandidates(entity.debtId)
        check(tailCandidates.size <= 1) { "Birden fazla aktif borç kuyruk sonu tespit edildi: ${entity.debtId}" }
        val tail = tailCandidates.firstOrNull()

        upsertDebtPaymentRow(entity)
        upsertDebtRow(updatedDebt)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)

        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "DEBT_PAYMENT",
            entityId = entity.id,
            operationTypeCode = OutboxOperationType.CREATE.name,
            baseVersion = if (tail != null) null else updatedDebt.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }


    @Transaction
    suspend fun mutateWorkspaceV2(
        entity: WorkspaceEntity,
        members: List<WorkspaceMemberEntity>,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(payloadJson.isNotBlank()) { "Workspace payload JSON boş olamaz." }

        val sync = entity.sync
        val status = SyncStatus.valueOf(sync.syncStatus)
        require(status != SyncStatus.SYNCED) { "Yerel mutasyon outbox'a eklenmeden önce pending olmalıdır: ${entity.id}" }

        if (type == OutboxOperationType.CREATE) {
            require(sync.baseVersion == null) { "CREATE işleminde baseVersion null olmalıdır: ${entity.id}" }
            require(sync.deletedAtEpochMillis == null) { "CREATE işleminde deletedAtEpochMillis null olmalıdır: ${entity.id}" }
            require(sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                "CREATE işleminde syncStatus PENDING_CREATE olmalıdır: ${entity.id}"
            }
            require(members.isNotEmpty()) { "CREATE işleminde üye listesi boş olamaz: ${entity.id}" }
            require(members.all { it.workspaceId == entity.id }) {
                "Tüm üyelerin workspaceId değeri '${entity.id}' ile eşleşmelidir."
            }
            val memberKeys = members.map { it.workspaceId to it.userId }
            require(memberKeys.distinct().size == members.size) {
                "CREATE işleminde yinelenen üye bulundu: ${entity.id}"
            }
            val ownerMembers = members.filter { it.userId == entity.ownerId }
            require(ownerMembers.size == 1) {
                "CREATE işleminde sahip için tam olarak bir üyelik kaydı bulunmalıdır: ownerId=${entity.ownerId}"
            }
            val ownerMember = ownerMembers.first()
            require(ownerMember.roleCode.equals("OWNER", ignoreCase = true)) {
                "Çalışma alanı sahibinin üyelik rolü OWNER olmalıdır: ${ownerMember.roleCode}"
            }
        } else if (type == OutboxOperationType.DELETE) {
            require(sync.deletedAtEpochMillis != null) { "DELETE işleminde deletedAtEpochMillis zorunludur: ${entity.id}" }
            require(sync.syncStatus == SyncStatus.PENDING_DELETE.name) {
                "DELETE işleminde syncStatus PENDING_DELETE olmalıdır: ${entity.id}"
            }
        } else {
            require(sync.deletedAtEpochMillis == null) { "UPDATE işleminde deletedAtEpochMillis null olmalıdır: ${entity.id}" }
            require(sync.syncStatus == SyncStatus.PENDING_UPDATE.name || sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                "UPDATE işleminde syncStatus PENDING_UPDATE veya PENDING_CREATE olmalıdır: ${entity.id}"
            }
        }

        val tailCandidates = getActiveTailCandidates("WORKSPACE", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif workspace kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = convertPendingDeleteToUpdate(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteWorkspaceMemberRows(entity.id)
                        deleteWorkspaceRow(entity.id)
                        val deleted = deleteOutboxRow(tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertWorkspaceRow(entity)
            if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "WORKSPACE",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertWorkspaceRow(entity)
        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "WORKSPACE",
            entityId = entity.id,
            operationTypeCode = type.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateWorkspaceInvitationCreateV2(
        entity: WorkspaceInvitationEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(payloadJson.isNotBlank()) { "Workspace invitation payload JSON boş olamaz." }

        val sync = entity.sync
        val status = SyncStatus.valueOf(sync.syncStatus)
        require(status == SyncStatus.PENDING_CREATE) {
            "CREATE işleminde syncStatus PENDING_CREATE olmalıdır: ${entity.id}"
        }
        require(sync.baseVersion == null) { "CREATE işleminde baseVersion null olmalıdır: ${entity.id}" }
        require(sync.deletedAtEpochMillis == null) { "CREATE işleminde deletedAtEpochMillis null olmalıdır: ${entity.id}" }

        val tokenHash = entity.tokenHash
        requireNotNull(tokenHash) { "Workspace invitation tokenHash null olamaz: ${entity.id}" }
        require(WorkspaceInvitationCrypto.isValidTokenHash(tokenHash)) {
            "Workspace invitation tokenHash geçerli 64 karakter SHA-256 hex olmalıdır: ${entity.id}"
        }
        require(entity.maxUses > 0) { "maxUses 0'dan büyük olmalıdır: ${entity.maxUses}" }
        require(entity.expiresAtEpochMillis > entity.createdAtEpochMillis) {
            "expiresAtEpochMillis (${entity.expiresAtEpochMillis}) createdAtEpochMillis (${entity.createdAtEpochMillis}) sonrasında olmalıdır."
        }

        val tailCandidates = getActiveTailCandidates("WORKSPACE_INVITATION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif workspace invitation kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        upsertWorkspaceInvitationRow(entity)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "WORKSPACE_INVITATION",
            entityId = entity.id,
            operationTypeCode = OutboxOperationType.CREATE.name,
            baseVersion = if (tail != null) null else entity.sync.baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = tail?.operationId,
            isBlocked = tail != null,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateWorkspaceMemberRoleV2(
        entity: WorkspaceMemberEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(payloadJson.isNotBlank()) { "Workspace member payload JSON boş olamaz." }

        val sync = entity.sync
        val status = SyncStatus.valueOf(sync.syncStatus)
        require(status == SyncStatus.PENDING_UPDATE || status == SyncStatus.PENDING_CREATE) {
            "UPDATE işleminde syncStatus PENDING_UPDATE veya PENDING_CREATE olmalıdır: ${entity.workspaceId}:${entity.userId}"
        }
        require(sync.deletedAtEpochMillis == null) {
            "UPDATE işleminde deletedAtEpochMillis null olmalıdır: ${entity.workspaceId}:${entity.userId}"
        }
        val baseVersion = sync.baseVersion ?: sync.version
        require(baseVersion > 0L) {
            "UPDATE işleminde baseVersion pozitif olmalıdır: ${entity.workspaceId}:${entity.userId}"
        }

        val canonicalEntityId = WorkspaceMemberEntityId.encode(entity.workspaceId, entity.userId)
        val tailCandidates = getActiveTailCandidates("WORKSPACE_MEMBER", canonicalEntityId)
        check(tailCandidates.size <= 1) {
            "Birden fazla aktif workspace member kuyruk sonu tespit edildi: $canonicalEntityId"
        }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (tail.operationTypeCode == OutboxOperationType.UPDATE.name) {
                    upsertWorkspaceMemberRow(entity)
                    val updated = coalescePendingPayload(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                }
            }

            upsertWorkspaceMemberRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "WORKSPACE_MEMBER",
                entityId = canonicalEntityId,
                operationTypeCode = OutboxOperationType.UPDATE.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertWorkspaceMemberRow(entity)
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "WORKSPACE_MEMBER",
            entityId = canonicalEntityId,
            operationTypeCode = OutboxOperationType.UPDATE.name,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateWorkspaceMemberLeaveV2(
        entity: WorkspaceMemberEntity,
        activeProfileId: String?,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        require(payloadJson.isNotBlank()) { "Workspace member payload JSON boş olamaz." }

        val sync = entity.sync
        val status = SyncStatus.valueOf(sync.syncStatus)
        require(status == SyncStatus.PENDING_DELETE) {
            "DELETE işleminde syncStatus PENDING_DELETE olmalıdır: ${entity.workspaceId}:${entity.userId}"
        }
        requireNotNull(sync.deletedAtEpochMillis) {
            "DELETE işleminde deletedAtEpochMillis zorunludur: ${entity.workspaceId}:${entity.userId}"
        }
        val baseVersion = sync.baseVersion ?: sync.version
        require(baseVersion > 0L) {
            "DELETE işleminde baseVersion pozitif olmalıdır: ${entity.workspaceId}:${entity.userId}"
        }

        val canonicalEntityId = WorkspaceMemberEntityId.encode(entity.workspaceId, entity.userId)
        val tailCandidates = getActiveTailCandidates("WORKSPACE_MEMBER", canonicalEntityId)
        check(tailCandidates.size <= 1) {
            "Birden fazla aktif workspace member kuyruk sonu tespit edildi: $canonicalEntityId"
        }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (tail.operationTypeCode == OutboxOperationType.UPDATE.name) {
                    upsertWorkspaceMemberRow(entity)
                    if (activeProfileId != null) {
                        clearActiveWorkspaceIfMatches(activeProfileId, entity.workspaceId)
                    }
                    val updated = convertToPendingDelete(tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                }
            }

            upsertWorkspaceMemberRow(entity)
            if (activeProfileId != null) {
                clearActiveWorkspaceIfMatches(activeProfileId, entity.workspaceId)
            }
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor = SyncOperationEntity(
                operationId = newOpId,
                entityTypeCode = "WORKSPACE_MEMBER",
                entityId = canonicalEntityId,
                operationTypeCode = OutboxOperationType.DELETE.name,
                baseVersion = null,
                payloadJson = payloadJson,
                predecessorOperationId = tail.operationId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            insertOutboxRow(successor)
            return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
        }

        upsertWorkspaceMemberRow(entity)
        if (activeProfileId != null) {
            clearActiveWorkspaceIfMatches(activeProfileId, entity.workspaceId)
        }
        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op = SyncOperationEntity(
            operationId = newOpId,
            entityTypeCode = "WORKSPACE_MEMBER",
            entityId = canonicalEntityId,
            operationTypeCode = OutboxOperationType.DELETE.name,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
        insertOutboxRow(op)
        return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateWorkspaceMemberRemovalV2(
        entity: WorkspaceMemberEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        return mutateWorkspaceMemberLeaveV2(
            entity = entity,
            activeProfileId = null,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillis,
        )
    }

    @Transaction
    suspend fun upsertProfileAndEnqueue(entity: UserProfileEntity, operation: SyncOperationEntity) {

        upsertProfileRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertWorkspaceAndEnqueue(
        entity: WorkspaceEntity,
        members: List<WorkspaceMemberEntity>,
        operation: SyncOperationEntity,
    ) {
        upsertWorkspaceRow(entity)
        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertCategoryAndEnqueue(entity: CategoryEntity, operation: SyncOperationEntity) {
        upsertCategoryRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertBudgetAndEnqueue(entity: BudgetEntity, operation: SyncOperationEntity) {
        upsertBudgetRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertTransactionAndEnqueue(
        entity: TransactionEntity,
        tags: List<TagEntity>,
        tagLinks: List<TransactionTagCrossRef>,
        operation: SyncOperationEntity,
    ) {
        upsertTransactionRow(entity)
        if (tags.isNotEmpty()) upsertTagRows(tags)
        deleteTransactionTagRows(entity.id)
        if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertTransactionKeepingTagsAndEnqueue(
        entity: TransactionEntity,
        operation: SyncOperationEntity,
    ) {
        upsertTransactionRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertTransactionsAndEnqueue(
        units: List<TransactionMutationUnit>,
    ) {
        for (unit in units) {
            upsertTransactionRow(unit.entity)
            if (unit.tags.isNotEmpty()) {
                upsertTagRows(unit.tags)
            }
            deleteTransactionTagRows(unit.entity.id)
            if (unit.tagLinks.isNotEmpty()) {
                upsertTransactionTagRows(unit.tagLinks)
            }
            insertOutboxRow(unit.operation)
        }
    }

    @Transaction
    suspend fun upsertTransactionsKeepingTagsAndEnqueue(
        units: List<TransactionKeepingTagsMutationUnit>,
    ) {
        for (unit in units) {
            upsertTransactionRow(unit.entity)
            insertOutboxRow(unit.operation)
        }
    }

    @Transaction
    suspend fun mutateTransactionCreatesV2(
        inputs: List<TransactionCreateInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<String> {
        return inputs.map { input ->
            val result = mutateTransactionV2(
                entity = input.entity,
                tags = input.tags,
                tagLinks = input.tagLinks,
                type = OutboxOperationType.CREATE,
                payloadJson = input.payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = nowEpochMillis,
            )
            result.operationId
        }
    }

    @Transaction
    suspend fun mutateTransactionDeletionsV2(
        inputs: List<TransactionDeleteInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<String> {
        return inputs.map { input ->
            val result = mutateTransactionKeepingTagsV2(
                entity = input.entity,
                type = OutboxOperationType.DELETE,
                payloadJson = input.payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = nowEpochMillis,
            )
            result.operationId
        }
    }
    @Transaction
    suspend fun mutateBudgetsV2(
        inputs: List<BudgetMutationInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<V2EnqueueResult> {
        if (inputs.isEmpty()) return emptyList()

        val ids = inputs.map { it.entity.id }
        require(ids.all { it.isNotBlank() }) { "Toplu bütçe mutasyonunda boş bütçe kimliği bulunamaz." }
        require(ids.distinct().size == inputs.size) {
            "Toplu bütçe mutasyonunda yinelenen bütçe kimliği bulundu: $ids"
        }

        return inputs.map { input ->
            mutateBudgetV2(
                entity = input.entity,
                type = input.type,
                payloadJson = input.payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = nowEpochMillis,
            )
        }
    }

    @Transaction
    suspend fun generateRecurringOccurrence(
        command: GenerateRecurringOccurrenceCommand,
        nowEpochMillis: Long,
    ): GenerateRecurringOccurrenceResult {
        require(command.dueDate == command.transactionEntity.transactionDate) {
            "dueDate (${command.dueDate}) transactionEntity.transactionDate (${command.transactionEntity.transactionDate}) ile tutarlı olmalıdır."
        }
        require(command.outboxOperation.entityTypeCode == "TRANSACTION") {
            "Outbox entityTypeCode 'TRANSACTION' olmalıdır: ${command.outboxOperation.entityTypeCode}"
        }
        require(command.outboxOperation.entityId == command.transactionEntity.id) {
            "Outbox entityId (${command.outboxOperation.entityId}) transactionEntity.id (${command.transactionEntity.id}) ile eşleşmelidir."
        }
        require(command.outboxOperation.operationTypeCode == "CREATE") {
            "Outbox operationTypeCode 'CREATE' olmalıdır: ${command.outboxOperation.operationTypeCode}"
        }
        require(command.outboxOperation.protocolVersion == 2) {
            "Outbox protocolVersion 2 olmalıdır: ${command.outboxOperation.protocolVersion}"
        }
        require(!command.outboxOperation.payloadJson.isNullOrBlank()) {
            "Outbox payloadJson boş veya null olamaz."
        }
        require(command.outboxOperation.baseVersion == null) {
            "CREATE outbox işleminde baseVersion null olmalıdır: ${command.outboxOperation.baseVersion}"
        }
        require(command.outboxOperation.statusCode == "PENDING") {
            "Outbox statusCode 'PENDING' olmalıdır: ${command.outboxOperation.statusCode}"
        }
        require(command.outboxOperation.attemptCount == 0) {
            "Outbox attemptCount 0 olmalıdır: ${command.outboxOperation.attemptCount}"
        }
        require(command.outboxOperation.predecessorOperationId == null) {
            "Yeni tekrar işlemi için predecessorOperationId null olmalıdır: ${command.outboxOperation.predecessorOperationId}"
        }
        require(!command.outboxOperation.isBlocked) {
            "Yeni tekrar işlemi için isBlocked false olmalıdır."
        }
        validateOperationId(command.outboxOperation.operationId)

        // 1. Aynı occurrence key zaten varsa idempotent biçimde AlreadyGenerated dön
        val existing = getOccurrence(command.recurringTransactionId, command.dueDate)
        if (existing != null) {
            return GenerateRecurringOccurrenceResult.AlreadyGenerated(existing.transactionId)
        }

        // 2. CAS ile lastGeneratedDate'i beklenen önceki değerden dueDate'e ilerlet
        val updatedRows = advanceRecurringLastGeneratedDate(
            recurringTransactionId = command.recurringTransactionId,
            expectedPreviousLastGeneratedDate = command.expectedPreviousLastGeneratedDate,
            newDueDate = command.dueDate,
            nowEpochMillis = nowEpochMillis,
        )
        if (updatedRows == 0) {
            return GenerateRecurringOccurrenceResult.StaleRecurringState
        }

        // 3. Transaction, occurrence ve CREATE V2 outbox satırını aynı Room transaction içinde yaz
        insertTransactionRow(command.transactionEntity)
        if (command.tags.isNotEmpty()) {
            upsertTagRows(command.tags)
        }
        if (command.tagLinks.isNotEmpty()) {
            upsertTransactionTagRows(command.tagLinks)
        }

        val occurrence = RecurringTransactionOccurrenceEntity(
            recurringTransactionId = command.recurringTransactionId,
            dueDate = command.dueDate,
            transactionId = command.transactionEntity.id,
            createdAtEpochMillis = nowEpochMillis,
        )
        upsertRecurringOccurrenceRow(occurrence)
        insertOutboxRow(command.outboxOperation)

        return GenerateRecurringOccurrenceResult.Created(
            transactionId = command.transactionEntity.id,
            operationId = command.outboxOperation.operationId,
        )
    }
}

private val HEX_32_REGEX = Regex("^[0-9a-f]{32}$")

internal fun validateOperationId(operationId: String) {
    require(operationId.isNotBlank()) { "Outbox işlem kimliği boş olamaz." }
    require(HEX_32_REGEX.matches(operationId)) {
        "Geçersiz outbox işlem kimliği: $operationId. 32 karakter küçük harfli onaltılık (hex) dize bekleniyor."
    }
}

data class WorkspaceMutationInputV2(
    val entity: WorkspaceEntity,
    val members: List<WorkspaceMemberEntity> = emptyList(),
    val type: OutboxOperationType,
    val payloadJson: String,
)

data class BudgetMutationInputV2(
    val entity: BudgetEntity,
    val type: OutboxOperationType,
    val payloadJson: String,
)

data class TransactionMutationUnit(
    val entity: TransactionEntity,
    val tags: List<TagEntity>,
    val tagLinks: List<TransactionTagCrossRef>,
    val operation: SyncOperationEntity,
)

data class TransactionKeepingTagsMutationUnit(
    val entity: TransactionEntity,
    val operation: SyncOperationEntity,
)

data class TransactionCreateInputV2(
    val entity: TransactionEntity,
    val tags: List<TagEntity> = emptyList(),
    val tagLinks: List<TransactionTagCrossRef> = emptyList(),
    val payloadJson: String,
)

data class TransactionDeleteInputV2(
    val entity: TransactionEntity,
    val payloadJson: String,
)

data class GenerateRecurringOccurrenceCommand(
    val recurringTransactionId: String,
    val expectedPreviousLastGeneratedDate: String?,
    val dueDate: String,
    val transactionEntity: TransactionEntity,
    val tags: List<TagEntity> = emptyList(),
    val tagLinks: List<TransactionTagCrossRef> = emptyList(),
    val outboxOperation: SyncOperationEntity,
)

sealed interface GenerateRecurringOccurrenceResult {
    data class Created(
        val transactionId: String,
        val operationId: String,
    ) : GenerateRecurringOccurrenceResult

    data class AlreadyGenerated(
        val existingTransactionId: String,
    ) : GenerateRecurringOccurrenceResult

    object StaleRecurringState : GenerateRecurringOccurrenceResult
}
