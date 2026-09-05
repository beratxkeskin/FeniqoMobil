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
import com.feniqo.mobile.data.remote.dto.WorkspaceDto

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

    @Test
    fun workspace_v2_executor_create_dispatches_cleanly_to_writer() = runTest {
        val testWsId = "550e8400-e29b-41d4-a716-446655440000"
        val remoteWs = WorkspaceDto(
            id = testWsId,
            name = "Aile Bütçesi",
            normalizedName = "aile bütçesi",
            ownerId = USER_ID,
            typeCode = "shared",
            currencyCode = "TRY",
            description = "Ortak",
            createdAt = "2026-03-01T10:00:00Z",
            updatedAt = "2026-03-01T10:00:01Z",
            deletedAt = null,
            version = 1L,
        )
        val writer = RecordingV2Writer(
            workspaceResult = ConditionalRemoteWriteResult.Applied(remoteWs),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val wsPayload = """
            {
                "id": "$testWsId",
                "name": "Aile Bütçesi",
                "type_code": "shared",
                "currency_code": "TRY",
                "description": "Ortak",
                "created_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()
        val wsOp = SyncOperationEntity(
            operationId = "op-ws-1",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = wsPayload,
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

        val result = executor.execute(wsOp)
        assertIs<OutboxExecutionResult.WorkspaceApplied>(result)
        assertEquals(1L, result.record.version)
        assertEquals("op-ws-1", writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
        assertEquals(testWsId, writer.lastWorkspacePayload?.get("id")?.toString()?.replace("\"", ""))
    }

    @Test
    fun workspace_v2_executor_update_and_delete_dispatch_and_conflict() = runTest {
        val testWsId = "550e8400-e29b-41d4-a716-446655440000"
        val remoteWs = WorkspaceDto(
            id = testWsId,
            name = "Aile Bütçesi Güncel",
            normalizedName = "aile bütçesi güncel",
            ownerId = USER_ID,
            typeCode = "shared",
            currencyCode = "TRY",
            description = null,
            createdAt = "2026-03-01T10:00:00Z",
            updatedAt = "2026-03-01T10:00:10Z",
            deletedAt = null,
            version = 2L,
        )
        val writer = RecordingV2Writer(
            workspaceResult = ConditionalRemoteWriteResult.Applied(remoteWs),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        // 1. UPDATE
        val updatePayload = """
            {
                "id": "$testWsId",
                "name": "Aile Bütçesi Güncel",
                "type_code": "shared",
                "currency_code": "TRY",
                "description": null
            }
        """.trimIndent()
        val updateOp = SyncOperationEntity(
            operationId = "op-ws-update",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = updatePayload,
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
        val updateResult = executor.execute(updateOp)
        assertIs<OutboxExecutionResult.WorkspaceApplied>(updateResult)
        assertEquals(2L, updateResult.record.version)
        assertEquals(1L, writer.lastBaseVersion)

        // 2. DELETE + NOT_FOUND -> MissingDeleteAcknowledged
        writer.workspaceResult = ConditionalRemoteWriteResult.NotFound
        val deletePayload = """{"id": "$testWsId"}"""
        val deleteOp = SyncOperationEntity(
            operationId = "op-ws-delete",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = deletePayload,
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
        val deleteResult = executor.execute(deleteOp)
        assertIs<OutboxExecutionResult.MissingDeleteAcknowledged>(deleteResult)

        // 3. CONFLICT
        writer.workspaceResult = ConditionalRemoteWriteResult.Conflict(remoteWs.copy(version = 5L))
        val conflictResult = executor.execute(updateOp)
        assertIs<OutboxExecutionResult.ConflictDetected>(conflictResult)
        assertEquals(5L, conflictResult.conflict.remoteVersion)
        assertEquals(1L, conflictResult.conflict.localVersion)
    }

    @Test
    fun workspace_v2_executor_rejects_invalid_payload_before_calling_writer() = runTest {
        val writer = RecordingV2Writer()
        val executor = V2OutboxOperationExecutor(writer) { 1000L }
        val testWsId = "550e8400-e29b-41d4-a716-446655440000"

        // 1. Forbidden key in UPDATE
        val invalidPayload = """
            {
                "id": "$testWsId",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY",
                "forbidden_field": 123
            }
        """.trimIndent()
        val opForbidden = SyncOperationEntity(
            operationId = "op-ws-invalid-1",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = invalidPayload,
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

        assertFailsWith<IllegalArgumentException> {
            executor.execute(opForbidden)
        }
        assertEquals(null, writer.lastOperationId)

        // 2. Non-UUID entityId / payload id
        val nonUuidPayload = """
            {
                "id": "ws-not-uuid",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY"
            }
        """.trimIndent()
        val opNonUuid = SyncOperationEntity(
            operationId = "op-ws-invalid-2",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-not-uuid",
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = nonUuidPayload,
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
        assertFailsWith<IllegalArgumentException> {
            executor.execute(opNonUuid)
        }
        assertEquals(null, writer.lastOperationId)

        // 3. Blank description
        val blankDescPayload = """
            {
                "id": "$testWsId",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY",
                "description": "   "
            }
        """.trimIndent()
        val opBlankDesc = SyncOperationEntity(
            operationId = "op-ws-invalid-3",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = blankDescPayload,
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
        assertFailsWith<IllegalArgumentException> {
            executor.execute(opBlankDesc)
        }
        assertEquals(null, writer.lastOperationId)

        // 4. Non-Z timezone offset timestamp
        val nonZPayload = """
            {
                "id": "$testWsId",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY",
                "created_at": "2026-03-01T10:00:00+03:00"
            }
        """.trimIndent()
        val opNonZ = SyncOperationEntity(
            operationId = "op-ws-invalid-4",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = nonZPayload,
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
        assertFailsWith<IllegalArgumentException> {
            executor.execute(opNonZ)
        }
        assertEquals(null, writer.lastOperationId)

        // 5. Leading/trailing whitespace in entityId
        val validPayload = """
            {
                "id": "$testWsId",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY"
            }
        """.trimIndent()
        val opWhitespaceEntityId = SyncOperationEntity(
            operationId = "op-ws-invalid-5",
            entityTypeCode = "WORKSPACE",
            entityId = " $testWsId",
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = validPayload,
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
        assertFailsWith<IllegalArgumentException> {
            executor.execute(opWhitespaceEntityId)
        }
        assertEquals(null, writer.lastOperationId)

        // 6. Leading/trailing whitespace in payload id
        val whitespacePayloadId = """
            {
                "id": "$testWsId ",
                "name": "Yeni Ad",
                "type_code": "shared",
                "currency_code": "TRY"
            }
        """.trimIndent()
        val opWhitespacePayloadId = SyncOperationEntity(
            operationId = "op-ws-invalid-6",
            entityTypeCode = "WORKSPACE",
            entityId = testWsId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = whitespacePayloadId,
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
        assertFailsWith<IllegalArgumentException> {
            executor.execute(opWhitespacePayloadId)
        }
        assertEquals(null, writer.lastOperationId)
    }

    private class RecordingV2Writer(
        var categoryResult: ConditionalRemoteWriteResult<CategoryDto> = ConditionalRemoteWriteResult.NotFound,
        var workspaceResult: ConditionalRemoteWriteResult<WorkspaceDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastCategoryDto: CategoryDto? = null
        var lastWorkspacePayload: kotlinx.serialization.json.JsonObject? = null

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

        override suspend fun writeWorkspace(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            payload: kotlinx.serialization.json.JsonObject,
        ): ConditionalRemoteWriteResult<WorkspaceDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastWorkspacePayload = payload
            return workspaceResult
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

        override suspend fun writeSubscription(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: com.feniqo.mobile.data.remote.dto.SubscriptionDto,
        ): ConditionalRemoteWriteResult<com.feniqo.mobile.data.remote.dto.SubscriptionDto> = error("Test kapsamı dışı")
    }



    private class RecordingDao(
        var category: CategoryEntity? = null,
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = category?.takeIf { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun getSubscriptionRow(id: String): com.feniqo.mobile.data.local.entity.SubscriptionEntity? = null
        override suspend fun getGoalRow(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        override suspend fun getWorkspaceRow(id: String): com.feniqo.mobile.data.local.entity.WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = OP_ID
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 1
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) {
            category = entities.single()
        }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertRecurringTransactionRows(entities: List<com.feniqo.mobile.data.local.entity.RecurringTransactionEntity>) = Unit
        override suspend fun upsertSubscriptionRows(entities: List<com.feniqo.mobile.data.local.entity.SubscriptionEntity>) = Unit
        override suspend fun upsertGoalRows(entities: List<com.feniqo.mobile.data.local.entity.GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<com.feniqo.mobile.data.local.entity.GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<com.feniqo.mobile.data.local.entity.DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) {
            this.conflict = conflict
        }
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 1
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 1
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalContributionConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtPaymentConflict(entityId: String, error: String): Int = 1
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
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
