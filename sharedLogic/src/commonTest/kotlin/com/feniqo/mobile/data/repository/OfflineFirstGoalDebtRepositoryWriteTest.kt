package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.DebtDao
import com.feniqo.mobile.data.local.dao.GoalDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.V2EnqueueDecision
import com.feniqo.mobile.data.local.dao.V2EnqueueResult
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfflineFirstGoalDebtRepositoryWriteTest {

    private class FakeAuthRepository(session: AuthSession? = null) : AuthRepository {
        val sessionFlow = MutableStateFlow(session)
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeGoalDao : GoalDao {
        val goals = mutableMapOf<String, GoalEntity>()
        val contributions = mutableMapOf<String, GoalContributionEntity>()

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<GoalEntity>> = flowOf(goals.values.toList())
        override fun observeById(id: String): Flow<GoalEntity?> = flowOf(goals[id])
        override suspend fun getById(id: String): GoalEntity? = goals[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
        override suspend fun getAnyById(id: String): GoalEntity? = goals[id]
        override suspend fun upsert(goal: GoalEntity) { goals[goal.id] = goal }
        override suspend fun upsertAll(goals: List<GoalEntity>) { goals.forEach { this.goals[it.id] = it } }

        override fun observeContributionsByGoalId(goalId: String): Flow<List<GoalContributionEntity>> =
            flowOf(contributions.values.filter { it.goalId == goalId && it.sync.deletedAtEpochMillis == null })
        override suspend fun getContributionsByGoalId(goalId: String): List<GoalContributionEntity> =
            contributions.values.filter { it.goalId == goalId && it.sync.deletedAtEpochMillis == null }
        override suspend fun getContributionById(id: String): GoalContributionEntity? =
            contributions[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
        override suspend fun getAnyContributionById(id: String): GoalContributionEntity? = contributions[id]
        override suspend fun upsertContribution(contribution: GoalContributionEntity) { contributions[contribution.id] = contribution }
        override suspend fun upsertAllContributions(contributions: List<GoalContributionEntity>) {
            contributions.forEach { this.contributions[it.id] = it }
        }
    }

    private class FakeDebtDao : DebtDao {
        val debts = mutableMapOf<String, DebtEntity>()
        val payments = mutableMapOf<String, DebtPaymentEntity>()

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<DebtEntity>> = flowOf(debts.values.toList())
        override fun observeAllByType(ownerId: String, workspaceId: String?, typeCode: String): Flow<List<DebtEntity>> =
            flowOf(debts.values.filter { it.typeCode == typeCode })
        override fun observeById(id: String): Flow<DebtEntity?> = flowOf(debts[id])
        override suspend fun getById(id: String): DebtEntity? = debts[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
        override suspend fun getAnyById(id: String): DebtEntity? = debts[id]
        override suspend fun upsert(debt: DebtEntity) { debts[debt.id] = debt }
        override suspend fun upsertAll(debts: List<DebtEntity>) { debts.forEach { this.debts[it.id] = it } }

        override fun observePaymentsByDebtId(debtId: String): Flow<List<DebtPaymentEntity>> =
            flowOf(payments.values.filter { it.debtId == debtId && it.sync.deletedAtEpochMillis == null })
        override suspend fun getPaymentsByDebtId(debtId: String): List<DebtPaymentEntity> =
            payments.values.filter { it.debtId == debtId && it.sync.deletedAtEpochMillis == null }
        override suspend fun getPaymentById(id: String): DebtPaymentEntity? =
            payments[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
        override suspend fun getAnyPaymentById(id: String): DebtPaymentEntity? = payments[id]
        override suspend fun upsertPayment(payment: DebtPaymentEntity) { payments[payment.id] = payment }
        override suspend fun upsertAllPayments(payments: List<DebtPaymentEntity>) {
            payments.forEach { this.payments[it.id] = it }
        }
    }

    private class FakeLocalMutationDao : LocalMutationDao {
        var lastGoal: GoalEntity? = null
        var lastGoalType: OutboxOperationType? = null
        var lastGoalPayloadJson: String? = null

        var lastContribution: GoalContributionEntity? = null
        var lastUpdatedGoal: GoalEntity? = null
        var lastContributionPayloadJson: String? = null

        var lastDebt: DebtEntity? = null
        var lastDebtType: OutboxOperationType? = null
        var lastDebtPayloadJson: String? = null

        var lastPayment: DebtPaymentEntity? = null
        var lastUpdatedDebt: DebtEntity? = null
        var lastPaymentPayloadJson: String? = null

        var shouldThrow: Throwable? = null

        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRow(entity: CategoryEntity) = Unit
        override suspend fun upsertBudgetRow(entity: BudgetEntity) = Unit
        override suspend fun upsertTransactionRow(entity: TransactionEntity) = Unit
        override suspend fun insertTransactionRow(entity: TransactionEntity) = Unit
        override suspend fun upsertTagRows(entities: List<TagEntity>) = Unit
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) = Unit
        override suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity) = Unit
        override suspend fun upsertSubscriptionRow(entity: SubscriptionEntity) = Unit
        override suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity) = Unit
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): RecurringTransactionEntity? = null
        override suspend fun advanceRecurringLastGeneratedDate(recurringTransactionId: String, expectedPreviousLastGeneratedDate: String?, newDueDate: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
        override suspend fun deleteProfileRow(id: String): Int = 0
        override suspend fun deleteCategoryRow(id: String): Int = 0
        override suspend fun deleteBudgetRow(id: String): Int = 0
        override suspend fun deleteTransactionRow(id: String): Int = 0
        override suspend fun deleteOutboxRow(operationId: String): Int = 0
        override suspend fun getOutboxById(operationId: String): SyncOperationEntity? = null
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
        override suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun deleteRecurringTransactionRow(id: String): Int = 0
        override suspend fun deleteSubscriptionRow(id: String): Int = 0
        override suspend fun markCategorySyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markBudgetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markRecurringTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markSubscriptionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteWorkspaceRow(id: String): Int = 0
        override suspend fun deleteWorkspaceMemberRows(workspaceId: String): Int = 0
        override suspend fun rebaseWorkspaceVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markWorkspaceSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun upsertWorkspaceMemberRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity) = Unit
        override suspend fun upsertWorkspaceInvitationRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity) = Unit
        override suspend fun deleteWorkspaceInvitationRow(id: String): Int = 0
        override suspend fun getWorkspaceInvitationById(id: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceInvitationByTokenHash(tokenHash: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceMember(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun rebaseWorkspaceMemberVersion(workspaceId: String, userId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseWorkspaceInvitationVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markWorkspaceMemberSyncedIfDeleted(workspaceId: String, userId: String, nowEpochMillis: Long): Int = 0
        override suspend fun upsertGoalRow(entity: GoalEntity) = Unit
        override suspend fun upsertGoalContributionRow(entity: GoalContributionEntity) = Unit
        override suspend fun upsertDebtRow(entity: DebtEntity) = Unit
        override suspend fun upsertDebtPaymentRow(entity: DebtPaymentEntity) = Unit
        override suspend fun deleteGoalRow(id: String): Int = 0
        override suspend fun deleteGoalContributionRow(id: String): Int = 0
        override suspend fun deleteDebtRow(id: String): Int = 0
        override suspend fun deleteDebtPaymentRow(id: String): Int = 0
        override suspend fun getGoalById(id: String): GoalEntity? = null
        override suspend fun getDebtById(id: String): DebtEntity? = null
        override suspend fun getGoalContributionById(id: String): GoalContributionEntity? = null
        override suspend fun getDebtPaymentById(id: String): DebtPaymentEntity? = null
        override suspend fun getActiveGoalContributions(goalId: String): List<GoalContributionEntity> = emptyList()
        override suspend fun getActiveDebtPayments(debtId: String): List<DebtPaymentEntity> = emptyList()
        override suspend fun rebaseGoalVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseGoalContributionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseDebtVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseDebtPaymentVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markGoalSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markDebtSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun tombstoneGoalContributionsForDeletedGoal(goalId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
        override suspend fun tombstoneDebtPaymentsForDeletedDebt(debtId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
        override suspend fun getActiveGoalAggregateTailCandidates(goalId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveDebtAggregateTailCandidates(debtId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun countPendingGoalAggregateOperations(goalId: String, operationId: String): Int = 0
        override suspend fun countPendingDebtAggregateOperations(debtId: String, operationId: String): Int = 0
        override suspend fun setGoalSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setGoalContributionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setDebtPaymentSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun upsertConflictRow(entity: SyncConflictEntity) = Unit
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1

        override suspend fun mutateGoalV2(
            entity: GoalEntity,
            type: OutboxOperationType,
            payloadJson: String,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): V2EnqueueResult {
            shouldThrow?.let { throw it }
            lastGoal = entity
            lastGoalType = type
            lastGoalPayloadJson = payloadJson
            return V2EnqueueResult("op-goal", V2EnqueueDecision.INSERTED)
        }

        override suspend fun mutateGoalContributionV2(
            entity: GoalContributionEntity,
            updatedGoal: GoalEntity,
            payloadJson: String,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): V2EnqueueResult {
            shouldThrow?.let { throw it }
            lastContribution = entity
            lastUpdatedGoal = updatedGoal
            lastContributionPayloadJson = payloadJson
            return V2EnqueueResult("op-contrib", V2EnqueueDecision.INSERTED)
        }

        override suspend fun mutateDebtV2(
            entity: DebtEntity,
            type: OutboxOperationType,
            payloadJson: String,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): V2EnqueueResult {
            shouldThrow?.let { throw it }
            lastDebt = entity
            lastDebtType = type
            lastDebtPayloadJson = payloadJson
            return V2EnqueueResult("op-debt", V2EnqueueDecision.INSERTED)
        }

        override suspend fun mutateDebtPaymentV2(
            entity: DebtPaymentEntity,
            updatedDebt: DebtEntity,
            payloadJson: String,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): V2EnqueueResult {
            shouldThrow?.let { throw it }
            lastPayment = entity
            lastUpdatedDebt = updatedDebt
            lastPaymentPayloadJson = payloadJson
            return V2EnqueueResult("op-pay", V2EnqueueDecision.INSERTED)
        }
    }

    private class FakeSyncOperationDao : SyncOperationDao {
        override fun observePendingCount(): Flow<Int> = flowOf(0)
        override fun observeFailedCount(): Flow<Int> = flowOf(0)
        override suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int) = emptyList<SyncOperationEntity>()
        override suspend fun getById(operationId: String): SyncOperationEntity? = null
        override suspend fun insert(operation: SyncOperationEntity) {}
        override suspend fun claimOperation(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun markFailed(operationId: String, lastError: String, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
        override suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
        override suspend fun recoverStaleInFlight(staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
        override suspend fun retryAllFailed(nowEpochMillis: Long): Int = 0
        override suspend fun deleteCompleted(operationId: String): Int = 1
        override suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 1
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 1
    }

    private val defaultSync = SyncMetadata(
        syncStatus = "SYNCED",
        updatedAtEpochMillis = 1000L,
        localUpdatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private val testSession = AuthSession(
        userId = EntityId("user-1"),
        email = "user@example.com",
        expiresAt = Instant.fromEpochMilliseconds(2000L),
    )

    @Test
    fun goal_create_enqueues_to_offline_write_queue() = runTest {
        val auth = FakeAuthRepository(testSession)
        val dao = FakeGoalDao()
        val mutationDao = FakeLocalMutationDao()
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val repo = OfflineFirstGoalRepository(
            authRepository = auth,
            goalDao = dao,
            offlineWriteQueue = queue,
            entityIdGenerator = EntityIdGenerator { EntityId("g-generated") },
            nowEpochMillisProvider = { 1000L },
        )

        val result = repo.create(
            CreateGoalCommand(
                name = "Yeni Araba",
                targetAmount = Money(300_000, Currency.TRY),
                initialAmount = Money(30_000, Currency.TRY),
                targetDate = LocalDate(2027, 12, 31),
                color = CategoryColor("#2E7D32"),
                icon = CategoryIcon("car"),
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(EntityId("g-generated"), result.value)
        assertEquals("g-generated", mutationDao.lastGoal?.id)
        assertEquals(OutboxOperationType.CREATE, mutationDao.lastGoalType)
        assertNotNull(mutationDao.lastGoalPayloadJson)
    }

    @Test
    fun goal_add_contribution_enqueues_child_and_updates_parent_aggregate() = runTest {
        val auth = FakeAuthRepository(testSession)
        val dao = FakeGoalDao()
        dao.goals["g-1"] = GoalEntity(
            id = "g-1",
            ownerId = "user-1",
            workspaceId = null,
            name = "Yeni Araba",
            targetAmountMinor = 300_000L,
            currentAmountMinor = 30_000L,
            currencyCode = "TRY",
            targetDate = "2027-12-31",
            colorHex = "#2E7D32",
            iconKey = "car",
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val mutationDao = FakeLocalMutationDao()
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val repo = OfflineFirstGoalRepository(
            authRepository = auth,
            goalDao = dao,
            offlineWriteQueue = queue,
            entityIdGenerator = EntityIdGenerator { EntityId("c-generated") },
            nowEpochMillisProvider = { 2000L },
        )

        val result = repo.addContribution(
            AddGoalContributionCommand(
                goalId = EntityId("g-1"),
                amount = Money(20_000, Currency.TRY),
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 2),
                note = "Bonus",
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(EntityId("c-generated"), result.value)
        assertEquals("c-generated", mutationDao.lastContribution?.id)
        assertEquals(50_000L, mutationDao.lastUpdatedGoal?.currentAmountMinor)
    }

    @Test
    fun debt_create_and_payment_settle_enqueues_to_offline_write_queue() = runTest {
        val auth = FakeAuthRepository(testSession)
        val dao = FakeDebtDao()
        val mutationDao = FakeLocalMutationDao()
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val repo = OfflineFirstDebtRepository(
            authRepository = auth,
            debtDao = dao,
            offlineWriteQueue = queue,
            entityIdGenerator = EntityIdGenerator { EntityId("d-generated") },
            nowEpochMillisProvider = { 1000L },
        )

        val createResult = repo.create(
            CreateDebtCommand(
                title = "Kredi",
                amount = Money(100_000, Currency.TRY),
                type = DebtType.DEBT,
                dueDate = LocalDate(2027, 1, 1),
                description = "İhtiyaç kredisi",
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals(EntityId("d-generated"), createResult.value)
        assertEquals(OutboxOperationType.CREATE, mutationDao.lastDebtType)

        // Pre-insert created debt into fake dao
        dao.debts["d-generated"] = mutationDao.lastDebt!!

        // Add payment for remaining balance (100_000L) -> Should settle debt
        val payRepo = OfflineFirstDebtRepository(
            authRepository = auth,
            debtDao = dao,
            offlineWriteQueue = queue,
            entityIdGenerator = EntityIdGenerator { EntityId("p-generated") },
            nowEpochMillisProvider = { 2000L },
        )

        val payResult = payRepo.addPayment(
            AddDebtPaymentCommand(
                debtId = EntityId("d-generated"),
                amount = Money(100_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 2),
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(payResult)
        assertEquals(EntityId("p-generated"), payResult.value)
        assertEquals("SETTLED", mutationDao.lastUpdatedDebt?.statusCode)
    }

    @Test
    fun repository_writes_do_not_swallow_cancellation_exception() = runTest {
        val auth = FakeAuthRepository(testSession)
        val dao = FakeGoalDao()
        val mutationDao = FakeLocalMutationDao().apply {
            shouldThrow = CancellationException("test_cancel")
        }
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val repo = OfflineFirstGoalRepository(
            authRepository = auth,
            goalDao = dao,
            offlineWriteQueue = queue,
        )

        assertFailsWith<CancellationException> {
            repo.create(
                CreateGoalCommand(
                    name = "Hedef",
                    targetAmount = Money(100_000, Currency.TRY),
                    targetDate = LocalDate(2027, 12, 31),
                    color = CategoryColor("#2E7D32"),
                ),
            )
        }
    }

    private class TestActiveWorkspaceScope(
        initial: EntityId? = null,
    ) : ActiveWorkspaceScope {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)
        override fun observe(profileId: EntityId): Flow<EntityId?> = flow
        override suspend fun current(profileId: EntityId): EntityId? = flow.value
    }

    @Test
    fun goalRepository_activeWorkspaceScope_enforcesScopedWrites() = runTest {
        val auth = FakeAuthRepository(testSession)
        val goalDao = FakeGoalDao()
        val mutationDao = FakeLocalMutationDao()
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val scope = TestActiveWorkspaceScope(EntityId("ws-1"))
        val repo = OfflineFirstGoalRepository(
            authRepository = auth,
            goalDao = goalDao,
            offlineWriteQueue = queue,
            entityIdGenerator = { EntityId("g-ws-1") },
            activeWorkspaceScope = scope,
        )

        // 1. ws-1 scope'unda goal oluştur
        val createResult = repo.create(
            CreateGoalCommand(
                name = "Şirket Rezervi",
                targetAmount = Money(500_000, Currency.TRY),
                targetDate = LocalDate(2027, 12, 31),
                color = CategoryColor("#2E7D32"),
            ),
        )
        assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals("ws-1", mutationDao.lastGoal?.workspaceId)

        // 2. ws-1 goal'üne katkı ekle
        goalDao.goals["g-ws-1"] = mutationDao.lastGoal!!
        val contribResult = repo.addContribution(
            AddGoalContributionCommand(
                goalId = EntityId("g-ws-1"),
                amount = Money(50_000, Currency.TRY),
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 1),
            ),
        )
        assertIs<RepositoryResult.Success<EntityId>>(contribResult)

        // 3. Başka workspace'e (ws-2) ait goal'e katkı eklemeyi dene -> fail-closed
        goalDao.goals["g-ws-2"] = GoalEntity(
            id = "g-ws-2",
            ownerId = "user-1",
            workspaceId = "ws-2",
            name = "Başka Workspace Hedefi",
            targetAmountMinor = 500_000L,
            currentAmountMinor = 0L,
            currencyCode = "TRY",
            targetDate = "2027-12-31",
            colorHex = "#2E7D32",
            iconKey = "flag",
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        val otherContribResult = repo.addContribution(
            AddGoalContributionCommand(
                goalId = EntityId("g-ws-2"),
                amount = Money(50_000, Currency.TRY),
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 1),
            ),
        )
        assertIs<RepositoryResult.Failure>(otherContribResult)
        assertEquals(AppError.Validation("goal_not_found"), otherContribResult.error)

        // 4. Başka workspace'e ait goal güncelleme -> fail-closed
        val otherUpdateResult = repo.update(
            UpdateGoalCommand(
                id = EntityId("g-ws-2"),
                name = "Başka",
                targetAmount = Money(600_000, Currency.TRY),
                targetDate = LocalDate(2028, 1, 1),
                color = CategoryColor("#2E7D32"),
            ),
        )
        assertIs<RepositoryResult.Failure>(otherUpdateResult)
        assertEquals(AppError.Validation("goal_not_found"), otherUpdateResult.error)

        // 5. Başka workspace'e ait goal silme -> fail-closed
        val otherDeleteResult = repo.softDelete(EntityId("g-ws-2"))
        assertIs<RepositoryResult.Failure>(otherDeleteResult)
        assertEquals(AppError.Validation("goal_not_found"), otherDeleteResult.error)
    }

    @Test
    fun debtRepository_activeWorkspaceScope_enforcesScopedWrites() = runTest {
        val auth = FakeAuthRepository(testSession)
        val debtDao = FakeDebtDao()
        val mutationDao = FakeLocalMutationDao()
        val queue = OfflineWriteQueue(mutationDao, FakeSyncOperationDao())
        val scope = TestActiveWorkspaceScope(EntityId("ws-1"))
        val repo = OfflineFirstDebtRepository(
            authRepository = auth,
            debtDao = debtDao,
            offlineWriteQueue = queue,
            entityIdGenerator = { EntityId("d-ws-1") },
            activeWorkspaceScope = scope,
        )

        // 1. ws-1 scope'unda debt oluştur
        val createResult = repo.create(
            CreateDebtCommand(
                title = "Şirket Kredisi",
                amount = Money(1_000_000, Currency.TRY),
                type = DebtType.DEBT,
                dueDate = LocalDate(2027, 12, 31),
            ),
        )
        assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals("ws-1", mutationDao.lastDebt?.workspaceId)

        // 2. ws-1 debt'ine ödeme ekle
        debtDao.debts["d-ws-1"] = mutationDao.lastDebt!!
        val paymentResult = repo.addPayment(
            AddDebtPaymentCommand(
                debtId = EntityId("d-ws-1"),
                amount = Money(100_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
            ),
        )
        assertIs<RepositoryResult.Success<EntityId>>(paymentResult)

        // 3. Başka workspace'e (ws-2) ait debt'e ödeme eklemeyi dene -> fail-closed
        debtDao.debts["d-ws-2"] = DebtEntity(
            id = "d-ws-2",
            ownerId = "user-1",
            workspaceId = "ws-2",
            title = "Başka Borç",
            amountMinor = 1_000_000L,
            currencyCode = "TRY",
            typeCode = "DEBT",
            dueDate = "2027-12-31",
            statusCode = "OPEN",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        val otherPaymentResult = repo.addPayment(
            AddDebtPaymentCommand(
                debtId = EntityId("d-ws-2"),
                amount = Money(100_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
            ),
        )
        assertIs<RepositoryResult.Failure>(otherPaymentResult)
        assertEquals(AppError.Validation("debt_not_found"), otherPaymentResult.error)

        // 4. Başka workspace'e ait debt güncelleme -> fail-closed
        val otherUpdateResult = repo.update(
            UpdateDebtCommand(
                id = EntityId("d-ws-2"),
                title = "Başka Borç",
                amount = Money(1_000_000, Currency.TRY),
                type = DebtType.DEBT,
                dueDate = LocalDate(2028, 1, 1),
                description = null,
            ),
        )
        assertIs<RepositoryResult.Failure>(otherUpdateResult)
        assertEquals(AppError.Validation("debt_not_found"), otherUpdateResult.error)

        // 5. Başka workspace'e ait debt silme -> fail-closed
        val otherDeleteResult = repo.softDelete(EntityId("d-ws-2"))
        assertIs<RepositoryResult.Failure>(otherDeleteResult)
        assertEquals(AppError.Validation("debt_not_found"), otherDeleteResult.error)
    }
}
