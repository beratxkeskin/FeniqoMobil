package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow

class ObserveGoalsUseCase(
    private val repository: GoalRepository,
) {
    operator fun invoke(): Flow<List<Goal>> = repository.observeGoals()
}

class ObserveGoalUseCase(
    private val repository: GoalRepository,
) {
    operator fun invoke(id: EntityId): Flow<Goal?> = repository.observeGoal(id)
}

class ObserveGoalContributionsUseCase(
    private val repository: GoalRepository,
) {
    operator fun invoke(goalId: EntityId): Flow<List<GoalContribution>> = repository.observeContributions(goalId)
}

class ObserveDebtsUseCase(
    private val repository: DebtRepository,
) {
    operator fun invoke(): Flow<List<Debt>> = repository.observeDebts()
}

class ObserveDebtUseCase(
    private val repository: DebtRepository,
) {
    operator fun invoke(id: EntityId): Flow<Debt?> = repository.observeDebt(id)
}

class ObserveDebtPaymentsUseCase(
    private val repository: DebtRepository,
) {
    operator fun invoke(debtId: EntityId): Flow<List<DebtPayment>> = repository.observePayments(debtId)
}
