package com.feniqo.mobile.data.local.outbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SubscriptionOfflineWriteQueueTest {

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
            val entity = subscriptionEntity("sub-1", categoryId = "cat-1", amountMinor = 5999L, syncStatus = SyncStatus.PENDING_CREATE)

            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Spotify","amountMinor":5999,"currency":"TRY","categoryId":"cat-1","frequency":"MONTHLY","interval":1,"startDate":"2026-08-01","nextRenewalDate":"2026-09-01","isActive":true}""",
            )

            // Entity Room'a yazıldı
            val stored = db.subscriptionDao().getById("sub-1")
            assertNotNull(stored)
            assertEquals("Spotify", stored.name)
            assertEquals(5999L, stored.amountMinor)
            assertEquals("cat-1", stored.categoryId)

            // Protocol version 2 outbox satırı oluştu
            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals("SUBSCRIPTION", op.entityTypeCode)
            assertEquals("sub-1", op.entityId)
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
    fun nullable_category_is_preserved_as_explicit_null_in_payload() = runTest {
        val db = inMemoryDatabase()
        try {
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = subscriptionEntity("sub-null-cat", categoryId = null, amountMinor = 12900L, syncStatus = SyncStatus.PENDING_CREATE)

            val payload = """{"name":"iCloud","amountMinor":12900,"currency":"TRY","categoryId":null,"frequency":"MONTHLY","interval":1,"startDate":"2026-08-01","nextRenewalDate":"2026-09-01","isActive":true}"""
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = payload,
            )

            val stored = db.subscriptionDao().getById("sub-null-cat")
            assertNotNull(stored)
            assertNull(stored.categoryId)

            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals(payload, op.payloadJson)
            assertTrue(op.payloadJson?.contains(""""categoryId":null""") == true)
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
            val entity = subscriptionEntity("sub-2", categoryId = "cat-1", amountMinor = 5999L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Spotify","amountMinor":5999}""",
            )

            val updatedEntity = entity.copy(
                amountMinor = 7999L,
                name = "Spotify Duo",
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueSubscriptionV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Spotify Duo","amountMinor":7999}""",
            )

            assertEquals(op1, op2)

            val stored = db.subscriptionDao().getById("sub-2")
            assertNotNull(stored)
            assertEquals("Spotify Duo", stored.name)
            assertEquals(7999L, stored.amountMinor)

            val storedOp = db.syncOperationDao().getById(op1)
            assertNotNull(storedOp)
            assertEquals("CREATE", storedOp.operationTypeCode)
            assertEquals("""{"name":"Spotify Duo","amountMinor":7999}""", storedOp.payloadJson)
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
            val entity = subscriptionEntity("sub-3", categoryId = "cat-1", amountMinor = 5999L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Spotify","amountMinor":5999}""",
            )
            assertEquals(1, scheduler.scheduleCount)

            val delEntity = entity.copy(
                sync = entity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val op2 = queue.enqueueSubscriptionV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            assertEquals(op1, op2)

            // Fiziksel silme: Tabloda kayıt kalmaz
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM subscriptions WHERE id = 'sub-3'")
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
            val entity = subscriptionEntity("sub-4", categoryId = "cat-1", amountMinor = 5999L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Spotify","amountMinor":5999}""",
            )

            val claimed = queue.claimOperation(op1)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val updatedEntity = entity.copy(
                amountMinor = 8999L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueSubscriptionV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Spotify Family","amountMinor":8999}""",
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

    @Test
    fun sequential_update_delete_predecessor_chain_and_single_tail() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = subscriptionEntity("sub-chain", categoryId = "cat-1", amountMinor = 5999L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Netflix","amountMinor":5999}""",
            )

            // op1 claim edilir (IN_FLIGHT)
            val claimed1 = queue.claimOperation(op1)
            assertNotNull(claimed1)
            assertEquals("IN_FLIGHT", claimed1.statusCode)

            // op2: UPDATE gelir -> op1'in blocked successor'ı
            val updatedEntity = entity.copy(
                amountMinor = 9999L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueSubscriptionV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Netflix HD","amountMinor":9999}""",
            )

            val successor1 = db.syncOperationDao().getById(op2)
            assertNotNull(successor1)
            assertEquals("UPDATE", successor1.operationTypeCode)
            assertTrue(successor1.isBlocked)
            assertEquals(op1, successor1.predecessorOperationId)

            // op2 unblock edilir ve claim edilir (IN_FLIGHT)
            val unblockCount = db.syncOperationDao().unblockSuccessor(op2, op1, appliedVersion = 1L, nowEpochMillis = 2000L)
            assertEquals(1, unblockCount)
            val claimed2 = queue.claimOperation(op2)
            assertNotNull(claimed2)
            assertEquals("IN_FLIGHT", claimed2.statusCode)

            // op3: DELETE gelir -> op2'nin blocked successor'ı
            val delEntity = updatedEntity.copy(
                sync = updatedEntity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 3000L,
                ),
            )
            val op3 = queue.enqueueSubscriptionV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            val successor2 = db.syncOperationDao().getById(op3)
            assertNotNull(successor2)
            assertEquals("DELETE", successor2.operationTypeCode)
            assertTrue(successor2.isBlocked)
            assertEquals(op2, successor2.predecessorOperationId)
            assertNull(successor2.baseVersion)

            // Tek aktif kuyruk sonu (tail) op3 olmalıdır
            val tails = db.localMutationDao().getActiveTailCandidates("SUBSCRIPTION", "sub-chain")
            assertEquals(1, tails.size)
            assertEquals(op3, tails.first().operationId)
        } finally {
            db.close()
        }
    }


    @Test
    fun invalid_operation_id_fails_closed() = runTest {
        val db = inMemoryDatabase()
        try {
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                syncScheduler = scheduler,
                operationIdFactory = { "invalid-not-32-hex" },
            )
            val entity = subscriptionEntity("sub-fail-id", syncStatus = SyncStatus.PENDING_CREATE)

            assertFailsWith<IllegalArgumentException> {
                queue.enqueueSubscriptionV2(
                    entity = entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"name":"Failing"}""",
                )
            }

            // Hiçbir kayıt Room'a veya Outbox'a yazılmamalı
            assertNull(db.subscriptionDao().getById("sub-fail-id"))
            assertEquals(0, db.syncOperationDao().observePendingCount().first())
            assertEquals(0, scheduler.scheduleCount)
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

    private fun subscriptionEntity(
        id: String,
        categoryId: String? = null,
        amountMinor: Long = 5999L,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAt: Long? = null,
    ): SubscriptionEntity = SubscriptionEntity(
        id = id,
        ownerId = "user-1",
        workspaceId = null,
        name = "Spotify",
        amountMinor = amountMinor,
        currencyCode = "TRY",
        categoryId = categoryId,
        frequencyCode = "MONTHLY",
        interval = 1,
        startDate = "2026-08-01",
        endDate = null,
        nextRenewalDate = "2026-09-01",
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
