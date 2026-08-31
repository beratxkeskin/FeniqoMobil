package com.feniqo.mobile.data.local.outbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.domain.model.SyncStatus
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
class OfflineWriteQueueCoalescingTest {

    @Test
    fun real_entity_and_outbox_coalescing_of_untried_pending_create_and_update() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-1"
            val initialCategory = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueCategoryV2(
                entity = initialCategory,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Verify initial entity in Room
            val cat1 = db.categoryDao().observeById(entityId).first()
            assertNotNull(cat1)
            assertEquals("Market", cat1.name)

            // Coalesce with UPDATE
            val updatedCategory = initialCategory.copy(
                name = "Süpermarket",
                normalizedName = "süpermarket",
                sync = initialCategory.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2 = queue.enqueueCategoryV2(
                entity = updatedCategory,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            assertEquals(op1, op2, "Untried pending op should coalesce in-place and return same operation ID")

            // Verify entity updated in Room
            val cat2 = db.categoryDao().observeById(entityId).first()
            assertNotNull(cat2)
            assertEquals("Süpermarket", cat2.name)

            // Verify outbox row updated in Room
            val stored = db.syncOperationDao().getById(op1)
            assertNotNull(stored)
            assertEquals("""{"name":"Süpermarket"}""", stored.payloadJson)
            assertEquals(OutboxOperationType.CREATE.name, stored.operationTypeCode)
            assertEquals(0, stored.attemptCount)
            assertFalse(stored.isBlocked)
        } finally {
            db.close()
        }
    }

    @Test
    fun real_entity_and_outbox_hard_delete_on_untried_create_and_delete() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-2"
            val initialCategory = categoryEntity(id = entityId, name = "Giyim", syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueCategoryV2(
                entity = initialCategory,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Giyim"}""",
            )

            assertNotNull(db.categoryDao().observeById(entityId).first())
            assertNotNull(db.syncOperationDao().getById(op1))

            // Enqueue DELETE on untried CREATE
            val delCategory = initialCategory.copy(
                sync = initialCategory.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val delOpId = queue.enqueueCategoryV2(
                entity = delCategory,
                type = OutboxOperationType.DELETE,
                payloadJson = """{}""",
            )

            assertEquals(op1, delOpId)

            // Verify physical hard delete: Entity removed from table completely
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM categories WHERE id = '$entityId'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0), "Category must be physically deleted from Room")
            cursor.close()

            // Verify outbox row completely deleted
            assertNull(db.syncOperationDao().getById(op1), "Outbox row must be deleted")
            assertEquals(0, db.syncOperationDao().observePendingCount().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun transaction_real_entity_and_tags_hard_delete_on_untried_create_and_delete() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val txId = "tx-c-3"
            val category = categoryEntity("cat-c-3", "Market", SyncStatus.SYNCED)
            db.categoryDao().upsert(category)

            val tag = TagEntity(id = "tag-1", ownerId = "user-1", workspaceId = null, scopeKey = "personal:user-1", name = "gıda", normalizedName = "gida", createdAtEpochMillis = 1000L, sync = newSyncMetadata(1000L))
            val tagLink = TransactionTagCrossRef(transactionId = txId, tagId = "tag-1", createdAtEpochMillis = 1000L, sync = newSyncMetadata(1000L))
            val transaction = transactionEntity(id = txId, categoryId = category.id, syncStatus = SyncStatus.PENDING_CREATE)

            val op1 = queue.enqueueTransactionV2(
                entity = transaction,
                tags = listOf(tag),
                tagLinks = listOf(tagLink),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"amount":1000}""",
            )

            assertNotNull(db.transactionDao().getByIdAndOwner(txId, "user-1"))
            assertEquals(1, db.syncOperationDao().observePendingCount().first())

            // Hard delete
            val delTransaction = transaction.copy(
                sync = transaction.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                ),
            )
            val delOpId = queue.enqueueTransactionKeepingTagsV2(
                entity = delTransaction,
                type = OutboxOperationType.DELETE,
                payloadJson = """{}""",
            )

            assertEquals(op1, delOpId)

            // Verify physical hard delete of transaction and tag link
            assertNull(db.transactionDao().getByIdAndOwner(txId, "user-1"))
            val linkCursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_tags WHERE transaction_id = '$txId'")
            linkCursor.moveToFirst()
            assertEquals(0, linkCursor.getInt(0), "Transaction tag link must be removed")
            linkCursor.close()

            assertNull(db.syncOperationDao().getById(op1))
        } finally {
            db.close()
        }
    }

    @Test
    fun attempted_pending_create_and_update_creates_blocked_successor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-4"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Claim op1 (makes it IN_FLIGHT, attempt=1)
            val claimed = queue.claimNextReadyOperation()
            assertNotNull(claimed)
            assertEquals(op1Id, claimed.operationId)
            assertEquals(1, claimed.attemptCount)
            assertEquals(OutboxStatus.IN_FLIGHT.name, claimed.statusCode)

            // Enqueue subsequent UPDATE while op1 is IN_FLIGHT
            val updatedCategory = category.copy(
                name = "Süpermarket",
                sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name),
            )
            val op2Id = queue.enqueueCategoryV2(
                entity = updatedCategory,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            assertTrue(op1Id != op2Id, "Successor must have unique operation ID")

            val op2 = db.syncOperationDao().getById(op2Id)
            assertNotNull(op2)
            assertTrue(op2.isBlocked, "Successor must be blocked pending predecessor resolution")
            assertEquals(op1Id, op2.predecessorOperationId)
            assertNull(op2.baseVersion)
            assertEquals(2, op2.protocolVersion)

            // Verify entity updated in Room
            assertEquals("Süpermarket", db.categoryDao().observeById(entityId).first()?.name)

            // Verify getReadyOperations does NOT return blocked successor
            val ready = queue.getReadyOperations()
            assertEquals(0, ready.size, "Blocked successor should not be ready")
        } finally {
            db.close()
        }
    }

    @Test
    fun blocked_update_successor_coalesces_second_update_without_branching() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-5"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Claim op1
            queue.claimNextReadyOperation()

            // Enqueue UPDATE -> becomes Op2 (blocked successor)
            val updated1 = category.copy(name = "Süpermarket", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op2Id = queue.enqueueCategoryV2(
                entity = updated1,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            // Enqueue 2nd UPDATE -> must coalesce into Op2, NOT create Op3!
            val updated2 = category.copy(name = "Mega Market", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op3Id = queue.enqueueCategoryV2(
                entity = updated2,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Mega Market"}""",
            )

            assertEquals(op2Id, op3Id, "Subsequent mutation must coalesce into single tail successor")

            val op2 = db.syncOperationDao().getById(op2Id)
            assertNotNull(op2)
            assertEquals("""{"name":"Mega Market"}""", op2.payloadJson)
            assertEquals(op1Id, op2.predecessorOperationId)
            assertTrue(op2.isBlocked)

            // Verify entity updated in Room
            assertEquals("Mega Market", db.categoryDao().observeById(entityId).first()?.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun blocked_update_successor_converts_to_delete_and_tombstones_entity() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-6"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            queue.claimNextReadyOperation()

            val updated1 = category.copy(name = "Süpermarket", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op2Id = queue.enqueueCategoryV2(
                entity = updated1,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            // Enqueue DELETE on blocked successor
            val delCategory = category.copy(
                sync = category.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 3000L,
                ),
            )
            val op3Id = queue.enqueueCategoryV2(
                entity = delCategory,
                type = OutboxOperationType.DELETE,
                payloadJson = """{}""",
            )

            assertEquals(op2Id, op3Id)

            val op2 = db.syncOperationDao().getById(op2Id)
            assertNotNull(op2)
            assertEquals(OutboxOperationType.DELETE.name, op2.operationTypeCode)
            assertTrue(op2.isBlocked)
            assertEquals(op1Id, op2.predecessorOperationId)

            // Entity tombstoned in Room
            assertNull(db.categoryDao().observeById(entityId).first()) // Soft deleted
        } finally {
            db.close()
        }
    }

    @Test
    fun attempted_update_creates_successor_delete() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-7"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            queue.claimNextReadyOperation()

            val updated = category.copy(name = "Süpermarket", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op2Id = queue.enqueueCategoryV2(
                entity = updated,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            // Resolve op1
            queue.resolvePredecessorSuccessor(op1Id, appliedVersion = 2L)

            // Claim op2 (now IN_FLIGHT, attempt=1)
            val claimed2 = queue.claimNextReadyOperation()
            assertNotNull(claimed2)
            assertEquals(op2Id, claimed2.operationId)

            // Enqueue DELETE while op2 is IN_FLIGHT -> must create successor Op3
            val delCategory = category.copy(
                sync = category.sync.copy(
                    version = 2L,
                    baseVersion = 2L,
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 4000L,
                ),
            )
            val op3Id = queue.enqueueCategoryV2(
                entity = delCategory,
                type = OutboxOperationType.DELETE,
                payloadJson = """{}""",
            )

            assertTrue(op2Id != op3Id)
            val op3 = db.syncOperationDao().getById(op3Id)
            assertNotNull(op3)
            assertEquals(OutboxOperationType.DELETE.name, op3.operationTypeCode)
            assertTrue(op3.isBlocked)
            assertEquals(op2Id, op3.predecessorOperationId)
        } finally {
            db.close()
        }
    }

    @Test
    fun legacy_v1_operation_is_not_coalesced_into_v2() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-8"

            // Insert legacy V1 operation
            db.syncOperationDao().insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "legacy-op-8",
                    entityTypeCode = "CATEGORY",
                    entityId = entityId,
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
                ),
            )

            val updatedCategory = categoryEntity(id = entityId, name = "Giyim", syncStatus = SyncStatus.PENDING_UPDATE)
            val v2OpId = queue.enqueueCategoryV2(
                entity = updatedCategory,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Giyim"}""",
            )

            assertTrue("legacy-op-8" != v2OpId, "Legacy V1 operation must NOT be coalesced into V2")
            val legacyOp = db.syncOperationDao().getById("legacy-op-8")
            assertNotNull(legacyOp)
            assertEquals(1, legacyOp.protocolVersion)
            assertNull(legacyOp.payloadJson)

            val v2Op = db.syncOperationDao().getById(v2OpId)
            assertNotNull(v2Op)
            assertEquals(2, v2Op.protocolVersion)
            assertEquals("legacy-op-8", v2Op.predecessorOperationId)
            assertTrue(v2Op.isBlocked)
        } finally {
            db.close()
        }
    }

    @Test
    fun multiple_tails_fail_closed() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-9"

            // Manually insert 2 uncompleted leaf operations without predecessors referencing each other
            db.syncOperationDao().insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "tail-1",
                    entityTypeCode = "CATEGORY",
                    entityId = entityId,
                    operationTypeCode = "UPDATE",
                    baseVersion = null,
                    payloadJson = """{"name":"1"}""",
                    predecessorOperationId = null,
                    isBlocked = false,
                    protocolVersion = 2,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1000L,
                    createdAtEpochMillis = 1000L,
                    updatedAtEpochMillis = 1000L,
                ),
            )
            db.syncOperationDao().insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "tail-2",
                    entityTypeCode = "CATEGORY",
                    entityId = entityId,
                    operationTypeCode = "UPDATE",
                    baseVersion = null,
                    payloadJson = """{"name":"2"}""",
                    predecessorOperationId = null,
                    isBlocked = false,
                    protocolVersion = 2,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 2000L,
                    createdAtEpochMillis = 2000L,
                    updatedAtEpochMillis = 2000L,
                ),
            )

            val updatedCategory = categoryEntity(id = entityId, name = "Giyim", syncStatus = SyncStatus.PENDING_UPDATE)
            assertFailsWith<IllegalStateException> {
                queue.enqueueCategoryV2(
                    entity = updatedCategory,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = """{"name":"Giyim"}""",
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun atomic_predecessor_resolve_and_failure_rollbacks() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-10"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            queue.claimNextReadyOperation()

            val updated = category.copy(name = "Süpermarket", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op2Id = queue.enqueueCategoryV2(
                entity = updated,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            // Test 1: Resolve non-existent predecessor throws exception
            assertFailsWith<IllegalArgumentException> {
                queue.resolvePredecessorSuccessor("non-existent-op", 2L)
            }

            // Test 2: Successful atomic resolve
            val resolved = queue.resolvePredecessorSuccessor(op1Id, appliedVersion = 2L)
            assertTrue(resolved)

            assertNull(db.syncOperationDao().getById(op1Id))
            val op2 = db.syncOperationDao().getById(op2Id)
            assertNotNull(op2)
            assertFalse(op2.isBlocked)
            assertEquals(2L, op2.baseVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun stale_in_flight_recovery_does_not_double_increment_attempt() = runTest {
        val db = inMemoryDatabase()
        try {
            var currentTime = 1000L
            val queue = createQueue(db, nowProvider = { currentTime })
            val entityId = "cat-c-11"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Initial attempt = 0
            assertEquals(0, db.syncOperationDao().getById(op1Id)?.attemptCount)

            // 1st Claim -> attempt = 1, status = IN_FLIGHT
            val claimed = queue.claimNextReadyOperation()
            assertNotNull(claimed)
            assertEquals(1, claimed.attemptCount)
            assertEquals(OutboxStatus.IN_FLIGHT.name, claimed.statusCode)

            // Stale recovery after timeout -> status = FAILED, attempt MUST STILL BE 1
            currentTime = 2000L
            db.syncOperationDao().recoverStaleInFlight(
                staleBeforeEpochMillis = 2000L,
                nowEpochMillis = 2000L,
                lastError = "Stale timeout",
            )
            val recovered = db.syncOperationDao().getById(op1Id)
            assertNotNull(recovered)
            assertEquals(OutboxStatus.FAILED.name, recovered.statusCode)
            assertEquals(1, recovered.attemptCount, "Stale recovery must NOT increment attempt count")

            // Reset next_attempt to allow immediate retry
            currentTime = 3000L
            db.syncOperationDao().retryAllFailed(3000L)

            // 2nd Claim on retry -> attempt = 2
            val reClaimed = queue.claimNextReadyOperation()
            assertNotNull(reClaimed)
            assertEquals(2, reClaimed.attemptCount, "Re-claiming failed retry must increment attempt to 2")
            assertEquals(OutboxStatus.IN_FLIGHT.name, reClaimed.statusCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun claim_safety_and_race_prevention() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-12"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // 1st claim wins
            val firstClaim = db.syncOperationDao().claimOperation(op1Id, 1000L)
            assertEquals(1, firstClaim)

            // 2nd concurrent claim on same op returns 0 (atomic failure)
            val secondClaim = db.syncOperationDao().claimOperation(op1Id, 1000L)
            assertEquals(0, secondClaim)

            // Blocked op cannot be claimed
            val op2Id = queue.enqueueCategoryV2(
                entity = category.copy(sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name)),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )
            val blockedClaim = db.syncOperationDao().claimOperation(op2Id, 1000L)
            assertEquals(0, blockedClaim, "Blocked operation cannot be claimed")
        } finally {
            db.close()
        }
    }

    @Test
    fun record_failure_does_not_modify_attempt_count() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-13"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Claim operation -> attempt = 1, status = IN_FLIGHT
            val claimed = queue.claimNextReadyOperation()
            assertNotNull(claimed)
            assertEquals(1, claimed.attemptCount)
            assertEquals(OutboxStatus.IN_FLIGHT.name, claimed.statusCode)

            // Record failure
            val recorded = queue.recordFailure(op1Id, "network timeout")
            assertTrue(recorded)

            val failedOp = db.syncOperationDao().getById(op1Id)
            assertNotNull(failedOp)
            assertEquals(OutboxStatus.FAILED.name, failedOp.statusCode)
            assertEquals(1, failedOp.attemptCount, "recordFailure must NOT increment or overwrite attempt count")
            assertEquals("network timeout", failedOp.lastError)
        } finally {
            db.close()
        }
    }

    @Test
    fun real_rollback_when_successor_integrity_or_unblock_fails() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val entityId = "cat-c-14"
            val category = categoryEntity(id = entityId, name = "Market", syncStatus = SyncStatus.PENDING_CREATE)

            val op1Id = queue.enqueueCategoryV2(
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Market"}""",
            )

            // Claim op1 -> IN_FLIGHT, attempt = 1
            queue.claimNextReadyOperation()

            val updated = category.copy(name = "Süpermarket", sync = category.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name))
            val op2Id = queue.enqueueCategoryV2(
                entity = updated,
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Süpermarket"}""",
            )

            // Corrupt successor in DB: change attempt_count to 1 so integrity check fails
            val op2 = db.syncOperationDao().getById(op2Id)
            assertNotNull(op2)
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_operations SET attempt_count = 1 WHERE operation_id = '$op2Id'",
            )

            // Resolve must fail
            assertFailsWith<IllegalStateException> {
                queue.resolvePredecessorSuccessor(op1Id, 2L)
            }

            // Verify rollback: op1 is STILL present in Room DB with status IN_FLIGHT
            val rolledBackPredecessor = db.syncOperationDao().getById(op1Id)
            assertNotNull(rolledBackPredecessor, "Predecessor must NOT be deleted due to transaction rollback")
            assertEquals(OutboxStatus.IN_FLIGHT.name, rolledBackPredecessor.statusCode)

            // Successor is still blocked
            val rolledBackSuccessor = db.syncOperationDao().getById(op2Id)
            assertNotNull(rolledBackSuccessor)
            assertTrue(rolledBackSuccessor.isBlocked)
        } finally {
            db.close()
        }
    }

    @Test
    fun profile_delete_is_rejected() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val profile = com.feniqo.mobile.data.local.entity.UserProfileEntity(
                id = "usr-1",
                email = "test@example.com",
                fullName = "Test User",
                currencyCode = "TRY",
                themeCode = "SYSTEM",
                languageCode = "tr",
                activeWorkspaceId = null,
                createdAtEpochMillis = 1000L,
                sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, deletedAtEpochMillis = 1000L),
            )

            val error = assertFailsWith<IllegalArgumentException> {
                queue.enqueueProfileV2(profile, OutboxOperationType.DELETE, "{}")
            }
            assertTrue(error.message?.contains("Profil silme işlemi desteklenmemektedir") == true)
        } finally {
            db.close()
        }
    }

    @Test
    fun operation_id_validation_rejects_invalid_hex() = runTest {
        val db = inMemoryDatabase()
        try {
            val invalidQueue = OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                operationIdFactory = { "not-a-32-hex-string" },
            )

            val category = categoryEntity(id = "cat-c-15", name = "Market", syncStatus = SyncStatus.PENDING_CREATE)
            val error = assertFailsWith<IllegalArgumentException> {
                invalidQueue.enqueueCategoryV2(
                    entity = category,
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"name":"Market"}""",
                )
            }
            assertTrue(error.message?.contains("32 karakter küçük harfli onaltılık") == true)
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
        nowProvider: () -> Long = { 1000L },
    ): OfflineWriteQueue {
        return OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
            nowEpochMillisProvider = nowProvider,
        )
    }

    private fun categoryEntity(id: String, name: String, syncStatus: SyncStatus): CategoryEntity =
        CategoryEntity(
            id = id,
            ownerId = "user-1",
            workspaceId = null,
            scopeKey = "personal:user-1",
            name = name,
            normalizedName = name.lowercase(),
            slug = name.lowercase(),
            typeCode = "EXPENSE",
            colorHex = "#EF4444",
            iconKey = "shopping-cart",
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L).copy(syncStatus = syncStatus.name),
        )

    private fun transactionEntity(id: String, categoryId: String, syncStatus: SyncStatus): TransactionEntity =
        TransactionEntity(
            id = id,
            ownerId = "user-1",
            workspaceId = null,
            amountMinor = 1000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = categoryId,
            description = "test transaction",
            searchText = "test transaction",
            paymentMethodCode = "CASH",
            transactionDate = "2026-08-01",
            receiptPath = null,
            installmentGroupId = null,
            installmentNumber = null,
            totalInstallments = null,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L).copy(syncStatus = syncStatus.name),
        )
}
