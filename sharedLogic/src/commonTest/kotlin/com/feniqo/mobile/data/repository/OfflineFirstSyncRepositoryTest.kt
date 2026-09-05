package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.domain.repository.ConflictResolution
import kotlinx.coroutines.CancellationException

class OfflineFirstSyncRepositoryTest {

    @Test
    fun successful_sync_writes_timestamp_to_room_for_active_user() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        val remoteSyncDao = FakeRemoteSyncDao()
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("user-1"))
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

        val storedState = syncStateDao.getUserState("user-1")
        assertEquals("user-1", storedState?.userId)
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
                userId = "user-1",
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
            SyncUserStateEntity(userId = "user-1", lastSuccessfulSyncAtEpochMillis = 3_000L, updatedAtEpochMillis = 3_000L),
        )
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "user-2", lastSuccessfulSyncAtEpochMillis = 7_000L, updatedAtEpochMillis = 7_000L),
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
            SyncUserStateEntity(userId = "user-1", lastSuccessfulSyncAtEpochMillis = 3_000L, updatedAtEpochMillis = 3_000L),
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
            SyncUserStateEntity(userId = "user-1", lastSuccessfulSyncAtEpochMillis = 2_000L, updatedAtEpochMillis = 2_000L),
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
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), remoteSyncDao) { 1_000L },
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
        assertEquals(2_000L, syncStateDao.getUserState("user-1")?.lastSuccessfulSyncAtEpochMillis)
    }

    @Test
    fun conflict_during_sync_returns_conflict_failure_and_preserves_stored_sync_time() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        syncStateDao.upsertUserState(
            SyncUserStateEntity(userId = "user-1", lastSuccessfulSyncAtEpochMillis = 4_000L, updatedAtEpochMillis = 4_000L),
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
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), remoteSyncDao) { 1_000L },
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
        assertEquals(4_000L, syncStateDao.getUserState("user-1")?.lastSuccessfulSyncAtEpochMillis)
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
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), remoteSyncDao) { 1_000L },
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
            remotePayloadJson = """{"id":"other-id","user_id":"user-1","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-05T00:00:00Z","version":3}""",
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
    fun workspace_initial_sync_is_triggered_when_marker_is_missing_even_if_profile_cursor_exists() = runTest {
        val auth = FakeAuthRepository(USER_1_SESSION)
        val syncStateDao = FakeSyncStateDao()
        // PROFILE cursor var
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "user-1",
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

        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("user-1"))

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
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "user-1",
            ),
        )
        syncStateDao.upsertCursor(
            com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.bootstrapCompleteEntity(1000L),
        )

        var workspaceInitialCalled = false
        var workspaceIncrementalCalled = false

        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun applyWorkspaceSnapshot(
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
        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource(profile = sampleProfileDto("user-1")) {
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
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), remoteSyncDao) { 1_000L },
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
            SyncCursorEntity("PROFILE", 1000L, "user-1"),
        )
        syncStateDao.upsertCursor(
            com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.bootstrapCompleteEntity(1000L),
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
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "user-1",
            ),
        )
        // Geçersiz marker: entityId "COMPLETED" değil ("INVALID_ID")
        syncStateDao.upsertCursor(
            SyncCursorEntity(
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
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                workspaceInitialCalled = true
            }
        }

        val outboxQueue = object : OutboxQueue by FakeOutboxQueue() {
            override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> {
                outboxCalled = true
                return emptyList()
            }
        }

        val remote = object : CoreRemoteDataSource by FakeCoreRemoteDataSource(profile = sampleProfileDto("user-1")) {
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
                entityTypeCode = "PROFILE",
                updatedAtEpochMillis = 1000L,
                entityId = "user-1",
            ),
        )
        // Geçersiz marker: updatedAtEpochMillis = 0L
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                entityTypeCode = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE,
                updatedAtEpochMillis = 0L,
                entityId = com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID,
            ),
        )

        var workspaceInitialCalled = false
        val remoteSyncDao = object : RemoteSyncDao by FakeRemoteSyncDao() {
            override suspend fun applyWorkspaceSnapshot(
                workspaces: List<WorkspaceEntity>,
                members: List<WorkspaceMemberEntity>,
                cursors: List<SyncCursorEntity>,
            ) {
                workspaceInitialCalled = true
            }
        }
        val remote = FakeCoreRemoteDataSource(profile = sampleProfileDto("user-1"))

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

    private companion object {
        val USER_1_SESSION = AuthSession(EntityId("user-1"), "user-1@example.com", Instant.fromEpochMilliseconds(100_000L))
        val USER_2_SESSION = AuthSession(EntityId("user-2"), "user-2@example.com", Instant.fromEpochMilliseconds(100_000L))
        val USER_3_SESSION = AuthSession(EntityId("user-3"), "user-3@example.com", Instant.fromEpochMilliseconds(100_000L))

        fun sampleProfileDto(userId: String) = ProfileDto(
            id = userId,
            email = "$userId@example.com",
            fullName = "Test User",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            version = 1L,
        )

        fun sampleOperationEntity(id: String) = SyncOperationEntity(
            operationId = id,
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

        fun sampleRecurringConflict(id: String) = SyncConflictEntity(
            entityTypeCode = "RECURRING_TRANSACTION",
            entityId = id,
            operationId = "op-$id",
            localVersion = 1L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$id","user_id":"user-1","amount_minor":40000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","version":1}""",
            remotePayloadJson = """{"id":"$id","user_id":"user-1","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","is_active":true,"created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-05T00:00:00Z","version":3}""",
            detectedAtEpochMillis = 1_000L,
        )

        fun sampleRecurringEntity(id: String, deletedAt: Long? = null) = RecurringTransactionEntity(
            id = id,
            ownerId = "user-1",
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

        override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]
        override suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity> =
            cursors.filterKeys { it.startsWith("WORKSPACE_MEMBER:") }.values.toList()
        override suspend fun getConflict(entityId: String): SyncConflictEntity? {
            getConflictError?.let { throw it }
            return conflicts[entityId]
        }
        override suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity? {
            getConflictError?.let { throw it }
            return conflicts[entityId]
        }
        override suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity> = emptyList()
        override suspend fun getAllConflicts(): List<SyncConflictEntity> = emptyList()
        override suspend fun upsertCursor(cursor: SyncCursorEntity) { cursors[cursor.entityTypeCode] = cursor }
        override fun observeConflicts(): Flow<List<SyncConflictEntity>> = flowOf(emptyList())
        override fun observeConflictCount(): Flow<Int> = flowOf(conflictCount)
        override suspend fun getConflictCount(): Int = conflictCount
        override suspend fun upsertConflict(conflict: SyncConflictEntity) { conflictCount++ }
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int { conflictCount = (conflictCount - 1).coerceAtLeast(0); return 1 }

        override fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?> =
            userStates.getOrPut(userId) { MutableStateFlow(null) }

        override suspend fun getUserState(userId: String): SyncUserStateEntity? =
            userStates[userId]?.value?.let { SyncUserStateEntity(userId, it, it) }

        override suspend fun upsertUserState(state: SyncUserStateEntity) {
            upsertUserStateError?.let { throw it }
            userStates.getOrPut(state.userId) { MutableStateFlow(null) }.value = state.lastSuccessfulSyncAtEpochMillis
        }
    }

    private class FakeRemoteSyncDao : RemoteSyncDao {
        var localRecurringRow: RecurringTransactionEntity? = null
        var resolvedRecurringRemote: RecurringTransactionEntity? = null
        var resolveKeepLocalCalledWith: Triple<SyncConflictEntity, String, Long>? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = localRecurringRow
        override suspend fun getSubscriptionRow(id: String): com.feniqo.mobile.data.local.entity.SubscriptionEntity? = null
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0
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
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
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
        override suspend fun getWorkspaceRow(id: String): com.feniqo.mobile.data.local.entity.WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity> = emptyList()
        override suspend fun upsertWorkspaceRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity>) = Unit
        override suspend fun upsertGoalRows(entities: List<com.feniqo.mobile.data.local.entity.GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<com.feniqo.mobile.data.local.entity.GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<com.feniqo.mobile.data.local.entity.DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity>) = Unit
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun resolveRecurringTransactionKeepRemote(entity: RecurringTransactionEntity) {
            resolvedRecurringRemote = entity
        }
        override suspend fun resolveKeepLocal(
            conflict: SyncConflictEntity,
            operationTypeCode: String,
            nowEpochMillis: Long,
        ) {
            resolveKeepLocalCalledWith = Triple(conflict, operationTypeCode, nowEpochMillis)
        }

        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun applyWorkspaceSnapshot(
            workspaces: List<WorkspaceEntity>,
            members: List<WorkspaceMemberEntity>,
            cursors: List<SyncCursorEntity>,
        ) {
            cursors.forEach { syncStateDao?.upsertCursor(it) }
        }
    }



    private class FakeCoreRemoteDataSource(val profile: ProfileDto? = null) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? = profile
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.RecurringTransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.SubscriptionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalContributionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtPaymentDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery): RemotePage<WorkspaceDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchWorkspaces(request: RemotePageRequest): RemotePage<WorkspaceDto> = RemotePage(emptyList(), request, 0)
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
        override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> = readyOps
        override suspend fun claimOperation(operationId: String): SyncOperationEntity? =
            readyOps.find { it.operationId == operationId }?.copy(statusCode = "IN_FLIGHT", attemptCount = 1)
        override suspend fun markSucceeded(operationId: String): Boolean = true
        override suspend fun ackV2Execution(operationId: String, result: com.feniqo.mobile.data.sync.OutboxExecutionResult): Boolean = true
        override suspend fun recordV2Conflict(conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean = true
        override suspend fun markConflict(operationId: String, errorMessage: String): Boolean = true
        override suspend fun recordFailure(operationId: String, errorMessage: String): Boolean = true
    }

    private class FakeOutboxExecutor : OutboxOperationExecutor {
        override suspend fun execute(operation: SyncOperationEntity): com.feniqo.mobile.data.sync.OutboxExecutionResult =
            com.feniqo.mobile.data.sync.OutboxExecutionResult.V1Completed
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
        override suspend fun upsertRecurringOccurrenceRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity) = Unit
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun advanceRecurringLastGeneratedDate(recurringTransactionId: String, expectedPreviousLastGeneratedDate: String?, newDueDate: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
        override suspend fun deleteProfileRow(id: String): Int = 0
        override suspend fun deleteCategoryRow(id: String): Int = 0
        override suspend fun deleteBudgetRow(id: String): Int = 0
        override suspend fun deleteTransactionRow(id: String): Int = 0
        override suspend fun deleteOutboxRow(operationId: String): Int = 0
        override suspend fun getOutboxById(operationId: String): SyncOperationEntity? = null
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
        override suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
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
        override suspend fun getActiveGoalAggregateTailCandidates(goalId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveDebtAggregateTailCandidates(debtId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun countPendingGoalAggregateOperations(goalId: String, operationId: String): Int = 0
        override suspend fun countPendingDebtAggregateOperations(debtId: String, operationId: String): Int = 0
        override suspend fun setGoalSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setGoalContributionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtPaymentSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun upsertConflictRow(entity: com.feniqo.mobile.data.local.entity.SyncConflictEntity) = Unit
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    }


    private class FakeSyncOperationDao(
        initialPending: Int = 0,
        initialFailed: Int = 0,
    ) : SyncOperationDao {
        private val pendingFlow = MutableStateFlow(initialPending)
        private val failedFlow = MutableStateFlow(initialFailed)

        override fun observePendingCount(): Flow<Int> = pendingFlow
        override fun observeFailedCount(): Flow<Int> = failedFlow
        override suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int): List<SyncOperationEntity> = emptyList()
        override suspend fun getById(operationId: String): SyncOperationEntity? = null
        override suspend fun insert(operation: SyncOperationEntity) = Unit
        override suspend fun claimOperation(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun markFailed(operationId: String, lastError: String, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
        override suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
        override suspend fun recoverStaleInFlight(staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
        override suspend fun retryAllFailed(nowEpochMillis: Long): Int = 0
        override suspend fun deleteCompleted(operationId: String): Int = 1
        override suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 1
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 1
    }
}
