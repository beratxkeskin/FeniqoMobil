package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class IncrementalRemoteSyncTest {

    @Test
    fun pulls_after_stored_cursor_and_advances_only_after_room_write() = runTest {
        val cursors = mutableMapOf(CATEGORY to storedCursor())
        val dao = RecordingRemoteSyncDao(cursors = cursors)
        val remote = FakeRemote(remoteCategory(version = 2))

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
        val cursors = mutableMapOf(CATEGORY to storedCursor())
        val local = localPendingCategory()
        val dao = RecordingRemoteSyncDao(category = local, cursors = cursors)

        val result = IncrementalRemoteSync(
            remote = FakeRemote(remoteCategory(version = 3)),
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

    private class FakeRemote(
        private val category: CategoryDto,
    ) : CoreRemoteDataSource {
        var receivedCategoryCursor: com.feniqo.mobile.data.remote.core.RemoteSyncCursor? = null

        override suspend fun fetchProfile(userId: String): ProfileDto? = null

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
            receivedCategoryCursor = query.updatedAfter
            val items = if (query.page.pageIndex == 0) listOf(category) else emptyList()
            return RemotePage(items, query.page, totalCount = 1)
        }

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("Test kapsamı dışı")
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
        override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
        override suspend fun upsertCursor(cursor: SyncCursorEntity) { cursors[cursor.entityTypeCode] = cursor }
        override fun observeConflicts(): Flow<List<SyncConflictEntity>> = flowOf(emptyList())
        override fun observeConflictCount(): Flow<Int> = flowOf(0)
        override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
    }

    private class RecordingRemoteSyncDao(
        var category: CategoryEntity? = null,
        private val cursors: MutableMap<String, SyncCursorEntity>,
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = category?.takeIf { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? =
            if (category?.id == entityId) "operation-1" else null
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) { category = entities.single() }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
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
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }

    private companion object {
        const val CATEGORY = "CATEGORY"
        const val USER_ID = "20000000-0000-0000-0000-000000000001"
        const val STORED_ID = "10000000-0000-0000-0000-000000000001"
        const val REMOTE_ID = "10000000-0000-0000-0000-000000000002"
        const val STORED_UPDATED_AT = "2026-08-14T09:00:00Z"
        const val REMOTE_UPDATED_AT = "2026-08-14T10:00:00Z"
        const val CREATED_AT = "2026-08-10T10:00:00Z"
        const val RECEIVED_AT = 1_765_707_200_000L

        fun storedCursor() = SyncCursorEntity(
            entityTypeCode = CATEGORY,
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
    }
}
