package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.OutboxErrorClassification
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.OutboxOperationExecutor
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.RoomOutboxQueue
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.sync.SyncSessionInvalidatedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class UserScopedSessionRaceOutboxTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var localMutationDao: LocalMutationDao
    private lateinit var offlineWriteQueue: OfflineWriteQueue
    private lateinit var roomOutboxQueue: RoomOutboxQueue

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .allowMainThreadQueries()
            .build()

        syncOperationDao = database.syncOperationDao()
        localMutationDao = database.localMutationDao()
        offlineWriteQueue = OfflineWriteQueue(localMutationDao, syncOperationDao)
        roomOutboxQueue = RoomOutboxQueue(offlineWriteQueue)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun remote_started_then_session_changed_preserves_operation_as_ambiguous_with_same_identity() = runTest {
        val opId = "op-a-1"
        val payload = """{"amount_minor": 10000, "note": "user A op"}"""
        val predOpId = "pred-op-0"
        val opA = SyncOperationEntity(
            operationId = opId,
            syncScopeKey = SCOPE_A,
            entityTypeCode = "TRANSACTION",
            entityId = "tx-a-1",
            operationTypeCode = "CREATE",
            baseVersion = 1L,
            predecessorOperationId = predOpId,
            protocolVersion = 2,
            payloadJson = payload,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        syncOperationDao.insert(opA)

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val executor = OutboxOperationExecutor {
            entered.complete(Unit)
            release.await()
            OutboxExecutionResult.V1Completed
        }

        val processor = OutboxProcessor(roomOutboxQueue, executor)

        var sessionValid = true
        val job = launch {
            processor.processReadyOperations(
                syncScopeKey = SCOPE_A,
                assertSessionCurrent = {
                    if (!sessionValid) throw SyncSessionInvalidatedException()
                },
            )
        }

        entered.await()
        sessionValid = false
        job.cancel(SyncSessionInvalidatedException())
        job.join()

        val finalOp = syncOperationDao.getById(SCOPE_A, opId)
        assertNotNull(finalOp)
        assertEquals("FAILED", finalOp.statusCode)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, finalOp.errorClassification)
        assertEquals(opId, finalOp.operationId)
        assertEquals(SCOPE_A, finalOp.syncScopeKey)
        assertEquals(payload, finalOp.payloadJson)
        assertEquals(predOpId, finalOp.predecessorOperationId)
        assertEquals(1L, finalOp.baseVersion)
        assertEquals("TRANSACTION", finalOp.entityTypeCode)
        assertEquals("CREATE", finalOp.operationTypeCode)
        assertEquals(2, finalOp.protocolVersion)
        assertEquals(1, finalOp.attemptCount)
    }

    @Test
    fun user_a_session_invalidation_does_not_modify_user_b_operation() = runTest {
        val opA = SyncOperationEntity(
            operationId = "op-a-race",
            syncScopeKey = SCOPE_A,
            entityTypeCode = "TRANSACTION",
            entityId = "tx-a-100",
            operationTypeCode = "CREATE",
            baseVersion = null,
            predecessorOperationId = null,
            protocolVersion = 2,
            payloadJson = """{"note": "A"}""",
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val opB = SyncOperationEntity(
            operationId = "op-b-untouched",
            syncScopeKey = SCOPE_B,
            entityTypeCode = "TRANSACTION",
            entityId = "tx-b-200",
            operationTypeCode = "UPDATE",
            baseVersion = 3L,
            predecessorOperationId = "pred-b-50",
            protocolVersion = 2,
            payloadJson = """{"note": "B pristine"}""",
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 2000L,
            updatedAtEpochMillis = 2000L,
        )
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val executor = OutboxOperationExecutor {
            entered.complete(Unit)
            release.await()
            OutboxExecutionResult.V1Completed
        }

        val processor = OutboxProcessor(roomOutboxQueue, executor)

        var sessionValid = true
        val job = launch {
            processor.processReadyOperations(
                syncScopeKey = SCOPE_A,
                assertSessionCurrent = {
                    if (!sessionValid) throw SyncSessionInvalidatedException()
                },
            )
        }

        entered.await()
        sessionValid = false
        job.cancel(SyncSessionInvalidatedException())
        job.join()

        // User A operasyonu FAILED + AMBIGUOUS_RESULT oldu
        val updatedA = syncOperationDao.getById(SCOPE_A, "op-a-race")
        assertNotNull(updatedA)
        assertEquals("FAILED", updatedA.statusCode)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, updatedA.errorClassification)

        // User B operasyonu KESİNLİKLE değişmedi
        val pristineB = syncOperationDao.getById(SCOPE_B, "op-b-untouched")
        assertNotNull(pristineB)
        assertEquals("PENDING", pristineB.statusCode)
        assertEquals(null, pristineB.errorClassification)
        assertEquals(0, pristineB.attemptCount)
        assertEquals("op-b-untouched", pristineB.operationId)
        assertEquals(SCOPE_B, pristineB.syncScopeKey)
        assertEquals("""{"note": "B pristine"}""", pristineB.payloadJson)
        assertEquals("pred-b-50", pristineB.predecessorOperationId)
        assertEquals(3L, pristineB.baseVersion)
        assertEquals(null, pristineB.lastError)
    }
}
