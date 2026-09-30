package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.sync.SyncScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserScopedSyncConflictDaoTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var syncStateDao: SyncStateDao

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"

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

        syncStateDao = database.syncStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

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

    @Test
    fun usersAAndBCanStoreConflictsForSameEntityWithoutCollision() = runTest {
        val entityId = "shared-entity-1"
        val conflictA = sampleConflict(SCOPE_A, "TRANSACTION", entityId, "op-a")
        val conflictB = sampleConflict(SCOPE_B, "TRANSACTION", entityId, "op-b")

        syncStateDao.upsertConflict(conflictA)
        syncStateDao.upsertConflict(conflictB)

        val retrievedA = syncStateDao.getConflict(SCOPE_A, entityId)
        val retrievedB = syncStateDao.getConflict(SCOPE_B, entityId)

        assertNotNull(retrievedA)
        assertNotNull(retrievedB)
        assertEquals("op-a", retrievedA.operationId)
        assertEquals(SCOPE_A, retrievedA.syncScopeKey)
        assertEquals("op-b", retrievedB.operationId)
        assertEquals(SCOPE_B, retrievedB.syncScopeKey)
    }

    @Test
    fun getConflictByEntityTypeAndId_isolatesBetweenUsers() = runTest {
        val entityId = "shared-entity-2"
        val conflictA = sampleConflict(SCOPE_A, "CATEGORY", entityId, "op-a-cat")
        syncStateDao.upsertConflict(conflictA)

        val retrievedA = syncStateDao.getConflict(SCOPE_A, "CATEGORY", entityId)
        val retrievedB = syncStateDao.getConflict(SCOPE_B, "CATEGORY", entityId)

        assertNotNull(retrievedA)
        assertEquals(SCOPE_A, retrievedA.syncScopeKey)
        assertNull(retrievedB)
    }

    @Test
    fun getConflictsByEntityType_returnsOnlyCallerScopeConflicts() = runTest {
        val conflictA1 = sampleConflict(SCOPE_A, "ACCOUNT", "acc-1", "op-a-1")
        val conflictA2 = sampleConflict(SCOPE_A, "ACCOUNT", "acc-2", "op-a-2")
        val conflictB1 = sampleConflict(SCOPE_B, "ACCOUNT", "acc-1", "op-b-1")

        syncStateDao.upsertConflict(conflictA1)
        syncStateDao.upsertConflict(conflictA2)
        syncStateDao.upsertConflict(conflictB1)

        val accountsA = syncStateDao.getConflictsByEntityType(SCOPE_A, "ACCOUNT")
        val accountsB = syncStateDao.getConflictsByEntityType(SCOPE_B, "ACCOUNT")

        assertEquals(2, accountsA.size)
        assertTrue(accountsA.all { it.syncScopeKey == SCOPE_A })
        assertEquals(1, accountsB.size)
        assertEquals(SCOPE_B, accountsB[0].syncScopeKey)
        assertEquals("op-b-1", accountsB[0].operationId)
    }

    @Test
    fun getAllConflicts_returnsOnlyCallerScopeConflicts() = runTest {
        val conflictA = sampleConflict(SCOPE_A, "TRANSACTION", "tx-1", "op-a")
        val conflictB = sampleConflict(SCOPE_B, "CATEGORY", "cat-1", "op-b")
        val conflictLegacy = sampleConflict(SCOPE_LEGACY, "ASSET", "ast-1", "op-legacy")

        syncStateDao.upsertConflict(conflictA)
        syncStateDao.upsertConflict(conflictB)
        syncStateDao.upsertConflict(conflictLegacy)

        val allA = syncStateDao.getAllConflicts(SCOPE_A)
        val allB = syncStateDao.getAllConflicts(SCOPE_B)

        assertEquals(1, allA.size)
        assertEquals("tx-1", allA[0].entityId)
        assertEquals(SCOPE_A, allA[0].syncScopeKey)

        assertEquals(1, allB.size)
        assertEquals("cat-1", allB[0].entityId)
        assertEquals(SCOPE_B, allB[0].syncScopeKey)
    }

    @Test
    fun observeConflicts_emitsOnlyCallerScopeConflicts() = runTest {
        val conflictA = sampleConflict(SCOPE_A, "TRANSACTION", "tx-1", "op-a", 1000L)
        val conflictB = sampleConflict(SCOPE_B, "TRANSACTION", "tx-2", "op-b", 2000L)

        syncStateDao.upsertConflict(conflictA)
        syncStateDao.upsertConflict(conflictB)

        val listA = syncStateDao.observeConflicts(SCOPE_A).first()
        val listB = syncStateDao.observeConflicts(SCOPE_B).first()

        assertEquals(1, listA.size)
        assertEquals("tx-1", listA[0].entityId)
        assertEquals(SCOPE_A, listA[0].syncScopeKey)

        assertEquals(1, listB.size)
        assertEquals("tx-2", listB[0].entityId)
        assertEquals(SCOPE_B, listB[0].syncScopeKey)
    }

    @Test
    fun observeConflictCount_and_getConflictCount_isolateBetweenUsers() = runTest {
        val conflictA1 = sampleConflict(SCOPE_A, "TRANSACTION", "tx-1", "op-a-1")
        val conflictA2 = sampleConflict(SCOPE_A, "TRANSACTION", "tx-2", "op-a-2")
        val conflictB1 = sampleConflict(SCOPE_B, "TRANSACTION", "tx-3", "op-b-1")

        syncStateDao.upsertConflict(conflictA1)
        syncStateDao.upsertConflict(conflictA2)
        syncStateDao.upsertConflict(conflictB1)

        assertEquals(2, syncStateDao.getConflictCount(SCOPE_A))
        assertEquals(1, syncStateDao.getConflictCount(SCOPE_B))
        assertEquals(0, syncStateDao.getConflictCount("user:non-existent"))

        assertEquals(2, syncStateDao.observeConflictCount(SCOPE_A).first())
        assertEquals(1, syncStateDao.observeConflictCount(SCOPE_B).first())
        assertEquals(0, syncStateDao.observeConflictCount("user:non-existent").first())
    }

    @Test
    fun deleteConflict_deletesOnlyCallerScopeConflict_preservingOtherUserConflict() = runTest {
        val entityId = "shared-entity-delete"
        val conflictA = sampleConflict(SCOPE_A, "GOAL", entityId, "op-a-goal", detectedAtEpochMillis = 1100L)
        val conflictB = sampleConflict(SCOPE_B, "GOAL", entityId, "op-b-goal", detectedAtEpochMillis = 2200L)

        syncStateDao.upsertConflict(conflictA)
        syncStateDao.upsertConflict(conflictB)

        // Delete from user A scope
        val deletedCount = syncStateDao.deleteConflict(SCOPE_A, "GOAL", entityId)
        assertEquals(1, deletedCount)

        // User A conflict is gone
        assertNull(syncStateDao.getConflict(SCOPE_A, "GOAL", entityId))
        assertEquals(0, syncStateDao.getConflictCount(SCOPE_A))

        // User B conflict is preserved exactly
        val preservedB = syncStateDao.getConflict(SCOPE_B, "GOAL", entityId)
        assertNotNull(preservedB)
        assertEquals("op-b-goal", preservedB.operationId)
        assertEquals(SCOPE_B, preservedB.syncScopeKey)
        assertEquals(2200L, preservedB.detectedAtEpochMillis)
        assertEquals(1, syncStateDao.getConflictCount(SCOPE_B))
    }

    @Test
    fun legacyUnresolvedConflicts_areExcludedFromUserScopes() = runTest {
        val conflictLegacy = sampleConflict(SCOPE_LEGACY, "DEBT", "debt-legacy-1", "op-legacy")
        syncStateDao.upsertConflict(conflictLegacy)

        // User A and User B cannot see legacy conflict via scoped queries
        assertNull(syncStateDao.getConflict(SCOPE_A, "debt-legacy-1"))
        assertNull(syncStateDao.getConflict(SCOPE_A, "DEBT", "debt-legacy-1"))
        assertNull(syncStateDao.getConflict(SCOPE_B, "debt-legacy-1"))
        assertNull(syncStateDao.getConflict(SCOPE_B, "DEBT", "debt-legacy-1"))

        assertEquals(0, syncStateDao.getConflictCount(SCOPE_A))
        assertEquals(0, syncStateDao.getConflictCount(SCOPE_B))
        assertEquals(emptyList(), syncStateDao.getAllConflicts(SCOPE_A))
        assertEquals(emptyList(), syncStateDao.getAllConflicts(SCOPE_B))
        assertEquals(emptyList(), syncStateDao.getConflictsByEntityType(SCOPE_A, "DEBT"))
        assertEquals(emptyList(), syncStateDao.getConflictsByEntityType(SCOPE_B, "DEBT"))
        assertEquals(0, syncStateDao.observeConflictCount(SCOPE_A).first())
        assertEquals(emptyList(), syncStateDao.observeConflicts(SCOPE_A).first())

        // Deleting from User A or B returns 0 and does not delete legacy row
        val deletedA = syncStateDao.deleteConflict(SCOPE_A, "DEBT", "debt-legacy-1")
        assertEquals(0, deletedA)

        // Direct query on legacy scope confirms it still exists
        val legacyDirect = syncStateDao.getConflict(SCOPE_LEGACY, "DEBT", "debt-legacy-1")
        assertNotNull(legacyDirect)
        assertEquals("op-legacy", legacyDirect.operationId)
    }
}
