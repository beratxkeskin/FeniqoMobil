package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand

import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>

    fun observeGoal(id: EntityId): Flow<Goal?>

    fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>>

    suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId>

    suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit>

    suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>
}

interface DebtRepository {
    fun observeDebts(): Flow<List<Debt>>

    fun observeDebt(id: EntityId): Flow<Debt?>

    fun observePayments(debtId: EntityId): Flow<List<DebtPayment>>

    suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId>

    suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit>

    suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>
}

