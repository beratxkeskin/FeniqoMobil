package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderKey
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlanSubscriptionPaymentRemindersUseCaseTest {

    private val useCase = PlanSubscriptionPaymentRemindersUseCase()
    private val today = LocalDate(2026, 9, 1)

    @Test
    fun exactSevenDaysBeforeRenewal_producesUpcomingCandidate() {
        val subscription = createSubscription(
            id = "sub-netflix",
            name = "Netflix",
            nextRenewalDate = LocalDate(2026, 9, 8),
            isActive = true,
        )

        val candidates = useCase(
            subscriptions = listOf(subscription),
            today = today,
        )

        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals(EntityId("sub-netflix"), candidate.subscriptionId)
        assertEquals("Netflix", candidate.subscriptionName)
        assertEquals(LocalDate(2026, 9, 8), candidate.nextRenewalDate)
        assertEquals(SubscriptionReminderKind.UPCOMING, candidate.reminderKind)
        assertEquals(
            SubscriptionPaymentReminderKey(
                subscriptionId = EntityId("sub-netflix"),
                nextRenewalDate = LocalDate(2026, 9, 8),
                reminderKind = SubscriptionReminderKind.UPCOMING,
            ),
            candidate.key,
        )
        assertEquals("sub-netflix_2026-09-08_UPCOMING", candidate.stableKey)
    }

    @Test
    fun sixDaysAndOtherDaysBeforeRenewal_producesNoCandidate() {
        val daysBeforeList = listOf(6, 5, 4, 3, 2, 1)
        for (days in daysBeforeList) {
            val renewalDate = LocalDate(2026, 9, 1 + days)
            val subscription = createSubscription(
                id = "sub-$days",
                name = "Sub $days",
                nextRenewalDate = renewalDate,
                isActive = true,
            )

            val candidates = useCase(
                subscriptions = listOf(subscription),
                today = today,
            )

            assertTrue(
                candidates.isEmpty(),
                "Expected no candidate for $days days before renewal, but got ${candidates.size}",
            )
        }
    }

    @Test
    fun dueToday_producesDueTodayCandidate() {
        val subscription = createSubscription(
            id = "sub-spotify",
            name = "Spotify",
            nextRenewalDate = today,
            isActive = true,
        )

        val candidates = useCase(
            subscriptions = listOf(subscription),
            today = today,
        )

        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals(EntityId("sub-spotify"), candidate.subscriptionId)
        assertEquals("Spotify", candidate.subscriptionName)
        assertEquals(today, candidate.nextRenewalDate)
        assertEquals(SubscriptionReminderKind.DUE_TODAY, candidate.reminderKind)
        assertEquals(
            SubscriptionPaymentReminderKey(
                subscriptionId = EntityId("sub-spotify"),
                nextRenewalDate = today,
                reminderKind = SubscriptionReminderKind.DUE_TODAY,
            ),
            candidate.key,
        )
        assertEquals("sub-spotify_2026-09-01_DUE_TODAY", candidate.stableKey)
    }

    @Test
    fun inactivePastOrFarScheduledSubscriptions_produceNoCandidate() {
        val inactiveDueToday = createSubscription(
            id = "sub-inactive-due",
            nextRenewalDate = today,
            isActive = false,
        )
        val inactiveUpcoming = createSubscription(
            id = "sub-inactive-up",
            nextRenewalDate = LocalDate(2026, 9, 8),
            isActive = false,
        )
        val overdueSubscription = createSubscription(
            id = "sub-overdue",
            nextRenewalDate = LocalDate(2026, 8, 31),
            isActive = true,
        )
        val farScheduledSubscription = createSubscription(
            id = "sub-far",
            nextRenewalDate = LocalDate(2026, 9, 15),
            isActive = true,
        )

        val candidates = useCase(
            subscriptions = listOf(
                inactiveDueToday,
                inactiveUpcoming,
                overdueSubscription,
                farScheduledSubscription,
            ),
            today = today,
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun candidates_areDeterministicallySortedByRenewalDateThenId() {
        val subTodayB = createSubscription(
            id = "sub-b",
            name = "Sub B",
            nextRenewalDate = today,
            isActive = true,
        )
        val subTodayA = createSubscription(
            id = "sub-a",
            name = "Sub A",
            nextRenewalDate = today,
            isActive = true,
        )
        val subUpcoming2 = createSubscription(
            id = "sub-2",
            name = "Sub 2",
            nextRenewalDate = LocalDate(2026, 9, 8),
            isActive = true,
        )
        val subUpcoming1 = createSubscription(
            id = "sub-1",
            name = "Sub 1",
            nextRenewalDate = LocalDate(2026, 9, 8),
            isActive = true,
        )

        // Pass in random order
        val candidates = useCase(
            subscriptions = listOf(subUpcoming2, subTodayB, subUpcoming1, subTodayA),
            today = today,
        )

        assertEquals(4, candidates.size)
        assertEquals("sub-a", candidates[0].subscriptionId.value)
        assertEquals(today, candidates[0].nextRenewalDate)
        assertEquals(SubscriptionReminderKind.DUE_TODAY, candidates[0].reminderKind)

        assertEquals("sub-b", candidates[1].subscriptionId.value)
        assertEquals(today, candidates[1].nextRenewalDate)
        assertEquals(SubscriptionReminderKind.DUE_TODAY, candidates[1].reminderKind)

        assertEquals("sub-1", candidates[2].subscriptionId.value)
        assertEquals(LocalDate(2026, 9, 8), candidates[2].nextRenewalDate)
        assertEquals(SubscriptionReminderKind.UPCOMING, candidates[2].reminderKind)

        assertEquals("sub-2", candidates[3].subscriptionId.value)
        assertEquals(LocalDate(2026, 9, 8), candidates[3].nextRenewalDate)
        assertEquals(SubscriptionReminderKind.UPCOMING, candidates[3].reminderKind)
    }

    @Test
    fun duplicateInput_failsClosedWithIllegalArgumentException() {
        val sub = createSubscription(
            id = "sub-dup",
            nextRenewalDate = today,
            isActive = true,
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(
                subscriptions = listOf(sub, sub),
                today = today,
            )
        }
    }

    @Test
    fun negativeUpcomingWindow_failsClosedWithIllegalArgumentException() {
        val sub = createSubscription(
            id = "sub-1",
            nextRenewalDate = today,
            isActive = true,
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(
                subscriptions = listOf(sub),
                today = today,
                upcomingWindowDays = -1,
            )
        }
    }

    private fun createSubscription(
        id: String,
        name: String = "Test Subscription",
        nextRenewalDate: LocalDate,
        isActive: Boolean = true,
    ): Subscription {
        return Subscription(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = name,
            amount = Money(2999L, Currency.TRY),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 1, 1),
                endDate = null,
            ),
            nextRenewalDate = nextRenewalDate,
            isActive = isActive,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
    }
}
