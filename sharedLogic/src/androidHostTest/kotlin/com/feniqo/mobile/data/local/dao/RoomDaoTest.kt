package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
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

            dao.markInFlight("op-1", 1_000L)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.markFailed("op-1", 1, "network_error", 2_000L, 1_500L)
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
            dao.markFailed("op-2", 1, "timeout", 2_000L, 1_500L)
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

    private companion object {
        val USER_ID = EntityId("user-1")
        val CATEGORY_ID = EntityId("category-1")
        val TRANSACTION_ID = EntityId("transaction-1")
        val TAG_ID = EntityId("tag-1")
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
        val SYNC = newSyncMetadata(NOW.toEpochMilliseconds())
    }
}
