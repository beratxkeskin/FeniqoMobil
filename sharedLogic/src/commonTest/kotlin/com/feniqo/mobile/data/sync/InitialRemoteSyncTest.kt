package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
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
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitialRemoteSyncTest {
    @Test
    fun personal_pages_are_mapped_and_applied_as_a_synced_snapshot() = runTest {
        val dao = RecordingRemoteSyncDao()
        val sync = InitialRemoteSync(FakeCoreRemote(), dao) { RECEIVED_AT }

        val result = sync.pullFor(EntityId(USER_ID))

        assertEquals(2, result.categoryCount)
        assertEquals(2, result.transactionCount)
        assertEquals(listOf(0), FakeCoreRemote.categoryPages)
        assertEquals(listOf(0), FakeCoreRemote.transactionPages)
        assertEquals("SYNCED", dao.profile?.sync?.syncStatus)
        assertEquals(RECEIVED_AT, dao.profile?.sync?.localUpdatedAtEpochMillis)
        assertEquals(7L, dao.categories.single { it.id == CATEGORY_2 }.sync.version)
        assertEquals(Instant.parse(REMOTE_UPDATED_AT).toEpochMilliseconds(), dao.categories.single { it.id == CATEGORY_2 }.sync.updatedAtEpochMillis)
        assertEquals(Instant.parse(REMOTE_DELETED_AT).toEpochMilliseconds(), dao.categories.single { it.id == CATEGORY_2 }.sync.deletedAtEpochMillis)
        assertNull(dao.transactions.first().sync.deletedAtEpochMillis)
        assertEquals(setOf("PROFILE", "CATEGORY", "TRANSACTION"), dao.cursors.map { it.entityTypeCode }.toSet())
    }

    private class RecordingRemoteSyncDao : RemoteSyncDao {
        var profile: UserProfileEntity? = null
        var categories: List<CategoryEntity> = emptyList()
        var transactions: List<TransactionEntity> = emptyList()
        var cursors: List<SyncCursorEntity> = emptyList()

        override suspend fun getProfileRow(id: String): UserProfileEntity? = profile
        override suspend fun getCategoryRow(id: String): CategoryEntity? = categories.firstOrNull { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = transactions.firstOrNull { it.id == id }
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun upsertProfileRow(entity: UserProfileEntity) { profile = entity }
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) { categories = entities }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) { transactions = entities }
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) = error("Test kapsamı dışı")
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) { this.cursors = cursors }
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 0
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }

    private class FakeCoreRemote : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String) = ProfileDto(
            id = userId, email = "test@feniqo.app", createdAt = REMOTE_CREATED_AT,
            updatedAt = REMOTE_UPDATED_AT, version = 3,
        )

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
            assertEquals(RemoteWorkspaceScope.Personal, query.workspaceScope)
            categoryPages += query.page.pageIndex
            val items = if (query.page.pageIndex == 0) {
                listOf(category(CATEGORY_1), category(CATEGORY_2, REMOTE_DELETED_AT, 7))
            } else emptyList()
            return RemotePage(items, query.page, totalCount = 2)
        }

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> {
            assertEquals(RemoteWorkspaceScope.Personal, query.workspaceScope)
            transactionPages += query.page.pageIndex
            val items = if (query.page.pageIndex == 0) {
                listOf(transaction("transaction-1"), transaction("transaction-2"))
            } else emptyList()
            return RemotePage(items, query.page, totalCount = 2)
        }

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("Kapsam dışı")
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

        companion object {
            val categoryPages = mutableListOf<Int>()
            val transactionPages = mutableListOf<Int>()
        }
    }

    private companion object {
        const val USER_ID = "user-1"
        const val CATEGORY_1 = "category-1"
        const val CATEGORY_2 = "category-2"
        const val REMOTE_CREATED_AT = "2026-08-14T08:00:00Z"
        const val REMOTE_UPDATED_AT = "2026-08-14T09:00:00Z"
        const val REMOTE_DELETED_AT = "2026-08-14T10:00:00Z"
        const val RECEIVED_AT = 1_785_767_400_000L
        fun category(id: String, deletedAt: String? = null, version: Long = 4) = CategoryDto(
            id = id, userId = USER_ID, name = "Market $id", type = "expense", color = "#0A7A55",
            createdAt = REMOTE_CREATED_AT, updatedAt = REMOTE_UPDATED_AT,
            deletedAt = deletedAt, version = version,
        )

        fun transaction(id: String) = TransactionDto(
            id = id, userId = USER_ID, amountMinor = 12_500, type = "expense",
            categoryId = CATEGORY_1, paymentMethod = "cash", transactionDate = "2026-08-14",
            createdAt = REMOTE_CREATED_AT, updatedAt = REMOTE_UPDATED_AT, version = 4,
        )
    }
}
