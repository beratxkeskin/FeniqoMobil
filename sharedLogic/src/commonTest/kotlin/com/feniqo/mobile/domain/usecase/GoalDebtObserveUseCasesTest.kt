package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalDebtObserveUseCasesTest {

    private val now = Instant.parse("2026-09-01T00:00:00Z")

    private val sampleGoal = Goal(
        id = EntityId("g-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Birikim",
        targetAmount = Money(100_000, Currency.TRY),
        currentAmount = Money(25_000, Currency.TRY),
        targetDate = LocalDate(2027, 1, 1),
        color = CategoryColor("#123456"),
        icon = null,
        createdAt = now,
    )

    private val sampleContribution = GoalContribution(
        id = EntityId("c-1"),
        goalId = EntityId("g-1"),
        amount = Money(25_000, Currency.TRY),
        direction = GoalContributionDirection.ADD,
        occurredOn = LocalDate(2026, 9, 1),
        note = "İlk katkı",
        createdAt = now,
    )

    private val sampleDebt = Debt(
        id = EntityId("d-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = "Borç",
        amount = Money(50_000, Currency.TRY),
        type = DebtType.DEBT,
        dueDate = LocalDate(2026, 12, 31),
        status = DebtStatus.OPEN,
        description = "Açıklama",
        createdAt = now,
    )

    private val samplePayment = DebtPayment(
        id = EntityId("p-1"),
        debtId = EntityId("d-1"),
        amount = Money(10_000, Currency.TRY),
        paidOn = LocalDate(2026, 9, 1),
        createdAt = now,
    )

    private class FakeGoalRepository(
        var goals: List<Goal> = emptyList(),
        var singleGoal: Goal? = null,
        var contributions: List<GoalContribution> = emptyList(),
    ) : GoalRepository {
        override fun observeGoals(): Flow<List<Goal>> = flowOf(goals)
        override fun observeGoal(id: EntityId): Flow<Goal?> = flowOf(singleGoal)
        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = flowOf(contributions)
        override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> = error("Not needed")
        override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = error("Not needed")
    }

    private class FakeDebtRepository(
        var debts: List<Debt> = emptyList(),
        var singleDebt: Debt? = null,
        var payments: List<DebtPayment> = emptyList(),
    ) : DebtRepository {
        override fun observeDebts(): Flow<List<Debt>> = flowOf(debts)
        override fun observeDebt(id: EntityId): Flow<Debt?> = flowOf(singleDebt)
        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flowOf(payments)
        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> = error("Not needed")
        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = error("Not needed")
    }


    @Test
    fun observeGoalsUseCase_emitsGoalsListDirectly() = runTest {
        val repo = FakeGoalRepository(goals = listOf(sampleGoal))
        val useCase = ObserveGoalsUseCase(repo)

        assertEquals(listOf(sampleGoal), useCase().first())
    }

    @Test
    fun observeGoalUseCase_emitsGoalAndPreservesNull() = runTest {
        val repo = FakeGoalRepository(singleGoal = sampleGoal)
        val useCase = ObserveGoalUseCase(repo)

        assertEquals(sampleGoal, useCase(EntityId("g-1")).first())

        repo.singleGoal = null
        assertNull(useCase(EntityId("g-non-existent")).first())
    }

    @Test
    fun observeGoalContributionsUseCase_emitsContributionsListDirectly() = runTest {
        val repo = FakeGoalRepository(contributions = listOf(sampleContribution))
        val useCase = ObserveGoalContributionsUseCase(repo)

        assertEquals(listOf(sampleContribution), useCase(EntityId("g-1")).first())
    }

    @Test
    fun observeDebtsUseCase_emitsDebtsListDirectly() = runTest {
        val repo = FakeDebtRepository(debts = listOf(sampleDebt))
        val useCase = ObserveDebtsUseCase(repo)

        assertEquals(listOf(sampleDebt), useCase().first())
    }

    @Test
    fun observeDebtUseCase_emitsDebtAndPreservesNull() = runTest {
        val repo = FakeDebtRepository(singleDebt = sampleDebt)
        val useCase = ObserveDebtUseCase(repo)

        assertEquals(sampleDebt, useCase(EntityId("d-1")).first())

        repo.singleDebt = null
        assertNull(useCase(EntityId("d-non-existent")).first())
    }

    @Test
    fun observeDebtPaymentsUseCase_emitsPaymentsListDirectly() = runTest {
        val repo = FakeDebtRepository(payments = listOf(samplePayment))
        val useCase = ObserveDebtPaymentsUseCase(repo)

        assertEquals(listOf(samplePayment), useCase(EntityId("d-1")).first())
    }
}
