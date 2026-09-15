package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.presentation.common.FinanceUiMessage

data class GoalChartPoint(val date: LocalDate, val amount: Money, val progress: RateBasisPoints)

data class GoalDetailDisplayModel(
    val goal: Goal,
    val statusLabel: String,
    val formattedCurrent: String,
    val formattedTarget: String,
    val formattedRemaining: String,
    val progress: RateBasisPoints,
    val insight: String?,
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
