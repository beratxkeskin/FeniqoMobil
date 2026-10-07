package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.validation.GoalProgressCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Birikim hedefleri liste ekranı UI durum modelidir.
 */
data class GoalsUiState(
    val isLoading: Boolean = true,
    /** Room akışından map edilmiş, filtre uygulanmamış hedefler. */
    val allGoals: List<GoalDisplayModel> = emptyList(),
    /** Seçili [selectedFilter] kapsamında listelenen hedefler. */
    val visibleGoals: List<GoalDisplayModel> = emptyList(),
    val selectedFilter: GoalStatusFilter = GoalStatusFilter.ALL,
    val summary: GoalsSummaryUiModel? = null,
    val isSummaryCalculationError: Boolean = false,
    val insights: List<GoalInsightUiModel> = emptyList(),
    val activeWorkspaceName: String? = null,
    val observationError: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && allGoals.isEmpty()
    val isFilterEmpty: Boolean get() = !isEmpty && visibleGoals.isEmpty()
}

enum class GoalStatusFilter {
    ALL,
    ACTIVE,
    ACHIEVED,
}


/**
 * Birikim hedefleri ekranı MVI kullanıcı niyetleridir.
 */
sealed interface GoalsIntent {
    data object Retry : GoalsIntent
    data class SelectFilter(val filter: GoalStatusFilter) : GoalsIntent
}

data class GoalCurrencySummaryUiModel(
    val currency: Currency,
    val savedAmount: Money,
    val targetAmount: Money,
)

data class GoalsSummaryUiModel(
    val scopedGoalCount: Int,
    val activeGoalCount: Int,
    val achievedGoalCount: Int,
    val averageProgressBasisPoints: RateBasisPoints?,
    val currencySummaries: List<GoalCurrencySummaryUiModel>,
)

sealed interface GoalInsightPayload {
    data class ClosestGoal(
        val goalName: String,
        val progressBasisPoints: RateBasisPoints,
        val remainingAmount: Money,
    ) : GoalInsightPayload

    data class CompletionCount(
        val totalCount: Int,
        val achievedCount: Int,
        val activeCount: Int,
    ) : GoalInsightPayload

    data class PastDateCount(
        val pastDateCount: Int,
    ) : GoalInsightPayload
}

data class GoalInsightUiModel(
    val id: String,
    val payload: GoalInsightPayload,
)

/**
 * Birikim hedefi saf presentation modelidir.
 */
data class GoalDisplayModel(
    val id: EntityId,
    val name: String,
    val targetAmount: Money,
    val currentAmount: Money,
    val remainingAmount: Money,
    val currency: Currency,
    val targetDate: LocalDate,
    val colorHex: String,
    val iconKey: String?,
    val status: GoalStatus,
    val progressBasisPoints: RateBasisPoints,
    val progressFraction: Float,
    val isAchieved: Boolean,
    val isTargetDatePast: Boolean = false,
)

/**
 * Domain Goal listesini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object GoalDisplayModelMapper {

    fun map(goals: List<Goal>, today: LocalDate? = null): List<GoalDisplayModel> {
        return goals.map { mapItem(it, today) }
            .sortedWith(
                compareBy<GoalDisplayModel> { it.status != GoalStatus.IN_PROGRESS }
                    .thenBy { it.targetDate }
                    .thenBy { it.name }
                    .thenBy { it.id.value }
            )
    }

    fun mapItem(goal: Goal, today: LocalDate? = null): GoalDisplayModel {
        val progress = GoalProgressCalculator.calculateProgress(goal)
        val progressFraction = progress.progressBasisPoints.value.coerceIn(0, 10_000).toFloat() / 10_000f

        return GoalDisplayModel(
            id = goal.id,
            name = goal.name,
            targetAmount = progress.targetAmount,
            currentAmount = progress.currentAmount,
            remainingAmount = progress.remainingAmount,
            currency = progress.targetAmount.currency,
            targetDate = goal.targetDate,
            colorHex = goal.color.hex,
            iconKey = goal.icon?.key,
            status = progress.status,
            progressBasisPoints = progress.progressBasisPoints,
            progressFraction = progressFraction,
            isAchieved = progress.isAchieved,
            isTargetDatePast = !progress.isAchieved && today?.let { goal.targetDate < it } == true,
        )
    }
}
