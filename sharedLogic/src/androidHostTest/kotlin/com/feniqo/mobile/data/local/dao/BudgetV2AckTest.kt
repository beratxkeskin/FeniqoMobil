package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import kotlinx.coroutines.Dispatchers
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
class BudgetV2AckTest {

    @Test
    fun budget_v2_ack_without_successor_applies_remote_record_and_removes_outbox_and_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Market"))

            val budget = budgetEntity("bgt-1", "cat-1", 500000L)
            val opId = queue.enqueueBudgetV2(
                entity = budget,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"bgt-1","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":500000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op -> IN_FLIGHT
            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // Insert dummy conflict row to verify it gets cleaned
            db.syncStateDao().upsertConflict(
                SyncConflictEntity("BUDGET", "bgt-1", opId, 0L, 1L, "{}", "{}", 1000L),
            )

            val remoteDto = BudgetDto(
                id = "bgt-1",
                userId = "usr-1",
                workspaceId = null,
                categoryId = "cat-1",
                month = "2026-08",
                limitMinor = 550000L,
                currency = "TRY",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.BudgetApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify: outbox removed, conflict removed, entity updated with remote values and SYNCED
            assertNull(db.syncOperationDao().getById(opId))
            assertNull(db.syncStateDao().getConflict("BUDGET", "bgt-1"))
            val entity = db.budgetDao().getByIdAndOwner("bgt-1", "usr-1")
            assertNotNull(entity)
            assertEquals(550000L, entity.limitMinor)
            assertEquals("SYNCED", entity.sync.syncStatus)
            assertEquals(1L, entity.sync.version)
            assertEquals(1L, entity.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun budget_v2_ack_with_successor_preserves_newer_local_fields_and_unblocks_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Market"))

            val budget = budgetEntity("bgt-2", "cat-1", 500000L)
            val op1Id = queue.enqueueBudgetV2(
                entity = budget,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"bgt-2","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":500000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op1 -> IN_FLIGHT
            val claimed = queue.claimOperation(op1Id)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // User updates budget locally -> creates blocked successor op2
            val updatedBudget = budget.copy(
                limitMinor = 750000L,
                sync = budget.sync.copy(syncStatus = "PENDING_UPDATE"),
            )
            val op2Id = queue.enqueueBudgetV2(
                entity = updatedBudget,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"bgt-2","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":750000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val successorBefore = db.syncOperationDao().getById(op2Id)
            assertNotNull(successorBefore)
            assertTrue(successorBefore.isBlocked)
            assertEquals(op1Id, successorBefore.predecessorOperationId)

            // ACK op1 with appliedVersion = 1L (with old limit)
            val remoteDto = BudgetDto(
                id = "bgt-2",
                userId = "usr-1",
                workspaceId = null,
                categoryId = "cat-1",
                month = "2026-08",
                limitMinor = 500000L,
                currency = "TRY",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = op1Id,
                result = OutboxExecutionResult.BudgetApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify op1 deleted
            assertNull(db.syncOperationDao().getById(op1Id))

            // Verify successor unblocked and rebased to 1L
            val successorAfter = db.syncOperationDao().getById(op2Id)
            assertNotNull(successorAfter)
            assertFalse(successorAfter.isBlocked)
            assertEquals(op1Id, successorAfter.predecessorOperationId)
            assertEquals(1L, successorAfter.baseVersion)

            // Verify budget entity preserves newer local limit (750_000) and version is rebased to 1L
            val entity = db.budgetDao().getByIdAndOwner("bgt-2", "usr-1")
            assertNotNull(entity)
            assertEquals(750000L, entity.limitMinor)
            assertEquals("PENDING_UPDATE", entity.sync.syncStatus)
            assertEquals(1L, entity.sync.version)
            assertEquals(1L, entity.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun budget_v2_ack_delete_applied_preserves_tombstone_and_clears_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Market"))

            val budget = budgetEntity("bgt-3", "cat-1", 500000L).copy(
                sync = SyncMetadata(
                    syncStatus = "PENDING_DELETE",
                    updatedAtEpochMillis = 2000L,
                    localUpdatedAtEpochMillis = 2000L,
                    deletedAtEpochMillis = 2000L,
                    version = 2L,
                    baseVersion = 2L,
                    lastSyncError = null,
                ),
            )
            val opId = queue.enqueueBudgetV2(
                entity = budget,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"bgt-3","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":500000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
            )

            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val remoteDto = BudgetDto(
                id = "bgt-3",
                userId = "usr-1",
                workspaceId = null,
                categoryId = "cat-1",
                month = "2026-08",
                limitMinor = 500000L,
                currency = "TRY",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:05:00Z",
                deletedAt = "2026-08-25T17:05:00Z",
                version = 3L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.BudgetApplied(remoteDto),
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(opId))
            val entity = db.budgetDao().getAnyByScopeCategoryAndMonth("user:usr-1", "cat-1", "2026-08")
            assertNotNull(entity)
            assertNotNull(entity.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", entity.sync.syncStatus)
            assertEquals(3L, entity.sync.version)
            assertEquals(3L, entity.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun budget_v2_ack_delete_not_found_preserves_tombstone_and_cleans_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Market"))

            val budget = budgetEntity("bgt-4", "cat-1", 500000L).copy(
                sync = SyncMetadata(
                    syncStatus = "PENDING_DELETE",
                    updatedAtEpochMillis = 2000L,
                    localUpdatedAtEpochMillis = 2000L,
                    deletedAtEpochMillis = 2000L,
                    version = 2L,
                    baseVersion = 2L,
                    lastSyncError = null,
                ),
            )
            val opId = queue.enqueueBudgetV2(
                entity = budget,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"bgt-4","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":500000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
            )

            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(opId))
            val entity = db.budgetDao().getAnyByScopeCategoryAndMonth("user:usr-1", "cat-1", "2026-08")
            assertNotNull(entity)
            assertNotNull(entity.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", entity.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun budget_v2_record_conflict_updates_entity_status_to_conflict_and_saves_conflict_row() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Market"))

            val budget = budgetEntity("bgt-5", "cat-1", 500000L)
            val opId = queue.enqueueBudgetV2(
                entity = budget,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"bgt-5","user_id":"usr-1","category_id":"cat-1","month":"2026-08","limit_minor":500000,"currency":"TRY","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val conflict = SyncConflictEntity(
                entityTypeCode = "BUDGET",
                entityId = "bgt-5",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 3L,
                localPayloadJson = """{"id":"bgt-5"}""",
                remotePayloadJson = """{"id":"bgt-5","version":3}""",
                detectedAtEpochMillis = 2000L,
            )

            val result = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.ConflictDetected(conflict),
                nowEpochMillis = 2000L,
            )
            assertTrue(result)

            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals("CONFLICT", op.statusCode)

            val entity = db.budgetDao().getByIdAndOwner("bgt-5", "usr-1")
            assertNotNull(entity)
            assertEquals("CONFLICT", entity.sync.syncStatus)

            val savedConflict = db.syncStateDao().getConflict("BUDGET", "bgt-5")
            assertNotNull(savedConflict)
            assertEquals(3L, savedConflict.remoteVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun budget_v2_record_conflict_missing_entity_rolls_back_transaction() = runTest {
        val db = inMemoryDatabase()
        try {
            // Outbox exists for bgt-missing, but no budget entity exists in Room
            val op = SyncOperationEntity(
                operationId = "op-missing",
                entityTypeCode = "BUDGET",
                entityId = "bgt-missing",
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = "{}",
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 2,
                statusCode = "IN_FLIGHT",
                attemptCount = 1,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
            db.syncOperationDao().insert(op)

            val conflict = SyncConflictEntity(
                entityTypeCode = "BUDGET",
                entityId = "bgt-missing",
                operationId = "op-missing",
                localVersion = 0L,
                remoteVersion = 2L,
                localPayloadJson = "{}",
                remotePayloadJson = "{}",
                detectedAtEpochMillis = 2000L,
            )

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    operationId = "op-missing",
                    result = OutboxExecutionResult.ConflictDetected(conflict),
                    nowEpochMillis = 2000L,
                )
            }

            // Verify rollback: op status remains IN_FLIGHT, no conflict row saved
            assertEquals("IN_FLIGHT", db.syncOperationDao().getById("op-missing")?.statusCode)
            assertNull(db.syncStateDao().getConflict("BUDGET", "bgt-missing"))
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

    private fun createQueue(db: FeniqoDatabase): OfflineWriteQueue {
        return OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
            nowEpochMillisProvider = { 1000L },
        )
    }

    private fun categoryEntity(id: String, name: String) = CategoryEntity(
        id = id,
        ownerId = "usr-1",
        workspaceId = null,
        scopeKey = "user:usr-1",
        name = name,
        normalizedName = name.lowercase(),
        slug = name.lowercase(),
        typeCode = "EXPENSE",
        colorHex = "#EF4444",
        iconKey = null,
        isDefault = false,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1,
            baseVersion = 1,
            lastSyncError = null,
        ),
    )

    private fun budgetEntity(id: String, categoryId: String, limit: Long) = BudgetEntity(
        id = id,
        ownerId = "usr-1",
        workspaceId = null,
        scopeKey = "user:usr-1",
        categoryId = categoryId,
        month = "2026-08",
        limitMinor = limit,
        currencyCode = "TRY",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "PENDING_CREATE",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 0,
            baseVersion = null,
            lastSyncError = null,
        ),
    )
}
