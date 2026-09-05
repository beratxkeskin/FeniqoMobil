package com.feniqo.mobile.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceOutboxProcessorTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private var opCounter = 1
    private fun testOpIdFactory(): String = (opCounter++).toString().padStart(32, '0')

    private fun sampleWorkspace(
        id: String = "550e8400-e29b-41d4-a716-446655440000",
        ownerId: String = "user-1",
        name: String = "Ortak Aile",
        typeCode: String = "shared",
        currencyCode: String = "TRY",
        description: String? = "Aile bütçesi",
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        ownerId = ownerId,
        typeCode = typeCode,
        currencyCode = currencyCode,
        description = description,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleOwnerMember(
        workspaceId: String = "550e8400-e29b-41d4-a716-446655440000",
        userId: String = "user-1",
        syncStatus: String = "PENDING_CREATE",
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = "OWNER",
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 0L,
            baseVersion = null,
            lastSyncError = null,
        ),
    )

    private class FakeRemoteWriter(
        var workspaceResult: ConditionalRemoteWriteResult<WorkspaceDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastWorkspacePayload: JsonObject? = null

        var onWriteWorkspaceCalled: CompletableDeferred<Unit>? = null
        var writeWorkspaceGate: CompletableDeferred<Unit>? = null

        override suspend fun writeWorkspace(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            payload: JsonObject,
        ): ConditionalRemoteWriteResult<WorkspaceDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastWorkspacePayload = payload

            onWriteWorkspaceCalled?.complete(Unit)
            writeWorkspaceGate?.await()

            return workspaceResult
        }

        override suspend fun writeCategory(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: CategoryDto,
        ): ConditionalRemoteWriteResult<CategoryDto> = error("Test kapsamı dışı")

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
            dto: SubscriptionDto,
        ): ConditionalRemoteWriteResult<SubscriptionDto> = error("Test kapsamı dışı")
    }

    @Test
    fun create_workspace_claims_outbox_dispatches_clean_payload_applies_and_syncs_room() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = operationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1000L },
            )
            val wsId = "550e8400-e29b-41d4-a716-446655440000"
            val workspace = sampleWorkspace(id = wsId, ownerId = "user-1", name = "Aile Bütçesi 🚀")
            val ownerMember = sampleOwnerMember(workspaceId = wsId, userId = "user-1")

            val payloadJson = WorkspacePayloadCodec.encode(
                entity = workspace,
                operationType = OutboxOperationType.CREATE,
                includeCreatedAtInCreate = true,
            )

            val enqueueResult = mutationDao.mutateWorkspaceV2(
                entity = workspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = payloadJson,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 1000L,
            )

            val remoteDto = WorkspaceDto(
                id = wsId,
                name = "Aile Bütçesi 🚀",
                normalizedName = "aile bütçesi 🚀",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = "Aile bütçesi",
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val writer = FakeRemoteWriter(
                workspaceResult = ConditionalRemoteWriteResult.Applied(remoteDto),
            )
            val v2Executor = V2OutboxOperationExecutor(writer) { 2000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(op: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(op: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(op: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = database.remoteSyncDao(),
            ) { 2000L }

            val executor = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)
            val processor = OutboxProcessor(RoomOutboxQueue(queue), executor)

            val result = processor.processReadyOperations()

            assertEquals(1, result.succeededCount)
            assertNull(result.failedOperationId)
            assertNull(result.conflictOperationId)

            // 1. Outbox silindi mi?
            assertNull(operationDao.getById(enqueueResult.operationId))

            // 2. Workspace SYNCED oldu mu?
            val stored = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(stored)
            assertEquals("Aile Bütçesi 🚀", stored.name)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertNull(stored.sync.baseVersion)

            // 3. Writer'a giden payload D3 allowlist dışında alan içermemeli
            val sentPayload = writer.lastWorkspacePayload
            assertNotNull(sentPayload)
            val allowedKeys = setOf("id", "name", "type_code", "currency_code", "description", "created_at")
            val forbiddenKeys = sentPayload.keys - allowedKeys
            assertTrue(forbiddenKeys.isEmpty(), "Payload yasak alan içeriyor: $forbiddenKeys")
        } finally {
            database.close()
        }
    }

    @Test
    fun update_predecessor_applied_unblocks_successor_with_correct_rebase_and_preserves_local_changes() = runTest {
        val database = inMemoryDatabase()
        try {
            var currentTime = 1000L
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = operationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { currentTime },
            )
            val wsId = "550e8400-e29b-41d4-a716-446655440000"

            // 1. Initial CREATE outbox
            val initialWorkspace = sampleWorkspace(id = wsId, name = "Eski İsim")
            val ownerMember = sampleOwnerMember(workspaceId = wsId, userId = "user-1")
            val createPayload = WorkspacePayloadCodec.encode(
                entity = initialWorkspace,
                operationType = OutboxOperationType.CREATE,
                includeCreatedAtInCreate = true,
            )
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = createPayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 1000L,
            )

            // 2. Gate kullanarak ilk CREATE işlemini gerçek OutboxProcessor üzerinden başlat
            val calledSignal = CompletableDeferred<Unit>()
            val releaseGate = CompletableDeferred<Unit>()

            val remoteCreateDto = WorkspaceDto(
                id = wsId,
                name = "Eski İsim",
                normalizedName = "eski isim",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = "Aile bütçesi",
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )
            val writer = FakeRemoteWriter(
                workspaceResult = ConditionalRemoteWriteResult.Applied(remoteCreateDto),
            ).apply {
                onWriteWorkspaceCalled = calledSignal
                writeWorkspaceGate = releaseGate
            }

            val v2Executor = V2OutboxOperationExecutor(writer) { 2500L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(op: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(op: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(op: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = database.remoteSyncDao(),
            ) { 2500L }

            val executor = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)
            val processor = OutboxProcessor(RoomOutboxQueue(queue), executor)

            // Processor CREATE işlemini asenkron claim edip writer'ı çağıracak
            val asyncProcess = async {
                processor.processReadyOperations(limit = 1)
            }

            // Writer çağrılana kadar bekle (bu sırada işlem Room içinde IN_FLIGHT durumundadır)
            calledSignal.await()

            val inFlightOp = operationDao.getById(createResult.operationId)
            assertNotNull(inFlightOp)
            assertEquals("IN_FLIGHT", inFlightOp.statusCode)

            // 3. Predecessor IN_FLIGHT iken UPDATE successor ekle
            currentTime = 2000L
            val updatedWorkspace = sampleWorkspace(
                id = wsId,
                name = "Yeni Yerel İsim (Successor)",
                syncStatus = "PENDING_UPDATE",
                version = 0L,
                baseVersion = null,
            )
            val updatePayload = """
                {
                    "id": "$wsId",
                    "name": "Yeni Yerel İsim (Successor)",
                    "type_code": "shared",
                    "currency_code": "TRY",
                    "description": "Aile bütçesi"
                }
            """.trimIndent()
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = updatedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )

            // Successor başlangıçta blocked olmalı
            val successorBefore = operationDao.getById(updateResult.operationId)
            assertNotNull(successorBefore)
            assertTrue(successorBefore.isBlocked)

            // 4. Writer gate'ini açıp predecessor'ın OutboxProcessor + ACK zinciriyle tamamlanmasını sağla
            releaseGate.complete(Unit)
            val createProcessResult = asyncProcess.await()
            assertEquals(1, createProcessResult.succeededCount)

            // Predecessor CREATE silindi mi?
            assertNull(operationDao.getById(createResult.operationId))

            // Successor unblock ve rebase edildi mi?
            val successorAfter = operationDao.getById(updateResult.operationId)
            assertNotNull(successorAfter)
            assertFalse(successorAfter.isBlocked)
            assertEquals(1L, successorAfter.baseVersion)
            assertEquals("PENDING", successorAfter.statusCode)

            // Yerel successor değişikliği ezilmedi mi?
            val storedWorkspace = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(storedWorkspace)
            assertEquals("Yeni Yerel İsim (Successor)", storedWorkspace.name)
            assertEquals(1L, storedWorkspace.sync.version)
            assertEquals(1L, storedWorkspace.sync.baseVersion)

            // 5. Şimdi UPDATE successor'ı da gerçek OutboxProcessor ile process et
            currentTime = 3000L
            val remoteUpdateDto = remoteCreateDto.copy(
                name = "Yeni Yerel İsim (Successor)",
                normalizedName = "yeni yerel isim (successor)",
                version = 2L,
                updatedAt = "2026-03-01T10:00:10Z",
            )
            writer.workspaceResult = ConditionalRemoteWriteResult.Applied(remoteUpdateDto)
            writer.onWriteWorkspaceCalled = null
            writer.writeWorkspaceGate = null

            val updateProcessResult = processor.processReadyOperations(limit = 1)
            assertEquals(1, updateProcessResult.succeededCount)

            // Successor da tamamlanıp silindi mi?
            assertNull(operationDao.getById(updateResult.operationId))

            val finalWorkspace = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(finalWorkspace)
            assertEquals("Yeni Yerel İsim (Successor)", finalWorkspace.name)
            assertEquals("SYNCED", finalWorkspace.sync.syncStatus)
            assertEquals(2L, finalWorkspace.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun delete_with_not_found_acknowledges_missing_delete_and_syncs_tombstone() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = operationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 3000L },
            )
            val wsId = "550e8400-e29b-41d4-a716-446655440000"

            val syncedWorkspace = sampleWorkspace(
                id = wsId,
                syncStatus = "SYNCED",
                version = 2L,
                baseVersion = 2L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val deletePayload = """{"id": "$wsId"}"""
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = syncedWorkspace.copy(
                    sync = syncedWorkspace.sync.copy(
                        syncStatus = "PENDING_DELETE",
                        deletedAtEpochMillis = 3000L,
                        baseVersion = 2L,
                    ),
                ),
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = deletePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 3000L,
            )

            // Writer NOT_FOUND döner (uzakta zaten silinmiş)
            val writer = FakeRemoteWriter(
                workspaceResult = ConditionalRemoteWriteResult.NotFound,
            )
            val v2Executor = V2OutboxOperationExecutor(writer) { 4000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(op: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(op: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(op: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = database.remoteSyncDao(),
            ) { 4000L }

            val executor = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)
            val processor = OutboxProcessor(RoomOutboxQueue(queue), executor)

            val result = processor.processReadyOperations()

            assertEquals(1, result.succeededCount)
            assertNull(result.failedOperationId)

            // Outbox silindi mi?
            assertNull(operationDao.getById(deleteResult.operationId))

            // Yerel tombstone SYNCED oldu mu?
            val stored = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(stored)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(3000L, stored.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun create_with_not_found_fails_closed_and_does_not_sync_workspace() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = operationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1000L },
            )
            val wsId = "550e8400-e29b-41d4-a716-446655440000"

            val workspace = sampleWorkspace(id = wsId, ownerId = "user-1", name = "Aile Bütçesi")
            val ownerMember = sampleOwnerMember(workspaceId = wsId, userId = "user-1")

            val payloadJson = WorkspacePayloadCodec.encode(
                entity = workspace,
                operationType = OutboxOperationType.CREATE,
                includeCreatedAtInCreate = true,
            )

            val createResult = mutationDao.mutateWorkspaceV2(
                entity = workspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = payloadJson,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 1000L,
            )

            // Writer CREATE için beklenmedik biçimde NOT_FOUND döner
            val writer = FakeRemoteWriter(
                workspaceResult = ConditionalRemoteWriteResult.NotFound,
            )
            val v2Executor = V2OutboxOperationExecutor(writer) { 2000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(op: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(op: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(op: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = database.remoteSyncDao(),
            ) { 2000L }

            val executor = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)
            val processor = OutboxProcessor(RoomOutboxQueue(queue), executor)

            val result = processor.processReadyOperations()

            assertEquals(0, result.succeededCount)
            assertEquals(createResult.operationId, result.failedOperationId)
            assertNotNull(result.lastError)

            // Outbox silinmemeli, FAILED olarak işaretlenmeli
            val op = operationDao.getById(createResult.operationId)
            assertNotNull(op)
            assertEquals("FAILED", op.statusCode)
            assertEquals(1, op.attemptCount)

            // Workspace yanlışlıkla SYNCED olmamalı
            val stored = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(stored)
            assertEquals("PENDING_CREATE", stored.sync.syncStatus)
            assertEquals(0L, stored.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun update_with_not_found_fails_closed_and_does_not_sync_workspace() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = operationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 2000L },
            )
            val wsId = "550e8400-e29b-41d4-a716-446655440000"

            val syncedWorkspace = sampleWorkspace(
                id = wsId,
                name = "Mevcut Yerel Ad",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val updatePayload = """
                {
                    "id": "$wsId",
                    "name": "Yeni Değiştirilmiş Ad",
                    "type_code": "shared",
                    "currency_code": "TRY",
                    "description": "Aile bütçesi"
                }
            """.trimIndent()
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = syncedWorkspace.copy(
                    name = "Yeni Değiştirilmiş Ad",
                    sync = syncedWorkspace.sync.copy(
                        syncStatus = "PENDING_UPDATE",
                        baseVersion = 1L,
                    ),
                ),
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )

            // Writer UPDATE için beklenmedik biçimde NOT_FOUND döner
            val writer = FakeRemoteWriter(
                workspaceResult = ConditionalRemoteWriteResult.NotFound,
            )
            val v2Executor = V2OutboxOperationExecutor(writer) { 3000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(op: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(op: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(op: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = database.remoteSyncDao(),
            ) { 3000L }

            val executor = ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor)
            val processor = OutboxProcessor(RoomOutboxQueue(queue), executor)

            val result = processor.processReadyOperations()

            assertEquals(0, result.succeededCount)
            assertEquals(updateResult.operationId, result.failedOperationId)
            assertNotNull(result.lastError)

            // Outbox silinmemeli, FAILED olarak işaretlenmeli
            val op = operationDao.getById(updateResult.operationId)
            assertNotNull(op)
            assertEquals("FAILED", op.statusCode)
            assertEquals(1, op.attemptCount)

            // Workspace yanlışlıkla SYNCED olmamalı, PENDING_UPDATE korunmalı
            val stored = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(stored)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
            assertEquals("Yeni Değiştirilmiş Ad", stored.name)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            database.close()
        }
    }
}
