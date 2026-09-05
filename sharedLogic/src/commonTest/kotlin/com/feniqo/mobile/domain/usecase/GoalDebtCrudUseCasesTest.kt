package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GoalDebtCrudUseCasesTest {

    private class RecordingGoalRepository : GoalRepository {
        var lastCreateCommand: CreateGoalCommand? = null
        var lastUpdateCommand: UpdateGoalCommand? = null
        var lastAddContributionCommand: AddGoalContributionCommand? = null
        var lastDeletedId: EntityId? = null

        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("goal-created-1"))
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var addContributionResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("contrib-created-1"))
        var softDeleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var throwCancellationOnCreate = false

        override fun observeGoals(): Flow<List<Goal>> = emptyFlow()
        override fun observeGoal(id: EntityId): Flow<Goal?> = emptyFlow()
        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = emptyFlow()

        override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> {
            if (throwCancellationOnCreate) throw CancellationException("Goal creation cancelled")
            lastCreateCommand = command
            return createResult
        }

        override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }

        override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> {
            lastAddContributionCommand = command
            return addContributionResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return softDeleteResult
        }
    }

    private class RecordingDebtRepository : DebtRepository {
        var lastCreateCommand: CreateDebtCommand? = null
        var lastUpdateCommand: UpdateDebtCommand? = null
        var lastAddPaymentCommand: AddDebtPaymentCommand? = null
        var lastDeletedId: EntityId? = null

        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("debt-created-1"))
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var addPaymentResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("payment-created-1"))
        var softDeleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var throwCancellationOnCreate = false

        override fun observeDebts(): Flow<List<Debt>> = emptyFlow()
        override fun observeDebt(id: EntityId): Flow<Debt?> = emptyFlow()
        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = emptyFlow()

        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> {
            if (throwCancellationOnCreate) throw CancellationException("Debt creation cancelled")
            lastCreateCommand = command
            return createResult
        }

        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }

        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> {
            lastAddPaymentCommand = command
            return addPaymentResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return softDeleteResult
        }
    }

    @Test
    fun goalWriteUseCases_delegateOriginalInputsAndResults() = runTest {
        val repository = RecordingGoalRepository()
        val create = CreateGoalUseCase(repository)
        val update = UpdateGoalUseCase(repository)
        val addContrib = AddGoalContributionUseCase(repository)
        val delete = DeleteGoalUseCase(repository)

        val createCmd = CreateGoalCommand(
            name = "Tatil Fonu",
            targetAmount = Money(100_000L, Currency.TRY),
            initialAmount = Money(20_000L, Currency.TRY),
            targetDate = LocalDate(2026, 12, 31),
            color = CategoryColor("#2E7D32"),
            icon = CategoryIcon("savings"),
        )
        val updateCmd = UpdateGoalCommand(
            id = EntityId("goal-1"),
            name = "Yeni Tatil Fonu",
            targetAmount = Money(150_000L, Currency.TRY),
            targetDate = LocalDate(2027, 6, 30),
            color = CategoryColor("#1976D2"),
            icon = CategoryIcon("savings"),
        )
        val contribCmd = AddGoalContributionCommand(
            goalId = EntityId("goal-1"),
            amount = Money(10_000L, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "Maaş katkısı",
        )

        val createResult = create(createCmd)
        val updateResult = update(updateCmd)
        val addContribResult = addContrib(contribCmd)
        val deleteResult = delete(EntityId("goal-1"))

        val createdId = assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals(EntityId("goal-created-1"), createdId.value)
        assertIs<RepositoryResult.Success<Unit>>(updateResult)
        val contribId = assertIs<RepositoryResult.Success<EntityId>>(addContribResult)
        assertEquals(EntityId("contrib-created-1"), contribId.value)
        assertIs<RepositoryResult.Success<Unit>>(deleteResult)

        assertEquals(createCmd, repository.lastCreateCommand)
        assertEquals(updateCmd, repository.lastUpdateCommand)
        assertEquals(contribCmd, repository.lastAddContributionCommand)
        assertEquals(EntityId("goal-1"), repository.lastDeletedId)
    }

    @Test
    fun debtWriteUseCases_delegateOriginalInputsAndResults() = runTest {
        val repository = RecordingDebtRepository()
        val create = CreateDebtUseCase(repository)
        val update = UpdateDebtUseCase(repository)
        val addPayment = AddDebtPaymentUseCase(repository)
        val delete = DeleteDebtUseCase(repository)

        val createCmd = CreateDebtCommand(
            title = "Kredi Kartı Borcu",
            amount = Money(50_000L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 10, 15),
            description = "Banka borcu",
        )
        val updateCmd = UpdateDebtCommand(
            id = EntityId("debt-1"),
            title = "Güncel Borç",
            amount = Money(60_000L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 11, 15),
            description = "Revize borç",
        )
        val paymentCmd = AddDebtPaymentCommand(
            debtId = EntityId("debt-1"),
            amount = Money(25_000L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 2),
        )

        val createResult = create(createCmd)
        val updateResult = update(updateCmd)
        val addPaymentResult = addPayment(paymentCmd)
        val deleteResult = delete(EntityId("debt-1"))

        val createdId = assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals(EntityId("debt-created-1"), createdId.value)
        assertIs<RepositoryResult.Success<Unit>>(updateResult)
        val paymentId = assertIs<RepositoryResult.Success<EntityId>>(addPaymentResult)
        assertEquals(EntityId("payment-created-1"), paymentId.value)
        assertIs<RepositoryResult.Success<Unit>>(deleteResult)

        assertEquals(createCmd, repository.lastCreateCommand)
        assertEquals(updateCmd, repository.lastUpdateCommand)
        assertEquals(paymentCmd, repository.lastAddPaymentCommand)
        assertEquals(EntityId("debt-1"), repository.lastDeletedId)
    }

    @Test
    fun writeUseCases_propagateFailuresWithoutModification() = runTest {
        val goalRepo = RecordingGoalRepository().apply {
            createResult = RepositoryResult.Failure(AppError.Validation("invalid_goal_name"))
        }
        val debtRepo = RecordingDebtRepository().apply {
            createResult = RepositoryResult.Failure(AppError.Validation("invalid_debt_title"))
        }

        val createGoal = CreateGoalUseCase(goalRepo)
        val createDebt = CreateDebtUseCase(debtRepo)

        val goalResult = createGoal(
            CreateGoalCommand(
                name = "",
                targetAmount = Money(100L, Currency.TRY),
                targetDate = LocalDate(2026, 12, 31),
                color = CategoryColor("#2E7D32"),
            ),
        )
        val debtResult = createDebt(
            CreateDebtCommand(
                title = "",
                amount = Money(100L, Currency.TRY),
                type = DebtType.DEBT,
                dueDate = LocalDate(2026, 10, 15),
            ),
        )

        val goalFailure = assertIs<RepositoryResult.Failure>(goalResult)
        assertEquals(AppError.Validation("invalid_goal_name"), goalFailure.error)

        val debtFailure = assertIs<RepositoryResult.Failure>(debtResult)
        assertEquals(AppError.Validation("invalid_debt_title"), debtFailure.error)
    }

    @Test
    fun writeUseCases_propagateCancellationExceptionWithoutSwallowing() = runTest {
        val goalRepo = RecordingGoalRepository().apply { throwCancellationOnCreate = true }
        val debtRepo = RecordingDebtRepository().apply { throwCancellationOnCreate = true }

        val createGoal = CreateGoalUseCase(goalRepo)
        val createDebt = CreateDebtUseCase(debtRepo)

        assertFailsWith<CancellationException> {
            createGoal(
                CreateGoalCommand(
                    name = "Test",
                    targetAmount = Money(100L, Currency.TRY),
                    targetDate = LocalDate(2026, 12, 31),
                    color = CategoryColor("#2E7D32"),
                ),
            )
        }


        assertFailsWith<CancellationException> {
            createDebt(
                CreateDebtCommand(
                    title = "Test",
                    amount = Money(100L, Currency.TRY),
                    type = DebtType.DEBT,
                    dueDate = LocalDate(2026, 10, 15),
                ),
            )
        }
    }
}
