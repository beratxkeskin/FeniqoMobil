package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.SyncScopeKey
import kotlinx.coroutines.Dispatchers
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
class RecurringTransactionV2AckTest {

    private val USER_ID = "11111111-1111-4111-8111-111111111111"
    private val SCOPE = SyncScopeKey.user(USER_ID).rawValue

    @Test
    fun recurring_transaction_v2_ack_without_successor_applies_remote_record_and_removes_outbox_and_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = recurringEntity("rec-1", "cat-1", 50000L)
            val opId = queue.enqueueRecurringTransactionV2(
                syncScopeKey = SCOPE,
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"rec-1","user_id":"$USER_ID","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op -> IN_FLIGHT
            val claimed = queue.claimOperation(SCOPE, opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // Insert dummy conflict row to verify it gets cleaned
            db.syncStateDao().upsertConflict(
                SyncConflictEntity(
                    syncScopeKey = SCOPE,
                    entityTypeCode = "RECURRING_TRANSACTION",
                    entityId = "rec-1",
                    operationId = opId,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = "{}",
                    remotePayloadJson = "{}",
                    detectedAtEpochMillis = 1000L,
                ),
            )

            val remoteDto = RecurringTransactionDto(
                id = "rec-1",
                userId = USER_ID,
                workspaceId = null,
                amountMinor = 55000L,
                currency = "TRY",
                type = "expense",
                categoryId = "cat-1",
                description = "İnternet Faturası",
                paymentMethod = "CREDIT_CARD",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                lastGeneratedDate = null,
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = opId,
                result = OutboxExecutionResult.RecurringTransactionApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify: outbox removed, conflict removed, entity updated with remote values and SYNCED
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            assertNull(db.syncStateDao().getConflict(SCOPE, "RECURRING_TRANSACTION", "rec-1"))
            val stored = db.recurringTransactionDao().getById("rec-1")
            assertNotNull(stored)
            assertEquals(55000L, stored.amountMinor)
            assertEquals("İnternet Faturası", stored.description)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun recurring_transaction_v2_ack_with_successor_preserves_newer_local_fields_and_unblocks_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val initial = recurringEntity("rec-2", "cat-1", 50000L)
            val op1Id = queue.enqueueRecurringTransactionV2(
                syncScopeKey = SCOPE,
                entity = initial,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"rec-2","user_id":"$USER_ID","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op1 -> IN_FLIGHT
            val claimed = queue.claimOperation(SCOPE, op1Id)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // User updates locally -> creates blocked successor op2
            val updated = initial.copy(
                amountMinor = 75000L,
                description = "Güncel Açıklama",
                sync = initial.sync.copy(syncStatus = "PENDING_UPDATE"),
            )
            val op2Id = queue.enqueueRecurringTransactionV2(
                syncScopeKey = SCOPE,
                entity = updated,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"rec-2","user_id":"$USER_ID","amount_minor":75000,"currency":"TRY","type":"expense","category_id":"cat-1","description":"Güncel Açıklama","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val successorBefore = db.syncOperationDao().getById(SCOPE, op2Id)
            assertNotNull(successorBefore)
            assertTrue(successorBefore.isBlocked)
            assertEquals(op1Id, successorBefore.predecessorOperationId)

            // ACK op1 with appliedVersion = 1L
            val remoteDto = RecurringTransactionDto(
                id = "rec-2",
                userId = USER_ID,
                workspaceId = null,
                amountMinor = 50000L,
                currency = "TRY",
                type = "expense",
                categoryId = "cat-1",
                description = null,
                paymentMethod = "CREDIT_CARD",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                lastGeneratedDate = null,
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = op1Id,
                result = OutboxExecutionResult.RecurringTransactionApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify op1 deleted
            assertNull(db.syncOperationDao().getById(SCOPE, op1Id))

            // Verify successor unblocked and rebased to 1L
            val successorAfter = db.syncOperationDao().getById(SCOPE, op2Id)
            assertNotNull(successorAfter)
            assertFalse(successorAfter.isBlocked)
            assertEquals(op1Id, successorAfter.predecessorOperationId)
            assertEquals(1L, successorAfter.baseVersion)

            // Verify entity preserves local values (75000L, Güncel Açıklama) and version rebased to 1L
            val stored = db.recurringTransactionDao().getById("rec-2")
            assertNotNull(stored)
            assertEquals(75000L, stored.amountMinor)
            assertEquals("Güncel Açıklama", stored.description)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun recurring_transaction_v2_ack_delete_applied_preserves_tombstone_and_clears_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = recurringEntity("rec-3", "cat-1", 50000L).copy(
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
            val opId = queue.enqueueRecurringTransactionV2(
                syncScopeKey = SCOPE,
                entity = entity,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"rec-3","user_id":"$USER_ID","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
            )

            val claimed = queue.claimOperation(SCOPE, opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val remoteDto = RecurringTransactionDto(
                id = "rec-3",
                userId = USER_ID,
                workspaceId = null,
                amountMinor = 50000L,
                currency = "TRY",
                type = "expense",
                categoryId = "cat-1",
                description = null,
                paymentMethod = "CREDIT_CARD",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                lastGeneratedDate = null,
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:05:00Z",
                deletedAt = "2026-08-25T17:05:00Z",
                version = 3L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = opId,
                result = OutboxExecutionResult.RecurringTransactionApplied(remoteDto),
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            val stored = db.recurringTransactionDao().getAnyById("rec-3")
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(3L, stored.sync.version)
            assertEquals(3L, stored.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun recurring_transaction_v2_ack_delete_not_found_preserves_tombstone_and_cleans_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = recurringEntity("rec-4", "cat-1", 50000L).copy(
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
            val opId = queue.enqueueRecurringTransactionV2(
                syncScopeKey = SCOPE,
                entity = entity,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"rec-4","user_id":"$USER_ID","amount_minor":50000,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"CREDIT_CARD","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
            )

            val claimed = queue.claimOperation(SCOPE, opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = opId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            val stored = db.recurringTransactionDao().getAnyById("rec-4")
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", stored.sync.syncStatus)

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
        ownerId = USER_ID,
        workspaceId = null,
        scopeKey = "user:$USER_ID",
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

    private fun recurringEntity(
        id: String,
        categoryId: String,
        amountMinor: Long,
        syncStatus: String = "PENDING_CREATE",
    ) = RecurringTransactionEntity(
        id = id,
        ownerId = USER_ID,
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
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = if (syncStatus == "PENDING_CREATE") 0L else 1L,
            baseVersion = if (syncStatus == "PENDING_CREATE") null else 1L,
            lastSyncError = null,
        ),
    )
}
