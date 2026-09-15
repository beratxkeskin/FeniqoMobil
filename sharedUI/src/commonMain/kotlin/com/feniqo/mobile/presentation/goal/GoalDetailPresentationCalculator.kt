package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.validation.GoalProgressCalculator
import com.feniqo.mobile.presentation.util.MoneyFormatter

/** Hedef detayı için yalnız gerçek kayıtlardan, Long küçük-birim aritmetiğiyle sunum üretir. */
object GoalDetailPresentationCalculator {
    fun calculate(goal: Goal, contributions: List<GoalContribution>, today: LocalDate): GoalDetailDisplayModel {
        val progress = GoalProgressCalculator.calculateProgress(goal)
        val days = goal.targetDate.toEpochDays() - today.toEpochDays()
        val sorted = contributions.sortedWith(compareBy<GoalContribution> { it.occurredOn }.thenBy { it.createdAt }.thenBy { it.id.value })
        val chart = runCatching { buildChart(goal, sorted) }.getOrNull()
        val monthly = runCatching {
            if (progress.isAchieved || days < 0) null else {
                // Takvim ayı tahmini: kalan günler 30 günlük dönemlere yukarı yuvarlanır, en az bir dönemdir.
                val months = maxOf(1L, (days + 29L) / 30L)
                Money(ceilDivide(progress.remainingAmount.amountMinor, months), goal.targetAmount.currency)
            }
        }.getOrNull()
        val estimate = runCatching { estimateCompletion(goal, sorted, today) }.getOrNull()
        val insight = when {
            progress.isAchieved -> "Hedefini tamamladın."
            goal.currentAmount.amountMinor == 0L -> "Henüz bu hedefe para eklenmedi."
            days < 0 -> "Hedef tarihin geçti ve ${MoneyFormatter.format(progress.remainingAmount)} kaldı."
            else -> "Hedefinin %${progress.progressBasisPoints.value / 100}’üne ulaştın. ${MoneyFormatter.format(progress.remainingAmount)} kaldı."
        }
        return GoalDetailDisplayModel(
            goal, when { progress.isAchieved -> "Tamamlandı"; days < 0 -> "Hedef tarihi geçti"; else -> "Aktif" },
            MoneyFormatter.format(goal.currentAmount), MoneyFormatter.format(goal.targetAmount), MoneyFormatter.format(progress.remainingAmount),
            progress.progressBasisPoints, insight, days.takeIf { it >= 0 }, monthly, estimate, chart, sorted.asReversed(),
        )
    }

    private fun buildChart(goal: Goal, items: List<GoalContribution>): List<GoalChartPoint>? {
        if (items.isEmpty()) return emptyList()
        items.forEach { require(it.goalId == goal.id && it.amount.currency == goal.targetAmount.currency) }
        var net = 0L
        items.forEach { net = if (it.direction == GoalContributionDirection.ADD) safeAdd(net, it.amount.amountMinor) else safeSubtract(net, it.amount.amountMinor) }
        var running = safeSubtract(goal.currentAmount.amountMinor, net)
        require(running >= 0L)
        val points = mutableListOf<GoalChartPoint>()
        items.groupBy { it.occurredOn }.toSortedMap().forEach { (date, daily) ->
            daily.forEach { item -> running = if (item.direction == GoalContributionDirection.ADD) safeAdd(running, item.amount.amountMinor) else safeSubtract(running, item.amount.amountMinor) }
            require(running >= 0L)
            points += GoalChartPoint(date, Money(running, goal.targetAmount.currency), basisPoints(running, goal.targetAmount.amountMinor))
        }
        require(running == goal.currentAmount.amountMinor)
        return points
    }

    private fun estimateCompletion(goal: Goal, items: List<GoalContribution>, today: LocalDate): LocalDate? {
        val dates = items.map { it.occurredOn }.distinct().sorted()
        if (dates.size < 3 || goal.currentAmount.amountMinor >= goal.targetAmount.amountMinor) return null
        val span = dates.last().toEpochDays() - dates.first().toEpochDays()
        if (span < 14L) return null
        var net = 0L
        items.forEach { item ->
            require(item.amount.currency == goal.targetAmount.currency)
            net = if (item.direction == GoalContributionDirection.ADD) safeAdd(net, item.amount.amountMinor) else safeSubtract(net, item.amount.amountMinor)
        }
        if (net <= 0L) return null
        val remaining = goal.targetAmount.amountMinor - goal.currentAmount.amountMinor
        val daysNeeded = ceilDivide(safeMultiply(remaining, span), net)
        if (daysNeeded !in 1L..36500L) return null
        return LocalDate.fromEpochDays(today.toEpochDays() + daysNeeded)
    }

    private fun basisPoints(value: Long, target: Long): RateBasisPoints = RateBasisPoints(
        if (value >= target) 10_000 else (safeMultiply(value, 10_000L) / target).toInt().coerceIn(0, 10_000),
    )
    private fun ceilDivide(value: Long, divisor: Long): Long { require(value >= 0 && divisor > 0); return value / divisor + if (value % divisor == 0L) 0 else 1 }
    private fun safeAdd(a: Long, b: Long): Long {
        if (b > 0L && a > Long.MAX_VALUE - b || b < 0L && a < Long.MIN_VALUE - b) throw ArithmeticException("overflow")
        return a + b
    }
    private fun safeSubtract(a: Long, b: Long): Long {
        if (b == Long.MIN_VALUE) { if (a >= 0L) throw ArithmeticException("overflow") } else return safeAdd(a, -b)
        return a - b
    }
    private fun safeMultiply(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val result = a * b
        if (result / b != a) throw ArithmeticException("overflow")
        return result
    }
}
