package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BudgetV2OutboxExecutorTest {

    @Test
    fun budget_create_applied_returns_budget_applied() = runTest {
        val remoteDto = remoteBudget(version = 1L)
        val writer = RecordingBudgetWriter(
            budgetResult = ConditionalRemoteWriteResult.Applied(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = budgetSnapshotJson(id = BUDGET_ID, version = null)
        val op = budgetOperation(
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.BudgetApplied>(result)
        assertEquals(BUDGET_ID, result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(OP_ID, writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
        assertEquals(500000L, writer.lastBudgetDto?.limitMinor)
    }

    @Test
    fun budget_update_conflict_returns_conflict_detected_and_preserves_remote_version() = runTest {
        val remoteDto = remoteBudget(version = 4L, limitMinor = 750000L)
        val writer = RecordingBudgetWriter(
            budgetResult = ConditionalRemoteWriteResult.Conflict(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 2000L }

        val snapshotJson = budgetSnapshotJson(id = BUDGET_ID, version = 2L, limit = 600000L)
        val op = budgetOperation(
            operationType = "UPDATE",
            baseVersion = 2L,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals(OP_ID, result.conflict.operationId)
        assertEquals("BUDGET", result.conflict.entityTypeCode)
        assertEquals(BUDGET_ID, result.conflict.entityId)
        assertEquals(2L, result.conflict.localVersion)
        assertEquals(4L, result.conflict.remoteVersion)
        assertEquals(2000L, result.conflict.detectedAtEpochMillis)
        assertEquals(RemoteWriteOperation.UPDATE, writer.lastOperation)
        assertEquals(2L, writer.lastBaseVersion)
    }

    @Test
    fun budget_delete_not_found_returns_missing_delete_acknowledged() = runTest {
        val writer = RecordingBudgetWriter(
            budgetResult = ConditionalRemoteWriteResult.NotFound,
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = budgetSnapshotJson(id = BUDGET_ID, version = 3L)
        val op = budgetOperation(
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
    fun budget_rejects_payload_id_mismatch_without_calling_writer() = runTest {
        val writer = RecordingBudgetWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = budgetSnapshotJson(id = "different-budget-id", version = null)
        val op = budgetOperation(
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(op)
        }
        assertEquals(null, writer.lastOperationId)
    }

    @Test
    fun budget_create_with_non_null_base_version_is_rejected() = runTest {
        val writer = RecordingBudgetWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = budgetSnapshotJson(id = BUDGET_ID, version = 1L)
        val op = budgetOperation(
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = snapshotJson,
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(op)
        }
        assertEquals(null, writer.lastOperationId)
    }

    @Test
    fun budget_update_and_delete_with_null_base_version_are_rejected() = runTest {
        val writer = RecordingBudgetWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = budgetSnapshotJson(id = BUDGET_ID, version = null)

        val updateOp = budgetOperation(
            operationType = "UPDATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(updateOp)
        }

        val deleteOp = budgetOperation(
            operationType = "DELETE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(deleteOp)
        }
        assertEquals(null, writer.lastOperationId)
    }

    private class RecordingBudgetWriter(
        var budgetResult: ConditionalRemoteWriteResult<BudgetDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastBudgetDto: BudgetDto? = null

        override suspend fun writeBudget(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: BudgetDto,
        ): ConditionalRemoteWriteResult<BudgetDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastBudgetDto = dto
            return budgetResult
        }

        override suspend fun writeProfile(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: ProfileDto,
        ): ConditionalRemoteWriteResult<ProfileDto> = error("Test kapsamı dışı")

        override suspend fun writeCategory(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: CategoryDto,
        ): ConditionalRemoteWriteResult<CategoryDto> = error("Test kapsamı dışı")

        override suspend fun writeTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: TransactionDto,
        ): ConditionalRemoteWriteResult<TransactionDto> = error("Test kapsamı dışı")

        override suspend fun writeRecurringTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: com.feniqo.mobile.data.remote.dto.RecurringTransactionDto,
        ): ConditionalRemoteWriteResult<com.feniqo.mobile.data.remote.dto.RecurringTransactionDto> = error("Test kapsamı dışı")
    }


    private companion object {
        const val OP_ID = "0123456789abcdef0123456789abcdef"
        const val BUDGET_ID = "bgt-12345678-abcd-1234-abcd-123456789abc"
        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val CATEGORY_ID = "cat-12345678-abcd-1234-abcd-123456789abc"
        const val MONTH = "2026-08"
        const val CREATED_AT = "2026-08-25T17:00:00Z"

        fun remoteBudget(version: Long, limitMinor: Long = 500000L) = BudgetDto(
            id = BUDGET_ID,
            userId = USER_ID,
            workspaceId = null,
            categoryId = CATEGORY_ID,
            month = MONTH,
            limitMinor = limitMinor,
            currency = "TRY",
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
            deletedAt = null,
            version = version,
        )

        fun budgetSnapshotJson(
            id: String,
            version: Long?,
            limit: Long = 500000L,
        ): String = """
            {
                "id": "$id",
                "user_id": "$USER_ID",
                "workspace_id": null,
                "category_id": "$CATEGORY_ID",
                "month": "$MONTH",
                "limit_minor": $limit,
                "currency": "TRY",
                "created_at": "$CREATED_AT",
                "version": ${version ?: "null"}
            }
        """.trimIndent()

        fun budgetOperation(
            operationType: String = "CREATE",
            baseVersion: Long? = null,
            payloadJson: String? = null,
        ) = SyncOperationEntity(
            operationId = OP_ID,
            entityTypeCode = "BUDGET",
            entityId = BUDGET_ID,
            operationTypeCode = operationType,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
    }
}
