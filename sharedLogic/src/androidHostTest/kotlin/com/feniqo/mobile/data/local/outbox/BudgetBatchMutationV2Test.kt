package com.feniqo.mobile.data.local.outbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.dao.BudgetMutationInputV2
import com.feniqo.mobile.data.local.dao.V2EnqueueDecision
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class BudgetBatchMutationV2Test {

    private class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
        var scheduleCount = 0
        override fun scheduleInitialSync() {}
        override fun scheduleOutboxSync() {
            scheduleCount++
        }
        override fun cancelSyncWork() {}
    }

    @Test
    fun two_valid_budgets_are_written_atomically_as_entity_and_v2_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            insertCategory(db, "cat-2")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            val b1 = budgetEntity("b-batch-1", categoryId = "cat-1", limitMinor = 100_000L)
            val b2 = budgetEntity("b-batch-2", categoryId = "cat-2", limitMinor = 200_000L)

            val inputs = listOf(
                BudgetMutationInputV2(entity = b1, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":100000}"""),
                BudgetMutationInputV2(entity = b2, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":200000}"""),
            )

            val results = queue.enqueueBudgetsV2(inputs)
            assertEquals(2, results.size)
            assertEquals(V2EnqueueDecision.INSERTED, results[0].decision)
            assertEquals(V2EnqueueDecision.INSERTED, results[1].decision)

            // Her iki bütçe de Room'da mevcut
            assertNotNull(db.budgetDao().observeById("b-batch-1").first())
            assertNotNull(db.budgetDao().observeById("b-batch-2").first())

            // Her iki outbox satırı da yazılmış
            assertNotNull(db.syncOperationDao().getById(results[0].operationId))
            assertNotNull(db.syncOperationDao().getById(results[1].operationId))

            // Scheduler yalnız bir kez çağrıldı
            assertEquals(1, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun failure_on_second_input_rolls_back_first_budget_entity_and_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            insertCategory(db, "cat-2")
            val scheduler = FakeBackgroundSyncScheduler()

            var callCount = 0
            val queue = OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                syncScheduler = scheduler,
                operationIdFactory = {
                    callCount++
                    if (callCount == 1) {
                        "00000000000000000000000000000001"
                    } else {
                        throw IllegalStateException("simulated_second_item_failure")
                    }
                },
            )

            val b1 = budgetEntity("b-rb-1", categoryId = "cat-1", limitMinor = 100_000L)
            val b2 = budgetEntity("b-rb-2", categoryId = "cat-2", limitMinor = 200_000L)

            val inputs = listOf(
                BudgetMutationInputV2(entity = b1, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":100000}"""),
                BudgetMutationInputV2(entity = b2, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":200000}"""),
            )

            assertFailsWith<IllegalStateException> {
                queue.enqueueBudgetsV2(inputs)
            }

            // Transaction rollback: İlk bütçe entity'si de outbox'ı da Room'a yazılmamış olmalı
            assertNull(db.budgetDao().observeById("b-rb-1").first())
            assertNull(db.budgetDao().observeById("b-rb-2").first())
            assertEquals(0, db.syncOperationDao().observePendingCount().first())

            // Başarısız işlemde scheduler çağrılmamalı
            assertEquals(0, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun duplicate_entity_ids_in_batch_is_rejected_without_any_writes() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            val b1 = budgetEntity("b-dup", categoryId = "cat-1", limitMinor = 100_000L)
            val b2 = budgetEntity("b-dup", categoryId = "cat-1", limitMinor = 150_000L)

            val inputs = listOf(
                BudgetMutationInputV2(entity = b1, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":100000}"""),
                BudgetMutationInputV2(entity = b2, type = OutboxOperationType.CREATE, payloadJson = """{"limitMinor":150000}"""),
            )

            assertFailsWith<IllegalArgumentException> {
                queue.enqueueBudgetsV2(inputs)
            }

            assertNull(db.budgetDao().observeById("b-dup").first())
            assertEquals(0, db.syncOperationDao().observePendingCount().first())
            assertEquals(0, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun successful_batch_calls_scheduler_exactly_once() = runTest {
        val db = inMemoryDatabase()
        try {
            insertCategory(db, "cat-1")
            insertCategory(db, "cat-2")
            insertCategory(db, "cat-3")
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            val inputs = listOf(
                BudgetMutationInputV2(entity = budgetEntity("b-s1", "cat-1"), type = OutboxOperationType.CREATE, payloadJson = "{}"),
                BudgetMutationInputV2(entity = budgetEntity("b-s2", "cat-2"), type = OutboxOperationType.CREATE, payloadJson = "{}"),
                BudgetMutationInputV2(entity = budgetEntity("b-s3", "cat-3"), type = OutboxOperationType.CREATE, payloadJson = "{}"),
            )

            val results = queue.enqueueBudgetsV2(inputs)
            assertEquals(3, results.size)
            assertEquals(1, scheduler.scheduleCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun empty_batch_does_not_call_scheduler() = runTest {
        val db = inMemoryDatabase()
        try {
            val scheduler = FakeBackgroundSyncScheduler()
            val queue = createQueue(db, scheduler)

            val results = queue.enqueueBudgetsV2(emptyList())
            assertEquals(0, results.size)
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
