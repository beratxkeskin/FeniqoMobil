package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserScopedRemoteSyncDaoTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var remoteSyncDao: RemoteSyncDao
    private lateinit var syncOperationDao: SyncOperationDao

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"
        const val USER_OWNER_ID = "00000000-0000-4000-8000-000000000001"

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
        val SCOPE_LEGACY = SyncScopeKey.UNRESOLVED_RAW
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

        remoteSyncDao = database.remoteSyncDao()
        syncOperationDao = database.syncOperationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createOperation(
        operationId: String,
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
        operationTypeCode: String = "UPDATE",
        createdAtEpochMs: Long = 1000L,
        updatedAtEpochMs: Long = 1000L,
        statusCode: String = "PENDING",
        predecessorId: String? = null,
        payloadJson: String? = "{\"id\":\"$entityId\"}",
    ): SyncOperationEntity = SyncOperationEntity(
        operationId = operationId,
        syncScopeKey = syncScopeKey,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationTypeCode = operationTypeCode,
        payloadJson = payloadJson,
        baseVersion = 1L,
        statusCode = statusCode,
        attemptCount = 0,
        lastError = null,
        nextAttemptAtEpochMillis = 0L,
        createdAtEpochMillis = createdAtEpochMs,
        updatedAtEpochMillis = updatedAtEpochMs,
        isBlocked = false,
        predecessorOperationId = predecessorId,
        protocolVersion = 2,
    )

    private fun createConflict(
        syncScopeKey: String,
        entityTypeCode: String,
        entityId: String,
        operationId: String,
    ): SyncConflictEntity = SyncConflictEntity(
        syncScopeKey = syncScopeKey,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationId = operationId,
        localVersion = 1L,
        remoteVersion = 2L,
        localPayloadJson = "{\"id\":\"$entityId\",\"v\":1}",
        remotePayloadJson = "{\"id\":\"$entityId\",\"v\":2}",
        detectedAtEpochMillis = 1000L,
    )

    private fun sampleWorkspace(
        id: String,
        ownerId: String = USER_OWNER_ID,
        version: Long = 1L,
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = "Workspace $id",
        normalizedName = "workspace $id",
        ownerId = ownerId,
        typeCode = "shared",
        currencyCode = "TRY",
        description = null,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "CONFLICT",
            version = version,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    private fun sampleCategory(
        id: String,
        ownerId: String = USER_A_ID,
        name: String = "Test Category",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
    ): CategoryEntity = CategoryEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        scopeKey = "user:$ownerId",
        name = name,
        normalizedName = name.lowercase(),
        slug = null,
        typeCode = "EXPENSE",
        colorHex = "#123456",
        iconKey = null,
        isDefault = false,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            version = version,
            baseVersion = version,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    private fun sampleProfile(
        id: String,
        fullName: String = "Test Profile",
    ): UserProfileEntity = UserProfileEntity(
        id = id,
        email = "test@example.com",
        fullName = fullName,
        currencyCode = "TRY",
        themeCode = "dark",
        languageCode = "tr",
        activeWorkspaceId = null,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    private fun sampleAsset(
        id: String,
        ownerId: String = USER_A_ID,
        name: String = "Test Asset",
    ): AssetEntity = AssetEntity(
        id = id,
        ownerId = ownerId,
        name = name,
        typeCode = "CASH",
        currentValueMinor = 10000L,
        currencyCode = "TRY",
        quantityUnscaled = null,
        quantityScale = null,
        purchaseUnitPriceMinor = null,
        trackingSymbol = null,
        autoTrack = false,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String,
        userId: String,
        roleCode: String = "MEMBER",
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = roleCode,
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    private fun sampleTransaction(
        id: String,
        ownerId: String = USER_A_ID,
        amountMinor: Long = 1000L,
    ): TransactionEntity = TransactionEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        typeCode = "EXPENSE",
        categoryId = "cat-1",
        description = "Test Tx",
        searchText = "test tx",
        paymentMethodCode = "CASH",
        transactionDate = "2026-09-25",
        receiptPath = null,
        installmentNumber = null,
        totalInstallments = null,
        installmentGroupId = null,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            lastSyncError = null,
        ),
    )

    @Test
    fun getFirstOutboxOperationId_forUserB_doesNotReturnUserAOperation() = runTest {
        val opA = createOperation("op-a", SCOPE_A, "PROFILE", "prof-1", createdAtEpochMs = 1000L)
        val opB = createOperation("op-b", SCOPE_B, "PROFILE", "prof-1", createdAtEpochMs = 2000L)
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val firstB = remoteSyncDao.getFirstOutboxOperationId(SCOPE_B, "PROFILE", "prof-1")
        val firstA = remoteSyncDao.getFirstOutboxOperationId(SCOPE_A, "PROFILE", "prof-1")

        assertEquals("op-b", firstB)
        assertEquals("op-a", firstA)
    }

    @Test
    fun activeWorkspaceTail_forUserB_ignoresNewerUserAOperation() = runTest {
        val wsId = "ws-shared-1"
        val opB = createOperation("op-b", SCOPE_B, "WORKSPACE", wsId, createdAtEpochMs = 1000L)
        val opA = createOperation("op-a", SCOPE_A, "WORKSPACE", wsId, createdAtEpochMs = 2000L)
        syncOperationDao.insert(opB)
        syncOperationDao.insert(opA)

        val tailB = remoteSyncDao.getActiveWorkspaceTailOperation(SCOPE_B, wsId)

        assertNotNull(tailB)
        assertEquals("op-b", tailB.operationId)
        assertEquals(SCOPE_B, tailB.syncScopeKey)
    }

    @Test
    fun getAllWorkspaceOperations_returnsOnlyRequestedUserScope() = runTest {
        val wsId = "ws-shared-2"
        val opA1 = createOperation("op-a-1", SCOPE_A, "WORKSPACE", wsId, createdAtEpochMs = 1000L)
        val opA2 = createOperation("op-a-2", SCOPE_A, "WORKSPACE", wsId, createdAtEpochMs = 1200L)
        val opB1 = createOperation("op-b-1", SCOPE_B, "WORKSPACE", wsId, createdAtEpochMs = 1100L)
        syncOperationDao.insert(opA1)
        syncOperationDao.insert(opA2)
        syncOperationDao.insert(opB1)

        val opsB = remoteSyncDao.getAllWorkspaceOperations(SCOPE_B, wsId)
        val opsA = remoteSyncDao.getAllWorkspaceOperations(SCOPE_A, wsId)

        assertEquals(listOf("op-b-1"), opsB.map { it.operationId })
        assertEquals(listOf("op-a-1", "op-a-2"), opsA.map { it.operationId })
    }

    @Test
    fun deleteSpecificWorkspaceOperations_forUserB_preservesUserARows() = runTest {
        val wsId = "ws-shared-3"
        val opA = createOperation("op-a", SCOPE_A, "WORKSPACE", wsId)
        val opB = createOperation("op-b", SCOPE_B, "WORKSPACE", wsId)
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val deleted = remoteSyncDao.deleteSpecificWorkspaceOperations(SCOPE_B, wsId, listOf("op-a", "op-b"))
        assertEquals(1, deleted)

        val remainingA = remoteSyncDao.getAllWorkspaceOperations(SCOPE_A, wsId)
        val remainingB = remoteSyncDao.getAllWorkspaceOperations(SCOPE_B, wsId)

        assertEquals(1, remainingA.size)
        assertEquals("op-a", remainingA.first().operationId)
        assertTrue(remainingB.isEmpty())
    }

    @Test
    fun resetWorkspaceConflictOperation_forUserB_doesNotResetUserAOperation() = runTest {
        val opA = createOperation("op-a", SCOPE_A, "WORKSPACE", "ws-shared-4", statusCode = "CONFLICT")
        syncOperationDao.insert(opA)

        val rowsUpdated = remoteSyncDao.resetWorkspaceConflictOperation(
            syncScopeKey = SCOPE_B,
            operationId = "op-a",
            operationTypeCode = "UPDATE",
            payloadJson = "{\"reset\":true}",
            remoteVersion = 5L,
            nowEpochMillis = 3000L,
        )
        assertEquals(0, rowsUpdated)

        val freshOpA = remoteSyncDao.getAllWorkspaceOperations(SCOPE_A, "ws-shared-4").first()
        assertEquals("CONFLICT", freshOpA.statusCode)
        assertEquals(1000L, freshOpA.updatedAtEpochMillis)
        assertEquals(1L, freshOpA.baseVersion)
    }

    @Test
    fun getConflictRow_forUserB_doesNotReturnUserAConflict() = runTest {
        val entityId = "ws-shared-5"
        val confA = createConflict(SCOPE_A, "WORKSPACE", entityId, "op-a")
        remoteSyncDao.upsertConflictRow(confA)

        val conflictB = remoteSyncDao.getConflictRow(SCOPE_B, "WORKSPACE", entityId)
        val conflictA = remoteSyncDao.getConflictRow(SCOPE_A, "WORKSPACE", entityId)

        assertNull(conflictB)
        assertNotNull(conflictA)
        assertEquals("op-a", conflictA.operationId)
    }

    @Test
    fun countOutboxRows_countsOnlyRequestedUserScope() = runTest {
        val entityId = "cat-shared-1"
        syncOperationDao.insert(createOperation("op-a-1", SCOPE_A, "CATEGORY", entityId))
        syncOperationDao.insert(createOperation("op-a-2", SCOPE_A, "CATEGORY", entityId))
        syncOperationDao.insert(createOperation("op-a-3", SCOPE_A, "CATEGORY", entityId))
        syncOperationDao.insert(createOperation("op-b-1", SCOPE_B, "CATEGORY", entityId))

        val countA = remoteSyncDao.countOutboxRows(SCOPE_A, "CATEGORY", entityId)
        val countB = remoteSyncDao.countOutboxRows(SCOPE_B, "CATEGORY", entityId)

        assertEquals(3, countA)
        assertEquals(1, countB)
    }

    @Test
    fun deleteOutboxRows_forUserB_preservesUserARows() = runTest {
        val entityId = "cat-shared-2"
        syncOperationDao.insert(createOperation("op-a-1", SCOPE_A, "CATEGORY", entityId))
        syncOperationDao.insert(createOperation("op-b-1", SCOPE_B, "CATEGORY", entityId))

        val deleted = remoteSyncDao.deleteOutboxRows(SCOPE_B, "CATEGORY", entityId)
        assertEquals(1, deleted)

        assertEquals(1, remoteSyncDao.countOutboxRows(SCOPE_A, "CATEGORY", entityId))
        assertEquals(0, remoteSyncDao.countOutboxRows(SCOPE_B, "CATEGORY", entityId))
    }

    @Test
    fun workspaceConflictResolution_forUserB_doesNotDeleteOrResetUserAMetadata() = runTest {
        val wsId = "ws-conflict-res"
        val ws = sampleWorkspace(wsId)
        remoteSyncDao.upsertWorkspaceRows(listOf(ws))

        val opA = createOperation("op-a", SCOPE_A, "WORKSPACE", wsId, statusCode = "CONFLICT")
        val opB = createOperation("op-b", SCOPE_B, "WORKSPACE", wsId, statusCode = "CONFLICT")
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val confA = createConflict(SCOPE_A, "WORKSPACE", wsId, "op-a")
        val confB = createConflict(SCOPE_B, "WORKSPACE", wsId, "op-b")
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val preconditionB = WorkspaceResolutionPrecondition(
            expectedConflict = confB.toSnapshot(),
            expectedWorkspace = ws.toSnapshot(),
            expectedOperations = listOf(opB.toSnapshot()),
        )

        remoteSyncDao.resolveWorkspaceKeepRemote(
            syncScopeKey = SCOPE_B,
            precondition = preconditionB,
            remoteEntity = ws.copy(sync = ws.sync.copy(syncStatus = "SYNCED", version = 2L)),
        )

        // User B conflict and outbox row are cleared
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "WORKSPACE", wsId))
        assertTrue(remoteSyncDao.getAllWorkspaceOperations(SCOPE_B, wsId).isEmpty())

        // User A conflict and outbox row are completely preserved!
        val remainingConfA = remoteSyncDao.getConflictRow(SCOPE_A, "WORKSPACE", wsId)
        assertNotNull(remainingConfA)
        assertEquals("op-a", remainingConfA.operationId)

        val remainingOpsA = remoteSyncDao.getAllWorkspaceOperations(SCOPE_A, wsId)
        assertEquals(1, remainingOpsA.size)
        assertEquals("op-a", remainingOpsA.first().operationId)
    }

    @Test
    fun remoteSnapshotApply_forUserB_doesNotDeleteUserAOutboxForSameEntity() = runTest {
        val profileId = "00000000-0000-4000-8000-000000000099"
        val opA = createOperation("op-prof-a", SCOPE_A, "PROFILE", profileId)
        val opB = createOperation("op-prof-b", SCOPE_B, "PROFILE", profileId)
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val confB = createConflict(SCOPE_B, "PROFILE", profileId, "op-prof-b")
        remoteSyncDao.upsertConflictRow(confB)

        val profileEntity = UserProfileEntity(
            id = profileId,
            email = "test@example.com",
            fullName = "Updated Profile",
            currencyCode = "TRY",
            themeCode = "dark",
            languageCode = "tr",
            activeWorkspaceId = null,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                version = 2L,
                baseVersion = 2L,
                updatedAtEpochMillis = 2000L,
                localUpdatedAtEpochMillis = 2000L,
                deletedAtEpochMillis = null,
                lastSyncError = null,
            ),
        )

        remoteSyncDao.resolveProfileKeepRemote(
            syncScopeKey = SCOPE_B,
            entity = profileEntity,
        )

        assertEquals(0, remoteSyncDao.countOutboxRows(SCOPE_B, "PROFILE", profileId))
        assertEquals(1, remoteSyncDao.countOutboxRows(SCOPE_A, "PROFILE", profileId))
    }

    @Test
    fun workspaceOwnerA_withMutationActorB_queriesUseActorScope() = runTest {
        val wsId = "ws-owner-a-actor-b"
        val wsOwnedByA = sampleWorkspace(wsId, ownerId = USER_A_ID)
        remoteSyncDao.upsertWorkspaceRows(listOf(wsOwnedByA))

        val opB = createOperation("op-b-actor", SCOPE_B, "WORKSPACE", wsId)
        syncOperationDao.insert(opB)

        // Querying with mutation actor scope finds the operation
        val tailB = remoteSyncDao.getActiveWorkspaceTailOperation(SCOPE_B, wsId)
        assertNotNull(tailB)
        assertEquals("op-b-actor", tailB.operationId)

        // Querying with workspace owner scope returns null (proving workspace ownerId is not scope authority)
        val tailA = remoteSyncDao.getActiveWorkspaceTailOperation(SCOPE_A, wsId)
        assertNull(tailA)
    }

    @Test
    fun legacyUnresolvedRows_areUnaffectedByNormalUserScopeMetadataOperations() = runTest {
        val entityId = "debt-legacy-1"
        val opLegacy = createOperation("op-legacy", SCOPE_LEGACY, "DEBT", entityId)
        syncOperationDao.insert(opLegacy)

        val confLegacy = createConflict(SCOPE_LEGACY, "DEBT", entityId, "op-legacy")
        remoteSyncDao.upsertConflictRow(confLegacy)

        // Delete with User A & User B
        remoteSyncDao.deleteOutboxRows(SCOPE_A, "DEBT", entityId)
        remoteSyncDao.deleteOutboxRows(SCOPE_B, "DEBT", entityId)

        // Legacy outbox row must still exist
        val countLegacy = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM sync_operations WHERE sync_scope_key = '$SCOPE_LEGACY' AND entity_id = '$entityId'",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(1, countLegacy)

        // Legacy conflict row must still exist
        val conflictLegacy = remoteSyncDao.getConflictRow(SCOPE_LEGACY, "DEBT", entityId)
        assertNotNull(conflictLegacy)
        assertEquals("op-legacy", conflictLegacy.operationId)
    }

    @Test
    fun applyCategoryPull_forUserB_deletesOnlyUserBConflict_andPreservesUserAConflict() = runTest {
        val catId = "cat-shared-pull"
        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-a-pull",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$catId","name":"Local A"}""",
            remotePayloadJson = """{"id":"$catId","name":"Remote A"}""",
            detectedAtEpochMillis = 1000L,
        )
        val confB = SyncConflictEntity(
            syncScopeKey = SCOPE_B,
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-b-pull",
            localVersion = 2L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$catId","name":"Local B"}""",
            remotePayloadJson = """{"id":"$catId","name":"Remote B"}""",
            detectedAtEpochMillis = 2000L,
        )
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val catB = sampleCategory(id = catId, ownerId = USER_B_ID, name = "Pulled B")
        val cursorB = SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "CATEGORY", updatedAtEpochMillis = 3000L, entityId = catId)

        remoteSyncDao.applyCategoryPull(SCOPE_B, catB, cursorB)

        // B conflict is deleted
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "CATEGORY", catId))

        // A conflict is preserved with exact metadata
        val preservedA = remoteSyncDao.getConflictRow(SCOPE_A, "CATEGORY", catId)
        assertNotNull(preservedA)
        assertEquals("op-a-pull", preservedA.operationId)
        assertEquals(confA.localPayloadJson, preservedA.localPayloadJson)
        assertEquals(confA.remotePayloadJson, preservedA.remotePayloadJson)
        assertEquals(1L, preservedA.localVersion)
        assertEquals(2L, preservedA.remoteVersion)
        assertEquals(1000L, preservedA.detectedAtEpochMillis)
    }

    @Test
    fun applyCategoryWrite_forUserB_deletesOnlyUserBConflict_andPreservesUserAConflict() = runTest {
        val catId = "cat-shared-write"
        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-a-write",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$catId","name":"Local A Write"}""",
            remotePayloadJson = """{"id":"$catId","name":"Remote A Write"}""",
            detectedAtEpochMillis = 1100L,
        )
        val confB = SyncConflictEntity(
            syncScopeKey = SCOPE_B,
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-b-write",
            localVersion = 2L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$catId","name":"Local B Write"}""",
            remotePayloadJson = """{"id":"$catId","name":"Remote B Write"}""",
            detectedAtEpochMillis = 2100L,
        )
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val catB = sampleCategory(id = catId, ownerId = USER_B_ID, name = "Written B")
        remoteSyncDao.applyCategoryWrite(SCOPE_B, catB)

        // Only B conflict is deleted
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "CATEGORY", catId))

        // A conflict is byte-for-byte preserved
        val preservedA = remoteSyncDao.getConflictRow(SCOPE_A, "CATEGORY", catId)
        assertNotNull(preservedA)
        assertEquals("op-a-write", preservedA.operationId)
        assertEquals(confA.localPayloadJson, preservedA.localPayloadJson)
        assertEquals(confA.remotePayloadJson, preservedA.remotePayloadJson)
        assertEquals(1L, preservedA.localVersion)
        assertEquals(2L, preservedA.remoteVersion)
        assertEquals(1100L, preservedA.detectedAtEpochMillis)
    }

    @Test
    fun applyProfilePull_forUserB_doesNotDeleteUserAConflict() = runTest {
        val profileId = "00000000-0000-4000-8000-000000000099"
        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "PROFILE",
            entityId = profileId,
            operationId = "op-prof-a",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$profileId","name":"Profile A"}""",
            remotePayloadJson = """{"id":"$profileId","name":"Remote Profile A"}""",
            detectedAtEpochMillis = 1200L,
        )
        val confB = SyncConflictEntity(
            syncScopeKey = SCOPE_B,
            entityTypeCode = "PROFILE",
            entityId = profileId,
            operationId = "op-prof-b",
            localVersion = 2L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$profileId","name":"Profile B"}""",
            remotePayloadJson = """{"id":"$profileId","name":"Remote Profile B"}""",
            detectedAtEpochMillis = 2200L,
        )
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val profileB = sampleProfile(id = profileId, fullName = "Pulled Profile B")
        val cursorB = SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "PROFILE", updatedAtEpochMillis = 3500L, entityId = profileId)

        remoteSyncDao.applyProfilePull(SCOPE_B, profileB, cursorB)

        // B conflict is deleted
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "PROFILE", profileId))

        // A conflict is preserved
        val preservedA = remoteSyncDao.getConflictRow(SCOPE_A, "PROFILE", profileId)
        assertNotNull(preservedA)
        assertEquals("op-prof-a", preservedA.operationId)
        assertEquals(confA.localPayloadJson, preservedA.localPayloadJson)
        assertEquals(confA.remotePayloadJson, preservedA.remotePayloadJson)
        assertEquals(1L, preservedA.localVersion)
        assertEquals(2L, preservedA.remoteVersion)
        assertEquals(1200L, preservedA.detectedAtEpochMillis)
    }

    @Test
    fun assetApplyPull_forUserB_deletesOnlyUserBConflict_andPreservesUserAConflict() = runTest {
        val assetId = "asset-shared-1"
        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "ASSET",
            entityId = assetId,
            operationId = "op-asset-a",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$assetId","val":100}""",
            remotePayloadJson = """{"id":"$assetId","val":200}""",
            detectedAtEpochMillis = 1300L,
        )
        val confB = SyncConflictEntity(
            syncScopeKey = SCOPE_B,
            entityTypeCode = "ASSET",
            entityId = assetId,
            operationId = "op-asset-b",
            localVersion = 2L,
            remoteVersion = 3L,
            localPayloadJson = """{"id":"$assetId","val":300}""",
            remotePayloadJson = """{"id":"$assetId","val":400}""",
            detectedAtEpochMillis = 2300L,
        )
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val assetDao = database.assetDao()
        val assetB = sampleAsset(id = assetId, ownerId = USER_B_ID, name = "Asset B")
        val cursorB = SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "ASSET", updatedAtEpochMillis = 3600L, entityId = assetId)

        assetDao.applyPull(SCOPE_B, assetB, cursorB)

        // B conflict is deleted
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "ASSET", assetId))

        // A conflict is preserved
        val preservedA = remoteSyncDao.getConflictRow(SCOPE_A, "ASSET", assetId)
        assertNotNull(preservedA)
        assertEquals("op-asset-a", preservedA.operationId)
        assertEquals(confA.localPayloadJson, preservedA.localPayloadJson)
        assertEquals(confA.remotePayloadJson, preservedA.remotePayloadJson)
        assertEquals(1L, preservedA.localVersion)
        assertEquals(2L, preservedA.remoteVersion)
        assertEquals(1300L, preservedA.detectedAtEpochMillis)
    }

    @Test
    fun recordCategoryPullConflict_rejectsActorConflictScopeMismatch_withoutMutation() = runTest {
        val catId = "cat-mismatch-1"
        val existingCat = sampleCategory(id = catId, ownerId = USER_B_ID, name = "Existing Category", syncStatus = "SYNCED", version = 1L)
        database.categoryDao().upsert(existingCat)

        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A, // Mismatch: Actor is B, Conflict is A
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-a-mismatch",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$catId"}""",
            remotePayloadJson = """{"id":"$catId","v":2}""",
            detectedAtEpochMillis = 1400L,
        )
        val cursor = SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "CATEGORY", updatedAtEpochMillis = 9999L, entityId = catId)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordCategoryPullConflict(SCOPE_B, confA, cursor)
        }

        // Conflict was not recorded for either scope
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, "CATEGORY", catId))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "CATEGORY", catId))

        // Entity status must not have changed
        val currentCat = database.categoryDao().getByIdAndOwner(catId, USER_B_ID)
        assertNotNull(currentCat)
        assertEquals("SYNCED", currentCat.sync.syncStatus)
        assertEquals(1L, currentCat.sync.version)

        // Cursor must not have advanced
        assertNull(database.syncStateDao().getCursor(SCOPE_B, "CATEGORY"))
    }

    @Test
    fun recordCategoryPullConflict_rejectsLegacyActorScope_withoutMutation() = runTest {
        val catId = "cat-legacy-reject"
        val existingCat = sampleCategory(id = catId, ownerId = USER_A_ID, name = "Category A", syncStatus = "SYNCED", version = 1L)
        database.categoryDao().upsert(existingCat)

        val confLegacy = SyncConflictEntity(
            syncScopeKey = SCOPE_LEGACY,
            entityTypeCode = "CATEGORY",
            entityId = catId,
            operationId = "op-legacy-mismatch",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$catId"}""",
            remotePayloadJson = """{"id":"$catId","v":2}""",
            detectedAtEpochMillis = 1500L,
        )
        val cursor = SyncCursorEntity(syncScopeKey = SCOPE_LEGACY, entityTypeCode = "CATEGORY", updatedAtEpochMillis = 8888L, entityId = catId)

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.recordCategoryPullConflict(SCOPE_LEGACY, confLegacy, cursor)
        }

        // No conflict was recorded
        assertNull(remoteSyncDao.getConflictRow(SCOPE_LEGACY, "CATEGORY", catId))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, "CATEGORY", catId))

        // Entity unchanged
        val currentCat = database.categoryDao().getByIdAndOwner(catId, USER_A_ID)
        assertNotNull(currentCat)
        assertEquals("SYNCED", currentCat.sync.syncStatus)

        // Cursor unchanged
        assertNull(database.syncStateDao().getCursor(SCOPE_LEGACY, "CATEGORY"))
    }

    @Test
    fun applyWorkspaceIncrementalPlan_rejectsConflictActorScopeMismatch_beforeAnyMutation() = runTest {
        val wsId = "ws-plan-mismatch"
        val existingWs = sampleWorkspace(id = wsId, ownerId = USER_OWNER_ID, version = 1L)
        remoteSyncDao.upsertWorkspaceRows(listOf(existingWs))

        val existingMember = sampleMember(workspaceId = wsId, userId = USER_A_ID, roleCode = "OWNER")
        remoteSyncDao.upsertWorkspaceMemberRows(listOf(existingMember))

        val existingOp = createOperation("op-init", SCOPE_B, "WORKSPACE", wsId)
        syncOperationDao.insert(existingOp)

        val initialCursor = SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "WORKSPACE", updatedAtEpochMillis = 1000L, entityId = wsId)
        remoteSyncDao.upsertCursorRows(listOf(initialCursor))

        // Plan with conflict belonging to SCOPE_A, but calling with SCOPE_B
        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "WORKSPACE",
            entityId = wsId,
            operationId = "op-conflict-a",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$wsId"}""",
            remotePayloadJson = """{"id":"$wsId","v":2}""",
            detectedAtEpochMillis = 1600L,
        )

        val plan = WorkspaceIncrementalPlan(
            conflictItems = listOf(
                WorkspaceConflictItem(
                    conflict = confA,
                    precondition = WorkspacePrecondition(expectedPresence = true),
                ),
            ),
            cursorsToPersist = listOf(
                SyncCursorEntity(syncScopeKey = SCOPE_B, entityTypeCode = "WORKSPACE", updatedAtEpochMillis = 9999L, entityId = wsId),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            remoteSyncDao.applyWorkspaceIncrementalPlan(SCOPE_B, plan)
        }

        // Entire DB state must be unchanged:
        // 1. Workspace table unchanged
        val ws = remoteSyncDao.getWorkspaceRow(wsId)
        assertNotNull(ws)
        assertEquals(1L, ws.sync.version)

        // 2. Member table unchanged
        val members = remoteSyncDao.getWorkspaceMemberRows(wsId)
        assertEquals(1, members.size)
        assertEquals(USER_A_ID, members.first().userId)

        // 3. Outbox table unchanged
        val outbox = syncOperationDao.getById(SCOPE_B, "op-init")
        assertNotNull(outbox)

        // 4. Conflict table unchanged (no conflict inserted)
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, "WORKSPACE", wsId))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_B, "WORKSPACE", wsId))

        // 5. Cursor table unchanged
        val cur = database.syncStateDao().getCursor(SCOPE_B, "WORKSPACE")
        assertNotNull(cur)
        assertEquals(1000L, cur.updatedAtEpochMillis)
    }

    @Test
    fun resolveTransactionKeepRemote_rejectsNonCanonicalOrLegacyScope_withoutMutation() = runTest {
        val cat = sampleCategory(id = "cat-1", ownerId = USER_A_ID)
        database.categoryDao().upsert(cat)

        val txId = "tx-resolve-boundary"
        val existingTxA = sampleTransaction(id = txId, ownerId = USER_A_ID, amountMinor = 1000L)
        database.transactionDao().upsert(existingTxA)

        val confA = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = "TRANSACTION",
            entityId = txId,
            operationId = "op-tx-a",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$txId","amt":1000}""",
            remotePayloadJson = """{"id":"$txId","amt":2000}""",
            detectedAtEpochMillis = 1700L,
        )
        val confB = SyncConflictEntity(
            syncScopeKey = SCOPE_B,
            entityTypeCode = "TRANSACTION",
            entityId = txId,
            operationId = "op-tx-b",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"id":"$txId","amt":3000}""",
            remotePayloadJson = """{"id":"$txId","amt":4000}""",
            detectedAtEpochMillis = 2700L,
        )
        remoteSyncDao.upsertConflictRow(confA)
        remoteSyncDao.upsertConflictRow(confB)

        val opA = createOperation("op-tx-a", SCOPE_A, "TRANSACTION", txId)
        val opB = createOperation("op-tx-b", SCOPE_B, "TRANSACTION", txId)
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val invalidScopes = listOf(
            SCOPE_LEGACY,
            "USER:compact-not-a-valid-uuid",
            "invalid-prefix:00000000-0000-4000-8000-00000000000a",
            "",
        )

        val dummyTx = sampleTransaction(id = txId, ownerId = USER_B_ID, amountMinor = 5000L)

        for (invalidScope in invalidScopes) {
            assertFailsWith<IllegalArgumentException> {
                remoteSyncDao.resolveTransactionKeepRemote(invalidScope, dummyTx)
            }
        }

        // Neither A nor B metadata is touched:
        val preservedA = remoteSyncDao.getConflictRow(SCOPE_A, "TRANSACTION", txId)
        val preservedB = remoteSyncDao.getConflictRow(SCOPE_B, "TRANSACTION", txId)
        assertNotNull(preservedA)
        assertNotNull(preservedB)
        assertEquals("op-tx-a", preservedA.operationId)
        assertEquals("op-tx-b", preservedB.operationId)

        assertEquals(1, remoteSyncDao.countOutboxRows(SCOPE_A, "TRANSACTION", txId))
        assertEquals(1, remoteSyncDao.countOutboxRows(SCOPE_B, "TRANSACTION", txId))

        val currentTx = database.transactionDao().getByIdAndOwner(txId, USER_A_ID)
        assertNotNull(currentTx)
        assertEquals(1000L, currentTx.amountMinor)
    }
}
