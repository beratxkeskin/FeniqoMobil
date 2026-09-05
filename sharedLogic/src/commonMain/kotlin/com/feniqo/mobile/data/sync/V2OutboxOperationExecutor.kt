package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * V2 profile/category/transaction/budget/recurring_transaction/subscription/goal/debt/workspace outbox kayıtlarını immutable payload snapshot üzerinden
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
            RemoteWriteOperation.CREATE -> {
                if (operation.entityTypeCode !in listOf("GOAL_CONTRIBUTION", "DEBT_PAYMENT")) {
                    require(operation.baseVersion == null) {
                        "CREATE işlemi için baseVersion null olmalıdır: ${operation.operationId}"
                    }
                }
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
            SyncEntityType.SUBSCRIPTION -> executeSubscription(operation, writeOp, payloadJson)
            SyncEntityType.GOAL -> executeGoal(operation, writeOp, payloadJson)
            SyncEntityType.GOAL_CONTRIBUTION -> executeGoalContribution(operation, writeOp, payloadJson)
            SyncEntityType.DEBT -> executeDebt(operation, writeOp, payloadJson)
            SyncEntityType.DEBT_PAYMENT -> executeDebtPayment(operation, writeOp, payloadJson)
            SyncEntityType.WORKSPACE -> executeWorkspace(operation, writeOp, payloadJson)
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

    private suspend fun executeSubscription(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<SubscriptionDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeSubscription(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.SubscriptionApplied(result.record)
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
                    error("Abonelik koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeGoal(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<GoalDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeGoal(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.GoalApplied(result.record)
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
                    error("Hedef koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeGoalContribution(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        require(writeOp == RemoteWriteOperation.CREATE) {
            "GOAL_CONTRIBUTION için yalnız CREATE işlemi desteklenir."
        }
        val dto = json.decodeFromString<GoalContributionDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeGoalContribution(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.GoalContributionApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.contribution.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                error("Hedef katkısı eklenirken üst hedef bulunamadı (NOT_FOUND).")
            }
        }
    }

    private suspend fun executeDebt(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = json.decodeFromString<DebtDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeDebt(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.DebtApplied(result.record)
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
                    error("Borç koşullu yazma sırasında bulunamadı (NOT_FOUND).")
                }
            }
        }
    }

    private suspend fun executeDebtPayment(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        require(writeOp == RemoteWriteOperation.CREATE) {
            "DEBT_PAYMENT için yalnız CREATE işlemi desteklenir."
        }
        val dto = json.decodeFromString<DebtPaymentDto>(payloadJson)
        require(dto.id == operation.entityId) {
            "Payload DTO id (${dto.id}) ile outbox entityId (${operation.entityId}) uyuşmuyor."
        }

        return when (val result = writer.writeDebtPayment(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.DebtPaymentApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.payment.version ?: 0L,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                error("Borç ödemesi eklenirken üst borç bulunamadı (NOT_FOUND).")
            }
        }
    }

    private suspend fun executeWorkspace(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val validatedPayload = WorkspacePayloadCodec.parseAndValidate(
            operationId = operation.operationId,
            entityId = operation.entityId,
            operation = writeOp,
            baseVersion = operation.baseVersion,
            payloadJson = payloadJson,
        )

        return when (val result = writer.writeWorkspace(operation.operationId, writeOp, operation.baseVersion, validatedPayload)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.WorkspaceApplied(result.record)
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
                    error("Çalışma alanı koşullu yazma sırasında bulunamadı (NOT_FOUND): ${operation.entityId}")
                }
            }
        }
    }
}

