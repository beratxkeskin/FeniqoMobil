package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceCommand
import com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceResult
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.RecurringTransactionDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
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
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import com.feniqo.mobile.domain.usecase.PlanDueRecurringOccurrencesUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

class OfflineFirstRecurringTransactionRepositoryTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }
    private val nowEpoch = 1700000000000L
    private val nowInstant = Instant.fromEpochMilliseconds(nowEpoch)

    private val defaultSync = SyncMetadata(
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtEpochMillis = nowEpoch,
        localUpdatedAtEpochMillis = nowEpoch,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private class FakeAuthRepository(var session: AuthSession? = null) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("N/A")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("N/A")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("N/A")
        override suspend fun signOut(): RepositoryResult<Unit> = error("N/A")
    }

    private class FakeCategoryDao : CategoryDao {
        val categories = mutableMapOf<String, CategoryEntity>()

        override fun observeAll(ownerId: String, workspaceId: String?, typeCode: String?): Flow<List<CategoryEntity>> = flowOf(emptyList())
        override fun observeById(id: String): Flow<CategoryEntity?> = flowOf(categories[id])
        override fun observeByIdAndOwner(id: String, ownerId: String): Flow<CategoryEntity?> = flowOf(categories[id])
        override suspend fun getByIdAndOwner(id: String, ownerId: String): CategoryEntity? = categories[id]
        override fun observeAllForHistoryLookup(ownerId: String, workspaceId: String?): Flow<List<CategoryEntity>> = flowOf(emptyList())
        override suspend fun upsert(entity: CategoryEntity) {
            categories[entity.id] = entity
        }
    }

    private class FakeRecurringTransactionDao : RecurringTransactionDao {
        val rules = mutableMapOf<String, RecurringTransactionEntity>()
        val occurrences = mutableMapOf<Pair<String, String>, RecurringTransactionOccurrenceEntity>()

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<RecurringTransactionEntity>> =
            flowOf(rules.values.filter { it.ownerId == ownerId && it.workspaceId == workspaceId && it.sync.deletedAtEpochMillis == null })
        override fun observeById(id: String): Flow<RecurringTransactionEntity?> = flowOf(rules[id])
        override suspend fun getById(id: String): RecurringTransactionEntity? = rules[id]
        override suspend fun getAnyById(id: String): RecurringTransactionEntity? = rules[id]
        override suspend fun getActiveRules(): List<RecurringTransactionEntity> =

            rules.values.filter { it.isActive && it.sync.deletedAtEpochMillis == null }
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): RecurringTransactionOccurrenceEntity? =
            occurrences[recurringTransactionId to dueDate]
        override fun observeOccurrencesForRecurring(recurringTransactionId: String): Flow<List<RecurringTransactionOccurrenceEntity>> =
            flowOf(occurrences.values.filter { it.recurringTransactionId == recurringTransactionId })
        override suspend fun upsert(entity: RecurringTransactionEntity) {
            rules[entity.id] = entity
        }
        override suspend fun upsertOccurrence(entity: RecurringTransactionOccurrenceEntity) {
            occurrences[entity.recurringTransactionId to entity.dueDate] = entity
        }
    }

    private class FakeLocalMutationDao(
        private val recurringDao: FakeRecurringTransactionDao,
    ) : LocalMutationDao {
        val enqueuedOutbox = mutableListOf<SyncOperationEntity>()
        val insertedTransactions = mutableListOf<TransactionEntity>()
        var staleOnNext: Boolean = false

        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
        override suspend fun upsertBudgetRow(entity: BudgetEntity) {}
        override suspend fun upsertTransactionRow(entity: TransactionEntity) {}
        override suspend fun insertTransactionRow(entity: TransactionEntity) {
            insertedTransactions.add(entity)
        }
        override suspend fun upsertTagRows(entities: List<TagEntity>) {}
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) {}
        override suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity) {
            recurringDao.upsert(entity)
        }
        override suspend fun upsertSubscriptionRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionEntity) {}
        override suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity) {
            recurringDao.upsertOccurrence(entity)
        }
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): RecurringTransactionOccurrenceEntity? =
            recurringDao.getOccurrence(recurringTransactionId, dueDate)
        override suspend fun getRecurringTransactionById(id: String): RecurringTransactionEntity? =
            recurringDao.getById(id)

        override suspend fun advanceRecurringLastGeneratedDate(
            recurringTransactionId: String,
            expectedPreviousLastGeneratedDate: String?,
            newDueDate: String,
            nowEpochMillis: Long,
        ): Int {
            if (staleOnNext) return 0
            val existing = recurringDao.getById(recurringTransactionId) ?: return 0
            if (existing.lastGeneratedDate != expectedPreviousLastGeneratedDate) return 0
            recurringDao.upsert(existing.copy(lastGeneratedDate = newDueDate))
            return 1
        }

        override suspend fun generateRecurringOccurrence(
            command: GenerateRecurringOccurrenceCommand,
            nowEpochMillis: Long,
        ): GenerateRecurringOccurrenceResult {
            val existing = getOccurrence(command.recurringTransactionId, command.dueDate)
            if (existing != null) {
                return GenerateRecurringOccurrenceResult.AlreadyGenerated(existing.transactionId)
            }

            val updated = advanceRecurringLastGeneratedDate(
                recurringTransactionId = command.recurringTransactionId,
                expectedPreviousLastGeneratedDate = command.expectedPreviousLastGeneratedDate,
                newDueDate = command.dueDate,
                nowEpochMillis = nowEpochMillis,
            )
            if (updated == 0) {
                return GenerateRecurringOccurrenceResult.StaleRecurringState
            }

            insertTransactionRow(command.transactionEntity)
            val occ = RecurringTransactionOccurrenceEntity(
                recurringTransactionId = command.recurringTransactionId,
                dueDate = command.dueDate,
                transactionId = command.transactionEntity.id,
                createdAtEpochMillis = nowEpochMillis,
            )
            upsertRecurringOccurrenceRow(occ)
            insertOutboxRow(command.outboxOperation)

            return GenerateRecurringOccurrenceResult.Created(
                transactionId = command.transactionEntity.id,
                operationId = command.outboxOperation.operationId,
            )
        }

        override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
        override suspend fun deleteProfileRow(id: String): Int = 0
        override suspend fun deleteCategoryRow(id: String): Int = 0
        override suspend fun deleteBudgetRow(id: String): Int = 0
        override suspend fun deleteTransactionRow(id: String): Int = 0
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) {
            enqueuedOutbox.add(operation)
        }
        override suspend fun deleteOutboxRow(operationId: String): Int = 0
        override suspend fun getOutboxById(operationId: String): SyncOperationEntity? =
            enqueuedOutbox.find { it.operationId == operationId }
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
        override suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
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
        override suspend fun upsertWorkspaceMemberRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity) {}
        override suspend fun upsertWorkspaceInvitationRow(entity: com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity) {}
        override suspend fun deleteWorkspaceInvitationRow(id: String): Int = 0
        override suspend fun getWorkspaceInvitationById(id: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceInvitationByTokenHash(tokenHash: String): com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity? = null
        override suspend fun getWorkspaceMember(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun rebaseWorkspaceMemberVersion(workspaceId: String, userId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseWorkspaceInvitationVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun markWorkspaceMemberSyncedIfDeleted(workspaceId: String, userId: String, nowEpochMillis: Long): Int = 0
        override suspend fun upsertGoalRow(entity: com.feniqo.mobile.data.local.entity.GoalEntity) = Unit
        override suspend fun upsertGoalContributionRow(entity: com.feniqo.mobile.data.local.entity.GoalContributionEntity) = Unit
        override suspend fun upsertDebtRow(entity: com.feniqo.mobile.data.local.entity.DebtEntity) = Unit
        override suspend fun upsertDebtPaymentRow(entity: com.feniqo.mobile.data.local.entity.DebtPaymentEntity) = Unit
        override suspend fun deleteGoalRow(id: String): Int = 0
        override suspend fun deleteGoalContributionRow(id: String): Int = 0
        override suspend fun deleteDebtRow(id: String): Int = 0
        override suspend fun deleteDebtPaymentRow(id: String): Int = 0
        override suspend fun getGoalById(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getDebtById(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getGoalContributionById(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtPaymentById(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        override suspend fun getActiveGoalContributions(goalId: String): List<com.feniqo.mobile.data.local.entity.GoalContributionEntity> = emptyList()
        override suspend fun getActiveDebtPayments(debtId: String): List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity> = emptyList()
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
        override suspend fun upsertConflictRow(entity: SyncConflictEntity) {}
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun upsertTransactionKeepingTagsAndEnqueue(entity: TransactionEntity, operation: SyncOperationEntity) {}
        override suspend fun upsertTransactionsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionMutationUnit>) {}
        override suspend fun upsertTransactionsKeepingTagsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit>) {}

    }

    private class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
        var scheduleCount = 0
        override fun scheduleInitialSync() {}
        override fun scheduleOutboxSync() {
            scheduleCount++
        }
        override fun cancelSyncWork() {}
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

    private class SequentialEntityIdGenerator : EntityIdGenerator {
        private var counter = 0
        override fun nextId(): EntityId = EntityId("tx-${++counter}")
    }

    private fun createCategory(
        id: String = "cat-1",
        ownerId: String = "user-1",
        typeCode: String = "EXPENSE",
        deletedAt: Long? = null,
    ) = CategoryEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        scopeKey = "personal:$ownerId",
        name = "Market",
        normalizedName = "market",
        slug = null,
        typeCode = typeCode,
        colorHex = "#FF0000",
        iconKey = "cart",
        isDefault = false,
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync.copy(deletedAtEpochMillis = deletedAt),
    )

    private fun createRecurringEntity(
        id: String = "rec-1",
        ownerId: String = "user-1",
        workspaceId: String? = null,
        categoryId: String = "cat-1",
        typeCode: String = "EXPENSE",
        startDate: String = "2026-08-01",
        lastGeneratedDate: String? = null,
        isActive: Boolean = true,
    ) = RecurringTransactionEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        amountMinor = 50000L,
        currencyCode = "TRY",
        typeCode = typeCode,
        categoryId = categoryId,
        description = "Aylık internet",
        paymentMethodCode = "CREDIT_CARD",
        frequencyCode = "DAILY",
        interval = 1,
        startDate = startDate,
        endDate = null,
        lastGeneratedDate = lastGeneratedDate,
        isActive = isActive,
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    private fun createFixture(
        sessionUserId: String = "user-1",
    ): Pair<OfflineFirstRecurringTransactionRepository, TestContext> {
        val authRepo = FakeAuthRepository(AuthSession(EntityId(sessionUserId), "user@test.com", nowInstant))
        val categoryDao = FakeCategoryDao()
        val recurringDao = FakeRecurringTransactionDao()
        val mutationDao = FakeLocalMutationDao(recurringDao)
        val scheduler = FakeBackgroundSyncScheduler()

        var opCounter = 0
        val queue = OfflineWriteQueue(
            mutationDao = mutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = scheduler,
            operationIdFactory = {
                val hex = (++opCounter).toString(16).padStart(32, '0')
                hex
            },
            nowEpochMillisProvider = { nowEpoch },
        )

        val repo = OfflineFirstRecurringTransactionRepository(
            authRepository = authRepo,
            categoryDao = categoryDao,
            recurringTransactionDao = recurringDao,
            offlineWriteQueue = queue,
            entityIdGenerator = SequentialEntityIdGenerator(),
            json = json,
            planDueRecurringOccurrencesUseCase = PlanDueRecurringOccurrencesUseCase(),
            nowEpochMillisProvider = { nowEpoch },
        )

        return repo to TestContext(authRepo, categoryDao, recurringDao, mutationDao, scheduler)
    }

    private data class TestContext(
        val authRepo: FakeAuthRepository,
        val categoryDao: FakeCategoryDao,
        val recurringDao: FakeRecurringTransactionDao,
        val mutationDao: FakeLocalMutationDao,
        val scheduler: FakeBackgroundSyncScheduler,
    )

    @Test
    fun validPersonalActiveRule_createsTransactionOccurrenceAndV2Outbox() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1", startDate = "2026-08-01"))

        val result = repo.generateDueTransactions(
            throughDate = LocalDate(2026, 8, 2),
            createdAt = nowInstant,
        )

        assertTrue(result is RepositoryResult.Success)
        assertEquals(2, result.value.createdCount)
        assertEquals(0, result.value.alreadyGeneratedCount)
        assertTrue(result.value.staleRecurringIds.isEmpty())
        assertTrue(result.value.skippedRecurringIds.isEmpty())

        // Verify outbox was written with V2 specs
        assertEquals(2, ctx.mutationDao.enqueuedOutbox.size)
        val op = ctx.mutationDao.enqueuedOutbox.first()
        assertEquals("TRANSACTION", op.entityTypeCode)
        assertEquals("CREATE", op.operationTypeCode)
        assertEquals(2, op.protocolVersion)
        assertEquals(null, op.baseVersion)
        assertEquals("PENDING", op.statusCode)
        assertEquals(0, op.attemptCount)
        assertEquals(null, op.predecessorOperationId)
        assertEquals(false, op.isBlocked)

        // Verify payload JSON decode
        val payloadDto = json.decodeFromString<TransactionDto>(op.payloadJson!!)
        assertEquals(50000L, payloadDto.amountMinor)
        assertEquals("TRY", payloadDto.currency)

        // Verify scheduler called
        assertEquals(2, ctx.scheduler.scheduleCount)
    }

    @Test
    fun queuedRecurringOccurrenceCreatePayload_containsExplicitNullsForNullableV2Fields() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1", startDate = "2026-08-01"))

        val result = repo.generateDueTransactions(
            throughDate = LocalDate(2026, 8, 1),
            createdAt = nowInstant,
        )

        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, result.value.createdCount)

        val op = ctx.mutationDao.enqueuedOutbox.single()
        assertEquals(2, op.protocolVersion)
        assertEquals("TRANSACTION", op.entityTypeCode)
        assertEquals("CREATE", op.operationTypeCode)

        val jsonObject = json.parseToJsonElement(op.payloadJson!!).jsonObject
        // Verify explicit nulls for optional/nullable V2 fields
        assertTrue(jsonObject.containsKey("receipt_path"), "receipt_path alani payload'da yer almalidir")
        assertEquals(JsonNull, jsonObject["receipt_path"])

        assertTrue(jsonObject.containsKey("installment_number"), "installment_number alani payload'da yer almalidir")
        assertEquals(JsonNull, jsonObject["installment_number"])

        assertTrue(jsonObject.containsKey("total_installments"), "total_installments alani payload'da yer almalidir")
        assertEquals(JsonNull, jsonObject["total_installments"])

        assertTrue(jsonObject.containsKey("installment_group_id"), "installment_group_id alani payload'da yer almalidir")
        assertEquals(JsonNull, jsonObject["installment_group_id"])
    }

    @Test
    fun secondCallWithSameThroughDate_producesNoNewCandidates() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1", startDate = "2026-08-01"))

        // First run generates candidate
        val res1 = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        assertTrue(res1 is RepositoryResult.Success)
        assertEquals(1, res1.value.createdCount)
        assertEquals(1, ctx.scheduler.scheduleCount)

        // Reset scheduler count
        ctx.scheduler.scheduleCount = 0

        // Second run for same throughDate has no new candidates
        val res2 = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        assertTrue(res2 is RepositoryResult.Success)
        assertEquals(0, res2.value.createdCount)
        assertEquals(0, res2.value.alreadyGeneratedCount)
        assertEquals(0, ctx.scheduler.scheduleCount)
    }

    @Test
    fun occurrenceAlreadyExists_returnsAlreadyGenerated_andDoesNotCallScheduler() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1", startDate = "2026-08-01", lastGeneratedDate = null))

        // Pre-insert occurrence in DAO so candidate is planned but already exists in Room
        ctx.recurringDao.upsertOccurrence(
            RecurringTransactionOccurrenceEntity(
                recurringTransactionId = "rec-1",
                dueDate = "2026-08-01",
                transactionId = "tx-existing",
                createdAtEpochMillis = nowEpoch,
            )
        )

        val res = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        assertTrue(res is RepositoryResult.Success)
        assertEquals(0, res.value.createdCount)
        assertEquals(1, res.value.alreadyGeneratedCount)
        assertEquals(0, ctx.scheduler.scheduleCount)
    }

    @Test
    fun inactiveAndWorkspaceRules_areNotGenerated() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-inactive", isActive = false))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-ws", workspaceId = "ws-1"))

        val result = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 5), createdAt = nowInstant)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(0, result.value.createdCount)
        assertEquals(0, result.value.alreadyGeneratedCount)
        assertEquals(0, ctx.mutationDao.insertedTransactions.size)
    }

    @Test
    fun noAuthSession_returnsAuthenticationFailure() = runTest {
        val (repo, ctx) = createFixture()
        ctx.authRepo.session = null

        val result = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 5), createdAt = nowInstant)
        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Authentication)
    }

    @Test
    fun categoryNotFoundOrDeletedOrTypeMismatch_skipsRuleAndReportsInSkippedIds() = runTest {
        val (repo, ctx) = createFixture()
        // Missing category rule
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-missing-cat", categoryId = "cat-none"))
        // Deleted category rule
        ctx.categoryDao.upsert(createCategory(id = "cat-deleted", deletedAt = nowEpoch))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-deleted-cat", categoryId = "cat-deleted"))
        // Type mismatch rule (rule is EXPENSE, category is INCOME)
        ctx.categoryDao.upsert(createCategory(id = "cat-income", typeCode = "INCOME"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-mismatch-cat", categoryId = "cat-income", typeCode = "EXPENSE"))
        // Valid rule
        ctx.categoryDao.upsert(createCategory(id = "cat-valid", typeCode = "EXPENSE"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-valid", categoryId = "cat-valid"))

        val result = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, result.value.createdCount)
        assertEquals(3, result.value.skippedRecurringIds.size)
        assertEquals(
            listOf(EntityId("rec-deleted-cat"), EntityId("rec-mismatch-cat"), EntityId("rec-missing-cat")),
            result.value.skippedRecurringIds,
        )
    }

    @Test
    fun staleCasState_reportsStaleIdAndDoesNotCreateTransaction() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", ownerId = "user-1"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-stale"))
        ctx.mutationDao.staleOnNext = true

        val result = repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(0, result.value.createdCount)
        assertEquals(listOf(EntityId("rec-stale")), result.value.staleRecurringIds)
        assertEquals(0, ctx.mutationDao.insertedTransactions.size)
    }

    @Test
    fun create_validExpenseRule_writesPersonalEntityAndV2CreateSnapshot() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", typeCode = "EXPENSE"))

        val result = repo.create(createCommand())

        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("tx-1"), result.value)
        val entity = assertNotNull(ctx.recurringDao.getById("tx-1"))
        assertEquals(SyncStatus.PENDING_CREATE.name, entity.sync.syncStatus)
        assertEquals(null, entity.sync.baseVersion)
        val operation = ctx.mutationDao.enqueuedOutbox.single()
        assertEquals("RECURRING_TRANSACTION", operation.entityTypeCode)
        assertEquals("CREATE", operation.operationTypeCode)
        assertEquals(null, operation.baseVersion)
        val payload = json.decodeFromString<RecurringTransactionDto>(operation.payloadJson!!)
        assertEquals("tx-1", payload.id)
        assertEquals("expense", payload.type)
        assertEquals(null, payload.workspaceId)
    }

    @Test
    fun create_incomeCategory_isRejectedWithoutEntityOrOutbox() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "income", typeCode = "INCOME"))

        val result = repo.create(createCommand(categoryId = "income"))

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(result.error is AppError.Validation)
        assertTrue(ctx.recurringDao.rules.isEmpty())
        assertTrue(ctx.mutationDao.enqueuedOutbox.isEmpty())
    }

    @Test
    fun update_validRule_preservesSyncBaseVersionAndQueuesUpdate() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", typeCode = "EXPENSE"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1"))

        val result = repo.update(updateCommand(id = "rec-1", amountMinor = 75000L))

        assertTrue(result is RepositoryResult.Success)
        val entity = assertNotNull(ctx.recurringDao.getById("rec-1"))
        assertEquals(75000L, entity.amountMinor)
        assertEquals(SyncStatus.PENDING_UPDATE.name, entity.sync.syncStatus)
        assertEquals(1L, entity.sync.baseVersion)
        val operation = ctx.mutationDao.enqueuedOutbox.single()
        assertEquals("UPDATE", operation.operationTypeCode)
        assertEquals(1L, operation.baseVersion)
    }

    @Test
    fun pauseAndDelete_queueUpdateThenDeleteTombstone() = runTest {
        val (repo, ctx) = createFixture()
        ctx.categoryDao.upsert(createCategory(id = "cat-1", typeCode = "EXPENSE"))
        ctx.recurringDao.upsert(createRecurringEntity(id = "rec-1"))

        assertTrue(repo.setActive(SetRecurringTransactionActiveCommand(EntityId("rec-1"), false)) is RepositoryResult.Success)
        val paused = assertNotNull(ctx.recurringDao.getById("rec-1"))
        assertEquals(false, paused.isActive)
        assertEquals("UPDATE", ctx.mutationDao.enqueuedOutbox.single().operationTypeCode)

        ctx.mutationDao.enqueuedOutbox.clear()
        assertTrue(repo.softDelete(EntityId("rec-1")) is RepositoryResult.Success)
        val deleted = assertNotNull(ctx.recurringDao.getById("rec-1"))
        assertEquals(SyncStatus.PENDING_DELETE.name, deleted.sync.syncStatus)
        assertEquals(nowEpoch, deleted.sync.deletedAtEpochMillis)
        val operation = ctx.mutationDao.enqueuedOutbox.single()
        assertEquals("DELETE", operation.operationTypeCode)
        assertEquals(1L, operation.baseVersion)
    }

    @Test
    fun cancellationException_isRethrown() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = throw CancellationException("coroutine_cancelled")
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = error("N/A")
            override suspend fun signUp(email: String, password: String, fullName: String?) = error("N/A")
            override suspend fun refreshSession() = error("N/A")
            override suspend fun signOut() = error("N/A")
        }
        val (baseRepo, ctx) = createFixture()
        val repo = OfflineFirstRecurringTransactionRepository(
            authRepository = authRepo,
            categoryDao = ctx.categoryDao,
            recurringTransactionDao = ctx.recurringDao,
            offlineWriteQueue = OfflineWriteQueue(
                mutationDao = ctx.mutationDao,
                operationDao = FakeSyncOperationDao(),
                syncScheduler = ctx.scheduler,
            ),
            entityIdGenerator = SequentialEntityIdGenerator(),
        )

        assertFailsWith<CancellationException> {
            repo.generateDueTransactions(throughDate = LocalDate(2026, 8, 1), createdAt = nowInstant)
        }
    }

    private fun createCommand(categoryId: String = "cat-1") = CreateRecurringTransactionCommand(
        amount = Money(50000L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId(categoryId),
        description = "Aylık internet",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        ),
    )

    private fun updateCommand(id: String, amountMinor: Long) = UpdateRecurringTransactionCommand(
        id = EntityId(id),
        amount = Money(amountMinor, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        description = "Güncel internet",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        ),
    )

    private class TestActiveWorkspaceScope(
        initial: EntityId? = null,
    ) : ActiveWorkspaceScope {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)
        override fun observe(profileId: EntityId): Flow<EntityId?> = flow
        override suspend fun current(profileId: EntityId): EntityId? = flow.value
    }

    @Test
    fun activeWorkspaceScope_isolatesObserveAndEnforcesScopedWrites() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "token", nowInstant))
        val categoryDao = FakeCategoryDao().apply {
            upsert(
                createCategory(
                    id = "cat-ws-1",
                    ownerId = "user-1",
                ).copy(workspaceId = "ws-1", scopeKey = "workspace:ws-1"),
            )
        }
        val recurringDao = FakeRecurringTransactionDao()
        val mutationDao = FakeLocalMutationDao(recurringDao)
        val writeQueue = OfflineWriteQueue(
            mutationDao = mutationDao,
            operationDao = FakeSyncOperationDao(),
        )
        val scope = TestActiveWorkspaceScope(null) // Personal mode initially

        val repo = OfflineFirstRecurringTransactionRepository(
            authRepository = authRepo,
            categoryDao = categoryDao,
            recurringTransactionDao = recurringDao,
            offlineWriteQueue = writeQueue,
            entityIdGenerator = { EntityId("rec-ws-1") },
            activeWorkspaceScope = scope,
            nowEpochMillisProvider = { nowEpoch },
        )

        // 1. Personal entity ekle
        recurringDao.upsert(
            createRecurringEntity(id = "rec-personal", workspaceId = null),
        )
        // 2. Workspace entity ekle
        recurringDao.upsert(
            createRecurringEntity(id = "rec-ws-existing", workspaceId = "ws-1"),
        )

        // 3. Personal modda yalnız personal kayıt gözlemlenmeli
        val personalList = repo.observeRecurringTransactions().first()
        assertEquals(1, personalList.size)
        assertEquals(EntityId("rec-personal"), personalList.first().id)

        // 4. Scope ws-1 olunca yalnız ws-1 kayıt gözlemlenmeli
        scope.flow.value = EntityId("ws-1")
        val wsList = repo.observeRecurringTransactions().first()
        assertEquals(1, wsList.size)
        assertEquals(EntityId("rec-ws-existing"), wsList.first().id)

        // 5. ws-1 scope'unda yeni kayıt oluşturma
        val createResult = repo.create(createCommand(categoryId = "cat-ws-1"))
        assertTrue(createResult is RepositoryResult.Success)
        val createdEntity = recurringDao.rules["rec-ws-1"]
        assertNotNull(createdEntity)
        assertEquals("ws-1", createdEntity.workspaceId)
        val outboxItem = mutationDao.enqueuedOutbox.last()
        assertEquals("RECURRING_TRANSACTION", outboxItem.entityTypeCode)

        // 6. Başka workspace'e (ws-2) ait kaydı güncellemeye çalışma -> fail-closed
        recurringDao.upsert(
            createRecurringEntity(id = "rec-ws-2", workspaceId = "ws-2"),
        )
        val updateOtherWsResult = repo.update(updateCommand("rec-ws-2", 60000L))
        assertTrue(updateOtherWsResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("recurring_transaction_not_found"), updateOtherWsResult.error)

        // 7. Başka workspace'e (ws-2) ait kaydı silmeye çalışma -> fail-closed
        val deleteOtherWsResult = repo.softDelete(EntityId("rec-ws-2"))
        assertTrue(deleteOtherWsResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("recurring_transaction_not_found"), deleteOtherWsResult.error)
    }
}
