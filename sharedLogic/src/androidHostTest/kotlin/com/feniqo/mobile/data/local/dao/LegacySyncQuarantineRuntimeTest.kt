package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.repository.FakeWorkspaceCoreRemoteDataSource
import com.feniqo.mobile.data.repository.OfflineFirstSyncRepository
import com.feniqo.mobile.data.sync.ConflictRecoveryService
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.OutboxOperationExecutor
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.RoomOutboxQueue
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync
import com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LegacySyncQuarantineRuntimeTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var syncStateDao: SyncStateDao
    private lateinit var localMutationDao: LocalMutationDao
    private lateinit var remoteSyncDao: RemoteSyncDao
    private lateinit var offlineWriteQueue: OfflineWriteQueue
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var fakeRemote: FakeWorkspaceCoreRemoteDataSource
    private lateinit var fakeExecutor: FakeOutboxExecutor
    private lateinit var outboxProcessor: OutboxProcessor
    private lateinit var syncRepository: OfflineFirstSyncRepository

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"
        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
        val SCOPE_QUARANTINE = SyncScopeKey.UNRESOLVED_RAW

        val SESSION_A = AuthSession(
            userId = EntityId(USER_A_ID),
            email = "user.a@feniqo.com",
            expiresAt = Instant.fromEpochMilliseconds(100_000L),
        )
        val SESSION_B = AuthSession(
            userId = EntityId(USER_B_ID),
            email = "user.b@feniqo.com",
            expiresAt = Instant.fromEpochMilliseconds(100_000L),
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .allowMainThreadQueries()
            .build()

        syncOperationDao = database.syncOperationDao()
        syncStateDao = database.syncStateDao()
        localMutationDao = database.localMutationDao()
        remoteSyncDao = database.remoteSyncDao()
        offlineWriteQueue = OfflineWriteQueue(localMutationDao, syncOperationDao)
        authRepository = FakeAuthRepository(SESSION_A)
        fakeRemote = FakeWorkspaceCoreRemoteDataSource()
        fakeExecutor = FakeOutboxExecutor()
        outboxProcessor = OutboxProcessor(RoomOutboxQueue(offlineWriteQueue), fakeExecutor)

        syncRepository = OfflineFirstSyncRepository(
            authRepository = authRepository,
            initialRemoteSync = InitialRemoteSync(fakeRemote, remoteSyncDao) { 1_000L },
            workspaceInitialRemoteSync = WorkspaceInitialRemoteSync(fakeRemote, remoteSyncDao) { 1_000L },
            outboxProcessor = outboxProcessor,
            incrementalRemoteSync = IncrementalRemoteSync(fakeRemote, remoteSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = WorkspaceIncrementalRemoteSync(fakeRemote, remoteSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = offlineWriteQueue,
            syncStateDao = syncStateDao,
            remoteSyncDao = remoteSyncDao,
            conflictRecoveryService = ConflictRecoveryService(syncStateDao, syncOperationDao, localMutationDao) { 1_000L },
            nowEpochMillisProvider = { 1_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleOperation(
        operationId: String,
        syncScopeKey: String,
        entityTypeCode: String = "TRANSACTION",
        entityId: String = "tx-1",
        operationTypeCode: String = "CREATE",
        statusCode: String = "PENDING",
        attemptCount: Int = 0,
        nextAttemptAt: Long = 1000L,
        now: Long = 1000L,
    ) = SyncOperationEntity(
        operationId = operationId,
        syncScopeKey = syncScopeKey,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationTypeCode = operationTypeCode,
        baseVersion = if (operationTypeCode == "CREATE") null else 1L,
        payloadJson = "{\"id\":\"$entityId\"}",
        predecessorOperationId = null,
        isBlocked = false,
        protocolVersion = 2,
        statusCode = statusCode,
        attemptCount = attemptCount,
        lastError = if (statusCode == "FAILED") "Some error" else null,
        nextAttemptAtEpochMillis = nextAttemptAt,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        errorClassification = null,
    )

    private fun sampleConflict(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
        operationId: String,
        detectedAtEpochMillis: Long = 1000L,
    ): SyncConflictEntity = SyncConflictEntity(
        syncScopeKey = syncScopeKey,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationId = operationId,
        localVersion = 1L,
        remoteVersion = 2L,
        localPayloadJson = """{"id":"$entityId","scope":"$syncScopeKey"}""",
        remotePayloadJson = """{"id":"$entityId","scope":"$syncScopeKey","remote":true}""",
        detectedAtEpochMillis = detectedAtEpochMillis,
    )

    private fun fileDatabase(context: Context, file: File): FeniqoDatabase {
        return Room.databaseBuilder<FeniqoDatabase>(
            context = context,
            name = file.absolutePath,
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    // 1. unresolved_operation_sets_generic_quarantine_flag_without_affecting_user_counts
    @Test
    fun unresolved_operation_sets_generic_quarantine_flag_without_affecting_user_counts() = runTest {
        syncOperationDao.insert(
            sampleOperation("quarantine-op-1", SCOPE_QUARANTINE, statusCode = "PENDING")
        )
        syncOperationDao.insert(
            sampleOperation("quarantine-op-2", SCOPE_QUARANTINE, statusCode = "FAILED")
        )
        syncOperationDao.insert(
            sampleOperation("userA-op-1", SCOPE_A, statusCode = "PENDING")
        )
        syncOperationDao.insert(
            sampleOperation("userA-op-2", SCOPE_A, statusCode = "FAILED")
        )

        val overview = syncRepository.observeOverview().first()

        assertTrue(overview.hasLegacyQuarantinedData, "Quarantined operation must set generic quarantine flag to true")
        assertEquals(2, overview.pendingOperationCount, "Pending count must only include User A's operations (pending + failed)")
        assertEquals(1, overview.failedOperationCount, "Failed count must only include User A's failed operation")
        assertEquals(0, overview.conflictCount)
    }

    // 2. unresolved_cursor_sets_generic_quarantine_flag_but_is_not_used_as_user_cursor
    @Test
    fun unresolved_cursor_sets_generic_quarantine_flag_but_is_not_used_as_user_cursor() = runTest {
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = SCOPE_QUARANTINE,
                entityTypeCode = "TRANSACTION",
                updatedAtEpochMillis = 9999L,
                entityId = "legacy-tx-1",
            )
        )
        syncStateDao.upsertCursor(
            SyncCursorEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = "TRANSACTION",
                updatedAtEpochMillis = 1000L,
                entityId = "userA-tx-1",
            )
        )

        val overview = syncRepository.observeOverview().first()
        assertTrue(overview.hasLegacyQuarantinedData, "Quarantined cursor must set generic quarantine flag to true")

        val cursorA = syncStateDao.getCursor(SCOPE_A, "TRANSACTION")
        assertNotNull(cursorA)
        assertEquals(1000L, cursorA.updatedAtEpochMillis)
        assertEquals("userA-tx-1", cursorA.entityId)

        val cursorB = syncStateDao.getCursor(SCOPE_B, "TRANSACTION")
        assertNull(cursorB, "Quarantined cursor must never be used as User B's cursor")
    }

    // 3. unresolved_conflict_sets_generic_quarantine_flag_but_is_hidden_from_user_conflicts
    @Test
    fun unresolved_conflict_sets_generic_quarantine_flag_but_is_hidden_from_user_conflicts() = runTest {
        syncStateDao.upsertConflict(
            sampleConflict(SCOPE_QUARANTINE, "TRANSACTION", "tx-quarantine", "op-quarantine")
        )
        syncStateDao.upsertConflict(
            sampleConflict(SCOPE_A, "TRANSACTION", "tx-userA", "op-userA")
        )

        val overview = syncRepository.observeOverview().first()
        assertTrue(overview.hasLegacyQuarantinedData, "Quarantined conflict must set generic quarantine flag to true")
        assertEquals(1, overview.conflictCount, "User conflicts count must exclude quarantined conflicts")

        val userConflicts = syncStateDao.observeConflicts(SCOPE_A).first()
        assertEquals(1, userConflicts.size)
        assertEquals("tx-userA", userConflicts[0].entityId)

        val repoConflicts = syncRepository.observeConflicts().first()
        assertEquals(1, repoConflicts.size)
        assertEquals("tx-userA", repoConflicts[0].entityId.value)

        assertNull(
            syncStateDao.getConflict(SCOPE_A, "tx-quarantine"),
            "Quarantined conflict must not be accessible via User A scope query",
        )
        assertNull(
            syncStateDao.getConflict(SCOPE_A, "TRANSACTION", "tx-quarantine"),
            "Quarantined conflict must not be accessible via User A scoped entity query",
        )
    }

    // 4. signed_out_overview_does_not_expose_legacy_quarantine_presence
    @Test
    fun signed_out_overview_does_not_expose_legacy_quarantine_presence() = runTest {
        syncOperationDao.insert(sampleOperation("quarantine-op-1", SCOPE_QUARANTINE))
        syncStateDao.upsertCursor(
            SyncCursorEntity(SCOPE_QUARANTINE, "TRANSACTION", 9999L, "legacy-tx-1")
        )
        syncStateDao.upsertConflict(
            sampleConflict(SCOPE_QUARANTINE, "TRANSACTION", "tx-quarantine", "op-quarantine")
        )

        authRepository.setSession(null)

        val overview = syncRepository.observeOverview().first()
        assertFalse(
            overview.hasLegacyQuarantinedData,
            "Unauthenticated overview must stay fail-closed and never expose quarantine data presence",
        )
        assertEquals(0, overview.pendingOperationCount)
        assertEquals(0, overview.failedOperationCount)
        assertEquals(0, overview.conflictCount)
    }

    // 5. user_a_and_user_b_counts_remain_isolated_when_quarantine_rows_exist
    @Test
    fun user_a_and_user_b_counts_remain_isolated_when_quarantine_rows_exist() = runTest {
        syncOperationDao.insert(sampleOperation("q-op-1", SCOPE_QUARANTINE, statusCode = "PENDING"))
        syncOperationDao.insert(sampleOperation("q-op-2", SCOPE_QUARANTINE, statusCode = "FAILED"))
        syncStateDao.upsertCursor(SyncCursorEntity(SCOPE_QUARANTINE, "TRANSACTION", 9999L, "q-tx"))
        syncStateDao.upsertConflict(sampleConflict(SCOPE_QUARANTINE, "TRANSACTION", "q-tx", "q-op-1"))

        syncOperationDao.insert(sampleOperation("a-op-1", SCOPE_A, statusCode = "PENDING"))
        syncOperationDao.insert(sampleOperation("a-op-2", SCOPE_A, statusCode = "PENDING"))
        syncOperationDao.insert(sampleOperation("a-op-3", SCOPE_A, statusCode = "FAILED"))
        syncStateDao.upsertConflict(sampleConflict(SCOPE_A, "TRANSACTION", "a-tx-1", "a-op-1"))

        // User A active
        authRepository.setSession(SESSION_A)
        val overviewA = syncRepository.observeOverview().first()
        assertTrue(overviewA.hasLegacyQuarantinedData)
        assertEquals(3, overviewA.pendingOperationCount)
        assertEquals(1, overviewA.failedOperationCount)
        assertEquals(1, overviewA.conflictCount)

        // Switch to User B active (no operations or conflicts)
        authRepository.setSession(SESSION_B)
        val overviewB = syncRepository.observeOverview().first()
        assertTrue(overviewB.hasLegacyQuarantinedData)
        assertEquals(0, overviewB.pendingOperationCount)
        assertEquals(0, overviewB.failedOperationCount)
        assertEquals(0, overviewB.conflictCount)
    }

    // 6. user_scoped_ready_retry_claim_and_stale_recovery_do_not_modify_unresolved_operation
    @Test
    fun user_scoped_ready_retry_claim_and_stale_recovery_do_not_modify_unresolved_operation() = runTest {
        val opPending = sampleOperation(
            operationId = "q-pending",
            syncScopeKey = SCOPE_QUARANTINE,
            statusCode = "PENDING",
            attemptCount = 0,
            nextAttemptAt = 500L,
            now = 500L,
        )
        val opFailed = sampleOperation(
            operationId = "q-failed",
            syncScopeKey = SCOPE_QUARANTINE,
            statusCode = "FAILED",
            attemptCount = 3,
            nextAttemptAt = 500L,
            now = 500L,
        )
        val opInFlight = sampleOperation(
            operationId = "q-inflight",
            syncScopeKey = SCOPE_QUARANTINE,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            nextAttemptAt = 500L,
            now = 500L,
        )

        syncOperationDao.insert(opPending)
        syncOperationDao.insert(opFailed)
        syncOperationDao.insert(opInFlight)

        val readyOps = syncOperationDao.getReadyOperations(SCOPE_A, nowEpochMillis = 2000L, limit = 10)
        assertTrue(readyOps.isEmpty(), "Quarantined operations must not be returned in ready operations")

        val claimed = syncOperationDao.claimOperation(SCOPE_A, "q-pending", nowEpochMillis = 2000L)
        assertEquals(0, claimed, "User-scoped claim must not affect quarantined operation")

        val retried = syncOperationDao.retryAllFailed(SCOPE_A, nowEpochMillis = 2000L)
        assertEquals(0, retried, "User-scoped retryAllFailed must not affect quarantined operation")

        val recovered = syncOperationDao.recoverStaleInFlight(
            syncScopeKey = SCOPE_A,
            staleBeforeEpochMillis = 2000L,
            nowEpochMillis = 2000L,
            lastError = "Stale recovery",
        )
        assertEquals(0, recovered, "User-scoped stale recovery must not affect quarantined operation")

        val opPendingAfter = syncOperationDao.getById(SCOPE_QUARANTINE, "q-pending")
        assertNotNull(opPendingAfter)
        assertEquals("PENDING", opPendingAfter.statusCode)
        assertEquals(0, opPendingAfter.attemptCount)
        assertEquals(500L, opPendingAfter.updatedAtEpochMillis)

        val opFailedAfter = syncOperationDao.getById(SCOPE_QUARANTINE, "q-failed")
        assertNotNull(opFailedAfter)
        assertEquals("FAILED", opFailedAfter.statusCode)
        assertEquals(3, opFailedAfter.attemptCount)
        assertEquals(500L, opFailedAfter.updatedAtEpochMillis)

        val opInFlightAfter = syncOperationDao.getById(SCOPE_QUARANTINE, "q-inflight")
        assertNotNull(opInFlightAfter)
        assertEquals("IN_FLIGHT", opInFlightAfter.statusCode)
        assertEquals(1, opInFlightAfter.attemptCount)
        assertEquals(500L, opInFlightAfter.updatedAtEpochMillis)

        assertNull(
            syncOperationDao.getById(SCOPE_A, "q-pending"),
            "Quarantine operation must not be found via User A syncScopeKey query",
        )
    }

    // 7. unresolved_operation_is_never_given_to_outbox_executor
    @Test
    fun unresolved_operation_is_never_given_to_outbox_executor() = runTest {
        val quarantineOp = sampleOperation("q-op-1", SCOPE_QUARANTINE, statusCode = "PENDING", nextAttemptAt = 500L)
        syncOperationDao.insert(quarantineOp)

        outboxProcessor.processReadyOperations(SCOPE_A, assertSessionCurrent = {})

        assertEquals(0, fakeExecutor.executeCalls, "Outbox executor must never be invoked for quarantine operation")
        assertTrue(fakeExecutor.executedOperations.isEmpty())

        val opAfter = syncOperationDao.getById(SCOPE_QUARANTINE, "q-op-1")
        assertNotNull(opAfter)
        assertEquals("PENDING", opAfter.statusCode)
        assertEquals(0, opAfter.attemptCount)
    }

    // 8. quarantine_rows_survive_database_close_and_reopen
    @Test
    fun quarantine_rows_survive_database_close_and_reopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "quarantine_reopen_test_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        var fileDb = fileDatabase(context, dbFile)
        try {
            fileDb.syncOperationDao().insert(sampleOperation("q-op-persist", SCOPE_QUARANTINE))
            fileDb.syncStateDao().upsertCursor(
                SyncCursorEntity(SCOPE_QUARANTINE, "TRANSACTION", 9999L, "q-tx-persist")
            )
            fileDb.syncStateDao().upsertConflict(
                sampleConflict(SCOPE_QUARANTINE, "TRANSACTION", "q-tx-persist", "q-op-persist")
            )

            assertEquals(1, fileDb.syncOperationDao().getLegacyQuarantineOperationCount())
            assertEquals(1, fileDb.syncStateDao().getLegacyQuarantineCursorCount())
            assertEquals(1, fileDb.syncStateDao().getLegacyQuarantineConflictCount())

            fileDb.close()

            fileDb = fileDatabase(context, dbFile)

            assertEquals(1, fileDb.syncOperationDao().getLegacyQuarantineOperationCount())
            assertEquals(1, fileDb.syncStateDao().getLegacyQuarantineCursorCount())
            assertEquals(1, fileDb.syncStateDao().getLegacyQuarantineConflictCount())
        } finally {
            fileDb.close()
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    }

    // 9. no_quarantine_rows_produces_false_status
    @Test
    fun no_quarantine_rows_produces_false_status() = runTest {
        syncOperationDao.insert(sampleOperation("a-op-1", SCOPE_A, statusCode = "PENDING"))
        syncStateDao.upsertCursor(
            SyncCursorEntity(SCOPE_A, "TRANSACTION", 1000L, "a-tx-1")
        )
        syncStateDao.upsertConflict(
            sampleConflict(SCOPE_A, "TRANSACTION", "a-tx-1", "a-op-1")
        )

        val overview = syncRepository.observeOverview().first()

        assertFalse(
            overview.hasLegacyQuarantinedData,
            "Overview must produce false when no quarantine rows exist",
        )
        assertEquals(1, overview.pendingOperationCount)
        assertEquals(1, overview.conflictCount)
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

    private class FakeOutboxExecutor : OutboxOperationExecutor {
        var executeCalls = 0
        val executedOperations = mutableListOf<SyncOperationEntity>()

        override suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult {
            executeCalls++
            executedOperations.add(operation)
            return OutboxExecutionResult.V1Completed
        }
    }
}
