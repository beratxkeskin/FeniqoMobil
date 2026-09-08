package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCategoriesSyncTest {

    @Test
    fun defaultCategories_areMappedWithGlobalScope_nullOwner_andIsDefaultTrue() = runTest {
        val dao = RecordingRemoteSyncDao()
        val remote = FakeCoreRemoteWithDefaults()
        val sync = InitialRemoteSync(remote, dao) { RECEIVED_AT }

        val result = sync.pullFor(EntityId(USER_ID))

        assertEquals(3, result.categoryCount)

        val expenseDefault = dao.categories.single { it.id == DEFAULT_EXPENSE_ID }
        assertTrue(expenseDefault.isDefault)
        assertNull(expenseDefault.ownerId)
        assertEquals("system", expenseDefault.scopeKey)
        assertEquals("Market", expenseDefault.name)
        assertEquals("market", expenseDefault.slug)
        assertEquals("EXPENSE", expenseDefault.typeCode)
        assertEquals("#EF4444", expenseDefault.colorHex)
        assertEquals("shopping-cart", expenseDefault.iconKey)
        assertEquals("SYNCED", expenseDefault.sync.syncStatus)

        val incomeDefault = dao.categories.single { it.id == DEFAULT_INCOME_ID }
        assertTrue(incomeDefault.isDefault)
        assertNull(incomeDefault.ownerId)
        assertEquals("system", incomeDefault.scopeKey)
        assertEquals("Maaş", incomeDefault.name)
        assertEquals("maas", incomeDefault.slug)
        assertEquals("INCOME", incomeDefault.typeCode)
        assertEquals("#10B981", incomeDefault.colorHex)
        assertEquals("briefcase", incomeDefault.iconKey)
        assertEquals("SYNCED", incomeDefault.sync.syncStatus)

        val customCat = dao.categories.single { it.id == CUSTOM_CATEGORY_ID }
        assertEquals(false, customCat.isDefault)
        assertEquals(USER_ID, customCat.ownerId)
        assertEquals("user:$USER_ID", customCat.scopeKey)
        assertNull(customCat.slug)
        assertEquals("SYNCED", customCat.sync.syncStatus)
    }

    @Test
    fun initialPull_storesTombstonedDefaultCategory_withDeletedAtEpochMillis() = runTest {
        val dao = RecordingRemoteSyncDao()
        val remote = FakeCoreRemoteWithDefaults(
            categoryItems = listOf(
                CategoryDto(
                    id = DEFAULT_EXPENSE_ID,
                    userId = null,
                    workspaceId = null,
                    name = "Eski Kategori",
                    slug = "eski-kategori",
                    type = "expense",
                    color = "#6B7280",
                    icon = "help-circle",
                    isDefault = true,
                    createdAt = REMOTE_CREATED_AT,
                    updatedAt = REMOTE_UPDATED_AT,
                    deletedAt = REMOTE_DELETED_AT,
                    version = 2,
                ),
            ),
        )
        val sync = InitialRemoteSync(remote, dao) { RECEIVED_AT }

        val result = sync.pullFor(EntityId(USER_ID))

        assertEquals(1, result.categoryCount)
        val tombstoned = dao.categories.single { it.id == DEFAULT_EXPENSE_ID }
        assertTrue(tombstoned.isDefault)
        assertNull(tombstoned.ownerId)
        assertEquals("system", tombstoned.scopeKey)
        assertEquals(kotlin.time.Instant.parse(REMOTE_DELETED_AT).toEpochMilliseconds(), tombstoned.sync.deletedAtEpochMillis)
        assertEquals("SYNCED", tombstoned.sync.syncStatus)
    }

    @Test
    fun incrementalPull_appliesNewDefaultCategory_andAdvancesCursor() = runTest {
        val cursors = mutableMapOf("CATEGORY" to SyncCursorEntity("CATEGORY", 1_000L, "cursor-prev-id"))
        val dao = RecordingRemoteSyncDao(cursorMap = cursors)
        val syncStateDao = object : com.feniqo.mobile.data.local.dao.SyncStateDao {
            override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]
            override suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity> =
                cursors.filterKeys { it.startsWith("WORKSPACE_MEMBER:") }.values.toList()
            override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
            override suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
            override suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity> = emptyList()
            override suspend fun getAllConflicts(): List<SyncConflictEntity> = emptyList()
            override suspend fun upsertCursor(cursor: SyncCursorEntity) { cursors[cursor.entityTypeCode] = cursor }
            override fun observeConflicts(): kotlinx.coroutines.flow.Flow<List<SyncConflictEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun observeConflictCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
            override suspend fun getConflictCount(): Int = 0
            override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
            override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
            override fun observeLastSuccessfulSyncAt(userId: String): kotlinx.coroutines.flow.Flow<Long?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun getUserState(userId: String): com.feniqo.mobile.data.local.entity.SyncUserStateEntity? = null
            override suspend fun upsertUserState(state: com.feniqo.mobile.data.local.entity.SyncUserStateEntity) = Unit
        }
        val remote = FakeCoreRemoteWithDefaults(
            categoryItems = listOf(
                CategoryDto(
                    id = DEFAULT_EXPENSE_ID,
                    userId = null,
                    workspaceId = null,
                    name = "Market",
                    slug = "market",
                    type = "expense",
                    color = "#EF4444",
                    icon = "shopping-cart",
                    isDefault = true,
                    createdAt = REMOTE_CREATED_AT,
                    updatedAt = REMOTE_UPDATED_AT,
                    version = 2,
                ),
            ),
        )
        val sync = IncrementalRemoteSync(remote, dao, syncStateDao) { RECEIVED_AT }

        val result = sync.pullFor(EntityId(USER_ID))

        assertEquals(2, result.appliedCount)
        assertEquals(0, result.conflictCount)
        val applied = dao.categories.single { it.id == DEFAULT_EXPENSE_ID }
        assertTrue(applied.isDefault)
        assertNull(applied.ownerId)
        assertEquals("system", applied.scopeKey)
        assertEquals(DEFAULT_EXPENSE_ID, cursors["CATEGORY"]?.entityId)
    }

    private class RecordingRemoteSyncDao(
        private val cursorMap: MutableMap<String, SyncCursorEntity>? = null,
    ) : RemoteSyncDao {
        var profile: UserProfileEntity? = null
        var categories: List<CategoryEntity> = emptyList()
        var transactions: List<TransactionEntity> = emptyList()
        var cursors: List<SyncCursorEntity> = emptyList()

        override suspend fun getProfileRow(id: String): UserProfileEntity? = profile
        override suspend fun getCategoryRow(id: String): CategoryEntity? = categories.firstOrNull { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = transactions.firstOrNull { it.id == id }
        override suspend fun getRecurringTransactionRow(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun getSubscriptionRow(id: String): com.feniqo.mobile.data.local.entity.SubscriptionEntity? = null
        override suspend fun getGoalRow(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
        override suspend fun getWorkspaceRow(id: String): com.feniqo.mobile.data.local.entity.WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) { profile = entity }
        override suspend fun upsertWorkspaceRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity>) = Unit
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) { categories = entities }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) { transactions = entities }
        override suspend fun upsertRecurringTransactionRows(entities: List<com.feniqo.mobile.data.local.entity.RecurringTransactionEntity>) = Unit
        override suspend fun upsertSubscriptionRows(entities: List<com.feniqo.mobile.data.local.entity.SubscriptionEntity>) = Unit
        override suspend fun upsertGoalRows(entities: List<com.feniqo.mobile.data.local.entity.GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<com.feniqo.mobile.data.local.entity.GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<com.feniqo.mobile.data.local.entity.DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) = error("Test kapsamı dışı")
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) {
            this.cursors = cursors
            cursorMap?.let { map -> cursors.forEach { map[it.entityTypeCode] = it } }
        }
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 0
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int = 0
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
        override suspend fun getActiveWorkspaceTailOperation(workspaceId: String): com.feniqo.mobile.data.local.entity.SyncOperationEntity? = null
        override suspend fun markWorkspaceConflict(entityId: String, error: String): Int = 0
        override suspend fun getAllWorkspaceOperations(workspaceId: String): List<com.feniqo.mobile.data.local.entity.SyncOperationEntity> = emptyList()
        override suspend fun deleteSpecificWorkspaceOperations(workspaceId: String, operationIds: List<String>): Int = 0
        override suspend fun rebaseWorkspaceForRetry(workspaceId: String, syncStatus: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun resetWorkspaceConflictOperation(operationId: String, operationTypeCode: String, payloadJson: String?, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun getConflictRow(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
    }




    private class FakeCoreRemoteWithDefaults(
        private val categoryItems: List<CategoryDto> = listOf(
            CategoryDto(
                id = DEFAULT_EXPENSE_ID,
                userId = null,
                workspaceId = null,
                name = "Market",
                slug = "market",
                type = "expense",
                color = "#EF4444",
                icon = "shopping-cart",
                isDefault = true,
                createdAt = REMOTE_CREATED_AT,
                updatedAt = REMOTE_UPDATED_AT,
                version = 1,
            ),
            CategoryDto(
                id = DEFAULT_INCOME_ID,
                userId = null,
                workspaceId = null,
                name = "Maaş",
                slug = "maas",
                type = "income",
                color = "#10B981",
                icon = "briefcase",
                isDefault = true,
                createdAt = REMOTE_CREATED_AT,
                updatedAt = REMOTE_UPDATED_AT,
                version = 1,
            ),
            CategoryDto(
                id = CUSTOM_CATEGORY_ID,
                userId = USER_ID,
                workspaceId = null,
                name = "Özel Hobiler",
                slug = null,
                type = "expense",
                color = "#EC4899",
                icon = "film",
                isDefault = false,
                createdAt = REMOTE_CREATED_AT,
                updatedAt = REMOTE_UPDATED_AT,
                version = 1,
            ),
        ),
    ) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String) = ProfileDto(
            id = userId, email = "test@feniqo.app", createdAt = REMOTE_CREATED_AT,
            updatedAt = REMOTE_UPDATED_AT, version = 1,
        )

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
            val items = if (query.page.pageIndex == 0) categoryItems else emptyList()
            return RemotePage(items, query.page, totalCount = categoryItems.size.toLong())
        }

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> {
            return RemotePage(emptyList(), query.page, totalCount = 0)
        }

        override suspend fun fetchRecurringTransactions(query: com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.RecurringTransactionDto> {
            return RemotePage(emptyList(), query.page, totalCount = 0)
        }

        override suspend fun fetchSubscriptions(query: com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.SubscriptionDto> {
            return RemotePage(emptyList(), query.page, totalCount = 0)
        }

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("Kapsam dışı")
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.GoalContributionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery): RemotePage<com.feniqo.mobile.data.remote.dto.DebtPaymentDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = error("Kapsam dışı")


        override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> = error("Kapsam dışı")
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> = error("Kapsam dışı")
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = error("Kapsam dışı")
        override suspend fun upsertProfile(dto: ProfileDto) = error("Kapsam dışı")
        override suspend fun upsertCategory(dto: CategoryDto) = error("Kapsam dışı")
        override suspend fun upsertTransaction(dto: TransactionDto) = error("Kapsam dışı")
        override suspend fun upsertBudget(dto: BudgetDto) = error("Kapsam dışı")
        override suspend fun upsertTag(dto: TagDto) = error("Kapsam dışı")
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = error("Kapsam dışı")
    }

    private companion object {
        const val USER_ID = "user-1"
        const val DEFAULT_EXPENSE_ID = "11111111-1111-4111-8111-111111111112"
        const val DEFAULT_INCOME_ID = "11111111-1111-4111-8111-111111111101"
        const val CUSTOM_CATEGORY_ID = "custom-cat-1"
        const val REMOTE_CREATED_AT = "2026-08-16T08:00:00Z"
        const val REMOTE_UPDATED_AT = "2026-08-16T09:00:00Z"
        const val REMOTE_DELETED_AT = "2026-08-16T10:00:00Z"
        const val RECEIVED_AT = 1_785_767_400_000L
    }
}
