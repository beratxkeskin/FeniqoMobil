package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserScopedSyncCursorDaoTest {

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"
        const val SCOPE_A = "USER:$USER_A_ID"
        const val SCOPE_B = "USER:$USER_B_ID"
    }

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private fun fileDatabase(file: File): FeniqoDatabase {
        return Room.databaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            name = file.absolutePath,
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private fun sampleProfile(
        id: String,
        email: String = "$id@feniqo.com",
    ): UserProfileEntity = UserProfileEntity(
        id = id,
        email = email,
        fullName = "User $id",
        currencyCode = "TRY",
        themeCode = "SYSTEM",
        languageCode = "tr",
        activeWorkspaceId = null,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = 1L,
            lastSyncError = null,
        ),
    )

    private fun sampleWorkspace(
        id: String = "ws-1",
        ownerId: String = "user-A",
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = "Workspace $id",
        normalizedName = "workspace $id",
        ownerId = ownerId,
        typeCode = "personal",
        currencyCode = "TRY",
        description = null,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = 1L,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String = "ws-1",
        userId: String = "user-A",
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = "owner",
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = 1L,
            lastSyncError = null,
        ),
    )

    @Test
    fun test1_userAAndUserBCanStoreSameEntityTypeCursorsConcurrently() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "TRANSACTION",
                    updatedAtEpochMillis = 1000L,
                    entityId = "cursor-A-tx-100",
                )
            )
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = "TRANSACTION",
                    updatedAtEpochMillis = 2000L,
                    entityId = "cursor-B-tx-200",
                )
            )

            val cursorA = db.syncStateDao().getCursor(scopeA, "TRANSACTION")
            val cursorB = db.syncStateDao().getCursor(scopeB, "TRANSACTION")

            assertNotNull(cursorA)
            assertNotNull(cursorB)
            assertEquals("cursor-A-tx-100", cursorA.entityId)
            assertEquals("cursor-B-tx-200", cursorB.entityId)
            assertEquals(scopeA, cursorA.syncScopeKey)
            assertEquals(scopeB, cursorB.syncScopeKey)
        } finally {
            db.close()
        }
    }

    @Test
    fun test2_scopedGetCursorReturnsOnlyMatchingScope() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "CATEGORY",
                    updatedAtEpochMillis = 1000L,
                    entityId = "cat-A",
                )
            )

            assertNotNull(db.syncStateDao().getCursor(scopeA, "CATEGORY"))
            assertNull(db.syncStateDao().getCursor(scopeB, "CATEGORY"))
        } finally {
            db.close()
        }
    }

    @Test
    fun test3_getWorkspaceMemberCursorsIsUserScoped() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = WorkspaceSyncCursorKeys.workspaceMemberEntityType("ws-1"),
                    updatedAtEpochMillis = 1000L,
                    entityId = "user-member-1",
                )
            )
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = WorkspaceSyncCursorKeys.workspaceMemberEntityType("ws-2"),
                    updatedAtEpochMillis = 1100L,
                    entityId = "user-member-2",
                )
            )
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = WorkspaceSyncCursorKeys.workspaceMemberEntityType("ws-1"),
                    updatedAtEpochMillis = 2000L,
                    entityId = "user-member-1",
                )
            )

            val cursorsA = db.syncStateDao().getWorkspaceMemberCursors(scopeA)
            val cursorsB = db.syncStateDao().getWorkspaceMemberCursors(scopeB)

            assertEquals(2, cursorsA.size)
            assertTrue(cursorsA.all { it.syncScopeKey == scopeA })
            assertEquals(setOf("WORKSPACE_MEMBER:ws-1", "WORKSPACE_MEMBER:ws-2"), cursorsA.map { it.entityTypeCode }.toSet())

            assertEquals(1, cursorsB.size)
            assertTrue(cursorsB.all { it.syncScopeKey == scopeB })
            assertEquals("WORKSPACE_MEMBER:ws-1", cursorsB[0].entityTypeCode)
            assertEquals("user-member-1", cursorsB[0].entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun test4_userAProfileCursorDoesNotBlockUserBInitialPull() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            // User A has PROFILE cursor and completed initial sync
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "PROFILE",
                    updatedAtEpochMillis = 1000L,
                    entityId = "profile-cursor-A",
                )
            )

            // When user B checks for PROFILE cursor, it must be null (signifying initial pull needed)
            val profileCursorB = db.syncStateDao().getCursor(scopeB, "PROFILE")
            assertNull(profileCursorB, "User B must see null PROFILE cursor even if user A has a PROFILE cursor")

            val profileCursorA = db.syncStateDao().getCursor(scopeA, "PROFILE")
            assertNotNull(profileCursorA)
            assertEquals("profile-cursor-A", profileCursorA.entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun test5_userBInitialAndIncrementalSyncDoesNotAlterUserACursor() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "ACCOUNT",
                    updatedAtEpochMillis = 1000L,
                    entityId = "account-cursor-A-v1",
                )
            )

            // User B advances cursor
            db.remoteSyncDao().advancePullCursor(
                syncScopeKey = scopeB,
                cursor = SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = "ACCOUNT",
                    updatedAtEpochMillis = 2000L,
                    entityId = "account-cursor-B-v1",
                )
            )

            // User A cursor must remain untouched
            val cursorA = db.syncStateDao().getCursor(scopeA, "ACCOUNT")
            val cursorB = db.syncStateDao().getCursor(scopeB, "ACCOUNT")

            assertNotNull(cursorA)
            assertEquals("account-cursor-A-v1", cursorA.entityId)
            assertEquals(1000L, cursorA.updatedAtEpochMillis)

            assertNotNull(cursorB)
            assertEquals("account-cursor-B-v1", cursorB.entityId)
            assertEquals(2000L, cursorB.updatedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun test6_whenUserAReturnsResumesFromOwnCursor() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "TRANSACTION",
                    updatedAtEpochMillis = 5000L,
                    entityId = "tx-A-step-5",
                )
            )

            // User B acts and reaches step 20
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = "TRANSACTION",
                    updatedAtEpochMillis = 20000L,
                    entityId = "tx-B-step-20",
                )
            )

            // When user A comes back, querying user A scope yields step 5
            val resumedA = db.syncStateDao().getCursor(scopeA, "TRANSACTION")
            assertNotNull(resumedA)
            assertEquals("tx-A-step-5", resumedA.entityId)
            assertEquals(5000L, resumedA.updatedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun test7_workspaceBootstrapMarkerIsUserScoped() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            db.syncStateDao().upsertCursor(
                WorkspaceSyncCursorKeys.bootstrapCompleteEntity(scopeA, 1000L)
            )

            val markerA = db.syncStateDao().getCursor(scopeA, WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE)
            val markerB = db.syncStateDao().getCursor(scopeB, WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE)

            assertNotNull(markerA)
            assertEquals(WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID, markerA.entityId)
            assertEquals(scopeA, markerA.syncScopeKey)

            assertNull(markerB, "User B must not see User A's workspace bootstrap marker")
        } finally {
            db.close()
        }
    }

    @Test
    fun test8_actorCursorScopeMismatchRollsBackTransaction() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            val initialCursor = SyncCursorEntity(
                syncScopeKey = scopeA,
                entityTypeCode = "INITIAL",
                updatedAtEpochMillis = 500L,
                entityId = "init-cursor-0",
            )
            db.syncStateDao().upsertCursor(initialCursor)

            // 1. advancePullCursor mismatch
            val mismatchedCursor = SyncCursorEntity(
                syncScopeKey = scopeB,
                entityTypeCode = "INITIAL",
                updatedAtEpochMillis = 1000L,
                entityId = "init-cursor-mismatch",
            )
            assertFailsWith<IllegalArgumentException> {
                db.remoteSyncDao().advancePullCursor(
                    syncScopeKey = scopeA,
                    cursor = mismatchedCursor,
                )
            }
            // Verify rollback: cursor remains unchanged
            val afterFailedAdvance = db.syncStateDao().getCursor(scopeA, "INITIAL")
            assertEquals("init-cursor-0", afterFailedAdvance?.entityId)

            // 2. applyWorkspaceSnapshot mismatch
            val ws = sampleWorkspace("ws-scoped", USER_A_ID)
            val member = sampleMember(workspaceId = "ws-scoped", userId = USER_A_ID)
            val mismatchedWsCursor = SyncCursorEntity(
                syncScopeKey = scopeB,
                entityTypeCode = WorkspaceSyncCursorKeys.WORKSPACE_ENTITY_TYPE,
                updatedAtEpochMillis = 1000L,
                entityId = "ws-scoped",
            )

            assertFailsWith<IllegalArgumentException> {
                db.remoteSyncDao().applyWorkspaceSnapshot(
                    syncScopeKey = scopeA,
                    workspaces = listOf(ws),
                    members = listOf(member),
                    cursors = listOf(mismatchedWsCursor),
                )
            }

            // Verify rollback: workspace was not inserted
            assertNull(db.workspaceDao().getWorkspaceById("ws-scoped"))
            assertNull(db.syncStateDao().getCursor(scopeA, WorkspaceSyncCursorKeys.WORKSPACE_ENTITY_TYPE))
            assertNull(db.syncStateDao().getCursor(scopeB, WorkspaceSyncCursorKeys.WORKSPACE_ENTITY_TYPE))

            // 3. applyInitialSnapshot mismatch
            val profile = sampleProfile(USER_A_ID)
            assertFailsWith<IllegalArgumentException> {
                db.remoteSyncDao().applyInitialSnapshot(
                    syncScopeKey = scopeA,
                    profile = profile,
                    categories = emptyList(),
                    transactions = emptyList(),
                    cursors = listOf(
                        SyncCursorEntity(
                            syncScopeKey = scopeB, // Mismatch!
                            entityTypeCode = "PROFILE",
                            updatedAtEpochMillis = 1000L,
                            entityId = "prof-cur",
                        )
                    ),
                )
            }
            assertNull(db.remoteSyncDao().getProfileRow(USER_A_ID))
            assertNull(db.syncStateDao().getCursor(scopeA, "PROFILE"))
        } finally {
            db.close()
        }
    }

    @Test
    fun test9_legacyUnresolvedCursorIsNotReturnedByNormalUserScopeQueries() = runTest {
        val db = inMemoryDatabase()
        try {
            val legacyScope = "LEGACY_UNRESOLVED"
            val userScope = SCOPE_A

            // Insert legacy cursor
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = legacyScope,
                    entityTypeCode = "TRANSACTION",
                    updatedAtEpochMillis = 500L,
                    entityId = "legacy-tx-cursor-999",
                )
            )

            // User A queries for TRANSACTION cursor
            val cursorForUserA = db.syncStateDao().getCursor(userScope, "TRANSACTION")
            assertNull(cursorForUserA, "Normal USER queries must not return LEGACY_UNRESOLVED cursors")

            // Workspace member query
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = legacyScope,
                    entityTypeCode = "WORKSPACE_MEMBER:ws-1",
                    updatedAtEpochMillis = 500L,
                    entityId = "legacy-wsm-cursor",
                )
            )
            val memberCursorsForUserA = db.syncStateDao().getWorkspaceMemberCursors(userScope)
            assertTrue(memberCursorsForUserA.isEmpty(), "Workspace member queries must not return LEGACY_UNRESOLVED")

            // But legacy cursor still exists in DB under its own key
            val legacyDirect = db.syncStateDao().getCursor(legacyScope, "TRANSACTION")
            assertNotNull(legacyDirect)
            assertEquals("legacy-tx-cursor-999", legacyDirect.entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun test10_closingAndReopeningDatabasePreservesCursorIsolation() = runTest {
        val tempDir = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        val dbFile = File(tempDir, "scoped_cursor_test_${System.currentTimeMillis()}.db")
        dbFile.deleteOnExit()

        val scopeA = SCOPE_A
        val scopeB = SCOPE_B

        var db = fileDatabase(dbFile)
        try {
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "CATEGORY",
                    updatedAtEpochMillis = 1111L,
                    entityId = "cat-A-persistent",
                )
            )
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = "CATEGORY",
                    updatedAtEpochMillis = 2222L,
                    entityId = "cat-B-persistent",
                )
            )
        } finally {
            db.close()
        }

        // Reopen database
        db = fileDatabase(dbFile)
        try {
            val cursorA = db.syncStateDao().getCursor(scopeA, "CATEGORY")
            val cursorB = db.syncStateDao().getCursor(scopeB, "CATEGORY")

            assertNotNull(cursorA)
            assertEquals("cat-A-persistent", cursorA.entityId)
            assertEquals(1111L, cursorA.updatedAtEpochMillis)

            assertNotNull(cursorB)
            assertEquals("cat-B-persistent", cursorB.entityId)
            assertEquals(2222L, cursorB.updatedAtEpochMillis)
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    @Test
    fun test11_accountSwitchingDoesNotDeleteExistingCursors() = runTest {
        val db = inMemoryDatabase()
        try {
            val scopeA = SCOPE_A
            val scopeB = SCOPE_B

            // User A operates
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeA,
                    entityTypeCode = "BUDGET",
                    updatedAtEpochMillis = 3000L,
                    entityId = "budget-cur-A",
                )
            )

            // Switch to User B
            db.syncStateDao().upsertCursor(
                SyncCursorEntity(
                    syncScopeKey = scopeB,
                    entityTypeCode = "BUDGET",
                    updatedAtEpochMillis = 4000L,
                    entityId = "budget-cur-B",
                )
            )

            // Switch back to User A - verify cursor was NOT deleted
            val cursorA = db.syncStateDao().getCursor(scopeA, "BUDGET")
            val cursorB = db.syncStateDao().getCursor(scopeB, "BUDGET")

            assertNotNull(cursorA, "User A cursor must not be deleted when switching to User B")
            assertEquals("budget-cur-A", cursorA.entityId)

            assertNotNull(cursorB, "User B cursor must not be deleted")
            assertEquals("budget-cur-B", cursorB.entityId)
        } finally {
            db.close()
        }
    }
}
