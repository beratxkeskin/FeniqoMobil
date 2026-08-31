package com.feniqo.mobile.data.local.outbox

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit
import com.feniqo.mobile.data.local.dao.TransactionMutationUnit
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.repository.SyncEntityType

import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock

data class TransactionCreateInput(
    val entity: TransactionEntity,
    val tags: List<TagEntity> = emptyList(),
    val tagLinks: List<TransactionTagCrossRef> = emptyList(),
)

enum class OutboxOperationType {
    CREATE,
    UPDATE,
    DELETE,
}

enum class OutboxStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
    CONFLICT,
}

/**
 * Offline mutasyonları önce Room'a yazar ve aynı transaction içinde kalıcı outbox'a ekler.
 * Başarılı yazma sonrasında BackgroundSyncScheduler üzerinden arka plan senkronizasyonunu tetikler.
 */
class OfflineWriteQueue(
    private val mutationDao: LocalMutationDao,
    private val operationDao: SyncOperationDao,
    private val syncScheduler: BackgroundSyncScheduler? = null,
    private val nowEpochMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val operationIdFactory: () -> String = ::newOperationId,
) {
    fun observePendingCount(): Flow<Int> = operationDao.observePendingCount()
    fun observeFailedCount(): Flow<Int> = operationDao.observeFailedCount()

    suspend fun enqueueProfile(entity: UserProfileEntity, type: OutboxOperationType): String =
        enqueue(entity.id, SyncEntityType.PROFILE, entity.sync, type) { operation ->
            mutationDao.upsertProfileAndEnqueue(entity, operation)
        }

    suspend fun enqueueWorkspace(
        entity: WorkspaceEntity,
        members: List<WorkspaceMemberEntity>,
        type: OutboxOperationType,
    ): String = enqueue(entity.id, SyncEntityType.WORKSPACE, entity.sync, type) { operation ->
        mutationDao.upsertWorkspaceAndEnqueue(entity, members, operation)
    }

    suspend fun enqueueCategory(entity: CategoryEntity, type: OutboxOperationType): String =
        enqueue(entity.id, SyncEntityType.CATEGORY, entity.sync, type) { operation ->
            mutationDao.upsertCategoryAndEnqueue(entity, operation)
        }

    suspend fun enqueueBudget(entity: BudgetEntity, type: OutboxOperationType): String =
        enqueue(entity.id, SyncEntityType.BUDGET, entity.sync, type) { operation ->
            mutationDao.upsertBudgetAndEnqueue(entity, operation)
        }

    suspend fun enqueueTransaction(
        entity: TransactionEntity,
        tags: List<TagEntity>,
        tagLinks: List<TransactionTagCrossRef>,
        type: OutboxOperationType,
    ): String = enqueue(entity.id, SyncEntityType.TRANSACTION, entity.sync, type) { operation ->
        mutationDao.upsertTransactionAndEnqueue(entity, tags, tagLinks, operation)
    }

    suspend fun enqueueTransactionKeepingTags(
        entity: TransactionEntity,
        type: OutboxOperationType,
    ): String = enqueue(entity.id, SyncEntityType.TRANSACTION, entity.sync, type) { operation ->
        mutationDao.upsertTransactionKeepingTagsAndEnqueue(entity, operation)
    }

    suspend fun enqueueTransactionCreates(
        inputs: List<TransactionCreateInput>,
    ): List<String> {
        require(inputs.isNotEmpty()) { "İşlem listesi boş olamaz." }

        val entityIds = inputs.map { it.entity.id }
        require(entityIds.all { it.isNotBlank() }) { "İşlem kimliği boş olamaz." }
        require(entityIds.distinct().size == inputs.size) { "İşlem kimlikleri benzersiz olmalıdır." }

        for (input in inputs) {
            val sync = input.entity.sync
            require(sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                "Yeni işlem durumu PENDING_CREATE olmalıdır."
            }
            require(sync.version == 0L) {
                "Yeni işlem versiyonu 0 olmalıdır."
            }
            require(sync.baseVersion == null) {
                "Yeni işlem baseVersion değeri null olmalıdır."
            }
            require(sync.deletedAtEpochMillis == null) {
                "Yeni işlem silinmiş olamaz."
            }
            for (link in input.tagLinks) {
                require(link.transactionId == input.entity.id) {
                    "Etiket bağlantısı işlem kimliği ile eşleşmelidir."
                }
            }
        }

        val operationIds = inputs.map { operationIdFactory() }
        require(operationIds.all { it.isNotBlank() }) { "Outbox işlem kimliği boş olamaz." }
        require(operationIds.distinct().size == inputs.size) { "Outbox işlem kimlikleri benzersiz olmalıdır." }

        val now = nowEpochMillisProvider()
        val units = inputs.zip(operationIds).map { (input, opId) ->
            val operation = SyncOperationEntity(
                operationId = opId,
                entityTypeCode = SyncEntityType.TRANSACTION.name,
                entityId = input.entity.id,
                operationTypeCode = OutboxOperationType.CREATE.name,
                baseVersion = null,
                statusCode = OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = now,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            TransactionMutationUnit(
                entity = input.entity,
                tags = input.tags,
                tagLinks = input.tagLinks,
                operation = operation,
            )
        }

        mutationDao.upsertTransactionsAndEnqueue(units)
        syncScheduler?.scheduleOutboxSync()
        return operationIds
    }

    suspend fun enqueueTransactionDeletions(
        entities: List<TransactionEntity>,
    ): List<String> {
        require(entities.isNotEmpty()) { "Silinecek transaction listesi boş olamaz." }

        val entityIds = entities.map { it.id }
        require(entityIds.all { it.isNotBlank() }) { "Transaction kimlikleri boş olamaz." }
        require(entityIds.distinct().size == entities.size) { "Silinecek transaction kimlikleri benzersiz olmalıdır." }

        for (entity in entities) {
            require(entity.sync.syncStatus == SyncStatus.PENDING_DELETE.name) {
                "Yalnız PENDING_DELETE durumundaki kayıtlar silme kuyruğuna alınabilir: ${entity.id}"
            }
            require(entity.sync.deletedAtEpochMillis != null) {
                "Silinecek kaydın deletedAtEpochMillis değeri zorunludur: ${entity.id}"
            }
            require(entity.sync.version >= 0L) {
                "Version negatif olamaz: ${entity.id}"
            }
            val baseVersion = entity.sync.baseVersion
            if (baseVersion != null) {
                require(baseVersion >= 0L) {
                    "Base version negatif olamaz: ${entity.id}"
                }
            } else {
                require(entity.sync.version == 0L) {
                    "Henüz sunucuya gitmemiş (baseVersion=null) kaydın version değeri 0 olmalıdır: ${entity.id}"
                }
            }
        }

        val operationIds = entities.map { operationIdFactory() }
        require(operationIds.all { it.isNotBlank() }) { "Outbox işlem kimliği boş olamaz." }
        require(operationIds.distinct().size == entities.size) { "Outbox işlem kimlikleri benzersiz olmalıdır." }

        val now = nowEpochMillisProvider()
        val units = entities.mapIndexed { index, entity ->
            val operation = SyncOperationEntity(
                operationId = operationIds[index],
                entityTypeCode = SyncEntityType.TRANSACTION.name,
                entityId = entity.id,
                operationTypeCode = OutboxOperationType.DELETE.name,
                baseVersion = entity.sync.baseVersion,
                statusCode = OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = now,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            TransactionKeepingTagsMutationUnit(
                entity = entity,
                operation = operation,
            )
        }

        mutationDao.upsertTransactionsKeepingTagsAndEnqueue(units)
        syncScheduler?.scheduleOutboxSync()
        return operationIds
    }

    suspend fun getReadyOperations(limit: Int = DEFAULT_BATCH_SIZE): List<SyncOperationEntity> {
        require(limit in 1..MAXIMUM_BATCH_SIZE) { "Outbox batch boyutu 1-$MAXIMUM_BATCH_SIZE aralığında olmalıdır." }
        val now = nowEpochMillisProvider()
        operationDao.recoverStaleInFlight(
            staleBeforeEpochMillis = now - IN_FLIGHT_LEASE_MILLIS,
            nowEpochMillis = now,
            lastError = INTERRUPTED_ERROR,
        )
        return operationDao.getReadyOperations(now, limit)
    }

    suspend fun claimNextReadyOperation(): SyncOperationEntity? {
        val now = nowEpochMillisProvider()
        operationDao.recoverStaleInFlight(
            staleBeforeEpochMillis = now - IN_FLIGHT_LEASE_MILLIS,
            nowEpochMillis = now,
            lastError = INTERRUPTED_ERROR,
        )
        val readyOperations = operationDao.getReadyOperations(now, 1)
        if (readyOperations.isEmpty()) return null

        val candidate = readyOperations.first()
        val claimed = operationDao.claimOperation(candidate.operationId, now)
        if (claimed != 1) return null

        return candidate.copy(
            statusCode = OutboxStatus.IN_FLIGHT.name,
            attemptCount = candidate.attemptCount + 1,
            updatedAtEpochMillis = now,
        )
    }

    suspend fun claimOperation(operationId: String): SyncOperationEntity? {
        val now = nowEpochMillisProvider()
        val claimed = operationDao.claimOperation(operationId, now)
        if (claimed != 1) return null
        return operationDao.getById(operationId)
    }

    suspend fun markInFlight(operationId: String): Boolean =
        operationDao.claimOperation(operationId, nowEpochMillisProvider()) == 1

    suspend fun markSucceeded(operationId: String): Boolean =
        operationDao.deleteCompleted(operationId) == 1

    suspend fun ackV2Execution(
        operationId: String,
        result: com.feniqo.mobile.data.sync.OutboxExecutionResult,
    ): Boolean = mutationDao.ackV2Execution(
        operationId = operationId,
        result = result,
        nowEpochMillis = nowEpochMillisProvider(),
    )

    suspend fun recordV2Conflict(
        conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity,
    ): Boolean = mutationDao.recordV2Conflict(
        conflict = conflict,
        nowEpochMillis = nowEpochMillisProvider(),
    )

    suspend fun resolvePredecessorSuccessor(
        predecessorOperationId: String,
        appliedVersion: Long,
    ): Boolean = mutationDao.resolvePredecessorSuccessor(
        predecessorOperationId = predecessorOperationId,
        appliedVersion = appliedVersion,
        nowEpochMillis = nowEpochMillisProvider(),
    )

    suspend fun recordFailure(operationId: String, errorMessage: String): Boolean {
        val operation = operationDao.getById(operationId) ?: return false
        val attemptCount = operation.attemptCount.coerceAtLeast(1)
        val now = nowEpochMillisProvider()
        val nextAttemptAt = SyncRetryPolicy.nextAttemptAt(now, attemptCount)
        val safeError = errorMessage.trim().ifEmpty { UNKNOWN_ERROR }.take(MAXIMUM_ERROR_LENGTH)

        return operationDao.markFailed(
            operationId = operationId,
            lastError = safeError,
            nextAttemptAtEpochMillis = nextAttemptAt,
            nowEpochMillis = now,
        ) == 1
    }

    suspend fun markConflict(operationId: String, errorMessage: String): Boolean {
        val safeError = errorMessage.trim().ifEmpty { CONFLICT_ERROR }.take(MAXIMUM_ERROR_LENGTH)
        return operationDao.markConflict(operationId, safeError, nowEpochMillisProvider()) == 1
    }

    suspend fun retryAllFailed(): Int = operationDao.retryAllFailed(nowEpochMillisProvider())

    suspend fun enqueueProfileV2(
        entity: UserProfileEntity,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        require(type != OutboxOperationType.DELETE) { "Profil silme işlemi desteklenmemektedir." }
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateProfileV2(
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueCategoryV2(
        entity: CategoryEntity,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateCategoryV2(
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueBudgetV2(
        entity: BudgetEntity,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateBudgetV2(
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueRecurringTransactionV2(
        entity: RecurringTransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateRecurringTransactionV2(
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueBudgetsV2(
        inputs: List<com.feniqo.mobile.data.local.dao.BudgetMutationInputV2>,
    ): List<com.feniqo.mobile.data.local.dao.V2EnqueueResult> {
        if (inputs.isEmpty()) return emptyList()

        val entityIds = inputs.map { it.entity.id }
        require(entityIds.all { it.isNotBlank() }) { "Bütçe kimliği boş olamaz." }
        require(entityIds.distinct().size == inputs.size) {
            "Toplu bütçe mutasyonunda yinelenen bütçe kimliği bulundu: $entityIds"
        }

        for (input in inputs) {
            validateMutation(input.entity.sync, input.type)
            require(input.payloadJson.isNotBlank()) { "Payload JSON boş olamaz: ${input.entity.id}" }
        }

        val results = mutationDao.mutateBudgetsV2(
            inputs = inputs,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )

        val hasNonHardDeleted = results.any { it.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED }
        if (hasNonHardDeleted) {
            syncScheduler?.scheduleOutboxSync()
        }

        return results
    }

    suspend fun enqueueTransactionV2(
        entity: TransactionEntity,
        tags: List<TagEntity>,
        tagLinks: List<TransactionTagCrossRef>,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateTransactionV2(
            entity = entity,
            tags = tags,
            tagLinks = tagLinks,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueTransactionKeepingTagsV2(
        entity: TransactionEntity,
        type: OutboxOperationType,
        payloadJson: String,
    ): String {
        validateMutation(entity.sync, type)
        val result = mutationDao.mutateTransactionKeepingTagsV2(
            entity = entity,
            type = type,
            payloadJson = payloadJson,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        if (result.decision != com.feniqo.mobile.data.local.dao.V2EnqueueDecision.HARD_DELETED) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result.operationId
    }

    suspend fun enqueueRecurringTransactionOccurrenceV2(
        recurringTransactionId: String,
        expectedPreviousLastGeneratedDate: String?,
        dueDate: String,
        entity: TransactionEntity,
        payloadJson: String,
    ): com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceResult {
        require(payloadJson.isNotBlank()) { "Tekrar işlem outbox payloadJson boş olamaz." }
        validateMutation(entity.sync, OutboxOperationType.CREATE)

        val now = nowEpochMillisProvider()
        val opId = operationIdFactory()
        com.feniqo.mobile.data.local.dao.validateOperationId(opId)

        val outboxOp = SyncOperationEntity(
            operationId = opId,
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
            nextAttemptAtEpochMillis = now,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        val command = com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceCommand(
            recurringTransactionId = recurringTransactionId,
            expectedPreviousLastGeneratedDate = expectedPreviousLastGeneratedDate,
            dueDate = dueDate,
            transactionEntity = entity,
            tags = emptyList(),
            tagLinks = emptyList(),
            outboxOperation = outboxOp,
        )

        val result = mutationDao.generateRecurringOccurrence(command, nowEpochMillis = now)
        if (result is com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceResult.Created) {
            syncScheduler?.scheduleOutboxSync()
        }
        return result
    }

    suspend fun enqueueTransactionCreatesV2(
        inputs: List<com.feniqo.mobile.data.local.dao.TransactionCreateInputV2>,
    ): List<String> {
        require(inputs.isNotEmpty()) { "İşlem listesi boş olamaz." }

        val entityIds = inputs.map { it.entity.id }
        require(entityIds.all { it.isNotBlank() }) { "İşlem kimliği boş olamaz." }
        require(entityIds.distinct().size == inputs.size) { "İşlem kimlikleri benzersiz olmalıdır." }

        for (input in inputs) {
            val sync = input.entity.sync
            validateMutation(sync, OutboxOperationType.CREATE)
            require(sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                "Yeni işlem durumu PENDING_CREATE olmalıdır."
            }
            require(sync.version == 0L) {
                "Yeni işlem versiyonu 0 olmalıdır."
            }
            require(sync.baseVersion == null) {
                "Yeni işlem baseVersion değeri null olmalıdır."
            }
            require(sync.deletedAtEpochMillis == null) {
                "Yeni işlem silinmiş olamaz."
            }
            require(input.payloadJson.isNotBlank()) {
                "Payload JSON boş olamaz: ${input.entity.id}"
            }
            for (link in input.tagLinks) {
                require(link.transactionId == input.entity.id) {
                    "Etiket bağlantısı işlem kimliği ile eşleşmelidir."
                }
            }
        }

        val operationIds = mutationDao.mutateTransactionCreatesV2(
            inputs = inputs,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        syncScheduler?.scheduleOutboxSync()
        return operationIds
    }

    suspend fun enqueueTransactionDeletionsV2(
        inputs: List<com.feniqo.mobile.data.local.dao.TransactionDeleteInputV2>,
    ): List<String> {
        require(inputs.isNotEmpty()) { "Silinecek transaction listesi boş olamaz." }

        val entityIds = inputs.map { it.entity.id }
        require(entityIds.all { it.isNotBlank() }) { "Transaction kimlikleri boş olamaz." }
        require(entityIds.distinct().size == inputs.size) { "Silinecek transaction kimlikleri benzersiz olmalıdır." }

        for (input in inputs) {
            val sync = input.entity.sync
            validateMutation(sync, OutboxOperationType.DELETE)
            require(sync.syncStatus == SyncStatus.PENDING_DELETE.name) {
                "Yalnız PENDING_DELETE durumundaki kayıtlar silme kuyruğuna alınabilir: ${input.entity.id}"
            }
            require(sync.deletedAtEpochMillis != null) {
                "Silinecek kaydın deletedAtEpochMillis değeri zorunludur: ${input.entity.id}"
            }
            require(input.payloadJson.isNotBlank()) {
                "Payload JSON boş olamaz: ${input.entity.id}"
            }
        }

        val operationIds = mutationDao.mutateTransactionDeletionsV2(
            inputs = inputs,
            operationIdFactory = operationIdFactory,
            nowEpochMillis = nowEpochMillisProvider(),
        )
        syncScheduler?.scheduleOutboxSync()
        return operationIds
    }

    private suspend fun enqueue(
        entityId: String,
        entityType: SyncEntityType,
        sync: SyncMetadata,
        type: OutboxOperationType,
        writer: suspend (SyncOperationEntity) -> Unit,
    ): String {
        validateMutation(sync, type)
        val now = nowEpochMillisProvider()
        val operationId = operationIdFactory()
        require(operationId.isNotBlank()) { "Outbox işlem kimliği boş olamaz." }

        writer(
            SyncOperationEntity(
                operationId = operationId,
                entityTypeCode = entityType.name,
                entityId = entityId,
                operationTypeCode = type.name,
                baseVersion = sync.baseVersion,
                statusCode = OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = now,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        syncScheduler?.scheduleOutboxSync()
        return operationId
    }

    private fun validateMutation(sync: SyncMetadata, type: OutboxOperationType) {
        val status = SyncStatus.valueOf(sync.syncStatus)
        require(status != SyncStatus.SYNCED) { "Yerel mutasyon kuyruğa eklenmeden önce pending olmalıdır." }

        if (type == OutboxOperationType.DELETE) {
            require(sync.deletedAtEpochMillis != null) { "Silme işlemi soft-delete zamanı taşımalıdır." }
        } else {
            require(sync.deletedAtEpochMillis == null) { "Silinmiş entity create/update olarak kuyruğa eklenemez." }
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val MAXIMUM_BATCH_SIZE = 100
        const val MAXIMUM_ERROR_LENGTH = 1_000
        const val UNKNOWN_ERROR = "Bilinmeyen senkronizasyon hatası"
        const val CONFLICT_ERROR = "Senkronizasyon çakışması kullanıcı kararı bekliyor"
        const val INTERRUPTED_ERROR = "Önceki senkronizasyon tamamlanmadan kesildi"
        const val IN_FLIGHT_LEASE_MILLIS = 10 * 60 * 1_000L
    }
}

/** UUID deneysel API'sine bağlanmadan 128 bit KMP uyumlu benzersiz işlem kimliği üretir. */
private fun newOperationId(): String = buildString(capacity = 32) {
    Random.Default.nextBytes(16).forEach { byte ->
        append(HEX_DIGITS[(byte.toInt() ushr 4) and 0x0F])
        append(HEX_DIGITS[byte.toInt() and 0x0F])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

/** Başarısız denemeleri 15 saniyeden 6 saate kadar üssel olarak geri çeker. */
object SyncRetryPolicy {
    private const val BASE_DELAY_MILLIS = 15_000L
    private const val MAXIMUM_DELAY_MILLIS = 6 * 60 * 60 * 1_000L
    private const val MAXIMUM_SHIFT = 16

    fun delayMillis(attemptCount: Int): Long {
        require(attemptCount >= 1) { "Deneme sayısı en az 1 olmalıdır." }
        val multiplier = 1L shl (attemptCount - 1).coerceAtMost(MAXIMUM_SHIFT)
        return min(BASE_DELAY_MILLIS * multiplier, MAXIMUM_DELAY_MILLIS)
    }

    fun nextAttemptAt(nowEpochMillis: Long, attemptCount: Int): Long {
        val delay = delayMillis(attemptCount)
        return if (Long.MAX_VALUE - nowEpochMillis < delay) Long.MAX_VALUE else nowEpochMillis + delay
    }
}
