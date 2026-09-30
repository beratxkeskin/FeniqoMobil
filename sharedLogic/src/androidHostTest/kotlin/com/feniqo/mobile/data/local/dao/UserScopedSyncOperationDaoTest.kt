package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserScopedSyncOperationDaoTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var localMutationDao: LocalMutationDao

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"
        const val USER_O_ID = "00000000-0000-4000-8000-000000000000" // Workspace record owner
        const val LEGACY_OWNER_ID = "00000000-0000-4000-8000-000000000009" // Backup payload original owner

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
        val SCOPE_O = SyncScopeKey.user(USER_O_ID).rawValue
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .allowMainThreadQueries()
            .build()

        syncOperationDao = database.syncOperationDao()
        localMutationDao = database.localMutationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleOperation(
        operationId: String,
        syncScopeKey: String,
        entityTypeCode: String = "TRANSACTION",
        entityId: String = "tx-1",
        operationTypeCode: String = "CREATE",
        statusCode: String = "PENDING",
        isBlocked: Boolean = false,
        predecessorOperationId: String? = null,
        errorClassification: String? = null,
        attemptCount: Int = 0,
        nextAttemptAt: Long = 1000L,
        now: Long = 1000L,
        payloadJson: String? = "{\"id\":\"$entityId\"}",
    ) = SyncOperationEntity(
        operationId = operationId,
        syncScopeKey = syncScopeKey,
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationTypeCode = operationTypeCode,
        baseVersion = if (operationTypeCode == "CREATE") null else 1L,
        payloadJson = payloadJson,
        predecessorOperationId = predecessorOperationId,
        isBlocked = isBlocked,
        protocolVersion = 2,
        statusCode = statusCode,
        attemptCount = attemptCount,
        lastError = if (statusCode == "FAILED") "Some error" else null,
        nextAttemptAtEpochMillis = nextAttemptAt,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        errorClassification = errorClassification,
    )

    // 1. user_a_pending_create_is_not_selected_for_user_b
    @Test
    fun user_a_pending_create_is_not_selected_for_user_b() = runTest {
        val opA = sampleOperation("op-a-1", SCOPE_A, operationTypeCode = "CREATE", statusCode = "PENDING")
        syncOperationDao.insert(opA)

        val readyForB = syncOperationDao.getReadyOperations(SCOPE_B, nowEpochMillis = 2000L, limit = 10)
        assertTrue(readyForB.isEmpty(), "User A's pending operation must NOT be selected for User B")
    }

    // 2. user_b_ready_operation_is_selected_normally
    @Test
    fun user_b_ready_operation_is_selected_normally() = runTest {
        val opA = sampleOperation("op-a-1", SCOPE_A, statusCode = "PENDING")
        val opB = sampleOperation("op-b-1", SCOPE_B, statusCode = "PENDING")
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val readyForB = syncOperationDao.getReadyOperations(SCOPE_B, nowEpochMillis = 2000L, limit = 10)
        assertEquals(1, readyForB.size)
        assertEquals("op-b-1", readyForB.first().operationId)
        assertEquals(SCOPE_B, readyForB.first().syncScopeKey)
    }

    // 3. user_a_operation_retains_operation_id_payload_and_predecessor
    @Test
    fun user_a_operation_retains_operation_id_payload_and_predecessor() = runTest {
        val opA1 = sampleOperation("op-a-1", SCOPE_A, statusCode = "PENDING", payloadJson = "{\"id\":\"tx-1\",\"amount\":100}")
        val opA2 = sampleOperation(
            "op-a-2",
            SCOPE_A,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-a-1",
            payloadJson = "{\"id\":\"tx-1\",\"amount\":200}",
        )
        syncOperationDao.insert(opA1)
        syncOperationDao.insert(opA2)

        // User B performs operations
        val opB = sampleOperation("op-b-1", SCOPE_B, statusCode = "PENDING")
        syncOperationDao.insert(opB)
        syncOperationDao.claimOperation(SCOPE_B, "op-b-1", 1500L)
        syncOperationDao.deleteCompleted(SCOPE_B, "op-b-1")

        // Verify User A's operation retains all fields
        val loadedA2 = syncOperationDao.getById(SCOPE_A, "op-a-2")
        assertNotNull(loadedA2)
        assertEquals("op-a-2", loadedA2.operationId)
        assertEquals(SCOPE_A, loadedA2.syncScopeKey)
        assertEquals("{\"id\":\"tx-1\",\"amount\":200}", loadedA2.payloadJson)
        assertEquals("op-a-1", loadedA2.predecessorOperationId)
        assertTrue(loadedA2.isBlocked)
        assertEquals("PENDING", loadedA2.statusCode)
    }

    // 4. retry_all_failed_only_retries_requested_user_scope
    @Test
    fun retry_all_failed_only_retries_requested_user_scope() = runTest {
        val opA = sampleOperation("op-a-failed", SCOPE_A, statusCode = "FAILED", errorClassification = "AMBIGUOUS_RESULT")
        val opB = sampleOperation("op-b-failed", SCOPE_B, statusCode = "FAILED", errorClassification = "AMBIGUOUS_RESULT")
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val updatedCount = syncOperationDao.retryAllFailed(SCOPE_A, nowEpochMillis = 2000L)
        assertEquals(1, updatedCount)

        val loadedA = syncOperationDao.getById(SCOPE_A, "op-a-failed")
        assertNotNull(loadedA)
        assertEquals("PENDING", loadedA.statusCode)
        assertNull(loadedA.lastError)
        assertNull(loadedA.errorClassification)

        val loadedB = syncOperationDao.getById(SCOPE_B, "op-b-failed")
        assertNotNull(loadedB)
        assertEquals("FAILED", loadedB.statusCode)
        assertEquals("AMBIGUOUS_RESULT", loadedB.errorClassification)
    }

    // 5. pending_and_failed_counts_only_include_requested_user_scope
    @Test
    fun pending_and_failed_counts_only_include_requested_user_scope() = runTest {
        // User A: 2 pending, 1 failed -> total pending flow count = 3, failed flow count = 1
        syncOperationDao.insert(sampleOperation("op-a-1", SCOPE_A, statusCode = "PENDING"))
        syncOperationDao.insert(sampleOperation("op-a-2", SCOPE_A, statusCode = "IN_FLIGHT"))
        syncOperationDao.insert(sampleOperation("op-a-3", SCOPE_A, statusCode = "FAILED"))

        // User B: 1 pending, 2 failed -> total pending flow count = 3, failed flow count = 2
        syncOperationDao.insert(sampleOperation("op-b-1", SCOPE_B, statusCode = "PENDING"))
        syncOperationDao.insert(sampleOperation("op-b-2", SCOPE_B, statusCode = "FAILED"))
        syncOperationDao.insert(sampleOperation("op-b-3", SCOPE_B, statusCode = "FAILED"))

        assertEquals(3, syncOperationDao.observePendingCount(SCOPE_A).first())
        assertEquals(1, syncOperationDao.observeFailedCount(SCOPE_A).first())

        assertEquals(3, syncOperationDao.observePendingCount(SCOPE_B).first())
        assertEquals(2, syncOperationDao.observeFailedCount(SCOPE_B).first())
    }

    // 6. ambiguous_result_is_not_selected_for_different_user
    @Test
    fun ambiguous_result_is_not_selected_for_different_user() = runTest {
        val opA = sampleOperation(
            "op-a-ambig",
            SCOPE_A,
            statusCode = "FAILED",
            errorClassification = "AMBIGUOUS_RESULT",
            nextAttemptAt = 1000L,
        )
        syncOperationDao.insert(opA)

        val readyB = syncOperationDao.getReadyOperations(SCOPE_B, nowEpochMillis = 2000L, limit = 10)
        assertTrue(readyB.isEmpty())

        val readyA = syncOperationDao.getReadyOperations(SCOPE_A, nowEpochMillis = 2000L, limit = 10)
        assertEquals(1, readyA.size)
        assertEquals("op-a-ambig", readyA.first().operationId)
    }

    // 7. blocked_successor_chain_does_not_cross_user_scope
    @Test
    fun blocked_successor_chain_does_not_cross_user_scope() = runTest {
        val opA1 = sampleOperation("op-a-pred", SCOPE_A, statusCode = "PENDING")
        val opA2 = sampleOperation(
            "op-a-succ",
            SCOPE_A,
            statusCode = "PENDING",
            isBlocked = true,
            predecessorOperationId = "op-a-pred",
        )
        syncOperationDao.insert(opA1)
        syncOperationDao.insert(opA2)

        // User B tries to unblock opA2 -> should affect 0 rows
        val unblockWrong = localMutationDao.unblockSuccessor(
            syncScopeKey = SCOPE_B,
            operationId = "op-a-succ",
            predecessorOperationId = "op-a-pred",
            appliedVersion = 2L,
            nowEpochMillis = 2000L,
        )
        assertEquals(0, unblockWrong)

        val stillBlocked = syncOperationDao.getById(SCOPE_A, "op-a-succ")
        assertNotNull(stillBlocked)
        assertTrue(stillBlocked.isBlocked)

        // User A unblocks opA2 -> succeeds
        val unblockCorrect = localMutationDao.unblockSuccessor(
            syncScopeKey = SCOPE_A,
            operationId = "op-a-succ",
            predecessorOperationId = "op-a-pred",
            appliedVersion = 2L,
            nowEpochMillis = 2000L,
        )
        assertEquals(1, unblockCorrect)

        val unblocked = syncOperationDao.getById(SCOPE_A, "op-a-succ")
        assertNotNull(unblocked)
        assertTrue(!unblocked.isBlocked)
        assertEquals(2L, unblocked.baseVersion)
    }

    // 8. stale_in_flight_recovery_only_updates_requested_user_scope
    @Test
    fun stale_in_flight_recovery_only_updates_requested_user_scope() = runTest {
        val opA = sampleOperation("op-a-stale", SCOPE_A, statusCode = "IN_FLIGHT", now = 500L)
        val opB = sampleOperation("op-b-stale", SCOPE_B, statusCode = "IN_FLIGHT", now = 500L)
        syncOperationDao.insert(opA)
        syncOperationDao.insert(opB)

        val recoveredA = syncOperationDao.recoverStaleInFlight(
            syncScopeKey = SCOPE_A,
            staleBeforeEpochMillis = 1000L,
            nowEpochMillis = 2000L,
            lastError = "Interrupted",
        )
        assertEquals(1, recoveredA)

        val loadedA = syncOperationDao.getById(SCOPE_A, "op-a-stale")
        assertNotNull(loadedA)
        assertEquals("FAILED", loadedA.statusCode)
        assertEquals("AMBIGUOUS_RESULT", loadedA.errorClassification)

        val loadedB = syncOperationDao.getById(SCOPE_B, "op-b-stale")
        assertNotNull(loadedB)
        assertEquals("IN_FLIGHT", loadedB.statusCode)
    }

    // 9. claim_mark_failed_mark_conflict_and_delete_require_matching_scope
    @Test
    fun claim_mark_failed_mark_conflict_and_delete_require_matching_scope() = runTest {
        val opA = sampleOperation("op-a-target", SCOPE_A, statusCode = "PENDING")
        syncOperationDao.insert(opA)

        // Claim with wrong scope -> 0
        assertEquals(0, syncOperationDao.claimOperation(SCOPE_B, "op-a-target", 1500L))
        // Mark failed with wrong scope -> 0
        assertEquals(0, syncOperationDao.markFailed(SCOPE_B, "op-a-target", "err", null, 2000L, 1500L))
        // Mark conflict with wrong scope -> 0
        assertEquals(0, syncOperationDao.markConflict(SCOPE_B, "op-a-target", "err", 1500L))
        // Delete completed with wrong scope -> 0
        assertEquals(0, syncOperationDao.deleteCompleted(SCOPE_B, "op-a-target"))

        // Correct scope claim -> 1
        assertEquals(1, syncOperationDao.claimOperation(SCOPE_A, "op-a-target", 1500L))
        // Correct scope delete -> 1
        assertEquals(1, syncOperationDao.deleteCompleted(SCOPE_A, "op-a-target"))
        assertNull(syncOperationDao.getById(SCOPE_A, "op-a-target"))
    }

    // 10. coalesce_and_convert_to_delete_require_matching_scope
    @Test
    fun coalesce_and_convert_to_delete_require_matching_scope() = runTest {
        val opA = sampleOperation("op-a-coalesce", SCOPE_A, statusCode = "PENDING", attemptCount = 0)
        syncOperationDao.insert(opA)

        // Coalesce with wrong scope -> 0
        val coalWrong = localMutationDao.coalescePendingPayload(SCOPE_B, "op-a-coalesce", "{\"new\":1}", 2000L)
        assertEquals(0, coalWrong)

        // Convert to delete with wrong scope -> 0
        val delWrong = localMutationDao.convertToPendingDelete(SCOPE_B, "op-a-coalesce", null, 2000L)
        assertEquals(0, delWrong)

        // With matching scope -> 1
        val coalCorrect = localMutationDao.coalescePendingPayload(SCOPE_A, "op-a-coalesce", "{\"new\":1}", 2000L)
        assertEquals(1, coalCorrect)

        val delCorrect = localMutationDao.convertToPendingDelete(SCOPE_A, "op-a-coalesce", null, 2000L)
        assertEquals(1, delCorrect)

        val finalOp = syncOperationDao.getById(SCOPE_A, "op-a-coalesce")
        assertNotNull(finalOp)
        assertEquals("DELETE", finalOp.operationTypeCode)
    }

    // 11. same_entity_tail_lookup_does_not_cross_user_scope
    @Test
    fun same_entity_tail_lookup_does_not_cross_user_scope() = runTest {
        val opA = sampleOperation("op-a-tail", SCOPE_A, entityTypeCode = "CATEGORY", entityId = "cat-shared")
        syncOperationDao.insert(opA)

        val tailForB = localMutationDao.getActiveTailCandidates(SCOPE_B, "CATEGORY", "cat-shared")
        assertTrue(tailForB.isEmpty(), "User B must not see User A's tail candidate for same entity")

        val tailForA = localMutationDao.getActiveTailCandidates(SCOPE_A, "CATEGORY", "cat-shared")
        assertEquals(1, tailForA.size)
        assertEquals("op-a-tail", tailForA.first().operationId)
    }

    // 12. user_a_soft_delete_operation_survives_user_b_activity
    @Test
    fun user_a_soft_delete_operation_survives_user_b_activity() = runTest {
        val opA = sampleOperation("op-a-soft-delete", SCOPE_A, operationTypeCode = "DELETE", statusCode = "PENDING")
        syncOperationDao.insert(opA)

        // User B performs operations: inserting, claiming, deleting
        val opB = sampleOperation("op-b-work", SCOPE_B, statusCode = "PENDING")
        syncOperationDao.insert(opB)
        syncOperationDao.claimOperation(SCOPE_B, "op-b-work", 1500L)
        syncOperationDao.deleteCompleted(SCOPE_B, "op-b-work")
        syncOperationDao.retryAllFailed(SCOPE_B, 2000L)

        // User A's soft-delete operation is completely intact
        val loadedA = syncOperationDao.getById(SCOPE_A, "op-a-soft-delete")
        assertNotNull(loadedA)
        assertEquals("DELETE", loadedA.operationTypeCode)
        assertEquals("PENDING", loadedA.statusCode)
        assertEquals(SCOPE_A, loadedA.syncScopeKey)
    }

    // 13. workspace_mutation_uses_authenticated_actor_not_record_owner
    @Test
    fun workspace_mutation_uses_authenticated_actor_not_record_owner() = runTest {
        // Workspace owned by USER_O, member is USER_A (the actor)
        val workspace = WorkspaceEntity(
            id = "ws-1",
            name = "Team Workspace",
            normalizedName = "team workspace",
            ownerId = USER_O_ID,
            typeCode = "COUPLE",
            currencyCode = "TRY",
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        )
        val members = listOf(
            WorkspaceMemberEntity(
                workspaceId = "ws-1",
                userId = USER_O_ID,
                roleCode = "OWNER",
                joinedAtEpochMillis = 1000L,
                sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
            ),
            WorkspaceMemberEntity(
                workspaceId = "ws-1",
                userId = USER_A_ID,
                roleCode = "MEMBER",
                joinedAtEpochMillis = 1000L,
                sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
            ),
        )

        // User A performs mutation as authenticated actor -> pass SCOPE_A, NOT SCOPE_O
        val result = localMutationDao.mutateWorkspaceV2(
            syncScopeKey = SCOPE_A,
            entity = workspace,
            members = members,
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"id\":\"ws-1\"}",
            operationIdFactory = { "11111111111111111111111111111111" },
            nowEpochMillis = 1000L,
        )

        val outbox = syncOperationDao.getById(SCOPE_A, result.operationId)
        assertNotNull(outbox)
        assertEquals(SCOPE_A, outbox.syncScopeKey, "Workspace outbox syncScopeKey MUST be authenticated actor, not record owner")

        // Must NOT exist in record owner's scope
        val outboxInOwner = syncOperationDao.getById(SCOPE_O, result.operationId)
        assertNull(outboxInOwner)
    }

    // 14. backup_or_import_enqueue_uses_authenticated_actor_not_payload_owner
    @Test
    fun backup_or_import_enqueue_uses_authenticated_actor_not_payload_owner() = runTest {
        val categoryInput = BackupCategoryCreateInputV1(
            entity = CategoryEntity(
                id = "cat-imported",
                ownerId = LEGACY_OWNER_ID, // Payload had legacy owner
                workspaceId = null,
                scopeKey = LEGACY_OWNER_ID,
                name = "Imported",
                normalizedName = "imported",
                slug = "imported",
                typeCode = "EXPENSE",
                colorHex = "#ff0000",
                iconKey = null,
                isDefault = false,
                createdAtEpochMillis = 1000L,
                sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
            ),
            payloadJson = "{\"id\":\"cat-imported\",\"owner_id\":\"$LEGACY_OWNER_ID\"}",
        )

        val opIds = localMutationDao.importPersonalBackupV1(
            syncScopeKey = SCOPE_A, // Authenticated actor is USER_A
            categoryInputs = listOf(categoryInput),
            transactionInputs = emptyList(),
            operationIdFactory = { "22222222222222222222222222222222" },
            nowEpochMillis = 1000L,
        )

        assertEquals(1, opIds.size)
        val outbox = syncOperationDao.getById(SCOPE_A, opIds.first())
        assertNotNull(outbox)
        assertEquals(SCOPE_A, outbox.syncScopeKey, "Import outbox MUST use authenticated actor SCOPE_A, not payload owner")

        val legacyScope = SyncScopeKey.user(LEGACY_OWNER_ID).rawValue
        val outboxInLegacy = syncOperationDao.getById(legacyScope, opIds.first())
        assertNull(outboxInLegacy)
    }

    // 11. malformed USER scope ile LocalMutationDao yeni operation yazmaz
    @Test
    fun localMutationDao_rejects_malformed_user_scope_and_legacy_unresolved() = runTest {
        val category = CategoryEntity(
            id = "cat-malformed-test",
            ownerId = USER_A_ID,
            workspaceId = null,
            scopeKey = USER_A_ID,
            name = "Test",
            normalizedName = "test",
            slug = "test",
            typeCode = "EXPENSE",
            colorHex = "#ffffff",
            iconKey = null,
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        )

        // Case A: Non-UUID user scope: "USER:user-1"
        assertFailsWith<IllegalArgumentException> {
            localMutationDao.mutateCategoryV2(
                syncScopeKey = "USER:user-1",
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = "{\"id\":\"cat-malformed-test\"}",
                operationIdFactory = { "11111111111111111111111111111111" },
                nowEpochMillis = 1000L,
            )
        }

        // Case B: Quarantine scope: "LEGACY_UNRESOLVED"
        assertFailsWith<IllegalArgumentException> {
            localMutationDao.mutateCategoryV2(
                syncScopeKey = "LEGACY_UNRESOLVED",
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = "{\"id\":\"cat-malformed-test\"}",
                operationIdFactory = { "22222222222222222222222222222222" },
                nowEpochMillis = 1000L,
            )
        }

        // Case C: Compact user scope: "USER:0000000000004000800000000000000a"
        assertFailsWith<IllegalArgumentException> {
            localMutationDao.mutateCategoryV2(
                syncScopeKey = "USER:0000000000004000800000000000000a",
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = "{\"id\":\"cat-malformed-test\"}",
                operationIdFactory = { "44444444444444444444444444444444" },
                nowEpochMillis = 1000L,
            )
        }

        // Verify no outbox row was written
        val count = syncOperationDao.observePendingCount(SCOPE_A).first()
        assertEquals(0, count)
    }

    // 12. canonical kullanıcı scope’u ile mevcut normal enqueue/process davranışı çalışmaya devam eder
    @Test
    fun localMutationDao_accepts_canonical_user_scope_and_enqueues_operation() = runTest {
        val category = CategoryEntity(
            id = "cat-canonical-test",
            ownerId = USER_A_ID,
            workspaceId = null,
            scopeKey = USER_A_ID,
            name = "Groceries",
            normalizedName = "groceries",
            slug = "groceries",
            typeCode = "EXPENSE",
            colorHex = "#00ff00",
            iconKey = null,
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        )

        val result = localMutationDao.mutateCategoryV2(
            syncScopeKey = SCOPE_A, // Canonical: "USER:00000000-0000-4000-8000-00000000000a"
            entity = category,
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"id\":\"cat-canonical-test\"}",
            operationIdFactory = { "33333333333333333333333333333333" },
            nowEpochMillis = 1000L,
        )

        assertEquals("33333333333333333333333333333333", result.operationId)
        val outbox = syncOperationDao.getById(SCOPE_A, result.operationId)
        assertNotNull(outbox)
        assertEquals(SCOPE_A, outbox.syncScopeKey)
        assertEquals(V2EnqueueDecision.INSERTED, result.decision)
    }
}
