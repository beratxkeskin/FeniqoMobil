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
import kotlin.test.assertIs

class SubscriptionRenewalStatusCalculatorTest {

    private fun createSubscription(
        nextRenewalDate: LocalDate,
        isActive: Boolean = true,
        startDate: LocalDate = LocalDate(2026, 1, 1),
    ): Subscription {
        return Subscription(
            id = EntityId("sub-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Netflix",
            amount = Money(19900L, Currency.TRY),
            categoryId = EntityId("cat-1"),
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = startDate,
                endDate = null,
            ),
            nextRenewalDate = nextRenewalDate,
            isActive = isActive,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
    }

    @Test
    fun calculate_whenSubscriptionIsInactive_returnsInactive() {
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 8, 31),
            isActive = false,
        )
        val today = LocalDate(2026, 8, 31)

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        assertEquals(SubscriptionRenewalStatus.Inactive, status)
    }

    @Test
    fun calculate_whenNextRenewalDateIsToday_returnsDueToday() {
        val today = LocalDate(2026, 8, 31)
        val subscription = createSubscription(
            nextRenewalDate = today,
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        assertEquals(SubscriptionRenewalStatus.DueToday, status)
    }

    @Test
    fun calculate_whenNextRenewalDateIsPast_returnsOverdueWithCorrectDays() {
        val today = LocalDate(2026, 8, 31)
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 8, 25),
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        val overdue = assertIs<SubscriptionRenewalStatus.Overdue>(status)
        assertEquals(6L, overdue.daysOverdue)
    }

    @Test
    fun calculate_whenNextRenewalDateIsExactlyOnUpcomingWindowBoundary_returnsUpcoming() {
        val today = LocalDate(2026, 8, 31)
        // 7 days window: 31 + 7 = Sep 7
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 9, 7),
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        val upcoming = assertIs<SubscriptionRenewalStatus.Upcoming>(status)
        assertEquals(7L, upcoming.daysUntilRenewal)
    }

    @Test
    fun calculate_whenNextRenewalDateIsTomorrow_returnsUpcomingWith1Day() {
        val today = LocalDate(2026, 8, 31)
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        val upcoming = assertIs<SubscriptionRenewalStatus.Upcoming>(status)
        assertEquals(1L, upcoming.daysUntilRenewal)
    }

    @Test
    fun calculate_whenNextRenewalDateIsBeyondUpcomingWindow_returnsScheduled() {
        val today = LocalDate(2026, 8, 31)
        // 8 days away (> 7)
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 9, 8),
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        val scheduled = assertIs<SubscriptionRenewalStatus.Scheduled>(status)
        assertEquals(8L, scheduled.daysUntilRenewal)
    }

    @Test
    fun calculate_whenUpcomingWindowDaysIsNegative_throwsIllegalArgumentException() {
        val today = LocalDate(2026, 8, 31)
        val subscription = createSubscription(
            nextRenewalDate = LocalDate(2026, 9, 5),
            isActive = true,
        )

        assertFailsWith<IllegalArgumentException> {
            SubscriptionRenewalStatusCalculator.calculate(
                subscription = subscription,
                today = today,
                upcomingWindowDays = -1,
            )
        }
    }

    @Test
    fun calculate_acrossYearBoundary_correctlyComputesDaysDifference() {
        val today = LocalDate(2026, 12, 28)
        val nextRenewalDate = LocalDate(2027, 1, 3) // 6 days difference
        val subscription = createSubscription(
            nextRenewalDate = nextRenewalDate,
            isActive = true,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 7,
        )

        val upcoming = assertIs<SubscriptionRenewalStatus.Upcoming>(status)
        assertEquals(6L, upcoming.daysUntilRenewal)
    }

    @Test
    fun calculate_acrossMonthBoundaryAndLeapYear_correctlyComputesDaysDifference() {
        // 2028 is a leap year (Feb 29 exists)
        val today = LocalDate(2028, 2, 27)
        val nextRenewalDate = LocalDate(2028, 3, 2) // 4 days: 28, 29, 1, 2
        val subscription = createSubscription(
            nextRenewalDate = nextRenewalDate,
            isActive = true,
            startDate = LocalDate(2028, 1, 1),
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 3,
        )

        // 4 > 3 -> Scheduled
        val scheduled = assertIs<SubscriptionRenewalStatus.Scheduled>(status)
        assertEquals(4L, scheduled.daysUntilRenewal)
    }

    @Test
    fun calculate_whenUpcomingWindowIsZero_dueTodayIsDueTodayAndTomorrowIsScheduled() {
        val today = LocalDate(2026, 8, 31)
        val subToday = createSubscription(nextRenewalDate = today, isActive = true)
        val subTomorrow = createSubscription(nextRenewalDate = LocalDate(2026, 9, 1), isActive = true)

        val statusToday = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subToday,
            today = today,
            upcomingWindowDays = 0,
        )
        assertEquals(SubscriptionRenewalStatus.DueToday, statusToday)

        val statusTomorrow = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subTomorrow,
            today = today,
            upcomingWindowDays = 0,
        )
        val scheduled = assertIs<SubscriptionRenewalStatus.Scheduled>(statusTomorrow)
        assertEquals(1L, scheduled.daysUntilRenewal)
    }

    @Test
    fun calculate_withVeryDistantFutureDateExceedingIntMaxDays_correctlyReturnsScheduledWithoutOverflow() {
        // Epoch day farkının Int.MAX_VALUE (2,147,483,647 gün ~ 5.8 milyon yıl) mertebesinde veya büyük olduğu senaryo
        val today = LocalDate(1970, 1, 1) // epochDay = 0
        val targetEpochDays = Int.MAX_VALUE.toLong() + 50_000L
        val distantFutureDate = LocalDate.fromEpochDays(targetEpochDays)

        val subscription = createSubscription(
            nextRenewalDate = distantFutureDate,
            isActive = true,
            startDate = today,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 30,
        )

        val scheduled = assertIs<SubscriptionRenewalStatus.Scheduled>(status)
        assertEquals(targetEpochDays, scheduled.daysUntilRenewal)
    }

    @Test
    fun calculate_withVeryDistantPastDateExceedingIntMaxDays_correctlyReturnsOverdueWithoutOverflow() {
        // Geçmiş tarih farkının Int.MAX_VALUE'yu aştığı senaryo
        val distantPastEpochDays = -(Int.MAX_VALUE.toLong() + 50_000L)
        val distantPastDate = LocalDate.fromEpochDays(distantPastEpochDays)
        val today = LocalDate(1970, 1, 1) // epochDay = 0

        val subscription = createSubscription(
            nextRenewalDate = distantPastDate,
            isActive = true,
            startDate = distantPastDate,
        )

        val status = SubscriptionRenewalStatusCalculator.calculate(
            subscription = subscription,
            today = today,
            upcomingWindowDays = 30,
        )

        val overdue = assertIs<SubscriptionRenewalStatus.Overdue>(status)
        assertEquals(Int.MAX_VALUE.toLong() + 50_000L, overdue.daysOverdue)
    }
}
