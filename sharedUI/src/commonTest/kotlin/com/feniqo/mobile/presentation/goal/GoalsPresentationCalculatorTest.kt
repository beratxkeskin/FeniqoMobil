package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsPresentationCalculatorTest {
    @Test fun singleCurrency_summaryUsesSafeTotalsAndAverage() {
        val result = GoalsPresentationCalculator.calculate(listOf(goal("a", 100, 20), goal("b", 100, 60)), GoalStatusFilter.ALL)
        assertEquals(80, result.summary!!.currencySummaries.single().savedAmount.amountMinor)
        assertEquals(200, result.summary.currencySummaries.single().targetAmount.amountMinor)
        assertEquals(4_000, result.summary.averageProgressBasisPoints!!.value)
    }

    @Test fun multipleCurrencies_remainSeparate() {
        val result = GoalsPresentationCalculator.calculate(listOf(goal("try", 100, 20, Currency.TRY), goal("usd", 200, 40, Currency.USD)), GoalStatusFilter.ALL)
        assertEquals(listOf(Currency.TRY, Currency.USD), result.summary!!.currencySummaries.map { it.currency })
        assertEquals(20, result.summary.currencySummaries[0].savedAmount.amountMinor)
        assertEquals(40, result.summary.currencySummaries[1].savedAmount.amountMinor)
    }

    @Test fun overflow_marksOnlySummaryAsUnsafe() {
        val result = GoalsPresentationCalculator.calculate(listOf(goal("a", Money.MAX_AMOUNT_MINOR, 1), goal("b", Money.MAX_AMOUNT_MINOR, 1)), GoalStatusFilter.ALL)
        assertTrue(result.isSummaryCalculationError)
        assertNull(result.summary)
        assertEquals(2, result.visibleGoals.size)
    }

    @Test fun filtersKeepExpectedStatuses() {
        val items = listOf(goal("active", 100, 20), goal("done", 100, 100))
        assertEquals(2, GoalsPresentationCalculator.calculate(items, GoalStatusFilter.ALL).visibleGoals.size)
        assertEquals(listOf("active"), GoalsPresentationCalculator.calculate(items, GoalStatusFilter.ACTIVE).visibleGoals.map { it.id.value })
        assertEquals(listOf("done"), GoalsPresentationCalculator.calculate(items, GoalStatusFilter.ACHIEVED).visibleGoals.map { it.id.value })
    }

    @Test fun closestInsightAndPastDateAreDeterministic() {
        val result = GoalsPresentationCalculator.calculate(listOf(goal("late", 100, 60, isPastDate = true), goal("close", 100, 80)), GoalStatusFilter.ALL)
        assertEquals("closest-close", result.insights.first().id)
        assertTrue(result.insights.any { it.id == "past-date-count" })
    }

    private fun goal(id: String, target: Long, current: Long, currency: Currency = Currency.TRY, isPastDate: Boolean = false): GoalDisplayModel {
        val domain = Goal(EntityId(id), EntityId("owner"), null, id, Money(target, currency), Money(current, currency), LocalDate(2026, 12, 31), CategoryColor("#2E7D32"), null, Instant.DISTANT_PAST)
        return GoalDisplayModelMapper.mapItem(domain).copy(isTargetDatePast = isPastDate)
    }
}
