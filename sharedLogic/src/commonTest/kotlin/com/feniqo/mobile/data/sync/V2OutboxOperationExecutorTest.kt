package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
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

class V2OutboxOperationExecutorTest {

    @Test
    fun v2_executor_uses_payload_json_snapshot_and_never_reads_room_entity() = runTest {
        val writer = RecordingV2Writer(
            categoryResult = ConditionalRemoteWriteResult.Applied(remoteCategory(version = 5)),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = """
            {
                "id": "$ENTITY_ID",
                "user_id": "$USER_ID",
                "name": "Snapshot Category",
                "type": "expense",
                "color": "#123456",
                "created_at": "$CREATED_AT",
                "version": null
            }
        """.trimIndent()

        val op = SyncOperationEntity(
            operationId = OP_ID,
            entityTypeCode = "CATEGORY",
            entityId = ENTITY_ID,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = snapshotJson,
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

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.CategoryApplied>(result)
        assertEquals(5L, result.record.version)
        assertEquals(OP_ID, writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
        assertEquals("Snapshot Category", writer.lastCategoryDto?.name)
    }

    @Test
    fun v2_executor_rejects_null_or_blank_payload() = runTest {
        val writer = RecordingV2Writer()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = operation(protocolVersion = 2, payloadJson = null)
        assertFailsWith<IllegalArgumentException> {
            executor.execute(op)
        }

        val blankOp = operation(protocolVersion = 2, payloadJson = "   ")
        assertFailsWith<IllegalArgumentException> {
            executor.execute(blankOp)
        }
    }

    @Test
    fun v2_executor_rejects_dto_id_mismatch() = runTest {
        val writer = RecordingV2Writer()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = """
            {
                "id": "different-id",
                "user_id": "$USER_ID",
                "name": "Category",
                "type": "expense",
                "color": "#123456",
                "created_at": "$CREATED_AT"
            }
        """.trimIndent()

        val op = operation(protocolVersion = 2, payloadJson = snapshotJson)
        val error = assertFailsWith<IllegalArgumentException> {
            executor.execute(op)
        }
        assertEquals(true, error.message?.contains("uyuşmuyor"))
    }

    @Test
    fun v2_executor_enforces_base_version_invariants() = runTest {
        val writer = RecordingV2Writer()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = """
            {
                "id": "$ENTITY_ID",
                "name": "Category",
                "type": "expense",
                "color": "#123456",
                "created_at": "$CREATED_AT"
            }
        """.trimIndent()

        // CREATE with baseVersion != null -> fail
        val createWithBase = operation(
            protocolVersion = 2,
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(createWithBase)
        }

        // UPDATE with baseVersion == null -> fail
        val updateWithoutBase = operation(
            protocolVersion = 2,
            operationType = "UPDATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(updateWithoutBase)
        }

        // DELETE with baseVersion == null -> fail
        val deleteWithoutBase = operation(
            protocolVersion = 2,
            operationType = "DELETE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )
        assertFailsWith<IllegalArgumentException> {
            executor.execute(deleteWithoutBase)
        }
    }

    @Test
    fun protocol_routing_dispatches_v1_and_v2_cleanly() = runTest {
        val v2Writer = RecordingV2Writer(
            categoryResult = ConditionalRemoteWriteResult.Applied(remoteCategory(version = 2)),
        )
        val v2Executor = V2OutboxOperationExecutor(v2Writer) { 1000L }
        val v1Writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
            override suspend fun writeCategory(
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: CategoryDto,
            ): ConditionalRemoteWriteResult<CategoryDto> = error("V1 writer called")

            override suspend fun writeProfile(
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: ProfileDto,
            ): ConditionalRemoteWriteResult<ProfileDto> = error("V1 writer called")

            override suspend fun writeTransaction(
                operation: RemoteWriteOperation,
                baseVersion: Long?,
                dto: TransactionDto,
            ): ConditionalRemoteWriteResult<TransactionDto> = error("V1 writer called")
        }
        val v1Executor = V1OutboxOperationExecutor(
            remoteSyncDao = RecordingDao(localCategory()),
            writer = v1Writer,
            nowEpochMillisProvider = { 1000L },
        )
        val router = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)

        // Protocol 1 -> routes to V1
        val v1Op = operation(protocolVersion = 1, operationType = "UPDATE", baseVersion = 2L, payloadJson = null)
        val v1Error = assertFailsWith<IllegalStateException> {
            router.execute(v1Op)
        }
        assertEquals("V1 writer called", v1Error.message)

        // Protocol 2 -> routes to V2
        val snapshotJson = """
            {
                "id": "$ENTITY_ID",
                "name": "Category",
                "type": "expense",
                "color": "#123456",
                "created_at": "$CREATED_AT"
            }
        """.trimIndent()
        val v2Op = operation(protocolVersion = 2, operationType = "CREATE", baseVersion = null, payloadJson = snapshotJson)
        val v2Result = router.execute(v2Op)
        assertIs<OutboxExecutionResult.CategoryApplied>(v2Result)

        // Unknown protocol -> rejected fail-closed
        val invalidOp = operation(protocolVersion = 99)
        val error = assertFailsWith<IllegalArgumentException> {
            router.execute(invalidOp)
        }
        assertEquals(true, error.message?.contains("Bilinmeyen protokol sürümü: 99"))
    }

    @Test
    fun v2_executor_returns_conflict_detected_result_without_room_writes() = runTest {
        val writer = RecordingV2Writer(
            categoryResult = ConditionalRemoteWriteResult.Conflict(remoteCategory(version = 3)),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = """
            {
                "id": "$ENTITY_ID",
                "name": "Local Name",
                "type": "expense",
                "color": "#123456",
                "created_at": "$CREATED_AT"
            }
        """.trimIndent()

        val op = operation(
            protocolVersion = 2,
            operationType = "UPDATE",
            baseVersion = 1L,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals(OP_ID, result.conflict.operationId)
        assertEquals(1L, result.conflict.localVersion)
        assertEquals(3L, result.conflict.remoteVersion)
    }

    private class RecordingV2Writer(
        var categoryResult: ConditionalRemoteWriteResult<CategoryDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastCategoryDto: CategoryDto? = null

        override suspend fun writeCategory(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: CategoryDto,
        ): ConditionalRemoteWriteResult<CategoryDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastCategoryDto = dto
            return categoryResult
        }

        override suspend fun writeProfile(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: ProfileDto,
        ): ConditionalRemoteWriteResult<ProfileDto> = error("Test kapsamı dışı")

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

        override suspend fun writeRecurringTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: RecurringTransactionDto,
        ): ConditionalRemoteWriteResult<RecurringTransactionDto> = error("Test kapsamı dışı")
    }


    private class RecordingDao(
        var category: CategoryEntity? = null,
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = category?.takeIf { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = OP_ID
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) {
            category = entities.single()
        }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertRecurringTransactionRows(entities: List<com.feniqo.mobile.data.local.entity.RecurringTransactionEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) {
            this.conflict = conflict
        }
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 1
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 1
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
    }


    private companion object {
        const val OP_ID = "0123456789abcdef0123456789abcdef"
        const val ENTITY_ID = "2d98a8d8-8e8a-a181-ca24-573d825edfbb"
        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val CREATED_AT = "2026-08-25T17:00:00Z"

        fun localCategory() = CategoryEntity(
            id = ENTITY_ID,
            ownerId = USER_ID,
            workspaceId = null,
            scopeKey = USER_ID,
            name = "Yerel kategori",
            normalizedName = "yerel kategori",
            slug = "yerel-kategori",
            typeCode = "EXPENSE",
            colorHex = "#123456",
            iconKey = null,
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 2,
                baseVersion = 2,
                lastSyncError = null,
            ),
        )

        fun remoteCategory(version: Long) = CategoryDto(
            id = ENTITY_ID,
            userId = USER_ID,
            name = "Uzak kategori",
            type = "expense",
            color = "#123456",
            createdAt = CREATED_AT,
            version = version,
        )

        fun operation(
            protocolVersion: Int = 2,
            operationType: String = "CREATE",
            baseVersion: Long? = null,
            payloadJson: String? = null,
        ) = SyncOperationEntity(
            operationId = OP_ID,
            entityTypeCode = "CATEGORY",
            entityId = ENTITY_ID,
            operationTypeCode = operationType,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = protocolVersion,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
    }
}
