package com.feniqo.mobile.data.local.outbox

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
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
import kotlinx.coroutines.flow.Flow
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock

enum class OutboxOperationType {
    CREATE,
    UPDATE,
    DELETE,
}

enum class OutboxStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
}

/**
 * Offline mutasyonları önce Room'a yazar ve aynı transaction içinde kalıcı outbox'a ekler.
 * Uzak veri kaynağı bu kuyruğu 5.x adımında tüketecektir.
 */
class OfflineWriteQueue(
    private val mutationDao: LocalMutationDao,
    private val operationDao: SyncOperationDao,
    private val nowEpochMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val operationIdFactory: () -> String = ::newOperationId,
) {
    fun observePendingCount(): Flow<Int> = operationDao.observePendingCount()

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

    suspend fun getReadyOperations(limit: Int = DEFAULT_BATCH_SIZE): List<SyncOperationEntity> {
        require(limit in 1..MAXIMUM_BATCH_SIZE) { "Outbox batch boyutu 1-$MAXIMUM_BATCH_SIZE aralığında olmalıdır." }
        return operationDao.getReadyOperations(nowEpochMillisProvider(), limit)
    }

    suspend fun markInFlight(operationId: String): Boolean =
        operationDao.markInFlight(operationId, nowEpochMillisProvider()) == 1

    suspend fun markSucceeded(operationId: String): Boolean =
        operationDao.deleteCompleted(operationId) == 1

    suspend fun recordFailure(operationId: String, errorMessage: String): Boolean {
        val operation = operationDao.getById(operationId) ?: return false
        val attemptCount = operation.attemptCount + 1
        val now = nowEpochMillisProvider()
        val nextAttemptAt = SyncRetryPolicy.nextAttemptAt(now, attemptCount)
        val safeError = errorMessage.trim().ifEmpty { UNKNOWN_ERROR }.take(MAXIMUM_ERROR_LENGTH)

        return operationDao.markFailed(
            operationId = operationId,
            attemptCount = attemptCount,
            lastError = safeError,
            nextAttemptAtEpochMillis = nextAttemptAt,
            nowEpochMillis = now,
        ) == 1
    }

    suspend fun retryAllFailed(): Int = operationDao.retryAllFailed(nowEpochMillisProvider())

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
