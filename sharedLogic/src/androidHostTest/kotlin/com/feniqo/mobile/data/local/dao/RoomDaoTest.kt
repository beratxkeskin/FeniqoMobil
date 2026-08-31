package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Tag
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionTag
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.YearMonth
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    @Test
    fun transaction_with_tags_is_written_atomically_and_observed_from_room() = runTest {
        val database = inMemoryDatabase()
        try {
            val category = category()
            val transaction = transaction()
            val tag = Tag(TAG_ID, USER_ID, null, "zorunlu", NOW)
            val relation = TransactionTag(transaction.id, tag.id)

            database.categoryDao().upsert(category.toEntity(SYNC))
            database.transactionDao().upsertWithTags(
                transaction = transaction.toEntity(SYNC),
                tags = listOf(tag.toEntity(SYNC)),
                links = listOf(relation.toEntity(NOW.toEpochMilliseconds(), SYNC)),
            )

            val stored = database.transactionDao().observeWithTags(TRANSACTION_ID.value).first()
            assertEquals(TRANSACTION_ID.value, stored?.transaction?.id)
            assertEquals(listOf(TAG_ID.value), stored?.tags?.map { it.id })

            val filtered = database.transactionDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                startDate = "2026-08-01",
                endDate = "2026-08-31",
                typeCode = TransactionType.EXPENSE.name,
                categoryId = CATEGORY_ID.value,
                paymentMethodCode = PaymentMethod.DEBIT_CARD.name,
                searchQuery = "market",
            ).first()
            assertEquals(listOf(TRANSACTION_ID.value), filtered.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun soft_deleted_row_is_hidden_from_normal_queries() = runTest {
        val database = inMemoryDatabase()
        try {
            val categoryEntity = category().toEntity(SYNC)
            database.categoryDao().upsert(categoryEntity)
            assertEquals(CATEGORY_ID.value, database.categoryDao().observeById(CATEGORY_ID.value).first()?.id)

            database.categoryDao().upsert(
                categoryEntity.copy(
                    sync = categoryEntity.sync.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
                ),
            )

            assertNull(database.categoryDao().observeById(CATEGORY_ID.value).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun default_and_custom_categories_are_observed_together_and_filtered_by_type_correctly() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultExpense = CategoryEntity(
                id = "11111111-1111-4111-8111-111111111112",
                ownerId = null,
                workspaceId = null,
                scopeKey = "system",
                name = "Market",
                normalizedName = "market",
                slug = "market",
                typeCode = "EXPENSE",
                colorHex = "#EF4444",
                iconKey = "shopping-cart",
                isDefault = true,
                createdAtEpochMillis = 1_000L,
                sync = SYNC,
            )
            val defaultIncome = CategoryEntity(
                id = "11111111-1111-4111-8111-111111111101",
                ownerId = null,
                workspaceId = null,
                scopeKey = "system",
                name = "Maaş",
                normalizedName = "maaş",
                slug = "maas",
                typeCode = "INCOME",
                colorHex = "#10B981",
                iconKey = "briefcase",
                isDefault = true,
                createdAtEpochMillis = 1_000L,
                sync = SYNC,
            )
            val customExpense = CategoryEntity(
                id = "custom-exp-1",
                ownerId = USER_ID.value,
                workspaceId = null,
                scopeKey = "user:${USER_ID.value}",
                name = "Hobiler",
                normalizedName = "hobiler",
                slug = null,
                typeCode = "EXPENSE",
                colorHex = "#EC4899",
                iconKey = "film",
                isDefault = false,
                createdAtEpochMillis = 2_000L,
                sync = SYNC,
            )
            val customIncome = CategoryEntity(
                id = "custom-inc-1",
                ownerId = USER_ID.value,
                workspaceId = null,
                scopeKey = "user:${USER_ID.value}",
                name = "Ek Gelir",
                normalizedName = "ek gelir",
                slug = null,
                typeCode = "INCOME",
                colorHex = "#34D399",
                iconKey = "laptop",
                isDefault = false,
                createdAtEpochMillis = 2_000L,
                sync = SYNC,
            )
            val deletedExpense = customExpense.copy(
                id = "deleted-exp-1",
                name = "Eski Gider",
                normalizedName = "eski gider",
                sync = SYNC.copy(deletedAtEpochMillis = 3_000L),
            )

            database.categoryDao().upsert(defaultExpense)
            database.categoryDao().upsert(defaultIncome)
            database.categoryDao().upsert(customExpense)
            database.categoryDao().upsert(customIncome)
            database.categoryDao().upsert(deletedExpense)

            // Test Expense Filter: default + custom active expense, sorted default first
            val expenseCategories = database.categoryDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                typeCode = "EXPENSE",
            ).first()
            assertEquals(listOf(defaultExpense.id, customExpense.id), expenseCategories.map { it.id })

            // Test Income Filter: default + custom active income, sorted default first
            val incomeCategories = database.categoryDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                typeCode = "INCOME",
            ).first()
            assertEquals(listOf(defaultIncome.id, customIncome.id), incomeCategories.map { it.id })

            // Test History Lookup: includes soft-deleted category
            val historyCategories = database.categoryDao().observeAllForHistoryLookup(
                ownerId = USER_ID.value,
                workspaceId = null,
            ).first()
            assertTrue(historyCategories.any { it.id == deletedExpense.id })
            assertEquals(5, historyCategories.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_transaction_and_outbox_survive_database_reopen_and_store_backoff() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "feniqo-outbox-${System.nanoTime()}.db"
        var nowEpochMillis = 10_000L

        try {
            val firstDatabase = persistentDatabase(context, databaseName)
            firstDatabase.categoryDao().upsert(category().toEntity(SYNC))
            OfflineWriteQueue(
                mutationDao = firstDatabase.localMutationDao(),
                operationDao = firstDatabase.syncOperationDao(),
                nowEpochMillisProvider = { nowEpochMillis },
                operationIdFactory = { "operation-persisted" },
            ).enqueueTransaction(
                entity = transaction().toEntity(SYNC),
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
            )
            firstDatabase.close()

            val reopenedDatabase = persistentDatabase(context, databaseName)
            try {
                assertEquals(
                    TRANSACTION_ID.value,
                    reopenedDatabase.transactionDao().observeById(TRANSACTION_ID.value).first()?.id,
                )
                assertEquals(1, reopenedDatabase.syncOperationDao().observePendingCount().first())

                val queue = OfflineWriteQueue(
                    mutationDao = reopenedDatabase.localMutationDao(),
                    operationDao = reopenedDatabase.syncOperationDao(),
                    nowEpochMillisProvider = { nowEpochMillis },
                    operationIdFactory = { "unused" },
                )
                assertTrue(queue.markInFlight("operation-persisted"))
                assertTrue(queue.recordFailure("operation-persisted", "ağ bağlantısı yok"))

                val failed = reopenedDatabase.syncOperationDao().getById("operation-persisted")
                assertEquals(1, failed?.attemptCount)
                assertEquals("ağ bağlantısı yok", failed?.lastError)
                assertEquals(25_000L, failed?.nextAttemptAtEpochMillis)
                assertTrue(queue.getReadyOperations().isEmpty())

                nowEpochMillis = 25_000L
                assertEquals(listOf("operation-persisted"), queue.getReadyOperations().map { it.operationId })
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun entity_write_is_rolled_back_when_outbox_insert_fails() = runTest {
        val database = inMemoryDatabase()
        try {
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                nowEpochMillisProvider = { 10_000L },
                operationIdFactory = { "duplicate-operation" },
            )
            queue.enqueueCategory(category().toEntity(SYNC), OutboxOperationType.CREATE)

            val secondCategory = category().copy(
                id = EntityId("category-2"),
                name = "Ulaşım",
            ).toEntity(SYNC)
            assertFailsWith<Exception> {
                queue.enqueueCategory(secondCategory, OutboxOperationType.CREATE)
            }

            assertNull(database.categoryDao().observeById(secondCategory.id).first())
            assertEquals(1, database.syncOperationDao().observePendingCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun create_update_and_delete_events_are_kept_in_order() = runTest {
        val database = inMemoryDatabase()
        try {
            var nowEpochMillis = 1_000L
            var operationNumber = 0
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                nowEpochMillisProvider = { nowEpochMillis },
                operationIdFactory = { "operation-${++operationNumber}" },
            )
            val created = category().toEntity(SYNC)
            queue.enqueueCategory(created, OutboxOperationType.CREATE)

            nowEpochMillis = 2_000L
            val updated = created.copy(
                name = "Süpermarket",
                normalizedName = "süpermarket",
                sync = created.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    localUpdatedAtEpochMillis = nowEpochMillis,
                ),
            )
            queue.enqueueCategory(updated, OutboxOperationType.UPDATE)

            nowEpochMillis = 3_000L
            val deleted = updated.copy(
                sync = updated.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = nowEpochMillis,
                    localUpdatedAtEpochMillis = nowEpochMillis,
                ),
            )
            queue.enqueueCategory(deleted, OutboxOperationType.DELETE)

            assertEquals(
                listOf("CREATE", "UPDATE", "DELETE"),
                queue.getReadyOperations().map { it.operationTypeCode },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_write_queue_triggers_background_sync_scheduler_after_successful_mutation() = runTest {
        val database = inMemoryDatabase()
        try {
            var outboxSyncCalled = 0
            val scheduler = object : com.feniqo.mobile.domain.sync.BackgroundSyncScheduler {
                override fun scheduleInitialSync() {}
                override fun scheduleOutboxSync() { outboxSyncCalled++ }
                override fun cancelSyncWork() {}
            }
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                syncScheduler = scheduler,
                nowEpochMillisProvider = { 1_000L },
                operationIdFactory = { "op-1" },
            )
            val category = category().toEntity(newSyncMetadata(1_000L).copy(syncStatus = SyncStatus.PENDING_CREATE.name))
            queue.enqueueCategory(category, OutboxOperationType.CREATE)

            assertEquals(1, outboxSyncCalled)
        } finally {
            database.close()
        }
    }

    @Test
    fun sync_user_states_stores_and_observes_last_successful_sync_at_per_user_isolated() = runTest {
        val database = inMemoryDatabase()
        try {
            val dao = database.syncStateDao()

            assertNull(dao.getUserState("user-1"))
            assertNull(dao.observeLastSuccessfulSyncAt("user-1").first())

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-1",
                    lastSuccessfulSyncAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-2",
                    lastSuccessfulSyncAtEpochMillis = 2_000L,
                    updatedAtEpochMillis = 2_000L,
                ),
            )

            assertEquals(1_000L, dao.observeLastSuccessfulSyncAt("user-1").first())
            assertEquals(2_000L, dao.observeLastSuccessfulSyncAt("user-2").first())
            assertNull(dao.observeLastSuccessfulSyncAt("user-3").first())

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-1",
                    lastSuccessfulSyncAtEpochMillis = 3_000L,
                    updatedAtEpochMillis = 3_000L,
                ),
            )
            assertEquals(3_000L, dao.observeLastSuccessfulSyncAt("user-1").first())
            assertEquals(2_000L, dao.observeLastSuccessfulSyncAt("user-2").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun observe_failed_count_tracks_pending_in_flight_failed_and_retried_operations() = runTest {
        val database = inMemoryDatabase()
        try {
            val dao = database.syncOperationDao()

            assertEquals(0, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-1",
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-1",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.claimOperation("op-1", 1_000L)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.markFailed("op-1", "network_error", 2_000L, 1_500L)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(1, dao.observeFailedCount().first())

            dao.insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-2",
                    entityTypeCode = "TRANSACTION",
                    entityId = "tx-1",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            dao.markFailed("op-2", "timeout", 2_000L, 1_500L)
            assertEquals(2, dao.observePendingCount().first())
            assertEquals(2, dao.observeFailedCount().first())

            dao.retryAllFailed(2_000L)
            assertEquals(2, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.deleteCompleted("op-1")
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_write_queue_observe_failed_count_delegates_to_dao() = runTest {
        val database = inMemoryDatabase()
        try {
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
            )
            assertEquals(0, queue.observeFailedCount().first())

            database.syncOperationDao().insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-f",
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-f",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "FAILED",
                    attemptCount = 1,
                    lastError = "error",
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            assertEquals(1, queue.observeFailedCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_3_to_4_creates_sync_user_states_and_preserves_existing_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v3_to_v4.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v3Db = migrationHelper.createDatabase(testDbPath, 3)
        try {
            v3Db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, 'user-1')")
        } finally {
            v3Db.close()
        }

        val v4Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            4,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_3_4,
        )
        try {
            val cursor = v4Db.query("SELECT entity_type_code, updated_at_epoch_ms FROM sync_cursors WHERE entity_type_code = 'PROFILE'")
            assertTrue(cursor.moveToFirst())
            assertEquals("PROFILE", cursor.getString(0))
            assertEquals(1000L, cursor.getLong(1))
            cursor.close()

            v4Db.execSQL("INSERT INTO sync_user_states (user_id, last_successful_sync_at_epoch_ms, updated_at_epoch_ms) VALUES ('user-1', 5000, 5000)")
            val userStateCursor = v4Db.query("SELECT user_id, last_successful_sync_at_epoch_ms, updated_at_epoch_ms FROM sync_user_states WHERE user_id = 'user-1'")
            assertTrue(userStateCursor.moveToFirst())
            assertEquals("user-1", userStateCursor.getString(0))
            assertEquals(5000L, userStateCursor.getLong(1))
            assertEquals(5000L, userStateCursor.getLong(2))
            userStateCursor.close()
        } finally {
            v4Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migration_4_to_5_adds_v2_columns_and_preserves_existing_operations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v4_to_v5.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v4Db = migrationHelper.createDatabase(testDbPath, 4)
        try {
            v4Db.execSQL(
                """
                INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'legacy-op-1', 'CATEGORY', 'cat-1', 'UPDATE',
                    1, 'PENDING', 0, NULL,
                    1000, 1000, 1000
                )
                """.trimIndent(),
            )
        } finally {
            v4Db.close()
        }

        val v5Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            5,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_4_5,
        )
        try {
            val cursor = v5Db.query(
                "SELECT operation_id, entity_type_code, entity_id, protocol_version, is_blocked, payload_json, predecessor_operation_id FROM sync_operations WHERE operation_id = 'legacy-op-1'",
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-op-1", cursor.getString(0))
            assertEquals("CATEGORY", cursor.getString(1))
            assertEquals("cat-1", cursor.getString(2))
            assertEquals(1, cursor.getInt(3)) // default protocol_version = 1
            assertEquals(0, cursor.getInt(4)) // default is_blocked = 0
            assertTrue(cursor.isNull(5)) // payload_json = null
            assertTrue(cursor.isNull(6)) // predecessor_operation_id = null
            cursor.close()

            // Insert new v2 operation with payload and predecessor
            v5Db.execSQL(
                """
                INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, payload_json, predecessor_operation_id, is_blocked,
                    protocol_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'v2-op-2', 'CATEGORY', 'cat-1', 'UPDATE',
                    NULL, '{"name":"Giyim"}', 'legacy-op-1', 1,
                    2, 'PENDING', 0, NULL,
                    2000, 2000, 2000
                )
                """.trimIndent(),
            )
            val v2Cursor = v5Db.query(
                "SELECT operation_id, protocol_version, is_blocked, payload_json, predecessor_operation_id FROM sync_operations WHERE operation_id = 'v2-op-2'",
            )
            assertTrue(v2Cursor.moveToFirst())
            assertEquals("v2-op-2", v2Cursor.getString(0))
            assertEquals(2, v2Cursor.getInt(1))
            assertEquals(1, v2Cursor.getInt(2))
            assertEquals("{\"name\":\"Giyim\"}", v2Cursor.getString(3))
            assertEquals("legacy-op-1", v2Cursor.getString(4))
            v2Cursor.close()
        } finally {
            v5Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun transactionDao_observeByIdAndOwner_isolates_by_owner_and_deleted_status() = runTest {
        val database = inMemoryDatabase()
        try {
            val category = category().toEntity(SYNC)
            database.categoryDao().upsert(category)

            val trxUser1 = transaction().toEntity(SYNC)
            val trxUser2 = transaction().copy(id = EntityId("t2"), ownerId = EntityId("user-2")).toEntity(SYNC)
            val trxDeleted = transaction().copy(id = EntityId("t3")).toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.transactionDao().upsert(trxUser1)
            database.transactionDao().upsert(trxUser2)
            database.transactionDao().upsert(trxDeleted)

            // User 1 sees their own active transaction
            val observed1 = database.transactionDao().observeByIdAndOwner("transaction-1", "user-1").first()
            assertEquals("transaction-1", observed1?.id)

            val fetched1 = database.transactionDao().getByIdAndOwner("transaction-1", "user-1")
            assertEquals("transaction-1", fetched1?.id)

            // User 1 cannot see user 2's transaction
            val observedOther = database.transactionDao().observeByIdAndOwner("t2", "user-1").first()
            assertNull(observedOther)

            val fetchedOther = database.transactionDao().getByIdAndOwner("t2", "user-1")
            assertNull(fetchedOther)

            // Deleted transaction is not returned
            val observedDel = database.transactionDao().observeByIdAndOwner("t3", "user-1").first()
            assertNull(observedDel)
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_observeByIdAndOwner_allows_default_and_matching_owner_only() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultCat = category().copy(id = EntityId("c-def"), ownerId = null, isDefault = true, name = "Varsayılan").toEntity(SYNC)
            val user1Cat = category().copy(id = EntityId("c-u1"), ownerId = EntityId("user-1"), name = "Market").toEntity(SYNC)
            val user2Cat = category().copy(id = EntityId("c-u2"), ownerId = EntityId("user-2"), name = "Giyim").toEntity(SYNC)
            val deletedCat = category().copy(id = EntityId("c-del"), ownerId = EntityId("user-1"), name = "Eski Kategori").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.categoryDao().upsert(defaultCat)
            database.categoryDao().upsert(user1Cat)
            database.categoryDao().upsert(user2Cat)
            database.categoryDao().upsert(deletedCat)

            // Default category can be observed by any user
            val obsDef = database.categoryDao().observeByIdAndOwner("c-def", "user-1").first()
            assertEquals("c-def", obsDef?.id)

            // User 1 can observe their own category
            val obsU1 = database.categoryDao().observeByIdAndOwner("c-u1", "user-1").first()
            assertEquals("c-u1", obsU1?.id)

            // User 1 cannot observe User 2's category
            val obsU2 = database.categoryDao().observeByIdAndOwner("c-u2", "user-1").first()
            assertNull(obsU2)

            // Deleted category is not returned
            val obsDel = database.categoryDao().observeByIdAndOwner("c-del", "user-1").first()
            assertNull(obsDel)
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_observeAllForHistoryLookup_includes_matching_owner_deleted_and_defaults_only() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultCat = category().copy(id = EntityId("c-def"), ownerId = null, isDefault = true, name = "Varsayılan").toEntity(SYNC)
            val user1Active = category().copy(id = EntityId("c-u1-act"), ownerId = EntityId("user-1"), name = "Market").toEntity(SYNC)
            val user1Deleted = category().copy(id = EntityId("c-u1-del"), ownerId = EntityId("user-1"), name = "Eski Kategori").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))
            val user2Deleted = category().copy(id = EntityId("c-u2-del"), ownerId = EntityId("user-2"), name = "Diğer Kullanıcı").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.categoryDao().upsert(defaultCat)
            database.categoryDao().upsert(user1Active)
            database.categoryDao().upsert(user1Deleted)
            database.categoryDao().upsert(user2Deleted)

            val historyList = database.categoryDao().observeAllForHistoryLookup("user-1", null).first()
            assertEquals(3, historyList.size)
            assertTrue(historyList.any { it.id == "c-def" })
            assertTrue(historyList.any { it.id == "c-u1-act" })
            assertTrue(historyList.any { it.id == "c-u1-del" }) // Silinmiş kategoriyi içerir
            assertTrue(historyList.none { it.id == "c-u2-del" }) // Başka kullanıcının silinmişi dönmez
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_rejects_corrupted_default_category_with_other_owner() = runTest {
        val database = inMemoryDatabase()
        try {
            val realDefault = category().copy(id = EntityId("c-real-def"), ownerId = null, isDefault = true, name = "Gerçek Varsayılan").toEntity(SYNC)
            val corruptedDefault = CategoryEntity(
                id = "c-bad-def",
                ownerId = "user-2",
                workspaceId = null,
                scopeKey = "personal:user-2",
                name = "Bozuk Varsayılan",
                normalizedName = "bozuk varsayilan",
                slug = null,
                typeCode = "EXPENSE",
                colorHex = "#FF0000",
                iconKey = null,
                isDefault = true,
                createdAtEpochMillis = 1000L,
                sync = SYNC,
            )
            val user1Category = category().copy(id = EntityId("c-u1"), ownerId = EntityId("user-1"), isDefault = false, name = "Kullanıcı 1").toEntity(SYNC)

            database.categoryDao().upsert(realDefault)
            database.categoryDao().upsert(corruptedDefault)
            database.categoryDao().upsert(user1Category)

            // observeAll: only real default and user1 category
            val activeList = database.categoryDao().observeAll("user-1", null, null).first()
            assertEquals(2, activeList.size)
            assertTrue(activeList.any { it.id == "c-real-def" })
            assertTrue(activeList.any { it.id == "c-u1" })
            assertTrue(activeList.none { it.id == "c-bad-def" })

            // observeByIdAndOwner: returns null for corrupted default owned by other user
            val obsBad = database.categoryDao().observeByIdAndOwner("c-bad-def", "user-1").first()
            assertNull(obsBad)

            // getByIdAndOwner: returns null for corrupted default owned by other user
            val getBad = database.categoryDao().getByIdAndOwner("c-bad-def", "user-1")
            assertNull(getBad)

            // observeAllForHistoryLookup: does not return corrupted default
            val historyList = database.categoryDao().observeAllForHistoryLookup("user-1", null).first()
            assertEquals(2, historyList.size)
            assertTrue(historyList.none { it.id == "c-bad-def" })
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionKeepingTags_preserves_cross_refs_during_update_and_delete() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val trx = transaction().toEntity(SYNC)
            val tag = Tag(TAG_ID, USER_ID, null, "Yemek", NOW).toEntity(SYNC)
            val link = TransactionTag(transaction().id, TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val createOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = "op-1",
                entityTypeCode = "TRANSACTION",
                entityId = trx.id,
                operationTypeCode = OutboxOperationType.CREATE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )

            // 1. Initial write with tags
            database.localMutationDao().upsertTransactionAndEnqueue(
                entity = trx,
                tags = listOf(tag),
                tagLinks = listOf(link),
                operation = createOp,
            )

            val initialWithTags = database.transactionDao().observeWithTags(trx.id).first()
            assertEquals(1, initialWithTags?.tags?.size)
            assertEquals("Yemek", initialWithTags?.tags?.first()?.name)

            // 2. Update using upsertTransactionKeepingTagsAndEnqueue
            val updatedTrx = trx.copy(description = "Güncellenmiş açıklama")
            val updateOp = createOp.copy(operationId = "op-2", operationTypeCode = OutboxOperationType.UPDATE.name)
            database.localMutationDao().upsertTransactionKeepingTagsAndEnqueue(
                entity = updatedTrx,
                operation = updateOp,
            )

            val afterUpdateWithTags = database.transactionDao().observeWithTags(trx.id).first()
            assertEquals("Güncellenmiş açıklama", afterUpdateWithTags?.transaction?.description)
            assertEquals(1, afterUpdateWithTags?.tags?.size)
            assertEquals("Yemek", afterUpdateWithTags?.tags?.first()?.name)

            // 3. Soft-delete using upsertTransactionKeepingTagsAndEnqueue
            val deletedTrx = updatedTrx.copy(sync = SYNC.copy(deletedAtEpochMillis = 5000L))
            val deleteOp = createOp.copy(operationId = "op-3", operationTypeCode = OutboxOperationType.DELETE.name)
            database.localMutationDao().upsertTransactionKeepingTagsAndEnqueue(
                entity = deletedTrx,
                operation = deleteOp,
            )

            // Outbox operations exist
            val pendingCount = database.syncOperationDao().observePendingCount().first()
            assertEquals(3, pendingCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionsAndEnqueue_writes_batch_atomically_and_rolls_back_on_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val tag = Tag(TAG_ID, USER_ID, null, "Zorunlu", NOW).toEntity(SYNC)

            val t1 = transaction().toEntity(SYNC).copy(id = "batch-trx-1", description = "Taksit 1/3")
            val t2 = transaction().toEntity(SYNC).copy(id = "batch-trx-2", description = "Taksit 2/3")
            val t3 = transaction().toEntity(SYNC).copy(id = "batch-trx-3", description = "Taksit 3/3")

            val link1 = TransactionTag(EntityId(t1.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)

            fun makeOp(opId: String, entityId: String) = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = opId,
                entityTypeCode = "TRANSACTION",
                entityId = entityId,
                operationTypeCode = OutboxOperationType.CREATE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )

            val successUnits = listOf(
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t1,
                    tags = listOf(tag),
                    tagLinks = listOf(link1),
                    operation = makeOp("op-b-1", t1.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t2,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    operation = makeOp("op-b-2", t2.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t3,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    operation = makeOp("op-b-3", t3.id),
                ),
            )

            // 1. Success batch write
            database.localMutationDao().upsertTransactionsAndEnqueue(successUnits)

            val activeTrxs = database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first()
            assertEquals(3, activeTrxs.size)
            assertEquals(3, database.syncOperationDao().observePendingCount().first())
            assertEquals(1, database.transactionDao().observeWithTags(t1.id).first()?.tags?.size)

            // 2. Rollback test on error
            val failUnit1 = com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                entity = transaction().toEntity(SYNC).copy(id = "rollback-trx-1"),
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = makeOp("op-rb-1", "rollback-trx-1"),
            )
            val failUnit2 = com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                entity = transaction().toEntity(SYNC).copy(
                    id = "rollback-trx-2",
                    categoryId = "non-existing-category-id", // Foreign key violation!
                ),
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = makeOp("op-rb-2", "rollback-trx-2"),
            )

            assertFailsWith<androidx.sqlite.SQLiteException> {
                database.localMutationDao().upsertTransactionsAndEnqueue(listOf(failUnit1, failUnit2))
            }

            // Verify full rollback: rollback-trx-1 was NOT saved, and op-rb-1 was NOT enqueued!
            val rollbackTrx = database.transactionDao().getByIdAndOwner("rollback-trx-1", USER_ID.value)
            assertNull(rollbackTrx)
            assertEquals(3, database.syncOperationDao().observePendingCount().first()) // Still 3 from previous batch
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionsKeepingTagsAndEnqueue_softDeletes_batch_atomically_preserves_tags_and_rolls_back_on_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val tag = Tag(TAG_ID, USER_ID, null, "Etiket", NOW).toEntity(SYNC)
            database.transactionDao().upsertTags(listOf(tag))

            val t1 = transaction().toEntity(SYNC).copy(id = "del-trx-1", description = "Taksit 1/3")
            val t2 = transaction().toEntity(SYNC).copy(id = "del-trx-2", description = "Taksit 2/3")
            val t3 = transaction().toEntity(SYNC).copy(id = "del-trx-3", description = "Taksit 3/3")
            database.transactionDao().upsert(t1)
            database.transactionDao().upsert(t2)
            database.transactionDao().upsert(t3)

            val link1 = TransactionTag(EntityId(t1.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val link2 = TransactionTag(EntityId(t2.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val link3 = TransactionTag(EntityId(t3.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            database.transactionDao().upsertTagLinks(listOf(link1, link2, link3))

            // Verify initial tag links
            assertEquals(1, database.transactionDao().observeWithTags(t1.id).first()?.tags?.size)
            assertEquals(1, database.transactionDao().observeWithTags(t2.id).first()?.tags?.size)
            assertEquals(1, database.transactionDao().observeWithTags(t3.id).first()?.tags?.size)

            fun makeDelOp(opId: String, entityId: String) = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = opId,
                entityTypeCode = "TRANSACTION",
                entityId = entityId,
                operationTypeCode = OutboxOperationType.DELETE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 5000L,
                createdAtEpochMillis = 5000L,
                updatedAtEpochMillis = 5000L,
            )

            val deleteSync = SYNC.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 5000L)
            val deleteUnits = listOf(
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t1.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-1", t1.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t2.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-2", t2.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t3.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-3", t3.id),
                ),
            )

            // 1. Success batch soft-delete
            database.localMutationDao().upsertTransactionsKeepingTagsAndEnqueue(deleteUnits)

            val activeTrxs = database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first()
            assertEquals(0, activeTrxs.size) // All 3 are soft-deleted
            assertEquals(3, database.syncOperationDao().observePendingCount().first())

            // Verify tag links are PRESERVED in SQLite cross-ref table!
            val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_tags WHERE transaction_id IN ('del-trx-1', 'del-trx-2', 'del-trx-3')")
            cursor.moveToFirst()
            val preservedTagCount = cursor.getInt(0)
            cursor.close()
            assertEquals(3, preservedTagCount)

            // 2. Rollback test on error
            val t4 = transaction().toEntity(SYNC).copy(id = "rollback-del-4", description = "Taksit 4")
            database.transactionDao().upsert(t4)
            assertEquals(1, database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first().size)

            val rollbackDelUnit1 = com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                entity = t4.copy(sync = deleteSync),
                operation = makeDelOp("op-del-4", t4.id),
            )
            val rollbackDelUnit2 = com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                entity = t1.copy(categoryId = "non-existing-category-fk"), // Foreign key violation!
                operation = makeDelOp("op-del-5", t1.id),
            )

            assertFailsWith<androidx.sqlite.SQLiteException> {
                database.localMutationDao().upsertTransactionsKeepingTagsAndEnqueue(listOf(rollbackDelUnit1, rollbackDelUnit2))
            }

            // Verify rollback: t4 is STILL active (not soft-deleted!)
            val t4AfterRollback = database.transactionDao().getByIdAndOwner(t4.id, USER_ID.value)
            assertNotNull(t4AfterRollback)
            assertNull(t4AfterRollback.sync.deletedAtEpochMillis)
            assertEquals(3, database.syncOperationDao().observePendingCount().first()) // Still 3, op-del-4 was rolled back
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getByIdAndOwner_returnsOnlyActiveOwnedBudget() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category()
            database.categoryDao().upsert(cat.toEntity(SYNC))

            val activeBudget = budget("b-active", ownerId = USER_ID, categoryId = cat.id)
            val otherUserBudget = budget("b-other-user", ownerId = EntityId("other-user"), categoryId = cat.id)
            val deletedBudget = budget("b-deleted", ownerId = USER_ID, categoryId = cat.id)

            database.budgetDao().upsert(activeBudget.toEntity(SYNC))
            database.budgetDao().upsert(otherUserBudget.toEntity(SYNC))
            database.budgetDao().upsert(
                deletedBudget.toEntity(
                    SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
                ),
            )

            // Doğru owner aktif kaydı alır
            val resultOwned = database.budgetDao().getByIdAndOwner(activeBudget.id.value, USER_ID.value)
            assertNotNull(resultOwned)
            assertEquals("b-active", resultOwned.id)

            // Yanlış owner null alır
            val resultWrongOwner = database.budgetDao().getByIdAndOwner(activeBudget.id.value, "wrong-owner")
            assertNull(resultWrongOwner)

            // Başka kullanıcının kaydı null alır
            val resultOtherUser = database.budgetDao().getByIdAndOwner(otherUserBudget.id.value, USER_ID.value)
            assertNull(resultOtherUser)

            // Soft-delete edilmiş kayıt null alır
            val resultDeleted = database.budgetDao().getByIdAndOwner(deletedBudget.id.value, USER_ID.value)
            assertNull(resultDeleted)
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getAnyByScopeCategoryAndMonth_returnsSoftDeletedBudget() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category()
            database.categoryDao().upsert(cat.toEntity(SYNC))

            val deletedBudget = budget("b-soft-del", ownerId = USER_ID, categoryId = cat.id, month = "2026-08")
            val deletedEntity = deletedBudget.toEntity(
                SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
            )
            database.budgetDao().upsert(deletedEntity)

            // deleted_at dolu kayıt reaktivasyon sorgusuyla bulunur
            val found = database.budgetDao().getAnyByScopeCategoryAndMonth(
                scopeKey = deletedEntity.scopeKey,
                categoryId = cat.id.value,
                month = "2026-08",
            )
            assertNotNull(found)
            assertEquals("b-soft-del", found.id)
            assertEquals(NOW.toEpochMilliseconds(), found.sync.deletedAtEpochMillis)

            // Yanlış scope null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth("wrong-scope", cat.id.value, "2026-08"))

            // Yanlış category null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth(deletedEntity.scopeKey, "other-cat", "2026-08"))

            // Yanlış month null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth(deletedEntity.scopeKey, cat.id.value, "2026-09"))
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getForMonth_filtersOwnerWorkspaceMonthAndDeletedRows() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat1 = category().copy(id = EntityId("cat-1"), name = "Market 1")
            val cat2 = category().copy(id = EntityId("cat-2"), name = "Market 2")
            database.categoryDao().upsert(cat1.toEntity(SYNC))
            database.categoryDao().upsert(cat2.toEntity(SYNC))

            val ws = Workspace(id = EntityId("ws-1"), name = "Ortak Alan", ownerId = USER_ID, createdAt = NOW)
            database.workspaceDao().upsertWorkspace(ws.toEntity(SYNC))

            val valid1 = budget("b-v1", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-08")
            val valid2 = budget("b-v2", ownerId = USER_ID, workspaceId = null, categoryId = cat2.id, month = "2026-08")
            val otherOwner = budget("b-other-owner", ownerId = EntityId("user-2"), workspaceId = null, categoryId = cat1.id, month = "2026-08")
            val otherWorkspace = budget("b-ws", ownerId = USER_ID, workspaceId = EntityId("ws-1"), categoryId = cat1.id, month = "2026-08")
            val otherMonth = budget("b-other-m", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-09")
            val deleted = budget("b-del", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-08")

            database.budgetDao().upsert(valid1.toEntity(SYNC))
            database.budgetDao().upsert(valid2.toEntity(SYNC))
            database.budgetDao().upsert(otherOwner.toEntity(SYNC))
            database.budgetDao().upsert(otherWorkspace.toEntity(SYNC))
            database.budgetDao().upsert(otherMonth.toEntity(SYNC))
            database.budgetDao().upsert(deleted.toEntity(SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds())))

            val personalResults = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = null,
                month = "2026-08",
            )
            assertEquals(listOf("b-v1", "b-v2"), personalResults.map { it.id })

            val wsResults = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = "ws-1",
                month = "2026-08",
            )
            assertEquals(listOf("b-ws"), wsResults.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getForMonth_ordersByCategoryId() = runTest {
        val database = inMemoryDatabase()
        try {
            val catZ = category().copy(id = EntityId("cat-z"), name = "Z Market")
            val catA = category().copy(id = EntityId("cat-a"), name = "A Market")
            val catM = category().copy(id = EntityId("cat-m"), name = "M Market")
            database.categoryDao().upsert(catZ.toEntity(SYNC))
            database.categoryDao().upsert(catA.toEntity(SYNC))
            database.categoryDao().upsert(catM.toEntity(SYNC))

            val bZ = budget("b-z", categoryId = catZ.id)
            val bA = budget("b-a", categoryId = catA.id)
            val bM = budget("b-m", categoryId = catM.id)

            // Insert in non-sorted order
            database.budgetDao().upsert(bZ.toEntity(SYNC))
            database.budgetDao().upsert(bA.toEntity(SYNC))
            database.budgetDao().upsert(bM.toEntity(SYNC))

            val results = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = null,
                month = "2026-08",
            )

            assertEquals(listOf("cat-a", "cat-m", "cat-z"), results.map { it.categoryId })
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    private fun persistentDatabase(context: Context, databaseName: String): FeniqoDatabase =
        Room.databaseBuilder<FeniqoDatabase>(
            context = context,
            name = databaseName,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    private fun category() = Category(
        id = CATEGORY_ID,
        ownerId = USER_ID,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#0A7A55"),
        icon = null,
        isDefault = false,
        createdAt = NOW,
    )

    private fun transaction() = Transaction(
        id = TRANSACTION_ID,
        ownerId = USER_ID,
        workspaceId = null,
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = CATEGORY_ID,
        description = "market alışverişi",
        paymentMethod = PaymentMethod.DEBIT_CARD,
        transactionDate = LocalDate(2026, 8, 5),
        receiptPath = null,
        installment = null,
        createdAt = NOW,
    )

    private fun budget(
        id: String = "budget-1",
        ownerId: EntityId = USER_ID,
        workspaceId: EntityId? = null,
        categoryId: EntityId = CATEGORY_ID,
        month: String = "2026-08",
        limitMinor: Long = 100_000L,
    ) = Budget(
        id = EntityId(id),
        ownerId = ownerId,
        workspaceId = workspaceId,
        categoryId = categoryId,
        month = YearMonth(month),
        limit = Money(limitMinor, Currency.TRY),
        createdAt = NOW,
    )

    private companion object {
        val USER_ID = EntityId("user-1")
        val CATEGORY_ID = EntityId("category-1")
        val TRANSACTION_ID = EntityId("transaction-1")
        val TAG_ID = EntityId("tag-1")
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
        val SYNC = newSyncMetadata(NOW.toEpochMilliseconds())
    }
}
