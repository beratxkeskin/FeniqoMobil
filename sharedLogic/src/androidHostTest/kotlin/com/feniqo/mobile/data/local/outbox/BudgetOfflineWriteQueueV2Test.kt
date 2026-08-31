package com.feniqo.mobile.data.local.outbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
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
class BudgetOfflineWriteQueueV2Test {

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
            val entity = budgetEntity("b-1", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)

            val opId = queue.enqueueBudgetV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"categoryId":"cat-1","month":"2026-08","limitMinor":100000}""",
            )

            // Entity Room'a yazıldı
            val storedBudget = db.budgetDao().observeById("b-1").first()
            assertNotNull(storedBudget)
            assertEquals(100_000L, storedBudget.limitMinor)

            // Protocol version 2 outbox satırı oluştu
            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals("BUDGET", op.entityTypeCode)
            assertEquals("b-1", op.entityId)
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
            val entity = budgetEntity("b-2", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueBudgetV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"limitMinor":100000}""",
            )

            val updatedEntity = entity.copy(
                limitMinor = 150_000L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueBudgetV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"limitMinor":150000}""",
            )

            assertEquals(op1, op2)

            val storedBudget = db.budgetDao().observeById("b-2").first()
            assertNotNull(storedBudget)
            assertEquals(150_000L, storedBudget.limitMinor)

            val storedOp = db.syncOperationDao().getById(op1)
            assertNotNull(storedOp)
            assertEquals("CREATE", storedOp.operationTypeCode) // CREATE olarak kalır
            assertEquals("""{"limitMinor":150000}""", storedOp.payloadJson)
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
            val entity = budgetEntity("b-3", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueBudgetV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"limitMinor":100000}""",
            )
            assertEquals(1, scheduler.scheduleCount)

            val delEntity = entity.copy(
                sync = entity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val op2 = queue.enqueueBudgetV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            assertEquals(op1, op2)

            // Fiziksel silme: Tabloda kayıt kalmaz
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM budgets WHERE id = 'b-3'")
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
    fun pending_update_then_delete_converts_same_op_to_delete() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val syncedEntity = budgetEntity("b-4", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.SYNCED, version = 2L, baseVersion = 2L)
            db.budgetDao().upsert(syncedEntity)

            val updateEntity = syncedEntity.copy(
                limitMinor = 120_000L,
                sync = syncedEntity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op1 = queue.enqueueBudgetV2(
                entity = updateEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"limitMinor":120000}""",
            )

            val delEntity = updateEntity.copy(
                sync = updateEntity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val op2 = queue.enqueueBudgetV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            assertEquals(op1, op2)

            val storedOp = db.syncOperationDao().getById(op1)
            assertNotNull(storedOp)
            assertEquals("DELETE", storedOp.operationTypeCode)
            assertEquals("{}", storedOp.payloadJson)
            assertEquals(2L, storedOp.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun pending_delete_then_update_converts_same_op_to_update_and_restores_entity() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val syncedEntity = budgetEntity("b-5", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.SYNCED, version = 3L, baseVersion = 3L)
            db.budgetDao().upsert(syncedEntity)

            // Silme isteği kuyruğa atılır (soft delete)
            val delEntity = syncedEntity.copy(
                sync = syncedEntity.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val op1 = queue.enqueueBudgetV2(
                entity = delEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
            )

            // Reaktivasyon senaryosu: Henüz sunucuya gitmemiş DELETE varken yeni UPDATE gelir
            val reactivatedEntity = syncedEntity.copy(
                limitMinor = 200_000L,
                sync = syncedEntity.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    deletedAtEpochMillis = null,
                ),
            )
            val op2 = queue.enqueueBudgetV2(
                entity = reactivatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"limitMinor":200000}""",
            )

            assertEquals(op1, op2)

            // Operasyon UPDATE'e geri dönüştürülmüştür ve baseVersion korunmuştur
            val storedOp = db.syncOperationDao().getById(op1)
            assertNotNull(storedOp)
            assertEquals("UPDATE", storedOp.operationTypeCode)
            assertEquals("""{"limitMinor":200000}""", storedOp.payloadJson)
            assertEquals(3L, storedOp.baseVersion)

            // Entity aktif olarak Room'da geri yüklenmiştir
            val restoredBudget = db.budgetDao().observeById("b-5").first()
            assertNotNull(restoredBudget)
            assertEquals(200_000L, restoredBudget.limitMinor)
            assertNull(restoredBudget.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun attempted_or_immutable_tail_then_update_creates_blocked_successor_without_modifying_predecessor() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)
            val entity = budgetEntity("b-6", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueBudgetV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"limitMinor":100000}""",
            )

            // Tail işlem attempt edilmiş / in-flight yapılıyor
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_operations SET status_code = 'IN_FLIGHT', attempt_count = 1 WHERE operation_id = '$op1'",
            )

            val updatedEntity = entity.copy(
                limitMinor = 180_000L,
                sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueBudgetV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"limitMinor":180000}""",
            )

            assertTrue(op1 != op2)

            // Predecessor değişmemiştir
            val predOp = db.syncOperationDao().getById(op1)
            assertNotNull(predOp)
            assertEquals("IN_FLIGHT", predOp.statusCode)
            assertEquals(1, predOp.attemptCount)

            // Successor blocked olarak eklenmiştir
            val succOp = db.syncOperationDao().getById(op2)
            assertNotNull(succOp)
            assertEquals("UPDATE", succOp.operationTypeCode)
            assertEquals(op1, succOp.predecessorOperationId)
            assertTrue(succOp.isBlocked)
            assertEquals(0, succOp.attemptCount)
            assertNull(succOp.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun v1_tail_present_adds_v2_blocked_successor_safely_and_v1_row_unchanged() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            val v1Op = SyncOperationEntity(
                operationId = "00000000000000000000000000000001",
                entityTypeCode = "BUDGET",
                entityId = "b-7",
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = null,
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 1,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
            db.syncOperationDao().insert(v1Op)

            val entity = budgetEntity("b-7", categoryId = "cat-1", limitMinor = 150_000L, syncStatus = SyncStatus.PENDING_UPDATE)
            val op2 = queue.enqueueBudgetV2(
                entity = entity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"limitMinor":150000}""",
            )

            // V1 operasyon satırı değişmez
            val storedV1 = db.syncOperationDao().getById(v1Op.operationId)
            assertNotNull(storedV1)
            assertEquals(1, storedV1.protocolVersion)
            assertEquals("CREATE", storedV1.operationTypeCode)

            // V2 successor blocked olarak eklenir
            val storedV2 = db.syncOperationDao().getById(op2)
            assertNotNull(storedV2)
            assertEquals(2, storedV2.protocolVersion)
            assertEquals(v1Op.operationId, storedV2.predecessorOperationId)
            assertTrue(storedV2.isBlocked)
        } finally {
            db.close()
        }
    }

    @Test
    fun invalid_operation_id_rolls_back_transaction_leaving_no_entity_or_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val failingQueue = OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                operationIdFactory = { "invalid-not-hex" },
            )
            val entity = budgetEntity("b-8", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)

            assertFailsWith<IllegalArgumentException> {
                failingQueue.enqueueBudgetV2(
                    entity = entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = "{}",
                )
            }

            // Rollback: Entity de outbox da yazılmamıştır
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM budgets WHERE id = 'b-8'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()

            assertEquals(0, db.syncOperationDao().observePendingCount().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun multiple_active_tails_detected_triggers_fail_closed_rollback() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val queue = createQueue(db)

            // 2 ayrı aktif tail yerleştir
            val opA = SyncOperationEntity(
                operationId = "0000000000000000000000000000000a",
                entityTypeCode = "BUDGET",
                entityId = "b-9",
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = null,
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
            val opB = opA.copy(operationId = "0000000000000000000000000000000b")
            db.syncOperationDao().insert(opA)
            db.syncOperationDao().insert(opB)

            val entity = budgetEntity("b-9", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_UPDATE)

            assertFailsWith<IllegalStateException> {
                queue.enqueueBudgetV2(
                    entity = entity,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = "{}",
                )
            }

            // Entity yazılmamıştır
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM budgets WHERE id = 'b-9'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        } finally {
            db.close()
        }
    }

    @Test
    fun scheduler_is_called_on_all_successful_mutations_except_hard_delete() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            // 1. CREATE -> scheduler çağrılır (count = 1)
            val entity = budgetEntity("b-10", categoryId = "cat-1", limitMinor = 100_000L, syncStatus = SyncStatus.PENDING_CREATE)
            queue.enqueueBudgetV2(entity, OutboxOperationType.CREATE, "{}")
            assertEquals(1, scheduler.scheduleCount)

            // 2. UPDATE (coalesce) -> scheduler çağrılır (count = 2)
            val updateEntity = entity.copy(limitMinor = 110_000L, sync = entity.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            queue.enqueueBudgetV2(updateEntity, OutboxOperationType.UPDATE, "{}")
            assertEquals(2, scheduler.scheduleCount)

            // 3. HARD DELETE on untried create -> scheduler ÇAĞRILMAZ (count = 2)
            val delEntity = updateEntity.copy(sync = updateEntity.sync.copy(syncStatus = SyncStatus.PENDING_DELETE.name, deletedAtEpochMillis = 2000L))
            queue.enqueueBudgetV2(delEntity, OutboxOperationType.DELETE, "{}")
            assertEquals(2, scheduler.scheduleCount)
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

    private fun budgetEntity(
        id: String,
        categoryId: String = "cat-1",
        month: String = "2026-08",
        limitMinor: Long = 100_000L,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAt: Long? = null,
    ): BudgetEntity = BudgetEntity(
        id = id,
        ownerId = "user-1",
        workspaceId = null,
        scopeKey = "personal:user-1",
        categoryId = categoryId,
        month = month,
        limitMinor = limitMinor,
        currencyCode = "TRY",
        createdAtEpochMillis = 1000L,
        sync = newSyncMetadata(1000L).copy(
            syncStatus = syncStatus.name,
            version = version,
            baseVersion = baseVersion,
            deletedAtEpochMillis = deletedAt,
        ),
    )
}
