package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity

/** Yerel entity ile outbox kaydını aynı Room transaction içinde yazar. */
@Dao
interface LocalMutationDao {
    @Upsert suspend fun upsertProfileRow(entity: UserProfileEntity)
    @Upsert suspend fun upsertWorkspaceRow(entity: WorkspaceEntity)
    @Upsert suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)
    @Upsert suspend fun upsertCategoryRow(entity: CategoryEntity)
    @Upsert suspend fun upsertBudgetRow(entity: BudgetEntity)
    @Upsert suspend fun upsertTransactionRow(entity: TransactionEntity)
    @Upsert suspend fun upsertTagRows(entities: List<TagEntity>)
    @Upsert suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun deleteTransactionTagRows(transactionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxRow(operation: SyncOperationEntity)

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
}
