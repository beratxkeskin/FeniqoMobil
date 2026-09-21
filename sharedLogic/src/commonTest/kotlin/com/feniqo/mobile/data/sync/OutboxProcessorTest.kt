package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.isDefinitiveRejection
import com.feniqo.mobile.data.local.entity.isAmbiguousResult
import com.feniqo.mobile.data.local.entity.OutboxErrorClassification
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.dto.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class OutboxProcessorTest {
    @Test
    fun sends_operations_in_order_and_removes_only_successful_ones() = runTest {
        val queue = FakeQueue(listOf(operation("one"), operation("two")))
        val sent = mutableListOf<String>()

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            sent += it.operationId
            OutboxExecutionResult.V1Completed
        }).processReadyOperations()

        assertEquals(listOf("one", "two"), sent)
        assertEquals(listOf("one", "two"), queue.succeeded)
        assertEquals(2, result.succeededCount)
        assertEquals(null, result.failedOperationId)
    }

    @Test
    fun v2_applied_result_triggers_ackV2Execution() = runTest {
        val queue = FakeQueue(listOf(operation("v2-op")))
        val dummyCategory = CategoryDto(
            id = "cat-1",
            name = "Test",
            type = "expense",
            color = "#123",
            createdAt = "2026-08-25T17:00:00Z",
            version = 2L,
        )

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            OutboxExecutionResult.CategoryApplied(dummyCategory)
        }).processReadyOperations()

        assertEquals(1, result.succeededCount)
        assertEquals(listOf("v2-op"), queue.v2Acked.map { it.first })
        assertEquals(dummyCategory, (queue.v2Acked.first().second as OutboxExecutionResult.CategoryApplied).record)
    }

    @Test
    fun executor_receives_claimed_in_flight_operation_with_incremented_attempt() = runTest {
        val queue = FakeQueue(listOf(operation("claim-test")))
        var receivedOp: SyncOperationEntity? = null

        OutboxProcessor(queue, OutboxOperationExecutor {
            receivedOp = it
            OutboxExecutionResult.V1Completed
        }).processReadyOperations()

        assertTrue(receivedOp != null)
        assertEquals("IN_FLIGHT", receivedOp?.statusCode)
        assertEquals(1, receivedOp?.attemptCount)
    }

    @Test
    fun records_failure_and_preserves_later_operations_for_the_next_attempt() = runTest {
        val queue = FakeQueue(listOf(operation("one"), operation("two")))
        val sent = mutableListOf<String>()

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            sent += it.operationId
            if (it.operationId == "one") error("Bearer secret-token; payload={\"amount_minor\":12345}")
            OutboxExecutionResult.V1Completed
        }).processReadyOperations()

        assertEquals(listOf("one"), sent)
        assertEquals(listOf("one"), queue.failures.map { it.first })
        assertEquals(OutboxProcessor.SAFE_FAILURE_CODE, queue.failures.single().second)
        assertEquals(emptyList(), queue.succeeded)
        assertEquals("one", result.failedOperationId)
    }

    @Test
    fun records_conflict_without_turning_it_into_a_retryable_failure() = runTest {
        val queue = FakeQueue(listOf(operation("one"), operation("two")))

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            throw OutboxConflictException("Sürüm uyuşmazlığı")
        }).processReadyOperations()

        assertEquals(listOf("one"), queue.conflicts.map { it.first })
        assertEquals(OutboxProcessor.SAFE_CONFLICT_CODE, queue.conflicts.single().second)
        assertEquals(emptyList(), queue.failures)
        assertEquals(null, result.failedOperationId)
        assertEquals("one", result.conflictOperationId)
    }

    @Test
    fun v2_conflict_detected_triggers_recordV2Conflict_and_stops_batch() = runTest {
        val queue = FakeQueue(listOf(operation("v2-conflict-op"), operation("two")))
        val conflict = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
            entityTypeCode = "TRANSACTION",
            entityId = "transaction-v2-conflict-op",
            operationId = "v2-conflict-op",
            localVersion = 0L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 1000L,
        )

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            OutboxExecutionResult.ConflictDetected(conflict)
        }).processReadyOperations()

        assertEquals(listOf(conflict), queue.v2Conflicts)
        assertEquals(emptyList(), queue.failures)
        assertEquals(0, result.succeededCount)
        assertEquals(null, result.failedOperationId)
        assertEquals("v2-conflict-op", result.conflictOperationId)
    }

    private class FakeQueue(private val operations: List<SyncOperationEntity>) : OutboxQueue {
        val succeeded = mutableListOf<String>()
        val v2Acked = mutableListOf<Pair<String, OutboxExecutionResult>>()
        val v2Conflicts = mutableListOf<com.feniqo.mobile.data.local.entity.SyncConflictEntity>()
        val failures = mutableListOf<Triple<String, String, String?>>()
        val conflicts = mutableListOf<Pair<String, String>>()

        override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> = operations.take(limit)

        override suspend fun claimOperation(operationId: String): SyncOperationEntity? {
            val op = operations.find { it.operationId == operationId } ?: return null
            return op.copy(statusCode = "IN_FLIGHT", attemptCount = op.attemptCount + 1)
        }

        override suspend fun markSucceeded(operationId: String): Boolean = succeeded.add(operationId)

        override suspend fun ackV2Execution(operationId: String, result: OutboxExecutionResult): Boolean {
            v2Acked.add(operationId to result)
            return true
        }

        override suspend fun recordV2Conflict(conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean {
            v2Conflicts.add(conflict)
            return true
        }

        override suspend fun recordFailure(operationId: String, errorMessage: String): Boolean =
            failures.add(Triple(operationId, errorMessage, null))

        override suspend fun recordFailure(operationId: String, errorMessage: String, errorClassification: String?): Boolean =
            failures.add(Triple(operationId, errorMessage, errorClassification))

        override suspend fun markConflict(operationId: String, errorMessage: String): Boolean =
            conflicts.add(operationId to errorMessage)
    }

    @Test
    fun classification_serialization_exception_is_ambiguous_result() {
        val error = kotlinx.serialization.SerializationException("Response decode error after remote execution")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            error.classifyOutboxError(),
        )
    }

    @Test
    fun classification_illegal_argument_exception_is_ambiguous_result() {
        val error = IllegalArgumentException("Generic argument mismatch")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            error.classifyOutboxError(),
        )
    }

    @Test
    fun classification_network_timeout_or_5xx_is_ambiguous_result() {
        val timeoutError = java.io.IOException("Connection timed out waiting for server ACK")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            timeoutError.classifyOutboxError(),
        )

        val socketError = java.net.SocketTimeoutException("Read timeout")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            socketError.classifyOutboxError(),
        )
    }

    @Test
    fun classification_definitive_outbox_failure_exception_is_definitive_rejection() {
        val failure = DefinitiveOutboxFailureException(
            message = "Pre-flight validation failed: payload id mismatch",
        )
        assertEquals(
            OutboxErrorClassification.DEFINITIVE_REJECTION,
            failure.classifyOutboxError(),
        )

        val serverRejection = ServerRejectedMutationException(
            message = "VALIDATION_FAILED: Category does not exist",
            statusCode = 422,
            errorCode = "P0001",
        )
        assertEquals(
            OutboxErrorClassification.DEFINITIVE_REJECTION,
            serverRejection.classifyOutboxError(),
        )
    }

    @Test
    fun classification_unknown_exception_is_ambiguous_result() {
        val unknown = RuntimeException("Unexpected runtime error")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            unknown.classifyOutboxError(),
        )
    }

    @Test
    fun legacy_v19_null_classification_entity_is_never_definitive_rejection() {
        val legacyOp = SyncOperationEntity(
            operationId = "legacy-op",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            statusCode = "FAILED",
            attemptCount = 3,
            lastError = "Connection reset",
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
            errorClassification = null,
        )

        // NULL errorClassification asla DEFINITIVE_REJECTION olmamalıdır!
        kotlin.test.assertFalse(legacyOp.isDefinitiveRejection())
        kotlin.test.assertTrue(legacyOp.isAmbiguousResult())
    }

    @Test
    fun test_A_local_payload_validation_failure_leaves_writer_calls_zero_and_is_definitive_rejection() = runTest {
        var writerCalls = 0
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                return ConditionalRemoteWriteResult.Applied(dto)
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val invalidOp = SyncOperationEntity(
            operationId = "op-invalid-payload",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = "{corrupted json, missing braces",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(invalidOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations()

        assertEquals("op-invalid-payload", runResult.failedOperationId)
        // Writer hiç çağrılmadı:
        assertEquals(0, writerCalls)
        // Hata sınıflandırması kesin ret oldu:
        assertEquals(1, queue.failures.size)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
    }

    @Test
    fun test_B_payload_id_mismatch_leaves_writer_calls_zero_and_is_definitive_rejection() = runTest {
        var writerCalls = 0
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                return ConditionalRemoteWriteResult.Applied(dto)
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val mismatchOp = SyncOperationEntity(
            operationId = "op-mismatch",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-entity-id-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"tx-different-id-2","user_id":"u-1","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"c-1","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(mismatchOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations()

        assertEquals("op-mismatch", runResult.failedOperationId)
        // Writer hiç çağrılmadı:
        assertEquals(0, writerCalls)
        // Hata sınıflandırması kesin ret oldu:
        assertEquals(1, queue.failures.size)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
    }

    @Test
    fun test_C_remote_mutation_applied_but_response_decode_error_is_ambiguous_result() = runTest {
        var writerCalls = 0
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                // Writer uzak sunucuda işlemi başarıyla uyguladı, ancak dönerken decode hatası oluştu:
                throw kotlinx.serialization.SerializationException("Response decode error from Supabase RPC")
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val validOp = SyncOperationEntity(
            operationId = "op-valid",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"tx-1","user_id":"u-1","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"c-1","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(validOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations()

        assertEquals("op-valid", runResult.failedOperationId)
        // Writer çağrıldı!
        assertEquals(1, writerCalls)
        // Hata sınıflandırması belirsiz (AMBIGUOUS_RESULT) kaldı:
        assertEquals(1, queue.failures.size)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
    }

    @Test
    fun test_D_generic_illegal_argument_exception_after_remote_call_is_ambiguous_result() = runTest {
        var writerCalls = 0
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                throw IllegalArgumentException("Unexpected post-network state")
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val validOp = SyncOperationEntity(
            operationId = "op-valid-d",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"tx-1","user_id":"u-1","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"c-1","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(validOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations()

        assertEquals("op-valid-d", runResult.failedOperationId)
        assertEquals(1, writerCalls)
        assertEquals(1, queue.failures.size)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
        assertEquals(OutboxProcessor.SAFE_FAILURE_CODE, queue.failures[0].second)
        assertTrue(queue.conflicts.isEmpty())
    }

    @Test
    fun cancellation_exception_in_executor_or_writer_propagates_without_recording_failure() = runTest {
        val op = operation("cancel-op")
        val queue = FakeQueue(listOf(op))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            throw kotlinx.coroutines.CancellationException("Job was cancelled")
        })

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            processor.processReadyOperations()
        }

        assertTrue(queue.failures.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
    }

    @Test
    fun fatal_error_in_executor_propagates_without_recording_failure() = runTest {
        val op = operation("fatal-op")
        val queue = FakeQueue(listOf(op))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            throw AssertionError("Fatal assertion failed during execution")
        })

        assertFailsWith<AssertionError> {
            processor.processReadyOperations()
        }

        assertTrue(queue.failures.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
    }

    @Test
    fun fatal_error_classification_is_not_definitive_rejection() {
        val error = AssertionError("JVM internal assertion failure")
        assertEquals(
            OutboxErrorClassification.AMBIGUOUS_RESULT,
            error.classifyOutboxError(),
        )
    }

    @Test
    fun sensitive_payload_values_do_not_leak_into_failure_message_or_exception() = runTest {
        val sensitiveIban = "TR990006100511234567890123"
        val sensitiveAmount = 999999999L
        val corruptedPayload = """{"id":"tx-leak","sensitive_iban":"$sensitiveIban","amount_minor":$sensitiveAmount,"invalid_json":true"""

        var writerCalls = 0
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                return ConditionalRemoteWriteResult.Applied(dto)
            }
        }
        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }

        val leakOp = SyncOperationEntity(
            operationId = "op-leak",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-leak",
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = corruptedPayload,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val caughtException = assertFailsWith<DefinitiveOutboxFailureException> {
            executor.execute(leakOp)
        }
        kotlin.test.assertNull(caughtException.cause, "Yerel payload decode hatasında ham exception cause zincirine eklenmemeli!")
        var currentEx: Throwable? = caughtException
        while (currentEx != null) {
            assertFalse(currentEx.message?.contains(sensitiveIban) == true)
            assertFalse(currentEx.message?.contains(sensitiveAmount.toString()) == true)
            currentEx = currentEx.cause
        }
        assertEquals("invalid_local_outbox_payload", caughtException.message)

        val queue = FakeQueue(listOf(leakOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val result = processor.processReadyOperations()

        assertEquals("op-leak", result.failedOperationId)
        assertEquals(0, writerCalls)
        assertEquals(1, queue.failures.size)
        val recordedErrorMessage = queue.failures[0].second
        assertFalse(recordedErrorMessage.contains(sensitiveIban))
        assertFalse(recordedErrorMessage.contains(sensitiveAmount.toString()))
        assertEquals(OutboxProcessor.SAFE_FAILURE_CODE, recordedErrorMessage)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)

        var lastErr: Throwable? = result.lastError
        kotlin.test.assertNotNull(lastErr)
        kotlin.test.assertNull(lastErr.cause, "lastError cause zincirinde ham serializer hatası bulunmamalı!")
        while (lastErr != null) {
            assertFalse(lastErr.message?.contains(sensitiveIban) == true)
            assertFalse(lastErr.message?.contains(sensitiveAmount.toString()) == true)
            lastErr = lastErr.cause
        }
    }

    @Test
    fun test_F_outbox_processor_records_error_classification_in_queue() = runTest {
        val queue = FakeQueue(listOf(operation("test-f")))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            throw DefinitiveOutboxFailureException("Test failure")
        })

        processor.processReadyOperations()
        assertEquals(1, queue.failures.size)
        assertEquals("test-f", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
    }

    private open class FakeWriterBase : IdempotentConditionalRemoteWriter

    private companion object {
        fun operation(id: String) = SyncOperationEntity(
            operationId = id,
            entityTypeCode = "TRANSACTION",
            entityId = "transaction-$id",
            operationTypeCode = "CREATE",
            baseVersion = null,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
    }
}
