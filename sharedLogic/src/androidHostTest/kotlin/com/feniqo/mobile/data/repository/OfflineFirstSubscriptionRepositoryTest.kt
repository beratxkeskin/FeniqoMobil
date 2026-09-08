package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceCommand
import com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceResult
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SubscriptionDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
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
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult

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
import kotlinx.serialization.json.jsonPrimitive

class OfflineFirstSubscriptionRepositoryTest {

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

    private class FakeSubscriptionDao : SubscriptionDao {
        val subscriptions = mutableMapOf<String, SubscriptionEntity>()

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<SubscriptionEntity>> =
            flowOf(subscriptions.values.filter { it.ownerId == ownerId && it.workspaceId == workspaceId && it.sync.deletedAtEpochMillis == null })

        override fun observeById(id: String): Flow<SubscriptionEntity?> =
            flowOf(subscriptions[id]?.takeIf { it.sync.deletedAtEpochMillis == null })

        override suspend fun getById(id: String): SubscriptionEntity? =
            subscriptions[id]?.takeIf { it.sync.deletedAtEpochMillis == null }

        override suspend fun getAnyById(id: String): SubscriptionEntity? =
            subscriptions[id]

        override suspend fun getActiveSubscriptions(): List<SubscriptionEntity> =
            subscriptions.values.filter { it.isActive && it.sync.deletedAtEpochMillis == null }

        override suspend fun upsert(entity: SubscriptionEntity) {
            subscriptions[entity.id] = entity
        }

        override suspend fun upsertAll(entities: List<SubscriptionEntity>) {
            entities.forEach { subscriptions[it.id] = it }
        }
    }

    private class FakeLocalMutationDao(
        private val subscriptionDao: FakeSubscriptionDao,
    ) : LocalMutationDao {
        val enqueuedOutbox = mutableListOf<SyncOperationEntity>()
        var throwCancellationOnMutate: Boolean = false

        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
        override suspend fun upsertBudgetRow(entity: BudgetEntity) {}
        override suspend fun upsertTransactionRow(entity: TransactionEntity) {}
        override suspend fun insertTransactionRow(entity: TransactionEntity) {}
        override suspend fun upsertTagRows(entities: List<TagEntity>) {}
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) {}
        override suspend fun upsertRecurringTransactionRow(entity: RecurringTransactionEntity) {}
        override suspend fun upsertSubscriptionRow(entity: SubscriptionEntity) {
            if (throwCancellationOnMutate) throw CancellationException("Simulated cancellation")
            subscriptionDao.upsert(entity)
        }
        override suspend fun upsertRecurringOccurrenceRow(entity: RecurringTransactionOccurrenceEntity) {}
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): RecurringTransactionEntity? = null
        override suspend fun advanceRecurringLastGeneratedDate(
            recurringTransactionId: String,
            expectedPreviousLastGeneratedDate: String?,
            newDueDate: String,
            nowEpochMillis: Long,
        ): Int = 0

        override suspend fun generateRecurringOccurrence(
            command: GenerateRecurringOccurrenceCommand,
            nowEpochMillis: Long,
        ): GenerateRecurringOccurrenceResult = error("N/A")

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
        override suspend fun deleteSubscriptionRow(id: String): Int {
            subscriptionDao.subscriptions.remove(id)
            return 1
        }
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
        override fun nextId(): EntityId = EntityId("sub-${++counter}")
    }

    private fun createFixture(
        sessionUserId: String = "usr-1",
    ): Pair<OfflineFirstSubscriptionRepository, TestContext> {
        val authRepo = FakeAuthRepository(AuthSession(EntityId(sessionUserId), "user@test.com", nowInstant))
        val categoryDao = FakeCategoryDao()
        val subscriptionDao = FakeSubscriptionDao()
        val mutationDao = FakeLocalMutationDao(subscriptionDao)
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

        val repo = OfflineFirstSubscriptionRepository(
            authRepository = authRepo,
            categoryDao = categoryDao,
            subscriptionDao = subscriptionDao,
            offlineWriteQueue = queue,
            entityIdGenerator = SequentialEntityIdGenerator(),
            json = json,
            nowEpochMillisProvider = { nowEpoch },
        )

        return repo to TestContext(authRepo, categoryDao, subscriptionDao, mutationDao, scheduler)
    }

    private data class TestContext(
        val authRepo: FakeAuthRepository,
        val categoryDao: FakeCategoryDao,
        val subscriptionDao: FakeSubscriptionDao,
        val mutationDao: FakeLocalMutationDao,
        val scheduler: FakeBackgroundSyncScheduler,
    )

    private fun expenseCategory(id: String, ownerId: String = "usr-1", isDefault: Boolean = false) = CategoryEntity(
        id = id,
        ownerId = if (isDefault) null else ownerId,
        workspaceId = null,
        scopeKey = if (isDefault) "default" else "user:$ownerId",
        name = "Abonelikler",
        normalizedName = "abonelikler",
        slug = "abonelikler",
        typeCode = "EXPENSE",
        colorHex = "#EF4444",
        iconKey = null,
        isDefault = isDefault,
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    @Test
    fun session_required_for_create_update_setActive_and_softDelete() = runTest {
        val (repo, context) = createFixture()
        context.authRepo.session = null

        val createRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(createRes is RepositoryResult.Failure)
        assertEquals(AppError.Authentication("auth_session_required"), (createRes as RepositoryResult.Failure).error)

        val updateRes = repo.update(
            UpdateSubscriptionCommand(
                id = EntityId("sub-1"),
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
            ),
        )
        assertTrue(updateRes is RepositoryResult.Failure)
        assertEquals(AppError.Authentication("auth_session_required"), (updateRes as RepositoryResult.Failure).error)

        val activeRes = repo.setActive(SetSubscriptionActiveCommand(EntityId("sub-1"), false))
        assertTrue(activeRes is RepositoryResult.Failure)
        assertEquals(AppError.Authentication("auth_session_required"), (activeRes as RepositoryResult.Failure).error)

        val deleteRes = repo.softDelete(EntityId("sub-1"))
        assertTrue(deleteRes is RepositoryResult.Failure)
        assertEquals(AppError.Authentication("auth_session_required"), (deleteRes as RepositoryResult.Failure).error)
    }

    @Test
    fun create_success_with_normalized_name_personal_metadata_and_correct_v2_payload() = runTest {
        val (repo, context) = createFixture()
        context.categoryDao.upsert(expenseCategory("cat-1"))

        val result = repo.create(
            CreateSubscriptionCommand(
                name = "  Spotify Premium  ",
                amount = Money(6499L, Currency.TRY),
                categoryId = EntityId("cat-1"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )

        assertTrue(result is RepositoryResult.Success)
        val createdId = result.value.value
        assertEquals("sub-1", createdId)

        // Room state verification
        val entity = context.subscriptionDao.subscriptions[createdId]
        assertNotNull(entity)
        assertEquals("Spotify Premium", entity.name)
        assertEquals("usr-1", entity.ownerId)
        assertNull(entity.workspaceId)
        assertEquals(6499L, entity.amountMinor)
        assertEquals("TRY", entity.currencyCode)
        assertEquals("cat-1", entity.categoryId)
        assertEquals("MONTHLY", entity.frequencyCode)
        assertEquals(1, entity.interval)
        assertEquals("2026-08-01", entity.startDate)
        assertNull(entity.endDate)
        assertEquals("2026-09-01", entity.nextRenewalDate)
        assertTrue(entity.isActive)
        assertEquals("PENDING_CREATE", entity.sync.syncStatus)

        // Outbox operation verification
        val op = context.mutationDao.enqueuedOutbox.firstOrNull { it.entityId == createdId }
        assertNotNull(op)
        assertEquals("CREATE", op.operationTypeCode)
        assertEquals(2, op.protocolVersion)
        assertNull(op.baseVersion)

        val payload = json.parseToJsonElement(checkNotNull(op.payloadJson)).jsonObject
        assertEquals("sub-1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("usr-1", payload["user_id"]?.jsonPrimitive?.content)
        assertEquals("Spotify Premium", payload["name"]?.jsonPrimitive?.content)
        assertEquals(6499L, payload["amount_minor"]?.jsonPrimitive?.content?.toLong())
        assertEquals("TRY", payload["currency"]?.jsonPrimitive?.content)
        assertEquals("cat-1", payload["category_id"]?.jsonPrimitive?.content)
        assertEquals("MONTHLY", payload["frequency"]?.jsonPrimitive?.content)
        assertEquals(1, payload["interval"]?.jsonPrimitive?.content?.toInt())
        assertEquals("2026-08-01", payload["start_date"]?.jsonPrimitive?.content)
        assertEquals("2026-09-01", payload["next_renewal_date"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, payload["workspace_id"])
        assertEquals(JsonNull, payload["end_date"])
        assertEquals(JsonNull, payload["updated_at"])
        assertEquals(JsonNull, payload["deleted_at"])
        assertEquals(JsonNull, payload["version"])
    }

    @Test
    fun create_success_with_null_category() = runTest {
        val (repo, context) = createFixture()

        val result = repo.create(
            CreateSubscriptionCommand(
                name = "iCloud",
                amount = Money(3999L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )

        assertTrue(result is RepositoryResult.Success)
        val entity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(entity)
        assertNull(entity.categoryId)

        val op = context.mutationDao.enqueuedOutbox.firstOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        val payload = json.parseToJsonElement(checkNotNull(op.payloadJson)).jsonObject
        assertEquals(JsonNull, payload["category_id"])
    }


    @Test
    fun create_fails_when_validation_fails_without_enqueuing() = runTest {
        val (repo, context) = createFixture()

        val blankNameRes = repo.create(
            CreateSubscriptionCommand(
                name = "   ",
                amount = Money(1000L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(blankNameRes is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_name_blank"), blankNameRes.error)
        assertTrue(context.mutationDao.enqueuedOutbox.isEmpty())

        val zeroAmountRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(0L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(zeroAmountRes is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_amount_non_positive"), zeroAmountRes.error)
        assertTrue(context.mutationDao.enqueuedOutbox.isEmpty())
    }


    @Test
    fun create_and_update_fail_when_category_missing_deleted_income_or_other_user() = runTest {
        val (repo, context) = createFixture()

        // 1. Missing category
        val missingRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = EntityId("non-existent-cat"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(missingRes is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_category_not_found"), missingRes.error)

        // 2. Deleted category
        context.categoryDao.upsert(
            expenseCategory("deleted-cat").copy(
                sync = defaultSync.copy(deletedAtEpochMillis = nowEpoch),
            ),
        )
        val deletedRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = EntityId("deleted-cat"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(deletedRes is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_category_type_mismatch"), deletedRes.error)

        // 3. Income category
        context.categoryDao.upsert(
            expenseCategory("income-cat").copy(typeCode = "INCOME"),
        )
        val incomeRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = EntityId("income-cat"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(incomeRes is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_category_type_mismatch"), incomeRes.error)

        // 4. Other user category
        context.categoryDao.upsert(
            expenseCategory("other-cat", ownerId = "other-user"),
        )
        val otherRes = repo.create(
            CreateSubscriptionCommand(
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = EntityId("other-cat"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(otherRes is RepositoryResult.Failure)
        assertEquals(AppError.Authentication("subscription_category_owner_mismatch"), otherRes.error)
    }

    @Test
    fun update_success_preserves_immutable_fields_and_enqueues_update_snapshot() = runTest {
        val (repo, context) = createFixture()
        context.categoryDao.upsert(expenseCategory("cat-1"))
        context.categoryDao.upsert(expenseCategory("cat-2"))

        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Spotify",
            amountMinor = 5999L,
            currencyCode = "TRY",
            categoryId = "cat-1",
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.update(
            UpdateSubscriptionCommand(
                id = EntityId("sub-1"),
                name = "Spotify Family",
                amount = Money(8999L, Currency.TRY),
                categoryId = EntityId("cat-2"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.YEARLY, 1, LocalDate(2026, 8, 1), LocalDate(2028, 8, 1)),
            ),
        )

        assertTrue(result is RepositoryResult.Success)

        val updatedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(updatedEntity)
        assertEquals("Spotify Family", updatedEntity.name)
        assertEquals(8999L, updatedEntity.amountMinor)
        assertEquals("cat-2", updatedEntity.categoryId)
        assertEquals("YEARLY", updatedEntity.frequencyCode)
        assertEquals("2028-08-01", updatedEntity.endDate)
        // Immutable & preserved fields
        assertEquals("usr-1", updatedEntity.ownerId)
        assertNull(updatedEntity.workspaceId)
        assertEquals(1000L, updatedEntity.createdAtEpochMillis)
        assertEquals("2026-09-01", updatedEntity.nextRenewalDate)
        assertTrue(updatedEntity.isActive)
        assertEquals("PENDING_UPDATE", updatedEntity.sync.syncStatus)

        val op = context.mutationDao.enqueuedOutbox.lastOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        assertEquals("UPDATE", op.operationTypeCode)
        assertEquals(2, op.protocolVersion)
        assertEquals(1L, op.baseVersion)
    }

    @Test
    fun setActive_success_only_changes_isActive_and_enqueues_update_snapshot() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Spotify",
            amountMinor = 5999L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.setActive(
            SetSubscriptionActiveCommand(EntityId("sub-1"), isActive = false),
        )
        assertTrue(result is RepositoryResult.Success)

        val updatedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(updatedEntity)
        assertEquals(false, updatedEntity.isActive)
        assertEquals("Spotify", updatedEntity.name)
        assertEquals("2026-09-01", updatedEntity.nextRenewalDate)
        assertEquals("PENDING_UPDATE", updatedEntity.sync.syncStatus)

        val op = context.mutationDao.enqueuedOutbox.lastOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        assertEquals("UPDATE", op.operationTypeCode)
        val payload = json.parseToJsonElement(checkNotNull(op.payloadJson)).jsonObject
        assertEquals("false", payload["is_active"]?.jsonPrimitive?.content)
    }


    @Test
    fun softDelete_success_marks_tombstone_and_enqueues_delete_snapshot() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Spotify",
            amountMinor = 5999L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.softDelete(EntityId("sub-1"))
        assertTrue(result is RepositoryResult.Success)

        val deletedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(deletedEntity)
        assertEquals("PENDING_DELETE", deletedEntity.sync.syncStatus)
        assertEquals(nowEpoch, deletedEntity.sync.deletedAtEpochMillis)

        val op = context.mutationDao.enqueuedOutbox.lastOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        assertEquals("DELETE", op.operationTypeCode)
        assertEquals(1L, op.baseVersion)
    }

    @Test
    fun update_setActive_and_softDelete_fail_when_entity_not_found_deleted_or_other_user() = runTest {
        val (repo, context) = createFixture()

        // 1. Missing
        val missingUpdate = repo.update(
            UpdateSubscriptionCommand(
                id = EntityId("missing-id"),
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
            ),
        )
        assertTrue(missingUpdate is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), missingUpdate.error)

        // 2. Deleted entity
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "deleted-id",
                ownerId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync.copy(deletedAtEpochMillis = nowEpoch),
            ),
        )
        val deletedActive = repo.setActive(SetSubscriptionActiveCommand(EntityId("deleted-id"), false))
        assertTrue(deletedActive is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), deletedActive.error)

        // 3. Foreign user entity
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "foreign-id",
                ownerId = "other-usr",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync,
            ),
        )
        val foreignDelete = repo.softDelete(EntityId("foreign-id"))
        assertTrue(foreignDelete is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), foreignDelete.error)

        // 4. Workspace entity (not personal)
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "workspace-id",
                ownerId = "usr-1",
                workspaceId = "ws-1",
                name = "Spotify",
                amountMinor = 5999L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync,
            ),
        )
        val wsUpdate = repo.update(
            UpdateSubscriptionCommand(
                id = EntityId("workspace-id"),
                name = "Spotify",
                amount = Money(5999L, Currency.TRY),
                categoryId = null,
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
            ),
        )
        assertTrue(wsUpdate is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), wsUpdate.error)
    }

    @Test
    fun observeSubscriptions_and_observeSubscription_filter_by_session_and_personal_scope() = runTest {
        val (repo, context) = createFixture()

        val personal = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Personal Sub",
            amountMinor = 5999L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        val foreign = personal.copy(id = "sub-2", ownerId = "other-user")
        val workspace = personal.copy(id = "sub-3", workspaceId = "ws-1")
        val deleted = personal.copy(id = "sub-4", sync = defaultSync.copy(deletedAtEpochMillis = nowEpoch))

        context.subscriptionDao.upsertAll(listOf(personal, foreign, workspace, deleted))

        // When logged in as usr-1
        val all = repo.observeSubscriptions().first()
        assertEquals(1, all.size)
        assertEquals("sub-1", all.first().id.value)

        val singlePersonal = repo.observeSubscription(EntityId("sub-1")).first()
        assertNotNull(singlePersonal)
        assertEquals("Personal Sub", singlePersonal.name)

        val singleForeign = repo.observeSubscription(EntityId("sub-2")).first()
        assertNull(singleForeign)

        val singleWorkspace = repo.observeSubscription(EntityId("sub-3")).first()
        assertNull(singleWorkspace)

        val singleDeleted = repo.observeSubscription(EntityId("sub-4")).first()
        assertNull(singleDeleted)

        // When logged out
        context.authRepo.session = null
        val loggedOutAll = repo.observeSubscriptions().first()
        assertTrue(loggedOutAll.isEmpty())

        val loggedOutSingle = repo.observeSubscription(EntityId("sub-1")).first()
        assertNull(loggedOutSingle)
    }

    @Test
    fun advanceRenewal_advanced_advancesSinglePeriodAndEnqueuesUpdateV2() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Netflix",
            amountMinor = 14999L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-01-01",
            endDate = null,
            nextRenewalDate = "2026-02-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.advanceRenewal(EntityId("sub-1"))
        assertEquals(
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 3, 1))),
            result,
        )

        val updatedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(updatedEntity)
        assertEquals("2026-03-01", updatedEntity.nextRenewalDate)
        assertTrue(updatedEntity.isActive)
        assertEquals("Netflix", updatedEntity.name)
        assertEquals(14999L, updatedEntity.amountMinor)
        assertEquals("PENDING_UPDATE", updatedEntity.sync.syncStatus)

        val op = context.mutationDao.enqueuedOutbox.lastOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        assertEquals("UPDATE", op.operationTypeCode)
        assertEquals(2, op.protocolVersion)
        val payload = json.parseToJsonElement(checkNotNull(op.payloadJson)).jsonObject
        assertEquals("2026-03-01", payload["next_renewal_date"]?.jsonPrimitive?.content)
        assertEquals("true", payload["is_active"]?.jsonPrimitive?.content)
    }

    @Test
    fun advanceRenewal_completed_setsInactiveAndEnqueuesUpdateV2() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Gym Membership",
            amountMinor = 50000L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-01-01",
            endDate = "2026-02-01",
            nextRenewalDate = "2026-02-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.advanceRenewal(EntityId("sub-1"))
        assertEquals(
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Completed),
            result,
        )

        val updatedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(updatedEntity)
        assertEquals("2026-02-01", updatedEntity.nextRenewalDate)
        assertEquals(false, updatedEntity.isActive)
        assertEquals("PENDING_UPDATE", updatedEntity.sync.syncStatus)

        val op = context.mutationDao.enqueuedOutbox.lastOrNull { it.entityId == "sub-1" }
        assertNotNull(op)
        assertEquals("UPDATE", op.operationTypeCode)
        assertEquals(2, op.protocolVersion)
        val payload = json.parseToJsonElement(checkNotNull(op.payloadJson)).jsonObject
        assertEquals("2026-02-01", payload["next_renewal_date"]?.jsonPrimitive?.content)
        assertEquals("false", payload["is_active"]?.jsonPrimitive?.content)
    }

    @Test
    fun advanceRenewal_pastDueDate_advancesSinglePeriodWithoutJumping() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Hosting",
            amountMinor = 20000L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-01-01",
            endDate = null,
            nextRenewalDate = "2026-03-01",
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.advanceRenewal(EntityId("sub-1"))
        assertEquals(
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 4, 1))),
            result,
        )

        val updatedEntity = context.subscriptionDao.subscriptions["sub-1"]
        assertNotNull(updatedEntity)
        assertEquals("2026-04-01", updatedEntity.nextRenewalDate)
        assertTrue(updatedEntity.isActive)
    }


    @Test
    fun advanceRenewal_inactiveSubscription_failsWithoutEnqueueing() = runTest {
        val (repo, context) = createFixture()
        val existingEntity = SubscriptionEntity(
            id = "sub-1",
            ownerId = "usr-1",
            workspaceId = null,
            name = "Inactive Sub",
            amountMinor = 1000L,
            currencyCode = "TRY",
            categoryId = null,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-01-01",
            endDate = null,
            nextRenewalDate = "2026-02-01",
            isActive = false,
            createdAtEpochMillis = 1000L,
            sync = defaultSync,
        )
        context.subscriptionDao.upsert(existingEntity)

        val result = repo.advanceRenewal(EntityId("sub-1"))
        assertTrue(result is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_inactive"), result.error)

        val outbox = context.mutationDao.enqueuedOutbox.filter { it.entityId == "sub-1" }
        assertTrue(outbox.isEmpty())
    }

    @Test
    fun advanceRenewal_notFound_foreignOwner_workspace_deleted_failsWithoutEnqueueing() = runTest {
        val (repo, context) = createFixture()

        // 1. Missing
        val missingResult = repo.advanceRenewal(EntityId("missing-id"))
        assertTrue(missingResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), missingResult.error)

        // 2. Foreign owner
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "other-user-sub",
                ownerId = "other-usr",
                workspaceId = null,
                name = "Other Sub",
                amountMinor = 1000L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-01-01",
                endDate = null,
                nextRenewalDate = "2026-02-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync,
            ),
        )
        val foreignResult = repo.advanceRenewal(EntityId("other-user-sub"))
        assertTrue(foreignResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), foreignResult.error)

        // 3. Workspace sub
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "workspace-sub",
                ownerId = "usr-1",
                workspaceId = "ws-1",
                name = "Workspace Sub",
                amountMinor = 1000L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-01-01",
                endDate = null,
                nextRenewalDate = "2026-02-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync,
            ),
        )
        val wsResult = repo.advanceRenewal(EntityId("workspace-sub"))
        assertTrue(wsResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), wsResult.error)

        // 4. Deleted sub
        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "deleted-sub",
                ownerId = "usr-1",
                workspaceId = null,
                name = "Deleted Sub",
                amountMinor = 1000L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-01-01",
                endDate = null,
                nextRenewalDate = "2026-02-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync.copy(deletedAtEpochMillis = nowEpoch),
            ),
        )
        val deletedResult = repo.advanceRenewal(EntityId("deleted-sub"))
        assertTrue(deletedResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), deletedResult.error)

        assertTrue(context.mutationDao.enqueuedOutbox.isEmpty())
    }

    @Test
    fun cancellation_exception_is_rethrown_by_all_methods() = runTest {
        val (repo, context) = createFixture()
        context.mutationDao.throwCancellationOnMutate = true

        assertFailsWith<CancellationException> {
            repo.create(
                CreateSubscriptionCommand(
                    name = "Spotify",
                    amount = Money(5999L, Currency.TRY),
                    categoryId = null,
                    renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                    nextRenewalDate = LocalDate(2026, 9, 1),
                ),
            )
        }

        context.subscriptionDao.upsert(
            SubscriptionEntity(
                id = "sub-1",
                ownerId = "usr-1",
                workspaceId = null,
                name = "Spotify",
                amountMinor = 5999L,
                currencyCode = "TRY",
                categoryId = null,
                frequencyCode = "MONTHLY",
                interval = 1,
                startDate = "2026-08-01",
                endDate = null,
                nextRenewalDate = "2026-09-01",
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = defaultSync,
            ),
        )

        assertFailsWith<CancellationException> {
            repo.update(
                UpdateSubscriptionCommand(
                    id = EntityId("sub-1"),
                    name = "Spotify Family",
                    amount = Money(8999L, Currency.TRY),
                    categoryId = null,
                    renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                ),
            )
        }

        assertFailsWith<CancellationException> {
            repo.setActive(SetSubscriptionActiveCommand(EntityId("sub-1"), false))
        }

        assertFailsWith<CancellationException> {
            repo.advanceRenewal(EntityId("sub-1"))
        }

        assertFailsWith<CancellationException> {
            repo.softDelete(EntityId("sub-1"))
        }
    }

    private class TestActiveWorkspaceScope(
        initial: EntityId? = null,
    ) : ActiveWorkspaceScope {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)
        override fun observe(profileId: EntityId): Flow<EntityId?> = flow
        override suspend fun current(profileId: EntityId): EntityId? = flow.value
    }

    private fun subscriptionEntity(
        id: String = "sub-1",
        ownerId: String = "user-1",
        workspaceId: String? = null,
        categoryId: String? = null,
        amountMinor: Long = 5999L,
    ) = SubscriptionEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
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
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    @Test
    fun activeWorkspaceScope_isolatesObserveAndEnforcesScopedWrites() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "token", nowInstant))
        val categoryDao = FakeCategoryDao().apply {
            upsert(
                expenseCategory("cat-ws-1", ownerId = "user-1").copy(
                    workspaceId = "ws-1",
                    scopeKey = "workspace:ws-1",
                ),
            )
        }
        val subscriptionDao = FakeSubscriptionDao()
        val mutationDao = FakeLocalMutationDao(subscriptionDao)
        val writeQueue = OfflineWriteQueue(
            mutationDao = mutationDao,
            operationDao = FakeSyncOperationDao(),
        )
        val scope = TestActiveWorkspaceScope(null) // Personal mode initially

        val repo = OfflineFirstSubscriptionRepository(
            authRepository = authRepo,
            categoryDao = categoryDao,
            subscriptionDao = subscriptionDao,
            offlineWriteQueue = writeQueue,
            entityIdGenerator = { EntityId("sub-ws-1") },
            activeWorkspaceScope = scope,
            nowEpochMillisProvider = { nowEpoch },
        )

        // 1. Personal entity ekle
        subscriptionDao.upsert(
            subscriptionEntity(id = "sub-personal", workspaceId = null),
        )
        // 2. Workspace entity ekle
        subscriptionDao.upsert(
            subscriptionEntity(id = "sub-ws-existing", workspaceId = "ws-1"),
        )

        // 3. Personal modda yalnız personal kayıt gözlemlenmeli
        val personalList = repo.observeSubscriptions().first()
        assertEquals(1, personalList.size)
        assertEquals(EntityId("sub-personal"), personalList.first().id)

        // 4. Scope ws-1 olunca yalnız ws-1 kayıt gözlemlenmeli
        scope.flow.value = EntityId("ws-1")
        val wsList = repo.observeSubscriptions().first()
        assertEquals(1, wsList.size)
        assertEquals(EntityId("sub-ws-existing"), wsList.first().id)

        // 5. ws-1 scope'unda yeni kayıt oluşturma
        val createResult = repo.create(
            CreateSubscriptionCommand(
                name = "GitHub Copilot Enterprise",
                amount = Money(3900L, Currency.TRY),
                categoryId = EntityId("cat-ws-1"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
                nextRenewalDate = LocalDate(2026, 9, 1),
            ),
        )
        assertTrue(createResult is RepositoryResult.Success)
        val createdEntity = subscriptionDao.subscriptions["sub-ws-1"]
        assertNotNull(createdEntity)
        assertEquals("ws-1", createdEntity.workspaceId)
        val outboxItem = mutationDao.enqueuedOutbox.last()
        assertEquals("SUBSCRIPTION", outboxItem.entityTypeCode)

        // 6. Başka workspace'e (ws-2) ait kaydı güncellemeye çalışma -> fail-closed
        subscriptionDao.upsert(
            subscriptionEntity(id = "sub-ws-2", workspaceId = "ws-2"),
        )
        val updateOtherWsResult = repo.update(
            UpdateSubscriptionCommand(
                id = EntityId("sub-ws-2"),
                name = "GitHub Copilot Business",
                amount = Money(4500L, Currency.TRY),
                categoryId = EntityId("cat-ws-1"),
                renewalRule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, LocalDate(2026, 8, 1), null),
            ),
        )
        assertTrue(updateOtherWsResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), updateOtherWsResult.error)

        // 7. Başka workspace'e (ws-2) ait kaydı silmeye çalışma -> fail-closed
        val deleteOtherWsResult = repo.softDelete(EntityId("sub-ws-2"))
        assertTrue(deleteOtherWsResult is RepositoryResult.Failure)
        assertEquals(AppError.Validation("subscription_not_found"), deleteOtherWsResult.error)
    }
}

