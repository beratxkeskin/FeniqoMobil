package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RecurringTransactionV2OutboxExecutorTest {

    @Test
    fun recurring_transaction_create_applied_returns_recurring_transaction_applied() = runTest {
        val remoteDto = remoteRecurringTransaction(version = 1L)
        val writer = RecordingRecurringTransactionWriter(
            recurringResult = ConditionalRemoteWriteResult.Applied(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = REC_ID, version = null)
        val op = recurringTransactionOperation(
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.RecurringTransactionApplied>(result)
        assertEquals(REC_ID, result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(OP_ID, writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
        assertEquals(50000L, writer.lastRecurringDto?.amountMinor)
    }

    @Test
    fun recurring_transaction_update_conflict_returns_conflict_detected_and_preserves_remote_version() = runTest {
        val remoteDto = remoteRecurringTransaction(version = 4L, amountMinor = 75000L)
        val writer = RecordingRecurringTransactionWriter(
            recurringResult = ConditionalRemoteWriteResult.Conflict(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 2000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = REC_ID, version = 2L, amountMinor = 60000L)
        val op = recurringTransactionOperation(
            operationType = "UPDATE",
            baseVersion = 2L,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals(OP_ID, result.conflict.operationId)
        assertEquals("RECURRING_TRANSACTION", result.conflict.entityTypeCode)
        assertEquals(REC_ID, result.conflict.entityId)
        assertEquals(2L, result.conflict.localVersion)
        assertEquals(4L, result.conflict.remoteVersion)
        assertEquals(2000L, result.conflict.detectedAtEpochMillis)
        assertEquals(RemoteWriteOperation.UPDATE, writer.lastOperation)
        assertEquals(2L, writer.lastBaseVersion)
    }

    @Test
    fun recurring_transaction_delete_not_found_returns_missing_delete_acknowledged() = runTest {
        val writer = RecordingRecurringTransactionWriter(
            recurringResult = ConditionalRemoteWriteResult.NotFound,
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = REC_ID, version = 3L)
        val op = recurringTransactionOperation(
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
    fun recurring_transaction_rejects_payload_id_mismatch_without_calling_writer() = runTest {
        val writer = RecordingRecurringTransactionWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = "different-rec-id", version = null)
        val op = recurringTransactionOperation(
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
    fun recurring_transaction_create_with_non_null_base_version_is_rejected() = runTest {
        val writer = RecordingRecurringTransactionWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = REC_ID, version = 1L)
        val op = recurringTransactionOperation(
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
    fun recurring_transaction_update_and_delete_with_null_base_version_are_rejected() = runTest {
        val writer = RecordingRecurringTransactionWriter()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = recurringTransactionSnapshotJson(id = REC_ID, version = null)

        val updateOp = recurringTransactionOperation(
            operationType = "UPDATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(updateOp)
        }

        val deleteOp = recurringTransactionOperation(
            operationType = "DELETE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(deleteOp)
        }
        assertEquals(null, writer.lastOperationId)
    }

    private class RecordingRecurringTransactionWriter(
        var recurringResult: ConditionalRemoteWriteResult<RecurringTransactionDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastRecurringDto: RecurringTransactionDto? = null

        override suspend fun writeRecurringTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: RecurringTransactionDto,
        ): ConditionalRemoteWriteResult<RecurringTransactionDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastRecurringDto = dto
            return recurringResult
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

        override suspend fun writeBudget(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: BudgetDto,
        ): ConditionalRemoteWriteResult<BudgetDto> = error("Test kapsamı dışı")
    }

    private companion object {
        const val OP_ID = "0123456789abcdef0123456789abcdef"
        const val REC_ID = "rec-uuid-1"
        const val USER_ID = "usr-uuid-1"
        const val CAT_ID = "cat-uuid-1"

        fun recurringTransactionOperation(
            operationType: String,
            baseVersion: Long?,
            payloadJson: String?,
        ): SyncOperationEntity = SyncOperationEntity(
            operationId = OP_ID,
            entityTypeCode = "RECURRING_TRANSACTION",
            entityId = REC_ID,
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

        fun recurringTransactionSnapshotJson(
            id: String,
            version: Long?,
            amountMinor: Long = 50000L,
        ): String {
            val vStr = if (version == null) "null" else "$version"
            return """{"id":"$id","user_id":"$USER_ID","workspace_id":null,"amount_minor":$amountMinor,"currency":"TRY","type":"expense","category_id":"$CAT_ID","description":"Internet","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"last_generated_date":null,"is_active":true,"created_at":"2026-08-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":$vStr}"""
        }

        fun remoteRecurringTransaction(
            version: Long,
            amountMinor: Long = 50000L,
        ): RecurringTransactionDto = RecurringTransactionDto(
            id = REC_ID,
            userId = USER_ID,
            workspaceId = null,
            amountMinor = amountMinor,
            currency = "TRY",
            type = "expense",
            categoryId = CAT_ID,
            description = "Internet",
            paymentMethod = "CREDIT_CARD",
            frequency = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = "2026-08-01T10:00:00Z",
            deletedAt = null,
            version = version,
        )
    }
}
