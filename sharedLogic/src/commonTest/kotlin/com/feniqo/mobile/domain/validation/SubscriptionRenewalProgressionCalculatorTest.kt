package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubscriptionRenewalProgressionCalculatorTest {

    private fun createSubscription(
        rule: RecurrenceRule,
        nextRenewalDate: LocalDate,
        isActive: Boolean = true,
    ): Subscription {
        return Subscription(
            id = EntityId("sub-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Test Subscription",
            amount = Money(9900L, Currency.TRY),
            categoryId = null,
            renewalRule = rule,
            nextRenewalDate = nextRenewalDate,
            isActive = isActive,
            createdAt = Instant.fromEpochMilliseconds(0L),
        )
    }

    @Test
    fun dailyProgression_intervalOneAndMultiple() {
        val rule1 = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )
        val result1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule1,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 8, 2)),
            result1,
        )

        val rule3 = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 3,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )
        val result3 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule3,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 8, 4)),
            result3,
        )
    }

    @Test
    fun weeklyProgression_intervalOneAndMultiple() {
        val rule1 = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1), // Saturday
            endDate = null,
        )
        val result1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule1,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 8, 8)),
            result1,
        )

        val rule2 = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )
        val result2 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule2,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 8, 15)),
            result2,
        )
    }

    @Test
    fun monthlyProgression_intervalOneAndMultiple() {
        val rule1 = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 15),
            endDate = null,
        )
        val result1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule1,
            currentNextRenewalDate = LocalDate(2026, 8, 15),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 9, 15)),
            result1,
        )

        val rule3 = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 3,
            startDate = LocalDate(2026, 1, 15),
            endDate = null,
        )
        val result3 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule3,
            currentNextRenewalDate = LocalDate(2026, 1, 15),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 4, 15)),
            result3,
        )
    }

    @Test
    fun yearlyProgression_intervalOneAndMultiple() {
        val rule1 = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )
        val result1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule1,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2027, 8, 1)),
            result1,
        )

        val rule2 = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 2,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )
        val result2 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule2,
            currentNextRenewalDate = LocalDate(2026, 8, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2028, 8, 1)),
            result2,
        )
    }

    @Test
    fun monthEndAndLeapYear_clampingAndPreservation() {
        // Non-leap year: Jan 31 -> Feb 28 -> Mar 31
        val nonLeapRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 31),
            endDate = null,
        )
        val nextFeb = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = nonLeapRule,
            currentNextRenewalDate = LocalDate(2026, 1, 31),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 2, 28)),
            nextFeb,
        )

        val nextMar = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = nonLeapRule,
            currentNextRenewalDate = LocalDate(2026, 2, 28),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 3, 31)),
            nextMar,
        )

        // Leap year (2024): Jan 31 -> Feb 29
        val leapRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2024, 1, 31),
            endDate = null,
        )
        val leapFeb = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = leapRule,
            currentNextRenewalDate = LocalDate(2024, 1, 31),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2024, 2, 29)),
            leapFeb,
        )

        // Yearly leap day: 2024-02-29 -> 2025-02-28 -> 2028-02-29
        val yearlyLeapRule = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
            startDate = LocalDate(2024, 2, 29),
            endDate = null,
        )
        val yearlyNext1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = yearlyLeapRule,
            currentNextRenewalDate = LocalDate(2024, 2, 29),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2025, 2, 28)),
            yearlyNext1,
        )

        val yearlyLeapRule4 = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 4,
            startDate = LocalDate(2024, 2, 29),
            endDate = null,
        )
        val yearlyNext4 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = yearlyLeapRule4,
            currentNextRenewalDate = LocalDate(2024, 2, 29),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2028, 2, 29)),
            yearlyNext4,
        )
    }

    @Test
    fun pastDueDate_advancesSinglePeriodWithoutJumpingPastToday() {
        // Assume today is 2026-09-01, but the subscription's nextRenewalDate is far in the past (e.g. 2026-05-01).
        // Paying it must advance to 2026-06-01 (exactly 1 period), NOT jumping to 2026-09-01 or 2026-10-01.
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )
        val subscription = createSubscription(
            rule = rule,
            nextRenewalDate = LocalDate(2026, 5, 1),
            isActive = true,
        )

        val result = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(subscription)
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 6, 1)),
            result,
        )
    }

    @Test
    fun endDateReached_returnsCompleted() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 3, 1),
        )

        // Advancing from 2026-01-01 -> 2026-02-01 (Advanced)
        val result1 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule,
            currentNextRenewalDate = LocalDate(2026, 1, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 2, 1)),
            result1,
        )

        // Advancing from 2026-02-01 -> 2026-03-01 (Advanced to endDate)
        val result2 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule,
            currentNextRenewalDate = LocalDate(2026, 2, 1),
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 3, 1)),
            result2,
        )

        // Advancing from 2026-03-01 -> past endDate (Completed)
        val subscription = createSubscription(
            rule = rule,
            nextRenewalDate = LocalDate(2026, 3, 1),
            isActive = true,
        )
        val result3 = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(subscription)
        assertEquals(
            SubscriptionRenewalProgressionResult.Completed,
            result3,
        )
    }

    @Test
    fun inactiveSubscription_isRejectedFailClosed() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )
        val subscription = createSubscription(
            rule = rule,
            nextRenewalDate = LocalDate(2026, 1, 1),
            isActive = false,
        )

        val resultFromSub = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(subscription)
        assertEquals(
            SubscriptionRenewalProgressionResult.InactiveSubscription,
            resultFromSub,
        )

        val resultFromParams = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
            rule = rule,
            currentNextRenewalDate = LocalDate(2026, 1, 1),
            isActive = false,
        )
        assertEquals(
            SubscriptionRenewalProgressionResult.InactiveSubscription,
            resultFromParams,
        )
    }

    @Test
    fun invalidSchedule_throwsIllegalArgumentException() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 15),
            endDate = null,
        )

        // currentNextRenewalDate before startDate
        assertFailsWith<IllegalArgumentException> {
            SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
                rule = rule,
                currentNextRenewalDate = LocalDate(2025, 12, 15),
            )
        }

        // currentNextRenewalDate not aligned with rule's dayOfMonth anchor (e.g. 1st instead of 15th)
        assertFailsWith<IllegalArgumentException> {
            SubscriptionRenewalProgressionCalculator.calculateNextRenewal(
                rule = rule,
                currentNextRenewalDate = LocalDate(2026, 2, 1),
            )
        }
    }
}

