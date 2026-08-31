package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
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
    @Upsert suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>)
    @Upsert suspend fun upsertCategoryRow(entity: CategoryEntity)
    @Upsert suspend fun upsertBudgetRow(entity: BudgetEntity)
    @Upsert suspend fun upsertTransactionRow(entity: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransactionRow(entity: TransactionEntity)
    @Upsert suspend fun upsertTagRows(entities: List<TagEntity>)
    @Upsert suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>)
    @Upsert suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity)
    @Upsert suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity)

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
