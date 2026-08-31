package com.feniqo.mobile.data.local.outbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecurringTransactionOfflineWriteQueueTest {

    private class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
        var scheduleCount = 0
        override fun scheduleInitialSync() {}
        override fun scheduleOutboxSync() {
            scheduleCount++
        }
        override fun cancelSyncWork() {}
    }

    @Test
    fun create_entity_and_v2_outbox_added_atomically() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = recurringEntity("rec-1", categoryId = "cat-1", amountMinor = 50000L, syncStatus = SyncStatus.PENDING_CREATE)

            val opId = queue.enqueueRecurringTransactionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"amountMinor":50000,"categoryId":"cat-1","frequency":"MONTHLY"}""",
            )

            // Entity Room'a yazıldı
            val stored = db.recurringTransactionDao().getById("rec-1")
            assertNotNull(stored)
            assertEquals(50000L, stored.amountMinor)

            // Protocol version 2 outbox satırı oluştu
            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals("RECURRING_TRANSACTION", op.entityTypeCode)
            assertEquals("rec-1", op.entityId)
            assertEquals("CREATE", op.operationTypeCode)
            assertEquals(2, op.protocolVersion)
            assertEquals("PENDING", op.statusCode)
            assertEquals(0, op.attemptCount)
            assertNull(op.baseVersion)
            assertNull(op.predecessorOperationId)
            assertFalse(op.isBlocked)

            // Scheduler tetiklendi
            assertEquals(1, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun pending_root_create_then_update_coalesces_payload_on_same_operation() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = recurringEntity("rec-2", categoryId = "cat-1", amountMinor = 50000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueRecurringTransactionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"amountMinor":50000}""",
            )

            val updatedEntity = entity.copy(
                amountMinor = 75000L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueRecurringTransactionV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"amountMinor":75000}""",
            )

            assertEquals(op1, op2)

            val stored = db.recurringTransactionDao().getById("rec-2")
            assertNotNull(stored)
            assertEquals(75000L, stored.amountMinor)

            val storedOp = db.syncOperationDao().getById(op1)
            assertNotNull(storedOp)
            assertEquals("CREATE", storedOp.operationTypeCode)
            assertEquals("""{"amountMinor":75000}""", storedOp.payloadJson)
            assertEquals(0, storedOp.attemptCount)
            assertFalse(storedOp.isBlocked)
            assertEquals(2, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun pending_root_create_then_delete_hard_deletes_entity_and_op_without_scheduling() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = recurringEntity("rec-3", categoryId = "cat-1", amountMinor = 50000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueRecurringTransactionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"amountMinor":50000}""",
            )
            assertEquals(1, scheduler.scheduleCount)

            val delEntity = entity.copy(
                sync = entity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val op2 = queue.enqueueRecurringTransactionV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            assertEquals(op1, op2)

            // Fiziksel silme: Tabloda kayıt kalmaz
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recurring_transactions WHERE id = 'rec-3'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()

            // Outbox kaydı silinir
            assertNull(db.syncOperationDao().getById(op1))
            assertEquals(0, db.syncOperationDao().observePendingCount().first())

            // Hard delete durumunda scheduler çağrılmaz (sayaç hala 1 olmalı)
            assertEquals(1, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun in_flight_create_then_update_creates_blocked_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = recurringEntity("rec-4", categoryId = "cat-1", amountMinor = 50000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueRecurringTransactionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"amountMinor":50000}""",
            )

            val claimed = queue.claimOperation(op1)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val updatedEntity = entity.copy(
                amountMinor = 80000L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueRecurringTransactionV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"amountMinor":80000}""",
            )

            val successor = db.syncOperationDao().getById(op2)
            assertNotNull(successor)
            assertEquals("UPDATE", successor.operationTypeCode)
            assertTrue(successor.isBlocked)
            assertEquals(op1, successor.predecessorOperationId)
            assertNull(successor.baseVersion)
            assertEquals(2, successor.protocolVersion)
        } finally {
            db.close()
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

    private fun createQueue(
        db: FeniqoDatabase,
        scheduler: BackgroundSyncScheduler? = null,
        nowProvider: () -> Long = { 1000L },
    ): OfflineWriteQueue {
        return OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
            syncScheduler = scheduler,
            nowEpochMillisProvider = nowProvider,
        )
    }

    private suspend fun insertCategory(db: FeniqoDatabase, id: String) {
        val category = CategoryEntity(
            id = id,
            ownerId = "user-1",
            workspaceId = null,
            scopeKey = "personal:user-1",
            name = "Kategori $id",
            normalizedName = "kategori $id",
            slug = "cat-slug-$id",
            typeCode = "EXPENSE",
            colorHex = "#4CAF50",
            iconKey = "tag",
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L),
        )
        db.categoryDao().upsert(category)
    }

    private fun recurringEntity(
        id: String,
        categoryId: String = "cat-1",
        amountMinor: Long = 50000L,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAt: Long? = null,
    ): RecurringTransactionEntity = RecurringTransactionEntity(
        id = id,
        ownerId = "user-1",
        workspaceId = null,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        typeCode = "EXPENSE",
        categoryId = categoryId,
        description = null,
        paymentMethodCode = "CREDIT_CARD",
        frequencyCode = "MONTHLY",
        interval = 1,
        startDate = "2026-08-01",
        endDate = null,
        lastGeneratedDate = null,
        isActive = true,
        createdAtEpochMillis = 1000L,
        sync = newSyncMetadata(1000L).copy(
            syncStatus = syncStatus.name,
            version = version,
            baseVersion = baseVersion,
            deletedAtEpochMillis = deletedAt,
        ),
    )
}
