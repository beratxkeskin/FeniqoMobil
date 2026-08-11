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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {

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
