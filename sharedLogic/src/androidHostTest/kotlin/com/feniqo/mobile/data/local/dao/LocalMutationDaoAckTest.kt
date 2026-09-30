package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LocalMutationDaoAckTest {

    private val USER_ID = "11111111-1111-4111-8111-111111111111"
    private val SCOPE = SyncScopeKey.user(USER_ID).rawValue

    @Test
    fun v2_ack_without_successor_applies_remote_record_and_removes_outbox_and_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-1", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-1","name":"Market","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op -> IN_FLIGHT
            val claimed = queue.claimOperation(SCOPE, opId)
            assertNotNull(claimed)
            assertEquals("IN_FLIGHT", claimed.statusCode)

            // Insert a dummy conflict row to verify it gets cleaned
            db.syncStateDao().upsertConflict(
                SyncConflictEntity(SCOPE, "CATEGORY", "cat-1", opId, 0L, 1L, "{}", "{}", 1000L),
            )

            val remoteDto = CategoryDto(
                id = "cat-1",
                userId = USER_ID,
                name = "Market Remote",
                slug = "market-remote",
                type = "expense",
                color = "#abcdef",
                icon = "cart",
                isDefault = false,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:01Z",
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = opId,
                result = OutboxExecutionResult.CategoryApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // Verify: outbox removed, conflict removed, entity updated with remote values and SYNCED
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-1"))
            val entity = db.categoryDao().getByIdAndOwner("cat-1", USER_ID)
            assertNotNull(entity)
            assertEquals("Market Remote", entity.name)
            assertEquals("market-remote", entity.slug)
            assertEquals("SYNCED", entity.sync.syncStatus)
            assertEquals(1L, entity.sync.version)
            assertEquals(1L, entity.sync.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_ack_with_successor_preserves_newer_local_fields_and_unblocks_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-2", "Market V1")
            val op1Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-2","name":"Market V1","created_at":"2026-08-25T17:00:00Z"}""",
            )

            // Claim op1 -> IN_FLIGHT
            queue.claimOperation(SCOPE, op1Id)

            // User edits category locally while op1 is IN_FLIGHT -> op2 created as blocked successor
            val newerCategory = category.copy(
                name = "Market V2 (Local Edit)",
                sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = newerCategory,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"cat-2","name":"Market V2 (Local Edit)","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val successorBeforeAck = db.syncOperationDao().getById(SCOPE, op2Id)
            assertNotNull(successorBeforeAck)
            assertTrue(successorBeforeAck.isBlocked)
            assertNull(successorBeforeAck.baseVersion)

            // Remote returns applied for op1 with version = 1 and old name "Market V1"
            val remoteDto = CategoryDto(
                id = "cat-2",
                userId = USER_ID,
                name = "Market V1",
                slug = "market-v1",
                type = "expense",
                color = "#123",
                createdAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = op1Id,
                result = OutboxExecutionResult.CategoryApplied(remoteDto),
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            // 1. op1 deleted
            assertNull(db.syncOperationDao().getById(SCOPE, op1Id))

            // 2. Entity's local user fields ("Market V2 (Local Edit)") are NOT overwritten!
            val currentEntity = db.categoryDao().getByIdAndOwner("cat-2", USER_ID)
            assertNotNull(currentEntity)
            assertEquals("Market V2 (Local Edit)", currentEntity.name, "Local user edit must NOT be overwritten by older remote record")
            assertEquals("PENDING_UPDATE", currentEntity.sync.syncStatus)
            assertEquals(1L, currentEntity.sync.version)
            assertEquals(1L, currentEntity.sync.baseVersion)

            // 3. Successor op2 is UNBLOCKED and has baseVersion = 1L
            val successorAfterAck = db.syncOperationDao().getById(SCOPE, op2Id)
            assertNotNull(successorAfterAck)
            assertEquals(false, successorAfterAck.isBlocked, "Successor must be unblocked")
            assertEquals(1L, successorAfterAck.baseVersion, "Successor baseVersion must be updated to appliedVersion")
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_ack_with_mismatched_operation_id_or_entity_id_rolls_back_cleanly() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-3", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-3","name":"Market","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, opId)

            // Remote DTO has mismatched id "cat-other"
            val remoteDto = CategoryDto(
                id = "cat-other",
                name = "Market",
                type = "expense",
                color = "#123",
                createdAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = SCOPE,
                    operationId = opId,
                    result = OutboxExecutionResult.CategoryApplied(remoteDto),
                    nowEpochMillis = 1000L,
                )
            }

            // Op still in DB due to rollback
            val opStillExists = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(opStillExists)
            assertEquals("IN_FLIGHT", opStillExists.statusCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_ack_delete_not_found_without_successor_succeeds() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-4", "Market").copy(
                sync = SyncMetadata("PENDING_DELETE", 1000L, 1000L, 1000L, 1, 1, null),
            )
            db.categoryDao().upsert(category)

            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"cat-4","name":"Market","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val ackResult = db.localMutationDao().ackV2Execution(
                syncScopeKey = SCOPE,
                operationId = opId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 1000L,
            )
            assertTrue(ackResult)

            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            val cat = db.remoteSyncDao().getCategoryRow("cat-4")
            assertNotNull(cat)
            assertEquals("SYNCED", cat.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_ack_delete_not_found_with_successor_fails_closed() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-5", "Market").copy(
                sync = SyncMetadata("PENDING_DELETE", 1000L, 1000L, 1000L, 1, 1, null),
            )
            db.categoryDao().upsert(category)

            val op1Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"cat-5","name":"Market","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, op1Id)

            // Add successor to DELETE
            val recreate = category.copy(
                sync = SyncMetadata("PENDING_UPDATE", 1000L, 1000L, null, 1, 1, null),
            )
            val op2Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = recreate,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"cat-5","name":"Market Recreated","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val error = assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = SCOPE,
                    operationId = op1Id,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 1000L,
                )
            }
            assertTrue(error.message?.contains("DELETE NOT_FOUND") == true)

            // op1 preserved due to rollback
            assertNotNull(db.syncOperationDao().getById(SCOPE, op1Id))
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_record_conflict_without_successor_updates_entity_to_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-c1", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-c1","name":"Market","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-c1",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 2L,
                localPayloadJson = """{"name":"Market"}""",
                remotePayloadJson = """{"name":"Market Server"}""",
                detectedAtEpochMillis = 1000L,
            )

            val result = queue.recordV2Conflict(SCOPE, conflict)
            assertTrue(result)

            val op = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(op)
            assertEquals("CONFLICT", op.statusCode)

            val savedConflict = db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-c1")
            assertNotNull(savedConflict)
            assertEquals(opId, savedConflict.operationId)

            val entity = db.remoteSyncDao().getCategoryRow("cat-c1")
            assertNotNull(entity)
            assertEquals("CONFLICT", entity.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2_record_conflict_with_successor_preserves_entity_pending_state_and_keeps_successor_blocked() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-c2", "Market V1")
            val op1Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-c2","name":"Market V1","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, op1Id)

            val updateEntity = category.copy(
                name = "Market V2",
                sync = SyncMetadata("PENDING_UPDATE", 1000L, 1000L, null, 1, 1, null),
            )
            val op2Id = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = updateEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"cat-c2","name":"Market V2","created_at":"2026-08-25T17:00:00Z"}""",
            )

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-c2",
                operationId = op1Id,
                localVersion = 0L,
                remoteVersion = 2L,
                localPayloadJson = """{"name":"Market V1"}""",
                remotePayloadJson = """{"name":"Market Server"}""",
                detectedAtEpochMillis = 1000L,
            )

            val result = queue.recordV2Conflict(SCOPE, conflict)
            assertTrue(result)

            val op1 = db.syncOperationDao().getById(SCOPE, op1Id)
            assertNotNull(op1)
            assertEquals("CONFLICT", op1.statusCode)

            val savedConflict = db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-c2")
            assertNotNull(savedConflict)

            // Entity should PRESERVE newer local business fields and PENDING_UPDATE
            val entity = db.categoryDao().getByIdAndOwner("cat-c2", USER_ID)
            assertNotNull(entity)
            assertEquals("Market V2", entity.name)
            assertEquals("PENDING_UPDATE", entity.sync.syncStatus)

            // Successor should remain BLOCKED
            val op2 = db.syncOperationDao().getById(SCOPE, op2Id)
            assertNotNull(op2)
            assertTrue(op2.isBlocked)
            assertEquals("PENDING", op2.statusCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun v1_operation_rejected_by_v2_ack_methods() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-v1", "Market")
            val opId = queue.enqueueCategory(SCOPE, category, OutboxOperationType.CREATE)
            queue.claimOperation(SCOPE, opId)

            val remoteDto = CategoryDto(
                id = "cat-v1",
                userId = USER_ID,
                name = "Market",
                type = "expense",
                color = "#EF4444",
                createdAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

            val error = assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackCategoryWriteV2(SCOPE, opId, remoteDto, 1000L)
            }
            assertTrue(error.message?.contains("protocolVersion=2") == true)
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_equivalent_category_accepts_protocol_version_1() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-pv1", "Market")
            val opId = queue.enqueueCategory(SCOPE, category, OutboxOperationType.CREATE)
            queue.claimOperation(SCOPE, opId)
            queue.markConflict(SCOPE, opId, "Conflict")

            val op = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(op)
            assertEquals(1, op.protocolVersion)
            assertEquals("CONFLICT", op.statusCode)

            val remoteDto = CategoryDto(
                id = "cat-pv1",
                userId = USER_ID,
                name = "Market",
                type = "expense",
                color = "#EF4444",
                createdAt = "2026-08-25T17:00:00Z",
                deletedAt = null,
                version = 1L,
            )

            val result = db.localMutationDao().recoverEquivalentCategory(SCOPE, opId, remoteDto, 1000L)
            assertTrue(result)

            val syncedCat = db.categoryDao().getByIdAndOwner("cat-pv1", USER_ID)
            assertNotNull(syncedCat)
            assertEquals("SYNCED", syncedCat.sync.syncStatus)
            assertEquals(1L, syncedCat.sync.version)
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_equivalent_category_accepts_protocol_version_2() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-pv2", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-pv2","name":"Market"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-pv2",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-pv2","name":"Market"}""",
                remotePayloadJson = """{"id":"cat-pv2","name":"Market","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            queue.recordV2Conflict(SCOPE, conflict)

            val op = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(op)
            assertEquals(2, op.protocolVersion)
            assertEquals("CONFLICT", op.statusCode)

            val remoteDto = CategoryDto(
                id = "cat-pv2",
                userId = USER_ID,
                name = "Market",
                type = "expense",
                color = "#EF4444",
                createdAt = "2026-08-25T17:00:00Z",
                deletedAt = null,
                version = 1L,
            )

            val result = db.localMutationDao().recoverEquivalentCategory(SCOPE, opId, remoteDto, 1000L)
            assertTrue(result)

            val syncedCat = db.categoryDao().getByIdAndOwner("cat-pv2", USER_ID)
            assertNotNull(syncedCat)
            assertEquals("SYNCED", syncedCat.sync.syncStatus)
            assertEquals(1L, syncedCat.sync.version)
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-pv2"))
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_equivalent_category_rejects_unknown_protocol_version() = runTest {
        val db = inMemoryDatabase()
        try {
            val op = SyncOperationEntity(
                operationId = "op-pv99",
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-pv99",
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = "{}",
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 99,
                statusCode = "CONFLICT",
                attemptCount = 1,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
            db.syncOperationDao().insert(op)

            val remoteDto = CategoryDto(
                id = "cat-pv99",
                userId = USER_ID,
                name = "Market",
                type = "expense",
                color = "#EF4444",
                createdAt = "2026-08-25T17:00:00Z",
                deletedAt = null,
                version = 1L,
            )

            val error = assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentCategory(SCOPE, "op-pv99", remoteDto, 1000L)
            }
            assertTrue(error.message?.contains("Desteklenmeyen protokol sürümü: 99") == true)
            // Operation remains intact in DB
            assertNotNull(db.syncOperationDao().getById(SCOPE, "op-pv99"))
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_equivalent_category_rejects_remote_record_with_deletedAt() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val category = categoryEntity("cat-del", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-del","name":"Market"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-del",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-del","name":"Market"}""",
                remotePayloadJson = """{"id":"cat-del","name":"Market","deleted_at":"2026-08-25T17:00:00Z","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            queue.recordV2Conflict(SCOPE, conflict)

            val remoteDtoWithDeletedAt = CategoryDto(
                id = "cat-del",
                userId = USER_ID,
                name = "Market",
                type = "expense",
                color = "#EF4444",
                createdAt = "2026-08-25T17:00:00Z",
                deletedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

            val error = assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentCategory(SCOPE, opId, remoteDtoWithDeletedAt, 1000L)
            }
            assertTrue(error.message?.contains("Silinmiş uzak kayıt eşdeğer kabul edilemez") == true)

            // Conflict and outbox are preserved
            assertNotNull(db.syncOperationDao().getById(SCOPE, opId))
            assertNotNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-del"))
        } finally {
            db.close()
        }
    }

    @Test
    fun recordV2Conflict_rolls_back_when_entity_is_missing_or_type_is_unknown() = runTest {
        val db = inMemoryDatabase()
        try {
            // Case A: Missing entity in Room table
            val opMissing = SyncOperationEntity(
                operationId = "op-missing",
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-nonexistent",
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
            db.syncOperationDao().insert(opMissing)

            val conflictMissing = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-nonexistent",
                operationId = "op-missing",
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = "{}",
                remotePayloadJson = "{}",
                detectedAtEpochMillis = 1000L,
            )

            val errorMissing = assertFailsWith<IllegalStateException> {
                db.localMutationDao().recordV2Conflict(SCOPE, conflictMissing, 1000L)
            }
            assertTrue(errorMissing.message?.contains("Entity sync_status güncellenemedi veya entity bulunamadı") == true)

            // Rollback verification: outbox status remains IN_FLIGHT, conflict row NOT saved
            assertEquals("IN_FLIGHT", db.syncOperationDao().getById(SCOPE, "op-missing")?.statusCode)
            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-nonexistent"))

            // Case B: Unknown entityTypeCode
            val opUnknownType = SyncOperationEntity(
                operationId = "op-unknown",
                syncScopeKey = SCOPE,
                entityTypeCode = "UNKNOWN_TYPE",
                entityId = "ent-1",
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
            db.syncOperationDao().insert(opUnknownType)

            val conflictUnknown = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "UNKNOWN_TYPE",
                entityId = "ent-1",
                operationId = "op-unknown",
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = "{}",
                remotePayloadJson = "{}",
                detectedAtEpochMillis = 1000L,
            )

            val errorUnknown = assertFailsWith<IllegalStateException> {
                db.localMutationDao().recordV2Conflict(SCOPE, conflictUnknown, 1000L)
            }
            assertTrue(errorUnknown.message?.contains("Bilinmeyen entityTypeCode") == true)

            // Rollback verification: outbox status remains IN_FLIGHT, conflict row NOT saved
            assertEquals("IN_FLIGHT", db.syncOperationDao().getById(SCOPE, "op-unknown")?.statusCode)
            assertNull(db.syncStateDao().getConflict(SCOPE, "UNKNOWN_TYPE", "ent-1"))
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
