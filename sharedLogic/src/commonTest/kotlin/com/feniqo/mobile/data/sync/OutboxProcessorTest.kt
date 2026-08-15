package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxProcessorTest {
    @Test
    fun sends_operations_in_order_and_removes_only_successful_ones() = runTest {
        val queue = FakeQueue(listOf(operation("one"), operation("two")))
        val sent = mutableListOf<String>()

        val result = OutboxProcessor(queue, OutboxOperationExecutor { sent += it.operationId })
            .processReadyOperations()

        assertEquals(listOf("one", "two"), sent)
        assertEquals(listOf("one", "two"), queue.succeeded)
        assertEquals(2, result.succeededCount)
        assertEquals(null, result.failedOperationId)
    }

    @Test
    fun records_failure_and_preserves_later_operations_for_the_next_attempt() = runTest {
        val queue = FakeQueue(listOf(operation("one"), operation("two")))
        val sent = mutableListOf<String>()

        val result = OutboxProcessor(queue, OutboxOperationExecutor {
            sent += it.operationId
            if (it.operationId == "one") error("ağ kesildi")
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

    private class FakeQueue(private val operations: List<SyncOperationEntity>) : OutboxQueue {
        val succeeded = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, String>>()
        val conflicts = mutableListOf<Pair<String, String>>()
        override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> = operations.take(limit)
        override suspend fun markInFlight(operationId: String): Boolean = true
        override suspend fun markSucceeded(operationId: String): Boolean = succeeded.add(operationId)
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
