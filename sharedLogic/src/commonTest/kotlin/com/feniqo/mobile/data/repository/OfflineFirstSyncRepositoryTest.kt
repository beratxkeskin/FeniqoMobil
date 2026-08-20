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

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = initialRemoteSync,
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = incrementalRemoteSync,
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
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

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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
            override suspend fun execute(operation: SyncOperationEntity) {
                throw io.ktor.utils.io.errors.IOException("Network down")
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, failingExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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
            override suspend fun execute(operation: SyncOperationEntity) {
                throw com.feniqo.mobile.data.sync.OutboxConflictException("Local version is outdated")
            }
        }
        val outboxProcessor = OutboxProcessor(outboxQueue, conflictExecutor)

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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
        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(sampleProfileDto("user-1")), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = OfflineWriteQueue(FakeLocalMutationDao(), FakeSyncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
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

        val repository = OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = InitialRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao()) { 1_000L },
            outboxProcessor = OutboxProcessor(FakeOutboxQueue(), FakeOutboxExecutor()),
            incrementalRemoteSync = IncrementalRemoteSync(FakeCoreRemoteDataSource(), FakeRemoteSyncDao(), syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = FakeRemoteSyncDao(),
            nowEpochMillisProvider = { 1_000L },
        )

        val overview = repository.observeOverview().first()
        assertEquals(4, overview.pendingOperationCount)
        assertEquals(2, overview.failedOperationCount)
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
            entityTypeCode = "PROFILE",
            entityId = "user-1",
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            statusCode = "PENDING",
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
        private val userStates = mutableMapOf<String, MutableStateFlow<Long?>>()
        private val cursors = mutableMapOf<String, SyncCursorEntity>()

        override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]
        override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
        override suspend fun upsertCursor(cursor: SyncCursorEntity) { cursors[cursor.entityTypeCode] = cursor }
        override fun observeConflicts(): Flow<List<SyncConflictEntity>> = flowOf(emptyList())
        override fun observeConflictCount(): Flow<Int> = flowOf(0)
        override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0

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
        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) = Unit
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) = Unit
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 0
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }

    private class FakeCoreRemoteDataSource(val profile: ProfileDto? = null) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? = profile
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchWorkspaces(request: RemotePageRequest): RemotePage<WorkspaceDto> = RemotePage(emptyList(), request, 0)
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
        override suspend fun markInFlight(operationId: String): Boolean = true
        override suspend fun markSucceeded(operationId: String): Boolean = true
        override suspend fun markConflict(operationId: String, error: String): Boolean = true
        override suspend fun recordFailure(operationId: String, error: String): Boolean = true
    }

    private class FakeOutboxExecutor : OutboxOperationExecutor {
        override suspend fun execute(operation: SyncOperationEntity) = Unit
    }

    private class FakeLocalMutationDao : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRow(entity: CategoryEntity) = Unit
        override suspend fun upsertBudgetRow(entity: BudgetEntity) = Unit
        override suspend fun upsertTransactionRow(entity: TransactionEntity) = Unit
        override suspend fun upsertTagRows(entities: List<TagEntity>) = Unit
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) = Unit
        override suspend fun deleteTransactionTagRows(transactionId: String) = Unit
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) = Unit
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
        override suspend fun markInFlight(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun markFailed(operationId: String, attemptCount: Int, lastError: String, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
        override suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
        override suspend fun recoverStaleInFlight(staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
        override suspend fun retryAllFailed(nowEpochMillis: Long): Int = 0
        override suspend fun deleteCompleted(operationId: String): Int = 1
        override suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int = 0
    }
}
