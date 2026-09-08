package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceRemoteSyncDaoTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private fun sampleWorkspace(
        id: String = "ws-1",
        ownerId: String = "user-1",
        name: String = "Ortak Aile",
        typeCode: String = "shared",
        currencyCode: String = "TRY",
        description: String? = "Aile bütçesi",
        syncStatus: String = "SYNCED",
        version: Long = 2L,
        baseVersion: Long? = 2L,
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
            updatedAtEpochMillis = 1500L,
            localUpdatedAtEpochMillis = 1500L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String = "ws-1",
        userId: String = "user-1",
        roleCode: String = "OWNER",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
        baseVersion: Long? = 1L,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = roleCode,
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1500L,
            localUpdatedAtEpochMillis = 1500L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleOutboxOperation(
        operationId: String = "op-1",
        entityTypeCode: String = "WORKSPACE",
        entityId: String = "ws-existing-pending",
    ): SyncOperationEntity = SyncOperationEntity(
        operationId = operationId,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationTypeCode = OutboxOperationType.CREATE.name,
        payloadJson = """{"name":"Pending WS"}""",
        baseVersion = null,
        attemptCount = 0,
        lastError = null,
        nextAttemptAtEpochMillis = 1000L,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
        statusCode = "PENDING",
        protocolVersion = 2,
    )

    @Test
    fun active_workspace_and_owner_editor_members_are_applied_atomically() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val workspaceDao = db.workspaceDao()

        val workspace = sampleWorkspace(id = "ws-10", ownerId = "owner-1")
        val ownerMember = sampleMember(workspaceId = "ws-10", userId = "owner-1", roleCode = "OWNER")
        val editorMember = sampleMember(workspaceId = "ws-10", userId = "editor-2", roleCode = "EDITOR")

        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(workspace),
            members = listOf(ownerMember, editorMember),
        )

        val persistedWs = workspaceDao.getWorkspaceById("ws-10")
        assertNotNull(persistedWs)
        assertEquals("ws-10", persistedWs.id)
        assertEquals("owner-1", persistedWs.ownerId)
        assertEquals(2L, persistedWs.sync.version)

        val members = syncDao.getWorkspaceMemberRows("ws-10")
        assertEquals(2, members.size)
        assertEquals("EDITOR", members.first { it.userId == "editor-2" }.roleCode)
        assertEquals("OWNER", members.first { it.userId == "owner-1" }.roleCode)

        db.close()
    }

    @Test
    fun workspace_and_member_tombstones_are_preserved() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val workspaceDao = db.workspaceDao()

        val deletedWs = sampleWorkspace(
            id = "ws-deleted",
            ownerId = "owner-1",
            deletedAtEpochMillis = 2000L,
            version = 5L,
            baseVersion = 5L,
        )
        val deletedMember = sampleMember(
            workspaceId = "ws-deleted",
            userId = "user-deleted",
            roleCode = "VIEWER",
            deletedAtEpochMillis = 2000L,
            version = 3L,
            baseVersion = 3L,
        )

        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(deletedWs),
            members = listOf(deletedMember),
        )

        val persistedWs = workspaceDao.getWorkspaceById("ws-deleted")
        assertNotNull(persistedWs)
        assertEquals(2000L, persistedWs.sync.deletedAtEpochMillis)
        assertEquals(5L, persistedWs.sync.version)

        val persistedMember = syncDao.getWorkspaceMemberRow("ws-deleted", "user-deleted")
        assertNotNull(persistedMember)
        assertEquals(2000L, persistedMember.sync.deletedAtEpochMillis)
        assertEquals(3L, persistedMember.sync.version)

        db.close()
    }

    @Test
    fun workspace_id_mismatch_fails_closed_and_rolls_back_with_no_rows_written() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val workspaceDao = db.workspaceDao()

        val validWs = sampleWorkspace(id = "ws-valid", ownerId = "user-1")
        val orphanMember = sampleMember(workspaceId = "ws-non-existent", userId = "user-2", roleCode = "EDITOR")

        assertFailsWith<IllegalArgumentException> {
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(validWs),
                members = listOf(orphanMember),
            )
        }

        // Entire transaction should be rolled back: ws-valid should not exist, orphanMember should not exist
        assertNull(workspaceDao.getWorkspaceById("ws-valid"))
        assertNull(syncDao.getWorkspaceMemberRow("ws-non-existent", "user-2"))
        assertEquals(0, syncDao.getWorkspaceMemberRows("ws-valid").size)

        db.close()
    }

    @Test
    fun existing_v2_outbox_operations_are_preserved_and_untouched_by_snapshot_application() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncOperationDao = db.syncOperationDao()

        // 1. Snapshot uygulanacak ws-snap-1 için halihazırda var olan bir outbox kaydı ekle
        val initialOutbox = sampleOutboxOperation(
            operationId = "op-outbox-1",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-snap-1",
        )
        syncOperationDao.insert(initialOutbox)

        // Snapshot öncesi outbox satır sayısının tam olarak 1 olduğunu doğrula
        val preCount = syncDao.countOutboxRows("WORKSPACE", "ws-snap-1")
        assertEquals(1, preCount)

        val workspace = sampleWorkspace(id = "ws-snap-1", ownerId = "user-1")
        val member = sampleMember(workspaceId = "ws-snap-1", userId = "user-1", roleCode = "OWNER")

        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(workspace),
            members = listOf(member),
        )

        // 2. Snapshot sonrası outbox satır sayısının KESİNLİKLE 1 kaldığını (yeni satır eklenmediğini) doğrula
        val postCount = syncDao.countOutboxRows("WORKSPACE", "ws-snap-1")
        assertEquals(1, postCount)

        // 3. Mevcut outbox kaydının aynı entityId için bozulmadan ve mutasyona uğramadan korunduğunu doğrula
        val firstOutboxIdForWs = syncDao.getFirstOutboxOperationId("WORKSPACE", "ws-snap-1")
        assertEquals("op-outbox-1", firstOutboxIdForWs)

        val remainingOutbox = syncOperationDao.getById("op-outbox-1")
        assertNotNull(remainingOutbox)
        assertEquals("op-outbox-1", remainingOutbox.operationId)
        assertEquals("WORKSPACE", remainingOutbox.entityTypeCode)
        assertEquals("ws-snap-1", remainingOutbox.entityId)
        assertEquals(initialOutbox.operationTypeCode, remainingOutbox.operationTypeCode)
        assertEquals(initialOutbox.payloadJson, remainingOutbox.payloadJson)
        assertEquals(initialOutbox.baseVersion, remainingOutbox.baseVersion)
        assertEquals(initialOutbox.attemptCount, remainingOutbox.attemptCount)
        assertEquals(initialOutbox.lastError, remainingOutbox.lastError)
        assertEquals(initialOutbox.nextAttemptAtEpochMillis, remainingOutbox.nextAttemptAtEpochMillis)
        assertEquals(initialOutbox.createdAtEpochMillis, remainingOutbox.createdAtEpochMillis)
        assertEquals(initialOutbox.updatedAtEpochMillis, remainingOutbox.updatedAtEpochMillis)
        assertEquals("PENDING", remainingOutbox.statusCode)

        db.close()
    }

    @Test
    fun valid_snapshot_with_cursors_persists_cursors_and_entities_atomically() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()

        val workspace = sampleWorkspace(id = "ws-100", ownerId = "owner-1")
        val member = sampleMember(workspaceId = "ws-100", userId = "owner-1", roleCode = "OWNER")

        val wsCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "WORKSPACE",
            updatedAtEpochMillis = 5000L,
            entityId = "ws-100",
        )
        val memberCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "WORKSPACE_MEMBER:ws-100",
            updatedAtEpochMillis = 5000L,
            entityId = "owner-1",
        )

        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(workspace),
            members = listOf(member),
            cursors = listOf(wsCursor, memberCursor),
        )

        val persistedWsCursor = syncStateDao.getCursor("WORKSPACE")
        assertNotNull(persistedWsCursor)
        assertEquals("ws-100", persistedWsCursor.entityId)
        assertEquals(5000L, persistedWsCursor.updatedAtEpochMillis)

        val memberCursors = syncStateDao.getWorkspaceMemberCursors()
        assertEquals(1, memberCursors.size)
        assertEquals("WORKSPACE_MEMBER:ws-100", memberCursors[0].entityTypeCode)
        assertEquals("owner-1", memberCursors[0].entityId)

        db.close()
    }

    @Test
    fun failed_snapshot_rolls_back_both_entities_and_cursors() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()

        val validWs = sampleWorkspace(id = "ws-valid-1", ownerId = "owner-1")
        val orphanMember = sampleMember(workspaceId = "ws-missing", userId = "user-1", roleCode = "EDITOR")

        val wsCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "WORKSPACE",
            updatedAtEpochMillis = 6000L,
            entityId = "ws-valid-1",
        )

        assertFailsWith<IllegalArgumentException> {
            syncDao.applyWorkspaceSnapshot(
                workspaces = listOf(validWs),
                members = listOf(orphanMember),
                cursors = listOf(wsCursor),
            )
        }

        // Entire transaction rolled back: no workspace and no cursor written
        assertNull(syncDao.getWorkspaceRow("ws-valid-1"))
        assertNull(syncStateDao.getCursor("WORKSPACE"))
        assertEquals(0, syncStateDao.getWorkspaceMemberCursors().size)

        db.close()
    }

    @Test
    fun get_all_known_live_workspace_ids_returns_only_non_deleted_workspaces_in_order() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val liveWsB = sampleWorkspace(id = "ws-b", ownerId = "owner-1")
        val liveWsA = sampleWorkspace(id = "ws-a", ownerId = "owner-1")
        val deletedWs = sampleWorkspace(id = "ws-deleted", ownerId = "owner-1", deletedAtEpochMillis = 2000L)

        val memberB = sampleMember(workspaceId = "ws-b", userId = "owner-1")
        val memberA = sampleMember(workspaceId = "ws-a", userId = "owner-1")
        val memberDel = sampleMember(workspaceId = "ws-deleted", userId = "owner-1")

        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(liveWsB, liveWsA, deletedWs),
            members = listOf(memberB, memberA, memberDel),
        )

        val liveIds = syncDao.getAllKnownLiveWorkspaceIds()
        assertEquals(listOf("ws-a", "ws-b"), liveIds)

        db.close()
    }

    @Test
    fun apply_incremental_plan_fails_closed_when_precondition_workspace_modified() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val existingWs = sampleWorkspace(id = "ws-1", name = "Eski", syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
        syncDao.upsertWorkspaceRows(listOf(existingWs))

        // Precondition expects version = 2L, but local version is 1L
        val stalePrecondition = WorkspacePrecondition(
            expectedPresence = true,
            expectedSyncStatus = "SYNCED",
            expectedVersion = 2L,
            expectedBaseVersion = 1L,
            expectedDeletedAtEpochMillis = null,
            expectedLocalUpdatedAtEpochMillis = 1500L,
            expectedActiveOperationId = null,
            expectedOperationUpdatedAtEpochMillis = null,
        )

        val newWs = sampleWorkspace(id = "ws-1", name = "Yeni Remote", version = 3L)
        val plan = WorkspaceIncrementalPlan(
            applyItems = listOf(WorkspaceApplyItem(entity = newWs, precondition = stalePrecondition)),
        )

        assertFailsWith<WorkspaceSyncStalePlanException> {
            syncDao.applyWorkspaceIncrementalPlan(plan)
        }

        // Workspace must be unmodified (rollback)
        val persisted = syncDao.getWorkspaceRow("ws-1")
        assertNotNull(persisted)
        assertEquals("Eski", persisted.name)
        assertEquals(1L, persisted.sync.version)

        db.close()
    }

    @Test
    fun apply_incremental_plan_fails_closed_when_precondition_outbox_tail_modified() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncOperationDao = db.syncOperationDao()

        val existingWs = sampleWorkspace(id = "ws-1", name = "Pending Name", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        syncDao.upsertWorkspaceRows(listOf(existingWs))

        // Yerelde op-current var
        val currentOp = sampleOutboxOperation(operationId = "op-current", entityTypeCode = "WORKSPACE", entityId = "ws-1")
        syncOperationDao.insert(currentOp)

        // Precondition op-stale bekliyor
        val staleTailPrecondition = WorkspacePrecondition(
            expectedPresence = true,
            expectedSyncStatus = "PENDING_UPDATE",
            expectedVersion = 1L,
            expectedBaseVersion = 1L,
            expectedDeletedAtEpochMillis = null,
            expectedLocalUpdatedAtEpochMillis = 1500L,
            expectedActiveOperationId = "op-stale",
            expectedOperationUpdatedAtEpochMillis = 1000L,
        )

        val conflict = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-1",
            operationId = "op-stale",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"name":"Pending Name"}""",
            remotePayloadJson = """{"name":"Remote Name"}""",
            detectedAtEpochMillis = 2000L,
        )

        val plan = WorkspaceIncrementalPlan(
            conflictItems = listOf(WorkspaceConflictItem(conflict = conflict, precondition = staleTailPrecondition)),
        )

        assertFailsWith<WorkspaceSyncStalePlanException> {
            syncDao.applyWorkspaceIncrementalPlan(plan)
        }

        // Conflict kaydedilmemeli ve workspace CONFLICT durumuna geçmemeli
        val persisted = syncDao.getWorkspaceRow("ws-1")
        assertNotNull(persisted)
        assertEquals("PENDING_UPDATE", persisted.sync.syncStatus)
        assertEquals(0, db.syncStateDao().getAllConflicts().size)

        db.close()
    }

    @Test
    fun apply_incremental_plan_rejects_duplicate_workspace_across_groups() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val precondition = WorkspacePrecondition(expectedPresence = false)
        val ws = sampleWorkspace(id = "ws-dup")
        val conflict = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-dup",
            operationId = "op-1",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 2000L,
        )

        val plan = WorkspaceIncrementalPlan(
            applyItems = listOf(WorkspaceApplyItem(entity = ws, precondition = precondition)),
            conflictItems = listOf(WorkspaceConflictItem(conflict = conflict, precondition = precondition)),
        )

        assertFailsWith<IllegalArgumentException> {
            syncDao.applyWorkspaceIncrementalPlan(plan)
        }

        db.close()
    }

    @Test
    fun successful_incremental_plan_applies_conflicts_workspaces_members_and_cursors_atomically() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncOperationDao = db.syncOperationDao()
        val syncStateDao = db.syncStateDao()

        // 1. ws-conflict için local row ve outbox hazırla
        val conflictWs = sampleWorkspace(
            id = "ws-conflict",
            name = "Local Conflict Name",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        syncDao.upsertWorkspaceRows(listOf(conflictWs))
        val conflictOp = sampleOutboxOperation(
            operationId = "op-conflict",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-conflict",
        )
        syncOperationDao.insert(conflictOp)

        // 2. ws-applied için local row yok (yeni eklenecek)
        val conflictPrecondition = WorkspacePrecondition(
            expectedPresence = true,
            expectedSyncStatus = "PENDING_UPDATE",
            expectedVersion = 1L,
            expectedBaseVersion = 1L,
            expectedDeletedAtEpochMillis = null,
            expectedLocalUpdatedAtEpochMillis = 1500L,
            expectedActiveOperationId = "op-conflict",
            expectedOperationUpdatedAtEpochMillis = 1000L,
        )

        val applyPrecondition = WorkspacePrecondition(expectedPresence = false)
        val appliedWs = sampleWorkspace(id = "ws-applied", name = "Newly Applied", syncStatus = "SYNCED", version = 2L)

        val conflictEntity = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-conflict",
            operationId = "op-conflict",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"name":"Local Conflict Name"}""",
            remotePayloadJson = """{"name":"Server Name"}""",
            detectedAtEpochMillis = 3000L,
        )

        val member = sampleMember(workspaceId = "ws-conflict", userId = "user-member", roleCode = "VIEWER")

        val wsCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "WORKSPACE",
            updatedAtEpochMillis = 5000L,
            entityId = "ws-applied",
        )

        val plan = WorkspaceIncrementalPlan(
            applyItems = listOf(WorkspaceApplyItem(entity = appliedWs, precondition = applyPrecondition)),
            conflictItems = listOf(WorkspaceConflictItem(conflict = conflictEntity, precondition = conflictPrecondition)),
            memberRows = listOf(member),
            cursorsToPersist = listOf(wsCursor),
        )

        syncDao.applyWorkspaceIncrementalPlan(plan)

        // Workspace apply doğrulandı
        val persistedApplied = syncDao.getWorkspaceRow("ws-applied")
        assertNotNull(persistedApplied)
        assertEquals("Newly Applied", persistedApplied.name)

        // Workspace conflict mark ve conflict entity doğrulandı
        val persistedConflictWs = syncDao.getWorkspaceRow("ws-conflict")
        assertNotNull(persistedConflictWs)
        assertEquals("CONFLICT", persistedConflictWs.sync.syncStatus)

        val persistedConflict = syncStateDao.getConflict("ws-conflict")
        assertNotNull(persistedConflict)
        assertEquals("op-conflict", persistedConflict.operationId)

        // Member doğrulandı
        val persistedMember = syncDao.getWorkspaceMemberRow("ws-conflict", "user-member")
        assertNotNull(persistedMember)
        assertEquals("VIEWER", persistedMember.roleCode)

        // Cursor doğrulandı
        val persistedCursor = syncStateDao.getCursor("WORKSPACE")
        assertNotNull(persistedCursor)
        assertEquals("ws-applied", persistedCursor.entityId)
        assertEquals(5000L, persistedCursor.updatedAtEpochMillis)

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepRemote_success_applies_remote_entity_and_deletes_outbox_and_conflict() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-res-1", syncStatus = "CONFLICT", version = 1L)
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-1",
            operationId = "op-res-1",
            localVersion = 1L,
            remoteVersion = 3L,
            localPayloadJson = """{"name":"Local Conflict"}""",
            remotePayloadJson = """{"name":"Remote Server"}""",
            detectedAtEpochMillis = 2000L,
        )
        syncDao.upsertConflictRow(conflict)

        val outboxOp = sampleOutboxOperation(
            operationId = "op-res-1",
            entityId = "ws-res-1",
        )
        opDao.insert(outboxOp)

        val remoteEntity = sampleWorkspace(id = "ws-res-1", name = "Remote Server", syncStatus = "SYNCED", version = 3L)
        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(outboxOp.toSnapshot()),
        )

        syncDao.resolveWorkspaceKeepRemote(precondition, remoteEntity)

        // Remote entity yazıldı
        val persisted = syncDao.getWorkspaceRow("ws-res-1")
        assertNotNull(persisted)
        assertEquals("Remote Server", persisted.name)
        assertEquals("SYNCED", persisted.sync.syncStatus)
        assertEquals(3L, persisted.sync.version)

        // Outbox ve conflict temizlendi
        assertEquals(0, syncDao.getAllWorkspaceOperations("ws-res-1").size)
        assertNull(syncStateDao.getConflict("ws-res-1"))

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepRemote_fails_when_untracked_extra_operation_exists() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-res-2", syncStatus = "CONFLICT", version = 1L)
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-2",
            operationId = "op-res-2a",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 2000L,
        )
        syncDao.upsertConflictRow(conflict)

        val op1 = sampleOutboxOperation(operationId = "op-res-2a", entityId = "ws-res-2")
        val op2 = sampleOutboxOperation(operationId = "op-res-2b", entityId = "ws-res-2")
        opDao.insert(op1)
        opDao.insert(op2) // Untracked in snapshot!

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(op1.toSnapshot()), // op2 missing from snapshot
        )

        val remoteEntity = sampleWorkspace(id = "ws-res-2", name = "Remote", syncStatus = "SYNCED", version = 2L)

        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            syncDao.resolveWorkspaceKeepRemote(precondition, remoteEntity)
        }

        // Rollback: hiçbir operasyon silinmedi, çakışma duruyor
        assertEquals(2, syncDao.getAllWorkspaceOperations("ws-res-2").size)
        assertNotNull(syncStateDao.getConflict("ws-res-2"))
        assertEquals("CONFLICT", syncDao.getWorkspaceRow("ws-res-2")?.sync?.syncStatus)

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepLocal_create_to_update_retains_tail_unblocks_and_rebases_to_pending_update() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-res-3", syncStatus = "CONFLICT", version = 1L)
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-3",
            operationId = "op-create",
            localVersion = 1L,
            remoteVersion = 4L,
            localPayloadJson = """{"name":"Pending WS"}""",
            remotePayloadJson = """{"name":"Server WS"}""",
            detectedAtEpochMillis = 2000L,
        )
        syncDao.upsertConflictRow(conflict)

        val opCreate = sampleOutboxOperation(
            operationId = "op-create",
            entityId = "ws-res-3",
        ).copy(
            operationTypeCode = "CREATE",
            payloadJson = """{"name":"Pending WS","created_at":"2026-09-07T00:00:00Z"}""",
            predecessorOperationId = null,
            isBlocked = true,
        )
        opDao.insert(opCreate)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(opCreate.toSnapshot()),
        )

        val canonicalUpdatePayload = """{"id":"ws-res-3","name":"Canonical Update"}"""

        syncDao.resolveWorkspaceKeepLocal(
            precondition = precondition,
            retainedOperationId = "op-create",
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = canonicalUpdatePayload,
            nowEpochMillis = 5000L,
        )

        // op-create UPDATE'e dönüştü, canonicalUpdatePayload aldı, unblocked
        val remainingOps = syncDao.getAllWorkspaceOperations("ws-res-3")
        assertEquals(1, remainingOps.size)
        val retained = remainingOps.first()
        assertEquals("op-create", retained.operationId)
        assertEquals("UPDATE", retained.operationTypeCode)
        assertEquals(canonicalUpdatePayload, retained.payloadJson)
        assertEquals(false, retained.isBlocked)
        assertNull(retained.predecessorOperationId)
        assertEquals(4L, retained.baseVersion)
        assertEquals("PENDING", retained.statusCode)

        // Workspace PENDING_UPDATE olarak rebase edildi
        val persistedWs = syncDao.getWorkspaceRow("ws-res-3")
        assertNotNull(persistedWs)
        assertEquals("PENDING_UPDATE", persistedWs.sync.syncStatus)
        assertEquals(4L, persistedWs.sync.version)
        assertEquals(4L, persistedWs.sync.baseVersion)
        assertEquals(persistedWs.sync.version, persistedWs.sync.baseVersion)
        assertEquals(persistedWs.sync.baseVersion, retained.baseVersion)
        assertEquals(retained.baseVersion, conflict.remoteVersion)
        assertNull(persistedWs.sync.lastSyncError)

        // Conflict silindi
        assertNull(syncStateDao.getConflict("ws-res-3"))

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepLocal_multi_op_chain_retains_tail_deletes_predecessors_and_rebases() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-multi", syncStatus = "CONFLICT", version = 1L)
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-multi",
            operationId = "op-tail",
            localVersion = 1L,
            remoteVersion = 5L,
            localPayloadJson = """{"name":"Root"}""",
            remotePayloadJson = """{"name":"Server"}""",
            detectedAtEpochMillis = 2000L,
        )
        syncDao.upsertConflictRow(conflict)

        val opRoot = sampleOutboxOperation(
            operationId = "op-root",
            entityId = "ws-multi",
        ).copy(operationTypeCode = "CREATE", predecessorOperationId = null, isBlocked = false)

        val tailPayload = """{"id":"ws-multi","name":"Tail Update"}"""
        val opTail = sampleOutboxOperation(
            operationId = "op-tail",
            entityId = "ws-multi",
        ).copy(
            operationTypeCode = "UPDATE",
            payloadJson = tailPayload,
            predecessorOperationId = "op-root",
            isBlocked = true,
            createdAtEpochMillis = 2000L,
            updatedAtEpochMillis = 2000L,
        )

        opDao.insert(opRoot)
        opDao.insert(opTail)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(opRoot.toSnapshot(), opTail.toSnapshot()),
        )

        syncDao.resolveWorkspaceKeepLocal(
            precondition = precondition,
            retainedOperationId = "op-tail",
            targetOperationTypeCode = "UPDATE",
            targetPayloadJson = tailPayload,
            nowEpochMillis = 5000L,
        )

        // op-root silindi, op-tail korundu
        val remainingOps = syncDao.getAllWorkspaceOperations("ws-multi")
        assertEquals(1, remainingOps.size)
        val retained = remainingOps.first()
        assertEquals("op-tail", retained.operationId)
        assertEquals("UPDATE", retained.operationTypeCode)
        assertEquals(tailPayload, retained.payloadJson)
        assertEquals(false, retained.isBlocked)
        assertNull(retained.predecessorOperationId)
        assertEquals(5L, retained.baseVersion)
        assertEquals("PENDING", retained.statusCode)

        // Workspace PENDING_UPDATE olarak rebase edildi
        val persistedWs = syncDao.getWorkspaceRow("ws-multi")
        assertNotNull(persistedWs)
        assertEquals("PENDING_UPDATE", persistedWs.sync.syncStatus)
        assertEquals(5L, persistedWs.sync.version)
        assertEquals(5L, persistedWs.sync.baseVersion)
        assertEquals(persistedWs.sync.version, persistedWs.sync.baseVersion)
        assertEquals(persistedWs.sync.baseVersion, retained.baseVersion)
        assertEquals(retained.baseVersion, conflict.remoteVersion)
        assertNull(persistedWs.sync.lastSyncError)

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepLocal_delete_operation_rebases_to_pending_delete_and_preserves_payload() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(
            id = "ws-res-4",
            syncStatus = "CONFLICT",
            version = 1L,
            deletedAtEpochMillis = 2500L,
        )
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-4",
            operationId = "op-del",
            localVersion = 1L,
            remoteVersion = 6L,
            localPayloadJson = """{"id":"ws-res-4"}""",
            remotePayloadJson = """{"name":"Server"}""",
            detectedAtEpochMillis = 2000L,
        )
        syncDao.upsertConflictRow(conflict)

        val deleteOp = sampleOutboxOperation(
            operationId = "op-del",
            entityId = "ws-res-4",
        ).copy(
            operationTypeCode = "DELETE",
            payloadJson = """{"id":"ws-res-4"}""",
            predecessorOperationId = null,
            isBlocked = false,
        )
        opDao.insert(deleteOp)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(deleteOp.toSnapshot()),
        )

        syncDao.resolveWorkspaceKeepLocal(
            precondition = precondition,
            retainedOperationId = "op-del",
            targetOperationTypeCode = "DELETE",
            targetPayloadJson = """{"id":"ws-res-4"}""",
            nowEpochMillis = 6000L,
        )

        // Workspace PENDING_DELETE olarak rebase edildi
        val persistedWs = syncDao.getWorkspaceRow("ws-res-4")
        assertNotNull(persistedWs)
        assertEquals("PENDING_DELETE", persistedWs.sync.syncStatus)
        assertEquals(6L, persistedWs.sync.version)
        assertEquals(6L, persistedWs.sync.baseVersion)

        // Operation baseVersion güncellendi, payload byte-for-byte korundu
        val remainingOps = syncDao.getAllWorkspaceOperations("ws-res-4")
        assertEquals(1, remainingOps.size)
        assertEquals("""{"id":"ws-res-4"}""", remainingOps.first().payloadJson)
        assertEquals(6L, remainingOps.first().baseVersion)
        assertEquals(persistedWs.sync.version, persistedWs.sync.baseVersion)
        assertEquals(persistedWs.sync.baseVersion, remainingOps.first().baseVersion)
        assertEquals(remainingOps.first().baseVersion, conflict.remoteVersion)

        assertNull(syncStateDao.getConflict("ws-res-4"))

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepLocal_fails_when_retained_id_is_not_verified_tail() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-res-5", syncStatus = "CONFLICT")
        syncDao.upsertWorkspaceRows(listOf(localWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-5",
            operationId = "op-root",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 1000L,
        )
        syncDao.upsertConflictRow(conflict)

        val opRoot = sampleOutboxOperation(operationId = "op-root", entityId = "ws-res-5")
            .copy(predecessorOperationId = null)
        val opTail = sampleOutboxOperation(operationId = "op-tail", entityId = "ws-res-5")
            .copy(predecessorOperationId = "op-root", createdAtEpochMillis = 2000L, updatedAtEpochMillis = 2000L)

        opDao.insert(opRoot)
        opDao.insert(opTail)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(opRoot.toSnapshot(), opTail.toSnapshot()),
        )

        // Retaining opRoot instead of opTail (not tail!)
        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            syncDao.resolveWorkspaceKeepLocal(
                precondition = precondition,
                retainedOperationId = "op-root",
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = "{}",
                nowEpochMillis = 3000L,
            )
        }

        assertEquals(2, syncDao.getAllWorkspaceOperations("ws-res-5").size)
        db.close()
    }

    @Test
    fun validateWorkspaceChain_fails_on_disconnected_or_cyclic_or_multi_root_chains() {
        val root = sampleOutboxOperation("op-1", entityId = "ws-1").copy(predecessorOperationId = null).toSnapshot()
        val child = sampleOutboxOperation("op-2", entityId = "ws-1").copy(predecessorOperationId = "op-1").toSnapshot()

        // 1. Valid chain -> returns child tail
        val validTail = validateWorkspaceChain(listOf(root, child))
        assertEquals("op-2", validTail?.operationId)

        // 2. Multi-root (2 roots)
        val anotherRoot = sampleOutboxOperation("op-3", entityId = "ws-1").copy(predecessorOperationId = null).toSnapshot()
        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            validateWorkspaceChain(listOf(root, anotherRoot))
        }

        // 3. Disconnected predecessor (child points to unknown op-999)
        val disconnected = sampleOutboxOperation("op-4", entityId = "ws-1").copy(predecessorOperationId = "op-999").toSnapshot()
        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            validateWorkspaceChain(listOf(root, disconnected))
        }

        // 4. Cyclic chain (op-1 -> op-2 -> op-1)
        val cyclic1 = sampleOutboxOperation("op-c1", entityId = "ws-1").copy(predecessorOperationId = "op-c2").toSnapshot()
        val cyclic2 = sampleOutboxOperation("op-c2", entityId = "ws-1").copy(predecessorOperationId = "op-c1").toSnapshot()
        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            validateWorkspaceChain(listOf(cyclic1, cyclic2))
        }

        // 5. Forked predecessor (both point to op-1)
        val forkA = sampleOutboxOperation("op-fa", entityId = "ws-1").copy(predecessorOperationId = "op-1").toSnapshot()
        val forkB = sampleOutboxOperation("op-fb", entityId = "ws-1").copy(predecessorOperationId = "op-1").toSnapshot()
        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            validateWorkspaceChain(listOf(root, forkA, forkB))
        }
    }

    @Test
    fun resolveWorkspace_fails_when_workspace_local_content_changed_concurrently() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val opDao = db.syncOperationDao()

        val initialWs = sampleWorkspace(id = "ws-res-6", name = "Planned Name", syncStatus = "CONFLICT")
        syncDao.upsertWorkspaceRows(listOf(initialWs))

        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-res-6",
            operationId = "op-1",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 1000L,
        )
        syncDao.upsertConflictRow(conflict)

        val op = sampleOutboxOperation(operationId = "op-1", entityId = "ws-res-6").copy(predecessorOperationId = null)
        opDao.insert(op)

        val plannedPrecondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = initialWs.toSnapshot(), // has "Planned Name"
            expectedOperations = listOf(op.toSnapshot()),
        )

        // Concurrent mutation changes name in DB
        val modifiedWs = initialWs.copy(name = "Changed Behind The Back")
        syncDao.upsertWorkspaceRows(listOf(modifiedWs))

        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            syncDao.resolveWorkspaceKeepLocal(
                precondition = plannedPrecondition,
                retainedOperationId = "op-1",
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = "{}",
                nowEpochMillis = 4000L,
            )
        }

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepRemote_fails_and_preserves_state_when_conflict_op_is_not_chain_tail() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-tail-check-1", syncStatus = "CONFLICT")
        syncDao.upsertWorkspaceRows(listOf(localWs))

        // Conflict op-1 için oluşturulmuş
        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-tail-check-1",
            operationId = "op-1",
            localVersion = 1L,
            remoteVersion = 3L,
            localPayloadJson = """{"name":"Op 1"}""",
            remotePayloadJson = """{"name":"Remote"}""",
            detectedAtEpochMillis = 1000L,
        )
        syncDao.upsertConflictRow(conflict)

        // Ancak zincirde op-1'e ek olarak successor op-2 var (gerçek tail op-2)
        val op1 = sampleOutboxOperation(operationId = "op-1", entityId = "ws-tail-check-1")
            .copy(predecessorOperationId = null)
        val op2 = sampleOutboxOperation(operationId = "op-2", entityId = "ws-tail-check-1")
            .copy(predecessorOperationId = "op-1")
        opDao.insert(op1)
        opDao.insert(op2)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(op1.toSnapshot(), op2.toSnapshot()),
        )

        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            syncDao.resolveWorkspaceKeepRemote(
                precondition = precondition,
                remoteEntity = sampleWorkspace(id = "ws-tail-check-1", name = "Remote"),
            )
        }

        // Hiçbir outbox, workspace veya conflict satırı değişmemeli:
        val wsAfter = syncDao.getWorkspaceRow("ws-tail-check-1")
        assertNotNull(wsAfter)
        assertEquals("CONFLICT", wsAfter.sync.syncStatus)
        assertEquals(2, syncDao.getAllWorkspaceOperations("ws-tail-check-1").size)
        assertNotNull(syncStateDao.getConflict("ws-tail-check-1"))

        db.close()
    }

    @Test
    fun resolveWorkspaceKeepLocal_fails_and_preserves_state_when_conflict_op_is_not_chain_tail() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val opDao = db.syncOperationDao()

        val localWs = sampleWorkspace(id = "ws-tail-check-2", syncStatus = "CONFLICT")
        syncDao.upsertWorkspaceRows(listOf(localWs))

        // Conflict op-1 için oluşturulmuş
        val conflict = SyncConflictEntity(
            entityTypeCode = "WORKSPACE",
            entityId = "ws-tail-check-2",
            operationId = "op-1",
            localVersion = 1L,
            remoteVersion = 3L,
            localPayloadJson = """{"name":"Op 1"}""",
            remotePayloadJson = """{"name":"Remote"}""",
            detectedAtEpochMillis = 1000L,
        )
        syncDao.upsertConflictRow(conflict)

        // Ancak zincirde op-1'e ek olarak successor op-2 var (gerçek tail op-2)
        val op1 = sampleOutboxOperation(operationId = "op-1", entityId = "ws-tail-check-2")
            .copy(predecessorOperationId = null)
        val op2 = sampleOutboxOperation(operationId = "op-2", entityId = "ws-tail-check-2")
            .copy(predecessorOperationId = "op-1")
        opDao.insert(op1)
        opDao.insert(op2)

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = listOf(op1.toSnapshot(), op2.toSnapshot()),
        )

        assertFailsWith<WorkspaceConflictStaleResolutionException> {
            syncDao.resolveWorkspaceKeepLocal(
                precondition = precondition,
                retainedOperationId = "op-2",
                targetOperationTypeCode = "UPDATE",
                targetPayloadJson = """{"name":"Pending WS"}""",
                nowEpochMillis = 5000L,
            )
        }

        // Hiçbir outbox, workspace veya conflict satırı değişmemeli:
        val wsAfter = syncDao.getWorkspaceRow("ws-tail-check-2")
        assertNotNull(wsAfter)
        assertEquals("CONFLICT", wsAfter.sync.syncStatus)
        assertEquals(2, syncDao.getAllWorkspaceOperations("ws-tail-check-2").size)
        assertNotNull(syncStateDao.getConflict("ws-tail-check-2"))

        db.close()
    }

    @Test
    fun applyOwnershipTransferSnapshot_success_atomically_updates_workspace_and_both_members() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val localWs = sampleWorkspace(id = "ws-tr-1", ownerId = "user-actor", version = 1L)
        val localActor = sampleMember(workspaceId = "ws-tr-1", userId = "user-actor", roleCode = "OWNER", version = 1L)
        val localTarget = sampleMember(workspaceId = "ws-tr-1", userId = "user-target", roleCode = "EDITOR", version = 1L)

        syncDao.upsertWorkspaceRows(listOf(localWs))
        syncDao.upsertWorkspaceMemberRows(listOf(localActor, localTarget))

        val updatedWs = localWs.copy(
            ownerId = "user-target",
            sync = localWs.sync.copy(version = 2L, updatedAtEpochMillis = 2000L),
        )
        val updatedActor = localActor.copy(
            roleCode = "EDITOR",
            sync = localActor.sync.copy(version = 2L, updatedAtEpochMillis = 2000L),
        )
        val updatedTarget = localTarget.copy(
            roleCode = "OWNER",
            sync = localTarget.sync.copy(version = 2L, updatedAtEpochMillis = 2000L),
        )

        syncDao.applyOwnershipTransferSnapshot(
            workspace = updatedWs,
            actorMember = updatedActor,
            targetMember = updatedTarget,
            expectedWorkspaceVersion = 1L,
            expectedActorMemberVersion = 1L,
            expectedTargetMemberVersion = 1L,
        )

        val wsAfter = syncDao.getWorkspaceRow("ws-tr-1")
        assertNotNull(wsAfter)
        assertEquals("user-target", wsAfter.ownerId)
        assertEquals(2L, wsAfter.sync.version)

        val actorAfter = syncDao.getWorkspaceMemberRow("ws-tr-1", "user-actor")
        assertNotNull(actorAfter)
        assertEquals("EDITOR", actorAfter.roleCode)
        assertEquals(2L, actorAfter.sync.version)

        val targetAfter = syncDao.getWorkspaceMemberRow("ws-tr-1", "user-target")
        assertNotNull(targetAfter)
        assertEquals("OWNER", targetAfter.roleCode)
        assertEquals(2L, targetAfter.sync.version)

        db.close()
    }

    @Test
    fun applyOwnershipTransferSnapshot_staleWorkspaceVersion_rollsBack_and_leaves_no_partial_change() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val localWs = sampleWorkspace(id = "ws-tr-2", ownerId = "user-actor", version = 2L)
        val localActor = sampleMember(workspaceId = "ws-tr-2", userId = "user-actor", roleCode = "OWNER", version = 1L)
        val localTarget = sampleMember(workspaceId = "ws-tr-2", userId = "user-target", roleCode = "EDITOR", version = 1L)

        syncDao.upsertWorkspaceRows(listOf(localWs))
        syncDao.upsertWorkspaceMemberRows(listOf(localActor, localTarget))

        val updatedWs = localWs.copy(ownerId = "user-target", sync = localWs.sync.copy(version = 3L))
        val updatedActor = localActor.copy(roleCode = "EDITOR", sync = localActor.sync.copy(version = 2L))
        val updatedTarget = localTarget.copy(roleCode = "OWNER", sync = localTarget.sync.copy(version = 2L))

        assertFailsWith<WorkspaceOwnershipTransferPreconditionException> {
            syncDao.applyOwnershipTransferSnapshot(
                workspace = updatedWs,
                actorMember = updatedActor,
                targetMember = updatedTarget,
                expectedWorkspaceVersion = 1L, // Beklenen 1L ama yerel 2L -> uyuşmazlık
                expectedActorMemberVersion = 1L,
                expectedTargetMemberVersion = 1L,
            )
        }

        // Hiçbir kısmi değişiklik kalmamalı
        val wsAfter = syncDao.getWorkspaceRow("ws-tr-2")
        assertNotNull(wsAfter)
        assertEquals("user-actor", wsAfter.ownerId)
        assertEquals(2L, wsAfter.sync.version)

        val actorAfter = syncDao.getWorkspaceMemberRow("ws-tr-2", "user-actor")
        assertNotNull(actorAfter)
        assertEquals("OWNER", actorAfter.roleCode)

        val targetAfter = syncDao.getWorkspaceMemberRow("ws-tr-2", "user-target")
        assertNotNull(targetAfter)
        assertEquals("EDITOR", targetAfter.roleCode)

        db.close()
    }

    @Test
    fun applyOwnershipTransferSnapshot_uncommittedMember_rollsBack() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val localWs = sampleWorkspace(id = "ws-tr-3", ownerId = "user-actor", version = 1L)
        val localActor = sampleMember(workspaceId = "ws-tr-3", userId = "user-actor", roleCode = "OWNER", version = 1L)
        val localTarget = sampleMember(workspaceId = "ws-tr-3", userId = "user-target", roleCode = "EDITOR", version = 1L, syncStatus = "PENDING_UPDATE")

        syncDao.upsertWorkspaceRows(listOf(localWs))
        syncDao.upsertWorkspaceMemberRows(listOf(localActor, localTarget))

        val updatedWs = localWs.copy(ownerId = "user-target", sync = localWs.sync.copy(version = 2L))
        val updatedActor = localActor.copy(roleCode = "EDITOR", sync = localActor.sync.copy(version = 2L))
        val updatedTarget = localTarget.copy(roleCode = "OWNER", sync = localTarget.sync.copy(version = 2L))

        assertFailsWith<WorkspaceOwnershipTransferPreconditionException> {
            syncDao.applyOwnershipTransferSnapshot(
                workspace = updatedWs,
                actorMember = updatedActor,
                targetMember = updatedTarget,
                expectedWorkspaceVersion = 1L,
                expectedActorMemberVersion = 1L,
                expectedTargetMemberVersion = 1L,
            )
        }

        // Hiçbir değişiklik uygulanmamalı
        val wsAfter = syncDao.getWorkspaceRow("ws-tr-3")
        assertNotNull(wsAfter)
        assertEquals("user-actor", wsAfter.ownerId)

        db.close()
    }

    @Test
    fun applyWorkspaceSnapshot_andIncrementalPlan_withTombstonedMember_clearsActiveWorkspaceIfMatches() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()

        val ws = sampleWorkspace(id = "ws-rem-sync", ownerId = "user-owner")
        syncDao.upsertWorkspaceRows(listOf(ws))

        // Kullanıcının profili tanımlanıyor ve bu workspace aktif
        val profileDao = db.profileDao()
        val userProfile = UserProfileEntity(
            id = "user-removed",
            email = "removed@test.com",
            fullName = "Removed User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "TR",
            activeWorkspaceId = "ws-rem-sync",
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )
        profileDao.upsert(userProfile)

        val tombstonedMember = sampleMember(
            workspaceId = "ws-rem-sync",
            userId = "user-removed",
            roleCode = "EDITOR",
            version = 2L,
            deletedAtEpochMillis = 3000L,
        )

        // Snapshot ile tombstoned üye içeri alınıyor
        syncDao.applyWorkspaceSnapshot(workspaces = listOf(ws), members = listOf(tombstonedMember))

        // Profilin active_workspace_id alanı null olmalı, ancak workspace verisi silinmemeli
        val profileAfterSnap = profileDao.observeById("user-removed").first()
        assertNotNull(profileAfterSnap)
        assertNull(profileAfterSnap.activeWorkspaceId)

        val wsAfterSnap = syncDao.getWorkspaceRow("ws-rem-sync")
        assertNotNull(wsAfterSnap)

        // Şimdi profili tekrar bu workspace'e aktif yapıp incremental plan ile test edelim
        profileDao.upsert(profileAfterSnap.copy(activeWorkspaceId = "ws-rem-sync"))

        val incrementalPlan = WorkspaceIncrementalPlan(
            applyItems = emptyList(),
            conflictItems = emptyList(),
            memberRows = listOf(tombstonedMember.copy(sync = tombstonedMember.sync.copy(version = 3L))),
            cursorsToPersist = emptyList(),
        )
        syncDao.applyWorkspaceIncrementalPlan(incrementalPlan)

        val profileAfterInc = profileDao.observeById("user-removed").first()
        assertNotNull(profileAfterInc)
        assertNull(profileAfterInc.activeWorkspaceId)

        db.close()
    }
}
