package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
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
class GoalDebtLocalMutationDaoTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }


    private fun createQueue(db: FeniqoDatabase): OfflineWriteQueue {
        return OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
        )
    }

    // --- Helpers ---
    private fun sampleGoalEntity(
        id: String = "goal-1",
        ownerId: String = "user-1",
        name: String = "Tatil Fonu",
        targetAmountMinor: Long = 100_000L,
        currentAmountMinor: Long = 20_000L,
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
    ): GoalEntity = GoalEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        name = name,
        targetAmountMinor = targetAmountMinor,
        currentAmountMinor = currentAmountMinor,
        currencyCode = "TRY",
        targetDate = "2026-12-31",
        colorHex = "#2E7D32",
        iconKey = "savings",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleContributionEntity(
        id: String = "contrib-1",
        goalId: String = "goal-1",
        amountMinor: Long = 10_000L,
        direction: String = "ADD",
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
    ): GoalContributionEntity = GoalContributionEntity(
        id = id,
        goalId = goalId,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        directionCode = direction,
        occurredOn = "2026-09-01",
        note = "Maaş katkısı",
        createdAtEpochMillis = 2000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 2000L,
            localUpdatedAtEpochMillis = 2000L,
            deletedAtEpochMillis = null,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleDebtEntity(
        id: String = "debt-1",
        ownerId: String = "user-1",
        title: String = "Kredi Kartı",
        amountMinor: Long = 50_000L,
        status: String = "OPEN",
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
    ): DebtEntity = DebtEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        title = title,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        typeCode = "DEBT",
        dueDate = "2026-10-15",
        statusCode = status,
        description = "Banka borcu",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleDebtPaymentEntity(
        id: String = "pay-1",
        debtId: String = "debt-1",
        amountMinor: Long = 50_000L,
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
    ): DebtPaymentEntity = DebtPaymentEntity(
        id = id,
        debtId = debtId,
        amountMinor = amountMinor,
        currencyCode = "TRY",
        paidOn = "2026-09-02",
        createdAtEpochMillis = 2000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 2000L,
            localUpdatedAtEpochMillis = 2000L,
            deletedAtEpochMillis = null,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )




    // --- Tests ---

    @Test
    fun goal_v2_ack_without_successor_applies_remote_record_and_removes_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            val goal = sampleGoalEntity(id = "g-1", currentAmountMinor = 20_000L)
            val opId = queue.enqueueGoalV2(
                entity = goal,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"g-1","user_id":"user-1","workspace_id":null,"name":"Tatil Fonu","target_amount_minor":100000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
            )

            queue.claimOperation(opId)

            val remoteDto = GoalDto(
                id = "g-1",
                userId = "user-1",
                workspaceId = null,
                name = "Tatil Fonu",
                targetAmountMinor = 100_000L,
                currentAmountMinor = 20_000L,
                currency = "TRY",
                targetDate = "2026-12-31",
                colorHex = "#2E7D32",
                iconKey = "savings",
                createdAt = "2026-09-01T10:00:00Z",
                updatedAt = "2026-09-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackSuccess = db.localMutationDao().ackV2Execution(
                operationId = opId,
                result = OutboxExecutionResult.GoalApplied(remoteDto),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            assertNull(db.syncOperationDao().getById(opId))
            val stored = db.goalDao().getById("g-1")
            assertNotNull(stored)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
            assertEquals("SYNCED", stored.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun goal_v2_parent_ack_does_not_overwrite_newer_child_contribution_aggregate() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            // Goal created locally (or updated) -> in outbox
            val initialGoal = sampleGoalEntity(id = "g-1", currentAmountMinor = 20_000L)
            val goalOpId = queue.enqueueGoalV2(
                entity = initialGoal,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"g-1"}""",
            )

            // Claim goal create -> IN_FLIGHT
            val claimedGoalOp = queue.claimOperation(goalOpId)
            assertNotNull(claimedGoalOp)
            assertEquals("IN_FLIGHT", claimedGoalOp.statusCode)

            // Child contribution added locally while goal CREATE is in-flight!
            // Goal's current amount becomes 30_000L
            val contribEntity = sampleContributionEntity(id = "c-1", goalId = "g-1", amountMinor = 10_000L)
            val updatedGoalEntity = initialGoal.copy(
                currentAmountMinor = 30_000L,
                sync = initialGoal.sync.toPendingUpdate(2500L),
            )
            val contribOpId = queue.enqueueGoalContributionV2(
                entity = contribEntity,
                updatedGoal = updatedGoalEntity,
                payloadJson = """{"id":"c-1"}""",
            )

            // Goal CREATE ACK arrives from remote with stale currentAmountMinor = 20_000L and version = 1L
            val remoteGoalDto = GoalDto(
                id = "g-1",
                userId = "user-1",
                workspaceId = null,
                name = "Tatil Fonu",
                targetAmountMinor = 100_000L,
                currentAmountMinor = 20_000L, // STALE remote snapshot!
                currency = "TRY",
                targetDate = "2026-12-31",
                colorHex = "#2E7D32",
                iconKey = "savings",
                createdAt = "2026-09-01T10:00:00Z",
                updatedAt = "2026-09-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackSuccess = db.localMutationDao().ackV2Execution(
                operationId = goalOpId,
                result = OutboxExecutionResult.GoalApplied(remoteGoalDto),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            // Assert: Parent version advanced to 1L, but currentAmountMinor is NOT overwritten back to 20_000L!
            val storedGoal = db.goalDao().getById("g-1")
            assertNotNull(storedGoal)
            assertEquals(30_000L, storedGoal.currentAmountMinor) // Preserved local aggregate!
            assertEquals(1L, storedGoal.sync.version)
            assertEquals(1L, storedGoal.sync.baseVersion)
            assertEquals("PENDING_CREATE", storedGoal.sync.syncStatus) // Still pending because child operation is pending!

            // Successor child contribution operation should now have predecessor cleared and parent base_version resolved
            val storedContribOp = db.syncOperationDao().getById(contribOpId)
            assertNotNull(storedContribOp)
            assertFalse(storedContribOp.isBlocked)
            assertEquals(1L, storedContribOp.baseVersion) // Rebased to parent's resolved version 1L!
        } finally {
            db.close()
        }
    }

    @Test
    fun goal_contribution_ack_atomically_updates_child_and_parent_aggregate() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            // Goal already SYNCED at version 1
            val goal = sampleGoalEntity(id = "g-1", currentAmountMinor = 20_000L, syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
            db.goalDao().upsert(goal)

            // Add contribution
            val contribEntity = sampleContributionEntity(id = "c-1", goalId = "g-1", amountMinor = 10_000L)
            val updatedGoalEntity = goal.copy(
                currentAmountMinor = 30_000L,
                sync = goal.sync.toPendingUpdate(2500L),
            )
            val contribOpId = queue.enqueueGoalContributionV2(
                entity = contribEntity,
                updatedGoal = updatedGoalEntity,
                payloadJson = """{"id":"c-1"}""",
            )

            queue.claimOperation(contribOpId)

            val remoteAggregateResponse = GoalContributionSyncRecordDto(
                contribution = GoalContributionDto(
                    id = "c-1",
                    goalId = "g-1",
                    amountMinor = 10_000L,
                    currency = "TRY",
                    direction = "ADD",
                    occurredOn = "2026-09-01",
                    note = "Maaş katkısı",
                    createdAt = "2026-09-01T10:00:00Z",
                    deletedAt = null,
                    version = 1L,
                ),
                goal = GoalDto(
                    id = "g-1",
                    userId = "user-1",
                    workspaceId = null,
                    name = "Tatil Fonu",
                    targetAmountMinor = 100_000L,
                    currentAmountMinor = 30_000L,
                    currency = "TRY",
                    targetDate = "2026-12-31",
                    colorHex = "#2E7D32",
                    iconKey = "savings",
                    createdAt = "2026-09-01T10:00:00Z",
                    updatedAt = "2026-09-01T10:00:02Z",
                    deletedAt = null,
                    version = 2L,
                ),
            )

            val ackSuccess = db.localMutationDao().ackV2Execution(
                operationId = contribOpId,
                result = OutboxExecutionResult.GoalContributionApplied(remoteAggregateResponse),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            // Child and Parent are both SYNCED
            assertNull(db.syncOperationDao().getById(contribOpId))
            val storedContrib = db.goalDao().getContributionById("c-1")
            assertNotNull(storedContrib)
            assertEquals("SYNCED", storedContrib.sync.syncStatus)
            assertEquals(1L, storedContrib.sync.version)

            val storedGoal = db.goalDao().getById("g-1")
            assertNotNull(storedGoal)
            assertEquals(30_000L, storedGoal.currentAmountMinor)
            assertEquals(2L, storedGoal.sync.version)
            assertEquals("SYNCED", storedGoal.sync.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun debt_and_payment_settled_race_condition_preserves_local_settled_status() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            // Debt is OPEN with 50_000L
            val debt = sampleDebtEntity(id = "d-1", amountMinor = 50_000L, status = "OPEN")
            val debtOpId = queue.enqueueDebtV2(
                entity = debt,
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"d-1"}""",
            )

            // Claim debt CREATE -> IN_FLIGHT
            queue.claimOperation(debtOpId)

            // Payment added locally for full amount 50_000L -> Debt status becomes SETTLED
            val payEntity = sampleDebtPaymentEntity(id = "p-1", debtId = "d-1", amountMinor = 50_000L)
            val settledDebtEntity = debt.copy(
                statusCode = "SETTLED",
                sync = debt.sync.toPendingUpdate(2500L),
            )
            val payOpId = queue.enqueueDebtPaymentV2(
                entity = payEntity,
                updatedDebt = settledDebtEntity,
                payloadJson = """{"id":"p-1"}""",
            )

            // Debt CREATE ACK arrives with OPEN status and version = 1L
            val remoteDebtDto = DebtDto(
                id = "d-1",
                userId = "user-1",
                workspaceId = null,
                title = "Kredi Kartı",
                amountMinor = 50_000L,
                currency = "TRY",
                type = "DEBT",
                dueDate = "2026-10-15",
                status = "OPEN", // Stale remote status!
                description = "Banka borcu",
                createdAt = "2026-09-01T10:00:00Z",
                updatedAt = "2026-09-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackSuccess = db.localMutationDao().ackV2Execution(
                operationId = debtOpId,
                result = OutboxExecutionResult.DebtApplied(remoteDebtDto),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            // Assert: Debt status remains SETTLED (not overwritten with OPEN)!
            val storedDebt = db.debtDao().getById("d-1")
            assertNotNull(storedDebt)
            assertEquals("SETTLED", storedDebt.statusCode)
            assertEquals(1L, storedDebt.sync.version)
            assertEquals("PENDING_CREATE", storedDebt.sync.syncStatus)

            // Payment operation is unblocked and has baseVersion = 1L
            val storedPayOp = db.syncOperationDao().getById(payOpId)
            assertNotNull(storedPayOp)
            assertFalse(storedPayOp.isBlocked)
            assertEquals(1L, storedPayOp.baseVersion)

        } finally {
            db.close()
        }
    }

    @Test
    fun parent_delete_tombstones_local_children_and_chains_predecessor() = runTest {
        val db = inMemoryDatabase()
        try {
            val queue = createQueue(db)
            // Goal SYNCED at v1 with child contrib SYNCED at v1
            val goal = sampleGoalEntity(id = "g-1", syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
            db.goalDao().upsert(goal)
            val contrib = sampleContributionEntity(id = "c-1", goalId = "g-1", syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
            db.goalDao().upsertContribution(contrib)

            // Soft-delete goal
            val deletedGoal = goal.copy(
                sync = goal.sync.copy(
                    syncStatus = "PENDING_DELETE",
                    deletedAtEpochMillis = 5000L,
                    updatedAtEpochMillis = 5000L,
                ),
            )
            val deleteOpId = queue.enqueueGoalV2(
                entity = deletedGoal,
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"g-1"}""",
            )

            // Live goal and child are tombstoned
            assertNull(db.goalDao().getById("g-1"))
            assertNull(db.goalDao().getContributionById("c-1"))

            // Verify child raw entity in DB has deletedAtEpochMillis set
            val rawContrib = db.goalDao().getAnyContributionById("c-1")
            assertNotNull(rawContrib)
            assertNotNull(rawContrib.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }
}
