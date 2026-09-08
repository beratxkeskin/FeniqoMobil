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
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberSyncCursor
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

class WorkspaceIncrementalRemoteSyncTest {

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

    private open class RecordingRemoteSyncDao : RemoteSyncDao {
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
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity> = emptyList()
        var knownLiveWorkspaceIds: List<String> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = knownLiveWorkspaceIds
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        var appliedPlan: com.feniqo.mobile.data.local.dao.WorkspaceIncrementalPlan? = null
        var localWorkspaceRows: MutableMap<String, WorkspaceEntity> = mutableMapOf()
        var activeTailOperations: MutableMap<String, com.feniqo.mobile.data.local.entity.SyncOperationEntity> = mutableMapOf()
        var markedConflicts: MutableMap<String, String> = mutableMapOf()

        override suspend fun applyWorkspaceIncrementalPlan(plan: com.feniqo.mobile.data.local.dao.WorkspaceIncrementalPlan) {
            applyCallCount++
            appliedPlan = plan
            appliedWorkspaces = plan.applyItems.map { it.entity }
            appliedMembers = plan.memberRows
            appliedCursors = plan.cursorsToPersist
            plan.conflictItems.forEach {
                markedConflicts[it.conflict.entityId] = RemoteSyncDao.CONFLICT_ERROR
            }
        }

        override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = localWorkspaceRows[id]

        override suspend fun getActiveWorkspaceTailOperation(workspaceId: String): com.feniqo.mobile.data.local.entity.SyncOperationEntity? =
            activeTailOperations[workspaceId]

        override suspend fun markWorkspaceConflict(entityId: String, error: String): Int {
            markedConflicts[entityId] = error
            return 1
        }
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
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
        override suspend fun getAllWorkspaceOperations(workspaceId: String): List<com.feniqo.mobile.data.local.entity.SyncOperationEntity> = emptyList()
        override suspend fun deleteSpecificWorkspaceOperations(workspaceId: String, operationIds: List<String>): Int = 0
        override suspend fun rebaseWorkspaceForRetry(workspaceId: String, syncStatus: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun resetWorkspaceConflictOperation(operationId: String, operationTypeCode: String, payloadJson: String?, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun getConflictRow(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
    }

    private class FakeSyncStateDao(
        initialCursors: Map<String, SyncCursorEntity> = emptyMap(),
    ) : com.feniqo.mobile.data.local.dao.SyncStateDao {
        val cursors = initialCursors.toMutableMap()

        override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]

        override suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity> =
            cursors.filterKeys { it.startsWith("WORKSPACE_MEMBER:") }.values.toList()

        override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
        override suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
        override suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity> = emptyList()
        override suspend fun getAllConflicts(): List<SyncConflictEntity> = emptyList()
        override suspend fun upsertCursor(cursor: SyncCursorEntity) {
            cursors[cursor.entityTypeCode] = cursor
        }
        override fun observeConflicts(): kotlinx.coroutines.flow.Flow<List<SyncConflictEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override fun observeConflictCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getConflictCount(): Int = 0
        override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
        override fun observeLastSuccessfulSyncAt(userId: String): kotlinx.coroutines.flow.Flow<Long?> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getUserState(userId: String): com.feniqo.mobile.data.local.entity.SyncUserStateEntity? = null
        override suspend fun upsertUserState(state: com.feniqo.mobile.data.local.entity.SyncUserStateEntity) = Unit
    }

    private class FakeCoreRemoteDataSource(
        var workspacePages: List<RemotePage<WorkspaceDto>> = emptyList(),
        var memberPagesByWorkspaceId: Map<String, List<RemotePage<WorkspaceMemberDto>>> = emptyMap(),
        var fetchWorkspacesError: Throwable? = null,
        var fetchMembersError: Throwable? = null,
    ) : CoreRemoteDataSource {
        val requestedWorkspaceQueries = mutableListOf<WorkspaceRemoteQuery>()
        val requestedMemberQueries = mutableListOf<WorkspaceMemberRemoteQuery>()

        override suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
            fetchWorkspacesError?.let { throw it }
            requestedWorkspaceQueries += query
            val pageIndex = query.page.pageIndex
            return workspacePages.getOrElse(pageIndex) {
                RemotePage(items = emptyList(), request = query.page, totalCount = 0)
            }
        }

        override suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> {
            fetchMembersError?.let { throw it }
            requestedMemberQueries += query
            val wsId = query.workspaceId?.value
            val pageIndex = query.page.pageIndex
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
    fun multi_page_workspaces_and_members_are_fetched_and_applied_with_correct_next_cursors() = runTest {
        val initialWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T00:00:00Z", entityId = "ws-0")
        val initialMemberCursors = mapOf(
            "ws-1" to WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T00:00:00Z", workspaceId = "ws-1", userId = "user-0"),
        )

        val ws1 = sampleWorkspaceDto(id = "ws-1", name = "Workspace 1", updatedAt = "2026-03-01T10:00:00Z")
        val ws2 = sampleWorkspaceDto(id = "ws-2", name = "Workspace 2", updatedAt = "2026-03-01T12:00:00Z")

        val m1Page0 = sampleMemberDto(workspaceId = "ws-1", userId = "user-1", updatedAt = "2026-03-01T10:00:00Z")
        val m1Page1 = sampleMemberDto(workspaceId = "ws-1", userId = "user-2", updatedAt = "2026-03-01T11:00:00Z")
        val m2Page0 = sampleMemberDto(workspaceId = "ws-2", userId = "user-3", updatedAt = "2026-03-01T12:00:00Z")

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
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 5_000L }

        val result = coordinator.pull(
            workspaceCursor = initialWsCursor,
            memberCursors = initialMemberCursors,
        )

        assertEquals(2, result.appliedWorkspacesCount)
        assertEquals(3, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        // Workspace cursor updated from last workspace record (ws-2)
        assertEquals(
            RemoteSyncCursor(updatedAt = "2026-03-01T12:00:00Z", entityId = "ws-2"),
            result.nextWorkspaceCursor,
        )

        // Member cursors updated per workspace
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T11:00:00Z", workspaceId = "ws-1", userId = "user-2"),
            result.nextMemberCursors["ws-1"],
        )
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T12:00:00Z", workspaceId = "ws-2", userId = "user-3"),
            result.nextMemberCursors["ws-2"],
        )

        val appliedWorkspaces = dao.appliedWorkspaces
        assertNotNull(appliedWorkspaces)
        assertEquals(2, appliedWorkspaces.size)
        assertEquals(listOf("ws-1", "ws-2"), appliedWorkspaces.map { it.id })
        assertEquals(5_000L, appliedWorkspaces[0].sync.localUpdatedAtEpochMillis)

        val appliedMembers = dao.appliedMembers
        assertNotNull(appliedMembers)
        assertEquals(3, appliedMembers.size)
        assertEquals(listOf("user-1", "user-2", "user-3"), appliedMembers.map { it.userId })

        // Verify cursors passed to DAO
        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        assertEquals(3, appliedCursors.size)
        assertEquals("ws-2", appliedCursors.find { it.entityTypeCode == "WORKSPACE" }?.entityId)
        assertEquals("user-2", appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-1" }?.entityId)
        assertEquals("user-3", appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-2" }?.entityId)

        // Verify query arguments passed to remote
        assertEquals(2, remote.requestedWorkspaceQueries.size)
        assertEquals(initialWsCursor, remote.requestedWorkspaceQueries[0].updatedAfter)
        assertEquals(initialWsCursor, remote.requestedWorkspaceQueries[1].updatedAfter)

        assertEquals(3, remote.requestedMemberQueries.size)
        assertEquals(EntityId("ws-1"), remote.requestedMemberQueries[0].workspaceId)
        assertEquals(initialMemberCursors["ws-1"], remote.requestedMemberQueries[0].updatedAfter)
        assertEquals(EntityId("ws-2"), remote.requestedMemberQueries[2].workspaceId)
        assertNull(remote.requestedMemberQueries[2].updatedAfter)
    }

    @Test
    fun parameterless_pull_reads_from_and_writes_to_sync_state_dao() = runTest {
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to SyncCursorEntity(
                    entityTypeCode = "WORKSPACE",
                    updatedAtEpochMillis = 1000L,
                    entityId = "ws-0",
                ),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity(
                    entityTypeCode = "WORKSPACE_MEMBER:ws-1",
                    updatedAtEpochMillis = 2000L,
                    entityId = "user-old",
                ),
            ),
        )

        val ws1 = sampleWorkspaceDto(id = "ws-1", updatedAt = "2026-03-01T12:00:00Z")
        val m1 = sampleMemberDto(workspaceId = "ws-1", userId = "user-new", updatedAt = "2026-03-01T12:30:00Z")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(ws1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(m1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 6_000L }

        val result = coordinator.pull()

        assertEquals(1, result.appliedWorkspacesCount)
        assertEquals(1, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        // Verify remote queries used initial stored cursors
        assertEquals(
            RemoteSyncCursor(updatedAt = kotlin.time.Instant.fromEpochMilliseconds(1000L).toString(), entityId = "ws-0"),
            remote.requestedWorkspaceQueries[0].updatedAfter,
        )
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = kotlin.time.Instant.fromEpochMilliseconds(2000L).toString(), workspaceId = "ws-1", userId = "user-old"),
            remote.requestedMemberQueries[0].updatedAfter,
        )

        // Verify DAO received updated cursors
        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        assertEquals(2, appliedCursors.size)
        assertEquals("ws-1", appliedCursors.find { it.entityTypeCode == "WORKSPACE" }?.entityId)
        assertEquals("user-new", appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-1" }?.entityId)
    }

    @Test
    fun tombstone_workspaces_and_members_are_passed_to_dao_snapshot_and_cursors_advanced() = runTest {
        val deletedWs = sampleWorkspaceDto(id = "ws-del", updatedAt = "2026-03-02T12:00:00Z", deletedAt = "2026-03-02T12:00:00Z")
        val deletedMember = sampleMemberDto(workspaceId = "ws-del", userId = "user-del", updatedAt = "2026-03-02T12:00:00Z", deletedAt = "2026-03-02T12:00:00Z")

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
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 6_000L }

        val result = coordinator.pull(workspaceCursor = null, memberCursors = emptyMap())

        assertEquals(1, result.appliedWorkspacesCount)
        assertEquals(1, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        assertNotNull(dao.appliedWorkspaces?.first()?.sync?.deletedAtEpochMillis)
        assertNotNull(dao.appliedMembers?.first()?.sync?.deletedAtEpochMillis)

        assertEquals(
            RemoteSyncCursor(updatedAt = "2026-03-02T12:00:00Z", entityId = "ws-del"),
            result.nextWorkspaceCursor,
        )
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = "2026-03-02T12:00:00Z", workspaceId = "ws-del", userId = "user-del"),
            result.nextMemberCursors["ws-del"],
        )
    }

    @Test
    fun empty_delta_page_does_not_advance_cursors() = runTest {
        val initialWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T00:00:00Z", entityId = "ws-0")
        val initialMemberCursors = mapOf(
            "ws-existing" to WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T00:00:00Z", workspaceId = "ws-existing", userId = "user-0"),
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 7_000L }

        val result = coordinator.pull(
            workspaceCursor = initialWsCursor,
            memberCursors = initialMemberCursors,
        )

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(0, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount) // dao called with empty lists
        assertEquals(0, dao.appliedWorkspaces?.size)
        assertEquals(0, dao.appliedMembers?.size)

        // Cursors remain unchanged
        assertEquals(initialWsCursor, result.nextWorkspaceCursor)
        assertEquals(initialMemberCursors, result.nextMemberCursors)
    }

    @Test
    fun fetch_error_does_not_call_dao_apply_snapshot() = runTest {
        val remote = FakeCoreRemoteDataSource(
            fetchWorkspacesError = IllegalStateException("Network timeout"),
        )
        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 7_000L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull(workspaceCursor = null)
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
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 7_500L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull(workspaceCursor = null)
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
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 8_000L }

        assertFailsWith<RemoteMappingException> {
            coordinator.pull(workspaceCursor = null)
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
    }

    @Test
    fun different_workspace_member_cursors_do_not_cross_contaminate() = runTest {
        val cursorWs1 = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-1", userId = "u1")
        val cursorWs2 = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T15:00:00Z", workspaceId = "ws-2", userId = "u2")

        val ws1 = sampleWorkspaceDto(id = "ws-1", updatedAt = "2026-03-01T10:00:00Z")
        val ws2 = sampleWorkspaceDto(id = "ws-2", updatedAt = "2026-03-01T15:00:00Z")

        val m1 = sampleMemberDto(workspaceId = "ws-1", userId = "u1-new", updatedAt = "2026-03-01T11:00:00Z")
        // ws-2 has no member deltas
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(ws1, ws2), request = RemotePageRequest(pageIndex = 0), totalCount = 2),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(m1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
                "ws-2" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 9_000L }

        val result = coordinator.pull(
            workspaceCursor = null,
            memberCursors = mapOf("ws-1" to cursorWs1, "ws-2" to cursorWs2),
        )

        // ws-1 member cursor advanced to m1
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T11:00:00Z", workspaceId = "ws-1", userId = "u1-new"),
            result.nextMemberCursors["ws-1"],
        )
        // ws-2 member cursor retained its original cursor since page was empty
        assertEquals(
            cursorWs2,
            result.nextMemberCursors["ws-2"],
        )

        // Verify remote queries used distinct member cursors
        val ws1MemberQuery = remote.requestedMemberQueries.find { it.workspaceId?.value == "ws-1" }
        val ws2MemberQuery = remote.requestedMemberQueries.find { it.workspaceId?.value == "ws-2" }

        assertEquals(cursorWs1, ws1MemberQuery?.updatedAfter)
        assertEquals(cursorWs2, ws2MemberQuery?.updatedAfter)
    }

    @Test
    fun empty_workspace_delta_still_pulls_member_deltas_for_existing_member_cursors() = runTest {
        val cursorWs1 = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-1", userId = "u1")
        val newMember = sampleMemberDto(workspaceId = "ws-1", userId = "u2", roleCode = "VIEWER", updatedAt = "2026-03-01T14:00:00Z", deletedAt = "2026-03-01T14:00:00Z")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(newMember), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 10_000L }

        val result = coordinator.pull(
            workspaceCursor = RemoteSyncCursor(updatedAt = "2026-03-01T09:00:00Z", entityId = "ws-1"),
            memberCursors = mapOf("ws-1" to cursorWs1),
        )

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(1, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        // Workspace cursor stays as initial
        assertEquals(
            RemoteSyncCursor(updatedAt = "2026-03-01T09:00:00Z", entityId = "ws-1"),
            result.nextWorkspaceCursor,
        )

        // Member cursor advances to newMember
        assertEquals(
            WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T14:00:00Z", workspaceId = "ws-1", userId = "u2"),
            result.nextMemberCursors["ws-1"],
        )

        val appliedMembers = dao.appliedMembers
        assertNotNull(appliedMembers)
        assertEquals(1, appliedMembers.size)
        assertEquals("u2", appliedMembers[0].userId)
        assertNotNull(appliedMembers[0].sync.deletedAtEpochMillis)
    }

    @Test
    fun workspace_present_in_both_metadata_delta_and_member_cursors_fetches_member_endpoint_only_once() = runTest {
        val cursorWs1 = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-1", userId = "u1")
        val ws1 = sampleWorkspaceDto(id = "ws-1", updatedAt = "2026-03-01T12:00:00Z")
        val m1 = sampleMemberDto(workspaceId = "ws-1", userId = "u1", roleCode = "EDITOR", updatedAt = "2026-03-01T12:00:00Z")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(ws1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(m1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 11_000L }

        val result = coordinator.pull(
            workspaceCursor = null,
            memberCursors = mapOf("ws-1" to cursorWs1),
        )

        assertEquals(1, result.appliedWorkspacesCount)
        assertEquals(1, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        // Exactly one fetch query for ws-1
        assertEquals(1, remote.requestedMemberQueries.size)
        assertEquals(EntityId("ws-1"), remote.requestedMemberQueries[0].workspaceId)
        assertEquals(cursorWs1, remote.requestedMemberQueries[0].updatedAfter)
    }

    @Test
    fun member_pull_queries_follow_deterministic_sorted_workspace_id_order() = runTest {
        // Provide cursors in non-sorted order (ws-z, ws-a, ws-m) and delta workspaces (ws-b)
        val cursorZ = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-z", userId = "u-z")
        val cursorA = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-a", userId = "u-a")
        val cursorM = WorkspaceMemberSyncCursor(updatedAt = "2026-03-01T10:00:00Z", workspaceId = "ws-m", userId = "u-m")

        val wsB = sampleWorkspaceDto(id = "ws-b")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(wsB), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-b" to listOf(
                    RemotePage(items = listOf(sampleMemberDto(workspaceId = "ws-b", userId = "u-b")), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
                "ws-a" to listOf(
                    RemotePage(items = listOf(sampleMemberDto(workspaceId = "ws-a", userId = "u-a")), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
                "ws-m" to listOf(
                    RemotePage(items = listOf(sampleMemberDto(workspaceId = "ws-m", userId = "u-m")), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
                "ws-z" to listOf(
                    RemotePage(items = listOf(sampleMemberDto(workspaceId = "ws-z", userId = "u-z")), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao()
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 12_000L }

        // LinkedHashMap with arbitrary insertion order
        val inputCursors = linkedMapOf(
            "ws-z" to cursorZ,
            "ws-a" to cursorA,
            "ws-m" to cursorM,
        )

        coordinator.pull(
            workspaceCursor = null,
            memberCursors = inputCursors,
        )

        // Union of {ws-b, ws-z, ws-a, ws-m} sorted is [ws-a, ws-b, ws-m, ws-z]
        val queriedWorkspaceIds = remote.requestedMemberQueries.mapNotNull { it.workspaceId?.value }
        assertEquals(listOf("ws-a", "ws-b", "ws-m", "ws-z"), queriedWorkspaceIds)
    }

    @Test
    fun local_live_workspace_without_member_cursor_triggers_full_member_pull_when_workspace_delta_is_empty() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-x")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                // No member cursor for ws-x
            ),
        )

        val m1 = sampleMemberDto(workspaceId = "ws-x", userId = "u-owner", roleCode = "OWNER", updatedAt = "2026-03-01T12:00:00Z")

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-x" to listOf(
                    RemotePage(items = listOf(m1), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.knownLiveWorkspaceIds = listOf("ws-x")
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 13_000L }

        val result = coordinator.pull()

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(1, result.appliedMembersCount)
        assertEquals(1, dao.applyCallCount)

        // Member endpoint called with updatedAfter = null
        assertEquals(1, remote.requestedMemberQueries.size)
        assertEquals(EntityId("ws-x"), remote.requestedMemberQueries[0].workspaceId)
        assertNull(remote.requestedMemberQueries[0].updatedAfter)

        // Stored cursor advanced for ws-x
        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        val memberCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE_MEMBER:ws-x" }
        assertNotNull(memberCursor)
        assertEquals("u-owner", memberCursor.entityId)
    }

    @Test
    fun live_workspace_missing_member_cursor_with_empty_remote_members_fails_closed() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-x")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
            ),
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-x" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.knownLiveWorkspaceIds = listOf("ws-x")
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 13_500L }

        assertFailsWith<IllegalStateException> {
            coordinator.pull()
        }

        assertEquals(0, dao.applyCallCount)
        assertNull(dao.appliedWorkspaces)
        assertNull(dao.appliedMembers)
        assertNull(dao.appliedCursors)
    }

    @Test
    fun pending_update_with_newer_remote_version_creates_conflict_and_advances_cursor() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-1")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity("WORKSPACE_MEMBER:ws-1", 1000L, "u-1"),
            ),
        )

        val localWs = WorkspaceEntity(
            id = "ws-1",
            name = "Yerel Değişiklik",
            normalizedName = "yerel değişiklik",
            ownerId = "u-1",
            typeCode = "personal",
            currencyCode = "TRY",
            description = "Açıklama",
            createdAtEpochMillis = 1000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1100L,
                localUpdatedAtEpochMillis = 1100L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val tailOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
            operationId = "op-ws-1",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-1",
            operationTypeCode = "UPDATE",
            payloadJson = """{"name":"Yerel Değişiklik"}""",
            baseVersion = 1L,
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1100L,
            updatedAtEpochMillis = 1100L,
            statusCode = "PENDING",
            protocolVersion = 2,
        )

        val remoteWs = sampleWorkspaceDto(
            id = "ws-1",
            name = "Uzak Sunucu Değişikliği",
            version = 2L,
            updatedAt = "2026-03-01T12:00:00Z",
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(remoteWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.localWorkspaceRows["ws-1"] = localWs
        dao.activeTailOperations["ws-1"] = tailOp
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 15_000L }

        val result = coordinator.pull()

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(1, result.conflictCount)
        assertEquals(1, dao.applyCallCount)

        val plan = dao.appliedPlan
        assertNotNull(plan)
        assertEquals(0, plan.applyItems.size)
        assertEquals(1, plan.conflictItems.size)
        assertEquals(0, plan.preserveItems.size)

        val conflictItem = plan.conflictItems[0]
        assertEquals("ws-1", conflictItem.conflict.entityId)
        assertEquals("WORKSPACE", conflictItem.conflict.entityTypeCode)
        assertEquals("op-ws-1", conflictItem.conflict.operationId)
        assertEquals(1L, conflictItem.conflict.localVersion)
        assertEquals(2L, conflictItem.conflict.remoteVersion)
        assertEquals("""{"name":"Yerel Değişiklik"}""", conflictItem.conflict.localPayloadJson)
        // remotePayloadJson must be valid serialized WorkspaceDto
        kotlinx.serialization.json.Json.decodeFromString<WorkspaceDto>(conflictItem.conflict.remotePayloadJson)

        // Cursors are advanced despite conflict
        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        val wsCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE" }
        assertNotNull(wsCursor)
        assertEquals("ws-1", wsCursor.entityId)
    }

    @Test
    fun pending_update_with_older_or_equal_remote_version_is_preserved_and_cursor_advances() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-1")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity("WORKSPACE_MEMBER:ws-1", 1000L, "u-1"),
            ),
        )

        val localWs = WorkspaceEntity(
            id = "ws-1",
            name = "Yerel Güncelleme",
            normalizedName = "yerel güncelleme",
            ownerId = "u-1",
            typeCode = "personal",
            currencyCode = "TRY",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1100L,
                localUpdatedAtEpochMillis = 1100L,
                deletedAtEpochMillis = null,
                version = 2L,
                baseVersion = 2L,
                lastSyncError = null,
            ),
        )

        val tailOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
            operationId = "op-ws-2",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-1",
            operationTypeCode = "UPDATE",
            payloadJson = """{"name":"Yerel Güncelleme"}""",
            baseVersion = 2L,
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1100L,
            updatedAtEpochMillis = 1100L,
            statusCode = "PENDING",
            protocolVersion = 2,
        )

        // Remote version is 2L, which is <= baseVersion (2L)
        val remoteWs = sampleWorkspaceDto(
            id = "ws-1",
            name = "Eski Remote",
            version = 2L,
            updatedAt = "2026-03-01T12:00:00Z",
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(remoteWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.localWorkspaceRows["ws-1"] = localWs
        dao.activeTailOperations["ws-1"] = tailOp
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 15_000L }

        val result = coordinator.pull()

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, dao.applyCallCount)

        val plan = dao.appliedPlan
        assertNotNull(plan)
        assertEquals(0, plan.applyItems.size)
        assertEquals(0, plan.conflictItems.size)
        assertEquals(1, plan.preserveItems.size)
        assertEquals("ws-1", plan.preserveItems[0].workspaceId)

        val appliedCursors = dao.appliedCursors
        assertNotNull(appliedCursors)
        val wsCursor = appliedCursors.find { it.entityTypeCode == "WORKSPACE" }
        assertNotNull(wsCursor)
        assertEquals("ws-1", wsCursor.entityId)
    }

    @Test
    fun synced_workspace_with_newer_remote_is_applied() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-1")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity("WORKSPACE_MEMBER:ws-1", 1000L, "u-1"),
            ),
        )

        val localWs = WorkspaceEntity(
            id = "ws-1",
            name = "Mevcut Senkronize",
            normalizedName = "mevcut senkronize",
            ownerId = "u-1",
            typeCode = "personal",
            currencyCode = "TRY",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val remoteWs = sampleWorkspaceDto(
            id = "ws-1",
            name = "Yeni Senkronize İsim",
            version = 2L,
            updatedAt = "2026-03-01T12:00:00Z",
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(remoteWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.localWorkspaceRows["ws-1"] = localWs
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 15_000L }

        val result = coordinator.pull()

        assertEquals(1, result.appliedWorkspacesCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, dao.applyCallCount)

        val plan = dao.appliedPlan
        assertNotNull(plan)
        assertEquals(1, plan.applyItems.size)
        assertEquals("ws-1", plan.applyItems[0].entity.id)
        assertEquals("Yeni Senkronize İsim", plan.applyItems[0].entity.name)
        assertEquals(0, plan.conflictItems.size)
        assertEquals(0, plan.preserveItems.size)
    }

    @Test
    fun metadata_conflict_allows_workspace_members_to_be_applied_atomically() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-1")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity("WORKSPACE_MEMBER:ws-1", 1000L, "u-1"),
            ),
        )

        val localWs = WorkspaceEntity(
            id = "ws-1",
            name = "Pending Name",
            normalizedName = "pending name",
            ownerId = "u-1",
            typeCode = "personal",
            currencyCode = "TRY",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = com.feniqo.mobile.data.local.entity.SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1100L,
                localUpdatedAtEpochMillis = 1100L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val tailOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
            operationId = "op-1",
            entityTypeCode = "WORKSPACE",
            entityId = "ws-1",
            operationTypeCode = "UPDATE",
            payloadJson = """{"name":"Pending Name"}""",
            baseVersion = 1L,
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1100L,
            updatedAtEpochMillis = 1100L,
            statusCode = "PENDING",
            protocolVersion = 2,
        )

        val remoteWs = sampleWorkspaceDto(
            id = "ws-1",
            name = "Server Name",
            version = 3L,
            updatedAt = "2026-03-01T13:00:00Z",
        )

        val newMember = sampleMemberDto(
            workspaceId = "ws-1",
            userId = "new-member-u2",
            roleCode = "VIEWER",
            updatedAt = "2026-03-01T13:00:00Z",
        )

        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(remoteWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = listOf(newMember), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
                ),
            ),
        )

        val dao = RecordingRemoteSyncDao()
        dao.localWorkspaceRows["ws-1"] = localWs
        dao.activeTailOperations["ws-1"] = tailOp
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 15_000L }

        val result = coordinator.pull()

        assertEquals(0, result.appliedWorkspacesCount)
        assertEquals(1, result.conflictCount)
        assertEquals(1, result.appliedMembersCount)

        val plan = dao.appliedPlan
        assertNotNull(plan)
        assertEquals(1, plan.conflictItems.size)
        assertEquals(1, plan.memberRows.size)
        assertEquals("new-member-u2", plan.memberRows[0].userId)
    }

    @Test
    fun stale_plan_exception_from_dao_fails_closed_without_updating_cursors() = runTest {
        val storedWsCursor = RemoteSyncCursor(updatedAt = "2026-03-01T10:00:00Z", entityId = "ws-1")
        val syncStateDao = FakeSyncStateDao(
            initialCursors = mapOf(
                "WORKSPACE" to WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor),
                "WORKSPACE_MEMBER:ws-1" to SyncCursorEntity("WORKSPACE_MEMBER:ws-1", 1000L, "u-1"),
            ),
        )

        val remoteWs = sampleWorkspaceDto(id = "ws-1", version = 2L)
        val remote = FakeCoreRemoteDataSource(
            workspacePages = listOf(
                RemotePage(items = listOf(remoteWs), request = RemotePageRequest(pageIndex = 0), totalCount = 1),
            ),
            memberPagesByWorkspaceId = mapOf(
                "ws-1" to listOf(
                    RemotePage(items = emptyList(), request = RemotePageRequest(pageIndex = 0), totalCount = 0),
                ),
            ),
        )

        val dao = object : RecordingRemoteSyncDao() {
            override suspend fun applyWorkspaceIncrementalPlan(plan: com.feniqo.mobile.data.local.dao.WorkspaceIncrementalPlan) {
                throw com.feniqo.mobile.data.local.dao.WorkspaceSyncStalePlanException("Concurrent update detected!")
            }
        }
        val coordinator = WorkspaceIncrementalRemoteSync(remote, dao, syncStateDao) { 15_000L }

        assertFailsWith<com.feniqo.mobile.data.local.dao.WorkspaceSyncStalePlanException> {
            coordinator.pull()
        }

        // Stored cursor must NOT be advanced
        assertEquals("ws-1", syncStateDao.getCursor("WORKSPACE")?.entityId)
        assertEquals(
            WorkspaceSyncCursorKeys.workspaceCursorToEntity(storedWsCursor).updatedAtEpochMillis,
            syncStateDao.getCursor("WORKSPACE")?.updatedAtEpochMillis,
        )
    }
}
