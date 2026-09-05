package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query(
        """
        SELECT * FROM goals
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY target_date ASC, id ASC
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
    ): Flow<List<GoalEntity>>

    @Query(
        """
        SELECT * FROM goals
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    fun observeById(id: String): Flow<GoalEntity?>

    @Query(
        """
        SELECT * FROM goals
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getById(id: String): GoalEntity?

    @Query(
        """
        SELECT * FROM goals
        WHERE id = :id
        """,
    )
    suspend fun getAnyById(id: String): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Upsert
    suspend fun upsertAll(goals: List<GoalEntity>)

    // --- Goal Contributions ---

    @Query(
        """
        SELECT * FROM goal_contributions
        WHERE goal_id = :goalId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY occurred_on ASC, created_at_epoch_ms ASC, id ASC
        """,
    )
    fun observeContributionsByGoalId(goalId: String): Flow<List<GoalContributionEntity>>

    @Query(
        """
        SELECT * FROM goal_contributions
        WHERE goal_id = :goalId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY occurred_on ASC, created_at_epoch_ms ASC, id ASC
        """,
    )
    suspend fun getContributionsByGoalId(goalId: String): List<GoalContributionEntity>

    @Query(
        """
        SELECT * FROM goal_contributions
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getContributionById(id: String): GoalContributionEntity?

    @Query(
        """
        SELECT * FROM goal_contributions
        WHERE id = :id
        """,
    )
    suspend fun getAnyContributionById(id: String): GoalContributionEntity?

    @Upsert
    suspend fun upsertContribution(contribution: GoalContributionEntity)

    @Upsert
    suspend fun upsertAllContributions(contributions: List<GoalContributionEntity>)
}

@Dao
interface DebtDao {

    @Query(
        """
        SELECT * FROM debts
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY due_date ASC, id ASC
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
    ): Flow<List<DebtEntity>>

    @Query(
        """
        SELECT * FROM debts
        WHERE owner_id = :ownerId
          AND type_code = :typeCode
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY due_date ASC, id ASC
        """,
    )
    fun observeAllByType(
        ownerId: String,
        workspaceId: String?,
        typeCode: String,
    ): Flow<List<DebtEntity>>

    @Query(
        """
        SELECT * FROM debts
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    fun observeById(id: String): Flow<DebtEntity?>

    @Query(
        """
        SELECT * FROM debts
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getById(id: String): DebtEntity?

    @Query(
        """
        SELECT * FROM debts
        WHERE id = :id
        """,
    )
    suspend fun getAnyById(id: String): DebtEntity?

    @Upsert
    suspend fun upsert(debt: DebtEntity)

    @Upsert
    suspend fun upsertAll(debts: List<DebtEntity>)

    // --- Debt Payments ---

    @Query(
        """
        SELECT * FROM debt_payments
        WHERE debt_id = :debtId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY paid_on ASC, created_at_epoch_ms ASC, id ASC
        """,
    )
    fun observePaymentsByDebtId(debtId: String): Flow<List<DebtPaymentEntity>>

    @Query(
        """
        SELECT * FROM debt_payments
        WHERE debt_id = :debtId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY paid_on ASC, created_at_epoch_ms ASC, id ASC
        """,
    )
    suspend fun getPaymentsByDebtId(debtId: String): List<DebtPaymentEntity>

    @Query(
        """
        SELECT * FROM debt_payments
        WHERE id = :id AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getPaymentById(id: String): DebtPaymentEntity?

    @Query(
        """
        SELECT * FROM debt_payments
        WHERE id = :id
        """,
    )
    suspend fun getAnyPaymentById(id: String): DebtPaymentEntity?

    @Upsert
    suspend fun upsertPayment(payment: DebtPaymentEntity)

    @Upsert
    suspend fun upsertAllPayments(payments: List<DebtPaymentEntity>)
}
