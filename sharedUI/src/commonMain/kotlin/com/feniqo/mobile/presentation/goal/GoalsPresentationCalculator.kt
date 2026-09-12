package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.presentation.util.MoneyFormatter

/** Hedef listesinden filtre, para-birimi-güvenli özet ve açıklanabilir içgörü üretir. */
object GoalsPresentationCalculator {

    data class Result(
        val visibleGoals: List<GoalDisplayModel>,
        val summary: GoalsSummaryUiModel?,
        val isSummaryCalculationError: Boolean,
        val insights: List<GoalInsightUiModel>,
    )

    fun calculate(
        allGoals: List<GoalDisplayModel>,
        filter: GoalStatusFilter,
    ): Result {
        val visibleGoals = allGoals.filter { goal ->
            when (filter) {
                GoalStatusFilter.ALL -> true
                GoalStatusFilter.ACTIVE -> goal.status == GoalStatus.IN_PROGRESS
                GoalStatusFilter.ACHIEVED -> goal.status == GoalStatus.ACHIEVED
            }
        }
        if (visibleGoals.isEmpty()) {
            return Result(visibleGoals, null, false, emptyList())
        }

        val summary = runCatching { buildSummary(visibleGoals) }
        return Result(
            visibleGoals = visibleGoals,
            summary = summary.getOrNull(),
            isSummaryCalculationError = summary.isFailure,
            insights = buildInsights(visibleGoals),
        )
    }

    private fun buildSummary(goals: List<GoalDisplayModel>): GoalsSummaryUiModel {
        val totals = linkedMapOf<Currency, CurrencyTotals>()
        var progressTotal = 0L
        goals.forEach { goal ->
            val total = totals.getOrPut(goal.currency) { CurrencyTotals() }
            total.savedMinor = safeAdd(total.savedMinor, goal.currentAmount.amountMinor)
            total.targetMinor = safeAdd(total.targetMinor, goal.targetAmount.amountMinor)
            progressTotal = safeAdd(
                progressTotal,
                goal.progressBasisPoints.value.coerceIn(0, MAX_PROGRESS_BASIS_POINTS).toLong(),
            )
        }
        val average = (progressTotal / goals.size).toInt().coerceIn(0, MAX_PROGRESS_BASIS_POINTS)
        return GoalsSummaryUiModel(
            scopedGoalCount = goals.size,
            activeGoalCount = goals.count { it.status == GoalStatus.IN_PROGRESS },
            achievedGoalCount = goals.count { it.status == GoalStatus.ACHIEVED },
            averageProgressBasisPoints = RateBasisPoints(average),
            currencySummaries = totals.map { (currency, total) ->
                val saved = Money(total.savedMinor, currency)
                val target = Money(total.targetMinor, currency)
                GoalCurrencySummaryUiModel(currency, saved, target, MoneyFormatter.format(saved), MoneyFormatter.format(target))
            },
        )
    }

    private fun buildInsights(goals: List<GoalDisplayModel>): List<GoalInsightUiModel> {
        val active = goals.filter { it.status == GoalStatus.IN_PROGRESS }
        val achievedCount = goals.count { it.status == GoalStatus.ACHIEVED }
        val result = mutableListOf<GoalInsightUiModel>()
        active.sortedWith(
            compareByDescending<GoalDisplayModel> { it.progressBasisPoints.value }
                .thenBy { it.targetDate }
                .thenBy { it.name }
                .thenBy { it.id.value },
        ).firstOrNull()?.let { closest ->
            result += GoalInsightUiModel(
                id = "closest-${closest.id.value}",
                title = "Tamamlanmaya en yakın hedefin: ${closest.name}",
                description = "%${closest.progressBasisPoints.value / 100} · ${closest.formattedRemainingAmount} kaldı",
            )
        }
        result += GoalInsightUiModel(
            id = "completion-count",
            title = "${goals.size} hedeften $achievedCount tanesi tamamlandı",
            description = if (active.isEmpty()) "Seçili kapsamda aktif hedef bulunmuyor." else "${active.size} aktif hedefin bulunuyor.",
        )
        val pastDateCount = active.count { it.isTargetDatePast }
        if (pastDateCount > 0) {
            result += GoalInsightUiModel(
                id = "past-date-count",
                title = "$pastDateCount aktif hedefin hedef tarihi geçti",
                description = "Hedeflerini güncelleyebilir veya birikimlerini gözden geçirebilirsin.",
            )
        }
        return result.take(3)
    }

    private data class CurrencyTotals(var savedMinor: Long = 0L, var targetMinor: Long = 0L)

    private fun safeAdd(a: Long, b: Long): Long {
        require(a >= 0L && b >= 0L)
        if (a > Long.MAX_VALUE - b) throw ArithmeticException("Hedef özeti toplamı taşar.")
        return a + b
    }

    private const val MAX_PROGRESS_BASIS_POINTS = 10_000
}
