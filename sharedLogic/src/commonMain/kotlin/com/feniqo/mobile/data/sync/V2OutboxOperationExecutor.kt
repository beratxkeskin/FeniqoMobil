package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.codec.WorkspaceMembershipPayloadCodec
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.AssetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
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

    private inline fun <reified T> parseAndValidateDto(
        payloadJson: String,
        operation: SyncOperationEntity,
        idExtractor: (T) -> String,
    ): T {
        val dto = try {
            json.decodeFromString<T>(payloadJson)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SerializationException) {
            throw DefinitiveOutboxFailureException(
                message = "invalid_local_outbox_payload"
            )
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException(
                message = "invalid_local_outbox_payload"
            )
        }
        val dtoId = try {
            idExtractor(dto)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw DefinitiveOutboxFailureException(
                message = "invalid_local_outbox_contract"
            )
        }
        if (dtoId != operation.entityId) {
            throw DefinitiveOutboxFailureException(
                message = "Payload entity ID'si işlem entity ID'si ile uyuşmuyor (payload_entity_id_mismatch)"
            )
        }
        return dto
    }

    suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult {
        if (operation.protocolVersion != 2) {
            throw DefinitiveOutboxFailureException(
                "invalid_protocol_version"
            )
        }
        val payloadJson = operation.payloadJson
        if (payloadJson.isNullOrBlank()) {
            throw DefinitiveOutboxFailureException(
                "missing_outbox_payload"
            )
        }

        val writeOp = try {
            RemoteWriteOperation.valueOf(operation.operationTypeCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException(
                message = "invalid_operation_type"
            )
        }

        when (writeOp) {
            RemoteWriteOperation.CREATE -> {
                if (operation.entityTypeCode !in listOf("GOAL_CONTRIBUTION", "DEBT_PAYMENT")) {
                    if (operation.baseVersion != null) {
                        throw DefinitiveOutboxFailureException(
                            "invalid_base_version_for_create"
                        )
                    }
                }
            }
            RemoteWriteOperation.UPDATE, RemoteWriteOperation.DELETE -> {
                if (operation.baseVersion == null) {
                    throw DefinitiveOutboxFailureException(
                        "missing_base_version"
                    )
                }
            }
        }

        if (operation.entityTypeCode == "ASSET") {
            return executeAsset(operation, writeOp, payloadJson)
        }

        val entityType = try {
            SyncEntityType.valueOf(operation.entityTypeCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException(
                message = "unsupported_entity_type"
            )
        }

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
            SyncEntityType.WORKSPACE_MEMBER -> executeWorkspaceMember(operation, writeOp, payloadJson)
            SyncEntityType.WORKSPACE_INVITATION -> executeWorkspaceInvitation(operation, writeOp, payloadJson)
        }
    }

    private suspend fun executeAsset(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = parseAndValidateDto<AssetDto>(payloadJson, operation) { it.id }
        return when (val result = writer.writeAsset(operation.operationId, writeOp, operation.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.AssetApplied(result.record)
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
                if (writeOp == RemoteWriteOperation.DELETE) OutboxExecutionResult.MissingDeleteAcknowledged
                else error("Varlık koşullu yazma sırasında bulunamadı.")
            }
        }
    }



    private suspend fun executeProfile(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        val dto = parseAndValidateDto<ProfileDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<CategoryDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<TransactionDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<BudgetDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<RecurringTransactionDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<SubscriptionDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<GoalDto>(payloadJson, operation) { it.id }

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
        if (writeOp != RemoteWriteOperation.CREATE) {
            throw DefinitiveOutboxFailureException("GOAL_CONTRIBUTION için yalnız CREATE işlemi desteklenir.")
        }
        val dto = parseAndValidateDto<GoalContributionDto>(payloadJson, operation) { it.id }

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
        val dto = parseAndValidateDto<DebtDto>(payloadJson, operation) { it.id }

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
        if (writeOp != RemoteWriteOperation.CREATE) {
            throw DefinitiveOutboxFailureException("DEBT_PAYMENT için yalnız CREATE işlemi desteklenir.")
        }
        val dto = parseAndValidateDto<DebtPaymentDto>(payloadJson, operation) { it.id }

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
        val validatedPayload = try {
            WorkspacePayloadCodec.parseAndValidate(
                operationId = operation.operationId,
                entityId = operation.entityId,
                operation = writeOp,
                baseVersion = operation.baseVersion,
                payloadJson = payloadJson,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SerializationException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_payload")
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_payload")
        } catch (e: IllegalStateException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_payload")
        } catch (e: RemoteMappingException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_payload")
        }

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

    private suspend fun executeWorkspaceMember(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        if (writeOp !in setOf(RemoteWriteOperation.UPDATE, RemoteWriteOperation.DELETE)) {
            throw DefinitiveOutboxFailureException(
                "WORKSPACE_MEMBER için generic outbox CREATE/JOIN işlemi desteklenmez (unsupported_workspace_member_operation)"
            )
        }
        val validatedPayload = try {
            WorkspaceMembershipPayloadCodec.parseAndValidateMember(
                operationId = operation.operationId,
                entityId = operation.entityId,
                operation = writeOp,
                baseVersion = operation.baseVersion,
                payloadJson = payloadJson,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SerializationException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_member_payload")
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_member_payload")
        } catch (e: IllegalStateException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_member_payload")
        } catch (e: RemoteMappingException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_member_payload")
        }

        return when (val result = writer.writeWorkspaceMember(operation.operationId, writeOp, operation.baseVersion, validatedPayload)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.WorkspaceMemberApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                if (writeOp == RemoteWriteOperation.DELETE) {
                    OutboxExecutionResult.MissingDeleteAcknowledged
                } else {
                    error("Workspace üyesi koşullu yazma sırasında bulunamadı (NOT_FOUND): ${operation.entityId}")
                }
            }
        }
    }

    private suspend fun executeWorkspaceInvitation(
        operation: SyncOperationEntity,
        writeOp: RemoteWriteOperation,
        payloadJson: String,
    ): OutboxExecutionResult {
        if (writeOp != RemoteWriteOperation.CREATE) {
            throw DefinitiveOutboxFailureException(
                "WORKSPACE_INVITATION için yalnız CREATE işlemi desteklenir (unsupported_workspace_invitation_operation)"
            )
        }
        val validatedPayload = try {
            WorkspaceMembershipPayloadCodec.parseAndValidateInvitation(
                operationId = operation.operationId,
                entityId = operation.entityId,
                operation = writeOp,
                baseVersion = operation.baseVersion,
                payloadJson = payloadJson,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SerializationException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_invitation_payload")
        } catch (e: IllegalArgumentException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_invitation_payload")
        } catch (e: IllegalStateException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_invitation_payload")
        } catch (e: RemoteMappingException) {
            throw DefinitiveOutboxFailureException("invalid_workspace_invitation_payload")
        }

        return when (val result = writer.writeWorkspaceInvitation(operation.operationId, writeOp, operation.baseVersion, validatedPayload)) {
            is ConditionalRemoteWriteResult.Applied -> OutboxExecutionResult.WorkspaceInvitationApplied(result.record)
            is ConditionalRemoteWriteResult.Conflict -> OutboxExecutionResult.ConflictDetected(
                SyncConflictEntity(
                    entityTypeCode = operation.entityTypeCode,
                    entityId = operation.entityId,
                    operationId = operation.operationId,
                    localVersion = operation.baseVersion ?: 0L,
                    remoteVersion = result.remoteRecord.version,
                    localPayloadJson = payloadJson,
                    remotePayloadJson = json.encodeToString(result.remoteRecord),
                    detectedAtEpochMillis = nowEpochMillisProvider(),
                ),
            )
            ConditionalRemoteWriteResult.NotFound -> {
                error("Workspace daveti koşullu yazma sırasında bulunamadı (NOT_FOUND): ${operation.entityId}")
            }
        }
    }
}
