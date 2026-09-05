package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncUserStateEntity
import com.feniqo.mobile.data.local.entity.TagEntity
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
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalDebtRemoteSyncTest {

    private class FakeRemoteSyncDao : RemoteSyncDao {
        val goals = mutableMapOf<String, GoalEntity>()
        val goalContributions = mutableMapOf<String, GoalContributionEntity>()
        val debts = mutableMapOf<String, DebtEntity>()
        val debtPayments = mutableMapOf<String, DebtPaymentEntity>()
        val conflicts = mutableListOf<SyncConflictEntity>()
        val cursors = mutableMapOf<String, SyncCursorEntity>()

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = null
        override suspend fun getSubscriptionRow(id: String): SubscriptionEntity? = null

        override suspend fun getGoalRow(id: String): GoalEntity? = goals[id]
        override suspend fun getGoalContributionRow(id: String): GoalContributionEntity? = goalContributions[id]
        override suspend fun getDebtRow(id: String): DebtEntity? = debts[id]
        override suspend fun getDebtPaymentRow(id: String): DebtPaymentEntity? = debtPayments[id]
        override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = 0

        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) {}
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) {}
        override suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>) {}
        override suspend fun upsertSubscriptionRows(entities: List<SubscriptionEntity>) {}

        override suspend fun upsertGoalRows(entities: List<GoalEntity>) {
            entities.forEach { goals[it.id] = it }
        }

        override suspend fun upsertGoalContributionRows(entities: List<GoalContributionEntity>) {
            entities.forEach { goalContributions[it.id] = it }
        }

        override suspend fun upsertDebtRows(entities: List<DebtEntity>) {
            entities.forEach { debts[it.id] = it }
        }

        override suspend fun upsertDebtPaymentRows(entities: List<DebtPaymentEntity>) {
            entities.forEach { debtPayments[it.id] = it }
        }

        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) {
            conflicts.add(conflict)
        }

        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) {
            cursors.forEach { this.cursors[it.entityTypeCode] = it }
        }

        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 1
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 1
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalContributionConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtPaymentConflict(entityId: String, error: String): Int = 1
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }

    private class FakeSyncStateDao(private val cursors: MutableMap<String, SyncCursorEntity>) : SyncStateDao {
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
        override suspend fun upsertConflict(conflict: SyncConflictEntity) {}
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
        override fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?> = flowOf(null)
        override suspend fun getUserState(userId: String): SyncUserStateEntity? = null
        override suspend fun upsertUserState(state: SyncUserStateEntity) {}
    }

    private class FakeRemoteDataSource(
        val profile: ProfileDto = ProfileDto(id = "user-1", email = "test@feniqo.com", createdAt = "2026-09-01T10:00:00Z", version = 1L),
        val goals: List<GoalDto> = emptyList(),
        val goalContributions: List<GoalContributionDto> = emptyList(),
        val debts: List<DebtDto> = emptyList(),
        val debtPayments: List<DebtPaymentDto> = emptyList(),
    ) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? = profile
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = RemotePage(goals, query.page, goals.size.toLong())
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> = RemotePage(goalContributions, query.page, goalContributions.size.toLong())
        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = RemotePage(debts, query.page, debts.size.toLong())
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> = RemotePage(debtPayments, query.page, debtPayments.size.toLong())
        override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }


    @Test
    fun initial_sync_applies_goals_and_debts_and_advances_cursors() = runTest {
        val goal = GoalDto(
            id = "g-1",
            userId = "user-1",
            workspaceId = null,
            name = "Ev Peşinatı",
            targetAmountMinor = 500_000L,
            currentAmountMinor = 50_000L,
            currency = "TRY",
            targetDate = "2027-06-30",
            colorHex = "#2E7D32",
            iconKey = "savings",
            createdAt = "2026-09-01T10:00:00Z",
            updatedAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val contrib = GoalContributionDto(
            id = "c-1",
            goalId = "g-1",
            amountMinor = 50_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = "İlkik",
            createdAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val debt = DebtDto(
            id = "d-1",
            userId = "user-1",
            workspaceId = null,
            title = "Kredi",
            amountMinor = 100_000L,
            currency = "TRY",
            type = "DEBT",
            dueDate = "2027-01-01",
            status = "OPEN",
            description = null,
            createdAt = "2026-09-01T10:00:00Z",
            updatedAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val payment = DebtPaymentDto(
            id = "p-1",
            debtId = "d-1",
            amountMinor = 10_000L,
            currency = "TRY",
            paidOn = "2026-09-02",
            createdAt = "2026-09-02T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(
            goals = listOf(goal),
            goalContributions = listOf(contrib),
            debts = listOf(debt),
            debtPayments = listOf(payment),
        )
        val dao = FakeRemoteSyncDao()
        val sync = InitialRemoteSync(remote, dao) { 1000L }

        val result = sync.pullFor(EntityId("user-1"))
        assertEquals(1, result.goalCount)
        assertEquals(1, result.goalContributionCount)
        assertEquals(1, result.debtCount)
        assertEquals(1, result.debtPaymentCount)

        assertEquals("g-1", dao.goals["g-1"]?.id)
        assertEquals("c-1", dao.goalContributions["c-1"]?.id)
        assertEquals("d-1", dao.debts["d-1"]?.id)
        assertEquals("p-1", dao.debtPayments["p-1"]?.id)
        assertNotNull(dao.cursors["GOAL"])
        assertNotNull(dao.cursors["GOAL_CONTRIBUTION"])
        assertNotNull(dao.cursors["DEBT"])
        assertNotNull(dao.cursors["DEBT_PAYMENT"])
    }

    @Test
    fun initial_sync_fail_closed_when_live_child_has_no_parent_in_snapshot() = runTest {
        val orphanedContrib = GoalContributionDto(
            id = "c-orphan",
            goalId = "g-missing",
            amountMinor = 10_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = null,
            createdAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(
            goals = emptyList(), // Missing parent!
            goalContributions = listOf(orphanedContrib),
        )
        val dao = FakeRemoteSyncDao()
        val sync = InitialRemoteSync(remote, dao) { 1000L }

        assertFailsWith<IllegalStateException> {
            sync.pullFor(EntityId("user-1"))
        }

        // Nothing was written and cursor was not advanced
        assertTrue(dao.goals.isEmpty())
        assertTrue(dao.goalContributions.isEmpty())
        assertNull(dao.cursors["GOAL_CONTRIBUTION"])
    }

    @Test
    fun incremental_sync_fail_closed_when_parent_goal_not_in_room() = runTest {
        val contrib = GoalContributionDto(
            id = "c-orphan-incremental",
            goalId = "g-nonexistent",
            amountMinor = 10_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = null,
            createdAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(goalContributions = listOf(contrib))
        val dao = FakeRemoteSyncDao()
        val syncStateDao = FakeSyncStateDao(dao.cursors)
        val sync = IncrementalRemoteSync(remote, dao, syncStateDao) { 1000L }

        assertFailsWith<IllegalStateException> {
            sync.pullFor(EntityId("user-1"))
        }

        // Batch rejected
        assertTrue(dao.goalContributions.isEmpty())
        assertNull(dao.cursors["GOAL_CONTRIBUTION"])
    }

    @Test
    fun incremental_sync_applies_goal_and_debt_when_parents_exist() = runTest {
        val dao = FakeRemoteSyncDao()
        // Pre-insert parent goal and debt
        dao.goals["g-1"] = GoalEntity(
            id = "g-1",
            ownerId = "user-1",
            workspaceId = null,
            name = "Hedef",
            targetAmountMinor = 100_000L,
            currentAmountMinor = 20_000L,
            currencyCode = "TRY",
            targetDate = "2026-12-31",
            colorHex = "#2E7D32",
            iconKey = "savings",
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )
        dao.debts["d-1"] = DebtEntity(
            id = "d-1",
            ownerId = "user-1",
            workspaceId = null,
            title = "Borç",
            amountMinor = 50_000L,
            currencyCode = "TRY",
            typeCode = "DEBT",
            dueDate = "2026-10-15",
            statusCode = "OPEN",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val contrib = GoalContributionDto(
            id = "c-1",
            goalId = "g-1",
            amountMinor = 10_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = null,
            createdAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val payment = DebtPaymentDto(
            id = "p-1",
            debtId = "d-1",
            amountMinor = 50_000L,
            currency = "TRY",
            paidOn = "2026-09-02",
            createdAt = "2026-09-02T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(
            goalContributions = listOf(contrib),
            debtPayments = listOf(payment),
        )
        val syncStateDao = FakeSyncStateDao(dao.cursors)
        val sync = IncrementalRemoteSync(remote, dao, syncStateDao) { 2000L }

        val result = sync.pullFor(EntityId("user-1"))
        assertEquals(3, result.appliedCount)

        assertEquals(0, result.conflictCount)
        assertEquals("c-1", dao.goalContributions["c-1"]?.id)
        assertEquals("p-1", dao.debtPayments["p-1"]?.id)
    }

    @Test
    fun incremental_sync_batch_atomicity_rejects_all_when_orphan_contribution_exists() = runTest {
        val dao = FakeRemoteSyncDao()
        dao.goals["g-1"] = GoalEntity(
            id = "g-1",
            ownerId = "user-1",
            workspaceId = null,
            name = "Hedef",
            targetAmountMinor = 100_000L,
            currentAmountMinor = 20_000L,
            currencyCode = "TRY",
            targetDate = "2026-12-31",
            colorHex = "#2E7D32",
            iconKey = "savings",
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val validContrib = GoalContributionDto(
            id = "c-valid",
            goalId = "g-1",
            amountMinor = 10_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = null,
            createdAt = "2026-09-01T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val orphanContrib = GoalContributionDto(
            id = "c-orphan",
            goalId = "g-nonexistent",
            amountMinor = 10_000L,
            currency = "TRY",
            direction = "ADD",
            occurredOn = "2026-09-01",
            note = null,
            createdAt = "2026-09-01T10:00:01Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(
            goalContributions = listOf(validContrib, orphanContrib),
        )
        val syncStateDao = FakeSyncStateDao(dao.cursors)
        val sync = IncrementalRemoteSync(remote, dao, syncStateDao) { 2000L }

        assertFailsWith<IllegalStateException> {
            sync.pullFor(EntityId("user-1"))
        }

        // Entire batch rejected: valid child not written, cursor not advanced
        assertNull(dao.goalContributions["c-valid"])
        assertNull(dao.goalContributions["c-orphan"])
        assertTrue(dao.goalContributions.isEmpty())
        assertNull(dao.cursors["GOAL_CONTRIBUTION"])
    }

    @Test
    fun incremental_sync_batch_atomicity_rejects_all_when_orphan_debt_payment_exists() = runTest {
        val dao = FakeRemoteSyncDao()
        dao.debts["d-1"] = DebtEntity(
            id = "d-1",
            ownerId = "user-1",
            workspaceId = null,
            title = "Borç",
            amountMinor = 50_000L,
            currencyCode = "TRY",
            typeCode = "DEBT",
            dueDate = "2026-10-15",
            statusCode = "OPEN",
            description = null,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )

        val validPayment = DebtPaymentDto(
            id = "p-valid",
            debtId = "d-1",
            amountMinor = 10_000L,
            currency = "TRY",
            paidOn = "2026-09-02",
            createdAt = "2026-09-02T10:00:00Z",
            deletedAt = null,
            version = 1L,
        )
        val orphanPayment = DebtPaymentDto(
            id = "p-orphan",
            debtId = "d-nonexistent",
            amountMinor = 10_000L,
            currency = "TRY",
            paidOn = "2026-09-02",
            createdAt = "2026-09-02T10:00:01Z",
            deletedAt = null,
            version = 1L,
        )

        val remote = FakeRemoteDataSource(
            debtPayments = listOf(validPayment, orphanPayment),
        )
        val syncStateDao = FakeSyncStateDao(dao.cursors)
        val sync = IncrementalRemoteSync(remote, dao, syncStateDao) { 2000L }

        assertFailsWith<IllegalStateException> {
            sync.pullFor(EntityId("user-1"))
        }

        // Entire batch rejected: valid payment not written, cursor not advanced
        assertNull(dao.debtPayments["p-valid"])
        assertNull(dao.debtPayments["p-orphan"])
        assertTrue(dao.debtPayments.isEmpty())
        assertNull(dao.cursors["DEBT_PAYMENT"])
    }
}

