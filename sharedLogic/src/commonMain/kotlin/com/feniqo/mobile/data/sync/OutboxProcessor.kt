package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import kotlinx.coroutines.CancellationException

/** Outbox'ın kalıcı sırasını koruyarak tek tek gönderilmesini sağlayan ortak senkronizasyon çekirdeği. */
class OutboxProcessor(
    private val queue: OutboxQueue,
    private val executor: OutboxOperationExecutor,
) {
    suspend fun processReadyOperations(limit: Int = DEFAULT_BATCH_SIZE): OutboxProcessResult {
        var succeeded = 0
        var failedOperationId: String? = null
        var conflictOperationId: String? = null
        var lastError: Throwable? = null

        for (operation in queue.readyOperations(limit)) {
            if (!queue.markInFlight(operation.operationId)) continue
            try {
                executor.execute(operation)
                if (queue.markSucceeded(operation.operationId)) succeeded++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (conflict: OutboxConflictException) {
                queue.markConflict(operation.operationId, conflict.message.orEmpty())
                conflictOperationId = operation.operationId
                break
            } catch (error: Throwable) {
                queue.recordFailure(operation.operationId, error.message.orEmpty())
                failedOperationId = operation.operationId
                lastError = error
                break
            }
        }
        return OutboxProcessResult(succeeded, failedOperationId, conflictOperationId, lastError)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
    }
}

data class OutboxProcessResult(
    val succeededCount: Int,
    val failedOperationId: String?,
    val conflictOperationId: String? = null,
    val lastError: Throwable? = null,
)

class OutboxConflictException(message: String) : IllegalStateException(message)

fun interface OutboxOperationExecutor {
    /** İşlem başarılı sayılmadan önce uzak kaynağın isteği kabul ettiğini doğrular. */
    suspend fun execute(operation: SyncOperationEntity)
}

/** İşleyiciyi Room uygulamasından ayırır; testte yan etkisiz sahte kuyruk kullanılabilir. */
interface OutboxQueue {
    suspend fun readyOperations(limit: Int): List<SyncOperationEntity>
    suspend fun markInFlight(operationId: String): Boolean
    suspend fun markSucceeded(operationId: String): Boolean
    suspend fun recordFailure(operationId: String, errorMessage: String): Boolean
    suspend fun markConflict(operationId: String, errorMessage: String): Boolean
}

class RoomOutboxQueue(
    private val delegate: OfflineWriteQueue,
) : OutboxQueue {
    override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> = delegate.getReadyOperations(limit)
    override suspend fun markInFlight(operationId: String): Boolean = delegate.markInFlight(operationId)
    override suspend fun markSucceeded(operationId: String): Boolean = delegate.markSucceeded(operationId)
    override suspend fun recordFailure(operationId: String, errorMessage: String): Boolean =
        delegate.recordFailure(operationId, errorMessage)
    override suspend fun markConflict(operationId: String, errorMessage: String): Boolean =
        delegate.markConflict(operationId, errorMessage)
}
