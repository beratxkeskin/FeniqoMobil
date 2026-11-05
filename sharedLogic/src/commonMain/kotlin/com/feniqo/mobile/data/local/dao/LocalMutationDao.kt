package com.feniqo.mobile.data.local.dao

import com.feniqo.mobile.domain.usecase.TransactionMerchantVerificationAliasResolver
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentEntity
import com.feniqo.mobile.data.local.entity.SubscriptionPriceHistoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.entity.isDefinitiveRejection
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.mergePreservingLocalMerchant
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.dto.AssetDto
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.sync.toRemoteSyncMetadata
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.data.util.WorkspaceInvitationCrypto
import com.feniqo.mobile.data.util.WorkspaceMemberEntityId
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionMerchantEligibility
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.usecase.CategoryMerchantCandidateResolver
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog

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

data class TransactionCreationWithMerchantResult(
    val transactionOperationId: String,
    val verificationOperationId: String?,
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

    @Upsert suspend fun upsertAssetRow(entity: AssetEntity)

    @Upsert suspend fun upsertTransactionRow(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactionRow(entity: TransactionEntity)

    @Upsert suspend fun upsertTagRows(entities: List<TagEntity>)

    @Upsert suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>)

    @Upsert suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity)

    @Upsert suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity)

    @Upsert suspend fun upsertSubscriptionRow(entity: SubscriptionEntity)

    @Upsert suspend fun upsertSubscriptionPriceHistoryRow(entity: SubscriptionPriceHistoryEntity)

    @Upsert suspend fun upsertSubscriptionPaymentRow(entity: SubscriptionPaymentEntity)

    @Upsert suspend fun upsertGoalRow(entity: GoalEntity)

    @Upsert suspend fun upsertGoalContributionRow(entity: GoalContributionEntity)

    @Upsert suspend fun upsertDebtRow(entity: DebtEntity)

    @Upsert suspend fun upsertDebtPaymentRow(entity: DebtPaymentEntity)

    @Upsert suspend fun upsertUserMerchantVerificationRow(entity: UserMerchantVerificationEntity)

    @Query("SELECT * FROM user_merchant_verifications WHERE id = :id LIMIT 1")
    suspend fun getUserMerchantVerificationRow(id: String): UserMerchantVerificationEntity?

    @Query("DELETE FROM user_merchant_verifications WHERE id = :id")
    suspend fun deleteUserMerchantVerificationRow(id: String): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET version = :appliedVersion,
            base_version = :appliedVersion,
            sync_status = 'PENDING_UPDATE',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseUserMerchantVerificationVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markUserMerchantVerificationSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET sync_status = :status,
            local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setUserMerchantVerificationSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAssetRow(id: String): Int

    @Query(
        """
        UPDATE assets SET version = :appliedVersion, base_version = :appliedVersion,
            local_updated_at_epoch_ms = :nowEpochMillis, last_sync_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun rebaseAssetVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE assets
        SET sync_status = 'SYNCED', local_updated_at_epoch_ms = :nowEpochMillis, last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markAssetSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
        """,
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

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryRow(id: String): CategoryEntity?

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetRow(id: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionRow(id: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionRow(id: String): TransactionEntity?

    @Query(
        """
        UPDATE transactions
        SET merchant_id = :merchantId,
            merchant_match_source_code = :merchantMatchSourceCode,
            merchant_confidence = :merchantConfidence,
            merchant_catalog_version = :merchantCatalogVersion
        WHERE id = :transactionId
        """,
    )
    suspend fun updateTransactionMerchantAttribution(
        transactionId: String,
        merchantId: String?,
        merchantMatchSourceCode: String?,
        merchantConfidence: Int?,
        merchantCatalogVersion: Int?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxRow(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId")
    suspend fun deleteOutboxRow(
        syncScopeKey: String,
        operationId: String,
    ): Int

    @Query("SELECT * FROM sync_operations WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId LIMIT 1")
    suspend fun getOutboxById(
        syncScopeKey: String,
        operationId: String,
    ): SyncOperationEntity?

    @Query(
        "SELECT * FROM sync_operations WHERE sync_scope_key = :syncScopeKey AND predecessor_operation_id = :predecessorOperationId LIMIT 2",
    )
    suspend fun getSuccessors(
        syncScopeKey: String,
        predecessorOperationId: String,
    ): List<SyncOperationEntity>

    @Query(
        "DELETE FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteConflictRow(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): Int

    @Query(
        """
        SELECT * FROM sync_conflicts
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = :entityTypeCode
          AND entity_id = :entityId
        LIMIT 1
        """,
    )
    suspend fun getConflictRow(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): SyncConflictEntity?

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
    suspend fun rebaseProfileVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseCategoryVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseTransactionVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE categories
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markCategorySyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE transactions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markTransactionSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseBudgetVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE budgets
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markBudgetSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseRecurringTransactionVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE recurring_transactions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markRecurringTransactionSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseSubscriptionVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE subscriptions
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markSubscriptionSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun getWorkspaceMember(
        workspaceId: String,
        userId: String,
    ): WorkspaceMemberEntity?

    @Query("UPDATE profiles SET active_workspace_id = NULL WHERE id = :profileId AND active_workspace_id = :workspaceId")
    suspend fun clearActiveWorkspaceIfMatches(
        profileId: String,
        workspaceId: String,
    ): Int

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
    suspend fun rebaseGoalVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseGoalContributionVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseDebtVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseDebtPaymentVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseWorkspaceVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE workspaces
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markWorkspaceSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseWorkspaceMemberVersion(
        workspaceId: String,
        userId: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

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
    suspend fun rebaseWorkspaceInvitationVersion(
        id: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE workspace_members
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE workspace_id = :workspaceId AND user_id = :userId AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markWorkspaceMemberSyncedIfDeleted(
        workspaceId: String,
        userId: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE goals
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markGoalSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE debts
        SET sync_status = 'SYNCED',
            local_updated_at_epoch_ms = :nowEpochMillis,
            last_sync_error = NULL
        WHERE id = :id AND deleted_at_epoch_ms IS NOT NULL
        """,
    )
    suspend fun markDebtSyncedIfDeleted(
        id: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE goal_contributions
        SET deleted_at_epoch_ms = :deletedAtEpochMillis,
            local_updated_at_epoch_ms = :nowEpochMillis,
            sync_status = 'SYNCED'
        WHERE goal_id = :goalId AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun tombstoneGoalContributionsForDeletedGoal(
        goalId: String,
        deletedAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE debt_payments
        SET deleted_at_epoch_ms = :deletedAtEpochMillis,
            local_updated_at_epoch_ms = :nowEpochMillis,
            sync_status = 'SYNCED'
        WHERE debt_id = :debtId AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun tombstoneDebtPaymentsForDeletedDebt(
        debtId: String,
        deletedAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND (
            (entity_type_code = 'GOAL' AND entity_id = :goalId)
            OR
            (entity_type_code = 'GOAL_CONTRIBUTION' AND entity_id IN (SELECT id FROM goal_contributions WHERE goal_id = :goalId))
        )
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE sync_scope_key = :syncScopeKey AND predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveGoalAggregateTailCandidates(
        syncScopeKey: String,
        goalId: String,
    ): List<SyncOperationEntity>

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND (
            (entity_type_code = 'DEBT' AND entity_id = :debtId)
            OR
            (entity_type_code = 'DEBT_PAYMENT' AND entity_id IN (SELECT id FROM debt_payments WHERE debt_id = :debtId))
        )
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE sync_scope_key = :syncScopeKey AND predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveDebtAggregateTailCandidates(
        syncScopeKey: String,
        debtId: String,
    ): List<SyncOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND (
            (entity_type_code = 'GOAL' AND entity_id = :goalId AND operation_id <> :operationId)
            OR
            (entity_type_code = 'GOAL_CONTRIBUTION' AND entity_id IN (SELECT id FROM goal_contributions WHERE goal_id = :goalId) AND operation_id <> :operationId)
        )
        """,
    )
    suspend fun countPendingGoalAggregateOperations(
        syncScopeKey: String,
        goalId: String,
        operationId: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND (
            (entity_type_code = 'DEBT' AND entity_id = :debtId AND operation_id <> :operationId)
            OR
            (entity_type_code = 'DEBT_PAYMENT' AND entity_id IN (SELECT id FROM debt_payments WHERE debt_id = :debtId) AND operation_id <> :operationId)
        )
        """,
    )
    suspend fun countPendingDebtAggregateOperations(
        syncScopeKey: String,
        debtId: String,
        operationId: String,
    ): Int

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE sync_scope_key = :syncScopeKey
          AND entity_type_code = :entityTypeCode
          AND entity_id = :entityId
          AND operation_id NOT IN (
              SELECT predecessor_operation_id
              FROM sync_operations
              WHERE sync_scope_key = :syncScopeKey AND predecessor_operation_id IS NOT NULL
          )
        LIMIT 2
        """,
    )
    suspend fun getActiveTailCandidates(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
    ): List<SyncOperationEntity>

    @Query(
        """
        UPDATE sync_operations
        SET payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE sync_scope_key = :syncScopeKey
          AND operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun coalescePendingPayload(
        syncScopeKey: String,
        operationId: String,
        payloadJson: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = 'DELETE',
            payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE sync_scope_key = :syncScopeKey
          AND operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun convertToPendingDelete(
        syncScopeKey: String,
        operationId: String,
        payloadJson: String?,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = 'UPDATE',
            payload_json = :payloadJson,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE sync_scope_key = :syncScopeKey
          AND operation_id = :operationId
          AND protocol_version = 2
          AND attempt_count = 0
          AND status_code = 'PENDING'
        """,
    )
    suspend fun convertPendingDeleteToUpdate(
        syncScopeKey: String,
        operationId: String,
        payloadJson: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET base_version = :appliedVersion,
            is_blocked = 0,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE sync_scope_key = :syncScopeKey
          AND operation_id = :operationId
          AND predecessor_operation_id = :predecessorOperationId
          AND is_blocked = 1
          AND attempt_count = 0
          AND status_code = 'PENDING'
          AND protocol_version = 2
        """,
    )
    suspend fun unblockSuccessor(
        syncScopeKey: String,
        operationId: String,
        predecessorOperationId: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET operation_type_code = 'CREATE',
            base_version = NULL,
            predecessor_operation_id = NULL,
            is_blocked = 0,
            updated_at_epoch_ms = :nowEpochMillis
        WHERE sync_scope_key = :syncScopeKey
          AND operation_id = :operationId
          AND predecessor_operation_id = :predecessorOperationId
          AND entity_type_code = :entityTypeCode
          AND entity_id = :entityId
          AND operation_type_code = 'UPDATE'
          AND is_blocked = 1
          AND attempt_count = 0
          AND status_code = 'PENDING'
          AND protocol_version = 2
        """,
    )
    suspend fun convertSuccessorToPendingCreate(
        syncScopeKey: String,
        operationId: String,
        predecessorOperationId: String,
        entityTypeCode: String,
        entityId: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE user_merchant_verifications
        SET sync_status = 'PENDING_CREATE',
            version = 0,
            base_version = NULL,
            deleted_at_epoch_ms = NULL,
            local_updated_at_epoch_ms = :nowEpochMillis
        WHERE id = :id
          AND scope_key = :scopeKey
        """,
    )
    suspend fun resetUserMerchantVerificationToPendingCreate(
        id: String,
        scopeKey: String,
        nowEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun resolvePredecessorSuccessor(
        syncScopeKey: String,
        predecessorOperationId: String,
        appliedVersion: Long,
        nowEpochMillis: Long,
    ): Boolean {
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, predecessorOperationId)
        requireNotNull(predecessor) { "Predecessor operasyon bulunamadı: $predecessorOperationId" }
        check(predecessor.statusCode == "IN_FLIGHT") {
            "Predecessor IN_FLIGHT durumunda olmalıdır: ${predecessor.statusCode}"
        }
        check(predecessor.attemptCount > 0) {
            "Predecessor attempt_count > 0 olmalıdır: ${predecessor.attemptCount}"
        }

        val successors = getSuccessors(syncScopeKey, predecessorOperationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $predecessorOperationId" }

        val successor = successors.firstOrNull()
        if (successor != null) {
            check(successor.syncScopeKey == predecessor.syncScopeKey) { "Successor syncScopeKey predecessor ile eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == predecessor.entityTypeCode) { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == predecessor.entityId) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }
        }

        val deleted = deleteOutboxRow(syncScopeKey, predecessorOperationId)
        check(deleted == 1) { "Predecessor outbox silinemedi: $predecessorOperationId" }

        if (successor != null) {
            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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

    @Query(
        "UPDATE sync_operations SET status_code = 'CONFLICT', updated_at_epoch_ms = :nowEpochMillis WHERE sync_scope_key = :syncScopeKey AND operation_id = :operationId",
    )
    suspend fun setOutboxStatusConflict(
        syncScopeKey: String,
        operationId: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE profiles SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setProfileSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE categories SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setCategorySyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE transactions SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setTransactionSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE budgets SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setBudgetSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE goals SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setGoalSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE goal_contributions SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setGoalContributionSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE debts SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setDebtSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Query("UPDATE debt_payments SET sync_status = :status, local_updated_at_epoch_ms = :nowEpochMillis WHERE id = :id")
    suspend fun setDebtPaymentSyncStatus(
        id: String,
        status: String,
        nowEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun recordV2Conflict(
        syncScopeKey: String,
        conflict: SyncConflictEntity,
        nowEpochMillis: Long,
    ): Boolean {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(conflict.syncScopeKey == syncScopeKey) {
            "Conflict syncScopeKey (${conflict.syncScopeKey}) yetkili syncScopeKey ($syncScopeKey) ile eşleşmiyor."
        }
        val predecessor = getOutboxById(syncScopeKey, conflict.operationId)
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
        val updated = setOutboxStatusConflict(syncScopeKey, conflict.operationId, nowEpochMillis)
        check(updated == 1) { "Outbox durumu CONFLICT olarak güncellenemedi: ${conflict.operationId}" }

        val successors = getSuccessors(syncScopeKey, conflict.operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: ${conflict.operationId}" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val updatedCount =
                when (conflict.entityTypeCode) {
                    "PROFILE" -> setProfileSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "CATEGORY" -> setCategorySyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "TRANSACTION" -> setTransactionSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "BUDGET" -> setBudgetSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "GOAL" -> setGoalSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "GOAL_CONTRIBUTION" -> setGoalContributionSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "DEBT" -> setDebtSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "DEBT_PAYMENT" -> setDebtPaymentSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    "USER_MERCHANT_VERIFICATION" -> setUserMerchantVerificationSyncStatus(conflict.entityId, "CONFLICT", nowEpochMillis)
                    else -> error("Bilinmeyen entityTypeCode: ${conflict.entityTypeCode}")
                }
            check(updatedCount == 1) {
                "Entity sync_status güncellenemedi veya entity bulunamadı: ${conflict.entityTypeCode}:${conflict.entityId}"
            }
        }
        return true
    }

    @Transaction
    suspend fun ackProfileWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: ProfileDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak profil kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertProfileRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "PROFILE", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "PROFILE") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseProfileVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Profil sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun ackCategoryWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: CategoryDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak kategori kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertCategoryRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis), slug = record.slug))
            deleteConflictRow(syncScopeKey, "CATEGORY", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "CATEGORY") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseCategoryVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Kategori sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun ackTransactionWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: TransactionDto,
        nowEpochMillis: Long,
        candidateMerchant: TransactionMerchantInfo?,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val existing = getTransactionRow(record.id)
            val domainTransaction = record.toDomain().copy(merchantInfo = candidateMerchant)
            val remoteEntity = domainTransaction.toEntity(record.toRemoteSyncMetadata(nowEpochMillis))
            upsertTransactionRow(remoteEntity.mergePreservingLocalMerchant(existing))
            deleteConflictRow(syncScopeKey, "TRANSACTION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseTransactionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "İşlem sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun ackBudgetWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: BudgetDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak bütçe kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertBudgetRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "BUDGET", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "BUDGET") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseBudgetVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Bütçe sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        record: RecurringTransactionDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak tekrarlayan işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertRecurringTransactionRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "RECURRING_TRANSACTION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "RECURRING_TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseRecurringTransactionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Tekrarlayan işlem sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        record: SubscriptionDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak abonelik kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertSubscriptionRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "SUBSCRIPTION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "SUBSCRIPTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseSubscriptionVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Abonelik sürümü rebase edilemedi: ${record.id}" }

            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun ackGoalWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: GoalDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak hedef kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingGoalAggregateOperations(syncScopeKey, record.id, operationId)

        if (successor == null && pendingAggregateCount == 0) {
            upsertGoalRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "GOAL", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseGoalVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Hedef sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow(syncScopeKey, "GOAL", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked =
                    unblockSuccessor(
                        syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
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

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingGoalAggregateOperations(syncScopeKey, goalDto.id, operationId)

        upsertGoalContributionRow(contribDto.toDomain().toEntity(contribDto.toRemoteSyncMetadata(nowEpochMillis)))
        deleteConflictRow(syncScopeKey, "GOAL_CONTRIBUTION", contribDto.id)

        if (successors.isEmpty() && pendingAggregateCount == 0) {
            upsertGoalRow(goalDto.toDomain().toEntity(goalDto.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "GOAL", goalDto.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseGoalVersion(goalDto.id, appliedGoalVersion, nowEpochMillis)
            check(rebased == 1) { "Hedef sürümü rebase edilemedi: ${goalDto.id}" }

            deleteConflictRow(syncScopeKey, "GOAL", goalDto.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked =
                    unblockSuccessor(
                        syncScopeKey = syncScopeKey,
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
    suspend fun ackDebtWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: DebtDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        requireNotNull(appliedVersion) { "Uzak borç kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingDebtAggregateOperations(syncScopeKey, record.id, operationId)

        if (successor == null && pendingAggregateCount == 0) {
            upsertDebtRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "DEBT", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseDebtVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Borç sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow(syncScopeKey, "DEBT", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked =
                    unblockSuccessor(
                        syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
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

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        val pendingAggregateCount = countPendingDebtAggregateOperations(syncScopeKey, debtDto.id, operationId)

        upsertDebtPaymentRow(paymentDto.toDomain().toEntity(paymentDto.toRemoteSyncMetadata(nowEpochMillis)))
        deleteConflictRow(syncScopeKey, "DEBT_PAYMENT", paymentDto.id)

        if (successors.isEmpty() && pendingAggregateCount == 0) {
            upsertDebtRow(debtDto.toDomain().toEntity(debtDto.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "DEBT", debtDto.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            val rebased = rebaseDebtVersion(debtDto.id, appliedDebtVersion, nowEpochMillis)
            check(rebased == 1) { "Borç sürümü rebase edilemedi: ${debtDto.id}" }

            deleteConflictRow(syncScopeKey, "DEBT", debtDto.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            if (successor != null) {
                check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
                check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
                check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
                check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
                check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
                check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

                val unblocked =
                    unblockSuccessor(
                        syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        record: WorkspaceDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertWorkspaceRow(record.toEntity(nowEpochMillis))
            deleteConflictRow(syncScopeKey, "WORKSPACE", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Workspace sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow(syncScopeKey, "WORKSPACE", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        record: WorkspaceMemberDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertWorkspaceMemberRow(record.toEntity(nowEpochMillis))
            deleteConflictRow(syncScopeKey, "WORKSPACE_MEMBER", canonicalEntityId)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE_MEMBER") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == canonicalEntityId) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceMemberVersion(record.workspaceId, record.userId, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "WorkspaceMember sürümü rebase edilemedi: $canonicalEntityId" }

            deleteConflictRow(syncScopeKey, "WORKSPACE_MEMBER", canonicalEntityId)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        record: WorkspaceInvitationDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = record.version
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val existing = getWorkspaceInvitationById(record.id)
            val preservedTokenHash = existing?.tokenHash
            upsertWorkspaceInvitationRow(record.toEntity(nowEpochMillis).copy(tokenHash = preservedTokenHash))
            deleteConflictRow(syncScopeKey, "WORKSPACE_INVITATION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == predecessor.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "WORKSPACE_INVITATION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == record.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseWorkspaceInvitationVersion(record.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "WorkspaceInvitation sürümü rebase edilemedi: ${record.id}" }

            deleteConflictRow(syncScopeKey, "WORKSPACE_INVITATION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        entityTypeCode: String,
        entityId: String,
        nowEpochMillis: Long,
    ): Boolean {
        val predecessor = getOutboxById(syncScopeKey, operationId)
        requireNotNull(predecessor) { "Outbox operasyonu bulunamadı: $operationId" }
        check(predecessor.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${predecessor.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor != null) {
            if (entityTypeCode == "USER_MERCHANT_VERIFICATION") {
                check(successor.syncScopeKey == syncScopeKey) {
                    "Successor syncScopeKey ($syncScopeKey) eşleşmelidir: ${successor.syncScopeKey}"
                }
                check(successor.protocolVersion == 2) {
                    "Successor protocolVersion 2 olmalıdır: ${successor.protocolVersion}"
                }
                check(successor.predecessorOperationId == predecessor.operationId) {
                    "Successor predecessorOperationId (${predecessor.operationId}) eşleşmelidir: ${successor.predecessorOperationId}"
                }
                check(successor.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
                    "Successor entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${successor.entityTypeCode}"
                }
                check(successor.entityId == predecessor.entityId) {
                    "Successor entityId (${predecessor.entityId}) eşleşmelidir: ${successor.entityId}"
                }
                check(successor.operationTypeCode == "UPDATE") {
                    "Successor operationTypeCode UPDATE olmalıdır: ${successor.operationTypeCode}"
                }
                check(successor.statusCode == "PENDING") {
                    "Successor statusCode PENDING olmalıdır: ${successor.statusCode}"
                }
                check(successor.attemptCount == 0) {
                    "Successor attemptCount 0 olmalıdır: ${successor.attemptCount}"
                }
                check(successor.isBlocked) {
                    "Successor isBlocked true olmalıdır: ${successor.isBlocked}"
                }
                check(!successor.payloadJson.isNullOrBlank()) {
                    "Successor payloadJson boş olamaz."
                }
                val validatedSuccessorPayload =
                    try {
                        com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = successor.payloadJson!!,
                            operation = "CREATE",
                            baseVersion = null,
                        )
                    } catch (e: Exception) {
                        throw IllegalStateException("Successor canonical payload sözleşmesine uymuyor: ${e.message}", e)
                    }
                check(validatedSuccessorPayload.id == predecessor.entityId) {
                    "Successor payload id (${validatedSuccessorPayload.id}) entityId (${predecessor.entityId}) ile eşleşmelidir."
                }
                val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
                check(validatedSuccessorPayload.userId == userScope.userId) {
                    "Successor payload userId (${validatedSuccessorPayload.userId}) syncScopeKey (${userScope.userId}) ile eşleşmelidir."
                }

                val converted =
                    convertSuccessorToPendingCreate(
                        syncScopeKey = syncScopeKey,
                        operationId = successor.operationId,
                        predecessorOperationId = operationId,
                        entityTypeCode = entityTypeCode,
                        entityId = entityId,
                        nowEpochMillis = nowEpochMillis,
                    )
                check(converted == 1) { "Successor outbox kaydı CREATE'e dönüştürülemedi: ${successor.operationId}" }

                val expectedScopeKey = "user:${userScope.userId}"
                val reset =
                    resetUserMerchantVerificationToPendingCreate(
                        id = entityId,
                        scopeKey = expectedScopeKey,
                        nowEpochMillis = nowEpochMillis,
                    )
                check(reset == 1) { "UserMerchantVerification entity PENDING_CREATE durumuna rebase edilemedi: $entityId" }

                deleteConflictRow(syncScopeKey, entityTypeCode, entityId)
                val deleted = deleteOutboxRow(syncScopeKey, operationId)
                check(deleted == 1) { "Predecessor DELETE outbox kaydı silinemedi: $operationId" }
                return true
            } else {
                error("DELETE NOT_FOUND durumunda successor varken güvenli rebase sürümü mevcut değildir.")
            }
        }

        when (entityTypeCode) {
            "CATEGORY" -> markCategorySyncedIfDeleted(entityId, nowEpochMillis)
            "TRANSACTION" -> markTransactionSyncedIfDeleted(entityId, nowEpochMillis)
            "BUDGET" -> markBudgetSyncedIfDeleted(entityId, nowEpochMillis)
            "RECURRING_TRANSACTION" -> markRecurringTransactionSyncedIfDeleted(entityId, nowEpochMillis)
            "SUBSCRIPTION" -> markSubscriptionSyncedIfDeleted(entityId, nowEpochMillis)
            "ASSET" -> {
                val marked = markAssetSyncedIfDeleted(entityId, nowEpochMillis)
                check(marked == 1) { "Silinmiş yerel Asset kaydı bulunamadı veya güncellenemedi: $entityId" }
            }
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
            "USER_MERCHANT_VERIFICATION" -> {
                val marked = markUserMerchantVerificationSyncedIfDeleted(entityId, nowEpochMillis)
                check(marked == 1) { "Silinmiş yerel UserMerchantVerification kaydı bulunamadı veya güncellenemedi: $entityId" }
            }
        }
        deleteConflictRow(syncScopeKey, entityTypeCode, entityId)
        val deleted = deleteOutboxRow(syncScopeKey, operationId)
        check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        return true
    }

    @Transaction
    suspend fun ackTransactionGroupV2(
        syncScopeKey: String,
        units: List<Triple<String, TransactionDto, TransactionMerchantInfo?>>,
        nowEpochMillis: Long,
    ): Boolean {
        for ((opId, dto, candidate) in units) {
            val ok = ackTransactionWriteV2(syncScopeKey, opId, dto, nowEpochMillis, candidate)
            if (!ok) return false
        }
        return true
    }

    @Transaction
    suspend fun recoverEquivalentCategory(
        syncScopeKey: String,
        operationId: String,
        remoteRecord: CategoryDto,
        nowEpochMillis: Long,
    ): Boolean {
        val appliedVersion = remoteRecord.version
        requireNotNull(appliedVersion) { "Uzak kategori kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val op = getOutboxById(syncScopeKey, operationId)
        requireNotNull(op) { "Outbox operasyonu bulunamadı: $operationId" }
        check(op.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${op.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            upsertCategoryRow(remoteRecord.toDomain().toEntity(remoteRecord.toRemoteSyncMetadata(nowEpochMillis), slug = remoteRecord.slug))
            deleteConflictRow(syncScopeKey, "CATEGORY", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == op.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "CATEGORY") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == remoteRecord.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseCategoryVersion(remoteRecord.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Kategori sürümü rebase edilemedi: ${remoteRecord.id}" }

            deleteConflictRow(syncScopeKey, "CATEGORY", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        operationId: String,
        remoteRecord: TransactionDto,
        nowEpochMillis: Long,
        candidateMerchant: TransactionMerchantInfo?,
    ): Boolean {
        val appliedVersion = remoteRecord.version
        requireNotNull(appliedVersion) { "Uzak işlem kaydı version taşımıyor: $operationId" }
        require(appliedVersion >= 1) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        val op = getOutboxById(syncScopeKey, operationId)
        requireNotNull(op) { "Outbox operasyonu bulunamadı: $operationId" }
        check(op.syncScopeKey == syncScopeKey) { "Outbox operasyonu syncScopeKey eşleşmelidir: ${op.syncScopeKey}" }
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

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val existing = getTransactionRow(remoteRecord.id)
            val domainTransaction = remoteRecord.toDomain().copy(merchantInfo = candidateMerchant)
            val remoteEntity = domainTransaction.toEntity(remoteRecord.toRemoteSyncMetadata(nowEpochMillis))
            upsertTransactionRow(remoteEntity.mergePreservingLocalMerchant(existing))
            deleteConflictRow(syncScopeKey, "TRANSACTION", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == op.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "TRANSACTION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == remoteRecord.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }

            val rebased = rebaseTransactionVersion(remoteRecord.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "İşlem sürümü rebase edilemedi: ${remoteRecord.id}" }

            deleteConflictRow(syncScopeKey, "TRANSACTION", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun recoverEquivalentTransactionUnits(
        syncScopeKey: String,
        units: List<Triple<String, TransactionDto, TransactionMerchantInfo?>>,
        nowEpochMillis: Long,
    ): Boolean {
        for ((opId, dto, candidate) in units) {
            val ok = recoverEquivalentTransaction(syncScopeKey, opId, dto, nowEpochMillis, candidate)
            if (!ok) return false
        }
        return true
    }

    @Transaction
    suspend fun recoverEquivalentUserMerchantVerification(
        syncScopeKey: String,
        operationId: String,
        remoteRecord: UserMerchantVerificationDto,
        nowEpochMillis: Long,
        expectedLocalEntity: UserMerchantVerificationEntity,
    ): Boolean {
        val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
        check(userScope.userId == remoteRecord.userId) {
            "Actor scope (${userScope.userId}) ile remote userId (${remoteRecord.userId}) eşleşmelidir."
        }
        check(remoteRecord.workspaceId == null) {
            "Merchant verification workspaceId null olmalıdır."
        }
        check(remoteRecord.scopeCode == "PERSONAL") {
            "Merchant verification scopeCode PERSONAL olmalıdır: ${remoteRecord.scopeCode}"
        }

        val appliedVersion = remoteRecord.version
        requireNotNull(appliedVersion) { "Uzak merchant verification version taşımıyor: $operationId" }
        require(appliedVersion >= 1L) { "appliedVersion en az 1 olmalıdır: $appliedVersion" }

        check(remoteRecord.deletedAt == null) {
            "Silinmiş uzak kayıt eşdeğer kabul edilemez: $operationId"
        }

        val op =
            requireNotNull(getOutboxById(syncScopeKey, operationId)) {
                "Outbox operasyonu bulunamadı: $operationId"
            }
        check(op.syncScopeKey == syncScopeKey) { "Outbox syncScopeKey eşleşmelidir: ${op.syncScopeKey}" }
        check(op.protocolVersion == 2) { "ProtocolVersion 2 olmalıdır: ${op.protocolVersion}" }
        check(op.statusCode == "CONFLICT") {
            "Recovery yalnız CONFLICT durumundaki operasyonlar için geçerlidir: ${op.statusCode}"
        }
        check(op.operationTypeCode == "CREATE") {
            "Recovery yalnız CREATE operasyonları için geçerlidir: ${op.operationTypeCode}"
        }
        check(op.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${op.entityTypeCode}"
        }
        check(op.entityId == remoteRecord.id) {
            "entityId (${op.entityId}) ile uzak kayıt id (${remoteRecord.id}) eşleşmelidir."
        }
        check(op.baseVersion == null) {
            "CREATE operasyonu için baseVersion null olmalıdır: ${op.baseVersion}"
        }
        check(op.predecessorOperationId == null) {
            "Predecessor CREATE kök olmalıdır: predecessorOperationId=${op.predecessorOperationId}"
        }
        check(!op.isBlocked) {
            "Predecessor CREATE unblocked olmalıdır: $operationId"
        }

        val conflict =
            requireNotNull(getConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteRecord.id)) {
                "SyncConflictEntity bulunamadı: $operationId"
            }
        check(conflict.syncScopeKey == syncScopeKey) { "Conflict syncScopeKey eşleşmelidir: ${conflict.syncScopeKey}" }
        check(conflict.operationId == operationId) { "Conflict operationId eşleşmelidir: ${conflict.operationId}" }
        check(
            conflict.entityTypeCode == "USER_MERCHANT_VERIFICATION",
        ) { "Conflict entityTypeCode eşleşmelidir: ${conflict.entityTypeCode}" }
        check(conflict.entityId == remoteRecord.id) { "Conflict entityId eşleşmelidir: ${conflict.entityId}" }

        val conflictLocalDto = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(conflict.localPayloadJson)
        val conflictRemoteDto = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(conflict.remotePayloadJson)

        // 1.a Live conflict metadata
        check(conflict.localVersion == conflictLocalDto.version) {
            "conflict.localVersion (${conflict.localVersion}) local snapshot version (${conflictLocalDto.version}) ile eşleşmelidir: $operationId"
        }
        check(conflict.remoteVersion == conflictRemoteDto.version) {
            "conflict.remoteVersion (${conflict.remoteVersion}) remote snapshot version (${conflictRemoteDto.version}) ile eşleşmelidir: $operationId"
        }
        check(conflict.remoteVersion == remoteRecord.version) {
            "conflict.remoteVersion (${conflict.remoteVersion}) remoteRecord.version (${remoteRecord.version}) ile eşleşmelidir: $operationId"
        }
        check(conflict.remoteVersion >= 1L) {
            "remoteVersion en az 1 olmalıdır: ${conflict.remoteVersion}"
        }
        check(conflict.localVersion == 0L && (conflictLocalDto.version == null || conflictLocalDto.version == 0L)) {
            "CREATE local snapshot version sözleşmesi (version=0L) geçerli olmalıdır: conflict.localVersion=${conflict.localVersion}, snapshot.version=${conflictLocalDto.version}"
        }

        // 1.b Predecessor CREATE payload bağı
        val predecessorPayload = op.payloadJson
        check(!predecessorPayload.isNullOrBlank()) {
            "Predecessor payloadJson null veya blank olamaz: $operationId"
        }
        val decodedPredecessorPayload =
            UserMerchantVerificationPayloadCodec.parseAndValidate(
                jsonString = predecessorPayload,
                operation = "CREATE",
                baseVersion = null,
            )
        check(decodedPredecessorPayload.id == conflictLocalDto.id) {
            "Predecessor payload id (${decodedPredecessorPayload.id}) ile conflictLocalDto id (${conflictLocalDto.id}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.userId == conflictLocalDto.userId) {
            "Predecessor payload userId (${decodedPredecessorPayload.userId}) ile conflictLocalDto userId (${conflictLocalDto.userId}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.workspaceId == conflictLocalDto.workspaceId) {
            "Predecessor payload workspaceId (${decodedPredecessorPayload.workspaceId}) ile conflictLocalDto workspaceId (${conflictLocalDto.workspaceId}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.normalizedAlias == conflictLocalDto.normalizedAlias) {
            "Predecessor payload normalizedAlias (${decodedPredecessorPayload.normalizedAlias}) ile conflictLocalDto normalizedAlias (${conflictLocalDto.normalizedAlias}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.decisionCode == conflictLocalDto.decisionCode) {
            "Predecessor payload decisionCode (${decodedPredecessorPayload.decisionCode}) ile conflictLocalDto decisionCode (${conflictLocalDto.decisionCode}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.merchantId == conflictLocalDto.merchantId) {
            "Predecessor payload merchantId (${decodedPredecessorPayload.merchantId}) ile conflictLocalDto merchantId (${conflictLocalDto.merchantId}) eşleşmelidir."
        }
        check(decodedPredecessorPayload.scopeCode == conflictLocalDto.scopeCode) {
            "Predecessor payload scopeCode (${decodedPredecessorPayload.scopeCode}) ile conflictLocalDto scopeCode (${conflictLocalDto.scopeCode}) eşleşmelidir."
        }

        // conflictLocalDto ile conflictRemoteDto 7 iş alanı eşdeğerliği
        check(conflictLocalDto.id == conflictRemoteDto.id) {
            "Eşdeğerlik uyuşmazlığı: id (local=${conflictLocalDto.id}, remote=${conflictRemoteDto.id})"
        }
        check(conflictLocalDto.userId == conflictRemoteDto.userId) {
            "Eşdeğerlik uyuşmazlığı: userId (local=${conflictLocalDto.userId}, remote=${conflictRemoteDto.userId})"
        }
        check(conflictLocalDto.workspaceId == conflictRemoteDto.workspaceId) {
            "Eşdeğerlik uyuşmazlığı: workspaceId (local=${conflictLocalDto.workspaceId}, remote=${conflictRemoteDto.workspaceId})"
        }
        check(conflictLocalDto.normalizedAlias == conflictRemoteDto.normalizedAlias) {
            "Eşdeğerlik uyuşmazlığı: normalizedAlias (local=${conflictLocalDto.normalizedAlias}, remote=${conflictRemoteDto.normalizedAlias})"
        }
        check(conflictLocalDto.decisionCode == conflictRemoteDto.decisionCode) {
            "Eşdeğerlik uyuşmazlığı: decisionCode (local=${conflictLocalDto.decisionCode}, remote=${conflictRemoteDto.decisionCode})"
        }
        check(conflictLocalDto.merchantId == conflictRemoteDto.merchantId) {
            "Eşdeğerlik uyuşmazlığı: merchantId (local=${conflictLocalDto.merchantId}, remote=${conflictRemoteDto.merchantId})"
        }
        check(conflictLocalDto.scopeCode == conflictRemoteDto.scopeCode) {
            "Eşdeğerlik uyuşmazlığı: scopeCode (local=${conflictLocalDto.scopeCode}, remote=${conflictRemoteDto.scopeCode})"
        }

        // Tombstone iki tarafta da reddedilmeli
        check(conflictLocalDto.deletedAt == null && conflictRemoteDto.deletedAt == null) {
            "Tombstone içeren snapshot eşdeğer kabul edilemez: $operationId"
        }
        check(conflictRemoteDto == remoteRecord) {
            "Conflict remote snapshot ile iletilen remoteRecord birebir eşleşmelidir."
        }

        val local =
            requireNotNull(getUserMerchantVerificationRow(remoteRecord.id)) {
                "Yerel merchant verification entity bulunamadı: ${remoteRecord.id}"
            }
        check(local == expectedLocalEntity) {
            "Yerel entity expectedLocalEntity ile tam eşleşmelidir (localUpdatedAtEpochMillis dahil)."
        }
        check(local.ownerId == remoteRecord.userId) {
            "Yerel entity ownerId (${local.ownerId}) ile remote userId (${remoteRecord.userId}) eşleşmelidir."
        }
        check(local.scopeKey == "user:${remoteRecord.userId}") {
            "Yerel entity scopeKey yetkili scope ile eşleşmelidir: ${local.scopeKey}"
        }

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Birden fazla successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            // 1.c Successor yoksa
            UserMerchantVerificationPayloadCodec.validateLocalSnapshotMatchesEntity(conflictLocalDto, local)

            val tailCandidates = getActiveTailCandidates(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteRecord.id)
            check(tailCandidates.size == 1 && tailCandidates[0].operationId == op.operationId) {
                "Successor yokken outbox operasyonu tek aktif tail olmalıdır: $operationId"
            }
            check(op.predecessorOperationId == null && !op.isBlocked) {
                "Successor yokken outbox operasyonu root unblocked olmalıdır: $operationId"
            }

            upsertUserMerchantVerificationRow(remoteRecord.toEntity(remoteRecord.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Outbox kaydı silinemedi: $operationId" }
        } else {
            // 1.d Successor varsa
            check(successor.operationTypeCode == "UPDATE") {
                "Successor operasyon türü kesinlikle UPDATE olmalıdır; CREATE veya DELETE desteklenmez: ${successor.operationTypeCode}"
            }
            check(op.predecessorOperationId == null && !op.isBlocked) {
                "Predecessor CREATE kök ve unblocked olmalıdır: $operationId"
            }
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(successor.protocolVersion == 2) { "Successor protocolVersion == 2 olmalıdır: ${successor.protocolVersion}" }
            check(successor.predecessorOperationId == op.operationId) { "Successor predecessor_operation_id eşleşmelidir" }
            check(successor.entityTypeCode == "USER_MERCHANT_VERIFICATION") { "Successor entityTypeCode eşleşmelidir" }
            check(successor.entityId == remoteRecord.id) { "Successor entityId eşleşmelidir" }
            check(successor.statusCode == "PENDING") { "Successor statusCode == PENDING olmalıdır: ${successor.statusCode}" }
            check(successor.attemptCount == 0) { "Successor attemptCount == 0 olmalıdır: ${successor.attemptCount}" }
            check(successor.isBlocked) { "Successor isBlocked == true olmalıdır" }
            check(successor.baseVersion == null) { "CREATE sonrası bloklanan pre-ACK successor baseVersion null olmalıdır" }

            val tailCandidates = getActiveTailCandidates(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteRecord.id)
            check(tailCandidates.size == 1 && tailCandidates[0].operationId == successor.operationId) {
                "Successor tek aktif tail olmalıdır: ${successor.operationId}"
            }

            val successorPayload = checkNotNull(successor.payloadJson) { "Successor payload_json boş olamaz: ${successor.operationId}" }
            val validatedSuccessorPayload =
                UserMerchantVerificationPayloadCodec.parseAndValidate(
                    jsonString = successorPayload,
                    operation = "CREATE",
                    baseVersion = null,
                )
            check(validatedSuccessorPayload.id == local.id) { "Successor payload id yerel id ile eşleşmelidir" }
            check(validatedSuccessorPayload.userId == local.ownerId) { "Successor payload userId yerel ownerId ile eşleşmelidir" }
            check(
                validatedSuccessorPayload.workspaceId == local.workspaceId,
            ) { "Successor payload workspaceId yerel workspaceId ile eşleşmelidir" }
            check(
                validatedSuccessorPayload.normalizedAlias == local.normalizedAlias,
            ) { "Successor payload alias yerel alias ile eşleşmelidir" }
            check(
                validatedSuccessorPayload.decisionCode == local.decisionCode,
            ) { "Successor payload decisionCode yerel karar ile eşleşmelidir" }
            check(
                validatedSuccessorPayload.merchantId == local.merchantId,
            ) { "Successor payload merchantId yerel merchantId ile eşleşmelidir" }
            check(validatedSuccessorPayload.scopeCode == "PERSONAL") { "Successor payload scopeCode PERSONAL olmalıdır" }

            val rebased = rebaseUserMerchantVerificationVersion(remoteRecord.id, appliedVersion, nowEpochMillis)
            check(rebased == 1) { "Merchant verification sürümü rebase edilemedi: ${remoteRecord.id}" }

            deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", remoteRecord.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Predecessor outbox silinemedi: $operationId" }

            val unblocked =
                unblockSuccessor(
                    syncScopeKey = syncScopeKey,
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
    suspend fun ackV2Execution(
        syncScopeKey: String,
        operationId: String,
        result: OutboxExecutionResult,
        nowEpochMillis: Long,
    ): Boolean {
        SyncScopeKey.requireUserScope(syncScopeKey)
        when (result) {
            is OutboxExecutionResult.V1Completed -> return deleteOutboxRow(syncScopeKey, operationId) == 1
            is OutboxExecutionResult.ProfileApplied -> ackProfileWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.CategoryApplied -> ackCategoryWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.TransactionApplied ->
                ackTransactionWriteV2(
                    syncScopeKey = syncScopeKey,
                    operationId = operationId,
                    record = result.record,
                    nowEpochMillis = nowEpochMillis,
                    candidateMerchant = result.candidateMerchant,
                )
            is OutboxExecutionResult.BudgetApplied -> ackBudgetWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.AssetApplied -> ackAssetWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.RecurringTransactionApplied ->
                ackRecurringTransactionWriteV2(
                    syncScopeKey,
                    operationId,
                    result.record,
                    nowEpochMillis,
                )
            is OutboxExecutionResult.SubscriptionApplied -> ackSubscriptionWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.GoalApplied -> ackGoalWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.GoalContributionApplied ->
                ackGoalContributionWriteV2(
                    syncScopeKey,
                    operationId,
                    result.record,
                    nowEpochMillis,
                )
            is OutboxExecutionResult.DebtApplied -> ackDebtWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.DebtPaymentApplied -> ackDebtPaymentWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.WorkspaceApplied -> ackWorkspaceWriteV2(syncScopeKey, operationId, result.record, nowEpochMillis)
            is OutboxExecutionResult.WorkspaceMemberApplied ->
                ackWorkspaceMemberWriteV2(
                    syncScopeKey,
                    operationId,
                    result.record,
                    nowEpochMillis,
                )
            is OutboxExecutionResult.WorkspaceInvitationApplied ->
                ackWorkspaceInvitationWriteV2(
                    syncScopeKey,
                    operationId,
                    result.record,
                    nowEpochMillis,
                )
            is OutboxExecutionResult.UserMerchantVerificationApplied ->
                ackUserMerchantVerificationWriteV2(
                    syncScopeKey,
                    operationId,
                    result.record,
                    nowEpochMillis,
                )
            is OutboxExecutionResult.MissingDeleteAcknowledged -> {
                val op = checkNotNull(getOutboxById(syncScopeKey, operationId)) { "Outbox işlemi bulunamadı: $operationId" }
                ackMissingDeleteV2(syncScopeKey, operationId, op.entityTypeCode, op.entityId, nowEpochMillis)
            }
            is OutboxExecutionResult.ConflictDetected -> {
                check(result.conflict.syncScopeKey == syncScopeKey) {
                    "Conflict syncScopeKey (${result.conflict.syncScopeKey}) ile istek syncScopeKey ($syncScopeKey) eşleşmiyor."
                }
                recordV2Conflict(syncScopeKey, result.conflict, nowEpochMillis)
            }
        }
        return true
    }

    @Transaction
    suspend fun ackAssetWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: AssetDto,
        nowEpochMillis: Long,
    ): Boolean {
        val version = requireNotNull(record.version) { "Uzak varlık sürümü yok: $operationId" }
        require(version >= 1L) { "Uzak varlık sürümü geçersiz: $version" }
        val operation = requireNotNull(getOutboxById(syncScopeKey, operationId)) { "Varlık outbox işlemi bulunamadı: $operationId" }
        check(operation.syncScopeKey == syncScopeKey) { "Varlık outbox işlemi syncScopeKey eşleşmiyor." }
        check(operation.entityTypeCode == "ASSET" && operation.entityId == record.id) { "Varlık ACK kimliği eşleşmiyor." }
        check(operation.protocolVersion == 2 && operation.statusCode == "IN_FLIGHT") { "Varlık ACK yalnız IN_FLIGHT V2 kayıt içindir." }
        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Varlık ACK için birden çok successor bulundu: $operationId" }
        val successor = successors.firstOrNull()
        if (successor == null) {
            upsertAssetRow(record.toDomain().toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "ASSET", record.id)
        } else {
            check(successor.syncScopeKey == syncScopeKey) { "Successor syncScopeKey eşleşmelidir" }
            check(rebaseAssetVersion(record.id, version, nowEpochMillis) == 1) { "Varlık sürümü rebase edilemedi: ${record.id}" }
        }
        check(deleteOutboxRow(syncScopeKey, operationId) == 1) { "Varlık outbox kaydı silinemedi: $operationId" }
        if (successor != null) {
            check(unblockSuccessor(syncScopeKey, successor.operationId, operationId, version, nowEpochMillis) == 1) {
                "Varlık successor kaydı açılamadı: ${successor.operationId}"
            }
        }
        return true
    }

    @Transaction
    suspend fun ackUserMerchantVerificationWriteV2(
        syncScopeKey: String,
        operationId: String,
        record: UserMerchantVerificationDto,
        nowEpochMillis: Long,
    ): Boolean {
        val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
        requireNotNull(record.toDomainOrNull()) {
            "Uzak merchant verification DTO geçersiz veya doğrulanamadı: ${record.id}"
        }
        require(record.userId == userScope.userId) {
            "Uzak merchant verification userId (${record.userId}) yetkili user (${userScope.userId}) ile eşleşmiyor."
        }

        val operation =
            requireNotNull(getOutboxById(syncScopeKey, operationId)) {
                "Merchant verification outbox işlemi bulunamadı: $operationId"
            }
        check(operation.syncScopeKey == syncScopeKey) { "Merchant verification outbox syncScopeKey eşleşmiyor." }
        check(operation.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
            "Merchant verification ACK entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${operation.entityTypeCode}"
        }
        check(operation.entityId == record.id) {
            "Merchant verification ACK kimliği eşleşmiyor: beklenen ${record.id}, gelen ${operation.entityId}"
        }
        check(operation.protocolVersion == 2) {
            "Merchant verification ACK yalnız protocolVersion=2 içindir: ${operation.protocolVersion}"
        }
        check(operation.statusCode == "IN_FLIGHT") {
            "Merchant verification ACK yalnız IN_FLIGHT V2 kayıt içindir: ${operation.statusCode}"
        }
        check(operation.attemptCount > 0) {
            "Merchant verification ACK attemptCount > 0 olmalıdır: ${operation.attemptCount}"
        }
        check(!operation.isBlocked) {
            "ACK operation isBlocked false olmalıdır: ${operation.operationId}"
        }
        check(operation.operationTypeCode in setOf("CREATE", "UPDATE", "DELETE")) {
            "Desteklenmeyen operationTypeCode: ${operation.operationTypeCode}"
        }
        if (operation.predecessorOperationId != null) {
            check(getOutboxById(syncScopeKey, operation.predecessorOperationId) == null) {
                "ACK operation canlı bir predecessor'a bağlı olamaz: ${operation.predecessorOperationId}"
            }
        }
        val opPayload =
            checkNotNull(operation.payloadJson?.takeIf { it.isNotBlank() }) {
                "ACK operation payloadJson boş olamaz: ${operation.operationId}"
            }

        val decodedOpPayload: UserMerchantVerificationPayloadCodec.ValidatedVerificationPayload
        when (operation.operationTypeCode) {
            "CREATE" -> {
                check(operation.baseVersion == null) {
                    "CREATE operasyonunda baseVersion null olmalıdır: ${operation.baseVersion}"
                }
                decodedOpPayload =
                    UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = opPayload,
                        operation = "CREATE",
                        baseVersion = null,
                    )
                check(record.version == 1L) {
                    "CREATE operasyonunda uzak sürüm kesinlikle 1 olmalıdır: ${record.version}"
                }
                check(record.deletedAt == null) {
                    "CREATE operasyonunda uzak deletedAt null olmalıdır: ${record.deletedAt}"
                }
            }
            "UPDATE" -> {
                check(operation.baseVersion != null && operation.baseVersion >= 1L) {
                    "UPDATE operasyonunda baseVersion >= 1 olmalıdır: ${operation.baseVersion}"
                }
                check(operation.baseVersion < Long.MAX_VALUE) {
                    "UPDATE operasyonunda baseVersion Long.MAX_VALUE olamaz."
                }
                decodedOpPayload =
                    UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = opPayload,
                        operation = "UPDATE",
                        baseVersion = operation.baseVersion,
                    )
                check(record.version == operation.baseVersion + 1L) {
                    "UPDATE operasyonunda uzak sürüm baseVersion + 1 (${operation.baseVersion + 1L}) olmalıdır: ${record.version}"
                }
                check(record.deletedAt == null) {
                    "UPDATE operasyonunda uzak deletedAt null olmalıdır: ${record.deletedAt}"
                }
            }
            "DELETE" -> {
                check(operation.baseVersion != null && operation.baseVersion >= 1L) {
                    "DELETE operasyonunda baseVersion >= 1 olmalıdır: ${operation.baseVersion}"
                }
                check(operation.baseVersion < Long.MAX_VALUE) {
                    "DELETE operasyonunda baseVersion Long.MAX_VALUE olamaz."
                }
                decodedOpPayload =
                    UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = opPayload,
                        operation = "DELETE",
                        baseVersion = operation.baseVersion,
                    )
                check(record.version == operation.baseVersion + 1L) {
                    "DELETE operasyonunda uzak sürüm baseVersion + 1 (${operation.baseVersion + 1L}) olmalıdır: ${record.version}"
                }
                check(record.deletedAt != null) {
                    "DELETE operasyonunda uzak deletedAt zorunludur: null"
                }
            }
            else -> error("Desteklenmeyen operationTypeCode: ${operation.operationTypeCode}")
        }

        check(decodedOpPayload.id == record.id) {
            "Payload id (${decodedOpPayload.id}) uzak id (${record.id}) ile eşleşmiyor."
        }
        check(decodedOpPayload.userId == record.userId) {
            "Payload userId (${decodedOpPayload.userId}) uzak userId (${record.userId}) ile eşleşmiyor."
        }
        check(decodedOpPayload.workspaceId == record.workspaceId) {
            "Payload workspaceId (${decodedOpPayload.workspaceId}) uzak workspaceId (${record.workspaceId}) ile eşleşmiyor."
        }
        check(decodedOpPayload.normalizedAlias == record.normalizedAlias) {
            "Payload alias (${decodedOpPayload.normalizedAlias}) uzak alias (${record.normalizedAlias}) ile eşleşmiyor."
        }
        check(decodedOpPayload.decisionCode == record.decisionCode) {
            "Payload decisionCode (${decodedOpPayload.decisionCode}) uzak decisionCode (${record.decisionCode}) ile eşleşmiyor."
        }
        check(decodedOpPayload.merchantId == record.merchantId) {
            "Payload merchantId (${decodedOpPayload.merchantId}) uzak merchantId (${record.merchantId}) ile eşleşmiyor."
        }
        check(decodedOpPayload.scopeCode == record.scopeCode) {
            "Payload scopeCode (${decodedOpPayload.scopeCode}) uzak scopeCode (${record.scopeCode}) ile eşleşmiyor."
        }

        val successors = getSuccessors(syncScopeKey, operationId)
        check(successors.size <= 1) { "Merchant verification ACK için birden çok successor bulundu: $operationId" }
        val successor = successors.firstOrNull()

        if (successor == null) {
            val tailCandidates = getActiveTailCandidates(syncScopeKey, "USER_MERCHANT_VERIFICATION", record.id)
            check(tailCandidates.size == 1 && tailCandidates[0].operationId == operation.operationId) {
                "Successor yokken outbox operasyonu tek aktif tail olmalıdır: $operationId"
            }
            val local =
                requireNotNull(getUserMerchantVerificationRow(record.id)) {
                    "Yerel merchant verification entity bulunamadı: ${record.id}"
                }
            check(
                local.id == record.id &&
                    local.ownerId == record.userId &&
                    local.scopeKey == "user:${record.userId}" &&
                    local.scopeCode == "PERSONAL" &&
                    local.workspaceId == null,
            ) {
                "Yerel entity kimlik ve sahiplik alanları uzak kayıt ile eşleşmiyor."
            }

            when (operation.operationTypeCode) {
                "CREATE" -> {
                    check(local.sync.syncStatus == "PENDING_CREATE" && local.sync.version == 0L && local.sync.baseVersion == null) {
                        "CREATE ACK için yerel sync durumu PENDING_CREATE ve version 0 olmalıdır: ${local.sync.syncStatus}, ${local.sync.version}"
                    }
                }
                "UPDATE" -> {
                    check(
                        local.sync.syncStatus == "PENDING_UPDATE" &&
                            local.sync.version >= 1L &&
                            local.sync.baseVersion == operation.baseVersion &&
                            local.sync.baseVersion == local.sync.version,
                    ) {
                        "UPDATE ACK için yerel sync durumu PENDING_UPDATE ve baseVersion yerel sürümle eşleşmelidir: ${local.sync.syncStatus}, base=${local.sync.baseVersion}, ver=${local.sync.version}"
                    }
                }
                "DELETE" -> {
                    check(
                        local.sync.syncStatus == "PENDING_DELETE" &&
                            local.sync.deletedAtEpochMillis != null &&
                            local.sync.version >= 1L &&
                            local.sync.baseVersion == operation.baseVersion &&
                            local.sync.baseVersion == local.sync.version,
                    ) {
                        "DELETE ACK için yerel sync durumu PENDING_DELETE ve baseVersion yerel sürümle eşleşmelidir: ${local.sync.syncStatus}, base=${local.sync.baseVersion}, ver=${local.sync.version}"
                    }
                }
            }

            check(decodedOpPayload.id == local.id) {
                "Payload id (${decodedOpPayload.id}) yerel id (${local.id}) ile eşleşmelidir."
            }
            check(decodedOpPayload.userId == local.ownerId) {
                "Payload userId (${decodedOpPayload.userId}) yerel ownerId (${local.ownerId}) ile eşleşmelidir."
            }
            check(decodedOpPayload.workspaceId == local.workspaceId) {
                "Payload workspaceId (${decodedOpPayload.workspaceId}) yerel workspaceId (${local.workspaceId}) ile eşleşmelidir."
            }
            check(decodedOpPayload.normalizedAlias == local.normalizedAlias) {
                "Payload alias (${decodedOpPayload.normalizedAlias}) yerel alias (${local.normalizedAlias}) ile eşleşmelidir."
            }
            check(decodedOpPayload.decisionCode == local.decisionCode) {
                "Payload decisionCode (${decodedOpPayload.decisionCode}) yerel karar (${local.decisionCode}) ile eşleşmelidir."
            }
            check(decodedOpPayload.merchantId == local.merchantId) {
                "Payload merchantId (${decodedOpPayload.merchantId}) yerel merchantId (${local.merchantId}) ile eşleşmelidir."
            }
            check(decodedOpPayload.scopeCode == local.scopeCode) {
                "Payload scopeCode (${decodedOpPayload.scopeCode}) yerel scopeCode (${local.scopeCode}) ile eşleşmelidir."
            }

            upsertUserMerchantVerificationRow(record.toEntity(record.toRemoteSyncMetadata(nowEpochMillis)))
            deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", record.id)
            val deleted = deleteOutboxRow(syncScopeKey, operationId)
            check(deleted == 1) { "Merchant verification outbox kaydı silinemedi: $operationId" }
        } else {
            check(successor.syncScopeKey == syncScopeKey) {
                "Successor syncScopeKey (${successor.syncScopeKey}) yetkili syncScopeKey ($syncScopeKey) ile eşleşmelidir."
            }
            check(successor.entityTypeCode == "USER_MERCHANT_VERIFICATION") {
                "Successor entityTypeCode USER_MERCHANT_VERIFICATION olmalıdır: ${successor.entityTypeCode}"
            }
            check(successor.entityId == record.id && successor.entityId == operation.entityId) {
                "Successor entityId (${successor.entityId}) ile record.id (${record.id}) eşleşmelidir."
            }
            check(successor.protocolVersion == 2) {
                "Successor protocolVersion 2 olmalıdır: ${successor.protocolVersion}"
            }
            check(successor.operationTypeCode == "UPDATE") {
                "Successor operasyon türü kesinlikle UPDATE olmalıdır; DELETE veya CREATE desteklenmez: ${successor.operationTypeCode}"
            }
            check(successor.statusCode == "PENDING") {
                "Successor statusCode PENDING olmalıdır: ${successor.statusCode}"
            }
            check(successor.attemptCount == 0) {
                "Successor attemptCount 0 olmalıdır: ${successor.attemptCount}"
            }
            check(successor.isBlocked) {
                "Successor isBlocked true olmalıdır: ${successor.operationId}"
            }
            check(successor.predecessorOperationId == operation.operationId) {
                "Successor predecessorOperationId (${successor.predecessorOperationId}) ACK operationId (${operation.operationId}) ile eşleşmelidir."
            }

            val tailCandidates = getActiveTailCandidates(syncScopeKey, "USER_MERCHANT_VERIFICATION", record.id)
            check(tailCandidates.size == 1 && tailCandidates[0].operationId == successor.operationId) {
                "Successor tek aktif tail olmalıdır: ${successor.operationId}"
            }

            val local =
                requireNotNull(getUserMerchantVerificationRow(record.id)) {
                    "Yerel merchant verification entity bulunamadı: ${record.id}"
                }
            check(
                local.id == record.id &&
                    local.ownerId == record.userId &&
                    local.scopeKey == "user:${record.userId}" &&
                    local.scopeCode == "PERSONAL" &&
                    local.workspaceId == null,
            ) {
                "Yerel entity kimlik ve sahiplik alanları doğrulanmalıdır: ${local.id}"
            }

            val successorPayload =
                checkNotNull(successor.payloadJson?.takeIf { it.isNotBlank() }) {
                    "Successor payloadJson null veya boş olamaz: ${successor.operationId}"
                }

            val decodedSuccessorPayload: UserMerchantVerificationPayloadCodec.ValidatedVerificationPayload
            when (operation.operationTypeCode) {
                "CREATE" -> {
                    check(operation.baseVersion == null) {
                        "CREATE operasyonunda baseVersion null olmalıdır: ${operation.baseVersion}"
                    }
                    check(local.sync.syncStatus == "PENDING_CREATE") {
                        "CREATE predecessor için yerel syncStatus PENDING_CREATE olmalıdır: ${local.sync.syncStatus}"
                    }
                    check(local.sync.version == 0L) {
                        "CREATE predecessor için yerel version 0 olmalıdır: ${local.sync.version}"
                    }
                    check(local.sync.baseVersion == null) {
                        "CREATE predecessor için yerel baseVersion null olmalıdır: ${local.sync.baseVersion}"
                    }
                    check(successor.operationTypeCode == "UPDATE") {
                        "CREATE predecessor zincirinde successor operationTypeCode UPDATE olmalıdır: ${successor.operationTypeCode}"
                    }
                    check(successor.baseVersion == null) {
                        "CREATE predecessor zincirinde successor baseVersion null olmalıdır: ${successor.baseVersion}"
                    }
                    decodedSuccessorPayload =
                        UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = successorPayload,
                            operation = "CREATE",
                            baseVersion = null,
                        )
                }
                "UPDATE" -> {
                    val baseB =
                        checkNotNull(operation.baseVersion) {
                            "UPDATE predecessor baseVersion null olamaz."
                        }
                    check(baseB >= 1L && baseB < Long.MAX_VALUE) {
                        "UPDATE predecessor baseVersion geçersiz: $baseB"
                    }
                    check(local.sync.syncStatus == "PENDING_UPDATE") {
                        "UPDATE predecessor için yerel syncStatus PENDING_UPDATE olmalıdır: ${local.sync.syncStatus}"
                    }
                    check(local.sync.version == baseB) {
                        "UPDATE predecessor için yerel version ($baseB) ile eşleşmelidir: ${local.sync.version}"
                    }
                    check(local.sync.baseVersion == baseB) {
                        "UPDATE predecessor için yerel baseVersion ($baseB) ile eşleşmelidir: ${local.sync.baseVersion}"
                    }
                    check(successor.operationTypeCode == "UPDATE") {
                        "UPDATE predecessor zincirinde successor operationTypeCode UPDATE olmalıdır: ${successor.operationTypeCode}"
                    }
                    check(successor.baseVersion == baseB) {
                        "UPDATE predecessor zincirinde successor baseVersion predecessor baseVersion ($baseB) ile eşleşmelidir: ${successor.baseVersion}"
                    }
                    decodedSuccessorPayload =
                        UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = successorPayload,
                            operation = "UPDATE",
                            baseVersion = baseB,
                        )
                }
                "DELETE" -> {
                    val baseB =
                        checkNotNull(operation.baseVersion) {
                            "DELETE predecessor baseVersion null olamaz."
                        }
                    check(baseB >= 1L && baseB < Long.MAX_VALUE) {
                        "DELETE predecessor baseVersion geçersiz: $baseB"
                    }
                    check(local.sync.syncStatus == "PENDING_UPDATE") {
                        "DELETE predecessor için resurrection yerel syncStatus PENDING_UPDATE olmalıdır: ${local.sync.syncStatus}"
                    }
                    check(local.sync.version == baseB) {
                        "DELETE predecessor için yerel version ($baseB) ile eşleşmelidir: ${local.sync.version}"
                    }
                    check(local.sync.baseVersion == baseB) {
                        "DELETE predecessor için yerel baseVersion ($baseB) ile eşleşmelidir: ${local.sync.baseVersion}"
                    }
                    check(local.sync.deletedAtEpochMillis == null) {
                        "DELETE predecessor için resurrection yerel deletedAtEpochMillis null olmalıdır: ${local.sync.deletedAtEpochMillis}"
                    }
                    check(successor.operationTypeCode == "UPDATE") {
                        "DELETE predecessor zincirinde resurrection successor operationTypeCode UPDATE olmalıdır: ${successor.operationTypeCode}"
                    }
                    check(successor.baseVersion == baseB) {
                        "DELETE predecessor zincirinde resurrection successor baseVersion ($baseB) ile eşleşmelidir: ${successor.baseVersion}"
                    }
                    decodedSuccessorPayload =
                        UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = successorPayload,
                            operation = "UPDATE",
                            baseVersion = baseB,
                        )
                }
                else -> error("Desteklenmeyen predecessor operationTypeCode: ${operation.operationTypeCode}")
            }

            check(decodedSuccessorPayload.id == local.id) {
                "Successor payload id (${decodedSuccessorPayload.id}) yerel id (${local.id}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.userId == local.ownerId) {
                "Successor payload userId (${decodedSuccessorPayload.userId}) yerel ownerId (${local.ownerId}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.workspaceId == local.workspaceId) {
                "Successor payload workspaceId (${decodedSuccessorPayload.workspaceId}) yerel workspaceId (${local.workspaceId}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.normalizedAlias == local.normalizedAlias) {
                "Successor payload alias (${decodedSuccessorPayload.normalizedAlias}) yerel alias (${local.normalizedAlias}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.decisionCode == local.decisionCode) {
                "Successor payload decisionCode (${decodedSuccessorPayload.decisionCode}) yerel karar (${local.decisionCode}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.merchantId == local.merchantId) {
                "Successor payload merchantId (${decodedSuccessorPayload.merchantId}) yerel merchantId (${local.merchantId}) ile eşleşmelidir."
            }
            check(decodedSuccessorPayload.scopeCode == local.scopeCode) {
                "Successor payload scopeCode (${decodedSuccessorPayload.scopeCode}) yerel scopeCode (${local.scopeCode}) ile eşleşmelidir."
            }

            check(rebaseUserMerchantVerificationVersion(record.id, record.version!!, nowEpochMillis) == 1) {
                "Merchant verification sürümü rebase edilemedi: ${record.id}"
            }
            deleteConflictRow(syncScopeKey, "USER_MERCHANT_VERIFICATION", record.id)
            check(deleteOutboxRow(syncScopeKey, operationId) == 1) {
                "Merchant verification outbox kaydı silinemedi: $operationId"
            }
            check(unblockSuccessor(syncScopeKey, successor.operationId, operationId, record.version!!, nowEpochMillis) == 1) {
                "Merchant verification successor kaydı açılamadı: ${successor.operationId}"
            }
        }
        return true
    }

    private sealed interface CanonicalMerchantVerificationTailTopology {
        data object CleanNoTail : CanonicalMerchantVerificationTailTopology

        data class EligiblePendingRootCreate(
            val op: SyncOperationEntity,
        ) : CanonicalMerchantVerificationTailTopology

        data class InFlightRootCreate(
            val op: SyncOperationEntity,
        ) : CanonicalMerchantVerificationTailTopology

        data class BlockedPreAckUpdateSuccessor(
            val op: SyncOperationEntity,
            val predecessor: SyncOperationEntity,
        ) : CanonicalMerchantVerificationTailTopology

        data class RebasedUnblockedUpdateTail(
            val op: SyncOperationEntity,
        ) : CanonicalMerchantVerificationTailTopology

        data class RebasedUnblockedDeleteTail(
            val op: SyncOperationEntity,
        ) : CanonicalMerchantVerificationTailTopology
    }

    @Transaction
    suspend fun mutateUserMerchantVerificationV2(
        syncScopeKey: String,
        entity: UserMerchantVerificationEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
        require(entity.scopeKey == "user:${userScope.userId}") {
            "Entity scopeKey (${entity.scopeKey}) yetkili kullanıcı kapsamı ile eşleşmelidir."
        }
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "USER_MERCHANT_VERIFICATION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        val topology: CanonicalMerchantVerificationTailTopology =
            if (tail == null) {
                CanonicalMerchantVerificationTailTopology.CleanNoTail
            } else {
                check(tail.protocolVersion == 2) { "Aktif tail protocolVersion 2 olmalıdır: ${tail.operationId}" }
                check(tail.syncScopeKey == syncScopeKey) { "Aktif tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
                check(
                    tail.entityTypeCode == "USER_MERCHANT_VERIFICATION",
                ) { "Aktif tail entityTypeCode eşleşmelidir: ${tail.entityTypeCode}" }
                check(tail.entityId == entity.id) { "Aktif tail entityId eşleşmelidir: ${tail.entityId}" }

                if (!tail.isBlocked) {
                    when (tail.operationTypeCode) {
                        "CREATE" -> {
                            require(tail.predecessorOperationId == null) {
                                "Kök CREATE operasyonunun predecessor'ı olamaz: ${tail.predecessorOperationId}"
                            }
                            require(tail.baseVersion == null) {
                                "CREATE operasyonu için baseVersion null olmalıdır: ${tail.baseVersion}"
                            }
                            when (tail.statusCode) {
                                "PENDING" -> {
                                    require(tail.attemptCount == 0) {
                                        "Denenmemiş kök CREATE attemptCount 0 olmalıdır: ${tail.attemptCount}"
                                    }
                                    require(
                                        entity.sync.baseVersion == null &&
                                            entity.sync.version == 0L &&
                                            (
                                                entity.sync.syncStatus == "PENDING_CREATE" ||
                                                    (type == OutboxOperationType.DELETE && entity.sync.syncStatus == "PENDING_DELETE")
                                            ),
                                    ) {
                                        "Entity denenmemiş kök CREATE ile uyumsuz: $entity"
                                    }
                                    CanonicalMerchantVerificationTailTopology.EligiblePendingRootCreate(tail)
                                }
                                "IN_FLIGHT" -> {
                                    require(entity.sync.baseVersion == null && entity.sync.version == 0L) {
                                        "Entity in-flight kök CREATE version/baseVersion ile uyumsuz: $entity"
                                    }
                                    require(entity.sync.syncStatus == "PENDING_CREATE") {
                                        "In-flight kök CREATE entity syncStatus PENDING_CREATE olmalıdır: ${entity.sync.syncStatus}"
                                    }
                                    CanonicalMerchantVerificationTailTopology.InFlightRootCreate(tail)
                                }
                                else -> throw IllegalStateException("Geçersiz kök CREATE durumu: ${tail.statusCode}")
                            }
                        }
                        "UPDATE" -> {
                            check(tail.baseVersion != null && tail.baseVersion >= 1L) {
                                "Unblocked UPDATE operasyonu için baseVersion en az 1 olmalıdır: ${tail.baseVersion}"
                            }
                            require(entity.sync.syncStatus == "PENDING_UPDATE") {
                                "Rebased UPDATE entity syncStatus PENDING_UPDATE olmalıdır: ${entity.sync.syncStatus}"
                            }
                            val currentLocal = getUserMerchantVerificationRow(entity.id)
                            if (currentLocal != null) {
                                require(currentLocal.sync.syncStatus == "PENDING_UPDATE") {
                                    "Rebased UPDATE yerel entity syncStatus PENDING_UPDATE olmalıdır: ${currentLocal.sync.syncStatus}"
                                }
                            }
                            require(entity.sync.version >= 1L) {
                                "Rebased UPDATE entity version en az 1 olmalıdır: ${entity.sync.version}"
                            }
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Rebased UPDATE entity baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            require(entity.sync.baseVersion <= entity.sync.version) {
                                "Rebased UPDATE entity baseVersion (${entity.sync.baseVersion}) version'dan (${entity.sync.version}) büyük olamaz."
                            }
                            require(entity.sync.baseVersion == entity.sync.version) {
                                "Rebased UPDATE entity baseVersion (${entity.sync.baseVersion}) version (${entity.sync.version}) ile eşit olmalıdır."
                            }
                            require(tail.baseVersion == entity.sync.baseVersion) {
                                "Tail baseVersion (${tail.baseVersion}) entity baseVersion (${entity.sync.baseVersion}) ile eşleşmelidir."
                            }
                            if (tail.predecessorOperationId != null) {
                                val livePred = getOutboxById(syncScopeKey, tail.predecessorOperationId)
                                check(livePred == null) {
                                    "Unblocked rebased UPDATE tail için canlı predecessor bulunamaz: ${tail.predecessorOperationId}"
                                }
                            }
                            when (tail.statusCode) {
                                "PENDING" -> {
                                    check(tail.attemptCount == 0) {
                                        "Denenmemiş UPDATE attemptCount 0 olmalıdır: ${tail.attemptCount}"
                                    }
                                    CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail(tail)
                                }
                                "IN_FLIGHT" -> {
                                    check(!tail.isBlocked) { "IN_FLIGHT tail blocked olamaz: ${tail.operationId}" }
                                    CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail(tail)
                                }
                                else -> throw IllegalStateException("Geçersiz UPDATE durumu: ${tail.statusCode}")
                            }
                        }
                        "DELETE" -> {
                            check(tail.baseVersion != null && tail.baseVersion >= 1L) {
                                "Unblocked DELETE operasyonu için baseVersion en az 1 olmalıdır: ${tail.baseVersion}"
                            }
                            if (type == OutboxOperationType.DELETE) {
                                require(entity.sync.syncStatus == "PENDING_DELETE") {
                                    "Rebased DELETE entity syncStatus PENDING_DELETE olmalıdır: ${entity.sync.syncStatus}"
                                }
                                require(entity.sync.deletedAtEpochMillis != null) {
                                    "Rebased DELETE entity deletedAt zorunludur: $entity"
                                }
                            } else {
                                require(entity.sync.syncStatus == "PENDING_UPDATE") {
                                    "Rebased UPDATE after DELETE entity syncStatus PENDING_UPDATE olmalıdır: ${entity.sync.syncStatus}"
                                }
                            }
                            val currentLocal = getUserMerchantVerificationRow(entity.id)
                            if (currentLocal != null) {
                                require(currentLocal.sync.syncStatus == "PENDING_DELETE") {
                                    "Rebased DELETE yerel entity syncStatus PENDING_DELETE olmalıdır: ${currentLocal.sync.syncStatus}"
                                }
                            }
                            require(entity.sync.version >= 1L) {
                                "Rebased DELETE/UPDATE entity version en az 1 olmalıdır: ${entity.sync.version}"
                            }
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Rebased DELETE/UPDATE entity baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            require(entity.sync.baseVersion <= entity.sync.version) {
                                "Rebased DELETE/UPDATE entity baseVersion (${entity.sync.baseVersion}) version'dan (${entity.sync.version}) büyük olamaz."
                            }
                            require(entity.sync.baseVersion == entity.sync.version) {
                                "Rebased DELETE/UPDATE entity baseVersion (${entity.sync.baseVersion}) version (${entity.sync.version}) ile eşit olmalıdır."
                            }
                            require(tail.baseVersion == entity.sync.baseVersion) {
                                "Tail baseVersion (${tail.baseVersion}) entity baseVersion (${entity.sync.baseVersion}) ile eşleşmelidir."
                            }
                            if (tail.predecessorOperationId != null) {
                                val livePred = getOutboxById(syncScopeKey, tail.predecessorOperationId)
                                check(livePred == null) {
                                    "Unblocked rebased DELETE tail için canlı predecessor bulunamaz: ${tail.predecessorOperationId}"
                                }
                            }
                            when (tail.statusCode) {
                                "PENDING" -> {
                                    check(tail.attemptCount == 0) {
                                        "Denenmemiş DELETE attemptCount 0 olmalıdır: ${tail.attemptCount}"
                                    }
                                    CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail(tail)
                                }
                                "IN_FLIGHT" -> {
                                    check(!tail.isBlocked) { "IN_FLIGHT tail blocked olamaz: ${tail.operationId}" }
                                    CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail(tail)
                                }
                                else -> throw IllegalStateException("Geçersiz DELETE durumu: ${tail.statusCode}")
                            }
                        }
                        else -> throw IllegalStateException("Desteklenmeyen operasyon türü: ${tail.operationTypeCode}")
                    }
                } else {
                    require(tail.predecessorOperationId != null) {
                        "Kök outbox operasyonu blocked olamaz: ${tail.operationId}"
                    }
                    check(tail.operationTypeCode == "UPDATE") {
                        "Blocked successor operationType UPDATE olmalıdır: ${tail.operationTypeCode}"
                    }
                    check(tail.statusCode == "PENDING") {
                        "Successor tail statusCode PENDING olmalıdır: ${tail.statusCode}"
                    }
                    check(tail.attemptCount == 0) {
                        "Successor tail attemptCount 0 olmalıdır: ${tail.attemptCount}"
                    }
                    check(tail.baseVersion == null) {
                        "Pre-ACK blocked successor baseVersion null olmalıdır: ${tail.baseVersion}"
                    }

                    val pred =
                        getOutboxById(syncScopeKey, tail.predecessorOperationId)
                            ?: throw IllegalStateException(
                                "Orphan successor tespit edildi; predecessor bulunamadı: ${tail.predecessorOperationId}",
                            )
                    check(pred.protocolVersion == 2) { "Predecessor protocolVersion 2 olmalıdır: ${pred.operationId}" }
                    check(pred.syncScopeKey == syncScopeKey) { "Predecessor syncScopeKey eşleşmelidir: ${pred.syncScopeKey}" }
                    check(
                        pred.entityTypeCode == "USER_MERCHANT_VERIFICATION",
                    ) { "Predecessor entityTypeCode eşleşmelidir: ${pred.entityTypeCode}" }
                    check(pred.entityId == entity.id) { "Predecessor entityId eşleşmelidir: ${pred.entityId}" }
                    check(
                        pred.operationTypeCode == "CREATE",
                    ) { "Predecessor operationTypeCode CREATE olmalıdır: ${pred.operationTypeCode}" }
                    check(pred.statusCode == "IN_FLIGHT") { "Predecessor statusCode IN_FLIGHT olmalıdır: ${pred.statusCode}" }
                    check(pred.predecessorOperationId == null) { "Predecessor kök olmalıdır: ${pred.predecessorOperationId}" }
                    check(!pred.isBlocked) { "Predecessor isBlocked false olmalıdır: ${pred.operationId}" }
                    check(pred.baseVersion == null) { "Predecessor baseVersion null olmalıdır: ${pred.baseVersion}" }

                    require(entity.sync.syncStatus == "PENDING_CREATE") {
                        "BlockedPreAckUpdateSuccessor entity syncStatus PENDING_CREATE olmalıdır: ${entity.sync.syncStatus}"
                    }
                    require(entity.sync.version == 0L) {
                        "BlockedPreAckUpdateSuccessor entity version 0 olmalıdır: ${entity.sync.version}"
                    }
                    require(entity.sync.baseVersion == null) {
                        "BlockedPreAckUpdateSuccessor entity baseVersion null olmalıdır: ${entity.sync.baseVersion}"
                    }

                    val currentLocal =
                        getUserMerchantVerificationRow(entity.id)
                            ?: throw IllegalStateException("Successor için yerel entity bulunamadı: ${entity.id}")
                    check(currentLocal.sync.syncStatus == "PENDING_CREATE") {
                        "Mevcut yerel entity syncStatus PENDING_CREATE olmalıdır: ${currentLocal.sync.syncStatus}"
                    }
                    check(currentLocal.sync.version == 0L) {
                        "Mevcut yerel entity version 0 olmalıdır: ${currentLocal.sync.version}"
                    }
                    check(currentLocal.sync.baseVersion == null) {
                        "Mevcut yerel entity baseVersion null olmalıdır: ${currentLocal.sync.baseVersion}"
                    }

                    val tailPayload =
                        checkNotNull(tail.payloadJson?.takeIf { it.isNotBlank() }) {
                            "Successor tail payload_json boş olamaz: ${tail.operationId}"
                        }
                    val validatedTailPayload =
                        UserMerchantVerificationPayloadCodec.parseAndValidate(
                            jsonString = tailPayload,
                            operation = "CREATE",
                            baseVersion = null,
                        )
                    check(validatedTailPayload.id == currentLocal.id) { "Successor payload id yerel id ile eşleşmelidir" }
                    check(validatedTailPayload.userId == currentLocal.ownerId) { "Successor payload userId yerel ownerId ile eşleşmelidir" }
                    check(
                        validatedTailPayload.workspaceId == currentLocal.workspaceId,
                    ) { "Successor payload workspaceId yerel workspaceId ile eşleşmelidir" }
                    check(
                        validatedTailPayload.normalizedAlias == currentLocal.normalizedAlias,
                    ) { "Successor payload alias yerel alias ile eşleşmelidir" }
                    check(
                        validatedTailPayload.decisionCode == currentLocal.decisionCode,
                    ) { "Successor payload decisionCode yerel karar ile eşleşmelidir" }
                    check(
                        validatedTailPayload.merchantId == currentLocal.merchantId,
                    ) { "Successor payload merchantId yerel merchantId ile eşleşmelidir" }
                    check(validatedTailPayload.scopeCode == "PERSONAL") { "Successor payload scopeCode PERSONAL olmalıdır" }

                    CanonicalMerchantVerificationTailTopology.BlockedPreAckUpdateSuccessor(tail, pred)
                }
            }

        val validatedPayload =
            when (type) {
                OutboxOperationType.CREATE -> {
                    require(topology is CanonicalMerchantVerificationTailTopology.CleanNoTail) {
                        "CREATE operasyonu yalnız aktif kuyruk olmadığında yürütülebilir: ${entity.id}"
                    }
                    require(entity.sync.baseVersion == null) {
                        "CREATE operasyonu için baseVersion null olmalıdır: ${entity.sync.baseVersion}"
                    }
                    com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                        jsonString = payloadJson,
                        operation = "CREATE",
                        baseVersion = null,
                    )
                }
                OutboxOperationType.UPDATE -> {
                    when (topology) {
                        is CanonicalMerchantVerificationTailTopology.CleanNoTail -> {
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Kuyruksuz UPDATE operasyonu için baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "UPDATE",
                                baseVersion = entity.sync.baseVersion,
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.EligiblePendingRootCreate,
                        is CanonicalMerchantVerificationTailTopology.InFlightRootCreate,
                        is CanonicalMerchantVerificationTailTopology.BlockedPreAckUpdateSuccessor,
                        -> {
                            require(entity.sync.baseVersion == null) {
                                "Pre-ACK UPDATE mutasyonlarında baseVersion null olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "CREATE",
                                baseVersion = null,
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail -> {
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Rebased UPDATE operasyonu için baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "UPDATE",
                                baseVersion = entity.sync.baseVersion,
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail -> {
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Rebased UPDATE operasyonu için baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "UPDATE",
                                baseVersion = entity.sync.baseVersion,
                            )
                        }
                    }
                }
                OutboxOperationType.DELETE -> {
                    when (topology) {
                        is CanonicalMerchantVerificationTailTopology.CleanNoTail -> {
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Kuyruksuz DELETE operasyonu için baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "DELETE",
                                baseVersion = entity.sync.baseVersion,
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.EligiblePendingRootCreate -> {
                            require(entity.sync.baseVersion == null) {
                                "Pre-ACK DELETE operasyonunda baseVersion null olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "CREATE",
                                baseVersion = null,
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.InFlightRootCreate,
                        is CanonicalMerchantVerificationTailTopology.BlockedPreAckUpdateSuccessor,
                        -> {
                            throw IllegalArgumentException(
                                "Pre-ACK kök/successor üzerine DELETE successor eklenemez: ${entity.id}",
                            )
                        }
                        is CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail,
                        is CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail,
                        -> {
                            val tailOp =
                                when (topology) {
                                    is CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail -> topology.op
                                    is CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail -> topology.op
                                }
                            require(tailOp.statusCode != "IN_FLIGHT") {
                                "IN_FLIGHT predecessor üzerine DELETE successor üretilemez: ${entity.id}"
                            }
                            require(entity.sync.baseVersion != null && entity.sync.baseVersion >= 1L) {
                                "Rebased DELETE operasyonu için baseVersion en az 1 olmalıdır: ${entity.sync.baseVersion}"
                            }
                            com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec.parseAndValidate(
                                jsonString = payloadJson,
                                operation = "DELETE",
                                baseVersion = entity.sync.baseVersion,
                            )
                        }
                    }
                }
            }
        require(validatedPayload.id == entity.id) { "Payload id entity ile eşleşmelidir." }
        require(validatedPayload.userId == entity.ownerId) { "Payload user_id entity ile eşleşmelidir." }
        require(validatedPayload.normalizedAlias == entity.normalizedAlias) { "Payload normalized_alias entity ile eşleşmelidir." }
        require(validatedPayload.decisionCode == entity.decisionCode) { "Payload decision_code entity ile eşleşmelidir." }
        require(validatedPayload.merchantId == entity.merchantId) { "Payload merchant_id entity ile eşleşmelidir." }

        when (topology) {
            is CanonicalMerchantVerificationTailTopology.CleanNoTail -> {
                upsertUserMerchantVerificationRow(entity)
                val newOpId = operationIdFactory()
                validateOperationId(newOpId)
                val op =
                    SyncOperationEntity(
                        syncScopeKey = syncScopeKey,
                        operationId = newOpId,
                        entityTypeCode = "USER_MERCHANT_VERIFICATION",
                        entityId = entity.id,
                        operationTypeCode = type.name,
                        baseVersion = entity.sync.baseVersion,
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
            is CanonicalMerchantVerificationTailTopology.EligiblePendingRootCreate -> {
                val tailOp = topology.op
                if (type == OutboxOperationType.UPDATE) {
                    upsertUserMerchantVerificationRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tailOp.operationId}" }
                    return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    deleteUserMerchantVerificationRow(entity.id)
                    val deleted = deleteOutboxRow(syncScopeKey, tailOp.operationId)
                    check(deleted == 1) { "Outbox kaydı silinemedi: ${tailOp.operationId}" }
                    return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.HARD_DELETED)
                } else {
                    error("EligiblePendingRootCreate üzerine yeni CREATE eklenemez: ${entity.id}")
                }
            }
            is CanonicalMerchantVerificationTailTopology.InFlightRootCreate -> {
                require(type == OutboxOperationType.UPDATE) {
                    "IN_FLIGHT kök CREATE üzerine yalnız UPDATE successor eklenebilir: ${entity.id}, type=$type"
                }
                val tailOp = topology.op
                upsertUserMerchantVerificationRow(entity)
                val newOpId = operationIdFactory()
                validateOperationId(newOpId)
                val successor =
                    SyncOperationEntity(
                        syncScopeKey = syncScopeKey,
                        operationId = newOpId,
                        entityTypeCode = "USER_MERCHANT_VERIFICATION",
                        entityId = entity.id,
                        operationTypeCode = "UPDATE",
                        baseVersion = null,
                        payloadJson = payloadJson,
                        predecessorOperationId = tailOp.operationId,
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
            is CanonicalMerchantVerificationTailTopology.BlockedPreAckUpdateSuccessor -> {
                val tailOp = topology.op
                if (type == OutboxOperationType.UPDATE) {
                    upsertUserMerchantVerificationRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tailOp.operationId}" }
                    return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.COALESCED)
                } else {
                    error("BlockedPreAckUpdateSuccessor üzerine yalnızca UPDATE coalesce edilebilir: ${entity.id}")
                }
            }
            is CanonicalMerchantVerificationTailTopology.RebasedUnblockedUpdateTail -> {
                val tailOp = topology.op
                if (tailOp.statusCode == "PENDING" && tailOp.attemptCount == 0) {
                    if (type == OutboxOperationType.UPDATE) {
                        upsertUserMerchantVerificationRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tailOp.operationId}" }
                        return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.COALESCED)
                    } else if (type == OutboxOperationType.DELETE) {
                        upsertUserMerchantVerificationRow(entity)
                        val updated = convertToPendingDelete(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tailOp.operationId}" }
                        return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    } else {
                        error("RebasedUnblockedUpdateTail üzerine yeni CREATE eklenemez: ${entity.id}")
                    }
                } else if (tailOp.statusCode == "IN_FLIGHT") {
                    require(type == OutboxOperationType.UPDATE) {
                        "IN_FLIGHT UPDATE predecessor üzerine DELETE successor eklenemez: ${entity.id}, type=$type"
                    }
                    upsertUserMerchantVerificationRow(entity)
                    val newOpId = operationIdFactory()
                    validateOperationId(newOpId)
                    val successor =
                        SyncOperationEntity(
                            syncScopeKey = syncScopeKey,
                            operationId = newOpId,
                            entityTypeCode = "USER_MERCHANT_VERIFICATION",
                            entityId = entity.id,
                            operationTypeCode = "UPDATE",
                            baseVersion = entity.sync.baseVersion,
                            payloadJson = payloadJson,
                            predecessorOperationId = tailOp.operationId,
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
                } else {
                    error("Desteklenmeyen rebased tail durumu: ${tailOp.statusCode}")
                }
            }
            is CanonicalMerchantVerificationTailTopology.RebasedUnblockedDeleteTail -> {
                val tailOp = topology.op
                if (tailOp.statusCode == "PENDING" && tailOp.attemptCount == 0) {
                    if (type == OutboxOperationType.UPDATE) {
                        upsertUserMerchantVerificationRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tailOp.operationId}" }
                        return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else if (type == OutboxOperationType.DELETE) {
                        upsertUserMerchantVerificationRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tailOp.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tailOp.operationId}" }
                        return V2EnqueueResult(tailOp.operationId, V2EnqueueDecision.COALESCED)
                    } else {
                        error("RebasedUnblockedDeleteTail üzerine yeni CREATE eklenemez: ${entity.id}")
                    }
                } else if (tailOp.statusCode == "IN_FLIGHT") {
                    require(type == OutboxOperationType.UPDATE) {
                        "IN_FLIGHT DELETE predecessor üzerine DELETE successor eklenemez: ${entity.id}, type=$type"
                    }
                    upsertUserMerchantVerificationRow(entity)
                    val newOpId = operationIdFactory()
                    validateOperationId(newOpId)
                    val successor =
                        SyncOperationEntity(
                            syncScopeKey = syncScopeKey,
                            operationId = newOpId,
                            entityTypeCode = "USER_MERCHANT_VERIFICATION",
                            entityId = entity.id,
                            operationTypeCode = "UPDATE",
                            baseVersion = entity.sync.baseVersion,
                            payloadJson = payloadJson,
                            predecessorOperationId = tailOp.operationId,
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
                } else {
                    error("Desteklenmeyen rebased delete tail durumu: ${tailOp.statusCode}")
                }
            }
        }
    }

    @Transaction
    suspend fun applyTransactionMerchantCorrectionV2(
        syncScopeKey: String,
        actorUserId: String,
        transactionId: String,
        isAssign: Boolean,
        assignedMerchantId: String?,
        assignedCatalogVersion: Int?,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
        require(userScope.userId == actorUserId) {
            "syncScopeKey ($syncScopeKey) actorUserId ($actorUserId) ile eşleşmelidir."
        }

        val catalogMerchant =
            if (isAssign) {
                require(!assignedMerchantId.isNullOrBlank()) { "ASSIGN için assignedMerchantId zorunludur." }
                requireNotNull(assignedCatalogVersion) { "ASSIGN için assignedCatalogVersion zorunludur." }
                val found =
                    StarterMerchantCatalog.merchants.firstOrNull { it.id == assignedMerchantId }
                        ?: throw IllegalArgumentException("Katalogda bulunmayan merchant: $assignedMerchantId")
                require(assignedCatalogVersion == found.catalogVersion) {
                    "Geçersiz katalog sürümü (beklenen: ${found.catalogVersion}, gelen: $assignedCatalogVersion)"
                }
                require(found.catalogVersion >= 1) {
                    "Katalog sürümü en az 1 olmalıdır: ${found.catalogVersion}"
                }
                found
            } else {
                require(assignedMerchantId == null && assignedCatalogVersion == null) {
                    "BLOCK için assignedMerchantId ve assignedCatalogVersion null olmalıdır."
                }
                null
            }

        val existingTrx =
            getTransactionRow(transactionId)
                ?: throw IllegalArgumentException("Transaction bulunamadı: $transactionId")

        require(existingTrx.ownerId == actorUserId) {
            "Transaction sahibi (${existingTrx.ownerId}) actorUserId ($actorUserId) ile eşleşmelidir."
        }
        require(existingTrx.workspaceId == null) {
            "Yalnız kişisel transaction üzerinde merchant düzeltmesi yapılabilir (workspaceId null olmalıdır): $transactionId"
        }
        require(existingTrx.sync.deletedAtEpochMillis == null) {
            "Silinmiş transaction üzerinde merchant düzeltmesi yapılamaz: $transactionId"
        }

        val rawDesc = existingTrx.description
        require(!rawDesc.isNullOrBlank()) {
            "Transaction description boş olamaz: $transactionId"
        }

        val normalizedAlias = TurkishDescriptionNormalizer.normalize(rawDesc)
        require(normalizedAlias.isNotBlank()) {
            "Transaction alias boş olamaz: $transactionId"
        }
        require(normalizedAlias.length <= com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH) {
            "Transaction alias uzunluk sınırını (${com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH}) aşıyor: $transactionId"
        }
        require(normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Transaction alias genel sözcükler kümesinde ($normalizedAlias) yer alamaz."
        }

        require(!TransactionMerchantEligibility.isExcluded(normalizedAlias)) {
            "EXCLUDED_TRANSACTION üzerinde merchant düzeltmesi yapılamaz: $transactionId"
        }

        val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(actorUserId, normalizedAlias)
        val existingVerification = getUserMerchantVerificationRow(deterministicId)

        val decisionCode = if (isAssign) "ASSIGN" else "BLOCK"
        val targetMerchantId = catalogMerchant?.id

        val (entityToSave, opType) =
            if (existingVerification == null) {
                val newEntity =
                    UserMerchantVerificationEntity(
                        id = deterministicId,
                        ownerId = actorUserId,
                        workspaceId = null,
                        scopeKey = "user:$actorUserId",
                        normalizedAlias = normalizedAlias,
                        decisionCode = decisionCode,
                        merchantId = targetMerchantId,
                        scopeCode = "PERSONAL",
                        createdAtEpochMillis = nowEpochMillis,
                        sync =
                            com.feniqo.mobile.data.local.entity.SyncMetadata(
                                syncStatus = SyncStatus.PENDING_CREATE.name,
                                updatedAtEpochMillis = nowEpochMillis,
                                localUpdatedAtEpochMillis = nowEpochMillis,
                                deletedAtEpochMillis = null,
                                version = 0L,
                                baseVersion = null,
                                lastSyncError = null,
                            ),
                    )
                newEntity to OutboxOperationType.CREATE
            } else {
                require(existingVerification.ownerId == actorUserId) {
                    "Mevcut verification sahibi ($actorUserId) ile eşleşmelidir."
                }
                require(existingVerification.scopeKey == "user:$actorUserId") {
                    "Mevcut verification scopeKey user:$actorUserId ile eşleşmelidir."
                }
                require(existingVerification.workspaceId == null) {
                    "Mevcut verification workspaceId null olmalıdır."
                }
                require(existingVerification.scopeCode == "PERSONAL") {
                    "Mevcut verification scopeCode PERSONAL olmalıdır."
                }
                require(existingVerification.normalizedAlias == normalizedAlias) {
                    "Mevcut verification alias ($normalizedAlias) ile eşleşmelidir."
                }
                require(existingVerification.id == deterministicId) {
                    "Mevcut verification id ($deterministicId) ile eşleşmelidir."
                }

                val isDeleted = existingVerification.sync.deletedAtEpochMillis != null
                val nextStatus =
                    if (existingVerification.sync.syncStatus == SyncStatus.PENDING_CREATE.name && !isDeleted) {
                        SyncStatus.PENDING_CREATE.name
                    } else {
                        SyncStatus.PENDING_UPDATE.name
                    }

                val updatedEntity =
                    existingVerification.copy(
                        decisionCode = decisionCode,
                        merchantId = targetMerchantId,
                        sync =
                            existingVerification.sync.copy(
                                syncStatus = nextStatus,
                                localUpdatedAtEpochMillis = nowEpochMillis,
                                deletedAtEpochMillis = null,
                                baseVersion = if (existingVerification.sync.version > 0L) existingVerification.sync.version else null,
                            ),
                    )
                updatedEntity to OutboxOperationType.UPDATE
            }

        val payloadJson =
            when (opType) {
                OutboxOperationType.CREATE -> UserMerchantVerificationPayloadCodec.encodeCreate(entityToSave)
                OutboxOperationType.UPDATE -> UserMerchantVerificationPayloadCodec.encodeUpdate(entityToSave)
                OutboxOperationType.DELETE -> error("DELETE desteklenmez")
            }

        val enqueueResult =
            mutateUserMerchantVerificationV2(
                syncScopeKey = syncScopeKey,
                entity = entityToSave,
                type = opType,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = nowEpochMillis,
            )

        val updatedRowCount =
            updateTransactionMerchantAttribution(
                transactionId = transactionId,
                merchantId = catalogMerchant?.id,
                merchantMatchSourceCode = if (isAssign) "USER_VERIFIED" else null,
                merchantConfidence = if (isAssign) 100 else null,
                merchantCatalogVersion = catalogMerchant?.catalogVersion,
            )
        check(updatedRowCount == 1) {
            "Transaction merchant attribution satırı güncellenemedi: $transactionId"
        }

        return enqueueResult
    }

    @Transaction
    suspend fun createTransactionWithMerchantDecisionV2(
        syncScopeKey: String,
        actorUserId: String,
        transactionEntity: TransactionEntity,
        tags: List<TagEntity> = emptyList(),
        tagLinks: List<TransactionTagCrossRef> = emptyList(),
        transactionPayloadJson: String,
        decision: TransactionMerchantCreateDecision,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): TransactionCreationWithMerchantResult {
        val userScope = SyncScopeKey.requireUserScope(syncScopeKey)
        require(userScope.userId == actorUserId) {
            "syncScopeKey ($syncScopeKey) actorUserId ($actorUserId) ile eşleşmelidir."
        }
        require(transactionEntity.ownerId == actorUserId) {
            "Transaction sahibi (${transactionEntity.ownerId}) actorUserId ($actorUserId) ile eşleşmelidir."
        }

        return when (decision) {
            is TransactionMerchantCreateDecision.Auto -> {
                val txResult =
                    mutateTransactionV2(
                        syncScopeKey = syncScopeKey,
                        entity = transactionEntity,
                        tags = tags,
                        tagLinks = tagLinks,
                        type = OutboxOperationType.CREATE,
                        payloadJson = transactionPayloadJson,
                        operationIdFactory = operationIdFactory,
                        nowEpochMillis = nowEpochMillis,
                    )
                TransactionCreationWithMerchantResult(
                    transactionOperationId = txResult.operationId,
                    verificationOperationId = null,
                )
            }
            is TransactionMerchantCreateDecision.KnownMerchant,
            is TransactionMerchantCreateDecision.UseTransactionName,
            -> {
                require(transactionEntity.workspaceId == null) {
                    "Explicit merchant kararı yalnız kişisel işlemlerde geçerlidir (workspaceId null olmalıdır): ${transactionEntity.id}"
                }
                require(transactionEntity.typeCode == "EXPENSE") {
                    "Explicit merchant kararı yalnız EXPENSE işlem türünde geçerlidir: ${transactionEntity.typeCode}"
                }

                val aliasResult =
                    TransactionMerchantVerificationAliasResolver.resolve(
                        rawDescription = transactionEntity.description,
                        decision = decision,
                    )
                val resolvedAlias =
                    when (aliasResult) {
                        is TransactionMerchantVerificationAliasResolver.Result.Resolved -> aliasResult.normalizedAlias
                        is TransactionMerchantVerificationAliasResolver.Result.Rejected -> {
                            throw IllegalArgumentException("Merchant kararı doğrulama alias'ı geçersiz: ${aliasResult.reason}")
                        }
                    }

                val category =
                    getCategoryRow(transactionEntity.categoryId)
                        ?: throw IllegalArgumentException("Kategori bulunamadı: ${transactionEntity.categoryId}")
                require(category.sync.deletedAtEpochMillis == null) {
                    "Silinmiş kategori kullanılamaz: ${transactionEntity.categoryId}"
                }
                require(category.typeCode == "EXPENSE") {
                    "Kategori türü EXPENSE olmalıdır: ${category.typeCode}"
                }
                require(category.workspaceId == null) {
                    "Kategori kişisel kapsamda olmalıdır (workspaceId null olmalıdır): ${category.id}"
                }
                val isValidSystem = category.isDefault && category.ownerId == null && category.workspaceId == null
                val isValidPersonal = !category.isDefault && category.ownerId == actorUserId && category.workspaceId == null
                require(isValidSystem || isValidPersonal) {
                    "Kategori yalnız sistem (isDefault=true, ownerId=null, workspaceId=null) veya kişisel (isDefault=false, ownerId=$actorUserId, workspaceId=null) olabilir: ${category.id}"
                }

                val isAssign: Boolean
                val targetMerchantId: String?
                val catalogVersion: Int?

                when (decision) {
                    is TransactionMerchantCreateDecision.KnownMerchant -> {
                        val cleanId = decision.merchantId.value.trim()
                        require(cleanId.isNotBlank()) { "Merchant kimliği boş olamaz." }
                        val found =
                            StarterMerchantCatalog.merchants.firstOrNull { it.id == cleanId }
                                ?: throw IllegalArgumentException("Katalogda bulunmayan merchant: $cleanId")
                        require(found.catalogVersion >= 1) {
                            "Katalog sürümü en az 1 olmalıdır: ${found.catalogVersion}"
                        }
                        require(CategoryMerchantCandidateResolver.isCompatible(category.iconKey, found)) {
                            "Kategori iconKey (${category.iconKey}) merchant (${found.id}/${found.categoryIconKey.key}) ile uyuşmuyor."
                        }
                        isAssign = true
                        targetMerchantId = found.id
                        catalogVersion = found.catalogVersion
                    }
                    is TransactionMerchantCreateDecision.UseTransactionName -> {
                        isAssign = false
                        targetMerchantId = null
                        catalogVersion = null
                    }
                    is TransactionMerchantCreateDecision.Auto -> error("Impossible")
                }

                val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(actorUserId, resolvedAlias)
                val existingVerification = getUserMerchantVerificationRow(deterministicId)

                val decisionCode = if (isAssign) "ASSIGN" else "BLOCK"

                val (verificationEntityToSave, verificationOpType) =
                    if (existingVerification == null) {
                        val newEntity =
                            UserMerchantVerificationEntity(
                                id = deterministicId,
                                ownerId = actorUserId,
                                workspaceId = null,
                                scopeKey = "user:$actorUserId",
                                normalizedAlias = resolvedAlias,
                                decisionCode = decisionCode,
                                merchantId = targetMerchantId,
                                scopeCode = "PERSONAL",
                                createdAtEpochMillis = nowEpochMillis,
                                sync =
                                    com.feniqo.mobile.data.local.entity.SyncMetadata(
                                        syncStatus = SyncStatus.PENDING_CREATE.name,
                                        updatedAtEpochMillis = nowEpochMillis,
                                        localUpdatedAtEpochMillis = nowEpochMillis,
                                        deletedAtEpochMillis = null,
                                        version = 0L,
                                        baseVersion = null,
                                        lastSyncError = null,
                                    ),
                            )
                        newEntity to OutboxOperationType.CREATE
                    } else {
                        require(existingVerification.ownerId == actorUserId) {
                            "Mevcut verification sahibi ($actorUserId) ile eşleşmelidir."
                        }
                        require(existingVerification.scopeKey == "user:$actorUserId") {
                            "Mevcut verification scopeKey user:$actorUserId ile eşleşmelidir."
                        }
                        require(existingVerification.workspaceId == null) {
                            "Mevcut verification workspaceId null olmalıdır."
                        }
                        require(existingVerification.scopeCode == "PERSONAL") {
                            "Mevcut verification scopeCode PERSONAL olmalıdır."
                        }
                        require(existingVerification.normalizedAlias == resolvedAlias) {
                            "Mevcut verification alias ($resolvedAlias) ile eşleşmelidir."
                        }
                        require(existingVerification.id == deterministicId) {
                            "Mevcut verification id ($deterministicId) ile eşleşmelidir."
                        }

                        val isDeleted = existingVerification.sync.deletedAtEpochMillis != null
                        val nextStatus =
                            if (existingVerification.sync.syncStatus == SyncStatus.PENDING_CREATE.name && !isDeleted) {
                                SyncStatus.PENDING_CREATE.name
                            } else {
                                SyncStatus.PENDING_UPDATE.name
                            }

                        val updatedEntity =
                            existingVerification.copy(
                                decisionCode = decisionCode,
                                merchantId = targetMerchantId,
                                sync =
                                    existingVerification.sync.copy(
                                        syncStatus = nextStatus,
                                        localUpdatedAtEpochMillis = nowEpochMillis,
                                        deletedAtEpochMillis = null,
                                        baseVersion =
                                            if (existingVerification.sync.version >
                                                0L
                                            ) {
                                                existingVerification.sync.version
                                            } else {
                                                null
                                            },
                                    ),
                            )
                        updatedEntity to OutboxOperationType.UPDATE
                    }

                val verificationPayloadJson =
                    when (verificationOpType) {
                        OutboxOperationType.CREATE -> UserMerchantVerificationPayloadCodec.encodeCreate(verificationEntityToSave)
                        OutboxOperationType.UPDATE -> UserMerchantVerificationPayloadCodec.encodeUpdate(verificationEntityToSave)
                        OutboxOperationType.DELETE -> error("DELETE desteklenmez")
                    }

                val verificationEnqueueResult =
                    mutateUserMerchantVerificationV2(
                        syncScopeKey = syncScopeKey,
                        entity = verificationEntityToSave,
                        type = verificationOpType,
                        payloadJson = verificationPayloadJson,
                        operationIdFactory = operationIdFactory,
                        nowEpochMillis = nowEpochMillis,
                    )

                val finalTransactionEntity =
                    if (isAssign) {
                        transactionEntity.copy(
                            merchantId = targetMerchantId,
                            merchantMatchSourceCode = "USER_VERIFIED",
                            merchantConfidence = 100,
                            merchantCatalogVersion = catalogVersion,
                        )
                    } else {
                        transactionEntity.copy(
                            merchantId = null,
                            merchantMatchSourceCode = null,
                            merchantConfidence = null,
                            merchantCatalogVersion = null,
                        )
                    }

                val txResult =
                    mutateTransactionV2(
                        syncScopeKey = syncScopeKey,
                        entity = finalTransactionEntity,
                        tags = tags,
                        tagLinks = tagLinks,
                        type = OutboxOperationType.CREATE,
                        payloadJson = transactionPayloadJson,
                        operationIdFactory = operationIdFactory,
                        nowEpochMillis = nowEpochMillis,
                    )

                TransactionCreationWithMerchantResult(
                    transactionOperationId = txResult.operationId,
                    verificationOperationId = verificationEnqueueResult.operationId,
                )
            }
        }
    }

    @Transaction
    suspend fun mutateProfileV2(
        syncScopeKey: String,
        entity: UserProfileEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(type != OutboxOperationType.DELETE) { "Profil silme işlemi desteklenmemektedir." }

        val tailCandidates = getActiveTailCandidates(syncScopeKey, "PROFILE", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertProfileRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                }
            }

            upsertProfileRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: CategoryEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "CATEGORY", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertCategoryRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteCategoryRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertCategoryRow(entity)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertCategoryRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: TransactionEntity,
        tags: List<TagEntity>,
        tagLinks: List<TransactionTagCrossRef>,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                if (type == OutboxOperationType.DELETE && canSafelyHardDeletePendingOrCreate(tail)) {
                    deleteTransactionTagRows(entity.id)
                    deleteTransactionRow(entity.id)
                    val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                    check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                }
                if (type == OutboxOperationType.UPDATE && canSafelyReplaceRejectedCreate(tail)) {
                    val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                    check(deleted == 1) { "Başarısız outbox kaydı temizlenemedi: ${tail.operationId}" }
                    upsertTransactionRow(entity)
                    if (tags.isNotEmpty()) upsertTagRows(tags)
                    deleteTransactionTagRows(entity.id)
                    if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
                    val newOpId = operationIdFactory()
                    validateOperationId(newOpId)
                    val freshCreate =
                        SyncOperationEntity(
                            syncScopeKey = syncScopeKey,
                            operationId = newOpId,
                            entityTypeCode = "TRANSACTION",
                            entityId = entity.id,
                            operationTypeCode = OutboxOperationType.CREATE.name,
                            baseVersion = null,
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
                    insertOutboxRow(freshCreate)
                    return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
                }
            }

            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertTransactionRow(entity)
                    if (tags.isNotEmpty()) upsertTagRows(tags)
                    deleteTransactionTagRows(entity.id)
                    if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
                    val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    upsertTransactionRow(entity)
                    val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                }
            }

            upsertTransactionRow(entity)
            if (tags.isNotEmpty()) upsertTagRows(tags)
            deleteTransactionTagRows(entity.id)
            if (tagLinks.isNotEmpty()) upsertTransactionTagRows(tagLinks)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: TransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                if (type == OutboxOperationType.DELETE && canSafelyHardDeletePendingOrCreate(tail)) {
                    deleteTransactionTagRows(entity.id)
                    deleteTransactionRow(entity.id)
                    val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                    check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                }
                if (type == OutboxOperationType.UPDATE && canSafelyReplaceRejectedCreate(tail)) {
                    val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                    check(deleted == 1) { "Başarısız outbox kaydı temizlenemedi: ${tail.operationId}" }
                    upsertTransactionRow(entity)
                    val newOpId = operationIdFactory()
                    validateOperationId(newOpId)
                    val freshCreate =
                        SyncOperationEntity(
                            syncScopeKey = syncScopeKey,
                            operationId = newOpId,
                            entityTypeCode = "TRANSACTION",
                            entityId = entity.id,
                            operationTypeCode = OutboxOperationType.CREATE.name,
                            baseVersion = null,
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
                    insertOutboxRow(freshCreate)
                    return V2EnqueueResult(newOpId, V2EnqueueDecision.INSERTED)
                }
            }

            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    upsertTransactionRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                } else if (type == OutboxOperationType.DELETE) {
                    upsertTransactionRow(entity)
                    val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                }
            }

            upsertTransactionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: BudgetEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "BUDGET", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertBudgetRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertBudgetRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteBudgetRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertBudgetRow(entity)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertBudgetRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: RecurringTransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            check(tail.syncScopeKey == syncScopeKey) { "Tail syncScopeKey eşleşmelidir: ${tail.syncScopeKey}" }
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertRecurringTransactionRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertRecurringTransactionRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteRecurringTransactionRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertRecurringTransactionRow(entity)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertRecurringTransactionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: SubscriptionEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "SUBSCRIPTION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertSubscriptionRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertSubscriptionRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteSubscriptionRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertSubscriptionRow(entity)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertSubscriptionRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
    suspend fun mutateSubscriptionWithPriceHistoryV2(
        syncScopeKey: String,
        entity: SubscriptionEntity,
        priceHistory: SubscriptionPriceHistoryEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        upsertSubscriptionPriceHistoryRow(priceHistory)
        return mutateSubscriptionV2(
            syncScopeKey = syncScopeKey,
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillis,
        )
    }

    @Transaction
    suspend fun mutateSubscriptionWithPaymentV2(
        syncScopeKey: String,
        entity: SubscriptionEntity,
        payment: SubscriptionPaymentEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        upsertSubscriptionPaymentRow(payment)
        return mutateSubscriptionV2(
            syncScopeKey = syncScopeKey,
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillis,
        )
    }

    /** Kişisel varlığı ve immutable V2 payload snapshot'ını tek Room transaction'ında yazar. */
    @Transaction
    suspend fun mutateAssetV2(
        syncScopeKey: String,
        entity: AssetEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(payloadJson.isNotBlank()) { "Varlık outbox payload'ı boş olamaz." }
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "ASSET", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif varlık kuyruk sonu bulundu: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2 && tail.attemptCount == 0 && tail.statusCode == "PENDING") {
            when (type) {
                OutboxOperationType.UPDATE -> {
                    upsertAssetRow(entity)
                    val updated =
                        if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                            convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        } else {
                            coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        }
                    check(updated == 1) { "Varlık outbox kaydı güncellenemedi: ${tail.operationId}" }
                    return V2EnqueueResult(
                        tail.operationId,
                        if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                            V2EnqueueDecision.CONVERTED_TO_UPDATE
                        } else {
                            V2EnqueueDecision.COALESCED
                        },
                    )
                }
                OutboxOperationType.DELETE -> {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        val entityDeleted = deleteAssetRow(entity.id)
                        check(entityDeleted == 1) { "Varlık satırı silinemedi: ${entity.id}" }
                        val outboxDeleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(outboxDeleted == 1) { "Varlık outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    }
                    upsertAssetRow(entity)
                    val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Varlık outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                }
                OutboxOperationType.CREATE -> error("ASSET CREATE mevcut aktif kuyruk sonuyla tekrar yazılamaz.")
            }
        }

        upsertAssetRow(entity)
        val operationId = operationIdFactory()
        validateOperationId(operationId)
        insertOutboxRow(
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
                operationId = operationId,
                entityTypeCode = "ASSET",
                entityId = entity.id,
                operationTypeCode = type.name,
                baseVersion = if (tail == null) entity.sync.baseVersion else null,
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
            ),
        )
        return V2EnqueueResult(operationId, V2EnqueueDecision.INSERTED)
    }

    @Transaction
    suspend fun mutateGoalV2(
        syncScopeKey: String,
        entity: GoalEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveGoalAggregateTailCandidates(syncScopeKey, entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif kuyruk sonu (tail) tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING" && tail.entityTypeCode == "GOAL") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertGoalRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertGoalRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        tombstoneGoalContributionsForDeletedGoal(
                            entity.id,
                            entity.sync.deletedAtEpochMillis ?: nowEpochMillis,
                            nowEpochMillis,
                        )
                        deleteGoalRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertGoalRow(entity)
                        tombstoneGoalContributionsForDeletedGoal(
                            entity.id,
                            entity.sync.deletedAtEpochMillis ?: nowEpochMillis,
                            nowEpochMillis,
                        )
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
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
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: GoalContributionEntity,
        updatedGoal: GoalEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        require(updatedGoal.currentAmountMinor >= 0) { "Hedef tutarı negatif olamaz." }

        val tailCandidates = getActiveGoalAggregateTailCandidates(syncScopeKey, entity.goalId)
        check(tailCandidates.size <= 1) { "Birden fazla aktif hedef kuyruk sonu tespit edildi: ${entity.goalId}" }
        val tail = tailCandidates.firstOrNull()

        upsertGoalContributionRow(entity)
        upsertGoalRow(updatedGoal)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)

        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: DebtEntity,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveDebtAggregateTailCandidates(syncScopeKey, entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif borç kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING" && tail.entityTypeCode == "DEBT") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertDebtRow(entity)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertDebtRow(entity)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        deleteDebtRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertDebtRow(entity)
                        tombstoneDebtPaymentsForDeletedDebt(entity.id, entity.sync.deletedAtEpochMillis ?: nowEpochMillis, nowEpochMillis)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
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
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: DebtPaymentEntity,
        updatedDebt: DebtEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val tailCandidates = getActiveDebtAggregateTailCandidates(syncScopeKey, entity.debtId)
        check(tailCandidates.size <= 1) { "Birden fazla aktif borç kuyruk sonu tespit edildi: ${entity.debtId}" }
        val tail = tailCandidates.firstOrNull()

        upsertDebtPaymentRow(entity)
        upsertDebtRow(updatedDebt)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)

        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: WorkspaceEntity,
        members: List<WorkspaceMemberEntity>,
        type: OutboxOperationType,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
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

        val tailCandidates = getActiveTailCandidates(syncScopeKey, "WORKSPACE", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif workspace kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (type == OutboxOperationType.UPDATE) {
                    if (tail.operationTypeCode == OutboxOperationType.DELETE.name) {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = convertPendingDeleteToUpdate(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı UPDATE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_UPDATE)
                    } else {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                    }
                } else if (type == OutboxOperationType.DELETE) {
                    if (tail.operationTypeCode == OutboxOperationType.CREATE.name && tail.predecessorOperationId == null) {
                        deleteWorkspaceMemberRows(entity.id)
                        deleteWorkspaceRow(entity.id)
                        val deleted = deleteOutboxRow(syncScopeKey, tail.operationId)
                        check(deleted == 1) { "Outbox kaydı silinemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.HARD_DELETED)
                    } else {
                        upsertWorkspaceRow(entity)
                        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
                        val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                        check(updated == 1) { "Outbox kaydı DELETE'e dönüştürülemedi: ${tail.operationId}" }
                        return V2EnqueueResult(tail.operationId, V2EnqueueDecision.CONVERTED_TO_DELETE)
                    }
                }
            }

            upsertWorkspaceRow(entity)
            if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: WorkspaceInvitationEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
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

        val tailCandidates = getActiveTailCandidates(syncScopeKey, "WORKSPACE_INVITATION", entity.id)
        check(tailCandidates.size <= 1) { "Birden fazla aktif workspace invitation kuyruk sonu tespit edildi: ${entity.id}" }
        val tail = tailCandidates.firstOrNull()

        upsertWorkspaceInvitationRow(entity)

        val newOpId = operationIdFactory()
        validateOperationId(newOpId)
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: WorkspaceMemberEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
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
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "WORKSPACE_MEMBER", canonicalEntityId)
        check(tailCandidates.size <= 1) {
            "Birden fazla aktif workspace member kuyruk sonu tespit edildi: $canonicalEntityId"
        }
        val tail = tailCandidates.firstOrNull()

        if (tail != null && tail.protocolVersion == 2) {
            if (tail.attemptCount == 0 && tail.statusCode == "PENDING") {
                if (tail.operationTypeCode == OutboxOperationType.UPDATE.name) {
                    upsertWorkspaceMemberRow(entity)
                    val updated = coalescePendingPayload(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
                    check(updated == 1) { "Outbox payload coalesce edilemedi: ${tail.operationId}" }
                    return V2EnqueueResult(tail.operationId, V2EnqueueDecision.COALESCED)
                }
            }

            upsertWorkspaceMemberRow(entity)
            val newOpId = operationIdFactory()
            validateOperationId(newOpId)
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: WorkspaceMemberEntity,
        activeProfileId: String?,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult {
        SyncScopeKey.requireUserScope(syncScopeKey)
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
        val tailCandidates = getActiveTailCandidates(syncScopeKey, "WORKSPACE_MEMBER", canonicalEntityId)
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
                    val updated = convertToPendingDelete(syncScopeKey, tail.operationId, payloadJson, nowEpochMillis)
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
            val successor =
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
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
        val op =
            SyncOperationEntity(
                syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        entity: WorkspaceMemberEntity,
        payloadJson: String,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): V2EnqueueResult =
        mutateWorkspaceMemberLeaveV2(
            syncScopeKey = syncScopeKey,
            entity = entity,
            activeProfileId = null,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillis,
        )

    @Transaction
    suspend fun upsertProfileAndEnqueue(
        entity: UserProfileEntity,
        operation: SyncOperationEntity,
    ) {
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
        upsertProfileRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertWorkspaceAndEnqueue(
        entity: WorkspaceEntity,
        members: List<WorkspaceMemberEntity>,
        operation: SyncOperationEntity,
    ) {
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
        upsertWorkspaceRow(entity)
        if (members.isNotEmpty()) upsertWorkspaceMemberRows(members)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertCategoryAndEnqueue(
        entity: CategoryEntity,
        operation: SyncOperationEntity,
    ) {
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
        upsertCategoryRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertBudgetAndEnqueue(
        entity: BudgetEntity,
        operation: SyncOperationEntity,
    ) {
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
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
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
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
        SyncScopeKey.requireUserScope(operation.syncScopeKey)
        upsertTransactionRow(entity)
        insertOutboxRow(operation)
    }

    @Transaction
    suspend fun upsertTransactionsAndEnqueue(units: List<TransactionMutationUnit>) {
        for (unit in units) {
            SyncScopeKey.requireUserScope(unit.operation.syncScopeKey)
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
    suspend fun upsertTransactionsKeepingTagsAndEnqueue(units: List<TransactionKeepingTagsMutationUnit>) {
        for (unit in units) {
            SyncScopeKey.requireUserScope(unit.operation.syncScopeKey)
            upsertTransactionRow(unit.entity)
            insertOutboxRow(unit.operation)
        }
    }

    @Transaction
    suspend fun mutateTransactionCreatesV2(
        syncScopeKey: String,
        inputs: List<TransactionCreateInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<String> {
        SyncScopeKey.requireUserScope(syncScopeKey)
        return inputs.map { input ->
            val result =
                mutateTransactionV2(
                    syncScopeKey = syncScopeKey,
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

    /** Backup restore is all-or-nothing across categories, transactions and their V2 outbox rows. */
    @Transaction
    suspend fun importPersonalBackupV1(
        syncScopeKey: String,
        categoryInputs: List<BackupCategoryCreateInputV1>,
        transactionInputs: List<TransactionCreateInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<String> {
        SyncScopeKey.requireUserScope(syncScopeKey)
        val categoryIds = categoryInputs.map { it.entity.id }
        val transactionIds = transactionInputs.map { it.entity.id }
        require(categoryIds.distinct().size == categoryIds.size) { "Yinelenen kategori kimliği." }
        require(transactionIds.distinct().size == transactionIds.size) { "Yinelenen işlem kimliği." }
        val operationIds = mutableListOf<String>()
        categoryInputs.forEach { input ->
            operationIds +=
                mutateCategoryV2(
                    syncScopeKey = syncScopeKey,
                    entity = input.entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = input.payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = nowEpochMillis,
                ).operationId
        }
        transactionInputs.forEach { input ->
            operationIds +=
                mutateTransactionV2(
                    syncScopeKey = syncScopeKey,
                    entity = input.entity,
                    tags = input.tags,
                    tagLinks = input.tagLinks,
                    type = OutboxOperationType.CREATE,
                    payloadJson = input.payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = nowEpochMillis,
                ).operationId
        }
        return operationIds
    }

    @Transaction
    suspend fun mutateTransactionDeletionsV2(
        syncScopeKey: String,
        inputs: List<TransactionDeleteInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<String> {
        SyncScopeKey.requireUserScope(syncScopeKey)
        return inputs.map { input ->
            val result =
                mutateTransactionKeepingTagsV2(
                    syncScopeKey = syncScopeKey,
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
        syncScopeKey: String,
        inputs: List<BudgetMutationInputV2>,
        operationIdFactory: () -> String,
        nowEpochMillis: Long,
    ): List<V2EnqueueResult> {
        SyncScopeKey.requireUserScope(syncScopeKey)
        if (inputs.isEmpty()) return emptyList()

        val ids = inputs.map { it.entity.id }
        require(ids.all { it.isNotBlank() }) { "Toplu bütçe mutasyonunda boş bütçe kimliği bulunamaz." }
        require(ids.distinct().size == inputs.size) {
            "Toplu bütçe mutasyonunda yinelenen bütçe kimliği bulundu: $ids"
        }

        return inputs.map { input ->
            mutateBudgetV2(
                syncScopeKey = syncScopeKey,
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
        SyncScopeKey.requireUserScope(command.outboxOperation.syncScopeKey)
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
        val updatedRows =
            advanceRecurringLastGeneratedDate(
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

        val occurrence =
            RecurringTransactionOccurrenceEntity(
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

internal fun canSafelyHardDeletePendingOrCreate(tail: SyncOperationEntity?): Boolean {
    if (tail == null || tail.protocolVersion != 2) return false
    if (tail.operationTypeCode != OutboxOperationType.CREATE.name || tail.predecessorOperationId != null) return false
    return (tail.attemptCount == 0 && tail.statusCode == "PENDING") || tail.isDefinitiveRejection()
}

internal fun canSafelyReplaceRejectedCreate(tail: SyncOperationEntity?): Boolean {
    if (tail == null || tail.protocolVersion != 2) return false
    if (tail.operationTypeCode != OutboxOperationType.CREATE.name || tail.predecessorOperationId != null) return false
    return tail.isDefinitiveRejection()
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

data class BackupCategoryCreateInputV1(
    val entity: CategoryEntity,
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
