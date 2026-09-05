package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
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
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstCategoryRepositoryTest {

    @Test
    fun observeCategories_whenNoSession_returnsEmptyList() = runTest {
        val authRepo = FakeCategoryAuthRepository(null)
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val list = repository.observeCategories().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun observeCategory_whenNoSession_returnsNull() = runTest {
        val authRepo = FakeCategoryAuthRepository(null)
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val item = repository.observeCategory(EntityId("c1")).first()
        assertNull(item)
    }

    @Test
    fun observeCategory_canObserveDefaultCategorySafely() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val defaultEntity = sampleCategoryEntity(id = "c-default", ownerId = null, isDefault = true)
        val dao = FakeCategoryDao(listOf(defaultEntity))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val item = repository.observeCategory(EntityId("c-default")).first()
        assertEquals("c-default", item?.id?.value)
        assertTrue(item?.isDefault == true)
    }

    @Test
    fun observeCategories_doesNotIncludeDeletedCategories() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val active = sampleCategoryEntity(id = "c-active", ownerId = "user-1")
        val deletedSync = newSyncMetadata(1_000L).copy(deletedAtEpochMillis = 2_000L)
        val deleted = sampleCategoryEntity(id = "c-del", ownerId = "user-1", sync = deletedSync)
        val dao = FakeCategoryDao(listOf(active, deleted))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val list = repository.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("c-active", list.first().id.value)
    }

    @Test
    fun observeCategoriesForHistoryLookup_includesSameOwnerDeletedCategories() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val active = sampleCategoryEntity(id = "c-act", ownerId = "user-1")
        val deletedSync = newSyncMetadata(1_000L).copy(deletedAtEpochMillis = 2_000L)
        val deleted = sampleCategoryEntity(id = "c-del", ownerId = "user-1", sync = deletedSync)
        val otherDeleted = sampleCategoryEntity(id = "c-other-del", ownerId = "user-2", sync = deletedSync)
        val dao = FakeCategoryDao(listOf(active, deleted, otherDeleted))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val list = repository.observeCategoriesForHistoryLookup().first()
        assertEquals(2, list.size)
        assertTrue(list.any { it.id.value == "c-act" })
        assertTrue(list.any { it.id.value == "c-del" })
        assertTrue(list.none { it.id.value == "c-other-del" }) // Başka kullanıcının silinmişi görünmez
    }

    @Test
    fun create_rejectsDefaultOrNullOwnerCategoryCreation() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        val defaultCat = sampleCategory("c-def", ownerId = null, isDefault = true)
        val result = repository.create(defaultCat)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_default_cannot_be_created_locally", failure.error.code)
    }

    @Test
    fun create_enqueuesPendingCreateWithVersion0AndNullBaseVersion() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 10_000L },
        )

        val cat = sampleCategory("c-new", ownerId = "user-1")
        val result = repository.create(cat)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        val enqueued = queue.lastEnqueuedCategory
        assertEquals("c-new", enqueued?.id)
        assertNull(enqueued?.slug)
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued?.sync?.syncStatus)
        assertEquals(0L, enqueued?.sync?.version)
        assertNull(enqueued?.sync?.baseVersion)
        assertEquals(OutboxOperationType.CREATE.name, queue.lastInsertedOperation?.operationTypeCode)
        assertTrue(queue.lastInsertedOperation?.payloadJson?.contains("\"slug\":null") == true)
    }

    @Test
    fun update_preservesRemoteVersionAndSetsPendingUpdate() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 5_000L,
            localUpdatedAtEpochMillis = 5_000L,
            deletedAtEpochMillis = null,
            version = 5L,
            baseVersion = 5L,
            lastSyncError = null,
        )
        val existingEntity = sampleCategoryEntity(id = "c-remote", ownerId = "user-1", sync = existingSync)
        val dao = FakeCategoryDao(listOf(existingEntity))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 15_000L },
        )

        val updatedCat = sampleCategory(id = "c-remote", ownerId = "user-1", name = "Yeni Kategori")
        val result = repository.update(updatedCat)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedCategory
        assertEquals("c-remote", enqueued?.id)
        assertNull(enqueued?.slug)
        assertEquals(SyncStatus.PENDING_UPDATE.name, enqueued?.sync?.syncStatus)
        assertEquals(5L, enqueued?.sync?.version)
        assertEquals(5L, enqueued?.sync?.baseVersion)
        assertEquals(15_000L, enqueued?.sync?.localUpdatedAtEpochMillis)
        assertEquals(OutboxOperationType.UPDATE.name, queue.lastInsertedOperation?.operationTypeCode)
        assertTrue(queue.lastInsertedOperation?.payloadJson?.contains("\"slug\":null") == true)
    }

    @Test
    fun update_withExistingSlug_preservesSlugInEntityAndDtoSnapshot() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 5_000L,
            localUpdatedAtEpochMillis = 5_000L,
            deletedAtEpochMillis = null,
            version = 5L,
            baseVersion = 5L,
            lastSyncError = null,
        )
        val existingEntity = sampleCategoryEntity(id = "c-slug", ownerId = "user-1", sync = existingSync).copy(slug = "food-dining")
        val dao = FakeCategoryDao(listOf(existingEntity))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 15_000L },
        )

        val updatedCat = sampleCategory(id = "c-slug", ownerId = "user-1", name = "Yemek Düzenleme")
        val result = repository.update(updatedCat)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedCategory
        assertEquals("c-slug", enqueued?.id)
        assertEquals("food-dining", enqueued?.slug)
        assertTrue(queue.lastInsertedOperation?.payloadJson?.contains("\"slug\":\"food-dining\"") == true)
    }

    @Test
    fun update_onUnsyncedCategory_preservesPendingCreateAndNullBaseVersion() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = newSyncMetadata(5_000L) // PENDING_CREATE
        val existingEntity = sampleCategoryEntity(id = "c-local", ownerId = "user-1", sync = existingSync)
        val dao = FakeCategoryDao(listOf(existingEntity))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 15_000L },
        )

        val updatedCat = sampleCategory(id = "c-local", ownerId = "user-1", name = "Yerel Düzenleme")
        val result = repository.update(updatedCat)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedCategory
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued?.sync?.syncStatus)
        assertNull(enqueued?.slug)
        assertEquals(0L, enqueued?.sync?.version)
        assertNull(enqueued?.sync?.baseVersion)
        assertEquals(OutboxOperationType.CREATE.name, queue.lastInsertedOperation?.operationTypeCode)
        assertTrue(queue.lastInsertedOperation?.payloadJson?.contains("\"slug\":null") == true)
    }

    @Test
    fun softDelete_setsPendingDeleteAndDeletedAtTimestamp() = runTest {
        val authRepo = FakeCategoryAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 5_000L,
            localUpdatedAtEpochMillis = 5_000L,
            deletedAtEpochMillis = null,
            version = 2L,
            baseVersion = 2L,
            lastSyncError = null,
        )
        val existingEntity = sampleCategoryEntity(id = "c-del", ownerId = "user-1", sync = existingSync)
        val dao = FakeCategoryDao(listOf(existingEntity))
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 20_000L },
        )

        val result = repository.softDelete(EntityId("c-del"))

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedCategory
        assertEquals(SyncStatus.PENDING_DELETE.name, enqueued?.sync?.syncStatus)
        assertNull(enqueued?.slug)
        assertEquals(2L, enqueued?.sync?.version)
        assertEquals(2L, enqueued?.sync?.baseVersion)
        assertEquals(20_000L, enqueued?.sync?.deletedAtEpochMillis)
        assertEquals(OutboxOperationType.DELETE.name, queue.lastInsertedOperation?.operationTypeCode)
        assertTrue(queue.lastInsertedOperation?.payloadJson?.contains("\"slug\":null") == true)
    }

    @Test
    fun operations_rethrowCancellationException() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        assertFailsWith<CancellationException> {
            repository.create(sampleCategory("c1", "u1"))
        }
    }

    @Test
    fun operations_rethrowErrorWithoutConvertingToRepositoryResult() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw AssertionError("critical_assertion_error")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeCategoryDao()
        val queue = FakeCategoryOfflineWriteQueueHolder()
        val repository = OfflineFirstCategoryRepository(authRepo, dao, queue.queue)

        assertFailsWith<AssertionError> {
            repository.create(sampleCategory("c1", "u1"))
        }
    }

    private fun sampleCategory(
        id: String,
        ownerId: String?,
        name: String = "Market",
        isDefault: Boolean = false,
    ) = Category(
        id = EntityId(id),
        ownerId = ownerId?.let(::EntityId),
        workspaceId = null,
        name = name,
        type = TransactionType.EXPENSE,
        color = CategoryColor("#4CAF50"),
        icon = CategoryIcon("shopping"),
        isDefault = isDefault,
        createdAt = NOW,
    )

    private fun sampleCategoryEntity(
        id: String,
        ownerId: String?,
        isDefault: Boolean = false,
        sync: SyncMetadata = newSyncMetadata(1_000L),
    ) = sampleCategory(id, ownerId, isDefault = isDefault).toEntity(sync)

    private companion object {
        val NOW = Instant.parse("2026-08-21T00:00:00Z")
    }
}

private class FakeCategoryAuthRepository(session: AuthSession?) : AuthRepository {
    val sessionFlow = MutableStateFlow(session)
    override fun observeSession(): Flow<AuthSession?> = sessionFlow
    override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
    override suspend fun refreshSession() = RepositoryResult.Success(Unit)
    override suspend fun signOut() = RepositoryResult.Success(Unit)
}

private class FakeCategoryDao(initial: List<CategoryEntity> = emptyList()) : CategoryDao {
    private val categories = MutableStateFlow(initial)

    override fun observeAll(ownerId: String, workspaceId: String?, typeCode: String?): Flow<List<CategoryEntity>> =
        categories.map { list ->
            list.filter {
                it.sync.deletedAtEpochMillis == null &&
                (typeCode == null || it.typeCode == typeCode) &&
                ((it.isDefault && it.ownerId == null) || (it.ownerId == ownerId && (workspaceId == null && it.workspaceId == null || it.workspaceId == workspaceId)))
            }
        }

    override fun observeById(id: String): Flow<CategoryEntity?> =
        categories.map { list -> list.firstOrNull { it.id == id && it.sync.deletedAtEpochMillis == null } }

    override fun observeByIdAndOwner(id: String, ownerId: String): Flow<CategoryEntity?> =
        categories.map { list ->
            list.firstOrNull {
                it.id == id &&
                it.sync.deletedAtEpochMillis == null &&
                ((it.isDefault && it.ownerId == null) || it.ownerId == ownerId)
            }
        }

    override suspend fun getByIdAndOwner(id: String, ownerId: String): CategoryEntity? =
        categories.value.firstOrNull {
            it.id == id &&
            it.sync.deletedAtEpochMillis == null &&
            ((it.isDefault && it.ownerId == null) || it.ownerId == ownerId)
        }

    override fun observeAllForHistoryLookup(ownerId: String, workspaceId: String?): Flow<List<CategoryEntity>> =
        categories.map { list ->
            list.filter {
                (it.isDefault && it.ownerId == null) || (it.ownerId == ownerId && (workspaceId == null && it.workspaceId == null || it.workspaceId == workspaceId))
            }
        }

    override suspend fun upsert(entity: CategoryEntity) {
        categories.value = categories.value.filterNot { it.id == entity.id } + entity
    }
}

private class FakeCategoryOfflineWriteQueueHolder {
    var lastEnqueuedCategory: CategoryEntity? = null
    var lastInsertedOperation: SyncOperationEntity? = null

    val mutationDao = object : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {
            lastEnqueuedCategory = entity
        }
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
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) {
            lastInsertedOperation = operation
        }
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
        override suspend fun upsertConflictRow(entity: com.feniqo.mobile.data.local.entity.SyncConflictEntity) {}
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1

        override suspend fun upsertTransactionKeepingTagsAndEnqueue(
            entity: TransactionEntity,
            operation: SyncOperationEntity,
        ) {}
    }

    val operationDao = object : SyncOperationDao {
        override fun observePendingCount(): Flow<Int> = flowOf(0)
        override fun observeFailedCount(): Flow<Int> = flowOf(0)
        override suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int) = emptyList<SyncOperationEntity>()
        override suspend fun getById(operationId: String): SyncOperationEntity? = null
        override suspend fun insert(operation: SyncOperationEntity) {
            lastInsertedOperation = operation
        }
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
