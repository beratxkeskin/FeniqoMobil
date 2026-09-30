package com.feniqo.mobile.data.sync

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
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
class ConflictRecoveryServiceTest {

    private val json = Json { encodeDefaults = true; explicitNulls = true }

    private companion object {
        const val SCOPE = "USER:11111111-1111-4111-8111-111111111111"
        const val SCOPE_A = "USER:11111111-1111-4111-8111-111111111111"
        const val SCOPE_B = "USER:22222222-2222-4222-8222-222222222222"
    }

    @Test
    fun single_equivalent_create_recovery_resolves_and_cleans_conflict_and_outbox_when_in_conflict_status() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            val category = categoryEntity("cat-1", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-1",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                remotePayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            queue.recordV2Conflict(SCOPE, conflict)

            // Op is now in CONFLICT status
            val conflictOp = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(conflictOp)
            assertEquals("CONFLICT", conflictOp.statusCode)

            // Run single equivalent recovery
            val recovered = service.recoverSingleEquivalentCreate(SCOPE, "CATEGORY", "cat-1")
            assertTrue(recovered)

            // Verify: conflict deleted, outbox deleted, category synced with version 1
            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-1"))
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            val syncedCat = db.categoryDao().getByIdAndOwner("cat-1", "usr-1")
            assertNotNull(syncedCat)
            assertEquals("SYNCED", syncedCat.sync.syncStatus)
            assertEquals(1L, syncedCat.sync.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun in_flight_operation_is_not_recovered_by_recovery_service() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            val category = categoryEntity("cat-if", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-if","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            // Claim op -> stays IN_FLIGHT (not CONFLICT)
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-if",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-if","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                remotePayloadJson = """{"id":"cat-if","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            db.syncStateDao().upsertConflict(conflict)

            // Recovery MUST fail because op is IN_FLIGHT, not CONFLICT
            val recovered = service.recoverSingleEquivalentCreate(SCOPE, "CATEGORY", "cat-if")
            assertFalse(recovered)

            // Preserved
            assertNotNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-if"))
            assertNotNull(db.syncOperationDao().getById(SCOPE, opId))
        } finally {
            db.close()
        }
    }

    @Test
    fun legacy_v1_conflict_recovered_by_recovery_service() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            val category = categoryEntity("cat-legacy", "Market")
            val opId = queue.enqueueCategory(syncScopeKey = SCOPE, entity = category, type = OutboxOperationType.CREATE)
            queue.claimOperation(SCOPE, opId)
            queue.markConflict(syncScopeKey = SCOPE, operationId = opId, errorMessage = "Legacy conflict")

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-legacy",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-legacy","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                remotePayloadJson = """{"id":"cat-legacy","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            db.syncStateDao().upsertConflict(conflict)

            val legacyOp = db.syncOperationDao().getById(SCOPE, opId)
            assertNotNull(legacyOp)
            assertEquals(1, legacyOp.protocolVersion)
            assertEquals("CONFLICT", legacyOp.statusCode)

            val recovered = service.recoverSingleEquivalentCreate(SCOPE, "CATEGORY", "cat-legacy")
            assertTrue(recovered)

            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-legacy"))
            assertNull(db.syncOperationDao().getById(SCOPE, opId))
            val syncedCat = db.categoryDao().getByIdAndOwner("cat-legacy", "usr-1")
            assertNotNull(syncedCat)
            assertEquals("SYNCED", syncedCat.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun single_non_equivalent_create_recovery_returns_false_and_preserves_conflict() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            val category = categoryEntity("cat-2", "Market")
            val opId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = category,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-2","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, opId)

            val conflict = SyncConflictEntity(
                syncScopeKey = SCOPE,
                entityTypeCode = "CATEGORY",
                entityId = "cat-2",
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = """{"id":"cat-2","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                remotePayloadJson = """{"id":"cat-2","user_id":"usr-1","name":"Faturalar","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                detectedAtEpochMillis = 1000L,
            )
            queue.recordV2Conflict(SCOPE, conflict)

            val recovered = service.recoverSingleEquivalentCreate(SCOPE, "CATEGORY", "cat-2")
            assertFalse(recovered)

            // Preserved
            assertNotNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-2"))
            assertNotNull(db.syncOperationDao().getById(SCOPE, opId))
        } finally {
            db.close()
        }
    }

    @Test
    fun installment_group_recovery_all_or_nothing_when_all_are_equivalent() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            db.categoryDao().upsert(categoryEntity("cat-1", "Elektronik"))

            val grpId = "grp-100"
            val txIds = listOf("tx-101", "tx-102", "tx-103")
            val opIds = mutableListOf<String>()

            for (i in 1..3) {
                val tx = transactionEntity(
                    id = txIds[i - 1],
                    amountMinor = if (i == 3) 33334 else 33333,
                    installmentNumber = i,
                    totalInstallments = 3,
                    installmentGroupId = grpId,
                )
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-1",
                    amountMinor = tx.amountMinor,
                    currency = "TRY",
                    type = "expense",
                    categoryId = "cat-1",
                    paymentMethod = "credit_card",
                    transactionDate = "2026-08-25",
                    installmentNumber = i,
                    totalInstallments = 3,
                    installmentGroupId = grpId,
                    createdAt = "2026-08-25T17:00:00Z",
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                opIds.add(opId)
                queue.claimOperation(SCOPE, opId)

                val conflict = SyncConflictEntity(
                    syncScopeKey = SCOPE,
                    entityTypeCode = "TRANSACTION",
                    entityId = tx.id,
                    operationId = opId,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = json.encodeToString(dto),
                    remotePayloadJson = json.encodeToString(dto.copy(version = 1L)),
                    detectedAtEpochMillis = 1000L,
                )
                queue.recordV2Conflict(SCOPE, conflict)
            }

            // All 3 are in CONFLICT status
            for (opId in opIds) {
                assertEquals("CONFLICT", db.syncOperationDao().getById(SCOPE, opId)?.statusCode)
            }

            // Run installment group recovery
            val recovered = service.recoverInstallmentGroup(SCOPE, grpId)
            assertTrue(recovered, "All 3 installments are equivalent and must be resolved atomically")

            // Verify: all 3 conflicts deleted, all 3 outbox rows deleted, all 3 transactions SYNCED
            for (id in txIds) {
                assertNull(db.syncStateDao().getConflict(SCOPE, "TRANSACTION", id))
                val syncedTx = db.transactionDao().getByIdAndOwner(id, "usr-1")
                assertNotNull(syncedTx)
                assertEquals("SYNCED", syncedTx.sync.syncStatus)
                assertEquals(1L, syncedTx.sync.version)
            }
            for (opId in opIds) {
                assertNull(db.syncOperationDao().getById(SCOPE, opId))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun installment_group_recovery_fails_and_preserves_all_when_one_installment_differs() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            db.categoryDao().upsert(categoryEntity("cat-1", "Elektronik"))

            val grpId = "grp-200"
            val txIds = listOf("tx-201", "tx-202", "tx-203")
            val opIds = mutableListOf<String>()

            for (i in 1..3) {
                val tx = transactionEntity(
                    id = txIds[i - 1],
                    amountMinor = if (i == 3) 33334 else 33333,
                    installmentNumber = i,
                    totalInstallments = 3,
                    installmentGroupId = grpId,
                )
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-1",
                    amountMinor = tx.amountMinor,
                    currency = "TRY",
                    type = "expense",
                    categoryId = "cat-1",
                    paymentMethod = "credit_card",
                    transactionDate = "2026-08-25",
                    installmentNumber = i,
                    totalInstallments = 3,
                    installmentGroupId = grpId,
                    createdAt = "2026-08-25T17:00:00Z",
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                opIds.add(opId)
                queue.claimOperation(SCOPE, opId)

                // 2nd installment has DIFFERENT amount on remote
                val remoteDto = if (i == 2) dto.copy(amountMinor = 99999, version = 1L) else dto.copy(version = 1L)
                val conflict = SyncConflictEntity(
                    syncScopeKey = SCOPE,
                    entityTypeCode = "TRANSACTION",
                    entityId = tx.id,
                    operationId = opId,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = json.encodeToString(dto),
                    remotePayloadJson = json.encodeToString(remoteDto),
                    detectedAtEpochMillis = 1000L,
                )
                queue.recordV2Conflict(SCOPE, conflict)
            }

            // Run installment group recovery -> must fail completely
            val recovered = service.recoverInstallmentGroup(SCOPE, grpId)
            assertFalse(recovered, "Group recovery must fail if any installment is not equivalent")

            // Verify all 3 conflicts are still present
            for (id in txIds) {
                assertNotNull(db.syncStateDao().getConflict(SCOPE, "TRANSACTION", id))
            }
            for (opId in opIds) {
                assertNotNull(db.syncOperationDao().getById(SCOPE, opId))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_all_pending_conflicts_recovers_groups_and_singles_in_one_scan() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            // 1. Setup single category conflict
            val cat = categoryEntity("cat-1", "Market")
            val catOpId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = cat,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, catOpId)
            queue.recordV2Conflict(
                SCOPE,
                SyncConflictEntity(
                    syncScopeKey = SCOPE,
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-1",
                    operationId = catOpId,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                    remotePayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                    detectedAtEpochMillis = 1000L,
                )
            )

            // 2. Setup 2-installment group conflict
            val grpId = "grp-all"
            for (i in 1..2) {
                val tx = transactionEntity("tx-all-$i", 10000L, i, 2, grpId)
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-1",
                    workspaceId = null,
                    amountMinor = 10000L,
                    currency = "TRY",
                    type = "EXPENSE",
                    categoryId = "cat-1",
                    description = "Taksitli işlem",
                    paymentMethod = "CREDIT_CARD",
                    transactionDate = "2026-08-25",
                    receiptPath = null,
                    installmentNumber = i,
                    totalInstallments = 2,
                    installmentGroupId = grpId,
                    deletedAt = null,
                    createdAt = "2026-08-25T17:00:00Z",
                    updatedAt = "2026-08-25T17:00:00Z",
                    version = 0L,
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                queue.claimOperation(SCOPE, opId)
                queue.recordV2Conflict(
                    SCOPE,
                    SyncConflictEntity(
                        syncScopeKey = SCOPE,
                        entityTypeCode = "TRANSACTION",
                        entityId = tx.id,
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(dto),
                        remotePayloadJson = json.encodeToString(dto.copy(version = 1L)),
                        detectedAtEpochMillis = 1000L,
                    )
                )
            }

            val recoveredCount = service.recoverAllPendingConflicts(SCOPE)
            assertEquals(3, recoveredCount)
            assertEquals(0, db.syncStateDao().getAllConflicts(SCOPE).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun recover_all_pending_conflicts_does_not_recover_broken_installment_group_individually() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            // 1. Setup standalone category conflict (equivalent)
            val cat = categoryEntity("cat-1", "Market")
            val catOpId = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE,
                entity = cat,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE, catOpId)
            queue.recordV2Conflict(
                SCOPE,
                SyncConflictEntity(
                    syncScopeKey = SCOPE,
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-1",
                    operationId = catOpId,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                    remotePayloadJson = """{"id":"cat-1","user_id":"usr-1","name":"Market","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                    detectedAtEpochMillis = 1000L,
                )
            )

            // 2. Setup 2-installment group where 1st is equivalent but 2nd is NOT equivalent (differing amount)
            val grpId = "grp-broken"
            val txIds = listOf("tx-brk-1", "tx-brk-2")
            val txOpIds = mutableListOf<String>()

            for (i in 1..2) {
                val tx = transactionEntity("tx-brk-$i", 10000L, i, 2, grpId)
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-1",
                    workspaceId = null,
                    amountMinor = 10000L,
                    currency = "TRY",
                    type = "EXPENSE",
                    categoryId = "cat-1",
                    description = "Taksitli işlem",
                    paymentMethod = "CREDIT_CARD",
                    transactionDate = "2026-08-25",
                    receiptPath = null,
                    installmentNumber = i,
                    totalInstallments = 2,
                    installmentGroupId = grpId,
                    deletedAt = null,
                    createdAt = "2026-08-25T17:00:00Z",
                    updatedAt = "2026-08-25T17:00:00Z",
                    version = 0L,
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                txOpIds.add(opId)
                queue.claimOperation(SCOPE, opId)

                // 2nd installment has different remote amount
                val remoteDto = if (i == 2) dto.copy(amountMinor = 99999L, version = 1L) else dto.copy(version = 1L)
                queue.recordV2Conflict(
                    SCOPE,
                    SyncConflictEntity(
                        syncScopeKey = SCOPE,
                        entityTypeCode = "TRANSACTION",
                        entityId = tx.id,
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(dto),
                        remotePayloadJson = json.encodeToString(remoteDto),
                        detectedAtEpochMillis = 1000L,
                    )
                )
            }

            // Run recoverAllPendingConflicts
            val recoveredCount = service.recoverAllPendingConflicts(SCOPE)

            // Only category recovered (1 item), installment group failed all-or-nothing and was NOT partially recovered
            assertEquals(1, recoveredCount)
            assertNull(db.syncStateDao().getConflict(SCOPE, "CATEGORY", "cat-1"))

            // Both installment conflicts still present
            for (id in txIds) {
                assertNotNull(db.syncStateDao().getConflict(SCOPE, "TRANSACTION", id))
            }
            for (opId in txOpIds) {
                assertNotNull(db.syncOperationDao().getById(SCOPE, opId))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun b_recovery_does_not_alter_a_conflict_outbox_or_entity_metadata() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            // Setup A's conflict & outbox
            val catA = categoryEntity("cat-a", "Market A").copy(ownerId = "usr-a", scopeKey = "usr-a")
            val opIdA = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE_A,
                entity = catA,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-a","user_id":"usr-a","name":"Market A","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE_A, opIdA)
            queue.recordV2Conflict(
                SCOPE_A,
                SyncConflictEntity(
                    syncScopeKey = SCOPE_A,
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-a",
                    operationId = opIdA,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = """{"id":"cat-a","user_id":"usr-a","name":"Market A","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z"}""",
                    remotePayloadJson = """{"id":"cat-a","user_id":"usr-a","name":"Market A","type":"expense","color":"#EF4444","created_at":"2026-08-25T17:00:00Z","version":1}""",
                    detectedAtEpochMillis = 1000L,
                ),
            )

            // Setup B's equivalent conflict & outbox
            val catB = categoryEntity("cat-b", "Market B").copy(ownerId = "usr-b", scopeKey = "usr-b")
            val opIdB = queue.enqueueCategoryV2(
                syncScopeKey = SCOPE_B,
                entity = catB,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"cat-b","user_id":"usr-b","name":"Market B","type":"expense","color":"#3B82F6","created_at":"2026-08-25T17:00:00Z"}""",
            )
            queue.claimOperation(SCOPE_B, opIdB)
            queue.recordV2Conflict(
                SCOPE_B,
                SyncConflictEntity(
                    syncScopeKey = SCOPE_B,
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-b",
                    operationId = opIdB,
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = """{"id":"cat-b","user_id":"usr-b","name":"Market B","type":"expense","color":"#3B82F6","created_at":"2026-08-25T17:00:00Z"}""",
                    remotePayloadJson = """{"id":"cat-b","user_id":"usr-b","name":"Market B","type":"expense","color":"#3B82F6","created_at":"2026-08-25T17:00:00Z","version":1}""",
                    detectedAtEpochMillis = 1000L,
                ),
            )

            // Run B recovery only
            val bRecovered = service.recoverAllPendingConflicts(SCOPE_B)
            assertEquals(1, bRecovered)

            // B is resolved
            assertNull(db.syncStateDao().getConflict(SCOPE_B, "CATEGORY", "cat-b"))
            assertNull(db.syncOperationDao().getById(SCOPE_B, opIdB))

            // A is completely untouched: conflict exists, op exists in CONFLICT status, entity sync status is CONFLICT
            assertNotNull(db.syncStateDao().getConflict(SCOPE_A, "CATEGORY", "cat-a"))
            val aOp = db.syncOperationDao().getById(SCOPE_A, opIdA)
            assertNotNull(aOp)
            assertEquals("CONFLICT", aOp.statusCode)
            val aCat = db.categoryDao().getByIdAndOwner("cat-a", "usr-a")
            assertNotNull(aCat)
            assertEquals("CONFLICT", aCat.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun a_installment_group_recovery_does_not_affect_b_conflicts() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            db.categoryDao().upsert(categoryEntity("cat-1", "Elektronik"))

            // Group A (2 installments)
            val grpA = "grp-a"
            for (i in 1..2) {
                val tx = transactionEntity("tx-a-$i", 10000L, i, 2, grpA)
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-1",
                    amountMinor = 10000L,
                    currency = "TRY",
                    type = "EXPENSE",
                    categoryId = "cat-1",
                    paymentMethod = "CREDIT_CARD",
                    transactionDate = "2026-08-25",
                    installmentNumber = i,
                    totalInstallments = 2,
                    installmentGroupId = grpA,
                    createdAt = "2026-08-25T17:00:00Z",
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE_A,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                queue.claimOperation(SCOPE_A, opId)
                queue.recordV2Conflict(
                    SCOPE_A,
                    SyncConflictEntity(
                        syncScopeKey = SCOPE_A,
                        entityTypeCode = "TRANSACTION",
                        entityId = tx.id,
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(dto),
                        remotePayloadJson = json.encodeToString(dto.copy(version = 1L)),
                        detectedAtEpochMillis = 1000L,
                    ),
                )
            }

            // Group B (2 installments)
            val grpB = "grp-b"
            val bOpIds = mutableListOf<String>()
            for (i in 1..2) {
                val tx = transactionEntity("tx-b-$i", 20000L, i, 2, grpB).copy(ownerId = "usr-b")
                val dto = TransactionDto(
                    id = tx.id,
                    userId = "usr-b",
                    amountMinor = 20000L,
                    currency = "TRY",
                    type = "EXPENSE",
                    categoryId = "cat-1",
                    paymentMethod = "CREDIT_CARD",
                    transactionDate = "2026-08-25",
                    installmentNumber = i,
                    totalInstallments = 2,
                    installmentGroupId = grpB,
                    createdAt = "2026-08-25T17:00:00Z",
                )
                val opId = queue.enqueueTransactionV2(
                    syncScopeKey = SCOPE_B,
                    entity = tx,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = json.encodeToString(dto),
                )
                bOpIds.add(opId)
                queue.claimOperation(SCOPE_B, opId)
                queue.recordV2Conflict(
                    SCOPE_B,
                    SyncConflictEntity(
                        syncScopeKey = SCOPE_B,
                        entityTypeCode = "TRANSACTION",
                        entityId = tx.id,
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(dto),
                        remotePayloadJson = json.encodeToString(dto.copy(version = 1L)),
                        detectedAtEpochMillis = 1000L,
                    ),
                )
            }

            // Recover A's installment group
            val recoveredA = service.recoverInstallmentGroup(SCOPE_A, grpA)
            assertTrue(recoveredA)

            // Verify A's group is resolved
            assertNull(db.syncStateDao().getConflict(SCOPE_A, "TRANSACTION", "tx-a-1"))
            assertNull(db.syncStateDao().getConflict(SCOPE_A, "TRANSACTION", "tx-a-2"))

            // Verify B's conflicts and outbox are untouched
            assertNotNull(db.syncStateDao().getConflict(SCOPE_B, "TRANSACTION", "tx-b-1"))
            assertNotNull(db.syncStateDao().getConflict(SCOPE_B, "TRANSACTION", "tx-b-2"))
            for (opId in bOpIds) {
                assertNotNull(db.syncOperationDao().getById(SCOPE_B, opId))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun legacy_unresolved_conflicts_are_invisible_and_never_recovered_by_user_scope_recovery() = runTest {
        val db = inMemoryDatabase()
        try {
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            // Insert legacy conflict
            db.syncStateDao().upsertConflict(
                SyncConflictEntity(
                    syncScopeKey = SyncScopeKey.UNRESOLVED_RAW,
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-legacy-unresolved",
                    operationId = "op-legacy",
                    localVersion = 0L,
                    remoteVersion = 1L,
                    localPayloadJson = """{"id":"cat-legacy-unresolved","name":"Legacy"}""",
                    remotePayloadJson = """{"id":"cat-legacy-unresolved","name":"Legacy","version":1}""",
                    detectedAtEpochMillis = 1000L,
                ),
            )

            // Attempt recoverAllPendingConflicts under SCOPE_A
            val recoveredA = service.recoverAllPendingConflicts(SCOPE_A)
            assertEquals(0, recoveredA)

            // Attempt recoverSingleEquivalentCreate under SCOPE_A
            val recoveredSingleA = service.recoverSingleEquivalentCreate(SCOPE_A, "CATEGORY", "cat-legacy-unresolved")
            assertFalse(recoveredSingleA)

            // The legacy conflict is still in db under UNRESOLVED_RAW
            assertNotNull(db.syncStateDao().getConflict(SyncScopeKey.UNRESOLVED_RAW, "cat-legacy-unresolved"))
        } finally {
            db.close()
        }
    }

    @Test
    fun recovery_entry_points_reject_unresolved_legacy_scope_fail_closed() = runTest {
        val db = inMemoryDatabase()
        try {
            val service = ConflictRecoveryService(
                syncStateDao = db.syncStateDao(),
                syncOperationDao = db.syncOperationDao(),
                localMutationDao = db.localMutationDao(),
                nowEpochMillisProvider = { 1000L },
            )

            assertFailsWith<IllegalArgumentException> {
                service.recoverAllPendingConflicts(SyncScopeKey.UNRESOLVED_RAW)
            }

            assertFailsWith<IllegalArgumentException> {
                service.recoverSingleEquivalentCreate(SyncScopeKey.UNRESOLVED_RAW, "CATEGORY", "cat-1")
            }

            assertFailsWith<IllegalArgumentException> {
                service.recoverInstallmentGroup(SyncScopeKey.UNRESOLVED_RAW, "grp-1")
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
        scopeKey = "usr-1",
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

    private fun transactionEntity(
        id: String,
        amountMinor: Long,
        installmentNumber: Int?,
        totalInstallments: Int?,
        installmentGroupId: String?,
    ) = TransactionEntity(
        id = id,
        ownerId = "usr-1",
        workspaceId = null,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        typeCode = "EXPENSE",
        categoryId = "cat-1",
        description = "Taksitli işlem",
        searchText = "taksitli islem",
        paymentMethodCode = "CREDIT_CARD",
        transactionDate = "2026-08-25",
        receiptPath = null,
        installmentNumber = installmentNumber,
        totalInstallments = totalInstallments,
        installmentGroupId = installmentGroupId,
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
