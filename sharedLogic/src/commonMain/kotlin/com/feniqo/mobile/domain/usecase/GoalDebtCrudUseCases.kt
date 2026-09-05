package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult

class CreateGoalUseCase(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(command: CreateGoalCommand): RepositoryResult<EntityId> =
        repository.create(command)
}

class UpdateGoalUseCase(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(command: UpdateGoalCommand): RepositoryResult<Unit> =
        repository.update(command)
}

class AddGoalContributionUseCase(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(command: AddGoalContributionCommand): RepositoryResult<EntityId> =
        repository.addContribution(command)
}

class DeleteGoalUseCase(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> =
        repository.softDelete(id)
}

class CreateDebtUseCase(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(command: CreateDebtCommand): RepositoryResult<EntityId> =
        repository.create(command)
}

class UpdateDebtUseCase(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(command: UpdateDebtCommand): RepositoryResult<Unit> =
        repository.update(command)
}

class AddDebtPaymentUseCase(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(command: AddDebtPaymentCommand): RepositoryResult<EntityId> =
        repository.addPayment(command)
}

class DeleteDebtUseCase(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> =
        repository.softDelete(id)
}
