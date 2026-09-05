package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalProgressCalculatorTest {

    private val testGoal = Goal(
        id = EntityId("goal-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Yeni Araba",
        targetAmount = Money(100_000, Currency.TRY),
        currentAmount = Money(0, Currency.TRY),
        targetDate = LocalDate(2027, 6, 1),
        color = CategoryColor("#0A7A55"),
        icon = CategoryIcon("car"),
        createdAt = NOW,
    )

    @Test
    fun calculate_initial_progress_starts_with_zero_and_in_progress() {
        val progress = GoalProgressCalculator.calculateProgress(testGoal)

        assertEquals(Money(0, Currency.TRY), progress.currentAmount)
        assertEquals(Money(100_000, Currency.TRY), progress.targetAmount)
        assertEquals(Money(100_000, Currency.TRY), progress.remainingAmount)
        assertEquals(GoalStatus.IN_PROGRESS, progress.status)
        assertEquals(RateBasisPoints(0), progress.progressBasisPoints)
        assertFalse(progress.isAchieved)
    }

    @Test
    fun calculate_partial_progress_correctly_computes_basis_points_and_remaining() {
        val partialGoal = testGoal.copy(currentAmount = Money(45_000, Currency.TRY))
        val progress = GoalProgressCalculator.calculateProgress(partialGoal)

        assertEquals(Money(45_000, Currency.TRY), progress.currentAmount)
        assertEquals(Money(55_000, Currency.TRY), progress.remainingAmount)
        assertEquals(GoalStatus.IN_PROGRESS, progress.status)
        assertEquals(RateBasisPoints(4_500), progress.progressBasisPoints) // 45.00%
        assertFalse(progress.isAchieved)
    }

    @Test
    fun calculate_progress_achieved_when_target_reached_exactly() {
        val achievedGoal = testGoal.copy(currentAmount = Money(100_000, Currency.TRY))
        val progress = GoalProgressCalculator.calculateProgress(achievedGoal)

        assertEquals(Money(100_000, Currency.TRY), progress.currentAmount)
        assertEquals(Money(0, Currency.TRY), progress.remainingAmount)
        assertEquals(GoalStatus.ACHIEVED, progress.status)
        assertEquals(RateBasisPoints(10_000), progress.progressBasisPoints) // 100.00%
        assertTrue(progress.isAchieved)
    }

    @Test
    fun calculate_progress_accepts_target_overshoot_without_error_and_preserves_amount() {
        val overachievedGoal = testGoal.copy(currentAmount = Money(150_000, Currency.TRY))
        val progress = GoalProgressCalculator.calculateProgress(overachievedGoal)

        assertEquals(Money(150_000, Currency.TRY), progress.currentAmount)
        assertEquals(Money(0, Currency.TRY), progress.remainingAmount)
        assertEquals(GoalStatus.ACHIEVED, progress.status)
        assertEquals(RateBasisPoints(15_000), progress.progressBasisPoints) // 150.00%
        assertTrue(progress.isAchieved)
    }

    @Test
    fun add_contribution_increases_current_amount() {
        val contribution1 = GoalContribution(
            id = EntityId("contrib-1"),
            goalId = testGoal.id,
            amount = Money(25_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "İlk birikim",
            createdAt = NOW,
        )
        val goalAfterAdd1 = GoalProgressCalculator.applyContribution(testGoal, contribution1)
        assertEquals(Money(25_000, Currency.TRY), goalAfterAdd1.currentAmount)

        val contribution2 = GoalContribution(
            id = EntityId("contrib-2"),
            goalId = testGoal.id,
            amount = Money(35_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 15),
            note = "İkinci birikim",
            createdAt = NOW,
        )
        val goalAfterAdd2 = GoalProgressCalculator.applyContribution(goalAfterAdd1, contribution2)
        assertEquals(Money(60_000, Currency.TRY), goalAfterAdd2.currentAmount)
    }

    @Test
    fun remove_contribution_decreases_current_amount() {
        val goalWithFunds = testGoal.copy(currentAmount = Money(60_000, Currency.TRY))
        val removeContribution = GoalContribution(
            id = EntityId("contrib-3"),
            goalId = testGoal.id,
            amount = Money(20_000, Currency.TRY),
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 20),
            note = "Acil çekim",
            createdAt = NOW,
        )
        val updatedGoal = GoalProgressCalculator.applyContribution(goalWithFunds, removeContribution)
        assertEquals(Money(40_000, Currency.TRY), updatedGoal.currentAmount)
    }

    @Test
    fun remove_contribution_can_reduce_balance_to_exact_zero() {
        val goalWithFunds = testGoal.copy(currentAmount = Money(30_000, Currency.TRY))
        val removeAll = GoalContribution(
            id = EntityId("contrib-4"),
            goalId = testGoal.id,
            amount = Money(30_000, Currency.TRY),
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 20),
            note = "Tümünü çek",
            createdAt = NOW,
        )
        val updatedGoal = GoalProgressCalculator.applyContribution(goalWithFunds, removeAll)
        assertEquals(Money(0, Currency.TRY), updatedGoal.currentAmount)
    }

    @Test
    fun remove_contribution_rejects_reducing_balance_below_zero() {
        val goalWithFunds = testGoal.copy(currentAmount = Money(30_000, Currency.TRY))
        val excessRemove = GoalContribution(
            id = EntityId("contrib-5"),
            goalId = testGoal.id,
            amount = Money(30_001, Currency.TRY),
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 20),
            note = "Fazla çekim denemesi",
            createdAt = NOW,
        )
        assertFailsWith<IllegalArgumentException> {
            GoalProgressCalculator.applyContribution(goalWithFunds, excessRemove)
        }
    }

    @Test
    fun apply_contribution_rejects_currency_mismatch() {
        val usdContribution = GoalContribution(
            id = EntityId("contrib-6"),
            goalId = testGoal.id,
            amount = Money(500, Currency.USD),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "Dolar katkısı",
            createdAt = NOW,
        )
        assertFailsWith<IllegalArgumentException> {
            GoalProgressCalculator.applyContribution(testGoal, usdContribution)
        }
    }

    @Test
    fun apply_contribution_rejects_mismatched_goal_id() {
        val otherGoalContribution = GoalContribution(
            id = EntityId("contrib-7"),
            goalId = EntityId("other-goal"),
            amount = Money(5_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "Başka hedef",
            createdAt = NOW,
        )
        assertFailsWith<IllegalArgumentException> {
            GoalProgressCalculator.applyContribution(testGoal, otherGoalContribution)
        }
    }

    @Test
    fun calculate_progress_rejects_currency_mismatch() {
        assertFailsWith<IllegalArgumentException> {
            GoalProgressCalculator.calculateProgress(
                targetAmount = Money(100_000, Currency.TRY),
                currentAmount = Money(10_000, Currency.USD),
            )
        }
    }

    @Test
    fun add_contribution_rejects_overflow_exceeding_money_max_amount() {
        val nearMaxGoal = testGoal.copy(currentAmount = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY))
        val overflowContrib = GoalContribution(
            id = EntityId("contrib-overflow"),
            goalId = testGoal.id,
            amount = Money(1, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "Taşma denemesi",
            createdAt = NOW,
        )
        assertFailsWith<IllegalStateException> {
            GoalProgressCalculator.applyContribution(nearMaxGoal, overflowContrib)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T12:00:00Z")
    }
}
