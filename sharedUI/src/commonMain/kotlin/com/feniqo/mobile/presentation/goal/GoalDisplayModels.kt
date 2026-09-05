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
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Birikim hedefleri liste ekranı UI durum modelidir.
 */
data class GoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<GoalDisplayModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && goals.isEmpty()
}


/**
 * Birikim hedefleri ekranı MVI kullanıcı niyetleridir.
 */
sealed interface GoalsIntent {
    data object Retry : GoalsIntent
}

/**
 * Birikim hedefi saf presentation modelidir.
 */
data class GoalDisplayModel(
    val id: EntityId,
    val name: String,
    val targetAmount: Money,
    val currentAmount: Money,
    val remainingAmount: Money,
    val formattedTargetAmount: String,
    val formattedCurrentAmount: String,
    val formattedRemainingAmount: String,
    val currency: Currency,
    val targetDate: LocalDate,
    val formattedTargetDate: String,
    val colorHex: String,
    val iconKey: String?,
    val status: GoalStatus,
    val progressBasisPoints: RateBasisPoints,
    val progressFraction: Float,
    val isAchieved: Boolean,
)

/**
 * Domain Goal listesini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object GoalDisplayModelMapper {

    fun map(goals: List<Goal>): List<GoalDisplayModel> {
        return goals.map { mapItem(it) }
            .sortedWith(
                compareBy<GoalDisplayModel> { it.status != GoalStatus.IN_PROGRESS }
                    .thenBy { it.targetDate }
                    .thenBy { it.id.value }
            )
    }

    fun mapItem(goal: Goal): GoalDisplayModel {
        val progress = GoalProgressCalculator.calculateProgress(goal)
        val progressFraction = progress.progressBasisPoints.value.coerceIn(0, 10_000).toFloat() / 10_000f

        return GoalDisplayModel(
            id = goal.id,
            name = goal.name,
            targetAmount = progress.targetAmount,
            currentAmount = progress.currentAmount,
            remainingAmount = progress.remainingAmount,
            formattedTargetAmount = MoneyFormatter.format(progress.targetAmount),
            formattedCurrentAmount = MoneyFormatter.format(progress.currentAmount),
            formattedRemainingAmount = MoneyFormatter.format(progress.remainingAmount),
            currency = progress.targetAmount.currency,
            targetDate = goal.targetDate,
            formattedTargetDate = DateFormatter.formatReadableDate(goal.targetDate),
            colorHex = goal.color.hex,
            iconKey = goal.icon?.key,
            status = progress.status,
            progressBasisPoints = progress.progressBasisPoints,
            progressFraction = progressFraction,
            isAchieved = progress.isAchieved,
        )
    }
}
