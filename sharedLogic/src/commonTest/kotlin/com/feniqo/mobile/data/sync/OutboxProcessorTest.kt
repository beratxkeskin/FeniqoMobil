package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.isDefinitiveRejection
import com.feniqo.mobile.data.local.entity.isAmbiguousResult
import com.feniqo.mobile.data.local.entity.OutboxErrorClassification
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.dto.*
import kotlinx.coroutines.launch
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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
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
        }).processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

        assertEquals(listOf(conflict), queue.v2Conflicts)
        assertEquals(emptyList(), queue.failures)
        assertEquals(0, result.succeededCount)
        assertEquals(null, result.failedOperationId)
        assertEquals("v2-conflict-op", result.conflictOperationId)
    }

    // 10. OutboxProcessor, LEGACY_UNRESOLVED ile hiçbir operasyon claim etmez ve remote writer çağırmaz
    @Test
    fun processReadyOperations_withLegacyUnresolvedScope_failsAndDoesNotClaimOrExecute() = runTest {
        val op = operation("legacy-op", syncScopeKey = "LEGACY_UNRESOLVED")
        val queue = FakeQueue(listOf(op))
        var executorCalls = 0

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            executorCalls++
            OutboxExecutionResult.V1Completed
        })

        assertFailsWith<IllegalArgumentException> {
            processor.processReadyOperations("LEGACY_UNRESOLVED", assertSessionCurrent = {})
        }

        assertEquals(0, executorCalls)
        assertEquals(emptyList(), queue.claimedOperations)
        assertEquals(emptyList(), queue.succeeded)
        assertEquals(emptyList(), queue.failures)
        assertEquals(emptyList(), queue.conflicts)
    }

    @Test
    fun processReadyOperations_withCompactUserScope_failsAndDoesNotClaimOrExecute() = runTest {
        val op = operation("compact-op", syncScopeKey = "USER:11111111111141118111111111111111")
        val queue = FakeQueue(listOf(op))
        var executorCalls = 0

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            executorCalls++
            OutboxExecutionResult.V1Completed
        })

        assertFailsWith<IllegalArgumentException> {
            processor.processReadyOperations("USER:11111111111141118111111111111111", assertSessionCurrent = {})
        }

        assertEquals(0, executorCalls)
        assertEquals(emptyList(), queue.claimedOperations)
        assertEquals(emptyList(), queue.succeeded)
        assertEquals(emptyList(), queue.failures)
        assertEquals(emptyList(), queue.conflicts)
    }

    private class FakeQueue(initialOperations: List<SyncOperationEntity>) : OutboxQueue {
        val operations = initialOperations.toMutableList()
        val succeeded = mutableListOf<String>()
        val v2Acked = mutableListOf<Pair<String, OutboxExecutionResult>>()
        val v2Conflicts = mutableListOf<com.feniqo.mobile.data.local.entity.SyncConflictEntity>()
        val failures = mutableListOf<Triple<String, String, String?>>()
        val conflicts = mutableListOf<Pair<String, String>>()
        val claimedOperations = mutableListOf<String>()

        override suspend fun readyOperations(syncScopeKey: String, limit: Int): List<SyncOperationEntity> =
            operations.filter { it.syncScopeKey == syncScopeKey }.take(limit)

        override suspend fun claimOperation(syncScopeKey: String, operationId: String): SyncOperationEntity? {
            claimedOperations.add(operationId)
            val idx = operations.indexOfFirst { it.syncScopeKey == syncScopeKey && it.operationId == operationId }
            if (idx < 0) return null
            val claimed = operations[idx].copy(statusCode = "IN_FLIGHT", attemptCount = operations[idx].attemptCount + 1)
            operations[idx] = claimed
            return claimed
        }

        override suspend fun markSucceeded(syncScopeKey: String, operationId: String): Boolean = succeeded.add(operationId)

        override suspend fun ackV2Execution(syncScopeKey: String, operationId: String, result: OutboxExecutionResult): Boolean {
            v2Acked.add(operationId to result)
            return true
        }

        override suspend fun recordV2Conflict(syncScopeKey: String, conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean {
            v2Conflicts.add(conflict)
            return true
        }

        override suspend fun recordFailure(syncScopeKey: String, operationId: String, errorMessage: String): Boolean =
            recordFailure(syncScopeKey, operationId, errorMessage, null)

        override suspend fun recordFailure(syncScopeKey: String, operationId: String, errorMessage: String, errorClassification: String?): Boolean {
            val idx = operations.indexOfFirst { it.syncScopeKey == syncScopeKey && it.operationId == operationId }
            if (idx >= 0) {
                operations[idx] = operations[idx].copy(statusCode = "FAILED", lastError = errorMessage)
            }
            return failures.add(Triple(operationId, errorMessage, errorClassification))
        }

        override suspend fun markConflict(syncScopeKey: String, operationId: String, errorMessage: String): Boolean =
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
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
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
            operationId = OP_ID_HEX_A,
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
            entityTypeCode = "TRANSACTION",
            entityId = CANONICAL_TX_ID_A,
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
        val runResult = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

        assertEquals(OP_ID_HEX_A, runResult.failedOperationId)
        // Pre-flight doğrulaması başarısız olduğu için writer hiç çağrılmadı:
        assertEquals(0, writerCalls)
        // Hata türü DefinitiveOutboxFailureException:
        assertTrue(runResult.lastError is DefinitiveOutboxFailureException)
        // Hata sınıflandırması kesin ret oldu:
        assertEquals(1, queue.failures.size)
        assertEquals(OP_ID_HEX_A, queue.failures[0].first)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
        assertTrue(queue.v2Acked.isEmpty())
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
        assertTrue(queue.v2Conflicts.isEmpty())
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
        // Her iki ID de ayrı ayrı geçerli canonical UUID; test invalid format değil, yalnız ID mismatch nedeniyle başarısız olur:
        val mismatchOp = SyncOperationEntity(
            operationId = OP_ID_HEX_B,
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
            entityTypeCode = "TRANSACTION",
            entityId = CANONICAL_TX_ID_A,
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"$CANONICAL_TX_ID_B","user_id":"$CANONICAL_USER_ID","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"$CANONICAL_CAT_ID","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(mismatchOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

        assertEquals(OP_ID_HEX_B, runResult.failedOperationId)
        // Writer hiç çağrılmadı:
        assertEquals(0, writerCalls)
        // Hata türü DefinitiveOutboxFailureException:
        assertTrue(runResult.lastError is DefinitiveOutboxFailureException)
        // Hata sınıflandırması kesin ret oldu:
        assertEquals(1, queue.failures.size)
        assertEquals(OP_ID_HEX_B, queue.failures[0].first)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
        assertTrue(queue.v2Acked.isEmpty())
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
        assertTrue(queue.v2Conflicts.isEmpty())
    }

    @Test
    fun test_C_remote_mutation_applied_but_response_decode_error_is_ambiguous_result() = runTest {
        var writerCalls = 0
        var receivedOpId: String? = null
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                receivedOpId = operationId
                // Writer uzak sunucuda işlemi başarıyla uyguladı, ancak dönerken decode hatası oluştu:
                throw kotlinx.serialization.SerializationException("Response decode error from Supabase RPC")
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val validOp = SyncOperationEntity(
            operationId = OP_ID_HEX_C,
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
            entityTypeCode = "TRANSACTION",
            entityId = CANONICAL_TX_ID_A,
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"$CANONICAL_TX_ID_A","user_id":"$CANONICAL_USER_ID","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"$CANONICAL_CAT_ID","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(validOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

        // Writer tam bir kez ve aynı operationId ile çağrıldı:
        assertEquals(1, writerCalls)
        assertEquals(OP_ID_HEX_C, receivedOpId)
        // Başarısız operasyon ID'si aynı operation_id:
        assertEquals(OP_ID_HEX_C, runResult.failedOperationId)
        // Last error SerializationException ve DefinitiveOutboxFailureException DEĞİL:
        assertTrue(runResult.lastError is kotlinx.serialization.SerializationException)
        assertTrue(runResult.lastError !is DefinitiveOutboxFailureException)
        // Hata sınıflandırması belirsiz (AMBIGUOUS_RESULT) kaldı:
        assertEquals(1, queue.failures.size)
        assertEquals(OP_ID_HEX_C, queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
        assertEquals(OutboxProcessor.SAFE_FAILURE_CODE, queue.failures[0].second)
        // ackV2Execution, markSucceeded ve conflict yolları çağrılmadı:
        assertTrue(queue.v2Acked.isEmpty())
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
        assertTrue(queue.v2Conflicts.isEmpty())
    }

    @Test
    fun test_D_generic_illegal_argument_exception_after_remote_call_is_ambiguous_result() = runTest {
        var writerCalls = 0
        var receivedOpId: String? = null
        val fakeWriter = object : FakeWriterBase() {
            override suspend fun writeTransaction(
                operationId: String,
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto
            ): ConditionalRemoteWriteResult<TransactionDto> {
                writerCalls++
                receivedOpId = operationId
                throw IllegalArgumentException("Unexpected post-network state")
            }
        }

        val executor = V2OutboxOperationExecutor(fakeWriter) { 1000L }
        val validOp = SyncOperationEntity(
            operationId = OP_ID_HEX_D,
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
            entityTypeCode = "TRANSACTION",
            entityId = CANONICAL_TX_ID_A,
            operationTypeCode = "CREATE",
            baseVersion = null,
            protocolVersion = 2,
            payloadJson = """{"id":"$CANONICAL_TX_ID_A","user_id":"$CANONICAL_USER_ID","amount_minor":5000,"currency":"TRY","type":"expense","category_id":"$CANONICAL_CAT_ID","payment_method":"CASH","transaction_date":"2026-09-21","created_at":"2026-09-21T00:00:00Z"}""",
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

        val queue = FakeQueue(listOf(validOp))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op -> executor.execute(op) })
        val runResult = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

        // Writer tam bir kez ve aynı operationId ile çağrıldı:
        assertEquals(1, writerCalls)
        assertEquals(OP_ID_HEX_D, receivedOpId)
        // Başarısız operasyon ID'si aynı:
        assertEquals(OP_ID_HEX_D, runResult.failedOperationId)
        // Last error genel IllegalArgumentException ve DefinitiveOutboxFailureException DEĞİL:
        assertTrue(runResult.lastError is IllegalArgumentException)
        assertTrue(runResult.lastError !is DefinitiveOutboxFailureException)
        // Hata sınıflandırması belirsiz (AMBIGUOUS_RESULT) kaldı:
        assertEquals(1, queue.failures.size)
        assertEquals(OP_ID_HEX_D, queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
        assertEquals(OutboxProcessor.SAFE_FAILURE_CODE, queue.failures[0].second)
        // ACK/success/conflict oluşmadı:
        assertTrue(queue.v2Acked.isEmpty())
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.conflicts.isEmpty())
        assertTrue(queue.v2Conflicts.isEmpty())
    }

    @Test
    fun cancellation_exception_in_executor_or_writer_propagates_without_recording_failure() = runTest {
        val op = operation("cancel-op")
        val queue = FakeQueue(listOf(op))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            throw kotlinx.coroutines.CancellationException("Job was cancelled")
        })

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})
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
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})
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
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
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
        val result = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})

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

        processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})
        assertEquals(1, queue.failures.size)
        assertEquals("test-f", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.DEFINITIVE_REJECTION.name, queue.failures[0].third)
    }

    // -------------------------------------------------------------
    // Session Race Tests (1 to 10)
    // -------------------------------------------------------------

    @Test
    fun session_invalid_before_ready_query_does_not_read_or_claim_queue() = runTest {
        var readyCalled = false
        val op = operation("op-1")
        val queue = object : OutboxQueue by FakeQueue(listOf(op)) {
            override suspend fun readyOperations(syncScopeKey: String, limit: Int): List<SyncOperationEntity> {
                readyCalled = true
                return listOf(op)
            }
        }
        val processor = OutboxProcessor(queue, OutboxOperationExecutor { OutboxExecutionResult.V1Completed })

        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                throw SyncSessionInvalidatedException()
            })
        }

        assertFalse(readyCalled)
    }

    @Test
    fun session_invalid_before_claim_does_not_claim_or_execute() = runTest {
        val op = operation("op-1")
        val queue = FakeQueue(listOf(op))
        var executorCalls = 0
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            executorCalls++
            OutboxExecutionResult.V1Completed
        })

        var guardCalls = 0
        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                guardCalls++
                if (guardCalls > 1) {
                    throw SyncSessionInvalidatedException()
                }
            })
        }

        assertEquals(0, queue.claimedOperations.size)
        assertEquals(0, executorCalls)
        assertTrue(queue.failures.isEmpty())
    }

    @Test
    fun session_invalid_after_claim_before_executor_does_not_call_remote_writer() = runTest {
        val op = operation("op-1")
        val queue = FakeQueue(listOf(op))
        var executorCalls = 0
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            executorCalls++
            OutboxExecutionResult.V1Completed
        })

        var guardCalls = 0
        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                guardCalls++
                if (guardCalls > 2) {
                    throw SyncSessionInvalidatedException()
                }
            })
        }

        assertEquals(listOf("op-1"), queue.claimedOperations)
        assertEquals(0, executorCalls)
        assertEquals(1, queue.failures.size)
        assertEquals("op-1", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
    }

    @Test
    fun session_changes_while_remote_mutation_is_suspended_marks_same_operation_ambiguous() = runTest {
        val op = operation("op-suspend")
        val queue = FakeQueue(listOf(op))
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            entered.complete(Unit)
            release.await()
            OutboxExecutionResult.V1Completed
        })

        var sessionValid = true
        val assertSession: suspend () -> Unit = {
            if (!sessionValid) throw SyncSessionInvalidatedException()
        }

        val job = launch {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = assertSession)
        }

        entered.await()
        sessionValid = false
        job.cancel(SyncSessionInvalidatedException())
        job.join()

        assertEquals(1, queue.failures.size)
        assertEquals("op-suspend", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.v2Acked.isEmpty())
    }

    @Test
    fun session_changes_after_remote_result_before_ack_does_not_ack_and_marks_ambiguous() = runTest {
        val op = operation("op-post-remote")
        val queue = FakeQueue(listOf(op))
        var executorCalls = 0

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            executorCalls++
            OutboxExecutionResult.V1Completed
        })

        var guardCalls = 0
        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                guardCalls++
                if (guardCalls > 3) {
                    throw SyncSessionInvalidatedException()
                }
            })
        }

        assertEquals(1, executorCalls)
        assertTrue(queue.succeeded.isEmpty())
        assertTrue(queue.v2Acked.isEmpty())
        assertEquals(1, queue.failures.size)
        assertEquals("op-post-remote", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
    }

    @Test
    fun auth_error_after_session_change_is_not_classified_as_definitive_rejection() = runTest {
        val op = operation("op-auth-err")
        val queue = FakeQueue(listOf(op))
        var sessionValid = true

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            sessionValid = false
            throw DefinitiveOutboxFailureException("Unauthorized 401", statusCode = 401)
        })

        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                if (!sessionValid) throw SyncSessionInvalidatedException()
            })
        }

        assertEquals(1, queue.failures.size)
        assertEquals("op-auth-err", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)
    }

    @Test
    fun same_user_session_keeps_existing_success_ack_behavior() = runTest {
        val op = operation("op-ok")
        val queue = FakeQueue(listOf(op))
        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            OutboxExecutionResult.V1Completed
        })

        val result = processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})
        assertEquals(1, result.succeededCount)
        assertEquals(listOf("op-ok"), queue.succeeded)
        assertTrue(queue.failures.isEmpty())
    }

    @Test
    fun external_cancellation_is_rethrown_and_never_converted_to_definitive_rejection() = runTest {
        val op = operation("op-ext-cancel")
        val queue = FakeQueue(listOf(op))
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            entered.complete(Unit)
            release.await()
            OutboxExecutionResult.V1Completed
        })

        val job = launch {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {})
        }

        entered.await()
        job.cancel(kotlinx.coroutines.CancellationException("User external cancel"))
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(queue.failures.none { it.third == OutboxErrorClassification.DEFINITIVE_REJECTION.name })
        assertTrue(queue.failures.isEmpty())
    }

    @Test
    fun session_invalidation_stops_second_ready_operation() = runTest {
        val op1 = operation("op-1")
        val op2 = operation("op-2")
        val queue = FakeQueue(listOf(op1, op2))
        var guardCalls = 0

        val processor = OutboxProcessor(queue, OutboxOperationExecutor { op ->
            OutboxExecutionResult.V1Completed
        })

        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                guardCalls++
                if (guardCalls > 4) {
                    throw SyncSessionInvalidatedException()
                }
            })
        }

        assertEquals(listOf("op-1"), queue.succeeded)
        assertFalse(queue.claimedOperations.contains("op-2"))
    }

    @Test
    fun ambiguous_transition_preserves_operation_id_payload_and_predecessor() = runTest {
        val op = SyncOperationEntity(
            operationId = "op-preserved-id",
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-123",
            operationTypeCode = "UPDATE",
            baseVersion = 5L,
            predecessorOperationId = "pred-op-999",
            protocolVersion = 2,
            payloadJson = """{"amount_minor": 4200, "note": "preserved"}""",
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val queue = FakeQueue(listOf(op))
        var sessionValid = true

        val processor = OutboxProcessor(queue, OutboxOperationExecutor {
            sessionValid = false
            throw DefinitiveOutboxFailureException("Unauthorized 403", statusCode = 403)
        })

        assertFailsWith<SyncSessionInvalidatedException> {
            processor.processReadyOperations("USER:11111111-1111-4111-8111-111111111111", assertSessionCurrent = {
                if (!sessionValid) throw SyncSessionInvalidatedException()
            })
        }

        assertEquals(1, queue.failures.size)
        assertEquals("op-preserved-id", queue.failures[0].first)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, queue.failures[0].third)

        val storedOp = queue.operations.single { it.operationId == "op-preserved-id" }
        assertEquals("op-preserved-id", storedOp.operationId)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", storedOp.syncScopeKey)
        assertEquals("TRANSACTION", storedOp.entityTypeCode)
        assertEquals("tx-123", storedOp.entityId)
        assertEquals("UPDATE", storedOp.operationTypeCode)
        assertEquals(5L, storedOp.baseVersion)
        assertEquals("pred-op-999", storedOp.predecessorOperationId)
        assertEquals(2, storedOp.protocolVersion)
        assertEquals("""{"amount_minor": 4200, "note": "preserved"}""", storedOp.payloadJson)
    }

    private open class FakeWriterBase : IdempotentConditionalRemoteWriter

    private companion object {
        const val CANONICAL_TX_ID_A = "11111111-1111-4111-8111-111111111111"
        const val CANONICAL_TX_ID_B = "22222222-2222-4222-8222-222222222222"
        const val CANONICAL_USER_ID = "33333333-3333-4333-8333-333333333333"
        const val CANONICAL_CAT_ID = "44444444-4444-4444-8444-444444444444"
        const val OP_ID_HEX_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OP_ID_HEX_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val OP_ID_HEX_C = "cccccccccccccccccccccccccccccccc"
        const val OP_ID_HEX_D = "dddddddddddddddddddddddddddddddd"

        fun operation(id: String, syncScopeKey: String = "USER:11111111-1111-4111-8111-111111111111") = SyncOperationEntity(
            operationId = id,
            syncScopeKey = syncScopeKey,
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
