package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * V2 profile/category/transaction/budget/recurring_transaction outbox kayıtlarını immutable payload snapshot üzerinden
 * sync_write_v2 RPC'si ile yürütür. Güncel Room entity'sini asla yeniden okumaz ve Room'a
 * doğrudan yazma yan etkisi üretmez.
 */
class V2OutboxOperationExecutor(
    private val writer: IdempotentConditionalRemoteWriter,
    private val nowEpochMillisProvider: () -> Long,
) {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult {
        require(operation.protocolVersion == 2) {
            "V2 executor yalnızca protocolVersion=2 kabul eder. Alınan: ${operation.protocolVersion}"
        }
        val payloadJson = operation.payloadJson
        require(!payloadJson.isNullOrBlank()) {
            "V2 outbox işlemi payload_json taşımalıdır: ${operation.operationId}"
        }

        val writeOp = RemoteWriteOperation.valueOf(operation.operationTypeCode)
        when (writeOp) {
            RemoteWriteOperation.CREATE -> require(operation.baseVersion == null) {
                "CREATE işlemi için baseVersion null olmalıdır: ${operation.operationId}"
            }
            RemoteWriteOperation.UPDATE, RemoteWriteOperation.DELETE -> require(operation.baseVersion != null) {
                "${writeOp.name} işlemi için baseVersion null olamaz: ${operation.operationId}"
            }
        }

        val entityType = runCatching { SyncEntityType.valueOf(operation.entityTypeCode) }
            .getOrElse { error("Desteklenmeyen entity type: ${operation.entityTypeCode}") }

        return when (entityType) {
            SyncEntityType.PROFILE -> executeProfile(operation, writeOp, payloadJson)
            SyncEntityType.CATEGORY -> executeCategory(operation, writeOp, payloadJson)
            SyncEntityType.TRANSACTION -> executeTransaction(operation, writeOp, payloadJson)
            SyncEntityType.BUDGET -> executeBudget(operation, writeOp, payloadJson)
            SyncEntityType.RECURRING_TRANSACTION -> executeRecurringTransaction(operation, writeOp, payloadJson)
            else -> error("V2 outbox henüz ${operation.entityTypeCode} türünü desteklemiyor.")
        }
    }


    private suspend fun executeProfile(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<ProfileDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeProfile(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.ProfileApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("Profil koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeCategory(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<CategoryDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeCategory(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.CategoryApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("Kategori koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeTransaction(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<TransactionDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeTransaction(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.TransactionApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("İşlem koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeBudget(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<BudgetDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeBudget(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.BudgetApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("Bütçe koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeRecurringTransaction(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<RecurringTransactionDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeRecurringTransaction(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.RecurringTransactionApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("Tekrarlayan işlem koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }
}

