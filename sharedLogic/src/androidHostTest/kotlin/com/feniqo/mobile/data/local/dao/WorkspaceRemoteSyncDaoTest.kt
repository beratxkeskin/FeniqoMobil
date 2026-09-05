package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}

