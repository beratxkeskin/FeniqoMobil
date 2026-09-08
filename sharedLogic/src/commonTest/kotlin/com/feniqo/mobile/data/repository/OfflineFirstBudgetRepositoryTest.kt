package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.BudgetDao
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
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
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

class OfflineFirstBudgetRepositoryTest {

    private val userSession = AuthSession(EntityId("user-1"), "user1@feniqo.com", Instant.fromEpochMilliseconds(1000L))

    @Test
    fun observe_and_crud_without_session_returns_empty_or_auth_failure() = runTest {
        val authRepo = FakeBudgetAuthRepository(null)
        val budgetDao = FakeBudgetDao()
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()
        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        // Observe boş döner
        val list = repository.observeBudgets(YearMonth("2026-08")).first()
        assertTrue(list.isEmpty())

        val item = repository.observeBudget(EntityId("b1")).first()
        assertNull(item)

        // CRUD auth failure döner
        val createResult = repository.create(CreateBudgetCommand(EntityId("c1"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(createResult)
        assertIs<AppError.Authentication>(createResult.error)

        val updateResult = repository.update(UpdateBudgetCommand(EntityId("b1"), Money(120_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(updateResult)
        assertIs<AppError.Authentication>(updateResult.error)

        val deleteResult = repository.softDelete(EntityId("b1"))
        assertIs<RepositoryResult.Failure>(deleteResult)
        assertIs<AppError.Authentication>(deleteResult.error)
    }

    @Test
    fun observe_filters_to_only_session_user_personal_budgets() = runTest {
        val myPersonalBudget = sampleBudgetEntity(id = "b-mine", ownerId = "user-1", workspaceId = null)
        val otherUserBudget = sampleBudgetEntity(id = "b-other", ownerId = "user-2", workspaceId = null)
        val myWorkspaceBudget = sampleBudgetEntity(id = "b-ws", ownerId = "user-1", workspaceId = "ws-1")

        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(myPersonalBudget, otherUserBudget, myWorkspaceBudget))
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()
        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val list = repository.observeBudgets(YearMonth("2026-08")).first()
        assertEquals(1, list.size)
        assertEquals("b-mine", list.first().id.value)

        val observedOther = repository.observeBudget(EntityId("b-other")).first()
        assertNull(observedOther)

        val observedWs = repository.observeBudget(EntityId("b-ws")).first()
        assertNull(observedWs)

        val observedMine = repository.observeBudget(EntityId("b-mine")).first()
        assertNotNull(observedMine)
        assertEquals("b-mine", observedMine.id.value)
    }

    @Test
    fun observe_with_workspace_id_returns_empty_list() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(sampleBudgetEntity("b-ws", ownerId = "user-1", workspaceId = "ws-1")))
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()
        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val list = repository.observeBudgets(YearMonth("2026-08"), EntityId("ws-1")).first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun create_new_budget_produces_correct_owner_workspace_id_createdAt_pending_create_and_create_payload() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao()
        val category = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")
        val categoryDao = FakeBudgetCategoryDao(listOf(category))
        val queue = FakeBudgetOfflineWriteQueueHolder()
        val customIdGenerator = EntityIdGenerator { EntityId("generated-b1") }

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            entityIdGenerator = customIdGenerator,
            nowEpochMillisProvider = { 5000L },
        )

        val result = repository.create(
            CreateBudgetCommand(
                categoryId = EntityId("c-1"),
                month = YearMonth("2026-08"),
                limit = Money(250_000L, Currency.TRY),
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("generated-b1", result.value.value)

        val enqueued = queue.lastEnqueuedBudget
        assertNotNull(enqueued)
        assertEquals("generated-b1", enqueued.id)
        assertEquals("user-1", enqueued.ownerId)
        assertNull(enqueued.workspaceId)
        assertEquals("user:user-1", enqueued.scopeKey)
        assertEquals("c-1", enqueued.categoryId)
        assertEquals("2026-08", enqueued.month)
        assertEquals(250_000L, enqueued.limitMinor)
        assertEquals("TRY", enqueued.currencyCode)
        assertEquals(5000L, enqueued.createdAtEpochMillis)
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued.sync.syncStatus)
        assertEquals(OutboxOperationType.CREATE, queue.lastEnqueuedType)
    }

    @Test
    fun create_when_active_duplicate_exists_returns_conflict_without_calling_queue() = runTest {
        val existingActive = sampleBudgetEntity("b-active", ownerId = "user-1", categoryId = "c-1", month = "2026-08")
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(existingActive))
        val category = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")
        val categoryDao = FakeBudgetCategoryDao(listOf(category))
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.create(
            CreateBudgetCommand(
                categoryId = EntityId("c-1"),
                month = YearMonth("2026-08"),
                limit = Money(300_000L, Currency.TRY),
            ),
        )

        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Conflict>(result.error)
        assertEquals("budget_already_exists", result.error.code)
        assertNull(queue.lastEnqueuedBudget)
    }

    @Test
    fun create_when_server_known_soft_deleted_exists_reactivates_as_update_preserving_id() = runTest {
        val serverKnownDeleted = sampleBudgetEntity(
            id = "b-deleted-server",
            ownerId = "user-1",
            categoryId = "c-1",
            month = "2026-08",
            createdAt = 1000L,
            sync = newSyncMetadata(1000L).copy(
                syncStatus = SyncStatus.SYNCED.name,
                version = 5L,
                baseVersion = 5L,
                deletedAtEpochMillis = 2000L,
            ),
        )
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(serverKnownDeleted))
        val category = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")
        val categoryDao = FakeBudgetCategoryDao(listOf(category))
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 6000L },
        )

        val result = repository.create(
            CreateBudgetCommand(
                categoryId = EntityId("c-1"),
                month = YearMonth("2026-08"),
                limit = Money(400_000L, Currency.TRY),
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("b-deleted-server", result.value.value)

        val enqueued = queue.lastEnqueuedBudget
        assertNotNull(enqueued)
        assertEquals("b-deleted-server", enqueued.id)
        assertEquals(1000L, enqueued.createdAtEpochMillis)
        assertEquals(400_000L, enqueued.limitMinor)
        assertNull(enqueued.sync.deletedAtEpochMillis)
        assertEquals(SyncStatus.PENDING_UPDATE.name, enqueued.sync.syncStatus)
        assertEquals(5L, enqueued.sync.baseVersion)
        assertEquals(OutboxOperationType.UPDATE, queue.lastEnqueuedType)
    }

    @Test
    fun create_when_local_only_soft_deleted_exists_reactivates_as_create_preserving_id() = runTest {
        val localDeleted = sampleBudgetEntity(
            id = "b-deleted-local",
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
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(localDeleted))
        val category = sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")
        val categoryDao = FakeBudgetCategoryDao(listOf(category))
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 7000L },
        )

        val result = repository.create(
            CreateBudgetCommand(
                categoryId = EntityId("c-1"),
                month = YearMonth("2026-08"),
                limit = Money(350_000L, Currency.TRY),
            ),
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("b-deleted-local", result.value.value)

        val enqueued = queue.lastEnqueuedBudget
        assertNotNull(enqueued)
        assertEquals("b-deleted-local", enqueued.id)
        assertEquals(1000L, enqueued.createdAtEpochMillis)
        assertEquals(350_000L, enqueued.limitMinor)
        assertNull(enqueued.sync.deletedAtEpochMillis)
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued.sync.syncStatus)
        assertNull(enqueued.sync.baseVersion)
        assertEquals(OutboxOperationType.CREATE, queue.lastEnqueuedType)
    }

    @Test
    fun create_when_category_is_income_fails_with_must_be_expense() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao()
        val incomeCat = sampleCategoryEntity("c-income", ownerId = "user-1", typeCode = "INCOME")
        val categoryDao = FakeBudgetCategoryDao(listOf(incomeCat))
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.create(
            CreateBudgetCommand(EntityId("c-income"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)),
        )

        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Validation>(result.error)
        assertEquals("budget_category_must_be_expense", result.error.code)
    }

    @Test
    fun create_when_category_belongs_to_other_owner_or_workspace_fails() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao()
        val otherOwnerCat = sampleCategoryEntity("c-other", ownerId = "user-2", typeCode = "EXPENSE")
        val wsCat = sampleCategoryEntity("c-ws", ownerId = "user-1", workspaceId = "ws-1", typeCode = "EXPENSE")
        val categoryDao = FakeBudgetCategoryDao(listOf(otherOwnerCat, wsCat))
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val res1 = repository.create(CreateBudgetCommand(EntityId("c-other"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(res1)

        val res2 = repository.create(CreateBudgetCommand(EntityId("c-ws"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(res2)
    }

    @Test
    fun update_updates_only_limit_currency_and_enqueues_update() = runTest {
        val existing = sampleBudgetEntity(
            id = "b-1",
            ownerId = "user-1",
            categoryId = "c-1",
            month = "2026-08",
            limitMinor = 100_000L,
            createdAt = 1000L,
            sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.SYNCED.name, version = 1L, baseVersion = 1L),
        )
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(existing))
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 8000L },
        )

        val result = repository.update(UpdateBudgetCommand(EntityId("b-1"), Money(150_000L, Currency.USD)))
        assertIs<RepositoryResult.Success<Unit>>(result)

        val enqueued = queue.lastEnqueuedBudget
        assertNotNull(enqueued)
        assertEquals("b-1", enqueued.id)
        assertEquals("user-1", enqueued.ownerId)
        assertNull(enqueued.workspaceId)
        assertEquals("c-1", enqueued.categoryId)
        assertEquals("2026-08", enqueued.month)
        assertEquals(150_000L, enqueued.limitMinor)
        assertEquals("USD", enqueued.currencyCode)
        assertEquals(1000L, enqueued.createdAtEpochMillis)
        assertEquals(SyncStatus.PENDING_UPDATE.name, enqueued.sync.syncStatus)
        assertEquals(OutboxOperationType.UPDATE, queue.lastEnqueuedType)
    }

    @Test
    fun update_or_delete_non_existent_or_other_owner_budget_fails_with_not_found() = runTest {
        val otherBudget = sampleBudgetEntity(id = "b-other", ownerId = "user-2")
        val wsBudget = sampleBudgetEntity(id = "b-ws", ownerId = "user-1", workspaceId = "ws-1")

        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(otherBudget, wsBudget))
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val updateNonExistent = repository.update(UpdateBudgetCommand(EntityId("b-404"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(updateNonExistent)
        assertIs<AppError.Validation>(updateNonExistent.error)

        val updateOther = repository.update(UpdateBudgetCommand(EntityId("b-other"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(updateOther)

        val updateWs = repository.update(UpdateBudgetCommand(EntityId("b-ws"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(updateWs)

        val deleteNonExistent = repository.softDelete(EntityId("b-404"))
        assertIs<RepositoryResult.Failure>(deleteNonExistent)

        val deleteOther = repository.softDelete(EntityId("b-other"))
        assertIs<RepositoryResult.Failure>(deleteOther)

        val deleteWs = repository.softDelete(EntityId("b-ws"))
        assertIs<RepositoryResult.Failure>(deleteWs)
    }

    @Test
    fun soft_delete_enqueues_tombstone_metadata_and_delete_payload() = runTest {
        val existing = sampleBudgetEntity(
            id = "b-del",
            ownerId = "user-1",
            categoryId = "c-1",
            month = "2026-08",
            limitMinor = 100_000L,
            createdAt = 1000L,
            sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.SYNCED.name, version = 3L, baseVersion = 3L),
        )
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao(listOf(existing))
        val categoryDao = FakeBudgetCategoryDao()
        val queue = FakeBudgetOfflineWriteQueueHolder()

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 9000L },
        )

        val result = repository.softDelete(EntityId("b-del"))
        assertIs<RepositoryResult.Success<Unit>>(result)

        val enqueued = queue.lastEnqueuedBudget
        assertNotNull(enqueued)
        assertEquals("b-del", enqueued.id)
        assertEquals(9000L, enqueued.sync.deletedAtEpochMillis)
        assertEquals(SyncStatus.PENDING_DELETE.name, enqueued.sync.syncStatus)
        assertEquals(OutboxOperationType.DELETE, queue.lastEnqueuedType)
    }

    @Test
    fun queue_or_storage_exception_is_mapped_to_safe_repository_error() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao()
        val categoryDao = FakeBudgetCategoryDao(listOf(sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")))
        val queue = FakeBudgetOfflineWriteQueueHolder(shouldThrow = IllegalStateException("queue_failed"))

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        val result = repository.create(CreateBudgetCommand(EntityId("c-1"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)))
        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Validation>(result.error)
    }

    @Test
    fun cancellation_exception_is_rethrown() = runTest {
        val authRepo = FakeBudgetAuthRepository(userSession)
        val budgetDao = FakeBudgetDao()
        val categoryDao = FakeBudgetCategoryDao(listOf(sampleCategoryEntity("c-1", ownerId = "user-1", typeCode = "EXPENSE")))
        val queue = FakeBudgetOfflineWriteQueueHolder(shouldThrow = CancellationException("coroutine_cancelled"))

        val repository = OfflineFirstBudgetRepository(
            authRepository = authRepo,
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            offlineWriteQueue = queue.queue,
        )

        assertFailsWith<CancellationException> {
            repository.create(CreateBudgetCommand(EntityId("c-1"), YearMonth("2026-08"), Money(100_000L, Currency.TRY)))
        }
    }

    private fun sampleCategoryEntity(
        id: String,
        ownerId: String?,
        workspaceId: String? = null,
        isDefault: Boolean = false,
        typeCode: String = "EXPENSE",
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
        sync = newSyncMetadata(1000L),
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

private class FakeBudgetAuthRepository(session: AuthSession?) : AuthRepository {
    val sessionFlow = MutableStateFlow(session)
    override fun observeSession(): Flow<AuthSession?> = sessionFlow
    override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
    override suspend fun refreshSession() = RepositoryResult.Success(Unit)
    override suspend fun signOut() = RepositoryResult.Success(Unit)
}

private class FakeBudgetCategoryDao(
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

private class FakeBudgetDao(
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

private class FakeBudgetOfflineWriteQueueHolder(
    private val shouldThrow: Throwable? = null,
) {
    var lastEnqueuedBudget: BudgetEntity? = null
    var lastEnqueuedType: OutboxOperationType? = null

    val mutationDao = object : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
        override suspend fun upsertBudgetRow(entity: BudgetEntity) {
            lastEnqueuedBudget = entity
        }
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
        override suspend fun mutateBudgetV2(
            entity: BudgetEntity,
            type: OutboxOperationType,
            payloadJson: String,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): com.feniqo.mobile.data.local.dao.V2EnqueueResult {
            shouldThrow?.let { throw it }
            lastEnqueuedBudget = entity
            lastEnqueuedType = type
            return com.feniqo.mobile.data.local.dao.V2EnqueueResult("op-b", com.feniqo.mobile.data.local.dao.V2EnqueueDecision.INSERTED)
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
