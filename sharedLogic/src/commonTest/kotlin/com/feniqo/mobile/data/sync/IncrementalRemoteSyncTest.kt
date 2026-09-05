package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity

import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto

import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant


class IncrementalRemoteSyncTest {

    @Test
    fun pulls_after_stored_cursor_and_advances_only_after_room_write() = runTest {
        val cursors = mutableMapOf(CATEGORY to storedCategoryCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(category = remoteCategory(version = 2))

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(STORED_UPDATED_AT, remote.receivedCategoryCursor?.updatedAt)
        assertEquals(STORED_ID, remote.receivedCategoryCursor?.entityId)
        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("SYNCED", dao.category?.sync?.syncStatus)
        assertEquals(2L, dao.category?.sync?.version)
        assertEquals(REMOTE_ID, cursors[CATEGORY]?.entityId)
    }

    @Test
    fun newer_remote_version_does_not_overwrite_pending_local_change() = runTest {
        val cursors = mutableMapOf(CATEGORY to storedCategoryCursor())
        val local = localPendingCategory()
        val dao = RecordingRemoteSyncDao(category = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(category = remoteCategory(version = 3)),
            remoteSyncDao = dao,
            syncStateDao = FakeSyncStateDao(cursors),
            nowEpochMillisProvider = { RECEIVED_AT },
        ).pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals("Yerel düzenleme", dao.category?.name)
        assertEquals("CONFLICT", dao.category?.sync?.syncStatus)
        val conflict = assertNotNull(dao.conflict)
        assertEquals(1L, conflict.localVersion)
        assertEquals(3L, conflict.remoteVersion)
        assertEquals(true, conflict.localPayloadJson.contains("Yerel düzenleme"))
        assertEquals(true, conflict.remotePayloadJson.contains("Uzak değişiklik"))
        assertEquals(REMOTE_ID, cursors[CATEGORY]?.entityId)
    }

    @Test
    fun pulls_recurring_transactions_after_stored_cursor_and_advances_only_after_room_write() = runTest {
        val cursors = mutableMapOf(RECURRING_TRANSACTION to storedRecurringCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(recurring = remoteRecurring(version = 2))

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(STORED_UPDATED_AT, remote.receivedRecurringCursor?.updatedAt)
        assertEquals(STORED_ID, remote.receivedRecurringCursor?.entityId)
        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedRecurringTransactionCount)
        assertEquals("SYNCED", dao.recurring?.sync?.syncStatus)
        assertEquals(2L, dao.recurring?.sync?.version)
        assertEquals(REMOTE_ID, cursors[RECURRING_TRANSACTION]?.entityId)
    }

    @Test
    fun newer_remote_recurring_transaction_version_does_not_overwrite_pending_local_change() = runTest {
        val cursors = mutableMapOf(RECURRING_TRANSACTION to storedRecurringCursor())
        val local = localPendingRecurring()
        val dao = RecordingRemoteSyncDao(recurring = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(recurring = remoteRecurring(version = 3)),
            remoteSyncDao = dao,
            syncStateDao = FakeSyncStateDao(cursors),
            nowEpochMillisProvider = { RECEIVED_AT },
        ).pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals("Yerel kural", dao.recurring?.description)
        assertEquals("CONFLICT", dao.recurring?.sync?.syncStatus)
        val conflict = assertNotNull(dao.conflict)
        assertEquals(RECURRING_TRANSACTION, conflict.entityTypeCode)
        assertEquals(1L, conflict.localVersion)
        assertEquals(3L, conflict.remoteVersion)
        assertEquals(true, conflict.localPayloadJson.contains("Yerel kural"))
        assertEquals(true, conflict.remotePayloadJson.contains("Uzak kural"))
        assertEquals(REMOTE_ID, cursors[RECURRING_TRANSACTION]?.entityId)
    }

    @Test
    fun remote_recurring_transaction_version_less_than_or_equal_to_base_version_preserves_local_and_advances_cursor() = runTest {
        val cursors = mutableMapOf(RECURRING_TRANSACTION to storedRecurringCursor())
        val local = localPendingRecurring().copy(
            sync = localPendingRecurring().sync.copy(baseVersion = 2, version = 2),
        )
        val dao = RecordingRemoteSyncDao(recurring = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(recurring = remoteRecurring(version = 2)),
            remoteSyncDao = dao,
            syncStateDao = FakeSyncStateDao(cursors),
            nowEpochMillisProvider = { RECEIVED_AT },
        ).pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("PENDING_UPDATE", dao.recurring?.sync?.syncStatus)
        assertEquals("Yerel kural", dao.recurring?.description)
        assertEquals(null, dao.conflict)
        assertEquals(REMOTE_ID, cursors[RECURRING_TRANSACTION]?.entityId)
    }

    @Test
    fun pulls_recurring_transaction_tombstone_and_marks_synced() = runTest {
        val cursors = mutableMapOf(RECURRING_TRANSACTION to storedRecurringCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(recurring = remoteRecurring(version = 4, deletedAt = REMOTE_UPDATED_AT))

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("SYNCED", dao.recurring?.sync?.syncStatus)
        assertEquals(Instant.parse(REMOTE_UPDATED_AT).toEpochMilliseconds(), dao.recurring?.sync?.deletedAtEpochMillis)
        assertEquals(REMOTE_ID, cursors[RECURRING_TRANSACTION]?.entityId)
    }

    @Test
    fun pulls_subscriptions_after_stored_cursor_and_advances_only_after_room_write() = runTest {
        val cursors = mutableMapOf(SUBSCRIPTION to storedSubscriptionCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(subscription = remoteSubscription(version = 2))

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(STORED_UPDATED_AT, remote.receivedSubscriptionCursor?.updatedAt)
        assertEquals(STORED_ID, remote.receivedSubscriptionCursor?.entityId)
        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedSubscriptionCount)
        assertEquals("SYNCED", dao.subscription?.sync?.syncStatus)
        assertEquals(2L, dao.subscription?.sync?.version)
        assertEquals(REMOTE_ID, cursors[SUBSCRIPTION]?.entityId)
    }

    @Test
    fun newer_remote_subscription_version_does_not_overwrite_pending_local_change() = runTest {
        val cursors = mutableMapOf(SUBSCRIPTION to storedSubscriptionCursor())
        val local = localPendingSubscription()
        val dao = RecordingRemoteSyncDao(subscription = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(subscription = remoteSubscription(version = 3)),
            remoteSyncDao = dao,
            syncStateDao = FakeSyncStateDao(cursors),
            nowEpochMillisProvider = { RECEIVED_AT },
        ).pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals("Yerel abonelik", dao.subscription?.name)
        assertEquals("CONFLICT", dao.subscription?.sync?.syncStatus)
        val conflict = assertNotNull(dao.conflict)
        assertEquals(SUBSCRIPTION, conflict.entityTypeCode)
        assertEquals(1L, conflict.localVersion)
        assertEquals(3L, conflict.remoteVersion)
        assertEquals(true, conflict.localPayloadJson.contains("Yerel abonelik"))
        assertEquals(true, conflict.remotePayloadJson.contains("Uzak abonelik"))
        assertEquals(REMOTE_ID, cursors[SUBSCRIPTION]?.entityId)
    }

    @Test
    fun remote_subscription_version_less_than_or_equal_to_base_version_preserves_local_and_advances_cursor() = runTest {
        val cursors = mutableMapOf(SUBSCRIPTION to storedSubscriptionCursor())
        val local = localPendingSubscription().copy(
            sync = localPendingSubscription().sync.copy(baseVersion = 2, version = 2),
        )
        val dao = RecordingRemoteSyncDao(subscription = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(subscription = remoteSubscription(version = 2)),
            remoteSyncDao = dao,
            syncStateDao = FakeSyncStateDao(cursors),
            nowEpochMillisProvider = { RECEIVED_AT },
        ).pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("PENDING_UPDATE", dao.subscription?.sync?.syncStatus)
        assertEquals("Yerel abonelik", dao.subscription?.name)
        assertNull(dao.conflict)
        assertEquals(REMOTE_ID, cursors[SUBSCRIPTION]?.entityId)
    }

    @Test
    fun pulls_subscription_tombstone_and_marks_synced() = runTest {
        val cursors = mutableMapOf(SUBSCRIPTION to storedSubscriptionCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(subscription = remoteSubscription(version = 4, deletedAt = REMOTE_UPDATED_AT))

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("SYNCED", dao.subscription?.sync?.syncStatus)
        assertEquals(Instant.parse(REMOTE_UPDATED_AT).toEpochMilliseconds(), dao.subscription?.sync?.deletedAtEpochMillis)
        assertEquals(REMOTE_ID, cursors[SUBSCRIPTION]?.entityId)
    }

    @Test
    fun missing_version_remote_subscription_fails_closed() = runTest {
        val cursors = mutableMapOf(SUBSCRIPTION to storedSubscriptionCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(subscription = remoteSubscription(version = null))

        assertFailsWith<IllegalArgumentException> {
            IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
                .pullFor(EntityId(USER_ID))
        }
    }

    private class FakeRemote(
        private val category: CategoryDto? = null,
        private val recurring: RecurringTransactionDto? = null,
        private val subscription: SubscriptionDto? = null,
    ) : CoreRemoteDataSource {
        var receivedCategoryCursor: com.feniqo.mobile.data.remote.core.RemoteSyncCursor? = null
        var receivedRecurringCursor: com.feniqo.mobile.data.remote.core.RemoteSyncCursor? = null
        var receivedSubscriptionCursor: com.feniqo.mobile.data.remote.core.RemoteSyncCursor? = null

        override suspend fun fetchProfile(userId: String): ProfileDto? = null

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
            receivedCategoryCursor = query.updatedAfter
            val items = if (category != null && query.page.pageIndex == 0) listOf(category) else emptyList()
            return RemotePage(items, query.page, totalCount = items.size.toLong())
        }

        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> {
            receivedRecurringCursor = query.updatedAfter
            val items = if (recurring != null && query.page.pageIndex == 0) listOf(recurring) else emptyList()
            return RemotePage(items, query.page, totalCount = items.size.toLong())
        }

        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> {
            receivedSubscriptionCursor = query.updatedAfter
            val items = if (subscription != null && query.page.pageIndex == 0) listOf(subscription) else emptyList()
            return RemotePage(items, query.page, totalCount = items.size.toLong())
        }

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("Test kapsamı dışı")
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalDto> = error("Test kapsamı dışı")
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalContributionDto> = error("Test kapsamı dışı")
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtDto> = error("Test kapsamı dışı")
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtPaymentDto> = error("Test kapsamı dışı")
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = error("Test kapsamı dışı")


        override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> = error("Test kapsamı dışı")
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> = error("Test kapsamı dışı")
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = error("Test kapsamı dışı")
        override suspend fun upsertProfile(dto: ProfileDto) = error("Test kapsamı dışı")
        override suspend fun upsertCategory(dto: CategoryDto) = error("Test kapsamı dışı")
        override suspend fun upsertTransaction(dto: TransactionDto) = error("Test kapsamı dışı")
        override suspend fun upsertBudget(dto: BudgetDto) = error("Test kapsamı dışı")
        override suspend fun upsertTag(dto: TagDto) = error("Test kapsamı dışı")
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = error("Test kapsamı dışı")
    }

    private class FakeSyncStateDao(
        private val cursors: MutableMap<String, SyncCursorEntity>,
    ) : SyncStateDao {
        override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]
        override suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity> =
            cursors.filterKeys { it.startsWith("WORKSPACE_MEMBER:") }.values.toList()
        override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
        override suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
        override suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity> = emptyList()
        override suspend fun getAllConflicts(): List<SyncConflictEntity> = emptyList()
        override suspend fun upsertCursor(cursor: SyncCursorEntity) { cursors[cursor.entityTypeCode] = cursor }
        override fun observeConflicts(): Flow<List<SyncConflictEntity>> = flowOf(emptyList())
        override fun observeConflictCount(): Flow<Int> = flowOf(0)
        override suspend fun getConflictCount(): Int = 0
        override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
        override fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?> = flowOf(null)
        override suspend fun getUserState(userId: String): com.feniqo.mobile.data.local.entity.SyncUserStateEntity? = null
        override suspend fun upsertUserState(state: com.feniqo.mobile.data.local.entity.SyncUserStateEntity) = Unit
    }

    private class RecordingRemoteSyncDao(
        var category: CategoryEntity? = null,
        var recurring: RecurringTransactionEntity? = null,
        var subscription: SubscriptionEntity? = null,
        private val cursors: MutableMap<String, SyncCursorEntity>,
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = category?.takeIf { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = recurring?.takeIf { it.id == id }
        override suspend fun getSubscriptionRow(id: String): SubscriptionEntity? = subscription?.takeIf { it.id == id }
        override suspend fun getGoalRow(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        override suspend fun getWorkspaceRow(id: String): com.feniqo.mobile.data.local.entity.WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? =
            if (category?.id == entityId || recurring?.id == entityId || subscription?.id == entityId) "operation-1" else null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) { category = entities.single() }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>) { recurring = entities.single() }
        override suspend fun upsertSubscriptionRows(entities: List<SubscriptionEntity>) { subscription = entities.single() }
        override suspend fun upsertGoalRows(entities: List<com.feniqo.mobile.data.local.entity.GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<com.feniqo.mobile.data.local.entity.GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<com.feniqo.mobile.data.local.entity.DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) { this.conflict = conflict }
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) {
            cursors.forEach { this.cursors[it.entityTypeCode] = it }
        }
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int {
            conflict = null
            return 1
        }
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int {
            category = category?.copy(sync = category!!.sync.copy(syncStatus = "CONFLICT", lastSyncError = error))
            return 1
        }
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int {
            recurring = recurring?.copy(sync = recurring!!.sync.copy(syncStatus = "CONFLICT", lastSyncError = error))
            return 1
        }
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int {
            subscription = subscription?.copy(sync = subscription!!.sync.copy(syncStatus = "CONFLICT", lastSyncError = error))
            return 1
        }
        override suspend fun markGoalConflict(entityId: String, error: String): Int = 0
        override suspend fun markGoalContributionConflict(entityId: String, error: String): Int = 0
        override suspend fun markDebtConflict(entityId: String, error: String): Int = 0
        override suspend fun markDebtPaymentConflict(entityId: String, error: String): Int = 0
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }


    private companion object {
        const val CATEGORY = "CATEGORY"
        const val RECURRING_TRANSACTION = "RECURRING_TRANSACTION"
        const val SUBSCRIPTION = "SUBSCRIPTION"
        const val CATEGORY_ID = "category-1"
        const val USER_ID = "20000000-0000-0000-0000-000000000001"
        const val STORED_ID = "10000000-0000-0000-0000-000000000001"
        const val REMOTE_ID = "10000000-0000-0000-0000-000000000002"
        const val STORED_UPDATED_AT = "2026-08-14T09:00:00Z"
        const val REMOTE_UPDATED_AT = "2026-08-14T10:00:00Z"
        const val CREATED_AT = "2026-08-10T10:00:00Z"
        const val RECEIVED_AT = 1_765_707_200_000L

        fun storedCategoryCursor() = SyncCursorEntity(
            entityTypeCode = CATEGORY,
            updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
            entityId = STORED_ID,
        )

        fun storedRecurringCursor() = SyncCursorEntity(
            entityTypeCode = RECURRING_TRANSACTION,
            updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
            entityId = STORED_ID,
        )

        fun storedSubscriptionCursor() = SyncCursorEntity(
            entityTypeCode = SUBSCRIPTION,
            updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
            entityId = STORED_ID,
        )

        fun remoteCategory(version: Long) = CategoryDto(
            id = REMOTE_ID,
            userId = USER_ID,
            name = "Uzak değişiklik",
            type = "expense",
            color = "#123456",
            createdAt = CREATED_AT,
            updatedAt = REMOTE_UPDATED_AT,
            version = version,
        )

        fun remoteRecurring(version: Long, deletedAt: String? = null) = RecurringTransactionDto(
            id = REMOTE_ID,
            userId = USER_ID,
            amountMinor = 50_000,
            currency = "TRY",
            type = "expense",
            categoryId = CATEGORY_ID,
            description = "Uzak kural",
            paymentMethod = "CREDIT_CARD",
            frequency = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            createdAt = CREATED_AT,
            updatedAt = REMOTE_UPDATED_AT,
            deletedAt = deletedAt,
            version = version,
        )

        fun remoteSubscription(version: Long?, deletedAt: String? = null, name: String = "Uzak abonelik") = SubscriptionDto(
            id = REMOTE_ID,
            userId = USER_ID,
            name = name,
            amountMinor = 5999,
            currency = "TRY",
            categoryId = CATEGORY_ID,
            frequency = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAt = CREATED_AT,
            updatedAt = REMOTE_UPDATED_AT,
            deletedAt = deletedAt,
            version = version,
        )

        fun localPendingCategory() = CategoryEntity(
            id = REMOTE_ID,
            ownerId = USER_ID,
            workspaceId = null,
            scopeKey = "user:$USER_ID",
            name = "Yerel düzenleme",
            normalizedName = "yerel düzenleme",
            slug = null,
            typeCode = "EXPENSE",
            colorHex = "#123456",
            iconKey = null,
            isDefault = false,
            createdAtEpochMillis = Instant.parse(CREATED_AT).toEpochMilliseconds(),
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
                localUpdatedAtEpochMillis = RECEIVED_AT - 1,
                deletedAtEpochMillis = null,
                version = 1,
                baseVersion = 1,
                lastSyncError = null,
            ),
        )

        fun localPendingRecurring() = RecurringTransactionEntity(
            id = REMOTE_ID,
            ownerId = USER_ID,
            workspaceId = null,
            amountMinor = 50_000,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = CATEGORY_ID,
            description = "Yerel kural",
            paymentMethodCode = "CREDIT_CARD",
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAtEpochMillis = Instant.parse(CREATED_AT).toEpochMilliseconds(),
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
                localUpdatedAtEpochMillis = RECEIVED_AT - 1,
                deletedAtEpochMillis = null,
                version = 1,
                baseVersion = 1,
                lastSyncError = null,
            ),
        )

        fun localPendingSubscription() = SubscriptionEntity(
            id = REMOTE_ID,
            ownerId = USER_ID,
            workspaceId = null,
            name = "Yerel abonelik",
            amountMinor = 5999,
            currencyCode = "TRY",
            categoryId = CATEGORY_ID,
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAtEpochMillis = Instant.parse(CREATED_AT).toEpochMilliseconds(),
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
                localUpdatedAtEpochMillis = RECEIVED_AT - 1,
                deletedAtEpochMillis = null,
                version = 1,
                baseVersion = 1,
                lastSyncError = null,
            ),
        )
    }
}
