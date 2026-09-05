package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
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
class SubscriptionV2AckTest {

    @Test
    fun subscription_v2_ack_without_successor_applies_remote_record_and_removes_outbox_and_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = subscriptionEntity("sub-1", "cat-1", 5999L)
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"sub-1","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
            )

            // Claim op -> IN_FLIGHT
            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // Insert dummy conflict row to verify it gets cleaned
            db.syncStateDao().upsertConflict(
                SyncConflictEntity("SUBSCRIPTION", "sub-1", opId, 0L, 1L, "{}", "{}", 1000L),
            )

            val remoteDto = SubscriptionDto(
                id = "sub-1",
                userId = "usr-1",
                workspaceId = null,
                name = "Spotify Premium",
                amountMinor = 6499L,
                currency = "TRY",
                categoryId = "cat-1",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.SubscriptionApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify: outbox removed, conflict removed, entity updated with remote values and SYNCED
            assertNull(db.syncOperationDao().getById(opId))
            assertNull(db.syncStateDao().getConflict("SUBSCRIPTION", "sub-1"))
            val stored = db.subscriptionDao().getById("sub-1")
            assertNotNull(stored)
            assertEquals(6499L, stored.amountMinor)
            assertEquals("Spotify Premium", stored.name)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun subscription_v2_ack_with_successor_preserves_newer_local_fields_and_unblocks_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val initial = subscriptionEntity("sub-2", "cat-1", 5999L)
            val op1Id = queue.enqueueSubscriptionV2(
                entity = initial,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"sub-2","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
            )

            // Claim op1 -> IN_FLIGHT
            val claimed = queue.claimOperation(op1Id)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // User updates locally -> creates blocked successor op2
            val updated = initial.copy(
                amountMinor = 8999L,
                name = "Spotify Family",
                sync = initial.sync.copy(syncStatus = "PENDING_UPDATE"),
            )
            val op2Id = queue.enqueueSubscriptionV2(
                entity = updated,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"sub-2","user_id":"usr-1","workspace_id":null,"name":"Spotify Family","amount_minor":8999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","updated_at":null,"deleted_at":null,"version":1}""",
            )

            val successorBefore = db.syncOperationDao().getById(op2Id)
            assertNotNull(successorBefore)
            assertTrue(successorBefore.isBlocked)
            assertEquals(op1Id, successorBefore.predecessorOperationId)

            // ACK op1 with appliedVersion = 1L
            val remoteDto = SubscriptionDto(
                id = "sub-2",
                userId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currency = "TRY",
                categoryId = "cat-1",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = op1Id,
                result = OutboxExecutionResult.SubscriptionApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify op1 deleted, op2 unblocked with baseVersion = 1L, entity keeps local updated values
            assertNull(db.syncOperationDao().getById(op1Id))

            val successorAfter = db.syncOperationDao().getById(op2Id)
            assertNotNull(successorAfter)
            assertFalse(successorAfter.isBlocked)
            assertEquals(1L, successorAfter.baseVersion)

            val stored = db.subscriptionDao().getById("sub-2")
            assertNotNull(stored)
            assertEquals("Spotify Family", stored.name)
            assertEquals(8999L, stored.amountMinor)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun subscription_v2_ack_delete_applied_updates_tombstone_version_and_cleans_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = subscriptionEntity("sub-3", "cat-1", 5999L).copy(
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
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"sub-3","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
            )

            val claimed = queue.claimOperation(opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            val remoteDto = SubscriptionDto(
                id = "sub-3",
                userId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currency = "TRY",
                categoryId = "cat-1",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:05:00Z",
                deletedAt = "2026-08-25T17:05:00Z",
                version = 3L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.SubscriptionApplied(remoteDto),
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(opId))
            val stored = db.subscriptionDao().getAnyById("sub-3")
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
    fun subscription_v2_ack_delete_not_found_preserves_tombstone_and_cleans_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = subscriptionEntity("sub-4", "cat-1", 5999L).copy(
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
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"sub-4","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","deleted_at":"2026-08-25T17:05:00Z","version":2}""",
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
            val stored = db.subscriptionDao().getAnyById("sub-4")
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", stored.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun subscription_v2_ack_with_invalid_remote_version_fails_closed_and_rolls_back() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = subscriptionEntity("sub-5", "cat-1", 5999L)
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"sub-5","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","end_date":null,"next_renewal_date":"2026-09-01","is_active":true,"created_at":"2026-08-25T17:00:00Z","version":null}""",
            )
            queue.claimOperation(opId)

            val invalidDto = SubscriptionDto(
                id = "sub-5",
                userId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currency = "TRY",
                categoryId = "cat-1",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 0L, // invalid: must be >= 1
            )

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().ackV2Execution(
                    operationId = opId,
                    result = OutboxExecutionResult.SubscriptionApplied(invalidDto),
                    nowEpochMillis = 1000L,
                )
            }

            // Atomic rollback: outbox remains IN_FLIGHT
            val op = db.syncOperationDao().getById(opId)
            assertNotNull(op)
            assertEquals("IN_FLIGHT", op.statusCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun subscription_v2_ack_with_mismatched_entity_id_fails_closed() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            db.categoryDao().upsert(categoryEntity("cat-1", "Abonelikler"))

            val entity = subscriptionEntity("sub-6", "cat-1", 5999L)
            val opId = queue.enqueueSubscriptionV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"sub-6","user_id":"usr-1","workspace_id":null,"name":"Spotify","amount_minor":5999,"currency":"TRY","category_id":"cat-1","frequency":"MONTHLY","interval":1,"start_date":"2026-08-01","version":null}""",
            )
            queue.claimOperation(opId)

            val mismatchedDto = SubscriptionDto(
                id = "different-sub-id",
                userId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currency = "TRY",
                categoryId = "cat-1",
                frequency = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    operationId = opId,
                    result = OutboxExecutionResult.SubscriptionApplied(mismatchedDto),
                    nowEpochMillis = 1000L,
                )
            }
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

    private fun subscriptionEntity(
        id: String,
        categoryId: String?,
        amountMinor: Long,
        syncStatus: String = "PENDING_CREATE",
    ) = SubscriptionEntity(
        id = id,
        ownerId = "usr-1",
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
