package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.WorkspaceResolutionPrecondition
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.SyncUserStateEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.local.dao.WorkspaceConflictStaleResolutionException
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.OutboxOperationExecutor
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.OutboxQueue
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.domain.repository.ConflictResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import com.feniqo.mobile.domain.repository.SyncEntityType
import com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys

class OfflineFirstSyncRepositoryTest {

    @Test
    fun successful_sync_writes_timestamp_to_room_for_active_user() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())
        val initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L }
        val incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L }
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao())

        val workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L }
        val workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = initialRemoteSync,
            workspaceInitialRemoteSync = workspaceInitialRemoteSync,
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = incrementalRemoteSync,
            workspaceIncrementalRemoteSync = workspaceIncrementalRemoteSync,
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)

        val storedState = syncStateDao.getUserState("11111111-1111-4111-8111-111111111111")
        assertEquals("11111111-1111-4111-8111-111111111111", storedState?.userId)
        assertEquals(5_000L, storedState?.lastSuccessfulSyncAtEpochMillis)

        val overview = repository.observeOverview().first()
        assertEquals(Instant.fromEpochMilliseconds(5_000L), overview.lastSuccessfulSyncAt)
        assertEquals(SyncPhase.IDLE, overview.phase)
    }

    @Test
    fun repository_recreation_restores_sync_time_from_room() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(
                userId = "11111111-1111-4111-8111-111111111111",
                lastSuccessfulSyncAtEpochMillis = 8_000L,
                updatedAtEpochMillis = 8_000L,
            ),
        )
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val overview = repository.observeOverview().first()
        assertEquals(Instant.fromEpochMilliseconds(8_000L), overview.lastSuccessfulSyncAt)
    }

    @Test
    fun user_switch_does_not_leak_previous_user_time() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "11111111-1111-4111-8111-111111111111", lastSuccessfulSyncAtEpochMillis = 3_000L, updatedAtEpochMillis = 3_000L),
        )
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "22222222-2222-4222-8222-222222222222", lastSuccessfulSyncAtEpochMillis = 7_000L, updatedAtEpochMillis = 7_000L),
        )
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        assertEquals(Instant.fromEpochMilliseconds(3_000L), repository.observeOverview().first().lastSuccessfulSyncAt)

        auth.setSession(USER_2_SESSION)
        assertEquals(Instant.fromEpochMilliseconds(7_000L), repository.observeOverview().first().lastSuccessfulSyncAt)

        auth.setSession(USER_3_SESSION)
        assertNull(repository.observeOverview().first().lastSuccessfulSyncAt)
    }

    @Test
    fun when_session_is_null_last_successful_sync_time_is_null() = runTest {
        val auth = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "11111111-1111-4111-8111-111111111111", lastSuccessfulSyncAtEpochMillis = 3_000L, updatedAtEpochMillis = 3_000L),
        )
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        assertNull(repository.observeOverview().first().lastSuccessfulSyncAt)
    }

    @Test
    fun failed_sync_does_not_update_previous_successful_time() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "11111111-1111-4111-8111-111111111111", lastSuccessfulSyncAtEpochMillis = 2_000L, updatedAtEpochMillis = 2_000L),
        )
        val outboxQueue = FakeOutboxQueue(
            readyOps = listOf(sampleOperationEntity("op-1")),
        )
        val failingExecutor = object : OutboxOperationExecutor {
            override suspend fun execute(operation: SyncOperationEntity): com.feniqo.mobile.data.sync.OutboxExecutionResult {
                throw io.ktor.utils.io.errors.IOException("Network down")
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, failingExecutor)
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("11111111-1111-4111-8111-111111111111")), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 9_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)

        // Önceki zamanın değişmediğini doğrula
        assertEquals(2_000L, syncStateDao.getUserState("11111111-1111-4111-8111-111111111111")?.lastSuccessfulSyncAtEpochMillis)
    }

    @Test
    fun initial_remote_sync_failure_preserves_pending_outbox_operations_and_does_not_advance_cursor() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val pendingOp = sampleOperationEntity("op-pending-local")
        val outboxQueue = FakeOutboxQueue(
            readyOps = listOf(pendingOp),
        )
        val executedOps = mutableListOf<String>()
        val outboxExecutor = object : OutboxOperationExecutor {
            override suspend fun execute(operation: SyncOperationEntity): com.feniqo.mobile.data.sync.OutboxExecutionResult {
                executedOps.add(operation.operationId)
                return com.feniqo.mobile.data.sync.OutboxExecutionResult.V1Completed
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, outboxExecutor)

        // Initial sync sırasında hata fırlatan bir remote kaynak
        val failingRemote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource() {
            override suspend fun fetchProfile(userId: String): com.feniqo.mobile.data.remote.dto.ProfileDto? {
                throw io.ktor.utils.io.errors.IOException("Remote schema unavailable")
            }
        }
        val failingInitialSync = InitialRemoteSync(failingRemote, remoteSyncDao) { 1_000L }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = failingInitialSync,
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)

        // Outbox işlemleri çalıştırılmadı ve silinmedi; kuyrukta korunuyor
        assertEquals(0, executedOps.size)
        assertEquals(1, outboxQueue.readyOperations(syncScopeKey = "USER:11111111-1111-4111-8111-111111111111", limit = 10).size)
        assertEquals("op-pending-local", outboxQueue.readyOperations(syncScopeKey = "USER:11111111-1111-4111-8111-111111111111", limit = 10).first().operationId)

        // Başarılı sync cursor'ı yazılmadı
        assertNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))
        assertNull(syncStateDao.getCursor("USER:11111111-1111-4111-8111-111111111111", com.feniqo.mobile.domain.repository.SyncEntityType.PROFILE.name))
    }

    @Test
    fun conflict_during_sync_returns_conflict_failure_and_preserves_stored_sync_time() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "11111111-1111-4111-8111-111111111111", lastSuccessfulSyncAtEpochMillis = 4_000L, updatedAtEpochMillis = 4_000L),
        )
        val outboxQueue = FakeOutboxQueue(
            readyOps = listOf(sampleOperationEntity("op-conflict")),
        )
        val conflictExecutor = object : OutboxOperationExecutor {
            override suspend fun execute(operation: SyncOperationEntity): com.feniqo.mobile.data.sync.OutboxExecutionResult {
                throw com.feniqo.mobile.data.sync.OutboxConflictException("Local version is outdated")
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, conflictExecutor)
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("11111111-1111-4111-8111-111111111111")), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 9_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.user_resolution_required", result.error.code)

        // Room'daki önceki zamanın değişmediğini doğrula
        assertEquals(4_000L, syncStateDao.getUserState("11111111-1111-4111-8111-111111111111")?.lastSuccessfulSyncAtEpochMillis)
        assertEquals(Instant.fromEpochMilliseconds(4_000L), repository.observeOverview().first().lastSuccessfulSyncAt)
    }

    @Test
    fun database_sqlite_exception_during_user_state_upsert_returns_storage_failure_and_sets_failed_phase() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao().apply {
            upsertUserStateError = androidx.sqlite.SQLiteException("disk I/O error")
        }
        val remoteSyncDao = FakeRemoteSyncDao()
        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("11111111-1111-4111-8111-111111111111")), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 9_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Storage)
        assertEquals("sync.storage_failed", result.error.code)

        val overview = repository.observeOverview().first()
        assertEquals(SyncPhase.FAILED, overview.phase)
        assertNull(overview.lastSuccessfulSyncAt)
    }

    @Test
    fun pending_and_failed_counts_are_reported_separately() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val syncOpDao = FakeSyncOperationDao(initialPending = 4, initialFailed = 2)
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), syncOpDao)
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 1_000L },
        )

        val overview = repository.observeOverview().first()
        assertEquals(4, overview.pendingOperationCount)
        assertEquals(2, overview.failedOperationCount)
    }

    @Test
    fun resolve_recurring_conflict_keep_remote_applies_remote_and_cleans_conflict() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-1")
        syncStateDao.conflicts["rec-1"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-1"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)

        val resolved = remoteSyncDao.resolvedRecurringRemote
        assertTrue(resolved != null)
        assertEquals("rec-1", resolved.id)
        assertEquals(50_000L, resolved.amountMinor)
        assertEquals("TRY", resolved.currencyCode)
        assertEquals(10_000L, resolved.sync.localUpdatedAtEpochMillis)
    }

    @Test
    fun resolve_recurring_conflict_keep_local_rebases_operation_and_resets_outbox() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-2")
        syncStateDao.conflicts["rec-2"] = conflict
        remoteSyncDao.localRecurringRow = sampleRecurringEntity("rec-2", deletedAt = null)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 12_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-2"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Success)

        val called = remoteSyncDao.resolveKeepLocalCalledWith
        assertTrue(called != null)
        assertEquals(conflict, called.first)
        assertEquals("UPDATE", called.second)
        assertEquals(12_000L, called.third)
    }

    @Test
    fun resolve_recurring_conflict_invalid_payload_fails_closed() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-3").copy(remotePayloadJson = "{ invalid json")
        syncStateDao.conflicts["rec-3"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-3"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Storage)
        assertEquals("sync.conflict_resolution_failed", result.error.code)

        assertNull(remoteSyncDao.resolvedRecurringRemote)
        assertNull(remoteSyncDao.resolveKeepLocalCalledWith)
    }

    @Test
    fun resolve_recurring_conflict_remote_payload_id_mismatch_fails_closed() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val localEntity = sampleRecurringEntity("rec-mismatch")
        remoteSyncDao.localRecurringRow = localEntity
        val conflict = sampleRecurringConflict("rec-mismatch").copy(
            remotePayloadJson = """{"id":"other-id","user_id":"11111111-1111-4111-8111-111111111111","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-05T00:00:00Z","version":3}""",
        )
        syncStateDao.conflicts["rec-mismatch"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-mismatch"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Storage)
        assertEquals("sync.conflict_resolution_failed", result.error.code)

        assertNull(remoteSyncDao.resolvedRecurringRemote)
        assertNull(remoteSyncDao.resolveKeepLocalCalledWith)
        assertEquals(conflict, syncStateDao.conflicts["rec-mismatch"])
        assertEquals(localEntity, remoteSyncDao.localRecurringRow)
    }

    @Test
    fun resolve_recurring_conflict_rethrows_cancellation() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.getConflictError = CancellationException("Scope cancelled")
        val remoteSyncDao = FakeRemoteSyncDao()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        assertFailsWith<CancellationException> {
            repository.resolveConflict(EntityId("rec-4"), ConflictResolution.KEEP_REMOTE)
        }
    }

    @Test
    fun resolve_workspace_conflict_keep_remote_calls_dao_with_decoded_dto_and_precondition() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleWorkspaceConflict("ws-1")
        syncStateDao.conflicts["ws-1"] = conflict

        val localWs = sampleWorkspaceEntity("ws-1")
        val localOp = sampleWorkspaceOperation("op-ws-1", "ws-1")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-1"] = listOf(localOp)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-1"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)

        val resolved = remoteSyncDao.resolvedWorkspaceRemote
        assertNotNull(resolved)
        assertEquals("Remote WS", resolved.second.name)
        assertEquals(4L, resolved.second.sync.version)
        assertEquals(15_000L, resolved.second.sync.localUpdatedAtEpochMillis)
        assertEquals("ws-1", resolved.first.expectedConflict.entityId)
        assertEquals(1, resolved.first.expectedOperations.size)
    }

    @Test
    fun resolve_workspace_conflict_keep_local_create_produces_canonical_update_payload_and_rebases() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION) // userId = "11111111-1111-4111-8111-111111111111"
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        // remote.ownerId = "11111111-1111-4111-8111-111111111111", local.ownerId = "11111111-1111-4111-8111-111111111111", actor = "11111111-1111-4111-8111-111111111111"
        val conflict = sampleWorkspaceConflict("ws-create-1", remoteOwnerId = "11111111-1111-4111-8111-111111111111", remoteDeletedAt = null)
        syncStateDao.conflicts["ws-create-1"] = conflict

        val localWs = sampleWorkspaceEntity("ws-create-1", ownerId = "11111111-1111-4111-8111-111111111111")
        val createOp = sampleWorkspaceOperation("op-ws-create-1", "ws-create-1", typeCode = "CREATE")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-create-1"] = listOf(createOp)

        // Doğrulama: CONFLICT durumundaki local entity'yi doğrudan codec'e vermek fail-closed patlar
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(localWs, OutboxOperationType.UPDATE)
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-create-1"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Success)

        val resolved = remoteSyncDao.resolvedWorkspaceLocal
        assertNotNull(resolved)
        assertEquals("UPDATE", resolved.targetOperationTypeCode)
        // Canonical UPDATE payload üretildi ve "created_at" İÇERMEZ:
        val payload = resolved.targetPayloadJson
        assertNotNull(payload)
        assertTrue(!payload.contains("created_at"), "Canonical UPDATE payload created_at içermemelidir!")
        assertTrue(payload.contains("ws-create-1"))
        assertTrue(payload.contains("Local WS"))
    }

    @Test
    fun resolve_workspace_conflict_keep_local_create_fails_closed_when_owner_mismatches() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION) // actor = "11111111-1111-4111-8111-111111111111"
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        // Remote owner is "99999999-9999-4999-8999-999999999999" != "11111111-1111-4111-8111-111111111111"
        val conflict = sampleWorkspaceConflict("ws-mismatch", remoteOwnerId = "99999999-9999-4999-8999-999999999999", remoteDeletedAt = null)
        syncStateDao.conflicts["ws-mismatch"] = conflict

        val localWs = sampleWorkspaceEntity("ws-mismatch", ownerId = "11111111-1111-4111-8111-111111111111")
        val createOp = sampleWorkspaceOperation("op-ws-mismatch", "ws-mismatch", typeCode = "CREATE")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-mismatch"] = listOf(createOp)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-mismatch"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.workspace_create_conflict_owner_mismatch", result.error.code)

        // Hiçbir DAO işlemi çağrılmadı, veriler değişmedi
        assertNull(remoteSyncDao.resolvedWorkspaceLocal)
    }

    @Test
    fun resolve_workspace_conflict_keep_local_create_fails_closed_when_remote_is_tombstone() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        // Remote has deleted_at != null
        val conflict = sampleWorkspaceConflict("ws-tomb", remoteOwnerId = "11111111-1111-4111-8111-111111111111", remoteDeletedAt = "2026-08-01T12:00:00Z")
        syncStateDao.conflicts["ws-tomb"] = conflict

        val localWs = sampleWorkspaceEntity("ws-tomb", ownerId = "11111111-1111-4111-8111-111111111111")
        val createOp = sampleWorkspaceOperation("op-ws-tomb", "ws-tomb", typeCode = "CREATE")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-tomb"] = listOf(createOp)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-tomb"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.workspace_create_conflict_remote_tombstone", result.error.code)

        assertNull(remoteSyncDao.resolvedWorkspaceLocal)
    }

    @Test
    fun resolve_workspace_conflict_keep_local_update_and_delete_preserves_payload_immutably() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleWorkspaceConflict("ws-imm")
        syncStateDao.conflicts["ws-imm"] = conflict

        val localWs = sampleWorkspaceEntity("ws-imm")
        val exactUpdatePayload = """{"custom_field":"exact_byte_for_byte_preserved"}"""
        val updateOp = sampleWorkspaceOperation("op-ws-imm", "ws-imm", typeCode = "UPDATE", payload = exactUpdatePayload)
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-imm"] = listOf(updateOp)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        // 1. UPDATE testi: payload byte-for-byte korunur
        val updateResult = repository.resolveConflict(EntityId("ws-imm"), ConflictResolution.KEEP_LOCAL)
        assertTrue(updateResult is RepositoryResult.Success)
        assertEquals("UPDATE", remoteSyncDao.resolvedWorkspaceLocal?.targetOperationTypeCode)
        assertEquals(exactUpdatePayload, remoteSyncDao.resolvedWorkspaceLocal?.targetPayloadJson)

        // 2. DELETE testi: payload byte-for-byte korunur
        val exactDeletePayload = """{"id":"ws-imm","delete_flag":true}"""
        val deleteOp = sampleWorkspaceOperation("op-ws-imm", "ws-imm", typeCode = "DELETE", payload = exactDeletePayload)
        remoteSyncDao.workspaceOperationsMap["ws-imm"] = listOf(deleteOp)
        val deleteResult = repository.resolveConflict(EntityId("ws-imm"), ConflictResolution.KEEP_LOCAL)
        assertTrue(deleteResult is RepositoryResult.Success)
        assertEquals("DELETE", remoteSyncDao.resolvedWorkspaceLocal?.targetOperationTypeCode)
        assertEquals(exactDeletePayload, remoteSyncDao.resolvedWorkspaceLocal?.targetPayloadJson)
    }

    @Test
    fun resolve_workspace_conflict_stale_exception_maps_to_conflict_resolution_stale() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val conflict = sampleWorkspaceConflict("ws-stale")
        syncStateDao.conflicts["ws-stale"] = conflict

        val localWs = sampleWorkspaceEntity("ws-stale")
        val op = sampleWorkspaceOperation("op-stale", "ws-stale")

        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = localWs
            override suspend fun getAllWorkspaceOperations(syncScopeKey: String, workspaceId: String): List<SyncOperationEntity> = listOf(op)
            override suspend fun resolveWorkspaceKeepRemote(
                syncScopeKey: String,
                precondition: WorkspaceResolutionPrecondition,
                remoteEntity: WorkspaceEntity,
            ) {
                throw WorkspaceConflictStaleResolutionException("Concurrent DB modification!")
            }
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-stale"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.conflict_resolution_stale", result.error.code)
    }

    @Test
    fun resolve_workspace_conflict_keep_remote_and_keep_local_returns_stale_when_conflict_op_is_not_chain_tail() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()

        // Conflict op-1 için oluşturulmuş
        val conflict = sampleWorkspaceConflict("ws-succ").copy(operationId = "op-1")
        syncStateDao.conflicts["ws-succ"] = conflict

        val localWs = sampleWorkspaceEntity("ws-succ")
        // Ancak zincirde op-1'e ek olarak successor op-2 var (zincirin gerçek tail'i op-2)
        val op1 = sampleWorkspaceOperation("op-1", "ws-succ")
        val op2 = sampleWorkspaceOperation("op-2", "ws-succ").copy(predecessorOperationId = "op-1")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-succ"] = listOf(op1, op2)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 15_000L },
        )

        // 1. KEEP_REMOTE testi: erken fail-fast ile stale dönmeli ve DAO'yu çağırmamalı
        val remoteResult = repository.resolveConflict(EntityId("ws-succ"), ConflictResolution.KEEP_REMOTE)
        assertTrue(remoteResult is RepositoryResult.Failure)
        assertTrue(remoteResult.error is AppError.Conflict)
        assertEquals("sync.conflict_resolution_stale", remoteResult.error.code)
        assertNull(remoteSyncDao.resolvedWorkspaceRemote)

        // 2. KEEP_LOCAL testi: erken fail-fast ile stale dönmeli ve DAO'yu çağırmamalı
        val localResult = repository.resolveConflict(EntityId("ws-succ"), ConflictResolution.KEEP_LOCAL)
        assertTrue(localResult is RepositoryResult.Failure)
        assertTrue(localResult.error is AppError.Conflict)
        assertEquals("sync.conflict_resolution_stale", localResult.error.code)
        assertNull(remoteSyncDao.resolvedWorkspaceLocal)

        // Outbox ve conflict değişmeden korunmalı
        assertEquals(conflict, syncStateDao.conflicts["ws-succ"])
        assertEquals(2, remoteSyncDao.workspaceOperationsMap["ws-succ"]?.size)
    }

    @Test
    fun resolveConflict_withoutSession_doesNotMutateConflict() = runTest {
        val auth = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-no-session")
        syncStateDao.conflicts["rec-no-session"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-no-session"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals("sync.session_required", result.error.code)
        assertEquals(0, remoteSyncDao.mutationCallCount)
        assertEquals(conflict, syncStateDao.conflicts["rec-no-session"])
    }

    @Test
    fun userB_cannotResolve_userAConflict() = runTest {
        val auth = FakeAuthRepository(USER_2_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-user-a", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111")
        syncStateDao.conflicts["rec-user-a"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-user-a"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.conflict_scope_mismatch", result.error.code)
        assertEquals(0, remoteSyncDao.mutationCallCount)
        assertEquals(conflict, syncStateDao.conflicts["rec-user-a"])
    }

    @Test
    fun userB_conflictMismatch_doesNotCallAnyRemoteSyncDaoMutation() = runTest {
        val auth = FakeAuthRepository(USER_2_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-user-a-local", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111")
        syncStateDao.conflicts["rec-user-a-local"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-user-a-local"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Conflict)
        assertEquals("sync.conflict_scope_mismatch", result.error.code)
        assertEquals(0, remoteSyncDao.mutationCallCount)
        assertEquals(conflict, syncStateDao.conflicts["rec-user-a-local"])
    }

    @Test
    fun userA_canResolve_ownConflict_withActiveAuthenticatedScope() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleRecurringConflict("rec-user-a-own", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111")
        syncStateDao.conflicts["rec-user-a-own"] = conflict

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-user-a-own"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", remoteSyncDao.lastRecordedSyncScopeKey)
        assertEquals(1, remoteSyncDao.mutationCallCount)
    }

    @Test
    fun workspace_owner_different_uses_active_mutation_actor_scope() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflict = sampleWorkspaceConflict(
            id = "ws-diff-owner",
            remoteOwnerId = "33333333-3333-4333-8333-333333333333",
            syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
        )
        syncStateDao.conflicts["ws-diff-owner"] = conflict

        val localWs = sampleWorkspaceEntity("ws-diff-owner", ownerId = "33333333-3333-4333-8333-333333333333")
        val localOp = sampleWorkspaceOperation("op-ws-diff-owner", "ws-diff-owner")
        remoteSyncDao.localWorkspaceRow = localWs
        remoteSyncDao.workspaceOperationsMap["ws-diff-owner"] = listOf(localOp)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("ws-diff-owner"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", remoteSyncDao.lastRecordedSyncScopeKey)
        assertEquals(1, remoteSyncDao.mutationCallCount)
    }

    @Test
    fun b_sync_calls_recovery_service_with_b_scope_only() = runTest {
        val auth = FakeAuthRepository(USER_2_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        remoteSyncDao.syncStateDao = syncStateDao
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("22222222-2222-4222-8222-222222222222"))
        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val syncResult = repository.requestSync()
        assertTrue(syncResult is RepositoryResult.Success<*>)
        assertTrue(syncStateDao.queriedRecoveryScopes.contains("USER:22222222-2222-4222-8222-222222222222"))
        assertFalse(syncStateDao.queriedRecoveryScopes.contains("USER:11111111-1111-4111-8111-111111111111"))
    }

    @Test
    fun observeOverview_emits_0_conflictCount_when_session_is_null() = runTest {
        val auth = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        syncStateDao.conflicts["tx-1"] = sampleRecurringConflict("tx-1", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111")
        syncStateDao.notifyConflictsChanged()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val overview = repository.observeOverview().first()
        assertEquals(0, overview.conflictCount)
    }

    @Test
    fun observeOverview_and_observeConflicts_switch_scope_on_user_transition() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val conflictA = sampleRecurringConflict("rec-a", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111")
        val conflictB1 = sampleRecurringConflict("rec-b1", syncScopeKey = "USER:22222222-2222-4222-8222-222222222222")
        val conflictB2 = sampleRecurringConflict("rec-b2", syncScopeKey = "USER:22222222-2222-4222-8222-222222222222")
        syncStateDao.conflicts["rec-a"] = conflictA
        syncStateDao.conflicts["rec-b1"] = conflictB1
        syncStateDao.conflicts["rec-b2"] = conflictB2
        syncStateDao.notifyConflictsChanged()

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        // 1. User A is active
        assertEquals(1, repository.observeOverview().first().conflictCount)
        val aConflicts = repository.observeConflicts().first()
        assertEquals(1, aConflicts.size)
        assertEquals("rec-a", aConflicts.first().entityId.value)

        // 2. Switch to User B
        auth.setSession(USER_2_SESSION)
        assertEquals(2, repository.observeOverview().first().conflictCount)
        val bConflicts = repository.observeConflicts().first()
        assertEquals(2, bConflicts.size)
        assertTrue(bConflicts.any { it.entityId.value == "rec-b1" })
        assertTrue(bConflicts.any { it.entityId.value == "rec-b2" })

        // 3. Switch to null (logged out)
        auth.setSession(null)
        assertEquals(0, repository.observeOverview().first().conflictCount)
        val emptyConflicts = repository.observeConflicts().first()
        assertTrue(emptyConflicts.isEmpty())
    }

    @Test
    fun user_b_resolve_keep_remote_does_not_delete_user_a_conflict_with_same_id() = runTest {
        val auth = FakeAuthRepository(USER_2_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        remoteSyncDao.syncStateDao = syncStateDao

        val conflictA = sampleRecurringConflict("rec-shared", userId = "11111111-1111-4111-8111-111111111111")
        val conflictB = sampleRecurringConflict("rec-shared", userId = "22222222-2222-4222-8222-222222222222")
        syncStateDao.conflicts["USER:11111111-1111-4111-8111-111111111111:RECURRING_TRANSACTION:rec-shared"] = conflictA
        syncStateDao.conflicts["USER:22222222-2222-4222-8222-222222222222:RECURRING_TRANSACTION:rec-shared"] = conflictB

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-shared"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)

        // B conflict is resolved / removed
        assertNull(syncStateDao.getConflict("USER:22222222-2222-4222-8222-222222222222", "rec-shared"))

        // A conflict is STILL intact!
        val preservedA = syncStateDao.getConflict("USER:11111111-1111-4111-8111-111111111111", "rec-shared")
        assertNotNull(preservedA)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", preservedA.syncScopeKey)
    }

    @Test
    fun user_b_resolve_keep_local_does_not_affect_user_a_conflict_with_same_id() = runTest {
        val auth = FakeAuthRepository(USER_2_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        remoteSyncDao.syncStateDao = syncStateDao
        remoteSyncDao.localRecurringRow = sampleRecurringEntity("rec-shared-l", deletedAt = null)

        val conflictA = sampleRecurringConflict("rec-shared-l", userId = "11111111-1111-4111-8111-111111111111")
        val conflictB = sampleRecurringConflict("rec-shared-l", userId = "22222222-2222-4222-8222-222222222222")
        syncStateDao.conflicts["USER:11111111-1111-4111-8111-111111111111:RECURRING_TRANSACTION:rec-shared-l"] = conflictA
        syncStateDao.conflicts["USER:22222222-2222-4222-8222-222222222222:RECURRING_TRANSACTION:rec-shared-l"] = conflictB

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(FakeCoreRemoteDataSource(), remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(EntityId("rec-shared-l"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Success)

        // B conflict is resolved
        assertNull(syncStateDao.getConflict("USER:22222222-2222-4222-8222-222222222222", "rec-shared-l"))

        // A conflict is STILL intact!
        val preservedA = syncStateDao.getConflict("USER:11111111-1111-4111-8111-111111111111", "rec-shared-l")
        assertNotNull(preservedA)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", preservedA.syncScopeKey)
    }

    @Test
    fun workspace_initial_sync_is_triggered_when_marker_is_missing_even_if_profile_cursor_exists() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        // PROFILE cursor var
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "11111111-1111-4111-8111-111111111111",
            ),
        )
        // WORKSPACE_BOOTSTRAP_COMPLETE marker YOK!

        var profileInitialCalled = false
        var workspaceInitialCalled = false

        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun upsertProfileRow(entity: UserProfileEntity) {
                profileInitialCalled = true
            }

            override suspend fun applyWorkspaceSnapshot(
                syncScopeKey: String,
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                if (cursors.any { it.entityTypeCode == com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE }) {
                    workspaceInitialCalled = true
                }
                cursors.forEach { syncStateDao.upsertCursor(it) }
            }
        }

        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))

        val initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L }
        val workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L }
        val incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L }
        val workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = initialRemoteSync,
            workspaceInitialRemoteSync = workspaceInitialRemoteSync,
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = incrementalRemoteSync,
            workspaceIncrementalRemoteSync = workspaceIncrementalRemoteSync,
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)
        // Profile initial should NOT be called because PROFILE cursor exists
        assertEquals(false, profileInitialCalled)
        // Workspace initial MUST be called because WORKSPACE_BOOTSTRAP_COMPLETE is missing
        assertEquals(true, workspaceInitialCalled)
    }

    @Test
    fun workspace_initial_sync_is_skipped_when_marker_is_present() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "11111111-1111-4111-8111-111111111111",
            ),
        )
        syncStateDao.upsertCursor(
            com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.bootstrapCompleteEntity("USER:11111111-1111-4111-8111-111111111111", 1000L),
        )

        var workspaceInitialCalled = false
        var workspaceIncrementalCalled = false

        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun applyWorkspaceSnapshot(
                syncScopeKey: String,
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                // Initial writes WORKSPACE_BOOTSTRAP_COMPLETE cursor, incremental does not
                if (cursors.any { it.entityTypeCode == com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE }) {
                    workspaceInitialCalled = true
                } else {
                    workspaceIncrementalCalled = true
                }
            }
        }
        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111")) {
            override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
                workspaceIncrementalCalled = true
                return RemotePage(emptyList(), query.page, 0)
            }
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)
        assertEquals(false, workspaceInitialCalled)
        assertEquals(true, workspaceIncrementalCalled)
    }

    @Test
    fun workspace_initial_failure_returns_repository_failure() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()

        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource() {
            override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
                throw io.ktor.utils.io.errors.IOException("Network error during workspace initial")
            }
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("11111111-1111-4111-8111-111111111111")), remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Network)
    }

    @Test
    fun workspace_incremental_failure_returns_repository_failure() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "11111111-1111-4111-8111-111111111111",
            ),
        )
        syncStateDao.upsertCursor(
            com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.bootstrapCompleteEntity("USER:11111111-1111-4111-8111-111111111111", 1000L),
        )
        val remoteSyncDao = FakeRemoteSyncDao()

        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource() {
            override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
                throw io.ktor.utils.io.errors.IOException("Network error during workspace incremental")
            }
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Network)
    }

    @Test
    fun workspace_initial_sync_fails_closed_when_marker_is_invalid() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "11111111-1111-4111-8111-111111111111",
            ),
        )
        // Geçersiz marker: entityId "COMPLETED" değil ("INVALID_ID")
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE,
                updatedAtEpochMillis = 1000L,
                entityId = "INVALID_ID",
            ),
        )

        var workspaceInitialCalled = false
        var outboxCalled = false
        var incrementalCalled = false

        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun applyWorkspaceSnapshot(
                syncScopeKey: String,
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                workspaceInitialCalled = true
            }
        }

        val outboxQueue = object : OutboxQueue by FakeOutboxQueue() {
            override suspend fun readyOperations(syncScopeKey: String, limit: Int): List<SyncOperationEntity> {
                outboxCalled = true
                return emptyList()
            }
        }

        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111")) {
            override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
                incrementalCalled = true
                return RemotePage(emptyList(), query.page, 0)
            }
        }

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Validation)
        assertEquals(false, workspaceInitialCalled)
        assertEquals(false, outboxCalled)
        assertEquals(false, incrementalCalled)
    }

    @Test
    fun workspace_initial_sync_fails_closed_when_marker_timestamp_is_non_positive() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "11111111-1111-4111-8111-111111111111",
            ),
        )
        // Geçersiz marker: updatedAtEpochMillis = 0L
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:11111111-1111-4111-8111-111111111111",
                entityTypeCode = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE,
                updatedAtEpochMillis = 0L,
                entityId = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID,
            ),
        )

        var workspaceInitialCalled = false
        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun applyWorkspaceSnapshot(
                syncScopeKey: String,
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                workspaceInitialCalled = true
            }
        }
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Validation)
        assertEquals(false, workspaceInitialCalled)
    }

    @Test
    fun test_A_user_A_profile_cursor_does_not_block_user_B_initial_sync_and_preserves_user_A_outbox() = runTest {
        val userA = "11111111-1111-4111-8111-111111111111"
        val userB = "22222222-2222-4222-8222-222222222222"
        val sessionB = AuthSession(EntityId(userB), "user-b@example.com", Instant.fromEpochMilliseconds(100_000L))
        val auth = FakeAuthRepository(sessionB)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:$userA",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = userA,
            ),
        )

        val opId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val entityId = "33333333-3333-4333-8333-333333333333"
        val payload = """{"id":"$entityId","amount_minor":5000}"""
        val predOpId = "00000000000000000000000000000000"
        val pendingOp = SyncOperationEntity(
            operationId = opId,
            syncScopeKey = "USER:$userA",
            entityTypeCode = "TRANSACTION",
            entityId = entityId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = payload,
            predecessorOperationId = predOpId,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 0L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
            errorClassification = null,
        )

        val syncOperationDao = FakeSyncOperationDao(initialPending = 1, initialOperations = listOf(pendingOp))
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), syncOperationDao)
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto(userB))
        val remoteSyncDao = FakeRemoteSyncDao()
        val outboxExecutor = FakeOutboxExecutor()
        val outboxQueue = FakeOutboxQueue(listOf(pendingOp))
        val outboxProcessor = OutboxProcessor(outboxQueue, outboxExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, syncOperationDao, FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)

        val overview = repository.observeOverview().first()
        assertEquals(SyncPhase.IDLE, overview.phase)
        assertNull(overview.lastError)

        val preservedOp = syncOperationDao.getById("USER:$userA", opId)
        assertNotNull(preservedOp)
        assertEquals(pendingOp, preservedOp)

        val preservedCursorA = syncStateDao.getCursor("USER:$userA", "PROFILE")
        assertNotNull(preservedCursorA)
        assertEquals(userA, preservedCursorA.entityId)
    }

    @Test
    fun test_B_inconsistent_scoped_profile_cursor_entity_id_fails_closed_with_owner_mismatch() = runTest {
        val userB = "22222222-2222-4222-8222-222222222222"
        val sessionB = AuthSession(EntityId(userB), "user-b@example.com", Instant.fromEpochMilliseconds(100_000L))
        val auth = FakeAuthRepository(sessionB)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:$userB",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "some-foreign-id",
            ),
        )

        val syncOperationDao = FakeSyncOperationDao()
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), syncOperationDao)
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto(userB))
        val remoteSyncDao = FakeRemoteSyncDao()
        val outboxExecutor = FakeOutboxExecutor()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, outboxExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, syncOperationDao, FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Failure)
        val error = result.error
        assertTrue(error is AppError.Authentication)
        assertEquals("sync.local_data_owner_mismatch", error.code)
    }

    @Test
    fun test_C_same_user_normal_sync_flow_proceeds_without_clearing_data() = runTest {
        val userA = "11111111-1111-4111-8111-111111111111"
        val sessionA = AuthSession(EntityId(userA), "user-a@example.com", Instant.fromEpochMilliseconds(100_000L))
        val auth = FakeAuthRepository(sessionA)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:$userA",
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = userA,
            ),
        )
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = "USER:$userA",
                entityTypeCode = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE,
                updatedAtEpochMillis = 1000L,
                entityId = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID,
            ),
        )

        val syncOperationDao = FakeSyncOperationDao()
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), syncOperationDao)
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto(userA))
        val remoteSyncDao = FakeRemoteSyncDao()
        val outboxExecutor = FakeOutboxExecutor()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, outboxExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, syncOperationDao, FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        println("TEST_C_RESULT: $result")
        assertTrue(result is RepositoryResult.Success)

        val storedState = syncStateDao.getUserState(userA)
        assertNotNull(storedState)
        assertEquals(5_000L, storedState.lastSuccessfulSyncAtEpochMillis)
    }

    @Test
    fun test_D_missing_profile_cursor_runs_initial_pull_and_is_not_treated_as_owner_mismatch() = runTest {
        val userA = "11111111-1111-4111-8111-111111111111"
        val sessionA = AuthSession(EntityId(userA), "user-a@example.com", Instant.fromEpochMilliseconds(100_000L))
        val auth = FakeAuthRepository(sessionA)
        val syncStateDao = FakeSyncStateDao()

        val syncOperationDao = FakeSyncOperationDao()
        val offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), syncOperationDao)
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto(userA))
        val remoteSyncDao = FakeRemoteSyncDao()
        val outboxExecutor = FakeOutboxExecutor()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, outboxExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, syncOperationDao, FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 5_000L },
        )

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)
        assertEquals(2, remote.fetchProfileCalls)

        val storedState = syncStateDao.getUserState(userA)
        assertNotNull(storedState)
        assertEquals(5_000L, storedState.lastSuccessfulSyncAtEpochMillis)

        val overview = repository.observeOverview().first()
        assertNull(overview.lastError)
        assertEquals(SyncPhase.IDLE, overview.phase)
    }

    // =========================================================================
    // P0 — Auth / Session Yarışı Dilim 1/2 Testleri
    // =========================================================================

    @Test
    fun sync_without_session_does_not_call_remote_or_outbox() = runTest {
        val auth = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result = repository.requestSync()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals(0, remote.fetchProfileCalls)
        assertEquals(0, outboxQueue.readyOperationsCalls)
        val overview = repository.observeOverview().first()
        assertEquals(SyncPhase.IDLE, overview.phase)
    }

    @Test
    fun session_change_to_B_before_first_remote_step_prevents_remote_calls() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        var switched = false
        syncStateDao.onGetCursorHook = { _, entityTypeCode ->
            if (!switched && entityTypeCode == SyncEntityType.PROFILE.name) {
                switched = true
                auth.setSession(USER_2_SESSION)
            }
        }

        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result = repository.requestSync()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals(0, remote.fetchProfileCalls)
        assertEquals(0, remote.fetchWorkspacesCalls)
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun session_change_during_initial_pull_aborts_subsequent_stages() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var remoteCancelled = false

        remote.onFetchProfile = {
            entered.complete(Unit)
            try {
                release.await()
            } catch (e: CancellationException) {
                remoteCancelled = true
                throw e
            }
        }

        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val syncDeferred = async { repository.requestSync() }

        entered.await()
        auth.setSession(USER_2_SESSION)

        val result = syncDeferred.await()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals("sync.session_invalidated", (result.error as AppError.Authentication).code)
        assertTrue(remoteCancelled)
        assertFalse(release.isCompleted)
        assertEquals(0, remote.fetchWorkspacesCalls)
        assertEquals(0, outboxQueue.readyOperationsCalls)
        assertNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun session_change_during_outbox_execution_stops_subsequent_operations_and_does_not_write_success_state() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val user1Scope = "USER:11111111-1111-4111-8111-111111111111"
        syncStateDao.upsertCursor(SyncCursorEntity(user1Scope, SyncEntityType.PROFILE.name, 100L, "11111111-1111-4111-8111-111111111111"))
        syncStateDao.upsertCursor(WorkspaceSyncCursorKeys.bootstrapCompleteEntity(user1Scope, 100L))

        val op1 = sampleOperationEntity("op-1", syncScopeKey = user1Scope)
        val op2 = sampleOperationEntity("op-2", syncScopeKey = user1Scope)
        val outboxQueue = FakeOutboxQueue(listOf(op1, op2))
        val executor = FakeOutboxExecutor()

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executorCancelled = false

        executor.onExecute = { op ->
            if (op.operationId == "op-1") {
                entered.complete(Unit)
                try {
                    release.await()
                } catch (e: CancellationException) {
                    executorCancelled = true
                    throw e
                }
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, executor)
        val remote = FakeCoreRemoteDataSource()
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val syncDeferred = async { repository.requestSync() }

        entered.await()
        auth.setSession(null)

        val result = syncDeferred.await()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals("sync.session_invalidated", (result.error as AppError.Authentication).code)
        assertTrue(executorCancelled)
        assertFalse(release.isCompleted)
        assertEquals(1, executor.executeCalls)
        assertNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun session_change_does_not_delete_user_outbox_cursor_or_conflict_records() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val user1Scope = "USER:11111111-1111-4111-8111-111111111111"

        val cursor = SyncCursorEntity(user1Scope, SyncEntityType.PROFILE.name, 100L, "11111111-1111-4111-8111-111111111111")
        val bootstrap = WorkspaceSyncCursorKeys.bootstrapCompleteEntity(user1Scope, 100L)
        syncStateDao.upsertCursor(cursor)
        syncStateDao.upsertCursor(bootstrap)

        val conflict = SyncConflictEntity(
            syncScopeKey = user1Scope,
            entityTypeCode = "TRANSACTION",
            entityId = "tx-c1",
            operationId = "op-c1",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 100L,
        )
        syncStateDao.conflicts[conflict.entityId] = conflict

        val op = sampleOperationEntity("op-persisted", syncScopeKey = user1Scope)
        val outboxQueue = FakeOutboxQueue(listOf(op))
        val executor = FakeOutboxExecutor()

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        executor.onExecute = {
            entered.complete(Unit)
            release.await()
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, executor)
        val remote = FakeCoreRemoteDataSource()
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val syncDeferred = async { repository.requestSync() }

        entered.await()
        auth.setSession(USER_2_SESSION)

        val result = syncDeferred.await()

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(cursor, syncStateDao.getCursor(user1Scope, SyncEntityType.PROFILE.name))
        assertEquals(conflict, syncStateDao.getConflict(user1Scope, "tx-c1"))
        assertTrue(outboxQueue.readyOps.any { it.operationId == "op-persisted" && it.syncScopeKey == user1Scope })
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun same_user_token_or_expiry_change_does_not_cancel_sync() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        remote.onFetchProfile = {
            val refreshedSession = AuthSession(
                userId = EntityId("11111111-1111-4111-8111-111111111111"),
                email = "user-1@example.com",
                expiresAt = Instant.fromEpochMilliseconds(999_999L),
            )
            auth.setSession(refreshedSession)
        }

        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result = repository.requestSync()

        assertTrue(result is RepositoryResult.Success)
        val storedState = syncStateDao.getUserState("11111111-1111-4111-8111-111111111111")
        assertNotNull(storedState)
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun retry_failed_operations_enforces_same_session_guard_contract() = runTest {
        val authNull = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())
        val repoNull = createRepository(authNull, FakeCoreRemoteDataSource(), syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val resNull = repoNull.retryFailedOperations()
        assertTrue(resNull is RepositoryResult.Failure)
        assertTrue(resNull.error is AppError.Authentication)

        val auth = FakeAuthRepository(USER_1_SESSION)
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        remote.onFetchProfile = {
            auth.setSession(USER_2_SESSION)
        }
        val repo = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)
        val res = repo.retryFailedOperations()

        assertTrue(res is RepositoryResult.Failure)
        assertTrue(res.error is AppError.Authentication)
        assertEquals(SyncPhase.IDLE, repo.observeOverview().first().phase)
    }

    @Test
    fun external_cancellation_is_rethrown_without_converting_to_auth_failure() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var remoteCancelled = false

        remote.onFetchProfile = {
            entered.complete(Unit)
            try {
                release.await()
            } catch (e: CancellationException) {
                remoteCancelled = true
                throw e
            }
        }

        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        var caughtCancellation: CancellationException? = null
        val job = launch {
            try {
                repository.requestSync()
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }

        entered.await()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertNotNull(caughtCancellation)
        assertTrue(remoteCancelled)
        assertFalse(release.isCompleted)
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun session_change_after_successful_sync_does_not_affect_completed_sync_and_watcher_is_cleaned_up() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result = repository.requestSync()
        assertTrue(result is RepositoryResult.Success)
        assertNotNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))

        // Session changes after sync has fully completed
        auth.setSession(USER_2_SESSION)

        // Verify that completed sync result and stored state remain untouched
        assertNotNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun auth_change_during_sync_leaves_phase_in_idle() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        remote.onFetchProfile = {
            auth.setSession(null)
        }
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result = repository.requestSync()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun subsequent_sync_call_for_user_B_runs_independently_with_B_snapshot() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("11111111-1111-4111-8111-111111111111"))
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        // Sync 1: User A gets cancelled by switching to User B
        remote.onFetchProfile = {
            auth.setSession(USER_2_SESSION)
        }
        val repository = createRepository(auth, remote, syncStateDao, remoteSyncDao, outboxProcessor, outboxQueue)

        val result1 = repository.requestSync()
        assertTrue(result1 is RepositoryResult.Failure)
        assertTrue(result1.error is AppError.Authentication)
        assertNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))

        // Sync 2: Now session is User B, onFetchProfile doesn't switch auth
        remote.onFetchProfile = null
        remote.profile = sampleProfileDto("22222222-2222-4222-8222-222222222222")
        val result2 = repository.requestSync()
        assertTrue(result2 is RepositoryResult.Success)

        val userBState = syncStateDao.getUserState("22222222-2222-4222-8222-222222222222")
        assertNotNull(userBState)
        assertNull(syncStateDao.getUserState("11111111-1111-4111-8111-111111111111"))
        assertEquals(SyncPhase.IDLE, repository.observeOverview().first().phase)
    }

    @Test
    fun overview_hasLegacyQuarantinedData_is_true_when_quarantine_rows_exist_and_user_is_authenticated() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val fakeOpDao = FakeSyncOperationDao()
        val writeQueue = OfflineWriteQueue(FakeLocalMutationDao(), fakeOpDao)
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        val repository = createRepository(
            auth = auth,
            remote = remote,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            outboxProcessor = outboxProcessor,
            outboxQueue = outboxQueue,
            offlineWriteQueue = writeQueue,
        )

        // Initially no quarantine rows
        assertFalse(repository.observeOverview().first().hasLegacyQuarantinedData)

        // Add quarantine operation
        fakeOpDao.insert(sampleOperationEntity("q-op-1", syncScopeKey = "LEGACY_UNRESOLVED"))
        assertTrue(repository.observeOverview().first().hasLegacyQuarantinedData)
    }

    @Test
    fun overview_hasLegacyQuarantinedData_is_false_when_signed_out_even_with_quarantine_rows() = runTest {
        val auth = FakeAuthRepository(null)
        val syncStateDao = FakeSyncStateDao()
        val fakeOpDao = FakeSyncOperationDao()
        fakeOpDao.insert(sampleOperationEntity("q-op-1", syncScopeKey = "LEGACY_UNRESOLVED"))
        syncStateDao.upsertCursor(SyncCursorEntity("LEGACY_UNRESOLVED", "TRANSACTION", 9999L, "q-tx"))
        syncStateDao.upsertConflict(sampleRecurringConflict("rec-1", syncScopeKey = "LEGACY_UNRESOLVED"))

        val writeQueue = OfflineWriteQueue(FakeLocalMutationDao(), fakeOpDao)
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        val repository = createRepository(
            auth = auth,
            remote = remote,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            outboxProcessor = outboxProcessor,
            outboxQueue = outboxQueue,
            offlineWriteQueue = writeQueue,
        )

        val overview = repository.observeOverview().first()
        assertFalse(overview.hasLegacyQuarantinedData, "Quarantine presence must never be exposed when signed out")
    }

    @Test
    fun overview_hasLegacyQuarantinedData_is_false_when_no_quarantine_rows_exist() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val fakeOpDao = FakeSyncOperationDao()
        fakeOpDao.insert(sampleOperationEntity("user-op-1", syncScopeKey = "USER:11111111-1111-4111-8111-111111111111"))

        val writeQueue = OfflineWriteQueue(FakeLocalMutationDao(), fakeOpDao)
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource()
        val outboxQueue = FakeOutboxQueue()
        val outboxProcessor = OutboxProcessor(outboxQueue, FakeOutboxExecutor())

        val repository = createRepository(
            auth = auth,
            remote = remote,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            outboxProcessor = outboxProcessor,
            outboxQueue = outboxQueue,
            offlineWriteQueue = writeQueue,
        )

        val overview = repository.observeOverview().first()
        assertFalse(overview.hasLegacyQuarantinedData)
    }

    private fun createRepository(
        auth: FakeAuthRepository,
        remote: FakeCoreRemoteDataSource,
        syncStateDao: FakeSyncStateDao,
        remoteSyncDao: FakeRemoteSyncDao,
        outboxProcessor: OutboxProcessor,
        outboxQueue: FakeOutboxQueue,
        nowEpochMillis: Long = 5_000L,
        offlineWriteQueue: OfflineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
    ): OfflineFirstSyncRepository {
        val initialRemoteSync = InitialRemoteSync(remote, remoteSyncDao) { 1_000L }
        val incrementalRemoteSync = IncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L }
        val workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(remote, remoteSyncDao) { 1_000L }
        val workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(remote, remoteSyncDao, syncStateDao) { 1_000L }

        return OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = initialRemoteSync,
            workspaceInitialRemoteSync = workspaceInitialRemoteSync,
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = incrementalRemoteSync,
            workspaceIncrementalRemoteSync = workspaceIncrementalRemoteSync,
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, FakeSyncOperationDao(), FakeLocalMutationDao()) { 1_000L },
            nowEpochMillisProvider = { nowEpochMillis },
        )
    }

    private companion object {
        val USER_1_SESSION = AuthSession(EntityId("11111111-1111-4111-8111-111111111111"), "user-1@example.com", Instant.fromEpochMilliseconds(100_000L))
        val USER_2_SESSION = AuthSession(EntityId("22222222-2222-4222-8222-222222222222"), "user-2@example.com", Instant.fromEpochMilliseconds(100_000L))
        val USER_3_SESSION = AuthSession(EntityId("33333333-3333-4333-8333-333333333333"), "user-3@example.com", Instant.fromEpochMilliseconds(100_000L))

        fun sampleProfileDto(userId: String) = ProfileDto(
            id = userId,
            email = "$userId@example.com",
            fullName = "Test User",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            version = 1L,
        )

        fun sampleOperationEntity(id: String, syncScopeKey: String = "USER:11111111-1111-4111-8111-111111111111") = SyncOperationEntity(
            operationId = id,
            syncScopeKey = syncScopeKey,
            entityTypeCode = "TRANSACTION",
            entityId = "tx-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0L,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        fun sampleRecurringConflict(
            id: String,
            userId: String = "11111111-1111-4111-8111-111111111111",
            syncScopeKey: String = "USER:$userId",
        ) = SyncConflictEntity(
            syncScopeKey = syncScopeKey,
            entityTypeCode = "RECURRING_TRANSACTION",
            entityId = id,
            operationId = "op-$id",
            localVersion = 1L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$id","user_id":"$userId","amount_minor":40000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","version":1}""",
            remotePayloadJson = """{"id":"$id","user_id":"$userId","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-05T00:00:00Z","version":3}""",
            detectedAtEpochMillis = 1_000L,
        )

        fun sampleRecurringEntity(id: String, deletedAt: Long? = null) = RecurringTransactionEntity(
            id = id,
            ownerId = "11111111-1111-4111-8111-111111111111",
            workspaceId = null,
            amountMinor = 40_000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = "cat-1",
            description = "Local recurring",
            paymentMethodCode = "CASH",
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAtEpochMillis = 1_000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "CONFLICT",
                updatedAtEpochMillis = 1_000L,
                localUpdatedAtEpochMillis = 1_000L,
                deletedAtEpochMillis = deletedAt,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        fun sampleWorkspaceConflict(
            id: String,
            remoteOwnerId: String = "11111111-1111-4111-8111-111111111111",
            remoteDeletedAt: String? = null,
            syncScopeKey: String = "USER:11111111-1111-4111-8111-111111111111",
        ) = SyncConflictEntity(
            syncScopeKey = syncScopeKey,
            entityTypeCode = "WORKSPACE",
            entityId = id,
            operationId = "op-$id",
            localVersion = 1L,
            remoteVersion = 4L,
            localPayloadJson = """{"id":"$id","name":"Local WS","type_code":"personal","currency_code":"TRY","created_at":"2026-08-01T00:00:00Z"}""",
            remotePayloadJson = """{"id":"$id","name":"Remote WS","normalized_name":"remote ws","owner_id":"$remoteOwnerId","type_code":"personal","currency_code":"TRY","description":"Remote desc","created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-05T00:00:00Z","deleted_at":${if (remoteDeletedAt != null) "\"$remoteDeletedAt\"" else "null"},"version":4}""",
            detectedAtEpochMillis = 2_000L,
        )

        fun sampleWorkspaceEntity(id: String, ownerId: String = "11111111-1111-4111-8111-111111111111", deletedAt: Long? = null) = WorkspaceEntity(
            id = id,
            name = "Local WS",
            normalizedName = "local ws",
            ownerId = ownerId,
            typeCode = "personal",
            currencyCode = "TRY",
            description = null,
            createdAtEpochMillis = 1_000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "CONFLICT",
                updatedAtEpochMillis = 1_000L,
                localUpdatedAtEpochMillis = 1_000L,
                deletedAtEpochMillis = deletedAt,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        fun sampleWorkspaceOperation(
            id: String,
            workspaceId: String,
            typeCode: String = "CREATE",
            payload: String = """{"id":"$workspaceId","name":"Local WS"}""",
            syncScopeKey: String = "USER:11111111-1111-4111-8111-111111111111",
        ) = SyncOperationEntity(
            operationId = id,
            syncScopeKey = syncScopeKey,
            entityTypeCode = "WORKSPACE",
            entityId = workspaceId,
            operationTypeCode = typeCode,
            payloadJson = payload,
            baseVersion = if (typeCode == "CREATE") null else 1L,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "CONFLICT",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0L,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }

    private class FakeAuthRepository(initialSession: AuthSession?) : AuthRepository {
        private val sessionFlow = MutableStateFlow(initialSession)
        fun setSession(session: AuthSession?) { sessionFlow.value = session }
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("N/A")
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("N/A")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("N/A")
        override suspend fun signOut(): RepositoryResult<Unit> = error("N/A")
    }

    private class FakeSyncStateDao : SyncStateDao {
        var upsertUserStateError: Throwable? = null
        var getConflictError: Throwable? = null
        val conflicts = mutableMapOf<String, SyncConflictEntity>()
        private val userStates = mutableMapOf<String, MutableStateFlow<Long?>>()
        private val cursors = mutableMapOf<String, SyncCursorEntity>()
        var conflictCount: Int = 0

        val queriedGetAllConflictsScopes = mutableListOf<String>()
        private val _conflictsTrigger = MutableStateFlow(0)
        private val _cursorsTrigger = MutableStateFlow(0)
        fun notifyConflictsChanged() { _conflictsTrigger.value++ }
        var onGetCursorHook: ((String, String) -> Unit)? = null

        override suspend fun getCursor(syncScopeKey: String, entityTypeCode: String): SyncCursorEntity? {
            onGetCursorHook?.invoke(syncScopeKey, entityTypeCode)
            return cursors["${syncScopeKey}:${entityTypeCode}"] ?: cursors[entityTypeCode]?.takeIf { it.syncScopeKey == syncScopeKey }
        }
        override suspend fun getWorkspaceMemberCursors(syncScopeKey: String): List<SyncCursorEntity> =
            cursors.values.filter { it.syncScopeKey == syncScopeKey && it.entityTypeCode.startsWith("WORKSPACE_MEMBER:") }
        override suspend fun getConflict(syncScopeKey: String, entityId: String): SyncConflictEntity? {
            getConflictError?.let { throw it }
            return conflicts.values.firstOrNull { it.syncScopeKey == syncScopeKey && it.entityId == entityId }
                ?: conflicts[entityId]?.takeIf { it.syncScopeKey == syncScopeKey }
        }
        override suspend fun getConflict(syncScopeKey: String, entityTypeCode: String, entityId: String): SyncConflictEntity? {
            getConflictError?.let { throw it }
            return conflicts.values.firstOrNull { it.syncScopeKey == syncScopeKey && it.entityTypeCode == entityTypeCode && it.entityId == entityId }
                ?: conflicts[entityId]?.takeIf { it.syncScopeKey == syncScopeKey && it.entityTypeCode == entityTypeCode }
        }
        val queriedRecoveryScopes = mutableListOf<String>()
        override suspend fun getConflictsByEntityType(syncScopeKey: String, entityTypeCode: String): List<SyncConflictEntity> {
            queriedRecoveryScopes.add(syncScopeKey)
            return conflicts.values.filter { it.syncScopeKey == syncScopeKey && it.entityTypeCode == entityTypeCode }
        }
        override suspend fun getAllConflicts(syncScopeKey: String): List<SyncConflictEntity> {
            queriedRecoveryScopes.add(syncScopeKey)
            return conflicts.values.filter { it.syncScopeKey == syncScopeKey }
        }
        override suspend fun upsertCursor(cursor: SyncCursorEntity) {
            cursors["${cursor.syncScopeKey}:${cursor.entityTypeCode}"] = cursor
            _cursorsTrigger.value++
        }
        override fun observeConflicts(syncScopeKey: String): Flow<List<SyncConflictEntity>> =
            _conflictsTrigger.map { conflicts.values.filter { it.syncScopeKey == syncScopeKey } }
        override fun observeConflictCount(syncScopeKey: String): Flow<Int> =
            _conflictsTrigger.map { conflicts.values.count { it.syncScopeKey == syncScopeKey } }
        override suspend fun getConflictCount(syncScopeKey: String): Int =
            conflicts.values.count { it.syncScopeKey == syncScopeKey }
        override suspend fun upsertConflict(conflict: SyncConflictEntity) {
            conflicts["${conflict.syncScopeKey}:${conflict.entityTypeCode}:${conflict.entityId}"] = conflict
            _conflictsTrigger.value++
        }
        override suspend fun deleteConflict(syncScopeKey: String, entityTypeCode: String, entityId: String): Int {
            val key = "${syncScopeKey}:${entityTypeCode}:${entityId}"
            val removed = (conflicts.remove(key) != null) or (conflicts[entityId]?.takeIf { it.syncScopeKey == syncScopeKey && it.entityTypeCode == entityTypeCode }?.let { conflicts.remove(entityId); true } ?: false)
            if (removed) _conflictsTrigger.value++
            return if (removed) 1 else 0
        }

        override fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?> =
            userStates.getOrPut(userId) { MutableStateFlow(null) }

        override suspend fun getUserState(userId: String): SyncUserStateEntity? =
            userStates[userId]?.value?.let { SyncUserStateEntity(userId, it, it) }

        override suspend fun upsertUserState(state: SyncUserStateEntity) {
            upsertUserStateError?.let { throw it }
            userStates.getOrPut(state.userId) { MutableStateFlow(null) }.value = state.lastSuccessfulSyncAtEpochMillis
        }

        override fun observeLegacyQuarantineCursorCount(): Flow<Int> =
            _cursorsTrigger.map { cursors.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" } }

        override suspend fun getLegacyQuarantineCursorCount(): Int =
            cursors.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" }

        override fun observeLegacyQuarantineConflictCount(): Flow<Int> =
            _conflictsTrigger.map { conflicts.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" } }

        override suspend fun getLegacyQuarantineConflictCount(): Int =
            conflicts.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" }
    }

    private class FakeRemoteSyncDao : RemoteSyncDao {
        var localRecurringRow: RecurringTransactionEntity? = null
        var resolvedRecurringRemote: RecurringTransactionEntity? = null
        var resolveKeepLocalCalledWith: Triple<SyncConflictEntity, String, Long>? = null
        var mutationCallCount = 0
        var lastRecordedSyncScopeKey: String? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = localRecurringRow
        override suspend fun getSubscriptionRow(id: String): com.feniqo.mobile.data.local.entity.SubscriptionEntity? = null
        override suspend fun getFirstOutboxOperationId(syncScopeKey: String, entityTypeCode: String, entityId: String): String? = null
        override suspend fun countOutboxRows(syncScopeKey: String, entityTypeCode: String, entityId: String): Int = 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) = Unit
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>) = Unit
        override suspend fun upsertSubscriptionRows(entities: List<com.feniqo.mobile.data.local.entity.SubscriptionEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) = Unit
        var syncStateDao: SyncStateDao? = null

        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) {
            cursors.forEach { syncStateDao?.upsertCursor(it) }
        }
        override suspend fun deleteConflictRow(syncScopeKey: String, entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 0
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int = 0
        override suspend fun markGoalConflict(entityId: String, error: String): Int = 0
        override suspend fun markGoalContributionConflict(entityId: String, error: String): Int = 0
        override suspend fun markDebtConflict(entityId: String, error: String): Int = 0
        override suspend fun markDebtPaymentConflict(entityId: String, error: String): Int = 0
        override suspend fun getGoalRow(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        var localWorkspaceRow: WorkspaceEntity? = null
        var workspaceOperationsMap = mutableMapOf<String, List<SyncOperationEntity>>()
        var conflictRow: SyncConflictEntity? = null
        var resolvedWorkspaceRemote: Pair<WorkspaceResolutionPrecondition, WorkspaceEntity>? = null
        var resolvedWorkspaceLocal: ResolvedWorkspaceLocalArgs? = null

        override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = localWorkspaceRow
        override suspend fun getAllWorkspaceOperations(syncScopeKey: String, workspaceId: String): List<SyncOperationEntity> =
            workspaceOperationsMap[workspaceId] ?: emptyList()
        override suspend fun deleteSpecificWorkspaceOperations(syncScopeKey: String, workspaceId: String, operationIds: List<String>): Int =
            operationIds.size
        override suspend fun rebaseWorkspaceForRetry(
            workspaceId: String,
            syncStatus: String,
            remoteVersion: Long,
            nowEpochMillis: Long,
        ): Int = 1
        override suspend fun resetWorkspaceConflictOperation(
            syncScopeKey: String,
            operationId: String,
            operationTypeCode: String,
            payloadJson: String?,
            remoteVersion: Long,
            nowEpochMillis: Long,
        ): Int = 1
        override suspend fun getConflictRow(syncScopeKey: String, entityTypeCode: String, entityId: String): SyncConflictEntity? = conflictRow
        override suspend fun resolveWorkspaceKeepRemote(
            syncScopeKey: String,
            precondition: WorkspaceResolutionPrecondition,
            remoteEntity: WorkspaceEntity,
        ) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
            resolvedWorkspaceRemote = precondition to remoteEntity
        }
        override suspend fun resolveWorkspaceKeepLocal(
            syncScopeKey: String,
            precondition: WorkspaceResolutionPrecondition,
            retainedOperationId: String,
            targetOperationTypeCode: String,
            targetPayloadJson: String?,
            nowEpochMillis: Long,
        ) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
            resolvedWorkspaceLocal = ResolvedWorkspaceLocalArgs(
                precondition = precondition,
                retainedOperationId = retainedOperationId,
                targetOperationTypeCode = targetOperationTypeCode,
                targetPayloadJson = targetPayloadJson,
                nowEpochMillis = nowEpochMillis,
            )
        }

        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity> = emptyList()
        override suspend fun upsertWorkspaceRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity>) = Unit
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun upsertGoalRows(entities: List<com.feniqo.mobile.data.local.entity.GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<com.feniqo.mobile.data.local.entity.GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<com.feniqo.mobile.data.local.entity.DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity>) = Unit
        override suspend fun deleteOutboxRows(syncScopeKey: String, entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(syncScopeKey: String, entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(syncScopeKey: String, operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun resolveRecurringTransactionKeepRemote(syncScopeKey: String, entity: RecurringTransactionEntity) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
            resolvedRecurringRemote = entity
            syncStateDao?.deleteConflict(syncScopeKey, "RECURRING_TRANSACTION", entity.id)
        }
        override suspend fun resolveProfileKeepRemote(syncScopeKey: String, entity: UserProfileEntity) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
        }
        override suspend fun resolveCategoryKeepRemote(syncScopeKey: String, entity: CategoryEntity) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
        }
        override suspend fun resolveTransactionKeepRemote(syncScopeKey: String, entity: TransactionEntity) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
        }
        override suspend fun resolveSubscriptionKeepRemote(syncScopeKey: String, entity: com.feniqo.mobile.data.local.entity.SubscriptionEntity) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
        }
        override suspend fun resolveKeepLocal(
            syncScopeKey: String,
            conflict: SyncConflictEntity,
            operationTypeCode: String,
            nowEpochMillis: Long,
        ) {
            mutationCallCount++
            lastRecordedSyncScopeKey = syncScopeKey
            resolveKeepLocalCalledWith = Triple(conflict, operationTypeCode, nowEpochMillis)
            syncStateDao?.deleteConflict(syncScopeKey, conflict.entityTypeCode, conflict.entityId)
        }

        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun applyWorkspaceSnapshot(
            syncScopeKey: String,
            workspaces: List<WorkspaceEntity>,
            members: List<WorkspaceMemberEntity>,
            cursors: List<SyncCursorEntity>,
        ) {
            cursors.forEach { syncStateDao?.upsertCursor(it) }
        }
        override suspend fun getActiveWorkspaceTailOperation(syncScopeKey: String, workspaceId: String): SyncOperationEntity? = null
        override suspend fun markWorkspaceConflict(entityId: String, error: String): Int = 0
    }

    private data class ResolvedWorkspaceLocalArgs(
        val precondition: WorkspaceResolutionPrecondition,
        val retainedOperationId: String,
        val targetOperationTypeCode: String,
        val targetPayloadJson: String?,
        val nowEpochMillis: Long,
    )



    private class FakeCoreRemoteDataSource(var profile: ProfileDto? = null) : CoreRemoteDataSource {
        var fetchProfileCalls = 0
        var fetchCategoriesCalls = 0
        var fetchTransactionsCalls = 0
        var fetchBudgetsCalls = 0
        var fetchWorkspacesCalls = 0
        var fetchWorkspaceMembersCalls = 0
        var onFetchProfile: (suspend () -> Unit)? = null

        override suspend fun fetchProfile(userId: String): ProfileDto? {
            fetchProfileCalls++
            onFetchProfile?.invoke()
            return profile
        }
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
            fetchCategoriesCalls++
            return RemotePage(emptyList(), query.page, 0)
        }
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> {
            fetchTransactionsCalls++
            return RemotePage(emptyList(), query.page, 0)
        }
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.RecurringTransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.SubscriptionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalContributionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtPaymentDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
            fetchWorkspacesCalls++
            return RemotePage(emptyList(), query.page, 0)
        }
        override suspend fun fetchWorkspaces(request: RemotePageRequest): RemotePage<WorkspaceDto> {
            fetchWorkspacesCalls++
            return RemotePage(emptyList(), request, 0)
        }
        override suspend fun fetchWorkspaceMembers(query: com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    private class FakeOutboxQueue(val readyOps: List<SyncOperationEntity> = emptyList()) : OutboxQueue {
        var readyOperationsCalls = 0

        override suspend fun readyOperations(syncScopeKey: String, limit: Int): List<SyncOperationEntity> {
            readyOperationsCalls++
            return readyOps.filter { it.syncScopeKey == syncScopeKey }
        }
        override suspend fun claimOperation(syncScopeKey: String, operationId: String): SyncOperationEntity? {
            coroutineContext.ensureActive()
            return readyOps.find { it.syncScopeKey == syncScopeKey && it.operationId == operationId }?.copy(statusCode = "IN_FLIGHT", attemptCount = 1)
        }
        override suspend fun markSucceeded(syncScopeKey: String, operationId: String): Boolean = true
        override suspend fun ackV2Execution(syncScopeKey: String, operationId: String, result: com.feniqo.mobile.data.sync.OutboxExecutionResult): Boolean = true
        override suspend fun recordV2Conflict(syncScopeKey: String, conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean = conflict.syncScopeKey == syncScopeKey
        override suspend fun markConflict(syncScopeKey: String, operationId: String, errorMessage: String): Boolean = true
        override suspend fun recordFailure(syncScopeKey: String, operationId: String, errorMessage: String): Boolean = true
    }

    private class FakeOutboxExecutor : OutboxOperationExecutor {
        var executeCalls = 0
        var onExecute: (suspend (SyncOperationEntity) -> Unit)? = null

        override suspend fun execute(operation: SyncOperationEntity): com.feniqo.mobile.data.sync.OutboxExecutionResult {
            coroutineContext.ensureActive()
            executeCalls++
            onExecute?.invoke(operation)
            kotlinx.coroutines.yield()
            coroutineContext.ensureActive()
            return com.feniqo.mobile.data.sync.OutboxExecutionResult.V1Completed
        }
    }

    private class FakeLocalMutationDao : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRow(entity: CategoryEntity) = Unit
        override suspend fun upsertBudgetRow(entity: BudgetEntity) = Unit
        override suspend fun upsertTransactionRow(entity: TransactionEntity) = Unit
        override suspend fun insertTransactionRow(entity: TransactionEntity) = Unit
        override suspend fun upsertTagRows(entities: List<TagEntity>) = Unit
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) = Unit
        override suspend fun upsertRecurringTransactionRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionEntity) = Unit
        override suspend fun upsertSubscriptionRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionEntity) = Unit
        override suspend fun upsertAssetRow(entity: com.feniqo.mobile.data.local.entity.AssetEntity) = Unit
        override suspend fun deleteAssetRow(id: String): Int = 0
        override suspend fun upsertRecurringOccurrenceRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity) = Unit
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun advanceRecurringLastGeneratedDate(recurringTransactionId: String, expectedPreviousLastGeneratedDate: String?, newDueDate: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
        override suspend fun deleteProfileRow(id: String): Int = 0
        override suspend fun deleteCategoryRow(id: String): Int = 0
        override suspend fun deleteBudgetRow(id: String): Int = 0
        override suspend fun deleteTransactionRow(id: String): Int = 0
        override suspend fun deleteOutboxRow(syncScopeKey: String, operationId: String): Int = 0
        override suspend fun getOutboxById(syncScopeKey: String, operationId: String): SyncOperationEntity? = null
        override suspend fun getSuccessors(syncScopeKey: String, predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveTailCandidates(syncScopeKey: String, entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(syncScopeKey: String, operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun convertToPendingDelete(syncScopeKey: String, operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
        override suspend fun convertPendingDeleteToUpdate(syncScopeKey: String, operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun unblockSuccessor(syncScopeKey: String, operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) = Unit
        override suspend fun deleteConflictRow(syncScopeKey: String, entityTypeCode: String, entityId: String): Int = 0
        override suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseAssetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markAssetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteRecurringTransactionRow(id: String): Int = 0
        override suspend fun deleteSubscriptionRow(id: String): Int = 0
        override suspend fun markCategorySyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markBudgetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markRecurringTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markSubscriptionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteWorkspaceRow(id: String): Int = 0
        override suspend fun deleteWorkspaceMemberRows(workspaceId: String): Int = 0
        override suspend fun rebaseWorkspaceVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markWorkspaceSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun upsertWorkspaceMemberRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity) = Unit
        override suspend fun upsertWorkspaceInvitationRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity) = Unit
        override suspend fun deleteWorkspaceInvitationRow(id: String): Int = 0
        override suspend fun getWorkspaceInvitationById(id: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceInvitationByTokenHash(tokenHash: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceMember(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun rebaseWorkspaceMemberVersion(workspaceId: String, userId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseWorkspaceInvitationVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markWorkspaceMemberSyncedIfDeleted(workspaceId: String, userId: String, nowEpochMillis: Long): Int = 0
        override suspend fun upsertSubscriptionPriceHistoryRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionPriceHistoryEntity) = Unit
        override suspend fun upsertSubscriptionPaymentRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionPaymentEntity) = Unit
        override suspend fun upsertGoalRow(entity: com.feniqo.mobile.data.local.entity.GoalEntity) = Unit
        override suspend fun upsertGoalContributionRow(entity: com.feniqo.mobile.data.local.entity.GoalContributionEntity) = Unit
        override suspend fun upsertDebtRow(entity: com.feniqo.mobile.data.local.entity.DebtEntity) = Unit
        override suspend fun upsertDebtPaymentRow(entity: com.feniqo.mobile.data.local.entity.DebtPaymentEntity) = Unit
        override suspend fun deleteGoalRow(id: String): Int = 0
        override suspend fun deleteGoalContributionRow(id: String): Int = 0
        override suspend fun deleteDebtRow(id: String): Int = 0
        override suspend fun deleteDebtPaymentRow(id: String): Int = 0
        override suspend fun getGoalById(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getDebtById(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getGoalContributionById(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtPaymentById(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        override suspend fun getActiveGoalContributions(goalId: String): List<com.feniqo.mobile.data.local.entity.GoalContributionEntity> = emptyList()
        override suspend fun getActiveDebtPayments(debtId: String): List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity> = emptyList()
        override suspend fun rebaseGoalVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseGoalContributionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseDebtVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseDebtPaymentVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markGoalSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markDebtSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun tombstoneGoalContributionsForDeletedGoal(goalId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
        override suspend fun tombstoneDebtPaymentsForDeletedDebt(debtId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
        override suspend fun getActiveGoalAggregateTailCandidates(syncScopeKey: String, goalId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveDebtAggregateTailCandidates(syncScopeKey: String, debtId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun countPendingGoalAggregateOperations(syncScopeKey: String, goalId: String, operationId: String): Int = 0
        override suspend fun countPendingDebtAggregateOperations(syncScopeKey: String, debtId: String, operationId: String): Int = 0
        override suspend fun setGoalSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setGoalContributionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtPaymentSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun upsertConflictRow(entity: com.feniqo.mobile.data.local.entity.SyncConflictEntity) = Unit
        override suspend fun setOutboxStatusConflict(syncScopeKey: String, operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    }


    private class FakeSyncOperationDao(
        initialPending: Int = 0,
        initialFailed: Int = 0,
        initialOperations: List<SyncOperationEntity> = emptyList(),
    ) : SyncOperationDao {
        private val pendingFlow = MutableStateFlow(initialPending)
        private val failedFlow = MutableStateFlow(initialFailed)
        private val _opsTrigger = MutableStateFlow(0)
        val operations = initialOperations.associateBy { it.operationId }.toMutableMap()
        var retryAllFailedCalls = 0

        override fun observePendingCount(syncScopeKey: String): Flow<Int> = pendingFlow
        override fun observeFailedCount(syncScopeKey: String): Flow<Int> = failedFlow
        override suspend fun getReadyOperations(syncScopeKey: String, nowEpochMillis: Long, limit: Int): List<SyncOperationEntity> =
            operations.values.filter { it.syncScopeKey == syncScopeKey && it.statusCode == "PENDING" }
        override suspend fun getById(syncScopeKey: String, operationId: String): SyncOperationEntity? =
            operations[operationId]?.takeIf { it.syncScopeKey == syncScopeKey }
        override suspend fun insert(operation: SyncOperationEntity) {
            operations[operation.operationId] = operation
            _opsTrigger.value++
        }
        override suspend fun claimOperation(syncScopeKey: String, operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun markFailed(syncScopeKey: String, operationId: String, lastError: String, errorClassification: String?, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
        override suspend fun markConflict(syncScopeKey: String, operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
        override suspend fun recoverStaleInFlight(syncScopeKey: String, staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
        override suspend fun retryAllFailed(syncScopeKey: String, nowEpochMillis: Long): Int {
            retryAllFailedCalls++
            var updated = 0
            operations.values.filter { it.syncScopeKey == syncScopeKey && it.statusCode == "FAILED" }.forEach { op ->
                operations[op.operationId] = op.copy(statusCode = "PENDING", attemptCount = 0)
                updated++
            }
            return updated
        }
        override suspend fun deleteCompleted(syncScopeKey: String, operationId: String): Int = 1
        override suspend fun deleteForEntity(syncScopeKey: String, entityTypeCode: String, entityId: String): Int = 0
        override suspend fun getSuccessors(syncScopeKey: String, predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun unblockSuccessor(syncScopeKey: String, operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun getActiveTailCandidates(syncScopeKey: String, entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(syncScopeKey: String, operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 1
        override suspend fun convertToPendingDelete(syncScopeKey: String, operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 1
        override fun observeLegacyQuarantineOperationCount(): Flow<Int> =
            _opsTrigger.map { operations.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" } }
        override suspend fun getLegacyQuarantineOperationCount(): Int =
            operations.values.count { it.syncScopeKey == "LEGACY_UNRESOLVED" }
    }
}
