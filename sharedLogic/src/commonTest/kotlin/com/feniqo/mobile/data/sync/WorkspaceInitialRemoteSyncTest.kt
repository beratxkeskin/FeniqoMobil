package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceInitialRemoteSyncTest {

    private fun sampleWorkspaceDto(
        id: String = "ws-1",
        name: String = "Ana Bütçe",
        normalizedName: String = "ana bütçe",
        ownerId: String = "user-1",
        typeCode: String = "personal",
        currencyCode: String = "TRY",
        description: String? = null,
        createdAt: String = "2026-03-01T10:00:00Z",
        updatedAt: String = "2026-03-01T10:00:00Z",
        deletedAt: String? = null,
        version: Long = 1L,
    ): WorkspaceDto = WorkspaceDto(
        id = id,
        name = name,
        normalizedName = normalizedName,
        ownerId = ownerId,
        typeCode = typeCode,
        currencyCode = currencyCode,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    private fun sampleMemberDto(
        workspaceId: String = "ws-1",
        userId: String = "user-1",
        roleCode: String = "OWNER",
        joinedAt: String = "2026-03-01T10:00:00Z",
        updatedAt: String = "2026-03-01T10:00:00Z",
        deletedAt: String? = null,
        version: Long = 1L,
    ): WorkspaceMemberDto = WorkspaceMemberDto(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = roleCode,
        joinedAt = joinedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    private class RecordingRemoteSyncDao : RemoteSyncDao {
        var appliedWorkspaces: List<WorkspaceEntity>? = null
        var appliedMembers: List<WorkspaceMemberEntity>? = null
        var appliedCursors: List<SyncCursorEntity>? = null
        var applyCallCount = 0

        override suspend fun applyWorkspaceSnapshot(
            workspaces: List<WorkspaceEntity>,
            members: List<WorkspaceMemberEntity>,
            cursors: List<SyncCursorEntity>,
        ) {
            applyCallCount++
            appliedWorkspaces = workspaces
            appliedMembers = members
            appliedCursors = cursors
        }

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = null
        override suspend fun getSubscriptionRow(id: String): SubscriptionEntity? = null
        override suspend fun getGoalRow(id: String): GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): DebtPaymentEntity? = null
        override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) = Unit
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>) = Unit
        override suspend fun upsertSubscriptionRows(entities: List<SubscriptionEntity>) = Unit
        override suspend fun upsertGoalRows(entities: List<GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<DebtPaymentEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) = Unit
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) = Unit
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
    }

    private class FakeCoreRemoteDataSource(
        var workspacePages: List<RemotePage<WorkspaceDto>> = emptyList(),
        var memberPagesByWorkspaceId: Map<String, List<RemotePage<WorkspaceMemberDto>>> = emptyMap(),
        var fetchWorkspacesError: Throwable? = null,
        var fetchMembersError: Throwable? = null,
    ) : CoreRemoteDataSource {
        val requestedWorkspacePageIndices = mutableListOf<Int>()
        val requestedMemberQueries = mutableListOf<Pair<String?, Int>>()

        override suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
            fetchWorkspacesError?.let { throw it }
            val pageIndex = query.page.pageIndex
            requestedWorkspacePageIndices += pageIndex
            return workspacePages.getOrElse(pageIndex) {
                RemotePage(items = emptyList(), request = query.page, totalCount = 0)
            }
        }

        override suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> {
            fetchMembersError?.let { throw it }
            val wsId = query.workspaceId?.value
            val pageIndex = query.page.pageIndex
            requestedMemberQueries += (wsId to pageIndex)
            val pages = memberPagesByWorkspaceId[wsId] ?: emptyList()
            return pages.getOrElse(pageIndex) {
                RemotePage(items = emptyList(), request = query.page, totalCount = 0)
            }
        }

        override suspend fun fetchProfile(userId: String): ProfileDto? = null
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = error("N/A")
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = error("N/A")
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("N/A")
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> = error("N/A")
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> = error("N/A")
        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = error("N/A")
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> = error("N/A")
        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = error("N/A")
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> = error("N/A")
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = error("N/A")
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = error("N/A")
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    @Test
    fun multi_page_workspaces_and_members_are_fetched_and_applied_in_a_single_atomic_dao_call() = runTest {
        val ws1 = sampleWorkspaceDto(id = "ws-1", name = "Workspace 1")
        val ws2 = sampleWorkspaceDto(id = "ws-2", name = "Workspace 2")

        val m1Page0 = sampleMemberDto(workspaceId = "ws-1", userId = "user-1", roleCode = "OWNER")
        val m1Page1 = sampleMemberDto(workspaceId = "ws-1", userId = "user-2", roleCode = "EDITOR")
        val m2Page0 = sampleMemberDto(workspaceId = "ws-2", userId = "user-3", roleCode = "OWNER")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(ws1), request = RemotePageRequest(pageIndex = 0, pageSize = 1), totalCount = 2),
                RemotePage(items = listOf(ws2), request = RemotePageRequest(pageIndex = 1, pageSize = 1), totalCount = 2),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(m1Page0), request = RemotePageRequest(pageIndex = 0, pageSize = 1), totalCount = 2),
                    RemotePage(items = listOf(m1Page1), request = RemotePageRequest(pageIndex = 1, pageSize = 1), totalCount = 2),
                ),
                "ws-2" to listOf(
                    RemotePage(items = listOf(m2Page0), request = RemotePageRequest(pageIndex = 0, pageSize = 1), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 5_000L }

        val result = coordinator.pull()

        assertEquals(2, result.workspaceCount)
        assertEquals(3, result.memberCount)
        assertEquals(1, dao.applyCallCount)

        val appliedWorkspaces = dao.appliedWorkspaces
        assertNotNull(appliedWorkspaces)
        assertEquals(2, appliedWorkspaces.size)
        assertEquals(listOf("ws-1", "ws-2"), appliedWorkspaces.map { it.id })
        assertEquals(5_000L, appliedWorkspaces[0].sync.localUpdatedAtEpochMillis)

        val appliedMembers = dao.appliedMembers
        assertNotNull(appliedMembers)
        assertEquals(3, appliedMembers.size)
        assertEquals(listOf("user-1", "user-2", "user-3"), appliedMembers.map { it.userId })

        // Verify cursors derived and passed to DAO (including bootstrap complete marker)
        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        assertEquals(4, appliedCursors.size) // 1 marker + 1 workspace cursor + 2 member cursors (ws-1, ws-2)

        val marker = appliedCursors.find { it.entityTypeCode == "WORKSPACE_BOOTSTRAP_COMPLETE" }
        assertNotNull(marker)
        assertEquals("COMPLETED", marker.entityId)
        assertEquals(5_000L, marker.updatedAtEpochMillis)

        val wsCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE" }
        assertNotNull(wsCursor)
        assertEquals("ws-2", wsCursor.entityId) // last workspace

        val ws1MemberCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-1" }
        assertNotNull(ws1MemberCursor)
        assertEquals("user-2", ws1MemberCursor.entityId) // last member for ws-1

        val ws2MemberCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-2" }
        assertNotNull(ws2MemberCursor)
        assertEquals("user-3", ws2MemberCursor.entityId) // last member for ws-2

        // Verify isolation between workspace member queries
        assertEquals(listOf(0, 1), remote.requestedWorkspacePageIndices)
        assertEquals(
            listOf<Pair<String?, Int>>("ws-1" to 0, "ws-1" to 1, "ws-2" to 0),
            remote.requestedMemberQueries,
        )
    }

    @Test
    fun empty_workspace_and_member_pull_produces_only_completion_marker() = runTest {
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
            ),
        )
        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 5_500L }

        val result = coordinator.pull()

        assertEquals(0, result.workspaceCount)
        assertEquals(0, result.memberCount)
        assertEquals(1, dao.applyCallCount)
        assertEquals(1, dao.appliedCursors?.size)

        val marker = dao.appliedCursors?.first()
        assertNotNull(marker)
        assertEquals("WORKSPACE_BOOTSTRAP_COMPLETE", marker.entityTypeCode)
        assertEquals("COMPLETED", marker.entityId)
        assertEquals(5_500L, marker.updatedAtEpochMillis)
    }

    @Test
    fun live_workspace_with_empty_members_fails_closed_and_does_not_call_dao() = runTest {
        val liveWs = sampleWorkspaceDto(id = "ws-live-1", deletedAt = null)
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(liveWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-live-1" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )
        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 5_600L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull()
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
        assertNull(dao.appliedCursors)
    }

    @Test
    fun tombstone_workspaces_and_members_are_passed_to_dao_snapshot() = runTest {
        val deletedWs = sampleWorkspaceDto(id = "ws-del", deletedAt = "2026-03-02T12:00:00Z")
        val deletedMember = sampleMemberDto(workspaceId = "ws-del", userId = "user-del", deletedAt = "2026-03-02T12:00:00Z")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(deletedWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-del" to listOf(
                    RemotePage(items = listOf(deletedMember), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 6_000L }

        val result = coordinator.pull()

        assertEquals(1, result.workspaceCount)
        assertEquals(1, result.memberCount)
        assertEquals(1, dao.applyCallCount)

        assertNotNull(dao.appliedWorkspaces?.first()?.sync?.deletedAtEpochMillis)
        assertNotNull(dao.appliedMembers?.first()?.sync?.deletedAtEpochMillis)
    }

    @Test
    fun fetch_error_does_not_call_dao_apply_snapshot() = runTest {
        val remote = FakeCoreRemoteDataSource(
            fetchWorkspacesError = IllegalStateException("Network timeout"),
        )
        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 7_000L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull()
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
    }

    @Test
    fun member_fetch_error_does_not_call_dao_apply_snapshot() = runTest {
        val validWs = sampleWorkspaceDto(id = "ws-1", name = "Valid WS")
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(validWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            fetchMembersError = IllegalStateException("Member network timeout"),
        )
        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 7_500L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull()
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
    }

    @Test
    fun mapping_error_does_not_call_dao_apply_snapshot() = runTest {
        // Invalid workspace currency code triggers RemoteMappingException in mapper
        val invalidWs = sampleWorkspaceDto(id = "ws-bad", currencyCode = "INVALID_CURRENCY")
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(invalidWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-bad" to listOf(
                    RemotePage(items = listOf(sampleMemberDto(workspaceId = "ws-bad", userId = "u-1")), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )
        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceInitialRemoteSync(remote, dao) { 8_000L }

        assertFailsWith<RemoteMappingException> {
            coordinator.pull()
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
    }
}
