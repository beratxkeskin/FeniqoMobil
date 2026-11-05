package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UserMerchantVerificationV2OutboxExecutorTest {
    @Test
    fun merchant_verification_create_applied_returns_verification_applied() =
        runTest {
            val remoteDto = remoteVerification(version = 1L)
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Applied(remoteDto),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val snapshotJson =
                snapshotJson(
                    id = VERIFICATION_ID,
                    userId = USER_ID,
                    alias = ALIAS,
                    decisionCode = "ASSIGN",
                    merchantId = MERCHANT_ID,
                )
            val op =
                verificationOperation(
                    operationType = "CREATE",
                    baseVersion = null,
                    payloadJson = snapshotJson,
                )

            val result = executor.execute(op)

            assertIs<OutboxExecutionResult.UserMerchantVerificationApplied>(result)
            assertEquals(VERIFICATION_ID, result.record.id)
            assertEquals(1L, result.record.version)
            assertEquals(OP_ID, writer.lastOperationId)
            assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
            assertEquals(null, writer.lastBaseVersion)
            assertEquals(MERCHANT_ID, writer.lastVerificationDto?.merchantId)
        }

    @Test
    fun merchant_verification_update_conflict_returns_conflict_detected() =
        runTest {
            val remoteDto = remoteVerification(version = 4L, decisionCode = "BLOCK", merchantId = null)
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Conflict(remoteDto),
                )
            val executor = V2OutboxOperationExecutor(writer) { 2000L }

            val snapshotJson =
                snapshotJson(
                    id = VERIFICATION_ID,
                    userId = USER_ID,
                    alias = ALIAS,
                    decisionCode = "ASSIGN",
                    merchantId = MERCHANT_ID,
                )
            val op =
                verificationOperation(
                    operationType = "UPDATE",
                    baseVersion = 2L,
                    payloadJson = snapshotJson,
                )

            val result = executor.execute(op)

            assertIs<OutboxExecutionResult.ConflictDetected>(result)
            assertEquals(OP_ID, result.conflict.operationId)
            assertEquals("USER_MERCHANT_VERIFICATION", result.conflict.entityTypeCode)
            assertEquals(VERIFICATION_ID, result.conflict.entityId)
            assertEquals(2L, result.conflict.localVersion)
            assertEquals(4L, result.conflict.remoteVersion)
            assertEquals(2000L, result.conflict.detectedAtEpochMillis)
            assertEquals(RemoteWriteOperation.UPDATE, writer.lastOperation)
            assertEquals(2L, writer.lastBaseVersion)
        }

    @Test
    fun merchant_verification_delete_not_found_returns_missing_delete_acknowledged() =
        runTest {
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.NotFound,
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val snapshotJson =
                snapshotJson(
                    id = VERIFICATION_ID,
                    userId = USER_ID,
                    alias = ALIAS,
                    decisionCode = "BLOCK",
                    merchantId = null,
                )
            val op =
                verificationOperation(
                    operationType = "DELETE",
                    baseVersion = 3L,
                    payloadJson = snapshotJson,
                )

            val result = executor.execute(op)

            assertIs<OutboxExecutionResult.MissingDeleteAcknowledged>(result)
            assertEquals(RemoteWriteOperation.DELETE, writer.lastOperation)
            assertEquals(3L, writer.lastBaseVersion)
        }

    @Test
    fun merchant_verification_create_not_found_throws_definitive_failure() =
        runTest {
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.NotFound,
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val snapshotJson =
                snapshotJson(
                    id = VERIFICATION_ID,
                    userId = USER_ID,
                    alias = ALIAS,
                    decisionCode = "ASSIGN",
                    merchantId = MERCHANT_ID,
                )
            val op =
                verificationOperation(
                    operationType = "CREATE",
                    baseVersion = null,
                    payloadJson = snapshotJson,
                )

            assertFailsWith<DefinitiveOutboxFailureException> {
                executor.execute(op)
            }
        }

    @Test
    fun merchant_verification_rejects_payload_id_mismatch() =
        runTest {
            val writer = RecordingMerchantVerificationWriter()
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val otherId = "88888888-8888-4888-8888-888888888888"
            val snapshotJson =
                snapshotJson(
                    id = otherId,
                    userId = USER_ID,
                    alias = ALIAS,
                    decisionCode = "ASSIGN",
                    merchantId = MERCHANT_ID,
                )
            val op =
                verificationOperation(
                    operationType = "CREATE",
                    baseVersion = null,
                    payloadJson = snapshotJson,
                )

            assertFailsWith<DefinitiveOutboxFailureException> {
                executor.execute(op)
            }
        }

    @Test
    fun merchant_verification_rejects_user_id_scope_mismatch() =
        runTest {
            val writer = RecordingMerchantVerificationWriter()
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val otherUserId = "99999999-9999-4999-8999-999999999999"
            val expectedId = UuidHelper.deterministicMerchantVerificationUuid(otherUserId, ALIAS)
            val snapshotJson =
                snapshotJson(
                    id = expectedId,
                    userId = otherUserId,
                    alias = ALIAS,
                    decisionCode = "ASSIGN",
                    merchantId = MERCHANT_ID,
                )
            val op =
                verificationOperation(
                    operationType = "CREATE",
                    baseVersion = null,
                    payloadJson = snapshotJson,
                ).copy(entityId = expectedId)

            assertFailsWith<DefinitiveOutboxFailureException> {
                executor.execute(op)
            }
        }

    @Test
    fun merchant_verification_rejects_malformed_payload() =
        runTest {
            val writer = RecordingMerchantVerificationWriter()
            val executor = V2OutboxOperationExecutor(writer) { 1000L }

            val op =
                verificationOperation(
                    operationType = "CREATE",
                    baseVersion = null,
                    payloadJson = "{\"invalid_json\": true}",
                )

            assertFailsWith<DefinitiveOutboxFailureException> {
                executor.execute(op)
            }
        }

    @Test
    fun merchant_verification_applied_rejects_missing_version() =
        runTest {
            val invalidRemote = remoteVerification(version = 1L).copy(version = null)
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Applied(invalidRemote),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }
            val op = verificationOperation("CREATE", null, snapshotJson())

            val error =
                assertFailsWith<DefinitiveOutboxFailureException> {
                    executor.execute(op)
                }
            assertEquals("invalid_remote_user_merchant_verification", error.message)
        }

    @Test
    fun merchant_verification_applied_rejects_malformed_updated_at() =
        runTest {
            val invalidRemote = remoteVerification(version = 1L).copy(updatedAt = "invalid-date")
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Applied(invalidRemote),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }
            val op = verificationOperation("CREATE", null, snapshotJson())

            val error =
                assertFailsWith<DefinitiveOutboxFailureException> {
                    executor.execute(op)
                }
            assertEquals("invalid_remote_user_merchant_verification", error.message)
        }

    @Test
    fun merchant_verification_applied_rejects_wrong_user_or_id() =
        runTest {
            val wrongUserRemote =
                remoteVerification(version = 1L).copy(
                    userId = "12345678-1234-4234-8234-123456789abc",
                )
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Applied(wrongUserRemote),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }
            val op = verificationOperation("CREATE", null, snapshotJson())

            val error =
                assertFailsWith<DefinitiveOutboxFailureException> {
                    executor.execute(op)
                }
            assertEquals("invalid_remote_user_merchant_verification", error.message)
        }

    @Test
    fun merchant_verification_conflict_rejects_missing_or_zero_version() =
        runTest {
            val zeroVersionRemote = remoteVerification(version = 0L)
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Conflict(zeroVersionRemote),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }
            val op = verificationOperation("UPDATE", 1L, snapshotJson())

            val error =
                assertFailsWith<DefinitiveOutboxFailureException> {
                    executor.execute(op)
                }
            assertEquals("invalid_remote_user_merchant_verification", error.message)
        }

    @Test
    fun merchant_verification_conflict_rejects_wrong_deterministic_id() =
        runTest {
            val arbitraryIdRemote =
                remoteVerification(version = 2L).copy(
                    id = "87654321-4321-4321-8321-123456789abc",
                )
            val writer =
                RecordingMerchantVerificationWriter(
                    verificationResult = ConditionalRemoteWriteResult.Conflict(arbitraryIdRemote),
                )
            val executor = V2OutboxOperationExecutor(writer) { 1000L }
            val op = verificationOperation("UPDATE", 1L, snapshotJson())

            val error =
                assertFailsWith<DefinitiveOutboxFailureException> {
                    executor.execute(op)
                }
            assertEquals("invalid_remote_user_merchant_verification", error.message)
        }

    private class RecordingMerchantVerificationWriter(
        var verificationResult: ConditionalRemoteWriteResult<UserMerchantVerificationDto> =
            ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastVerificationDto: UserMerchantVerificationDto? = null

        override suspend fun writeMerchantVerification(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: UserMerchantVerificationDto,
        ): ConditionalRemoteWriteResult<UserMerchantVerificationDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastVerificationDto = dto
            return verificationResult
        }
    }

    private companion object {
        const val OP_ID = "0123456789abcdef0123456789abcdef"
        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val ALIAS = "NETFLIX"
        const val MERCHANT_ID = "netflix"
        val VERIFICATION_ID = UuidHelper.deterministicMerchantVerificationUuid(USER_ID, ALIAS)

        fun snapshotJson(
            id: String = VERIFICATION_ID,
            userId: String = USER_ID,
            alias: String = ALIAS,
            decisionCode: String = "ASSIGN",
            merchantId: String? = MERCHANT_ID,
        ): String {
            val merchantIdVal = if (merchantId == null) "null" else "\"$merchantId\""
            return """{"id":"$id","user_id":"$userId","workspace_id":null,"normalized_alias":"$alias",""" +
                """"decision_code":"$decisionCode","merchant_id":$merchantIdVal,"scope_code":"PERSONAL"}"""
        }

        fun verificationOperation(
            operationType: String,
            baseVersion: Long?,
            payloadJson: String?,
        ): SyncOperationEntity =
            SyncOperationEntity(
                syncScopeKey = "USER:$USER_ID",
                operationId = OP_ID,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = VERIFICATION_ID,
                operationTypeCode = operationType,
                baseVersion = baseVersion,
                payloadJson = payloadJson,
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 2,
                statusCode = "IN_FLIGHT",
                attemptCount = 1,
                lastError = null,
                nextAttemptAtEpochMillis = 0L,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            )

        fun remoteVerification(
            version: Long,
            decisionCode: String = "ASSIGN",
            merchantId: String? = MERCHANT_ID,
        ): UserMerchantVerificationDto =
            UserMerchantVerificationDto(
                id = VERIFICATION_ID,
                userId = USER_ID,
                workspaceId = null,
                normalizedAlias = ALIAS,
                decisionCode = decisionCode,
                merchantId = merchantId,
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T10:00:00Z",
                deletedAt = null,
                version = version,
            )
    }
}
