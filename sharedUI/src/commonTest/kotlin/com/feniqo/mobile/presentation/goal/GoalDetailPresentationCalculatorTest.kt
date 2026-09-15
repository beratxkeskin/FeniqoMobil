package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.*
import kotlinx.datetime.Instant
import kotlin.test.*

class GoalDetailPresentationCalculatorTest {
    private fun goal(current: Long = 50_000, target: Long = 100_000, date: LocalDate = LocalDate(2027, 1, 1), currency: Currency = Currency.TRY) = Goal(
        EntityId("g"), EntityId("u"), null, "Hedef", Money(target,currency), Money(current,currency), date, CategoryColor("#336655"), CategoryIcon("savings"), Instant.fromEpochMilliseconds(0)
    )
    private fun contribution(id:String, amount:Long, direction:GoalContributionDirection, date:LocalDate, currency:Currency=Currency.TRY, created:Long=0)=GoalContribution(EntityId(id),EntityId("g"),Money(amount,currency),direction,date,null,Instant.fromEpochMilliseconds(created))

    @Test fun progressAndRemaining_areExact() { val d=GoalDetailPresentationCalculator.calculate(goal(),emptyList(),LocalDate(2026,1,1)); assertEquals(5_000,d.progress.value); assertEquals("500,00 ₺",d.formattedRemaining) }
    @Test fun exceededGoal_isClampedByUiAndRemainingIsZero() { val d=GoalDetailPresentationCalculator.calculate(goal(120_000),emptyList(),LocalDate(2026,1,1)); assertEquals(12_000,d.progress.value); assertEquals("0,00 ₺",d.formattedRemaining); assertNull(d.monthlyRequired) }
    @Test fun chart_reconstructsOpeningBalanceAndCombinesSameDay() { val items=listOf(contribution("2",10_000,GoalContributionDirection.REMOVE,LocalDate(2026,2,1),created=2),contribution("1",20_000,GoalContributionDirection.ADD,LocalDate(2026,2,1),created=1),contribution("3",30_000,GoalContributionDirection.ADD,LocalDate(2026,3,1))); val d=GoalDetailPresentationCalculator.calculate(goal(),items,LocalDate(2026,1,1)); assertEquals(2,d.chart!!.size); assertEquals(20_000,d.chart!![0].amount.amountMinor); assertEquals(50_000,d.chart!!.last().amount.amountMinor) }
    @Test fun currencyMismatch_hidesChart() { val d=GoalDetailPresentationCalculator.calculate(goal(),listOf(contribution("1",10,GoalContributionDirection.ADD,LocalDate(2026,1,1),Currency.USD)),LocalDate(2026,1,1)); assertNull(d.chart) }
    @Test fun monthlyRequired_usesCeilingAndAtLeastOnePeriod() { val d=GoalDetailPresentationCalculator.calculate(goal(current=0,target=100_001,date=LocalDate(2026,1,2)),emptyList(),LocalDate(2026,1,1)); assertEquals(100_001,d.monthlyRequired!!.amountMinor) }
    @Test fun pastDate_hasNoMonthlyRequired() { val d=GoalDetailPresentationCalculator.calculate(goal(date=LocalDate(2025,1,1)),emptyList(),LocalDate(2026,1,1)); assertNull(d.monthlyRequired); assertEquals("Hedef tarihi geçti",d.statusLabel) }
    @Test fun estimate_requiresThreeDatesAndMeaningfulSpan() { val two=listOf(contribution("1",10_000,GoalContributionDirection.ADD,LocalDate(2026,1,1)),contribution("2",10_000,GoalContributionDirection.ADD,LocalDate(2026,2,1))); assertNull(GoalDetailPresentationCalculator.calculate(goal(),two,LocalDate(2026,3,1)).estimatedCompletion); val three=two+contribution("3",10_000,GoalContributionDirection.ADD,LocalDate(2026,3,1)); assertNotNull(GoalDetailPresentationCalculator.calculate(goal(),three,LocalDate(2026,3,1)).estimatedCompletion) }
    @Test fun nonPositiveNetProgress_hasNoEstimate() { val items=listOf(contribution("1",10_000,GoalContributionDirection.ADD,LocalDate(2026,1,1)),contribution("2",5_000,GoalContributionDirection.REMOVE,LocalDate(2026,2,1)),contribution("3",5_000,GoalContributionDirection.REMOVE,LocalDate(2026,3,1))); assertNull(GoalDetailPresentationCalculator.calculate(goal(),items,LocalDate(2026,3,1)).estimatedCompletion) }
}
