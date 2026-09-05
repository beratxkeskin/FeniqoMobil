package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.DebtDao
import com.feniqo.mobile.data.local.dao.GoalDao
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest

class OfflineFirstGoalDebtRepositoryTest {

    private val nowEpoch = 1700000000000L

    private val defaultSync = SyncMetadata(
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtEpochMillis = nowEpoch,
        localUpdatedAtEpochMillis = nowEpoch,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private class FakeAuthRepository(var session: AuthSession? = null) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("N/A")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("N/A")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("N/A")
        override suspend fun signOut(): RepositoryResult<Unit> = error("N/A")
    }

    private class FakeGoalDao : GoalDao {
        val goals = mutableMapOf<String, GoalEntity>()
        val contributions = mutableListOf<GoalContributionEntity>()
        var observeAllException: Throwable? = null
        var cancellationExceptionToThrow: CancellationException? = null

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<GoalEntity>> = flow {
            cancellationExceptionToThrow?.let { ex ->
                currentCoroutineContext().job.parent?.cancel(ex)
                throw ex
            }
            observeAllException?.let { throw it }
            val list = goals.values.filter {
                it.ownerId == ownerId &&
                    it.sync.deletedAtEpochMillis == null &&
                    it.workspaceId == workspaceId
            }
            emit(list)
        }

        override fun observeById(id: String): Flow<GoalEntity?> = flow {
            val goal = goals[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
            emit(goal)
        }

        override suspend fun getById(id: String): GoalEntity? =
            goals[id]?.takeIf { it.sync.deletedAtEpochMillis == null }

        override suspend fun getAnyById(id: String): GoalEntity? = goals[id]

        override suspend fun upsert(goal: GoalEntity) {
            goals[goal.id] = goal
        }

        override suspend fun upsertAll(goals: List<GoalEntity>) {
            goals.forEach { upsert(it) }
        }

        override fun observeContributionsByGoalId(goalId: String): Flow<List<GoalContributionEntity>> = flow {
            val list = contributions.filter {
                it.goalId == goalId && it.sync.deletedAtEpochMillis == null
            }
            emit(list)
        }

        override suspend fun getContributionsByGoalId(goalId: String): List<GoalContributionEntity> =
            contributions.filter { it.goalId == goalId && it.sync.deletedAtEpochMillis == null }

        override suspend fun getContributionById(id: String): GoalContributionEntity? =
            contributions.firstOrNull { it.id == id && it.sync.deletedAtEpochMillis == null }

        override suspend fun getAnyContributionById(id: String): GoalContributionEntity? =
            contributions.firstOrNull { it.id == id }

        override suspend fun upsertContribution(contribution: GoalContributionEntity) {
            contributions.removeAll { it.id == contribution.id }
            contributions.add(contribution)
        }

        override suspend fun upsertAllContributions(contributions: List<GoalContributionEntity>) {
            contributions.forEach { upsertContribution(it) }
        }
    }

    private class FakeDebtDao : DebtDao {
        val debts = mutableMapOf<String, DebtEntity>()
        val payments = mutableListOf<DebtPaymentEntity>()
        var observeAllException: Throwable? = null
        var cancellationExceptionToThrow: CancellationException? = null

        override fun observeAll(ownerId: String, workspaceId: String?): Flow<List<DebtEntity>> = flow {
            cancellationExceptionToThrow?.let { ex ->
                currentCoroutineContext().job.parent?.cancel(ex)
                throw ex
            }
            observeAllException?.let { throw it }
            val list = debts.values.filter {
                it.ownerId == ownerId &&
                    it.sync.deletedAtEpochMillis == null &&
                    it.workspaceId == workspaceId
            }
            emit(list)
        }

        override fun observeAllByType(ownerId: String, workspaceId: String?, typeCode: String): Flow<List<DebtEntity>> = flow {
            val list = debts.values.filter {
                it.ownerId == ownerId &&
                    it.typeCode == typeCode &&
                    it.sync.deletedAtEpochMillis == null &&
                    it.workspaceId == workspaceId
            }
            emit(list)
        }

        override fun observeById(id: String): Flow<DebtEntity?> = flow {
            val debt = debts[id]?.takeIf { it.sync.deletedAtEpochMillis == null }
            emit(debt)
        }

        override suspend fun getById(id: String): DebtEntity? =
            debts[id]?.takeIf { it.sync.deletedAtEpochMillis == null }

        override suspend fun getAnyById(id: String): DebtEntity? = debts[id]

        override suspend fun upsert(debt: DebtEntity) {
            debts[debt.id] = debt
        }

        override suspend fun upsertAll(debts: List<DebtEntity>) {
            debts.forEach { upsert(it) }
        }

        override fun observePaymentsByDebtId(debtId: String): Flow<List<DebtPaymentEntity>> = flow {
            val list = payments.filter {
                it.debtId == debtId && it.sync.deletedAtEpochMillis == null
            }
            emit(list)
        }

        override suspend fun getPaymentsByDebtId(debtId: String): List<DebtPaymentEntity> =
            payments.filter { it.debtId == debtId && it.sync.deletedAtEpochMillis == null }

        override suspend fun getPaymentById(id: String): DebtPaymentEntity? =
            payments.firstOrNull { it.id == id && it.sync.deletedAtEpochMillis == null }

        override suspend fun getAnyPaymentById(id: String): DebtPaymentEntity? =
            payments.firstOrNull { it.id == id }

        override suspend fun upsertPayment(payment: DebtPaymentEntity) {
            payments.removeAll { it.id == payment.id }
            payments.add(payment)
        }

        override suspend fun upsertAllPayments(payments: List<DebtPaymentEntity>) {
            payments.forEach { upsertPayment(it) }
        }
    }

    private fun sampleGoalEntity(
        id: String = "goal-1",
        ownerId: String = "user-1",
        workspaceId: String? = null,
        targetAmountMinor: Long = 100_000L,
        currentAmountMinor: Long = 20_000L,
    ) = GoalEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        name = "Hedef",
        targetAmountMinor = targetAmountMinor,
        currentAmountMinor = currentAmountMinor,
        currencyCode = "TRY",
        targetDate = "2027-01-01",
        colorHex = "#123456",
        iconKey = "car",
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    private fun sampleGoalContributionEntity(
        id: String = "c-1",
        goalId: String = "goal-1",
    ) = GoalContributionEntity(
        id = id,
        goalId = goalId,
        amountMinor = 10_000L,
        currencyCode = "TRY",
        directionCode = "ADD",
        occurredOn = "2026-09-01",
        note = "Not",
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    private fun sampleDebtEntity(
        id: String = "debt-1",
        ownerId: String = "user-1",
        workspaceId: String? = null,
        typeCode: String = "DEBT",
    ) = DebtEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        title = "Borç",
        amountMinor = 50_000L,
        currencyCode = "TRY",
        typeCode = typeCode,
        dueDate = "2026-12-01",
        statusCode = "OPEN",
        description = "Açıklama",
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    private fun sampleDebtPaymentEntity(
        id: String = "p-1",
        debtId: String = "debt-1",
    ) = DebtPaymentEntity(
        id = id,
        debtId = debtId,
        amountMinor = 15_000L,
        currencyCode = "TRY",
        paidOn = "2026-09-01",
        createdAtEpochMillis = nowEpoch,
        sync = defaultSync,
    )

    private val testSession = AuthSession(
        userId = EntityId("user-1"),
        email = "user@test.com",
        expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(nowEpoch + 100000L),
    )

    // region Goal Repository Tests

    @Test
    fun goalRepository_observeGoals_returnsEmptyWhenUnauthenticated() = runTest {
        val authRepo = FakeAuthRepository(session = null)
        val goalDao = FakeGoalDao()
        val repo = OfflineFirstGoalRepository(authRepo, goalDao)

        assertEquals(emptyList(), repo.observeGoals().first())
    }

    @Test
    fun goalRepository_observeGoals_mapsEntitiesForActiveSession() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val goalDao = FakeGoalDao().apply {
            upsert(sampleGoalEntity(id = "g-1", ownerId = "user-1"))
            upsert(sampleGoalEntity(id = "g-other-owner", ownerId = "user-2"))
            upsert(sampleGoalEntity(id = "g-ws", ownerId = "user-1", workspaceId = "ws-1"))
        }
        val repo = OfflineFirstGoalRepository(authRepo, goalDao)

        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertEquals(EntityId("g-1"), list[0].id)
        assertEquals(Money(100_000, Currency.TRY), list[0].targetAmount)
        assertEquals(Money(20_000, Currency.TRY), list[0].currentAmount)
    }

    @Test
    fun goalRepository_observeGoal_returnsNullWhenUnauthenticatedOrMismatched() = runTest {
        val authRepo = FakeAuthRepository(session = null)
        val goalDao = FakeGoalDao().apply {
            upsert(sampleGoalEntity(id = "g-1", ownerId = "user-1"))
            upsert(sampleGoalEntity(id = "g-ws", ownerId = "user-1", workspaceId = "ws-1"))
        }
        val repo = OfflineFirstGoalRepository(authRepo, goalDao)

        assertNull(repo.observeGoal(EntityId("g-1")).first())

        authRepo.session = testSession
        val valid = repo.observeGoal(EntityId("g-1")).first()
        assertNotNull(valid)
        assertEquals(EntityId("g-1"), valid.id)

        // Workspace item filtered out in personal scope
        assertNull(repo.observeGoal(EntityId("g-ws")).first())
        // Non-existent item
        assertNull(repo.observeGoal(EntityId("non-existent")).first())
    }

    @Test
    fun goalRepository_observeContributions_requiresParentGoalOwnership() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val goalDao = FakeGoalDao().apply {
            upsert(sampleGoalEntity(id = "g-1", ownerId = "user-1"))
            upsert(sampleGoalEntity(id = "g-user2", ownerId = "user-2"))
            upsertContribution(sampleGoalContributionEntity(id = "c-1", goalId = "g-1"))
            upsertContribution(sampleGoalContributionEntity(id = "c-2", goalId = "g-user2"))
        }
        val repo = OfflineFirstGoalRepository(authRepo, goalDao)

        val contribsGoal1 = repo.observeContributions(EntityId("g-1")).first()
        assertEquals(1, contribsGoal1.size)
        assertEquals(EntityId("c-1"), contribsGoal1[0].id)

        // Other user's goal contributions return empty
        val contribsGoal2 = repo.observeContributions(EntityId("g-user2")).first()
        assertEquals(emptyList(), contribsGoal2)
    }

    @Test
    fun goalRepository_doesNotSwallowMapperOrCancellationException() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val goalDao = FakeGoalDao()
        val repo = OfflineFirstGoalRepository(authRepo, goalDao)

        // Corrupt entity throws when mapped
        goalDao.goals["corrupt"] = sampleGoalEntity(id = "corrupt", ownerId = "user-1").copy(currencyCode = "INVALID")
        assertFailsWith<IllegalArgumentException> {
            repo.observeGoals().first()
        }
        goalDao.goals.remove("corrupt")

        // DAO exception propagates
        goalDao.observeAllException = IllegalStateException("database failure")
        assertFailsWith<IllegalStateException> {
            repo.observeGoals().first()
        }
        goalDao.observeAllException = null

        // CancellationException is rethrown directly without converting to empty list or other error
        goalDao.cancellationExceptionToThrow = CancellationException("goal_observe_cancelled")
        assertFailsWith<CancellationException> {
            repo.observeGoals().first()
        }
    }

    // endregion

    // region Debt Repository Tests

    @Test
    fun debtRepository_observeDebts_returnsEmptyWhenUnauthenticated() = runTest {
        val authRepo = FakeAuthRepository(session = null)
        val debtDao = FakeDebtDao()
        val repo = OfflineFirstDebtRepository(authRepo, debtDao)

        assertEquals(emptyList(), repo.observeDebts().first())
    }

    @Test
    fun debtRepository_observeDebts_mapsEntitiesForActiveSession() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val debtDao = FakeDebtDao().apply {
            upsert(sampleDebtEntity(id = "d-1", ownerId = "user-1"))
            upsert(sampleDebtEntity(id = "d-other", ownerId = "user-2"))
            upsert(sampleDebtEntity(id = "d-ws", ownerId = "user-1", workspaceId = "ws-1"))
        }
        val repo = OfflineFirstDebtRepository(authRepo, debtDao)

        val list = repo.observeDebts().first()
        assertEquals(1, list.size)
        assertEquals(EntityId("d-1"), list[0].id)
        assertEquals(Money(50_000, Currency.TRY), list[0].amount)
        assertEquals(DebtType.DEBT, list[0].type)
        assertEquals(DebtStatus.OPEN, list[0].status)
    }

    @Test
    fun debtRepository_observeDebt_returnsNullWhenUnauthenticatedOrMismatched() = runTest {
        val authRepo = FakeAuthRepository(session = null)
        val debtDao = FakeDebtDao().apply {
            upsert(sampleDebtEntity(id = "d-1", ownerId = "user-1"))
            upsert(sampleDebtEntity(id = "d-ws", ownerId = "user-1", workspaceId = "ws-1"))
        }
        val repo = OfflineFirstDebtRepository(authRepo, debtDao)

        assertNull(repo.observeDebt(EntityId("d-1")).first())

        authRepo.session = testSession
        val valid = repo.observeDebt(EntityId("d-1")).first()
        assertNotNull(valid)
        assertEquals(EntityId("d-1"), valid.id)

        // Workspace item filtered out in personal scope
        assertNull(repo.observeDebt(EntityId("d-ws")).first())
        // Non-existent item
        assertNull(repo.observeDebt(EntityId("non-existent")).first())
    }

    @Test
    fun debtRepository_observePayments_requiresParentDebtOwnership() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val debtDao = FakeDebtDao().apply {
            upsert(sampleDebtEntity(id = "d-1", ownerId = "user-1"))
            upsert(sampleDebtEntity(id = "d-user2", ownerId = "user-2"))
            upsertPayment(sampleDebtPaymentEntity(id = "p-1", debtId = "d-1"))
            upsertPayment(sampleDebtPaymentEntity(id = "p-2", debtId = "d-user2"))
        }
        val repo = OfflineFirstDebtRepository(authRepo, debtDao)

        val paymentsDebt1 = repo.observePayments(EntityId("d-1")).first()
        assertEquals(1, paymentsDebt1.size)
        assertEquals(EntityId("p-1"), paymentsDebt1[0].id)

        // Other user's debt payments return empty
        val paymentsDebt2 = repo.observePayments(EntityId("d-user2")).first()
        assertEquals(emptyList(), paymentsDebt2)
    }

    @Test
    fun debtRepository_doesNotSwallowMapperOrCancellationException() = runTest {
        val authRepo = FakeAuthRepository(session = testSession)
        val debtDao = FakeDebtDao()
        val repo = OfflineFirstDebtRepository(authRepo, debtDao)

        // Corrupt entity throws when mapped
        debtDao.debts["corrupt"] = sampleDebtEntity(id = "corrupt", ownerId = "user-1").copy(typeCode = "INVALID_TYPE")
        assertFailsWith<IllegalArgumentException> {
            repo.observeDebts().first()
        }
        debtDao.debts.remove("corrupt")

        // DAO exception propagates
        debtDao.observeAllException = IllegalStateException("database failure")
        assertFailsWith<IllegalStateException> {
            repo.observeDebts().first()
        }
        debtDao.observeAllException = null

        // CancellationException is rethrown directly without converting to empty list or other error
        debtDao.cancellationExceptionToThrow = CancellationException("debt_observe_cancelled")
        assertFailsWith<CancellationException> {
            repo.observeDebts().first()
        }
    }

    // endregion
}
