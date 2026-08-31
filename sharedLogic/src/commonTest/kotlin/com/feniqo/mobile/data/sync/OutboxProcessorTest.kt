package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            if (it.operationId == "one") error("ağ kesildi")
            OutboxExecutionResult.V1Completed
        }).processReadyOperations()

        assertEquals(listOf("one"), sent)
        assertEquals(listOf("one"), queue.failures.map { it.first })
        assertEquals("ağ kesildi", queue.failures.single().second)
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
        val failures = mutableListOf<Pair<String, String>>()
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
            failures.add(operationId to errorMessage)

        override suspend fun markConflict(operationId: String, errorMessage: String): Boolean =
            conflicts.add(operationId to errorMessage)
    }

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
