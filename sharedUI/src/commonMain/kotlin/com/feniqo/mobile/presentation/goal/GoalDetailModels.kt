package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.presentation.common.FinanceUiMessage

enum class GoalDetailStatus {
    ACTIVE,
    ACHIEVED,
    PAST_DUE,
}

sealed interface GoalDetailInsight {
    data object Achieved : GoalDetailInsight
    data object ZeroContribution : GoalDetailInsight
    data class PastDue(val remaining: Money) : GoalDetailInsight
    data class InProgress(val progress: RateBasisPoints, val remaining: Money) : GoalDetailInsight
}

data class GoalChartPoint(val date: LocalDate, val amount: Money, val progress: RateBasisPoints)

data class GoalDetailDisplayModel(
    val goal: Goal,
    val status: GoalDetailStatus,
    val typedInsight: GoalDetailInsight,
    val remainingAmount: Money,
    val progress: RateBasisPoints,
    val daysRemaining: Long?,
    val monthlyRequired: Money?,
    val estimatedCompletion: LocalDate?,
    val chart: List<GoalChartPoint>?,
    val recentContributions: List<GoalContribution>,
)

data class GoalDetailUiState(
    val isLoading: Boolean = true,
    val detail: GoalDetailDisplayModel? = null,
    val isNotFound: Boolean = false,
    val observationError: FinanceUiMessage? = null,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
)

sealed interface GoalDetailEvent {
    data object Deleted : GoalDetailEvent
    data class Message(val message: FinanceUiMessage) : GoalDetailEvent
}
