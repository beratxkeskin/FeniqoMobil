package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.BudgetDao
import com.feniqo.mobile.data.local.dao.BudgetMutationInputV2
import com.feniqo.mobile.data.local.dao.CategoryDao
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
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstBudgetRepositoryCopyTest {

    private val userSession = AuthSession(EntityId("user-1"), "user1@feniqo.com", Instant.fromEpochMilliseconds(1000L))

    @Test
    fun copyBudgets_whenNoSession_returnsAuthenticationFailure() = runTest {
        val authRepo = CopyFakeBudgetAuthRepository(null)
        val budgetDao = CopyFakeBudgetDao()
        val categoryDao = CopyFakeBudgetCategoryDao()
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Authentication>(result.error)
        assertEquals("auth_session_required", result.error.code)
    }

    @Test
    fun copyBudgets_whenSourceMonthEmpty_returnsZeroCopiedWithoutCallingQueue() = runTest {
        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(emptyList())
        val categoryDao = CopyFakeBudgetCategoryDao()
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(0, result.value.copiedCount)
        assertTrue(result.value.skippedCategoryIds.isEmpty())
        assertTrue(queue.lastEnqueuedBatch.isEmpty())
    }

    @Test
    fun copyBudgets_twoValidBudgets_areCopiedAtomicallyWithExactLimitAndCurrency() = runTest {
        val b1 = sampleBudgetEntity("b-src-1", ownerId = "user-1", categoryId = "c-1", month = "2026-07", limitMinor = 150_000L, currencyCode = "TRY")
        val b2 = sampleBudgetEntity("b-src-2", ownerId = "user-1", categoryId = "c-2", month = "2026-07", limitMinor = 250_000L, currencyCode = "USD")

        val cat1 = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")
        val cat2 = sampleCategoryEntity("c-2", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(b1, b2))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat1, cat2))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        var idGenSeq = 0
        val idGen = EntityIdGenerator { EntityId("new-id-${++idGenSeq}") }

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            entityIdGenerator = idGen,
            nowEpochMillisProvider = { 9000L },
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(2, result.value.copiedCount)
        assertEquals(0, result.value.skippedCount)

        val enqueued = queue.lastEnqueuedBatch
        assertEquals(2, enqueued.size)

        assertEquals("new-id-1", enqueued[0].entity.id)
        assertEquals("c-1", enqueued[0].entity.categoryId)
        assertEquals("2026-08", enqueued[0].entity.month)
        assertEquals(150_000L, enqueued[0].entity.limitMinor)
        assertEquals("TRY", enqueued[0].entity.currencyCode)
        assertEquals(OutboxOperationType.CREATE, enqueued[0].type)

        assertEquals("new-id-2", enqueued[1].entity.id)
        assertEquals("c-2", enqueued[1].entity.categoryId)
        assertEquals("2026-08", enqueued[1].entity.month)
        assertEquals(250_000L, enqueued[1].entity.limitMinor)
        assertEquals("USD", enqueued[1].entity.currencyCode)
        assertEquals(OutboxOperationType.CREATE, enqueued[1].type)
    }

    @Test
    fun copyBudgets_whenActiveTargetBudgetExists_skipsCategory() = runTest {
        val srcBudget = sampleBudgetEntity("b-src", ownerId = "user-1", categoryId = "c-1", month = "2026-07", limitMinor = 100_000L)
        val activeTargetBudget = sampleBudgetEntity("b-tgt-active", ownerId = "user-1", categoryId = "c-1", month = "2026-08", limitMinor = 120_000L)
        val cat = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(srcBudget, activeTargetBudget))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(0, result.value.copiedCount)
        assertEquals(1, result.value.skippedCount)
        assertEquals(EntityId("c-1"), result.value.skippedCategoryIds.first())
        assertTrue(queue.lastEnqueuedBatch.isEmpty())
    }

    @Test
    fun copyBudgets_serverKnownSoftDeletedTarget_isReactivatedAsUpdatePreservingId() = runTest {
        val srcBudget = sampleBudgetEntity("b-src", ownerId = "user-1", categoryId = "c-1", month = "2026-07", limitMinor = 150_000L)
        val serverKnownDeleted = sampleBudgetEntity(
            id = "b-tgt-del-srv",
            ownerId = "user-1",
            categoryId = "c-1",
            month = "2026-08",
            createdAt = 1000L,
            sync = newSyncMetadata(1000L).copy(
                syncStatus = SyncStatus.SYNCED.name,
                version = 4L,
                baseVersion = 4L,
                deletedAtEpochMillis = 2000L,
            ),
        )
        val cat = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(srcBudget, serverKnownDeleted))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 9500L },
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(1, result.value.copiedCount)
        assertEquals(0, result.value.skippedCount)

        val enqueued = queue.lastEnqueuedBatch.first()
        assertEquals("b-tgt-del-srv", enqueued.entity.id)
        assertEquals(1000L, enqueued.entity.createdAtEpochMillis)
        assertEquals(150_000L, enqueued.entity.limitMinor)
        assertNull(enqueued.entity.sync.deletedAtEpochMillis)
        assertEquals(SyncStatus.PENDING_UPDATE.name, enqueued.entity.sync.syncStatus)
        assertEquals(4L, enqueued.entity.sync.baseVersion)
        assertEquals(OutboxOperationType.UPDATE, enqueued.type)
    }

    @Test
    fun copyBudgets_localOnlySoftDeletedTarget_isReactivatedAsCreatePreservingId() = runTest {
        val srcBudget = sampleBudgetEntity("b-src", ownerId = "user-1", categoryId = "c-1", month = "2026-07", limitMinor = 180_000L)
        val localDeleted = sampleBudgetEntity(
            id = "b-tgt-del-loc",
            ownerId = "user-1",
            categoryId = "c-1",
            month = "2026-08",
            createdAt = 1000L,
            sync = newSyncMetadata(1000L).copy(
                syncStatus = SyncStatus.PENDING_DELETE.name,
                version = 0L,
                baseVersion = null,
                deletedAtEpochMillis = 2000L,
            ),
        )
        val cat = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(srcBudget, localDeleted))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 9600L },
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(1, result.value.copiedCount)
        assertEquals(0, result.value.skippedCount)

        val enqueued = queue.lastEnqueuedBatch.first()
        assertEquals("b-tgt-del-loc", enqueued.entity.id)
        assertEquals(1000L, enqueued.entity.createdAtEpochMillis)
        assertEquals(180_000L, enqueued.entity.limitMinor)
        assertNull(enqueued.entity.sync.deletedAtEpochMillis)
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued.entity.sync.syncStatus)
        assertNull(enqueued.entity.sync.baseVersion)
        assertEquals(OutboxOperationType.CREATE, enqueued.type)
    }

    @Test
    fun copyBudgets_deleted_income_otherUser_orWorkspaceCategory_isSkipped() = runTest {
        val b1 = sampleBudgetEntity("b-1", categoryId = "c-deleted", month = "2026-07")
        val b2 = sampleBudgetEntity("b-2", categoryId = "c-income", month = "2026-07")
        val b3 = sampleBudgetEntity("b-3", categoryId = "c-other-user", month = "2026-07")
        val b4 = sampleBudgetEntity("b-4", categoryId = "c-workspace", month = "2026-07")
        val bValid = sampleBudgetEntity("b-5", categoryId = "c-valid", month = "2026-07")

        val catDeleted = sampleCategoryEntity("c-deleted", ownerId = "user-1", deletedAt = 2000L)
        val catIncome = sampleCategoryEntity("c-income", ownerId = "user-1", typeCode = "INCOME")
        val catOtherUser = sampleCategoryEntity("c-other-user", ownerId = "user-2")
        val catWorkspace = sampleCategoryEntity("c-workspace", ownerId = "user-1", workspaceId = "ws-1")
        val catValid = sampleCategoryEntity("c-valid", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(b1, b2, b3, b4, bValid))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(catDeleted, catIncome, catOtherUser, catWorkspace, catValid))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(1, result.value.copiedCount)
        assertEquals(4, result.value.skippedCount)
        val skippedIds = result.value.skippedCategoryIds.map { it.value }
        assertTrue(skippedIds.contains("c-deleted"))
        assertTrue(skippedIds.contains("c-income"))
        assertTrue(skippedIds.contains("c-other-user"))
        assertTrue(skippedIds.contains("c-workspace"))
    }

    @Test
    fun copyBudgets_copiedCount_and_skippedCategoryIds_areDeterministicAndUnique() = runTest {
        val bZ = sampleBudgetEntity("b-z", categoryId = "c-z", month = "2026-07")
        val bA = sampleBudgetEntity("b-a", categoryId = "c-a", month = "2026-07")
        val bSkip = sampleBudgetEntity("b-skip", categoryId = "c-skip", month = "2026-07")

        val catZ = sampleCategoryEntity("c-z", ownerId = "user-1", typeCode = "EXPENSE")
        val catA = sampleCategoryEntity("c-a", ownerId = "user-1", typeCode = "EXPENSE")
        val catSkipIncome = sampleCategoryEntity("c-skip", ownerId = "user-1", typeCode = "INCOME")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(bZ, bA, bSkip))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(catZ, catA, catSkipIncome))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(2, result.value.copiedCount)
        assertEquals(1, result.value.skippedCount)
        assertEquals("c-skip", result.value.skippedCategoryIds.first().value)

        // Queue girişleri sıralı olmalı: c-a önce, c-z sonra
        assertEquals("c-a", queue.lastEnqueuedBatch[0].entity.categoryId)
        assertEquals("c-z", queue.lastEnqueuedBatch[1].entity.categoryId)
    }

    @Test
    fun copyBudgets_queueError_returnsSafeRepositoryFailure() = runTest {
        val b1 = sampleBudgetEntity("b-1", categoryId = "c-1", month = "2026-07")
        val cat1 = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(b1))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat1))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder(shouldThrow = IllegalStateException("queue_batch_failed"))

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.copyBudgets(
            CopyBudgetsCommand(
                sourceMonth = YearMonth("2026-07"),
                targetMonth = YearMonth("2026-08"),
            ),
        )

        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Validation>(result.error)
        assertEquals("invalid_state", result.error.code)
    }

    @Test
    fun copyBudgets_cancellationException_isRethrown() = runTest {
        val b1 = sampleBudgetEntity("b-1", categoryId = "c-1", month = "2026-07")
        val cat1 = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")

        val authRepo = CopyFakeBudgetAuthRepository(userSession)
        val budgetDao = CopyFakeBudgetDao(listOf(b1))
        val categoryDao = CopyFakeBudgetCategoryDao(listOf(cat1))
        val queue = CopyFakeBudgetOfflineWriteQueueHolder(shouldThrow = CancellationException("batch_cancelled"))

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        assertFailsWith<CancellationException> {
            repository.copyBudgets(
                CopyBudgetsCommand(
                    sourceMonth = YearMonth("2026-07"),
                    targetMonth = YearMonth("2026-08"),
                ),
            )
        }
    }

    private fun sampleCategoryEntity(
        id: String,
        ownerId: String?,
        workspaceId: String? = null,
        isDefault: Boolean = false,
        typeCode: String = "EXPENSE",
        deletedAt: Long? = null,
    ) = CategoryEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        scopeKey = if (workspaceId != null) "workspace:$workspaceId" else if (ownerId != null) "user:$ownerId" else "system",
        name = "Category $id",
        normalizedName = "category $id",
        slug = "cat-slug-$id",
        typeCode = typeCode,
        colorHex = "#4CAF50",
        iconKey = "tag",
        isDefault = isDefault,
        createdAtEpochMillis = 1000L,
        sync = newSyncMetadata(1000L).copy(deletedAtEpochMillis = deletedAt),
    )

    private fun sampleBudgetEntity(
        id: String,
        ownerId: String = "user-1",
        workspaceId: String? = null,
        categoryId: String = "c-1",
        month: String = "2026-08",
        limitMinor: Long = 100_000L,
        currencyCode: String = "TRY",
        createdAt: Long = 1000L,
        sync: SyncMetadata = newSyncMetadata(1000L),
    ) = BudgetEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        scopeKey = if (workspaceId != null) "workspace:$workspaceId" else "user:$ownerId",
        categoryId = categoryId,
        month = month,
        limitMinor = limitMinor,
        currencyCode = currencyCode,
        createdAtEpochMillis = createdAt,
        sync = sync,
    )
}

private class CopyFakeBudgetAuthRepository(session: AuthSession?) : AuthRepository {
    val sessionFlow = MutableStateFlow(session)
    override fun observeSession(): Flow<AuthSession?> = sessionFlow
    override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
    override suspend fun refreshSession() = RepositoryResult.Success(Unit)
    override suspend fun signOut() = RepositoryResult.Success(Unit)
}

private class CopyFakeBudgetCategoryDao(
    initial: List<CategoryEntity> = emptyList(),
) : CategoryDao {
    private val categories = MutableStateFlow(initial)

    override fun observeAll(ownerId: String, workspaceId: String?, typeCode: String?): Flow<List<CategoryEntity>> =
        categories.map { list ->
            list.filter {
                (it.isDefault && it.ownerId == null) || (it.ownerId == ownerId && it.workspaceId == workspaceId)
            }.filter { typeCode == null || it.typeCode == typeCode }
        }

    override fun observeById(id: String): Flow<CategoryEntity?> =
        categories.map { it.find { c -> c.id == id } }

    override fun observeByIdAndOwner(id: String, ownerId: String): Flow<CategoryEntity?> =
        categories.map { list ->
            list.find { c -> c.id == id && ((c.isDefault && c.ownerId == null) || c.ownerId == ownerId) }
        }

    override suspend fun getByIdAndOwner(id: String, ownerId: String): CategoryEntity? =
        categories.value.find { c -> c.id == id && ((c.isDefault && c.ownerId == null) || c.ownerId == ownerId) }

    override fun observeAllForHistoryLookup(ownerId: String, workspaceId: String?): Flow<List<CategoryEntity>> =
        categories.map { list ->
            list.filter { (it.isDefault && it.ownerId == null) || (it.ownerId == ownerId && it.workspaceId == workspaceId) }
        }

    override suspend fun upsert(entity: CategoryEntity) {
        categories.value = categories.value.filterNot { it.id == entity.id } + entity
    }
}

private class CopyFakeBudgetDao(
    initial: List<BudgetEntity> = emptyList(),
) : BudgetDao {
    private val budgets = MutableStateFlow(initial)

    override fun observeForMonth(ownerId: String, workspaceId: String?, month: String): Flow<List<BudgetEntity>> =
        budgets.map { list ->
            list.filter {
                it.ownerId == ownerId && it.workspaceId == workspaceId && it.month == month && it.sync.deletedAtEpochMillis == null
            }
        }

    override suspend fun getForMonth(ownerId: String, workspaceId: String?, month: String): List<BudgetEntity> =
        budgets.value.filter {
            it.ownerId == ownerId && it.workspaceId == workspaceId && it.month == month && it.sync.deletedAtEpochMillis == null
        }

    override fun observeById(id: String): Flow<BudgetEntity?> =
        budgets.map { it.find { b -> b.id == id && b.sync.deletedAtEpochMillis == null } }

    override suspend fun getByIdAndOwner(id: String, ownerId: String): BudgetEntity? =
        budgets.value.find { b -> b.id == id && b.ownerId == ownerId && b.sync.deletedAtEpochMillis == null }

    override suspend fun getAnyByScopeCategoryAndMonth(scopeKey: String, categoryId: String, month: String): BudgetEntity? =
        budgets.value.find { b -> b.scopeKey == scopeKey && b.categoryId == categoryId && b.month == month }

    override suspend fun upsert(entity: BudgetEntity) {
        budgets.value = budgets.value.filterNot { it.id == entity.id } + entity
    }
}

private class CopyFakeBudgetOfflineWriteQueueHolder(
    private val shouldThrow: Throwable? = null,
) {
    var lastEnqueuedBatch: List<BudgetMutationInputV2> = emptyList()

    val mutationDao = object : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
        override suspend fun upsertBudgetRow(entity: BudgetEntity) {}
        override suspend fun upsertTransactionRow(entity: TransactionEntity) {}
        override suspend fun insertTransactionRow(entity: TransactionEntity) {}
        override suspend fun upsertTagRows(entities: List<TagEntity>) {}
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) {}
        override suspend fun upsertRecurringTransactionRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionEntity) {}
        override suspend fun upsertSubscriptionRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionEntity) {}
        override suspend fun upsertRecurringOccurrenceRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity) {}
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
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
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) {}
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
        override suspend fun upsertConflictRow(entity: SyncConflictEntity) {}
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1

        override suspend fun upsertTransactionKeepingTagsAndEnqueue(entity: TransactionEntity, operation: SyncOperationEntity) {}
        override suspend fun mutateBudgetsV2(
            inputs: List<BudgetMutationInputV2>,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): List<V2EnqueueResult> {
            shouldThrow?.let { throw it }
            lastEnqueuedBatch = inputs
            return inputs.map { input ->
                V2EnqueueResult("op-b-${input.entity.id}", V2EnqueueDecision.INSERTED)
            }
        }
    }

    val operationDao = object : SyncOperationDao {
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

    val queue = OfflineWriteQueue(
        mutationDao = mutationDao,
        operationDao = operationDao,
        nowEpochMillisProvider = { 1_000L },
        operationIdFactory = { "0123456789abcdef0123456789abcdef" },
    )
}
