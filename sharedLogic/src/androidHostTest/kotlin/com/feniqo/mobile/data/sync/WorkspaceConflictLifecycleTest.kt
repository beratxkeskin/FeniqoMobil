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
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.repository.OfflineFirstSyncRepository
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
class WorkspaceConflictLifecycleTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private var opCounter = 1
    private fun testOpIdFactory(): String = (opCounter++).toString().padStart(32, '0')

    private fun sampleWorkspace(
        id: String,
        ownerId: String = "user-1",
        name: String = "Test WS",
        typeCode: String = "personal",
        currencyCode: String = "TRY",
        description: String? = null,
        syncStatus: String = "SYNCED",
        version: Long = 1L,
        baseVersion: Long? = 1L,
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
        workspaceId: String,
        userId: String = "user-1",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
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
            version = version,
            baseVersion = version,
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
        var callCount: Int = 0

        override suspend fun writeWorkspace(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            payload: JsonObject,
        ): ConditionalRemoteWriteResult<WorkspaceDto> {
            callCount++
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastWorkspacePayload = payload
            return workspaceResult
        }
    }

    private class FakeCoreRemoteDataSource(
        var workspacePages: List<RemotePage<WorkspaceDto>> = emptyList(),
        var memberPagesByWorkspaceId: Map<String, List<RemotePage<WorkspaceMemberDto>>> = emptyMap(),
    ) : CoreRemoteDataSource {
        override suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
            val pageIndex = query.page.pageIndex
            return workspacePages.getOrElse(pageIndex) {
                RemotePage(items = emptyList(), request = query.page, totalCount = 0)
            }
        }

        override suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> {
            val wsId = query.workspaceId?.value
            val pageIndex = query.page.pageIndex
            val pages = memberPagesByWorkspaceId[wsId] ?: emptyList()
            return pages.getOrElse(pageIndex) {
                RemotePage(items = emptyList(), request = query.page, totalCount = 0)
            }
        }

        override suspend fun fetchProfile(userId: String): ProfileDto? = null
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> =
            RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> =
            RemotePage(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    private class FakeAuthRepository(initialSession: AuthSession?) : AuthRepository {
        private val sessionFlow = MutableStateFlow(initialSession)
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("N/A")
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("N/A")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("N/A")
        override suspend fun signOut(): RepositoryResult<Unit> = error("N/A")
    }

    private val userSession = AuthSession(
        userId = EntityId("user-1"),
        email = "user-1@example.com",
        expiresAt = Instant.fromEpochMilliseconds(100_000L),
    )

    @Test
    fun keepRemote_inbound_conflict_resolved_clears_outbox_and_skips_processor_write() = runTest {
        val db = inMemoryDatabase()
        try {
            val syncDao = db.remoteSyncDao()
            val syncStateDao = db.syncStateDao()
            val mutationDao = db.localMutationDao()
            val workspaceDao = db.workspaceDao()
            val opDao = db.syncOperationDao()

            val wsId = "550e8400-e29b-41d4-a716-446655440001"
            val initialWs = sampleWorkspace(id = wsId, name = "Yerel Başlangıç", version = 1L, baseVersion = 1L)
            val ownerMember = sampleOwnerMember(workspaceId = wsId)
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(initialWs),
                members = listOf(ownerMember),
                cursors = listOf(
                    WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(
                        WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = wsId, userId = "user-1"),
                    ),
                ),
            )

            // 1. Pending yerel mutasyon: UPDATE
            val localUpdatedWs = initialWs.copy(
                name = "Yerel Pending Değişiklik",
                sync = initialWs.sync.copy(
                    syncStatus = "PENDING_UPDATE",
                    localUpdatedAtEpochMillis = 2000L,
                ),
            )
            val updatePayload = WorkspacePayloadCodec.encode(localUpdatedWs, OutboxOperationType.UPDATE)
            mutationDao.mutateWorkspaceV2(
                entity = localUpdatedWs,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )
            assertEquals(1, syncDao.getAllWorkspaceOperations(wsId).size)

            // 2. Inbound pull: Daha yeni remote kayıt (version = 4 > baseVersion = 1)
            val remoteDto = WorkspaceDto(
                id = wsId,
                name = "Sunucu Güncel Başlık",
                normalizedName = "sunucu güncel başlık",
                ownerId = "user-1",
                typeCode = "personal",
                currencyCode = "TRY",
                description = "Uzak açıklama",
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-02T12:00:00Z",
                deletedAt = null,
                version = 4L,
            )
            val remoteDataSource = FakeCoreRemoteDataSource(
                workspacePages = listOf(
                    RemotePage(items = listOf(remoteDto), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            )
            val workspaceIncrementalSync = WorkspaceIncrementalRemoteSync(
                remote = remoteDataSource,
                remoteSyncDao = syncDao,
                syncStateDao = syncStateDao,
                nowEpochMillisProvider = { 3000L },
            )

            val pullResult = workspaceIncrementalSync.pull()
            assertEquals(1, pullResult.conflictCount)

            // Room'da çakışma oluştuğunu doğrula
            val wsInConflict = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(wsInConflict)
            assertEquals("CONFLICT", wsInConflict.sync.syncStatus)
            val conflictRow = syncStateDao.getConflict(wsId)
            assertNotNull(conflictRow)
            assertEquals(4L, conflictRow.remoteVersion)

            // 3. Kullanıcı KEEP_REMOTE çözümü çağırır
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = opDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 4000L },
            )
            val writer = FakeRemoteWriter()
            val v2Executor = V2OutboxOperationExecutor(writer) { 4000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(operation: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(operation: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(operation: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = syncDao,
            ) { 4000L }
            val outboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor))
            val repository = OfflineFirstSyncRepository(
                authRepository = FakeAuthRepository(userSession),
                initialRemoteSync = InitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                workspaceInitialRemoteSync = WorkspaceInitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                outboxProcessor = outboxProcessor,
                incrementalRemoteSync = IncrementalRemoteSync(remoteDataSource, syncDao, syncStateDao) { 3000L },
                workspaceIncrementalRemoteSync = workspaceIncrementalSync,
                offlineWriteQueue = queue,
                syncStateDao = syncStateDao,
                remoteSyncDao = syncDao,
                conflictRecoveryService = ConflictRecoveryService(syncStateDao, opDao, mutationDao) { 3000L },
                nowEpochMillisProvider = { 3500L },
            )

            val resolveResult = repository.resolveConflict(EntityId(wsId), ConflictResolution.KEEP_REMOTE)
            assertTrue(resolveResult is RepositoryResult.Success)

            // Doğrulama: Uzak snapshot uygulandı, outbox ve conflict silindi
            val resolvedWs = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(resolvedWs)
            assertEquals("Sunucu Güncel Başlık", resolvedWs.name)
            assertEquals("SYNCED", resolvedWs.sync.syncStatus)
            assertEquals(4L, resolvedWs.sync.version)
            assertNull(resolvedWs.sync.baseVersion)

            assertNull(syncStateDao.getConflict(wsId))
            assertEquals(0, syncDao.getAllWorkspaceOperations(wsId).size)

            // 4. Sonraki outbox turunda Workspace writer çağrılmamalı
            val outboxRun = outboxProcessor.processReadyOperations()
            assertEquals(0, outboxRun.succeededCount)
            assertEquals(0, writer.callCount)
            assertNull(writer.lastOperationId)
        } finally {
            db.close()
        }
    }

    @Test
    fun keepLocal_update_inbound_conflict_resolved_rebases_and_processor_applies_and_syncs() = runTest {
        val db = inMemoryDatabase()
        try {
            val syncDao = db.remoteSyncDao()
            val syncStateDao = db.syncStateDao()
            val mutationDao = db.localMutationDao()
            val workspaceDao = db.workspaceDao()
            val opDao = db.syncOperationDao()

            val wsId = "550e8400-e29b-41d4-a716-446655440002"
            val initialWs = sampleWorkspace(id = wsId, name = "Eski Başlık", version = 2L, baseVersion = 2L)
            val ownerMember = sampleOwnerMember(workspaceId = wsId)
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(initialWs),
                members = listOf(ownerMember),
                cursors = listOf(
                    WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(
                        WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = wsId, userId = "user-1"),
                    ),
                ),
            )

            // 1. Pending yerel UPDATE mutasyonu
            val localUpdatedWs = initialWs.copy(
                name = "Yerel Kazanan Başlık",
                sync = initialWs.sync.copy(
                    syncStatus = "PENDING_UPDATE",
                    localUpdatedAtEpochMillis = 2000L,
                ),
            )
            val updatePayload = WorkspacePayloadCodec.encode(localUpdatedWs, OutboxOperationType.UPDATE)
            val enqueueResult = mutationDao.mutateWorkspaceV2(
                entity = localUpdatedWs,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )
            val opId = enqueueResult.operationId

            // 2. Inbound pull: Çakışma oluşturan uzaktaki kayıt (version = 5 > baseVersion = 2)
            val remoteDto = WorkspaceDto(
                id = wsId,
                name = "Sunucu Çakışan Başlık",
                normalizedName = "sunucu çakışan başlık",
                ownerId = "user-1",
                typeCode = "personal",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-02T14:00:00Z",
                deletedAt = null,
                version = 5L,
            )
            val remoteDataSource = FakeCoreRemoteDataSource(
                workspacePages = listOf(
                    RemotePage(items = listOf(remoteDto), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            )
            val workspaceIncrementalSync = WorkspaceIncrementalRemoteSync(
                remote = remoteDataSource,
                remoteSyncDao = syncDao,
                syncStateDao = syncStateDao,
                nowEpochMillisProvider = { 3000L },
            )

            val pullResult = workspaceIncrementalSync.pull()
            assertEquals(1, pullResult.conflictCount)
            val wsInConflict = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(wsInConflict)
            assertEquals("CONFLICT", wsInConflict.sync.syncStatus)

            // 3. KEEP_LOCAL çözümü
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = opDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 4000L },
            )
            val writer = FakeRemoteWriter()
            val v2Executor = V2OutboxOperationExecutor(writer) { 4000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(operation: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(operation: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(operation: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = syncDao,
            ) { 4000L }
            val outboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor))
            val repository = OfflineFirstSyncRepository(
                authRepository = FakeAuthRepository(userSession),
                initialRemoteSync = InitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                workspaceInitialRemoteSync = WorkspaceInitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                outboxProcessor = outboxProcessor,
                incrementalRemoteSync = IncrementalRemoteSync(remoteDataSource, syncDao, syncStateDao) { 3000L },
                workspaceIncrementalRemoteSync = workspaceIncrementalSync,
                offlineWriteQueue = queue,
                syncStateDao = syncStateDao,
                remoteSyncDao = syncDao,
                conflictRecoveryService = ConflictRecoveryService(syncStateDao, opDao, mutationDao) { 3000L },
                nowEpochMillisProvider = { 3500L },
            )

            val resolveResult = repository.resolveConflict(EntityId(wsId), ConflictResolution.KEEP_LOCAL)
            assertTrue(resolveResult is RepositoryResult.Success)

            // Doğrulama: Çözüm sonrası rebase edildi, tail unblocked, baseVersion = 5
            val rebasedWs = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(rebasedWs)
            assertEquals("PENDING_UPDATE", rebasedWs.sync.syncStatus)
            assertEquals(5L, rebasedWs.sync.version)
            assertEquals(5L, rebasedWs.sync.baseVersion)

            val ops = syncDao.getAllWorkspaceOperations(wsId)
            assertEquals(1, ops.size)
            val rebasedOp = ops.first()
            assertEquals(opId, rebasedOp.operationId)
            assertEquals("UPDATE", rebasedOp.operationTypeCode)
            assertEquals("PENDING", rebasedOp.statusCode)
            assertFalse(rebasedOp.isBlocked)
            assertNull(rebasedOp.predecessorOperationId)
            assertEquals(5L, rebasedOp.baseVersion)
            assertEquals(rebasedWs.sync.version, rebasedOp.baseVersion)

            assertNull(syncStateDao.getConflict(wsId))

            // 4. Sonraki OutboxProcessor turu: Writer APPLIED döner
            val appliedServerDto = remoteDto.copy(
                name = "Yerel Kazanan Başlık",
                version = 6L,
                updatedAt = "2026-03-02T15:00:00Z",
            )
            writer.workspaceResult = ConditionalRemoteWriteResult.Applied(appliedServerDto)

            val outboxResult = outboxProcessor.processReadyOperations()
            assertEquals(1, outboxResult.succeededCount)
            assertEquals(1, writer.callCount)
            assertEquals(RemoteWriteOperation.UPDATE, writer.lastOperation)
            assertEquals(5L, writer.lastBaseVersion)

            // ACK sonrası Room durumu: Workspace SYNCED, conflict ve outbox temiz
            val finalWs = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(finalWs)
            assertEquals("SYNCED", finalWs.sync.syncStatus)
            assertEquals(6L, finalWs.sync.version)
            assertNull(finalWs.sync.baseVersion)
            assertEquals("Yerel Kazanan Başlık", finalWs.name)

            assertEquals(0, syncDao.getAllWorkspaceOperations(wsId).size)
            assertNull(syncStateDao.getConflict(wsId))
        } finally {
            db.close()
        }
    }

    @Test
    fun keepLocal_delete_inbound_conflict_resolved_rebases_and_processor_applies_or_ack_missing() = runTest {
        val db = inMemoryDatabase()
        try {
            val syncDao = db.remoteSyncDao()
            val syncStateDao = db.syncStateDao()
            val mutationDao = db.localMutationDao()
            val workspaceDao = db.workspaceDao()
            val opDao = db.syncOperationDao()

            val wsId = "550e8400-e29b-41d4-a716-446655440003"
            val initialWs = sampleWorkspace(id = wsId, name = "Silinecek WS", version = 3L, baseVersion = 3L)
            val ownerMember = sampleOwnerMember(workspaceId = wsId)
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(initialWs),
                members = listOf(ownerMember),
                cursors = listOf(
                    WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(
                        WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = wsId, userId = "user-1"),
                    ),
                ),
            )

            // 1. Pending yerel DELETE mutasyonu
            val localDeletedWs = initialWs.copy(
                sync = initialWs.sync.copy(
                    syncStatus = "PENDING_DELETE",
                    deletedAtEpochMillis = 2000L,
                    localUpdatedAtEpochMillis = 2000L,
                ),
            )
            val deletePayload = WorkspacePayloadCodec.encode(localDeletedWs, OutboxOperationType.DELETE)
            val enqueueResult = mutationDao.mutateWorkspaceV2(
                entity = localDeletedWs,
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = deletePayload,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )
            val delOpId = enqueueResult.operationId

            // 2. Inbound pull: Çakışma oluşturan uzaktaki kayıt (version = 7 > baseVersion = 3)
            val remoteDto = WorkspaceDto(
                id = wsId,
                name = "Uzakta Yeniden İsimlendirildi",
                normalizedName = "uzakta yeniden isimlendirildi",
                ownerId = "user-1",
                typeCode = "personal",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-02T16:00:00Z",
                deletedAt = null,
                version = 7L,
            )
            val remoteDataSource = FakeCoreRemoteDataSource(
                workspacePages = listOf(
                    RemotePage(items = listOf(remoteDto), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            )
            val workspaceIncrementalSync = WorkspaceIncrementalRemoteSync(
                remote = remoteDataSource,
                remoteSyncDao = syncDao,
                syncStateDao = syncStateDao,
                nowEpochMillisProvider = { 3000L },
            )

            val pullResult = workspaceIncrementalSync.pull()
            assertEquals(1, pullResult.conflictCount)
            val wsInConflict = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(wsInConflict)
            assertEquals("CONFLICT", wsInConflict.sync.syncStatus)

            // 3. KEEP_LOCAL çözümü
            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = opDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 4000L },
            )
            val writer = FakeRemoteWriter()
            val v2Executor = V2OutboxOperationExecutor(writer) { 4000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(operation: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(operation: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(operation: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = syncDao,
            ) { 4000L }
            val outboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor))
            val repository = OfflineFirstSyncRepository(
                authRepository = FakeAuthRepository(userSession),
                initialRemoteSync = InitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                workspaceInitialRemoteSync = WorkspaceInitialRemoteSync(remoteDataSource, syncDao) { 3000L },
                outboxProcessor = outboxProcessor,
                incrementalRemoteSync = IncrementalRemoteSync(remoteDataSource, syncDao, syncStateDao) { 3000L },
                workspaceIncrementalRemoteSync = workspaceIncrementalSync,
                offlineWriteQueue = queue,
                syncStateDao = syncStateDao,
                remoteSyncDao = syncDao,
                conflictRecoveryService = ConflictRecoveryService(syncStateDao, opDao, mutationDao) { 3000L },
                nowEpochMillisProvider = { 3500L },
            )

            val resolveResult = repository.resolveConflict(EntityId(wsId), ConflictResolution.KEEP_LOCAL)
            assertTrue(resolveResult is RepositoryResult.Success)

            // Doğrulama: Rebased DELETE
            val rebasedWs = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(rebasedWs)
            assertEquals("PENDING_DELETE", rebasedWs.sync.syncStatus)
            assertEquals(7L, rebasedWs.sync.version)
            assertEquals(7L, rebasedWs.sync.baseVersion)

            val ops = syncDao.getAllWorkspaceOperations(wsId)
            assertEquals(1, ops.size)
            val rebasedOp = ops.first()
            assertEquals(delOpId, rebasedOp.operationId)
            assertEquals("DELETE", rebasedOp.operationTypeCode)
            assertEquals(7L, rebasedOp.baseVersion)
            assertFalse(rebasedOp.isBlocked)

            // 4. Sonraki OutboxProcessor turu: DELETE işleminin NotFound (MissingDeleteAcknowledged) dönüşü
            writer.workspaceResult = ConditionalRemoteWriteResult.NotFound

            val outboxResult = outboxProcessor.processReadyOperations()
            assertEquals(1, outboxResult.succeededCount)
            assertEquals(1, writer.callCount)
            assertEquals(RemoteWriteOperation.DELETE, writer.lastOperation)
            assertEquals(7L, writer.lastBaseVersion)

            // ACK sonrası: Tombstone SYNCED, outbox/conflict temiz
            val finalWs = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(finalWs)
            assertEquals("SYNCED", finalWs.sync.syncStatus)
            assertNotNull(finalWs.sync.deletedAtEpochMillis)

            assertEquals(0, syncDao.getAllWorkspaceOperations(wsId).size)
            assertNull(syncStateDao.getConflict(wsId))
        } finally {
            db.close()
        }
    }

    @Test
    fun stale_resolution_when_successor_added_after_conflict_processor_does_not_execute() = runTest {
        val db = inMemoryDatabase()
        try {
            val syncDao = db.remoteSyncDao()
            val syncStateDao = db.syncStateDao()
            val mutationDao = db.localMutationDao()
            val workspaceDao = db.workspaceDao()
            val opDao = db.syncOperationDao()

            val wsId = "550e8400-e29b-41d4-a716-446655440004"
            val initialWs = sampleWorkspace(id = wsId, name = "Orijinal Başlık", version = 1L, baseVersion = 1L)
            val ownerMember = sampleOwnerMember(workspaceId = wsId)
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(initialWs),
                members = listOf(ownerMember),
                cursors = listOf(
                    WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(
                        WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = wsId, userId = "user-1"),
                    ),
                ),
            )

            // 1. Pending mutasyon: op-1
            val localUpdatedWs1 = initialWs.copy(
                name = "İlk Değişiklik",
                sync = initialWs.sync.copy(
                    syncStatus = "PENDING_UPDATE",
                    localUpdatedAtEpochMillis = 2000L,
                ),
            )
            val updatePayload1 = WorkspacePayloadCodec.encode(localUpdatedWs1, OutboxOperationType.UPDATE)
            val enqueue1 = mutationDao.mutateWorkspaceV2(
                entity = localUpdatedWs1,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload1,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 2000L,
            )
            val opId1 = enqueue1.operationId

            // 2. Inbound pull: Çakışma tespiti (opId1 çakışma olarak kaydedilir)
            val remoteDto = WorkspaceDto(
                id = wsId,
                name = "Sunucu Başlık",
                normalizedName = "sunucu başlık",
                ownerId = "user-1",
                typeCode = "personal",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-02T12:00:00Z",
                deletedAt = null,
                version = 3L,
            )
            val remoteDataSource = FakeCoreRemoteDataSource(
                workspacePages = listOf(
                    RemotePage(items = listOf(remoteDto), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            )
            val workspaceIncrementalSync = WorkspaceIncrementalRemoteSync(
                remote = remoteDataSource,
                remoteSyncDao = syncDao,
                syncStateDao = syncStateDao,
                nowEpochMillisProvider = { 3000L },
            )

            val pullResult = workspaceIncrementalSync.pull()
            assertEquals(1, pullResult.conflictCount)
            val conflict = syncStateDao.getConflict(wsId)
            assertNotNull(conflict)
            assertEquals(opId1, conflict.operationId)

            // Conflict durumunda op1 outbox durumu CONFLICT olarak güncellenir
            opDao.markConflict(opId1, "Inbound conflict detected", 3000L)

            // 3. Kullanıcı çözmeden ÖNCE yeni bir yerel mutasyon ekler (Successor op-2 kuyruğa girer)
            val localUpdatedWs2 = localUpdatedWs1.copy(
                name = "İkinci Ardıl Değişiklik",
                sync = localUpdatedWs1.sync.copy(
                    syncStatus = "PENDING_UPDATE",
                    localUpdatedAtEpochMillis = 4000L,
                ),
            )
            val updatePayload2 = WorkspacePayloadCodec.encode(localUpdatedWs2, OutboxOperationType.UPDATE)
            val enqueue2 = mutationDao.mutateWorkspaceV2(
                entity = localUpdatedWs2,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = updatePayload2,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillis = 4000L,
            )
            val opId2 = enqueue2.operationId

            // Zincirde 2 operasyon olduğunu doğrula (op1 -> op2)
            val ops = syncDao.getAllWorkspaceOperations(wsId)
            assertEquals(2, ops.size)
            assertEquals(opId1, ops[0].operationId)
            assertEquals(opId2, ops[1].operationId)
            assertEquals(opId1, ops[1].predecessorOperationId)
            assertTrue(ops[1].isBlocked)

            val queue = OfflineWriteQueue(
                mutationDao = mutationDao,
                operationDao = opDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 5000L },
            )
            val writer = FakeRemoteWriter()
            val v2Executor = V2OutboxOperationExecutor(writer) { 5000L }
            val v1Executor = V1OutboxOperationExecutor(
                writer = object : com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter {
                    override suspend fun writeCategory(operation: RemoteWriteOperation, baseVersion: Long?, dto: CategoryDto) = error("N/A")
                    override suspend fun writeProfile(operation: RemoteWriteOperation, baseVersion: Long?, dto: ProfileDto) = error("N/A")
                    override suspend fun writeTransaction(operation: RemoteWriteOperation, baseVersion: Long?, dto: TransactionDto) = error("N/A")
                },
                remoteSyncDao = syncDao,
            ) { 5000L }
            val outboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), ProtocolAwareOutboxOperationExecutor(v1Executor, v2Executor))
            val repository = OfflineFirstSyncRepository(
                authRepository = FakeAuthRepository(userSession),
                initialRemoteSync = InitialRemoteSync(remoteDataSource, syncDao) { 5000L },
                workspaceInitialRemoteSync = WorkspaceInitialRemoteSync(remoteDataSource, syncDao) { 5000L },
                outboxProcessor = outboxProcessor,
                incrementalRemoteSync = IncrementalRemoteSync(remoteDataSource, syncDao, syncStateDao) { 5000L },
                workspaceIncrementalRemoteSync = workspaceIncrementalSync,
                offlineWriteQueue = queue,
                syncStateDao = syncStateDao,
                remoteSyncDao = syncDao,
                conflictRecoveryService = ConflictRecoveryService(syncStateDao, opDao, mutationDao) { 5000L },
                nowEpochMillisProvider = { 5000L },
            )

            // 4. Çözüm denemesi: Tail (opId2) != conflict.operationId (opId1) olduğundan STALE dönmeli
            val keepRemoteResult = repository.resolveConflict(EntityId(wsId), ConflictResolution.KEEP_REMOTE)
            assertTrue(keepRemoteResult is RepositoryResult.Failure)
            assertTrue(keepRemoteResult.error is AppError.Conflict)
            assertEquals("sync.conflict_resolution_stale", keepRemoteResult.error.code)

            val keepLocalResult = repository.resolveConflict(EntityId(wsId), ConflictResolution.KEEP_LOCAL)
            assertTrue(keepLocalResult is RepositoryResult.Failure)
            assertTrue(keepLocalResult.error is AppError.Conflict)
            assertEquals("sync.conflict_resolution_stale", keepLocalResult.error.code)

            // Room durumunun tamamen korunduğunu doğrula
            val wsStillInConflict = workspaceDao.getWorkspaceById(wsId)
            assertNotNull(wsStillInConflict)
            assertEquals("PENDING_UPDATE", wsStillInConflict.sync.syncStatus)
            assertNotNull(syncStateDao.getConflict(wsId))
            val opsAfter = syncDao.getAllWorkspaceOperations(wsId)
            assertEquals(2, opsAfter.size)
            assertEquals(opId1, opsAfter[0].operationId)
            assertEquals(opId2, opsAfter[1].operationId)

            // 5. Outbox processor eski veya yeni hiçbir operasyonu işlememeli veya silmemeli
            val processorResult = outboxProcessor.processReadyOperations()
            assertEquals(0, processorResult.succeededCount)
            assertEquals(0, writer.callCount)
            assertNull(writer.lastOperationId)

            // İki operasyon da outbox'ta durmalı
            assertEquals(2, syncDao.getAllWorkspaceOperations(wsId).size)
        } finally {
            db.close()
        }
    }
}
