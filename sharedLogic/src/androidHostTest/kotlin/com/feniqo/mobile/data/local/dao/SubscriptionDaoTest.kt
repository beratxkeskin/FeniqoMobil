package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_6_7
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
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
class SubscriptionDaoTest {

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

    private fun testCategory(id: String = "cat-1", ownerId: String = "user-1"): CategoryEntity {
        return CategoryEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = null,
            scopeKey = "personal:$ownerId",
            name = "Eğlence",
            normalizedName = "eglence",
            slug = null,
            typeCode = "EXPENSE",
            colorHex = "#FF0000",
            iconKey = "movie",
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = testSync,
        )
    }

    private fun testWorkspace(id: String = "ws-1", ownerId: String = "user-1"): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            name = "İşyeri",
            normalizedName = "isyeri",
            ownerId = ownerId,
            createdAtEpochMillis = 1000L,
            sync = testSync,
        )
    }

    private fun testSubscription(
        id: String,
        ownerId: String = "user-1",
        workspaceId: String? = null,
        categoryId: String? = "cat-1",
        name: String = "Abonelik $id",
        nextRenewalDate: String = "2026-09-01",
        isActive: Boolean = true,
        deletedAtEpochMillis: Long? = null,
    ): SubscriptionEntity {
        return SubscriptionEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = workspaceId,
            name = name,
            amountMinor = 4999L,
            currencyCode = "TRY",
            categoryId = categoryId,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = nextRenewalDate,
            isActive = isActive,
            createdAtEpochMillis = 1000L,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    @Test
    fun observeAll_filtersPersonalScopeAndExcludesWorkspacesAndOtherOwners() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory("cat-1", "user-1"))
            db.workspaceDao().upsertWorkspace(testWorkspace("ws-1", "user-1"))

            val subPersonalUser1 = testSubscription("sub-1", ownerId = "user-1", workspaceId = null)
            val subWorkspaceUser1 = testSubscription("sub-2", ownerId = "user-1", workspaceId = "ws-1")
            val subPersonalUser2 = testSubscription("sub-3", ownerId = "user-2", workspaceId = null)

            db.subscriptionDao().upsertAll(listOf(subPersonalUser1, subWorkspaceUser1, subPersonalUser2))

            val personalResult = db.subscriptionDao().observeAll("user-1", null).first()
            assertEquals(listOf(subPersonalUser1), personalResult)

            val workspaceResult = db.subscriptionDao().observeAll("user-1", "ws-1").first()
            assertEquals(listOf(subWorkspaceUser1), workspaceResult)
        } finally {
            db.close()
        }
    }

    @Test
    fun observeAll_and_getById_excludeSoftDeleted_while_getAnyById_includesSoftDeleted() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory("cat-1", "user-1"))

            val activeSub = testSubscription("sub-active", ownerId = "user-1")
            val deletedSub = testSubscription("sub-deleted", ownerId = "user-1", deletedAtEpochMillis = 2000L)

            db.subscriptionDao().upsertAll(listOf(activeSub, deletedSub))

            val allList = db.subscriptionDao().observeAll("user-1", null).first()
            assertEquals(listOf(activeSub), allList)

            assertEquals(activeSub, db.subscriptionDao().getById("sub-active"))
            assertNull(db.subscriptionDao().getById("sub-deleted"))
            assertEquals(activeSub, db.subscriptionDao().observeById("sub-active").first())
            assertNull(db.subscriptionDao().observeById("sub-deleted").first())

            val anyActive = db.subscriptionDao().getAnyById("sub-active")
            val anyDeleted = db.subscriptionDao().getAnyById("sub-deleted")
            assertNotNull(anyActive)
            assertNotNull(anyDeleted)
            assertEquals("sub-deleted", anyDeleted.id)
            assertEquals(2000L, anyDeleted.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun nullableCategory_isPreserved_and_nullCategorySubscriptionsAreValid() = runTest {
        val db = inMemoryDatabase()
        try {
            val subWithoutCategory = testSubscription("sub-no-cat", ownerId = "user-1", categoryId = null)
            db.subscriptionDao().upsert(subWithoutCategory)

            val loaded = db.subscriptionDao().getById("sub-no-cat")
            assertNotNull(loaded)
            assertNull(loaded.categoryId)
        } finally {
            db.close()
        }
    }

    @Test
    fun getActiveSubscriptions_returnsOnlyActiveNonDeletedSubscriptions() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory("cat-1", "user-1"))

            val subActive = testSubscription("sub-active", isActive = true)
            val subPaused = testSubscription("sub-paused", isActive = false)
            val subDeleted = testSubscription("sub-del", isActive = true, deletedAtEpochMillis = 5000L)

            db.subscriptionDao().upsertAll(listOf(subActive, subPaused, subDeleted))

            val activeList = db.subscriptionDao().getActiveSubscriptions()
            assertEquals(listOf(subActive), activeList)
        } finally {
            db.close()
        }
    }

    @Test
    fun observeAll_ordersDeterministically_byNextRenewalDateAscThenIdAsc() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory("cat-1", "user-1"))

            val sub1 = testSubscription("sub-b", nextRenewalDate = "2026-09-15")
            val sub2 = testSubscription("sub-a", nextRenewalDate = "2026-09-15")
            val sub3 = testSubscription("sub-early", nextRenewalDate = "2026-09-01")
            val sub4 = testSubscription("sub-late", nextRenewalDate = "2026-10-01")

            db.subscriptionDao().upsertAll(listOf(sub1, sub2, sub3, sub4))

            val list = db.subscriptionDao().observeAll("user-1", null).first()
            assertEquals(listOf("sub-early", "sub-a", "sub-b", "sub-late"), list.map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun migration_6_to_7_createsSubscriptionsTable_andPreservesExistingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration-test-6-7.db"
        val testDbFile = File(context.filesDir, testDbName)
        val testDbPath = testDbFile.absolutePath
        context.deleteDatabase(testDbName)

        val v6Db = migrationHelper.createDatabase(testDbPath, 6)
        try {
            v6Db.execSQL(
                """
                INSERT INTO categories (
                    id, owner_id, workspace_id, scope_key, name, normalized_name,
                    slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'cat-1', 'user-1', NULL, 'personal:user-1', 'Market', 'market',
                    NULL, 'EXPENSE', '#FF0000', 'cart', 0, 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v6Db.execSQL(
                """
                INSERT INTO recurring_transactions (
                    id, owner_id, workspace_id, amount_minor, currency_code, type_code,
                    category_id, description, payment_method_code, frequency_code, `interval`,
                    start_date, end_date, last_generated_date, is_active, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'rec-1', 'user-1', NULL, 19900, 'TRY', 'EXPENSE',
                    'cat-1', 'Kira', 'BANK_TRANSFER', 'MONTHLY', 1,
                    '2026-08-01', NULL, NULL, 1, 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v6Db.close()
        }

        val v7Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            7,
            true,
            ANDROID_MIGRATION_6_7,
        )
        try {
            // Verify existing v6 data preserved
            val recCursor = v7Db.query("SELECT id, description, amount_minor FROM recurring_transactions WHERE id = 'rec-1'")
            assertTrue(recCursor.moveToFirst())
            assertEquals("rec-1", recCursor.getString(0))
            assertEquals("Kira", recCursor.getString(1))
            assertEquals(19900L, recCursor.getLong(2))
            recCursor.close()

            // Insert into new v7 subscriptions table
            v7Db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, owner_id, workspace_id, name, amount_minor, currency_code,
                    category_id, frequency_code, `interval`, start_date, end_date,
                    next_renewal_date, is_active, created_at_epoch_ms, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'sub-1', 'user-1', NULL, 'Spotify', 5999, 'TRY',
                    'cat-1', 'MONTHLY', 1, '2026-08-01', NULL,
                    '2026-09-01', 1, 1000, 'SYNCED',
                    1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val subCursor = v7Db.query("SELECT id, name, amount_minor, next_renewal_date FROM subscriptions WHERE id = 'sub-1'")
            assertTrue(subCursor.moveToFirst())
            assertEquals("sub-1", subCursor.getString(0))
            assertEquals("Spotify", subCursor.getString(1))
            assertEquals(5999L, subCursor.getLong(2))
            assertEquals("2026-09-01", subCursor.getString(3))
            subCursor.close()
        } finally {
            v7Db.close()
            context.deleteDatabase(testDbName)
        }
    }
}
