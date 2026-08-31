package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity

/**
 * Outbox operasyonunun protokol sürümüne göre doğru yürütücüye (V1 veya V2) yönlendirme yapan birleşik yürütücü.
 */
class ProtocolAwareOutboxOperationExecutor(
    private val v1Executor: V1OutboxOperationExecutor,
    private val v2Executor: V2OutboxOperationExecutor,
) : OutboxOperationExecutor {

    override suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult {
        return when (operation.protocolVersion) {
            1 -> v1Executor.execute(operation)
            2 -> v2Executor.execute(operation)
            else -> throw IllegalArgumentException(
                "Bilinmeyen protokol sürümü: ${operation.protocolVersion}. Beklenen: 1 veya 2 (İşlem: ${operation.operationId})",
            )
        }
    }
}
