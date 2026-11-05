package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.remote.dto.toDto
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Faz 8.6-D2-B3-B: User Merchant Verification Manual KEEP_LOCAL / KEEP_REMOTE Conflict Resolution testleri.
 * 20 zorunlu senaryoyu doğrudan Room in-memory veritabanı ve transaction sınırında doğrular.
 */
@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationConflictResolutionDaoTest {

    private lateinit var db: FeniqoDatabase
    private lateinit var remoteSyncDao: RemoteSyncDao
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var syncStateDao: SyncStateDao

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
        const val ENTITY_TYPE = "USER_MERCHANT_VERIFICATION"

        fun sampleVerification(
            userId: String = USER_A_ID,
            alias: String = "MIGROS",
            decisionCode: String = "ASSIGN",
            merchantId: String? = "migros",
            version: Long = 1L,
            baseVersion: Long? = 1L,
            deletedAtEpochMillis: Long? = null,
            createdAtEpochMillis: Long = 1000L,
            updatedAtEpochMillis: Long = 1000L,
            localUpdatedAtEpochMillis: Long = 1000L,
            id: String = UuidHelper.deterministicMerchantVerificationUuid(userId, alias),
            scopeKey: String = "user:$userId",
            syncStatus: String = "CONFLICT",
            lastSyncError: String? = "Çakışma tespit edildi.",
        ): UserMerchantVerificationEntity = UserMerchantVerificationEntity(
            id = id,
            ownerId = userId,
            workspaceId = null,
            normalizedAlias = alias,
            decisionCode = decisionCode,
            merchantId = merchantId,
            scopeCode = "PERSONAL",
            scopeKey = scopeKey,
            createdAtEpochMillis = createdAtEpochMillis,
            sync = SyncMetadata(
                syncStatus = syncStatus,
                updatedAtEpochMillis = updatedAtEpochMillis,
                localUpdatedAtEpochMillis = localUpdatedAtEpochMillis,
                deletedAtEpochMillis = deletedAtEpochMillis,
                version = version,
                baseVersion = baseVersion,
                lastSyncError = lastSyncError,
            ),
        )

        fun sampleSnapshotJson(entity: UserMerchantVerificationEntity): String {
            val dto = entity.toDomain().toDto(
                version = entity.sync.version,
                deletedAt = entity.sync.deletedAtEpochMillis?.let { Instant.fromEpochMilliseconds(it) },
            )
            return UserMerchantVerificationPayloadCodec.encodeSnapshot(dto)
        }

        fun sampleConflict(
            syncScopeKey: String = SCOPE_A,
            entityId: String,
            operationId: String,
            localVersion: Long = 1L,
            remoteVersion: Long = 2L,
            detectedAtEpochMillis: Long = 2000L,
            localPayloadJson: String? = null,
            remotePayloadJson: String? = null,
            localEntity: UserMerchantVerificationEntity? = null,
            remoteEntity: UserMerchantVerificationEntity? = null,
        ): SyncConflictEntity {
            val effLocal = localEntity ?: sampleVerification(
                id = entityId,
                version = localVersion,
                baseVersion = localVersion,
            )
            val effRemote = remoteEntity ?: sampleVerification(
                id = entityId,
                version = remoteVersion,
                baseVersion = localVersion,
                updatedAtEpochMillis = detectedAtEpochMillis,
            )
            return SyncConflictEntity(
                syncScopeKey = syncScopeKey,
                entityTypeCode = ENTITY_TYPE,
                entityId = entityId,
                operationId = operationId,
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localPayloadJson = localPayloadJson ?: sampleSnapshotJson(effLocal),
                remotePayloadJson = remotePayloadJson ?: sampleSnapshotJson(effRemote),
                detectedAtEpochMillis = detectedAtEpochMillis,
            )
        }

        fun sampleOperation(
            operationId: String,
            syncScopeKey: String = SCOPE_A,
            entityId: String,
            operationTypeCode: String = "UPDATE",
            statusCode: String = "CONFLICT",
            payloadJson: String,
            baseVersion: Long? = 1L,
            isBlocked: Boolean = false,
            predecessorOperationId: String? = null,
            protocolVersion: Int = 2,
            attemptCount: Int = 0,
            updatedAtEpochMillis: Long = 1000L,
            createdAtEpochMillis: Long = 1000L,
        ): SyncOperationEntity = SyncOperationEntity(
            operationId = operationId,
            syncScopeKey = syncScopeKey,
            entityTypeCode = ENTITY_TYPE,
            entityId = entityId,
            operationTypeCode = operationTypeCode,
            statusCode = statusCode,
            payloadJson = payloadJson,
            baseVersion = baseVersion,
            attemptCount = attemptCount,
            lastError = null,
            nextAttemptAtEpochMillis = 0L,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            isBlocked = isBlocked,
            predecessorOperationId = predecessorOperationId,
            protocolVersion = protocolVersion,
            errorClassification = null,
        )
    }

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()

        remoteSyncDao = db.remoteSyncDao()
        syncOperationDao = db.syncOperationDao()
        syncStateDao = db.syncStateDao()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------------------------------------
    // KEEP_REMOTE TESTS (1 - 8)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test01_keepRemote_validActiveRemoteSnapshot_appliesRemoteAndCleansOutboxAndConflict() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L, decisionCode = "ASSIGN", merchantId = "migros")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-1",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            baseVersion = 1L,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleVerification(
            version = 2L,
            baseVersion = 2L,
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2000L,
        )
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = op.operationId,
            localVersion = 1L,
            remoteVersion = 2L,
            localEntity = local,
            remoteEntity = remoteEntity,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remoteEntity)

        // Remote entity applied with SYNCED
        val liveEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(liveEntity)
        assertEquals(remoteEntity, liveEntity)
        assertEquals("SYNCED", liveEntity.sync.syncStatus)
        assertEquals("BLOCK", liveEntity.decisionCode)
        assertNull(liveEntity.merchantId)
        assertEquals(2L, liveEntity.sync.version)

        // Outbox and conflict atomically cleaned
        val remainingOps = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id)
        assertTrue(remainingOps.isEmpty())
        assertNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test02_keepRemote_validRemoteTombstone_appliesAsSoftDelete() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-del",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            baseVersion = 1L,
        )
        syncOperationDao.insert(op)

        val remoteTombstone = sampleVerification(
            version = 2L,
            baseVersion = 2L,
            deletedAtEpochMillis = 2500L,
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2500L,
        )
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = op.operationId,
            localVersion = 1L,
            remoteVersion = 2L,
            localEntity = local,
            remoteEntity = remoteTombstone,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remoteTombstone)

        val liveEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(liveEntity)
        assertEquals(2500L, liveEntity.sync.deletedAtEpochMillis)
        assertEquals(2L, liveEntity.sync.version)
        assertEquals("SYNCED", liveEntity.sync.syncStatus)
        assertTrue(remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id).isEmpty())
        assertNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test03_keepRemote_staleExpectedConflict_rollsBack() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-stale-c",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        // Precondition expects remoteVersion = 3L, but live has 2L
        val staleConflictSnapshot = conflict.toSnapshot().copy(remoteVersion = 3L)
        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = staleConflictSnapshot,
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remote)
        }

        // Verify state is completely preserved
        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(1, remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id).size)
        assertNotNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test04_keepRemote_staleExpectedLocalEntity_includingLocalUpdatedAtDifference_rollsBack() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L, localUpdatedAtEpochMillis = 1000L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-toctou",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        // Stale precondition with only localUpdatedAtEpochMillis diff
        val staleLocal = local.copy(
            sync = local.sync.copy(localUpdatedAtEpochMillis = 9999L),
        )
        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = staleLocal,
            expectedOperations = listOf(op.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remote)
        }

        // Assert nothing changed
        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(1, remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id).size)
        assertNotNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test05_keepRemote_crossUserSnapshot_rollsBack() = runTest {
        val local = sampleVerification(userId = USER_A_ID, version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(operationId = "op-xuser", entityId = local.id, payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local))
        syncOperationDao.insert(op)

        // Remote snapshot belongs to USER_B
        val crossUserRemote = sampleVerification(userId = USER_B_ID, version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            syncScopeKey = SCOPE_A,
            entityId = local.id,
            operationId = op.operationId,
            localEntity = local,
            remoteEntity = crossUserRemote,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, crossUserRemote)
        }

        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertNotNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test06_keepRemote_malformedOrNonCanonicalRemoteSnapshot_rollsBack() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(operationId = "op-malformed", entityId = local.id, payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local))
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = op.operationId,
            remotePayloadJson = "{ not json at all }",
            localEntity = local,
            remoteEntity = remote,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remote)
        }

        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
    }

    @Test
    fun test07_keepRemote_missingMultipleOrTerminalOutboxTopology_rollsBack() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        // Op has FAILED status
        val failedOp = sampleOperation(
            operationId = "op-failed",
            entityId = local.id,
            statusCode = "FAILED",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(failedOp)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = failedOp.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(failedOp.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remote)
        }

        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
    }

    @Test
    fun test08_keepRemote_secondEntityOrScopeOutboxOperationsArePreserved() = runTest {
        val localA = sampleVerification(userId = USER_A_ID, alias = "MIGROS", version = 1L, baseVersion = 1L)
        val localA2 = sampleVerification(userId = USER_A_ID, alias = "BIM", version = 1L, baseVersion = 1L)
        val localB = sampleVerification(userId = USER_B_ID, alias = "MIGROS", version = 1L, baseVersion = 1L)

        remoteSyncDao.upsertUserMerchantVerificationRow(localA)
        remoteSyncDao.upsertUserMerchantVerificationRow(localA2)
        remoteSyncDao.upsertUserMerchantVerificationRow(localB)

        val opA = sampleOperation(operationId = "op-a", entityId = localA.id, payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(localA))
        val opA2 = sampleOperation(operationId = "op-a2", entityId = localA2.id, payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(localA2))
        val opB = sampleOperation(operationId = "op-b", syncScopeKey = SCOPE_B, entityId = localB.id, payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(localB))

        syncOperationDao.insert(opA)
        syncOperationDao.insert(opA2)
        syncOperationDao.insert(opB)

        val remoteA = sampleVerification(
            userId = USER_A_ID,
            alias = "MIGROS",
            version = 2L,
            baseVersion = 2L,
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2000L,
        )
        val conflictA = sampleConflict(entityId = localA.id, operationId = opA.operationId, localEntity = localA, remoteEntity = remoteA)
        syncStateDao.upsertConflict(conflictA)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflictA.toSnapshot(),
            expectedLocalEntity = localA,
            expectedOperations = listOf(opA.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remoteA)

        // localA outbox deleted
        assertTrue(remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, localA.id).isEmpty())

        // localA2 outbox in SCOPE_A and localB outbox in SCOPE_B are perfectly preserved!
        val opsA2 = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, localA2.id)
        assertEquals(1, opsA2.size)
        assertEquals("op-a2", opsA2.first().operationId)

        val opsB = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_B, localB.id)
        assertEquals(1, opsB.size)
        assertEquals("op-b", opsB.first().operationId)
    }

    // ---------------------------------------------------------------------------------------------
    // KEEP_LOCAL TESTS (9 - 16)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test09_keepLocal_createConflictWithoutSuccessor_transformsToUpdateRetry() = runTest {
        val local = sampleVerification(version = 0L, baseVersion = null, syncStatus = "CONFLICT")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val createOp = sampleOperation(
            operationId = "op-create",
            entityId = local.id,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        syncOperationDao.insert(createOp)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = createOp.operationId,
            localVersion = 0L,
            remoteVersion = 2L,
            localEntity = local,
            remoteEntity = remote,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(createOp.toSnapshot()),
        )

        val targetPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(local)

        remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
            syncScopeKey = SCOPE_A,
            precondition = precondition,
            retainedOperationId = createOp.operationId,
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = targetPayload,
            nowEpochMillis = 3000L,
        )

        // Retained operation transformed to UPDATE retry with baseVersion = remoteVersion (2L)
        val ops = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id)
        assertEquals(1, ops.size)
        val retOp = ops.single()
        assertEquals("op-create", retOp.operationId)
        assertEquals("UPDATE", retOp.operationTypeCode)
        assertEquals(2L, retOp.baseVersion)
        assertEquals("PENDING", retOp.statusCode)
        assertEquals(false, retOp.isBlocked)
        assertNull(retOp.predecessorOperationId)
        assertEquals(targetPayload, retOp.payloadJson)

        // Room entity rebased to PENDING_UPDATE and version=2L, baseVersion=2L
        val rebasedEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(rebasedEntity)
        assertEquals("PENDING_UPDATE", rebasedEntity.sync.syncStatus)
        assertEquals(2L, rebasedEntity.sync.version)
        assertEquals(2L, rebasedEntity.sync.baseVersion)
        assertNull(rebasedEntity.sync.lastSyncError)
        assertEquals(3000L, rebasedEntity.sync.localUpdatedAtEpochMillis)

        // Conflict deleted
        assertNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    data class RealTwoOpChainFixture(
        val currentLocal: UserMerchantVerificationEntity,
        val predOp: SyncOperationEntity,
        val succOp: SyncOperationEntity,
        val conflict: SyncConflictEntity,
        val remoteDto: UserMerchantVerificationDto,
    )

    private suspend fun setupRealTwoOpConflictChain(
        alias: String = "MIGROS",
        initialDecisionCode: String = "ASSIGN",
        initialMerchantId: String? = "migros",
        userMutatedDecisionCode: String = "BLOCK",
        userMutatedMerchantId: String? = null,
        remoteDecisionCode: String = "ASSIGN",
        remoteMerchantId: String? = "migros",
        remoteVersion: Long = 3L,
        predOpId: String = "00000000000000000000000000000001",
        succOpId: String = "00000000000000000000000000000002",
        nowEpochMillis: Long = 1600L,
    ): RealTwoOpChainFixture {
        val verificationId = UuidHelper.deterministicMerchantVerificationUuid(USER_A_ID, alias)

        val initialEntity = sampleVerification(
            id = verificationId,
            userId = USER_A_ID,
            alias = alias,
            decisionCode = initialDecisionCode,
            merchantId = initialMerchantId,
            version = 0L,
            baseVersion = null,
            syncStatus = "PENDING_CREATE",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(initialEntity)

        val initialPredOp = sampleOperation(
            operationId = predOpId,
            entityId = verificationId,
            operationTypeCode = "CREATE",
            statusCode = "IN_FLIGHT",
            baseVersion = null,
            isBlocked = false,
            attemptCount = 1,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(initialEntity),
        )
        syncOperationDao.insert(initialPredOp)

        val currentLocalEntity = sampleVerification(
            id = verificationId,
            userId = USER_A_ID,
            alias = alias,
            decisionCode = userMutatedDecisionCode,
            merchantId = userMutatedMerchantId,
            version = 0L,
            baseVersion = null,
            syncStatus = "PENDING_UPDATE",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(currentLocalEntity)

        val initialSuccOp = sampleOperation(
            operationId = succOpId,
            entityId = verificationId,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = null,
            isBlocked = true,
            predecessorOperationId = predOpId,
            protocolVersion = 2,
            attemptCount = 0,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(currentLocalEntity),
        )
        syncOperationDao.insert(initialSuccOp)

        val remoteDto = UserMerchantVerificationDto(
            id = verificationId,
            userId = USER_A_ID,
            workspaceId = null,
            normalizedAlias = alias,
            decisionCode = remoteDecisionCode,
            merchantId = remoteMerchantId,
            scopeCode = "PERSONAL",
            createdAt = "1970-01-01T00:00:01Z",
            updatedAt = "1970-01-01T00:00:02Z",
            version = remoteVersion,
        )
        val conflict = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = ENTITY_TYPE,
            entityId = verificationId,
            operationId = predOpId,
            localVersion = 0L,
            remoteVersion = remoteVersion,
            localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
            remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
            detectedAtEpochMillis = nowEpochMillis,
        )
        val recordOk = db.localMutationDao().recordV2Conflict(SCOPE_A, conflict, nowEpochMillis)
        check(recordOk) { "recordV2Conflict başarısız oldu" }

        val predOpInDb = syncOperationDao.getById(SCOPE_A, predOpId)!!
        val succOpInDb = syncOperationDao.getById(SCOPE_A, succOpId)!!
        val localInDb = remoteSyncDao.getUserMerchantVerificationRow(verificationId)!!

        assertEquals("CONFLICT", predOpInDb.statusCode)
        assertEquals("PENDING", succOpInDb.statusCode)
        assertTrue(succOpInDb.isBlocked)
        assertEquals(predOpId, succOpInDb.predecessorOperationId)
        assertNull(succOpInDb.baseVersion)
        assertEquals(predOpId, conflict.operationId)

        return RealTwoOpChainFixture(
            currentLocal = localInDb,
            predOp = predOpInDb,
            succOp = succOpInDb,
            conflict = conflict,
            remoteDto = remoteDto,
        )
    }

    @Test
    fun test10_keepLocal_realTwoOpChain_rebasesAndUnblocksSuccessorAsUpdateRetry() = runTest {
        val f = setupRealTwoOpConflictChain()

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), f.succOp.toSnapshot()),
        )
        val targetPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal)

        remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
            syncScopeKey = SCOPE_A,
            precondition = precondition,
            retainedOperationId = f.succOp.operationId,
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = targetPayload,
            nowEpochMillis = 3500L,
        )

        // Predecessor removed, successor unblocked and rebased to remoteVersion (3L)
        val ops = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id)
        assertEquals(1, ops.size)
        val retOp = ops.single()
        assertEquals(f.succOp.operationId, retOp.operationId)
        assertEquals("UPDATE", retOp.operationTypeCode)
        assertEquals(3L, retOp.baseVersion)
        assertEquals("PENDING", retOp.statusCode)
        assertEquals(false, retOp.isBlocked)
        assertNull(retOp.predecessorOperationId)
        assertEquals(targetPayload, retOp.payloadJson)

        val rebasedEntity = remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id)
        assertNotNull(rebasedEntity)
        assertEquals("PENDING_UPDATE", rebasedEntity.sync.syncStatus)
        assertEquals(3L, rebasedEntity.sync.version)
        assertEquals(3L, rebasedEntity.sync.baseVersion)
        assertEquals("BLOCK", rebasedEntity.decisionCode)
        assertNull(rebasedEntity.merchantId)
        assertNull(syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10b_keepRemote_realTwoOpChain_cleansBothOutboxRowsAndAppliesRemote() = runTest {
        val f = setupRealTwoOpConflictChain()

        val remoteEntity = sampleVerification(
            id = f.currentLocal.id,
            version = 3L,
            baseVersion = 3L,
            decisionCode = "ASSIGN",
            merchantId = "migros",
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2000L,
        )
        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), f.succOp.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remoteEntity)

        // Remote applied
        val liveEntity = remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id)
        assertEquals(remoteEntity, liveEntity)
        assertEquals("SYNCED", liveEntity?.sync?.syncStatus)
        assertEquals(3L, liveEntity?.sync?.version)

        // Both outbox operations cleaned
        val remainingOps = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id)
        assertTrue(remainingOps.isEmpty())
        assertNull(syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10c_2opChain_conflictOperationIdAttachedToSuccessor_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        val badConflict = f.conflict.copy(operationId = f.succOp.operationId)
        syncStateDao.upsertConflict(badConflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = badConflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), f.succOp.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(f.currentLocal, remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id))
        assertEquals(listOf(f.predOp, f.succOp), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(badConflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10d_2opChain_predecessorInFlight_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        val inFlightPred = f.predOp.copy(statusCode = "IN_FLIGHT")
        syncOperationDao.deleteCompleted(SCOPE_A, inFlightPred.operationId)
        syncOperationDao.insert(inFlightPred)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(inFlightPred.toSnapshot(), f.succOp.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(f.currentLocal, remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id))
        assertEquals(listOf(inFlightPred, f.succOp), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10e_2opChain_successorConflict_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        val conflictSucc = f.succOp.copy(statusCode = "CONFLICT")
        syncOperationDao.deleteCompleted(SCOPE_A, conflictSucc.operationId)
        syncOperationDao.insert(conflictSucc)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), conflictSucc.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(f.currentLocal, remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id))
        assertEquals(listOf(f.predOp, conflictSucc), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10f_2opChain_successorBaseVersionNonNull_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        val nonNullBaseSucc = f.succOp.copy(baseVersion = 1L)
        syncOperationDao.deleteCompleted(SCOPE_A, nonNullBaseSucc.operationId)
        syncOperationDao.insert(nonNullBaseSucc)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), nonNullBaseSucc.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(f.currentLocal, remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id))
        assertEquals(listOf(f.predOp, nonNullBaseSucc), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10g_2opChain_successorPayloadUpdateFormatWithBaseVersionNull_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        // 1. Successor payload parsed via UPDATE codec with baseVersion=null throws IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(
                jsonString = f.succOp.payloadJson!!,
                operation = "UPDATE",
                baseVersion = null,
            )
        }

        // 2. Corrupt successor payload with unexpected UPDATE field (unknown key base_version) fails closed
        val corruptPayload = """{"id":"${f.currentLocal.id}","user_id":"$USER_A_ID","workspace_id":null,"normalized_alias":"MIGROS","decision_code":"BLOCK","merchant_id":null,"scope_code":"PERSONAL","base_version":1}"""
        val corruptSucc = f.succOp.copy(payloadJson = corruptPayload)
        syncOperationDao.deleteCompleted(SCOPE_A, corruptSucc.operationId)
        syncOperationDao.insert(corruptSucc)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), corruptSucc.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(f.currentLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(f.currentLocal, remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id))
        assertEquals(listOf(f.predOp, corruptSucc), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    @Test
    fun test10h_2opChain_successorPayloadDiffersFromCurrentLocalEntity_failsClosedRollback() = runTest {
        val f = setupRealTwoOpConflictChain()

        // Current local entity modified to ASSIGN, but successor payload still has BLOCK
        val changedLocal = f.currentLocal.copy(
            decisionCode = "ASSIGN",
            merchantId = "migros",
            sync = f.currentLocal.sync.copy(localUpdatedAtEpochMillis = 2500L),
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(changedLocal)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = changedLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), f.succOp.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = f.succOp.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(changedLocal),
                nowEpochMillis = 3500L,
            )
        }

        // Byte-for-byte check
        assertEquals(changedLocal, remoteSyncDao.getUserMerchantVerificationRow(changedLocal.id))
        assertEquals(listOf(f.predOp, f.succOp), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, changedLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, changedLocal.id))
    }

    @Test
    fun test11_keepLocal_updateConflict_staysCanonicalUpdateRetry() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L, syncStatus = "CONFLICT", decisionCode = "BLOCK", merchantId = null)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-upd",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = op.operationId,
            localVersion = 1L,
            remoteVersion = 2L,
            localEntity = local,
            remoteEntity = remote,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
            syncScopeKey = SCOPE_A,
            precondition = precondition,
            retainedOperationId = op.operationId,
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            nowEpochMillis = 4000L,
        )

        val ops = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id)
        assertEquals(1, ops.size)
        val liveOp = ops.single()
        assertEquals("UPDATE", liveOp.operationTypeCode)
        assertEquals(2L, liveOp.baseVersion)
        assertEquals("PENDING", liveOp.statusCode)

        val rebased = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(rebased)
        assertEquals("PENDING_UPDATE", rebased.sync.syncStatus)
        assertEquals(2L, rebased.sync.version)
        assertEquals(2L, rebased.sync.baseVersion)
        assertNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test12_keepLocal_deleteConflict_staysCanonicalDeleteRetryAndPreservesTombstone() = runTest {
        val local = sampleVerification(
            version = 1L,
            baseVersion = 1L,
            deletedAtEpochMillis = 1500L,
            syncStatus = "CONFLICT",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-del",
            entityId = local.id,
            operationTypeCode = "DELETE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = op.operationId,
            localVersion = 1L,
            remoteVersion = 2L,
            localEntity = local,
            remoteEntity = remote,
        )
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
            syncScopeKey = SCOPE_A,
            precondition = precondition,
            retainedOperationId = op.operationId,
            targetOperationTypeCode = "DELETE",
            targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
            nowEpochMillis = 4500L,
        )

        val ops = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id)
        assertEquals(1, ops.size)
        val liveOp = ops.single()
        assertEquals("DELETE", liveOp.operationTypeCode)
        assertEquals(2L, liveOp.baseVersion)
        assertEquals("PENDING", liveOp.statusCode)

        val rebased = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(rebased)
        assertEquals("PENDING_DELETE", rebased.sync.syncStatus)
        assertEquals(2L, rebased.sync.version)
        assertEquals(2L, rebased.sync.baseVersion)
        assertEquals(1500L, rebased.sync.deletedAtEpochMillis) // Tombstone preserved!
        assertNull(syncStateDao.getConflict(SCOPE_A, local.id))
    }

    @Test
    fun test13_keepLocal_localDecisionChangedAfterSnapshot_oldSnapshotNotOverwritten() = runTest {
        // Original local decision was ASSIGN migros
        val originalLocal = sampleVerification(
            version = 1L,
            baseVersion = 1L,
            decisionCode = "ASSIGN",
            merchantId = "migros",
        )
        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = originalLocal.id,
            operationId = "op-chg",
            localEntity = originalLocal,
            remoteEntity = remote,
        )

        // Then user changed local decision to BLOCK (or modified localUpdatedAt)
        val updatedLocal = originalLocal.copy(
            decisionCode = "BLOCK",
            merchantId = null,
            sync = originalLocal.sync.copy(localUpdatedAtEpochMillis = 2500L),
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(updatedLocal)

        val op = sampleOperation(
            operationId = "op-chg",
            entityId = updatedLocal.id,
            operationTypeCode = "UPDATE",
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(updatedLocal),
        )
        syncOperationDao.insert(op)
        syncStateDao.upsertConflict(conflict)

        // Case A: If caller provided expectedLocalEntity = originalLocal (stale), it must rollback!
        val stalePrecondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = originalLocal,
            expectedOperations = listOf(op.toSnapshot()),
        )
        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = stalePrecondition,
                retainedOperationId = op.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(originalLocal),
                nowEpochMillis = 5000L,
            )
        }
        assertEquals("BLOCK", remoteSyncDao.getUserMerchantVerificationRow(updatedLocal.id)?.decisionCode)

        // Case B: If caller verified expectedLocalEntity matches updatedLocal, canonical payload from updatedLocal is preserved!
        val freshPrecondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = updatedLocal,
            expectedOperations = listOf(op.toSnapshot()),
        )
        remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
            syncScopeKey = SCOPE_A,
            precondition = freshPrecondition,
            retainedOperationId = op.operationId,
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(updatedLocal),
            nowEpochMillis = 5000L,
        )

        val live = remoteSyncDao.getUserMerchantVerificationRow(updatedLocal.id)
        assertNotNull(live)
        assertEquals("BLOCK", live.decisionCode) // Current decision preserved, NOT overwritten with old conflict.localPayloadJson!
        val liveOp = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, updatedLocal.id).single()
        assertTrue(liveOp.payloadJson!!.contains("\"decision_code\":\"BLOCK\""))
    }

    @Test
    fun test14_keepLocal_wrongOperationTypeOrBaseVersionOrPayloadRollback() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-bad-type",
            entityId = local.id,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        // Wrong targetOperationTypeCode (DELETE instead of UPDATE for active entity)
        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = op.operationId,
                targetOperationTypeCode = "DELETE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
                nowEpochMillis = 5000L,
            )
        }

        // Wrong targetPayloadJson
        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = op.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = "{ corrupt payload }",
                nowEpochMillis = 5000L,
            )
        }

        // Verify state is completely preserved
        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(1, remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id).size)
    }

    @Test
    fun test15_keepLocal_multiActiveTailOrOrphanSuccessorOrBlockedRootOrProtocolVersionNot2_rollsBack() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        // Blocked root
        val blockedRoot = sampleOperation(
            operationId = "op-blocked-root",
            entityId = local.id,
            isBlocked = true, // INVALID for root!
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(blockedRoot)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = blockedRoot.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(blockedRoot.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = blockedRoot.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
                nowEpochMillis = 5000L,
            )
        }

        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
    }

    @Test
    fun test16_negativeTests_entityOutboxConflictAndCursorSnapshotsRemainUnchanged() = runTest {
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-inv-check",
            entityId = local.id,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val cursor = SyncCursorEntity(syncScopeKey = SCOPE_A, entityTypeCode = ENTITY_TYPE, entityId = local.id, updatedAtEpochMillis = 1000L)
        syncStateDao.upsertCursor(cursor)

        // Trigger rollback via invalid retainedOperationId
        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedLocalEntity = local,
            expectedOperations = listOf(op.toSnapshot()),
        )

        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = precondition,
                retainedOperationId = "wrong-op-id",
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
                nowEpochMillis = 5000L,
            )
        }

        // All 4 tables remain 100% byte-for-byte identical to snapshot before invocation
        assertEquals(local, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(listOf(op), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, local.id))
        assertEquals(conflict, syncStateDao.getConflict(SCOPE_A, local.id))
        assertEquals(cursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // ---------------------------------------------------------------------------------------------
    // REPOSITORY & CROSS-CUTTING TESTS (17 - 20)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test17_repository_staleAppErrorMappingOnResolutionException() = runTest {
        val auth = SimpleFakeAuthRepository(USER_1_SESSION)
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-repo-stale",
            entityId = local.id,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val failingSyncDao = object : RemoteSyncDao by remoteSyncDao {
            override suspend fun resolveUserMerchantVerificationKeepRemote(
                syncScopeKey: String,
                precondition: UserMerchantVerificationResolutionPrecondition,
                remoteEntity: UserMerchantVerificationEntity,
            ) {
                throw UserMerchantVerificationConflictStaleResolutionException("Concurrent conflict resolution!")
            }
        }

        val repository = com.feniqo.mobile.data.repository.OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = com.feniqo.mobile.data.sync.InitialRemoteSync(DummyCoreRemoteDataSource(), failingSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(DummyCoreRemoteDataSource(), failingSyncDao) { 1_000L },
            outboxProcessor = com.feniqo.mobile.data.sync.OutboxProcessor(DummyOutboxQueue(), DummyOutboxExecutor()),
            incrementalRemoteSync = com.feniqo.mobile.data.sync.IncrementalRemoteSync(DummyCoreRemoteDataSource(), failingSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(DummyCoreRemoteDataSource(), failingSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = com.feniqo.mobile.data.local.outbox.OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = failingSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, db.syncOperationDao(), db.localMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.resolveConflict(com.feniqo.mobile.domain.model.EntityId(local.id), com.feniqo.mobile.domain.repository.ConflictResolution.KEEP_REMOTE)
        assertTrue(result is com.feniqo.mobile.domain.repository.RepositoryResult.Failure)
        val error = (result as com.feniqo.mobile.domain.repository.RepositoryResult.Failure).error
        assertTrue(error is com.feniqo.mobile.domain.model.AppError.Conflict)
        assertEquals("sync.conflict_resolution_stale", error.code)
    }

    @Test
    fun test18_repository_cancellationExceptionRethrownWithoutCatching() = runTest {
        val auth = SimpleFakeAuthRepository(USER_1_SESSION)
        val local = sampleVerification(version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op = sampleOperation(
            operationId = "op-cancel",
            entityId = local.id,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        syncOperationDao.insert(op)

        val remote = sampleVerification(version = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = op.operationId, localEntity = local, remoteEntity = remote)
        syncStateDao.upsertConflict(conflict)

        val cancellingSyncDao = object : RemoteSyncDao by remoteSyncDao {
            override suspend fun resolveUserMerchantVerificationKeepRemote(
                syncScopeKey: String,
                precondition: UserMerchantVerificationResolutionPrecondition,
                remoteEntity: UserMerchantVerificationEntity,
            ) {
                throw CancellationException("Coroutine cancelled")
            }
        }

        val repository = com.feniqo.mobile.data.repository.OfflineFirstSyncRepository(
            authRepository = auth,
            initialRemoteSync = com.feniqo.mobile.data.sync.InitialRemoteSync(DummyCoreRemoteDataSource(), cancellingSyncDao) { 1_000L },
            workspaceInitialRemoteSync = com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync(DummyCoreRemoteDataSource(), cancellingSyncDao) { 1_000L },
            outboxProcessor = com.feniqo.mobile.data.sync.OutboxProcessor(DummyOutboxQueue(), DummyOutboxExecutor()),
            incrementalRemoteSync = com.feniqo.mobile.data.sync.IncrementalRemoteSync(DummyCoreRemoteDataSource(), cancellingSyncDao, syncStateDao) { 1_000L },
            workspaceIncrementalRemoteSync = com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync(DummyCoreRemoteDataSource(), cancellingSyncDao, syncStateDao) { 1_000L },
            offlineWriteQueue = com.feniqo.mobile.data.local.outbox.OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao()),
            syncStateDao = syncStateDao,
            remoteSyncDao = cancellingSyncDao,
            conflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(syncStateDao, db.syncOperationDao(), db.localMutationDao()) { 1_000L },
            nowEpochMillisProvider = { 10_000L },
        )

        assertFailsWith<CancellationException> {
            repository.resolveConflict(com.feniqo.mobile.domain.model.EntityId(local.id), com.feniqo.mobile.domain.repository.ConflictResolution.KEEP_REMOTE)
        }
    }

    @Test
    fun test19_keepLocalAndKeepRemote_cannotTouchOtherUsersRecord() = runTest {
        val localB = sampleVerification(userId = USER_B_ID, alias = "MIGROS", version = 1L, baseVersion = 1L, scopeKey = "user:$USER_B_ID")
        remoteSyncDao.upsertUserMerchantVerificationRow(localB)

        val opB = sampleOperation(
            operationId = "op-b-cross",
            syncScopeKey = SCOPE_B,
            entityId = localB.id,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(localB),
        )
        syncOperationDao.insert(opB)

        val remoteB = sampleVerification(
            userId = USER_B_ID,
            alias = "MIGROS",
            version = 2L,
            baseVersion = 2L,
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2000L,
            scopeKey = "user:$USER_B_ID",
        )
        val conflictB = sampleConflict(
            syncScopeKey = SCOPE_B,
            entityId = localB.id,
            operationId = opB.operationId,
            localEntity = localB,
            remoteEntity = remoteB,
        )
        syncStateDao.upsertConflict(conflictB)

        val preconditionB = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = conflictB.toSnapshot(),
            expectedLocalEntity = localB,
            expectedOperations = listOf(opB.toSnapshot()),
        )

        // Attempting KEEP_REMOTE from SCOPE_A on USER_B's record must fail-closed
        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, preconditionB, remoteB)
        }

        // Attempting KEEP_LOCAL from SCOPE_A on USER_B's record must fail-closed
        assertFailsWith<UserMerchantVerificationConflictStaleResolutionException> {
            remoteSyncDao.resolveUserMerchantVerificationKeepLocal(
                syncScopeKey = SCOPE_A,
                precondition = preconditionB,
                retainedOperationId = opB.operationId,
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(localB),
                nowEpochMillis = 3000L,
            )
        }

        // Verify USER_B's record, outbox, and conflict were untouched
        assertEquals(localB, remoteSyncDao.getUserMerchantVerificationRow(localB.id))
        val remainingOps = remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_B, localB.id)
        assertEquals(1, remainingOps.size)
        assertEquals("op-b-cross", remainingOps.first().operationId)
        assertNotNull(syncStateDao.getConflict(SCOPE_B, localB.id))
    }

    @Test
    fun test20_transaction_failureAfterInitialMutation_rollsBackAllChanges() = runTest {
        val f = setupRealTwoOpConflictChain()

        // Create a test-only SQLite trigger on sync_operations to fail mid-transaction
        // (after remoteEntity upsert in resolveUserMerchantVerificationKeepRemote, during sync_operations deletion).
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER test_abort_keep_remote BEFORE DELETE ON sync_operations " +
                "BEGIN SELECT RAISE(ABORT, 'Test SQL trigger failure mid-transaction'); END;",
        )

        val remoteEntity = sampleVerification(
            id = f.currentLocal.id,
            version = 3L,
            baseVersion = 3L,
            decisionCode = "ASSIGN",
            merchantId = "migros",
            syncStatus = "SYNCED",
            lastSyncError = null,
            updatedAtEpochMillis = 2000L,
        )
        val precondition = UserMerchantVerificationResolutionPrecondition(
            expectedConflict = f.conflict.toSnapshot(),
            expectedLocalEntity = f.currentLocal,
            expectedOperations = listOf(f.predOp.toSnapshot(), f.succOp.toSnapshot()),
        )

        try {
            assertFailsWith<Exception> {
                remoteSyncDao.resolveUserMerchantVerificationKeepRemote(SCOPE_A, precondition, remoteEntity)
            }
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS test_abort_keep_remote")
        }

        // Entire transaction rolled back: entity was NOT mutated to remote, ops were NOT deleted, conflict was NOT deleted!
        val liveEntity = remoteSyncDao.getUserMerchantVerificationRow(f.currentLocal.id)
        assertEquals(f.currentLocal, liveEntity)
        assertEquals(listOf(f.predOp, f.succOp), remoteSyncDao.getAllUserMerchantVerificationOperations(SCOPE_A, f.currentLocal.id))
        assertEquals(f.conflict, syncStateDao.getConflict(SCOPE_A, f.currentLocal.id))
    }

    private class SimpleFakeAuthRepository(
        private val session: com.feniqo.mobile.domain.repository.AuthSession?,
    ) : com.feniqo.mobile.domain.repository.AuthRepository {
        override fun observeSession(): kotlinx.coroutines.flow.Flow<com.feniqo.mobile.domain.repository.AuthSession?> =
            kotlinx.coroutines.flow.flowOf(session)
        override fun observeCurrentProfile(): kotlinx.coroutines.flow.Flow<com.feniqo.mobile.domain.model.UserProfile?> =
            kotlinx.coroutines.flow.flowOf(null)
        override suspend fun signIn(email: String, password: String): com.feniqo.mobile.domain.repository.RepositoryResult<Unit> = error("Not needed")
        override suspend fun signUp(email: String, password: String, fullName: String?): com.feniqo.mobile.domain.repository.RepositoryResult<com.feniqo.mobile.domain.model.EntityId> = error("Not needed")
        override suspend fun refreshSession(): com.feniqo.mobile.domain.repository.RepositoryResult<Unit> = error("Not needed")
        override suspend fun signOut(): com.feniqo.mobile.domain.repository.RepositoryResult<Unit> = error("Not needed")
    }

    private class DummyCoreRemoteDataSource : com.feniqo.mobile.data.remote.core.CoreRemoteDataSource {
        override suspend fun fetchCategories(query: com.feniqo.mobile.data.remote.core.CategoryRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.CategoryDto>(), query.page, 0)
        override suspend fun fetchTransactions(query: com.feniqo.mobile.data.remote.core.TransactionRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.TransactionDto>(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.RecurringTransactionDto>(), query.page, 0)
        override suspend fun fetchSubscriptions(query: com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.SubscriptionDto>(), query.page, 0)
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.GoalDto>(), query.page, 0)
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.GoalContributionDto>(), query.page, 0)
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.DebtDto>(), query.page, 0)
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.DebtPaymentDto>(), query.page, 0)
        override suspend fun fetchWorkspaces(query: com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.WorkspaceDto>(), query.page, 0)
        override suspend fun fetchWorkspaces(request: com.feniqo.mobile.data.remote.core.RemotePageRequest) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.WorkspaceDto>(), request, 0)
        override suspend fun fetchWorkspaceMembers(query: com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto>(), query.page, 0)
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: com.feniqo.mobile.data.remote.core.RemotePageRequest) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto>(), page, 0)
        override suspend fun fetchBudgets(query: com.feniqo.mobile.data.remote.core.BudgetRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.BudgetDto>(), query.page, 0)
        override suspend fun fetchMerchantVerifications(query: com.feniqo.mobile.data.remote.core.UserMerchantVerificationRemoteQuery) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto>(), query.page, 0)
        override suspend fun fetchTags(scope: com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope, page: com.feniqo.mobile.data.remote.core.RemotePageRequest) = com.feniqo.mobile.data.remote.core.RemotePage(emptyList<com.feniqo.mobile.data.remote.dto.TagDto>(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String) = emptyList<com.feniqo.mobile.data.remote.dto.TransactionTagDto>()
        override suspend fun upsertProfile(dto: com.feniqo.mobile.data.remote.dto.ProfileDto) = Unit
        override suspend fun upsertCategory(dto: com.feniqo.mobile.data.remote.dto.CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: com.feniqo.mobile.data.remote.dto.TransactionDto) = Unit
        override suspend fun upsertBudget(dto: com.feniqo.mobile.data.remote.dto.BudgetDto) = Unit
        override suspend fun upsertTag(dto: com.feniqo.mobile.data.remote.dto.TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: com.feniqo.mobile.data.remote.dto.TransactionTagDto) = Unit
        override suspend fun fetchProfile(userId: String): com.feniqo.mobile.data.remote.dto.ProfileDto? = null
    }

    private class DummyOutboxQueue : com.feniqo.mobile.data.sync.OutboxQueue {
        override suspend fun readyOperations(syncScopeKey: String, limit: Int): List<SyncOperationEntity> = emptyList()
        override suspend fun claimOperation(syncScopeKey: String, operationId: String): SyncOperationEntity? = null
        override suspend fun markSucceeded(syncScopeKey: String, operationId: String): Boolean = true
        override suspend fun ackV2Execution(syncScopeKey: String, operationId: String, result: com.feniqo.mobile.data.sync.OutboxExecutionResult): Boolean = true
        override suspend fun recordV2Conflict(syncScopeKey: String, conflict: SyncConflictEntity): Boolean = true
        override suspend fun markConflict(syncScopeKey: String, operationId: String, errorMessage: String): Boolean = true
        override suspend fun recordFailure(syncScopeKey: String, operationId: String, errorMessage: String): Boolean = true
    }

    private class DummyOutboxExecutor : com.feniqo.mobile.data.sync.OutboxOperationExecutor {
        override suspend fun execute(operation: SyncOperationEntity) = com.feniqo.mobile.data.sync.OutboxExecutionResult.V1Completed
    }

    private val USER_1_SESSION = com.feniqo.mobile.domain.repository.AuthSession(
        userId = com.feniqo.mobile.domain.model.EntityId(USER_A_ID),
        email = "user1@example.com",
        expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(2_000_000L),
    )
}
