package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_9_10
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_10_11
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.domain.model.SyncStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceDaoTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private val testSync = SyncMetadata(
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtEpochMillis = 1000L,
        localUpdatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private fun testWorkspace(
        id: String = "ws-1",
        ownerId: String = "user-1",
        name: String = "Aile Bütçesi",
        typeCode: String = "shared",
        currencyCode: String = "TRY",
        description: String? = "Ev harcamaları",
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            ownerId = ownerId,
            typeCode = typeCode,
            currencyCode = currencyCode,
            description = description,
            createdAtEpochMillis = 1000L,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    private fun testInvitation(
        id: String = "inv-1",
        workspaceId: String = "ws-1",
        inviterId: String = "user-1",
        roleCode: String = "EDITOR",
        createdAtEpochMillis: Long = 1000L,
        expiresAtEpochMillis: Long = 5000L,
        maxUses: Int = 5,
        usesCount: Int = 1,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceInvitationEntity {
        return WorkspaceInvitationEntity(
            id = id,
            workspaceId = workspaceId,
            inviterId = inviterId,
            roleCode = roleCode,
            createdAtEpochMillis = createdAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            maxUses = maxUses,
            usesCount = usesCount,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    @Test
    fun observeInvitations_returnsOnlyActiveInvitationsForSpecifiedWorkspaceInDeterministicOrder() = runTest {
        val database = inMemoryDatabase()
        try {
            val workspaceDao = database.workspaceDao()

            // 1. İki workspace oluştur
            workspaceDao.upsertWorkspace(testWorkspace("ws-1", "user-1", "Ev Bütçesi"))
            workspaceDao.upsertWorkspace(testWorkspace("ws-2", "user-1", "İş Bütçesi"))

            // 2. ws-1 için farklı sürelerde ve tombstone içeren davetler ekle
            val inv1 = testInvitation(id = "inv-1", workspaceId = "ws-1", expiresAtEpochMillis = 8000L)
            val inv2 = testInvitation(id = "inv-2", workspaceId = "ws-1", expiresAtEpochMillis = 4000L)
            val inv3 = testInvitation(id = "inv-3", workspaceId = "ws-1", expiresAtEpochMillis = 4000L)
            val tombstoneInv = testInvitation(id = "inv-4", workspaceId = "ws-1", expiresAtEpochMillis = 2000L, deletedAtEpochMillis = 2500L)

            // ws-2 için başka bir davet ekle
            val otherWsInv = testInvitation(id = "inv-other", workspaceId = "ws-2", expiresAtEpochMillis = 3000L)

            workspaceDao.upsertInvitation(inv1)
            workspaceDao.upsertInvitation(inv2)
            workspaceDao.upsertInvitation(inv3)
            workspaceDao.upsertInvitation(tombstoneInv)
            workspaceDao.upsertInvitation(otherWsInv)

            // 3. ws-1 davetlerini gözlemle
            val ws1Invitations = workspaceDao.observeInvitations("ws-1").first()

            // Doğrulamalar:
            // - Tombstone olan (inv-4) gelmemeli
            // - Başka workspace'e ait olan (inv-other) gelmemeli
            // - Sıralama expiresAtEpochMillis ASC, id ASC olmalı -> inv-2 (4000L), inv-3 (4000L), inv-1 (8000L)
            assertEquals(3, ws1Invitations.size)
            assertEquals("inv-2", ws1Invitations[0].id)
            assertEquals("inv-3", ws1Invitations[1].id)
            assertEquals("inv-1", ws1Invitations[2].id)

            // Alan doğrulamaları
            val first = ws1Invitations[0]
            assertEquals("ws-1", first.workspaceId)
            assertEquals("user-1", first.inviterId)
            assertEquals("EDITOR", first.roleCode)
            assertEquals(5, first.maxUses)
            assertEquals(1, first.usesCount)
            assertEquals(4000L, first.expiresAtEpochMillis)

            // 4. ws-2 davetlerini gözlemle
            val ws2Invitations = workspaceDao.observeInvitations("ws-2").first()
            assertEquals(1, ws2Invitations.size)
            assertEquals("inv-other", ws2Invitations[0].id)
        } finally {
            database.close()
        }
    }

    @Test
    fun upsertInvitation_updatesExistingInvitation() = runTest {
        val database = inMemoryDatabase()
        try {
            val workspaceDao = database.workspaceDao()
            workspaceDao.upsertWorkspace(testWorkspace("ws-1", "user-1"))

            val initial = testInvitation("inv-1", "ws-1", usesCount = 0)
            workspaceDao.upsertInvitation(initial)

            val observedInitial = workspaceDao.observeInvitations("ws-1").first()
            assertEquals(1, observedInitial.size)
            assertEquals(0, observedInitial[0].usesCount)

            val updated = initial.copy(usesCount = 2)
            workspaceDao.upsertInvitation(updated)

            val observedUpdated = workspaceDao.observeInvitations("ws-1").first()
            assertEquals(1, observedUpdated.size)
            assertEquals(2, observedUpdated[0].usesCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration9to10_preservesOldWorkspaceAndBackfillsNewColumnsAndCreatesInvitationsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_9_10_test.db"
        val testDbFile = File(context.filesDir, testDbName)
        val testDbPath = testDbFile.absolutePath

        context.deleteDatabase(testDbName)

        // 1. v9 şemasıyla veritabanını oluştur ve v9 workspace satırı yaz
        val v9Db = migrationHelper.createDatabase(testDbPath, 9)
        try {
            v9Db.execSQL(
                """
                INSERT INTO workspaces (
                    id, name, normalized_name, owner_id, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms,
                    deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'ws-legacy-1', 'Kişisel Alan', 'kisisel alan', 'user-legacy', 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v9Db.close()
        }

        // 2. v9 -> v10 migration'ını çalıştır ve doğrula
        val v10Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            10,
            true,
            ANDROID_MIGRATION_9_10,
        )
        try {
            // 2.1 Eski workspace satırının korunduğunu ve yeni alanların default değerlerle backfill edildiğini doğrula
            val wsCursor = v10Db.query(
                "SELECT id, name, type_code, currency_code, description FROM workspaces WHERE id = 'ws-legacy-1'"
            )
            assertTrue(wsCursor.moveToFirst())
            assertEquals("ws-legacy-1", wsCursor.getString(0))
            assertEquals("Kişisel Alan", wsCursor.getString(1))
            assertEquals("personal", wsCursor.getString(2))
            assertEquals("TRY", wsCursor.getString(3))
            assertNull(wsCursor.getString(4))
            wsCursor.close()

            // 2.2 Yeni workspace_invitations tablosuna yazma ve okuma doğrulaması
            v10Db.execSQL(
                """
                INSERT INTO workspace_invitations (
                    id, workspace_id, inviter_id, role_code, created_at_epoch_ms,
                    expires_at_epoch_ms, max_uses, uses_count, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'inv-legacy-1', 'ws-legacy-1', 'user-legacy', 'EDITOR', 1000,
                    6000, 10, 0, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val invCursor = v10Db.query(
                "SELECT id, workspace_id, role_code, max_uses, uses_count FROM workspace_invitations WHERE id = 'inv-legacy-1'"
            )
            assertTrue(invCursor.moveToFirst())
            assertEquals("inv-legacy-1", invCursor.getString(0))
            assertEquals("ws-legacy-1", invCursor.getString(1))
            assertEquals("EDITOR", invCursor.getString(2))
            assertEquals(10, invCursor.getInt(3))
            assertEquals(0, invCursor.getInt(4))
            invCursor.close()
        } finally {
            v10Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migration_10_to_11_adds_token_hash_to_invitations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_10_11_test.db"
        val testDbFile = File(context.filesDir, testDbName)
        val testDbPath = testDbFile.absolutePath
        context.deleteDatabase(testDbName)

        // 1. v10 şeması ile veritabanını oluştur ve eski bir davet ekle
        val v10Db = migrationHelper.createDatabase(testDbPath, 10)
        try {
            v10Db.execSQL(
                """
                INSERT INTO workspaces (
                    id, name, normalized_name, owner_id, type_code, currency_code,
                    description, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'ws-v10-1', 'Ortak Alan', 'ortak alan', 'user-v10', 'shared', 'TRY',
                    NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v10Db.execSQL(
                """
                INSERT INTO workspace_invitations (
                    id, workspace_id, inviter_id, role_code, created_at_epoch_ms,
                    expires_at_epoch_ms, max_uses, uses_count, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'inv-v10-1', 'ws-v10-1', 'user-v10', 'EDITOR', 1000,
                    6000, 10, 0, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v10Db.close()
        }

        // 2. v10 -> v11 migration'ını çalıştır ve doğrula
        val v11Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            11,
            true,
            ANDROID_MIGRATION_10_11,
        )
        try {
            // 2.1 Eski davet satırının korunduğunu ve token_hash'in null olduğunu doğrula
            val invCursor = v11Db.query(
                "SELECT id, workspace_id, role_code, token_hash FROM workspace_invitations WHERE id = 'inv-v10-1'"
            )
            assertTrue(invCursor.moveToFirst())
            assertEquals("inv-v10-1", invCursor.getString(0))
            assertEquals("ws-v10-1", invCursor.getString(1))
            assertEquals("EDITOR", invCursor.getString(2))
            assertNull(invCursor.getString(3))
            invCursor.close()

            // 2.2 Yeni token_hash ile davet yazma ve okuma doğrulaması
            v11Db.execSQL(
                """
                INSERT INTO workspace_invitations (
                    id, workspace_id, inviter_id, role_code, token_hash, created_at_epoch_ms,
                    expires_at_epoch_ms, max_uses, uses_count, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'inv-v11-1', 'ws-v10-1', 'user-v10', 'VIEWER', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 2000,
                    8000, 5, 0, 'SYNCED', 2000, 2000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val v11Cursor = v11Db.query(
                "SELECT id, role_code, token_hash FROM workspace_invitations WHERE id = 'inv-v11-1'"
            )
            assertTrue(v11Cursor.moveToFirst())
            assertEquals("inv-v11-1", v11Cursor.getString(0))
            assertEquals("VIEWER", v11Cursor.getString(1))
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", v11Cursor.getString(2))
            v11Cursor.close()
        } finally {
            v11Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun ackWorkspaceInvitationWriteV2_preservesLocalTokenHash() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()

            // 1. Workspace ve token_hash içeren yerel davet oluştur
            val ws = testWorkspace(id = "ws-ack-test")
            workspaceDao.upsertWorkspace(ws)

            val inv = WorkspaceInvitationEntity(
                id = "inv-ack-1",
                workspaceId = "ws-ack-test",
                inviterId = "user-1",
                tokenHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                roleCode = "EDITOR",
                createdAtEpochMillis = 1000L,
                expiresAtEpochMillis = 5000L,
                maxUses = 5,
                usesCount = 0,
                sync = testSync.copy(syncStatus = SyncStatus.PENDING_CREATE.name),
            )
            mutationDao.upsertWorkspaceInvitationRow(inv)

            // 2. Outbox kaydı ekle (IN_FLIGHT)
            val op = SyncOperationEntity(
                operationId = "00000000000000000000000000000010",
                entityTypeCode = "WORKSPACE_INVITATION",
                entityId = "inv-ack-1",
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = "{}",
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 2,
                statusCode = "IN_FLIGHT",
                attemptCount = 1,
                lastError = null,
                nextAttemptAtEpochMillis = 2000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
            mutationDao.insertOutboxRow(op)

            // 3. Sunucudan dönen DTO (tanım gereği token_hash İÇERMEZ)
            val remoteDto = WorkspaceInvitationDto(
                id = "inv-ack-1",
                workspaceId = "ws-ack-test",
                inviterId = "user-1",
                roleCode = "EDITOR",
                createdAt = "2026-09-08T00:00:00Z",
                expiresAt = "2026-09-15T00:00:00Z",
                maxUses = 5,
                usesCount = 0,
                deletedAt = null,
                version = 1L,
            )

            // 4. ACK işlemini yürüt
            val acked = mutationDao.ackWorkspaceInvitationWriteV2(
                operationId = "00000000000000000000000000000010",
                record = remoteDto,
                nowEpochMillis = 3000L,
            )
            assertTrue(acked)

            // 5. Yerel Room cache'inde token_hash değerinin korunduğunu doğrula
            val updatedInv = mutationDao.getWorkspaceInvitationById("inv-ack-1")
            assertNotNull(updatedInv)
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                updatedInv.tokenHash,
                "ACK sonrası yerel token_hash korunmalıdır, sunucu cevabı ile null'a ezilmemelidir."
            )
            assertEquals("SYNCED", updatedInv.sync.syncStatus)
            assertNull(mutationDao.getOutboxById("00000000000000000000000000000010"))
        } finally {
            database.close()
        }
    }
}
