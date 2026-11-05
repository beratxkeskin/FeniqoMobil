package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.OutboxErrorClassification
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.dto.toDto
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.datetime.Instant
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationPullDaoTest {

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

        fun sampleRemoteEntity(
            userId: String = USER_A_ID,
            alias: String = "MIGROS",
            decisionCode: String = "ASSIGN",
            merchantId: String? = "migros",
            version: Long = 1L,
            baseVersion: Long? = 1L,
            deletedAtEpochMillis: Long? = null,
            createdAtEpochMillis: Long = 1000L,
            updatedAtEpochMillis: Long = 1000L,
            id: String = UuidHelper.deterministicMerchantVerificationUuid(userId, alias),
            scopeKey: String = "user:$userId",
            workspaceId: String? = null,
            scopeCode: String = "PERSONAL",
            syncStatus: String = "SYNCED",
            lastSyncError: String? = null,
        ): UserMerchantVerificationEntity = UserMerchantVerificationEntity(
            id = id,
            ownerId = userId,
            workspaceId = workspaceId,
            normalizedAlias = alias,
            decisionCode = decisionCode,
            merchantId = merchantId,
            scopeCode = scopeCode,
            scopeKey = scopeKey,
            createdAtEpochMillis = createdAtEpochMillis,
            sync = SyncMetadata(
                syncStatus = syncStatus,
                updatedAtEpochMillis = updatedAtEpochMillis,
                localUpdatedAtEpochMillis = updatedAtEpochMillis,
                deletedAtEpochMillis = deletedAtEpochMillis,
                version = version,
                baseVersion = baseVersion,
                lastSyncError = lastSyncError,
            ),
        )

        fun sampleCursor(
            syncScopeKey: String = SCOPE_A,
            entityTypeCode: String = ENTITY_TYPE,
            entityId: String,
            updatedAtEpochMillis: Long = 1000L,
        ): SyncCursorEntity = SyncCursorEntity(
            syncScopeKey = syncScopeKey,
            entityTypeCode = entityTypeCode,
            updatedAtEpochMillis = updatedAtEpochMillis,
            entityId = entityId,
        )

        fun sampleOperation(
            operationId: String,
            syncScopeKey: String = SCOPE_A,
            entityTypeCode: String = ENTITY_TYPE,
            entityId: String,
            operationTypeCode: String = "UPDATE",
            baseVersion: Long? = 1L,
            statusCode: String = "PENDING",
            createdAtEpochMillis: Long = 1000L,
            updatedAtEpochMillis: Long = 1000L,
            payloadJson: String? = null,
            protocolVersion: Int = 2,
            errorClassification: String? = null,
            isBlocked: Boolean = false,
            predecessorOperationId: String? = null,
        ): SyncOperationEntity {
            val resolvedPayload = payloadJson ?: UserMerchantVerificationPayloadCodec.encodeUpdate(
                sampleRemoteEntity(
                    id = entityId,
                    version = baseVersion ?: 1L,
                    baseVersion = baseVersion ?: 1L,
                )
            )
            return SyncOperationEntity(
                operationId = operationId,
                syncScopeKey = syncScopeKey,
                entityTypeCode = entityTypeCode,
                entityId = entityId,
                operationTypeCode = operationTypeCode,
                payloadJson = resolvedPayload,
                baseVersion = baseVersion,
                statusCode = statusCode,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 0L,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
                isBlocked = isBlocked,
                predecessorOperationId = predecessorOperationId,
                protocolVersion = protocolVersion,
                errorClassification = errorClassification,
            )
        }

        fun sampleSnapshotJson(entity: UserMerchantVerificationEntity): String {
            val dto = entity.toDomain().toDto(
                version = entity.sync.version,
                deletedAt = entity.sync.deletedAtEpochMillis?.let { Instant.fromEpochMilliseconds(it) },
            )
            return UserMerchantVerificationPayloadCodec.encodeSnapshot(dto)
        }

        fun sampleConflict(
            syncScopeKey: String = SCOPE_A,
            entityTypeCode: String = ENTITY_TYPE,
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
            val effLocal = localEntity ?: sampleRemoteEntity(
                id = entityId,
                version = localVersion,
                baseVersion = localVersion,
            )
            val effRemote = remoteEntity ?: sampleRemoteEntity(
                id = entityId,
                version = remoteVersion,
                baseVersion = localVersion,
                updatedAtEpochMillis = detectedAtEpochMillis,
            )
            return SyncConflictEntity(
                syncScopeKey = syncScopeKey,
                entityTypeCode = entityTypeCode,
                entityId = entityId,
                operationId = operationId,
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localPayloadJson = localPayloadJson ?: sampleSnapshotJson(effLocal),
                remotePayloadJson = remotePayloadJson ?: sampleSnapshotJson(effRemote),
                detectedAtEpochMillis = detectedAtEpochMillis,
            )
        }
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

    // -------------------------------------------------------------------------
    // Apply Tests (1 to 6)
    // -------------------------------------------------------------------------

    @Test
    fun apply_cleanDb_atomicallyInsertsActiveRemoteEntityAndAdvancesCursor() = runTest {
        val remote = sampleRemoteEntity()
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = remote.sync.updatedAtEpochMillis)

        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)

        val stored = remoteSyncDao.getUserMerchantVerificationRow(remote.id)
        assertNotNull(stored)
        assertEquals(remote, stored)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(cursor.updatedAtEpochMillis, storedCursor.updatedAtEpochMillis)
        assertEquals(remote.id, storedCursor.entityId)

        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, remote.id))
    }

    @Test
    fun apply_existingSyncedEntity_appliesNewerRemoteDecisionAndAdvancesCursor() = runTest {
        val initial = sampleRemoteEntity(
            decisionCode = "ASSIGN",
            merchantId = "migros",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        val initialCursor = sampleCursor(entityId = initial.id, updatedAtEpochMillis = 1000L)
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, initial, initialCursor)

        val newerRemote = sampleRemoteEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            version = 2L,
            baseVersion = 2L,
            updatedAtEpochMillis = 2000L,
        )
        val newerCursor = sampleCursor(entityId = newerRemote.id, updatedAtEpochMillis = 2000L)

        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, newerRemote, newerCursor)

        val stored = remoteSyncDao.getUserMerchantVerificationRow(newerRemote.id)
        assertNotNull(stored)
        assertEquals("BLOCK", stored.decisionCode)
        assertNull(stored.merchantId)
        assertEquals(2L, stored.sync.version)
        assertEquals(2000L, stored.sync.updatedAtEpochMillis)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals(newerRemote.id, storedCursor.entityId)
    }

    @Test
    fun apply_remoteTombstone_appliesSoftDeleteAndAdvancesCursor() = runTest {
        val initial = sampleRemoteEntity(version = 1L, baseVersion = 1L, updatedAtEpochMillis = 1000L)
        remoteSyncDao.applyUserMerchantVerificationPull(
            SCOPE_A,
            initial,
            sampleCursor(entityId = initial.id, updatedAtEpochMillis = 1000L),
        )

        val tombstone = sampleRemoteEntity(
            version = 2L,
            baseVersion = 2L,
            deletedAtEpochMillis = 2000L,
            updatedAtEpochMillis = 2000L,
        )
        val cursor = sampleCursor(entityId = tombstone.id, updatedAtEpochMillis = 2000L)

        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, tombstone, cursor)

        val stored = remoteSyncDao.getUserMerchantVerificationRow(tombstone.id)
        assertNotNull(stored)
        assertEquals("SYNCED", stored.sync.syncStatus)
        assertEquals(2000L, stored.sync.deletedAtEpochMillis)
        assertEquals(2L, stored.sync.version)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals(tombstone.id, storedCursor.entityId)
    }

    @Test
    fun apply_sameVersionAndSameContent_isIdempotent() = runTest {
        val remote = sampleRemoteEntity(version = 1L, baseVersion = 1L, updatedAtEpochMillis = 1000L)
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 1000L)

        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)
        // Repeat apply with identical content and version
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)

        val stored = remoteSyncDao.getUserMerchantVerificationRow(remote.id)
        assertNotNull(stored)
        assertEquals(remote, stored)
    }

    @Test
    fun apply_lowerRemoteVersion_rejectsAndRollsBackCompletely() = runTest {
        val initial = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val initialCursor = sampleCursor(entityId = initial.id, updatedAtEpochMillis = 2000L)
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, initial, initialCursor)

        val lowerRemote = sampleRemoteEntity(
            version = 1L,
            baseVersion = 1L,
            decisionCode = "BLOCK",
            merchantId = null,
            updatedAtEpochMillis = 1500L,
        )
        val lowerCursor = sampleCursor(entityId = lowerRemote.id, updatedAtEpochMillis = 1500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(initial.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, lowerRemote, lowerCursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(initial.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_sameVersionDifferentContent_rejectsAndRollsBackCompletely() = runTest {
        val initial = sampleRemoteEntity(
            decisionCode = "ASSIGN",
            merchantId = "migros",
            version = 2L,
            baseVersion = 2L,
            updatedAtEpochMillis = 2000L,
        )
        val initialCursor = sampleCursor(entityId = initial.id, updatedAtEpochMillis = 2000L)
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, initial, initialCursor)

        val conflictingSameVersion = sampleRemoteEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            version = 2L,
            baseVersion = 2L,
            updatedAtEpochMillis = 2000L,
        )
        val cursor = sampleCursor(entityId = conflictingSameVersion.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(initial.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, conflictingSameVersion, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(initial.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_sameVersionDifferentCreatedAt_rejectsAndRollsBack() = runTest {
        val initial = sampleRemoteEntity(
            version = 2L,
            baseVersion = 2L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 2000L,
        )
        val initialCursor = sampleCursor(entityId = initial.id, updatedAtEpochMillis = 2000L)
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, initial, initialCursor)

        val conflictingCreatedAt = sampleRemoteEntity(
            version = 2L,
            baseVersion = 2L,
            createdAtEpochMillis = 1500L,
            updatedAtEpochMillis = 2000L,
        )
        val cursor = sampleCursor(entityId = conflictingCreatedAt.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(initial.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, conflictingCreatedAt, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(initial.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_sameVersionDifferentUpdatedAt_rejectsAndRollsBack() = runTest {
        val initial = sampleRemoteEntity(
            version = 2L,
            baseVersion = 2L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 2000L,
        )
        val initialCursor = sampleCursor(entityId = initial.id, updatedAtEpochMillis = 2000L)
        remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, initial, initialCursor)

        val conflictingUpdatedAt = sampleRemoteEntity(
            version = 2L,
            baseVersion = 2L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 2500L,
        )
        val cursor = sampleCursor(entityId = conflictingUpdatedAt.id, updatedAtEpochMillis = 2500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(initial.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, conflictingUpdatedAt, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(initial.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // -------------------------------------------------------------------------
    // Local Protection Tests (7 to 10)
    // -------------------------------------------------------------------------

    @Test
    fun apply_localPendingCreate_rejectsAndPreservesSnapshot() = runTest {
        val pendingCreate = sampleRemoteEntity(
            syncStatus = "PENDING_CREATE",
            version = 0L,
            baseVersion = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(pendingCreate)
        val op = sampleOperation("op-create", entityId = pendingCreate.id, operationTypeCode = "CREATE", baseVersion = null)
        syncOperationDao.insert(op)

        val remote = sampleRemoteEntity(version = 1L, baseVersion = 1L, updatedAtEpochMillis = 2000L)
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(pendingCreate.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-create")
        val snapshotConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingCreate.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(pendingCreate.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-create"))
        assertEquals(snapshotConflict, remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingCreate.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_localPendingUpdate_rejectsAndPreservesSnapshot() = runTest {
        val pendingUpdate = sampleRemoteEntity(
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(pendingUpdate)
        val op = sampleOperation("op-update", entityId = pendingUpdate.id, operationTypeCode = "UPDATE", baseVersion = 1L)
        syncOperationDao.insert(op)

        val remote = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(pendingUpdate.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-update")
        val snapshotConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingUpdate.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(pendingUpdate.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-update"))
        assertEquals(snapshotConflict, remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingUpdate.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_localPendingDelete_rejectsAndPreservesSnapshot() = runTest {
        val pendingDelete = sampleRemoteEntity(
            syncStatus = "PENDING_DELETE",
            version = 1L,
            baseVersion = 1L,
            deletedAtEpochMillis = 1500L,
            updatedAtEpochMillis = 1500L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(pendingDelete)
        val op = sampleOperation("op-delete", entityId = pendingDelete.id, operationTypeCode = "DELETE", baseVersion = 1L)
        syncOperationDao.insert(op)

        val remote = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(pendingDelete.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-delete")
        val snapshotConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingDelete.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(pendingDelete.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-delete"))
        assertEquals(snapshotConflict, remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, pendingDelete.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun apply_localConflict_rejectsAndPreservesSnapshot() = runTest {
        val conflictEntity = sampleRemoteEntity(
            syncStatus = "CONFLICT",
            version = 1L,
            baseVersion = 1L,
            lastSyncError = "CONFLICT",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(conflictEntity)
        val op = sampleOperation("op-conflict", entityId = conflictEntity.id, operationTypeCode = "UPDATE", baseVersion = 1L)
        syncOperationDao.insert(op)
        val conflict = sampleConflict(entityId = conflictEntity.id, operationId = "op-conflict")
        remoteSyncDao.upsertConflictRow(conflict)

        val remote = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val cursor = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(conflictEntity.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-conflict")
        val snapshotConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, conflictEntity.id)
        val snapshotCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(conflictEntity.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-conflict"))
        assertEquals(snapshotConflict, remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, conflictEntity.id))
        assertEquals(snapshotCursor, syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // -------------------------------------------------------------------------
    // Conflict Tests
    // -------------------------------------------------------------------------

    @Test
    fun recordConflict_validPendingUpdateWithLiveV2CanonicalOutbox_atomicallyRecordsConflict() = runTest {
        val local = sampleRemoteEntity(
            decisionCode = "ASSIGN",
            merchantId = "migros",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1200L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(local)
        val op = sampleOperation(
            operationId = "op-valid",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = 1L,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            version = 2L,
            baseVersion = 2L,
            updatedAtEpochMillis = 2500L,
        )
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-valid",
            localVersion = 1L,
            remoteVersion = 2L,
            detectedAtEpochMillis = 2500L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2500L)

        remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)

        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(updatedLocal)
        // Local decision fields and alias MUST be preserved
        assertEquals("ASSIGN", updatedLocal.decisionCode)
        assertEquals("migros", updatedLocal.merchantId)
        assertEquals("MIGROS", updatedLocal.normalizedAlias)
        // Only syncStatus and error must be updated
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)
        assertEquals(RemoteSyncDao.CONFLICT_ERROR, updatedLocal.sync.lastSyncError)

        // Outbox operation is preserved untouched
        val storedOp = syncOperationDao.getById(SCOPE_A, "op-valid")
        assertNotNull(storedOp)
        assertEquals(op.payloadJson, storedOp.payloadJson)
        assertEquals("PENDING", storedOp.statusCode)

        // Conflict row is written
        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(storedConflict)
        assertEquals("op-valid", storedConflict.operationId)
        assertEquals(2L, storedConflict.remoteVersion)

        // Cursor is advanced
        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2500L, storedCursor.updatedAtEpochMillis)
        assertEquals(local.id, storedCursor.entityId)
    }

    @Test
    fun recordConflict_expectedLocalEntityLocalUpdatedAtMismatch_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-valid",
            entityId = local.id,
            statusCode = "PENDING",
            isBlocked = false,
            predecessorOperationId = null,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-valid",
            remoteVersion = 2L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-valid")

        // expectedLocalEntity Room'daki güncel entity ile yalnız localUpdatedAtEpochMillis alanında farklı
        val mismatchedExpectedLocal = local.copy(
            sync = local.sync.copy(
                localUpdatedAtEpochMillis = local.sync.localUpdatedAtEpochMillis + 10_000L,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(
                SCOPE_A,
                conflict,
                remoteEntity,
                cursor,
                mismatchedExpectedLocal,
            )
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-valid"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_definitiveFailedOutbox_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-failed",
            entityId = local.id,
            statusCode = "FAILED",
            errorClassification = OutboxErrorClassification.DEFINITIVE_REJECTION.name,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-failed", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-failed")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-failed"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_protocolVersionNot2_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-proto1",
            entityId = local.id,
            protocolVersion = 1,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-proto1", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-proto1")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-proto1"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_terminalStatus_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-completed",
            entityId = local.id,
            statusCode = "COMPLETED",
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-completed", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-completed")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-completed"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_wrongOperationTypeCode_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // Local is PENDING_UPDATE but outbox is CREATE
        val op = sampleOperation(
            operationId = "op-wrongtype",
            entityId = local.id,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-wrongtype", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-wrongtype")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-wrongtype"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_outboxBaseVersionMismatch_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // Local has baseVersion = 1L, but outbox has baseVersion = 99L
        val op = sampleOperation(
            operationId = "op-basever",
            entityId = local.id,
            baseVersion = 99L,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-basever", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-basever")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-basever"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_nonCanonicalOrEntityMismatchedPayload_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L, alias = "MIGROS")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // Payload has mismatched alias
        val mismatchedEntity = local.copy(
            normalizedAlias = "CARREFOUR",
            id = UuidHelper.deterministicMerchantVerificationUuid(local.ownerId, "CARREFOUR"),
        )
        val op = sampleOperation(
            operationId = "op-mismatch",
            entityId = local.id,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(mismatchedEntity),
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-mismatch", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-mismatch")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-mismatch"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_localFailedSyncStatus_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "FAILED", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-failed-status", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-failed-status", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-failed-status")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-failed-status"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_conflictLocalVersionMismatch_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-live", localVersion = 99L, remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-live")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-live"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_conflictRemoteVersionMismatch_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-live", localVersion = 1L, remoteVersion = 99L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-live")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-live"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_cursorTimestampMismatchWithRemote_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-live", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 9999L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-live")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-live"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_blankLocalOrRemotePayloadJson_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val blankLocalConflict = sampleConflict(
            entityId = local.id,
            operationId = "op-live",
            remoteVersion = 2L,
            localPayloadJson = "",
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-live")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, blankLocalConflict, remoteEntity, cursor, local)
        }

        val blankRemoteConflict = sampleConflict(
            entityId = local.id,
            operationId = "op-live",
            remoteVersion = 2L,
            remotePayloadJson = "   ",
        )
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, blankRemoteConflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-live"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_wrongOperationId_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-actual", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-mismatched", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-actual")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-actual"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_missingLiveOutbox_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // No outbox row inserted!

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-orphan", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        val stored = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(stored)
        assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_remoteVersionLessThanOrEqualToBaseVersion_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id, baseVersion = 2L)
        syncOperationDao.insert(op)

        val remoteEntityEqual = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflictEqual = sampleConflict(entityId = local.id, operationId = "op-live", localVersion = 2L, remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflictEqual, remoteEntityEqual, cursor, local)
        }

        val remoteEntityLower = sampleRemoteEntity(version = 1L, baseVersion = 1L, updatedAtEpochMillis = 2000L)
        val conflictLower = sampleConflict(entityId = local.id, operationId = "op-live", localVersion = 2L, remoteVersion = 1L)
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflictLower, remoteEntityLower, cursor, local)
        }

        val stored = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(stored)
        assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_syncedLocalEntity_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-live", entityId = local.id)
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = local.id, operationId = "op-live", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-live")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-live"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_validPendingCreateWithLiveV2CanonicalOutbox_atomicallyRecordsConflict() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "PENDING_CREATE",
            version = 0L,
            baseVersion = null,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeCreate(local)
        val op = sampleOperation(
            operationId = "op-create-valid",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = null,
            operationTypeCode = "CREATE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 2000L,
        )
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-create-valid",
            localVersion = 0L,
            remoteVersion = 1L,
            detectedAtEpochMillis = 2000L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)

        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(updatedLocal)
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)
        assertEquals(0L, updatedLocal.sync.version)
        assertNull(updatedLocal.sync.baseVersion)
        assertEquals(RemoteSyncDao.CONFLICT_ERROR, updatedLocal.sync.lastSyncError)

        val storedOp = syncOperationDao.getById(SCOPE_A, "op-create-valid")
        assertNotNull(storedOp)
        assertEquals("PENDING", storedOp.statusCode)

        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(storedConflict)
        assertEquals("op-create-valid", storedConflict.operationId)
        assertEquals(1L, storedConflict.remoteVersion)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals(local.id, storedCursor.entityId)
    }

    @Test
    fun recordConflict_validPendingDeleteWithLiveV2CanonicalOutbox_atomicallyRecordsConflict() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "PENDING_DELETE",
            version = 2L,
            baseVersion = 2L,
            deletedAtEpochMillis = 1500L,
            updatedAtEpochMillis = 1500L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeDelete(local)
        val op = sampleOperation(
            operationId = "op-delete-valid",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = 2L,
            operationTypeCode = "DELETE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(
            version = 3L,
            baseVersion = 3L,
            updatedAtEpochMillis = 2500L,
        )
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-delete-valid",
            localVersion = 2L,
            remoteVersion = 3L,
            detectedAtEpochMillis = 2500L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2500L)

        remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)

        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(updatedLocal)
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)
        assertEquals(2L, updatedLocal.sync.version)
        assertEquals(2L, updatedLocal.sync.baseVersion)
        assertEquals(RemoteSyncDao.CONFLICT_ERROR, updatedLocal.sync.lastSyncError)

        val storedOp = syncOperationDao.getById(SCOPE_A, "op-delete-valid")
        assertNotNull(storedOp)
        assertEquals("PENDING", storedOp.statusCode)

        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(storedConflict)
        assertEquals("op-delete-valid", storedConflict.operationId)
        assertEquals(3L, storedConflict.remoteVersion)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2500L, storedCursor.updatedAtEpochMillis)
        assertEquals(local.id, storedCursor.entityId)
    }

    @Test
    fun recordConflict_pendingUpdate_localVersionLessThanBaseVersion_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 2L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(local)
        val op = sampleOperation(
            operationId = "op-reg",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = 2L,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 3L, baseVersion = 3L, updatedAtEpochMillis = 2500L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-reg",
            localVersion = 1L,
            remoteVersion = 3L,
            detectedAtEpochMillis = 2500L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-reg")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-reg"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_pendingDelete_localVersionLessThanBaseVersion_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "PENDING_DELETE",
            version = 1L,
            baseVersion = 2L,
            deletedAtEpochMillis = 1500L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeDelete(local)
        val op = sampleOperation(
            operationId = "op-del-reg",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = 2L,
            operationTypeCode = "DELETE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 3L, baseVersion = 3L, updatedAtEpochMillis = 2500L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-del-reg",
            localVersion = 1L,
            remoteVersion = 3L,
            detectedAtEpochMillis = 2500L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-del-reg")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-del-reg"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_pendingCreate_localVersionNonZero_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "PENDING_CREATE",
            version = 1L,
            baseVersion = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeCreate(local)
        val op = sampleOperation(
            operationId = "op-create-ver",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = null,
            operationTypeCode = "CREATE",
            statusCode = "PENDING",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-create-ver",
            localVersion = 1L,
            remoteVersion = 2L,
            detectedAtEpochMillis = 2000L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-create-ver")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-create-ver"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_conflictStatus_createWithNonZeroVersion_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "CONFLICT",
            version = 1L,
            baseVersion = null,
            lastSyncError = "CONFLICT",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeCreate(local)
        val op = sampleOperation(
            operationId = "op-conf-create",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = null,
            operationTypeCode = "CREATE",
            statusCode = "CONFLICT",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-conf-create",
            localVersion = 1L,
            remoteVersion = 2L,
            detectedAtEpochMillis = 2000L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-conf-create")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-conf-create"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun recordConflict_conflictStatus_updateWithBaseVersionGreaterThanVersion_rejectsAndRollsBack() = runTest {
        val local = sampleRemoteEntity(
            syncStatus = "CONFLICT",
            version = 1L,
            baseVersion = 2L,
            lastSyncError = "CONFLICT",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val canonicalPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(local)
        val op = sampleOperation(
            operationId = "op-conf-upd",
            entityId = local.id,
            payloadJson = canonicalPayload,
            baseVersion = 2L,
            operationTypeCode = "UPDATE",
            statusCode = "CONFLICT",
            protocolVersion = 2,
        )
        syncOperationDao.insert(op)

        val remoteEntity = sampleRemoteEntity(version = 3L, baseVersion = 3L, updatedAtEpochMillis = 2500L)
        val conflict = sampleConflict(
            entityId = local.id,
            operationId = "op-conf-upd",
            localVersion = 1L,
            remoteVersion = 3L,
            detectedAtEpochMillis = 2500L,
            localPayloadJson = sampleSnapshotJson(local),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = local.id, updatedAtEpochMillis = 2500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-conf-upd")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, local)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(local.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-conf-upd"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // -------------------------------------------------------------------------
    // Isolation & Invariant Tests (15 to 23)
    // -------------------------------------------------------------------------

    @Test
    fun isolation_ownerMismatchWithActorScope_rejectsAndRollsBack() = runTest {
        val foreignRemote = sampleRemoteEntity(userId = USER_B_ID, scopeKey = "user:$USER_B_ID")
        val cursor = sampleCursor(syncScopeKey = SCOPE_A, entityId = foreignRemote.id)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, foreignRemote, cursor)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(foreignRemote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun isolation_scopeKeyMismatch_rejectsAndRollsBack() = runTest {
        val wrongScopeRemote = sampleRemoteEntity(scopeKey = "workspace:ws-custom")
        val cursor = sampleCursor(entityId = wrongScopeRemote.id)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, wrongScopeRemote, cursor)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(wrongScopeRemote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun isolation_nonNullWorkspaceIdOrNonPersonalScope_rejectsAndRollsBack() = runTest {
        val withWorkspace = sampleRemoteEntity(workspaceId = "00000000-0000-4000-8000-000000000001")
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                withWorkspace,
                sampleCursor(entityId = withWorkspace.id),
            )
        }

        val withWrongScopeCode = sampleRemoteEntity(scopeCode = "WORKSPACE")
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                withWrongScopeCode,
                sampleCursor(entityId = withWrongScopeCode.id),
            )
        }

        assertTrue(remoteSyncDao.getUserMerchantVerificationRow(withWorkspace.id) == null)
    }

    @Test
    fun isolation_deterministicIdMismatch_rejectsAndRollsBack() = runTest {
        val nonDeterministicRemote = sampleRemoteEntity(
            id = "00000000-0000-4000-8000-999999999999",
            alias = "MIGROS",
        )
        val cursor = sampleCursor(entityId = nonDeterministicRemote.id)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, nonDeterministicRemote, cursor)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(nonDeterministicRemote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun isolation_invalidAssignOrBlockInvariants_rejectsAndRollsBack() = runTest {
        // ASSIGN with null merchantId
        val invalidAssign = sampleRemoteEntity(decisionCode = "ASSIGN", merchantId = null)
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                invalidAssign,
                sampleCursor(entityId = invalidAssign.id),
            )
        }

        // BLOCK with non-null merchantId
        val invalidBlock = sampleRemoteEntity(decisionCode = "BLOCK", merchantId = "migros")
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                invalidBlock,
                sampleCursor(entityId = invalidBlock.id),
            )
        }

        // Unknown decision code
        val unknownDecision = sampleRemoteEntity(decisionCode = "SKIP")
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                unknownDecision,
                sampleCursor(entityId = unknownDecision.id),
            )
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(invalidAssign.id))
    }

    @Test
    fun isolation_cursorScopeMismatch_rejectsAndRollsBack() = runTest {
        val remote = sampleRemoteEntity()
        val cursorMismatchedScope = sampleCursor(syncScopeKey = SCOPE_B, entityId = remote.id)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursorMismatchedScope)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(remote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
        assertNull(syncStateDao.getCursor(SCOPE_B, ENTITY_TYPE))
    }

    @Test
    fun isolation_cursorEntityTypeCodeMismatch_rejectsAndRollsBack() = runTest {
        val remote = sampleRemoteEntity()
        val cursorWrongType = sampleCursor(entityTypeCode = "TRANSACTION", entityId = remote.id)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursorWrongType)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(remote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun isolation_cursorEntityIdOrTimestampMismatch_rejectsAndRollsBack() = runTest {
        val remote = sampleRemoteEntity(updatedAtEpochMillis = 1000L)

        // ID mismatch
        val cursorWrongId = sampleCursor(entityId = "00000000-0000-4000-8000-ffffffffffff", updatedAtEpochMillis = 1000L)
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursorWrongId)
        }

        // Timestamp mismatch
        val cursorWrongTimestamp = sampleCursor(entityId = remote.id, updatedAtEpochMillis = 9999L)
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, remote, cursorWrongTimestamp)
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(remote.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun isolation_invalidRemoteSyncMetadata_rejectsAndRollsBack() = runTest {
        val base = sampleRemoteEntity()

        // 1. Non-SYNCED remote status
        val nonSynced = base.copy(sync = base.sync.copy(syncStatus = "PENDING_UPDATE"))
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, nonSynced, sampleCursor(entityId = nonSynced.id))
        }

        // 2. Version 0
        val zeroVersion = base.copy(sync = base.sync.copy(version = 0L, baseVersion = 0L))
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, zeroVersion, sampleCursor(entityId = zeroVersion.id))
        }

        // 3. baseVersion != version
        val mismatchedBaseVersion = base.copy(sync = base.sync.copy(baseVersion = 99L))
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                mismatchedBaseVersion,
                sampleCursor(entityId = mismatchedBaseVersion.id),
            )
        }

        // 4. lastSyncError is non-null
        val withError = base.copy(sync = base.sync.copy(lastSyncError = "FAILED"))
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(SCOPE_A, withError, sampleCursor(entityId = withError.id))
        }

        // 5. Disallowed generic token (e.g. POS)
        val genericTokenAlias = "POS"
        val genericTokenId = "00000000-0000-4000-8000-000000000099"
        val genericTokenEntity = sampleRemoteEntity(alias = genericTokenAlias, id = genericTokenId)
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyUserMerchantVerificationPull(
                SCOPE_A,
                genericTokenEntity,
                sampleCursor(entityId = genericTokenEntity.id),
            )
        }

        assertNull(remoteSyncDao.getUserMerchantVerificationRow(base.id))
    }

    @Test
    fun chain_inFlightPredecessorAndBlockedSuccessor_conflictAttachesToSuccessorTail() = runTest {
        val baseEntity = sampleRemoteEntity(
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            decisionCode = "BLOCK",
            merchantId = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        val predPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(
            sampleRemoteEntity(id = baseEntity.id, version = 1L, baseVersion = 1L, decisionCode = "ASSIGN", merchantId = "migros")
        )
        val predOp = sampleOperation(
            operationId = "op-pred",
            entityId = baseEntity.id,
            statusCode = "IN_FLIGHT",
            isBlocked = false,
            predecessorOperationId = null,
            payloadJson = predPayload,
        )
        val succPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(baseEntity)
        val succOp = sampleOperation(
            operationId = "op-succ",
            entityId = baseEntity.id,
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-pred",
            payloadJson = succPayload,
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(
            entityId = baseEntity.id,
            operationId = "op-succ",
            remoteVersion = 2L,
            localPayloadJson = sampleSnapshotJson(baseEntity),
            remotePayloadJson = sampleSnapshotJson(remoteEntity),
        )
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)

        // Local entity: syncStatus is CONFLICT, decision fields preserved
        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        assertNotNull(updatedLocal)
        assertEquals("BLOCK", updatedLocal.decisionCode)
        assertNull(updatedLocal.merchantId)
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)
        assertEquals(RemoteSyncDao.CONFLICT_ERROR, updatedLocal.sync.lastSyncError)

        // Both outbox operations are preserved untouched
        val storedPred = syncOperationDao.getById(SCOPE_A, "op-pred")
        assertNotNull(storedPred)
        assertEquals("IN_FLIGHT", storedPred.statusCode)
        assertEquals(false, storedPred.isBlocked)
        assertNull(storedPred.predecessorOperationId)

        val storedSucc = syncOperationDao.getById(SCOPE_A, "op-succ")
        assertNotNull(storedSucc)
        assertEquals("PENDING", storedSucc.statusCode)
        assertEquals(true, storedSucc.isBlocked)
        assertEquals("op-pred", storedSucc.predecessorOperationId)

        // Conflict is attached to successor active tail, NOT predecessor
        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id)
        assertNotNull(storedConflict)
        assertEquals("op-succ", storedConflict.operationId)
        assertTrue(storedConflict.operationId != "op-pred")
        assertEquals(2L, storedConflict.remoteVersion)

        // Cursor is advanced
        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals(baseEntity.id, storedCursor.entityId)
    }

    @Test
    fun chain_inFlightPredecessorAndBlockedSuccessor_advanceCursorWhenRemoteVersionNotGreater() = runTest {
        val baseEntity = sampleRemoteEntity(
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            decisionCode = "BLOCK",
            merchantId = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        val predPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(
            sampleRemoteEntity(id = baseEntity.id, version = 1L, baseVersion = 1L, decisionCode = "ASSIGN", merchantId = "migros")
        )
        val predOp = sampleOperation(
            operationId = "op-pred",
            entityId = baseEntity.id,
            statusCode = "IN_FLIGHT",
            isBlocked = false,
            predecessorOperationId = null,
            payloadJson = predPayload,
        )
        val succPayload = UserMerchantVerificationPayloadCodec.encodeUpdate(baseEntity)
        val succOp = sampleOperation(
            operationId = "op-succ",
            entityId = baseEntity.id,
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-pred",
            payloadJson = succPayload,
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        // Remote version <= baseVersion (both 1L)
        val remoteEntity = sampleRemoteEntity(version = 1L, baseVersion = 1L, updatedAtEpochMillis = 1500L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 1500L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotPred = syncOperationDao.getById(SCOPE_A, "op-pred")
        val snapshotSucc = syncOperationDao.getById(SCOPE_A, "op-succ")

        remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)

        // Entity, predecessor, and successor are unchanged
        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotPred, syncOperationDao.getById(SCOPE_A, "op-pred"))
        assertEquals(snapshotSucc, syncOperationDao.getById(SCOPE_A, "op-succ"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))

        // Only cursor advanced
        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(1500L, storedCursor.updatedAtEpochMillis)
        assertEquals(baseEntity.id, storedCursor.entityId)
    }

    @Test
    fun topology_multipleActiveTails_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        // Two unblocked root operations -> 2 active tails
        val op1 = sampleOperation(operationId = "op-tail1", entityId = baseEntity.id)
        val op2 = sampleOperation(operationId = "op-tail2", entityId = baseEntity.id)
        syncOperationDao.insert(op1)
        syncOperationDao.insert(op2)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-tail1", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotOp1 = syncOperationDao.getById(SCOPE_A, "op-tail1")
        val snapshotOp2 = syncOperationDao.getById(SCOPE_A, "op-tail2")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotOp1, syncOperationDao.getById(SCOPE_A, "op-tail1"))
        assertEquals(snapshotOp2, syncOperationDao.getById(SCOPE_A, "op-tail2"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun topology_orphanBlockedSuccessor_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        // Successor points to a non-existent predecessor
        val succOp = sampleOperation(
            operationId = "op-orphan-succ",
            entityId = baseEntity.id,
            isBlocked = true,
            predecessorOperationId = "op-non-existent",
        )
        syncOperationDao.insert(succOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-orphan-succ", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-orphan-succ")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-orphan-succ"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun topology_blockedRoot_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        // Root operation (predecessorOperationId == null) but isBlocked == true
        val blockedRootOp = sampleOperation(
            operationId = "op-blocked-root",
            entityId = baseEntity.id,
            isBlocked = true,
            predecessorOperationId = null,
        )
        syncOperationDao.insert(blockedRootOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-blocked-root", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotOp = syncOperationDao.getById(SCOPE_A, "op-blocked-root")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotOp, syncOperationDao.getById(SCOPE_A, "op-blocked-root"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun topology_unblockedSuccessorWithPredecessor_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        val predOp = sampleOperation(
            operationId = "op-pred",
            entityId = baseEntity.id,
            statusCode = "IN_FLIGHT",
            isBlocked = false,
            predecessorOperationId = null,
        )
        // Successor has predecessorOperationId != null but isBlocked == false
        val unblockedSuccOp = sampleOperation(
            operationId = "op-unblocked-succ",
            entityId = baseEntity.id,
            isBlocked = false,
            predecessorOperationId = "op-pred",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(unblockedSuccOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-unblocked-succ", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotPred = syncOperationDao.getById(SCOPE_A, "op-pred")
        val snapshotSucc = syncOperationDao.getById(SCOPE_A, "op-unblocked-succ")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotPred, syncOperationDao.getById(SCOPE_A, "op-pred"))
        assertEquals(snapshotSucc, syncOperationDao.getById(SCOPE_A, "op-unblocked-succ"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun topology_predecessorTerminalFailed_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        // Predecessor is terminal/FAILED
        val predOp = sampleOperation(
            operationId = "op-pred-failed",
            entityId = baseEntity.id,
            statusCode = "FAILED",
            isBlocked = false,
            predecessorOperationId = null,
        )
        val succOp = sampleOperation(
            operationId = "op-succ",
            entityId = baseEntity.id,
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-pred-failed",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-succ", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotPred = syncOperationDao.getById(SCOPE_A, "op-pred-failed")
        val snapshotSucc = syncOperationDao.getById(SCOPE_A, "op-succ")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotPred, syncOperationDao.getById(SCOPE_A, "op-pred-failed"))
        assertEquals(snapshotSucc, syncOperationDao.getById(SCOPE_A, "op-succ"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    @Test
    fun topology_predecessorProtocolVersionNot2_failsClosedAndRollsBack() = runTest {
        val baseEntity = sampleRemoteEntity(syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(baseEntity)

        // Predecessor has protocolVersion = 1
        val predOp = sampleOperation(
            operationId = "op-pred-proto1",
            entityId = baseEntity.id,
            statusCode = "IN_FLIGHT",
            isBlocked = false,
            predecessorOperationId = null,
            protocolVersion = 1,
        )
        val succOp = sampleOperation(
            operationId = "op-succ",
            entityId = baseEntity.id,
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-pred-proto1",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        val remoteEntity = sampleRemoteEntity(version = 2L, baseVersion = 2L, updatedAtEpochMillis = 2000L)
        val conflict = sampleConflict(entityId = baseEntity.id, operationId = "op-succ", remoteVersion = 2L)
        val cursor = sampleCursor(entityId = baseEntity.id, updatedAtEpochMillis = 2000L)

        val snapshotEntity = remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id)
        val snapshotPred = syncOperationDao.getById(SCOPE_A, "op-pred-proto1")
        val snapshotSucc = syncOperationDao.getById(SCOPE_A, "op-succ")

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordUserMerchantVerificationPullConflict(SCOPE_A, conflict, remoteEntity, cursor, baseEntity)
        }
        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.advanceUserMerchantVerificationPullCursor(SCOPE_A, remoteEntity, cursor)
        }

        assertEquals(snapshotEntity, remoteSyncDao.getUserMerchantVerificationRow(baseEntity.id))
        assertEquals(snapshotPred, syncOperationDao.getById(SCOPE_A, "op-pred-proto1"))
        assertEquals(snapshotSucc, syncOperationDao.getById(SCOPE_A, "op-succ"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, baseEntity.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }
}
