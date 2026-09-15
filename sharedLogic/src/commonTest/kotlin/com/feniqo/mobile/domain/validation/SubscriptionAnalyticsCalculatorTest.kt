package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPaymentSourceType
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionAnalyticsCalculatorTest {

    private fun createSubscription(
        id: String = "sub-1",
        name: String = "Netflix",
        amountMinor: Long = 10_000L,
        currency: Currency = Currency.TRY,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate(2026, 1, 1),
        nextRenewalDate: LocalDate = LocalDate(2026, 9, 15),
        lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
        trialEndDate: LocalDate? = null,
        cancellationDate: LocalDate? = null,
    ): Subscription {
        return Subscription(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = name,
            amount = Money(amountMinor, currency),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = null,
            ),
            nextRenewalDate = nextRenewalDate,
            lifecycleStatus = lifecycleStatus,
            trialEndDate = trialEndDate,
            cancellationDate = cancellationDate,
            createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        )
    }

    @Test
    fun testEstimatedNormalizedCostFrequencies() {
        // Daily: 10 TL/gün * 365 = 3650 TL/yıl -> Aylık: (365000 + 6) / 12 = 30417 kuruş (~304.17 TL)
        val dailySub = createSubscription(
            amountMinor = 1_000L,
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
        )
        val dailyYearly = SubscriptionAnalyticsCalculator.calculateYearlyEstimatedMinor(dailySub)
        assertEquals(365_000L, dailyYearly)
        val dailyMonthly = SubscriptionAnalyticsCalculator.calculateMonthlyEstimatedMinor(dailySub)
        assertEquals((365_000L + 6L) / 12L, dailyMonthly)

        // Weekly: 100 TL/hafta * 52 = 5200 TL/yıl -> Aylık: (520000 + 6) / 12 = 43333 (~433.33 TL)
        val weeklySub = createSubscription(
            amountMinor = 10_000L,
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
        )
        val weeklyYearly = SubscriptionAnalyticsCalculator.calculateYearlyEstimatedMinor(weeklySub)
        assertEquals(520_000L, weeklyYearly)
        val weeklyMonthly = SubscriptionAnalyticsCalculator.calculateMonthlyEstimatedMinor(weeklySub)
        assertEquals((520_000L + 6L) / 12L, weeklyMonthly)

        // Monthly: 120 TL/ay * 12 = 1440 TL/yıl -> Aylık: 120 TL (12000 kuruş)
        val monthlySub = createSubscription(
            amountMinor = 12_000L,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
        )
        val monthlyYearly = SubscriptionAnalyticsCalculator.calculateYearlyEstimatedMinor(monthlySub)
        assertEquals(144_000L, monthlyYearly)
        val monthlyMonthly = SubscriptionAnalyticsCalculator.calculateMonthlyEstimatedMinor(monthlySub)
        assertEquals(12_000L, monthlyMonthly)

        // Yearly: 1200 TL/yıl -> Aylık: 100 TL (10000 kuruş)
        val yearlySub = createSubscription(
            amountMinor = 120_000L,
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
        )
        val yearlyYearly = SubscriptionAnalyticsCalculator.calculateYearlyEstimatedMinor(yearlySub)
        assertEquals(120_000L, yearlyYearly)
        val yearlyMonthly = SubscriptionAnalyticsCalculator.calculateMonthlyEstimatedMinor(yearlySub)
        assertEquals(10_000L, yearlyMonthly)
    }

    @Test
    fun testEstimatedCostIntervalGreaterThanOne() {
        // Her 3 ayda 300 TL -> Yıllık: 300 * 12 / 3 = 1200 TL. Aylık: 100 TL (10000 kuruş)
        val quarterlySub = createSubscription(
            amountMinor = 30_000L,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 3,
        )
        val yearly = SubscriptionAnalyticsCalculator.calculateYearlyEstimatedMinor(quarterlySub)
        assertEquals(120_000L, yearly)
        val monthly = SubscriptionAnalyticsCalculator.calculateMonthlyEstimatedMinor(quarterlySub)
        assertEquals(10_000L, monthly)
    }

    @Test
    fun testCurrencySeparationNeverCombinesDifferentCurrencies() {
        val trySub = createSubscription(id = "sub-try", amountMinor = 10_000L, currency = Currency.TRY)
        val usdSub = createSubscription(id = "sub-usd", amountMinor = 1_000L, currency = Currency.USD)
        val eurSub = createSubscription(id = "sub-eur", amountMinor = 2_000L, currency = Currency.EUR)

        val costs = SubscriptionAnalyticsCalculator.calculateEstimatedCosts(listOf(trySub, usdSub, eurSub))

        assertEquals(3, costs.size)
        assertEquals(10_000L, costs[Currency.TRY]?.monthlyEstimatedMinor)
        assertEquals(1_000L, costs[Currency.USD]?.monthlyEstimatedMinor)
        assertEquals(2_000L, costs[Currency.EUR]?.monthlyEstimatedMinor)
    }

    @Test
    fun testPausedAndCancelledExcludedFromEstimatedCost() {
        val active = createSubscription(id = "sub-1", lifecycleStatus = SubscriptionLifecycleStatus.ACTIVE, amountMinor = 10_000L)
        val trial = createSubscription(id = "sub-2", lifecycleStatus = SubscriptionLifecycleStatus.TRIAL, trialEndDate = LocalDate(2026, 9, 20), amountMinor = 5_000L)
        val paused = createSubscription(id = "sub-3", lifecycleStatus = SubscriptionLifecycleStatus.PAUSED, amountMinor = 20_000L)
        val cancelled = createSubscription(
            id = "sub-4",
            lifecycleStatus = SubscriptionLifecycleStatus.CANCELLED,
            cancellationDate = LocalDate(2026, 9, 1),
            amountMinor = 30_000L,
        )

        val costs = SubscriptionAnalyticsCalculator.calculateEstimatedCosts(listOf(active, trial, paused, cancelled))

        assertEquals(15_000L, costs[Currency.TRY]?.monthlyEstimatedMinor)
        assertEquals(2, costs[Currency.TRY]?.activeSubscriptionCount)
    }

    @Test
    fun testOverflowProtectionFailsClosed() {
        val hugeSubs = (1..35).map { idx ->
            createSubscription(
                id = "huge-$idx",
                amountMinor = Money.MAX_AMOUNT_MINOR,
                frequency = RecurrenceFrequency.DAILY,
            )
        }
        val costs = SubscriptionAnalyticsCalculator.calculateEstimatedCosts(hugeSubs)

        val summary = costs[Currency.TRY]
        assertNotNull(summary)
        assertTrue(summary.isOverflowOrUnavailable)
        assertEquals(0L, summary.monthlyEstimatedMinor)
    }

    @Test
    fun testActualMonthlySpendingCalculatesOnlyRealPayments() {
        val p1 = SubscriptionPayment(
            id = EntityId("p-1"),
            subscriptionId = EntityId("sub-1"),
            amount = Money(15_000L, Currency.TRY),
            paymentDate = LocalDate(2026, 9, 5),
            renewalDueDate = LocalDate(2026, 9, 5),
            sourceType = SubscriptionPaymentSourceType.MANUAL,
            createdAt = Instant.fromEpochMilliseconds(1_000L),
        )
        val p2 = SubscriptionPayment(
            id = EntityId("p-2"),
            subscriptionId = EntityId("sub-2"),
            amount = Money(25_000L, Currency.TRY),
            paymentDate = LocalDate(2026, 9, 12),
            renewalDueDate = LocalDate(2026, 9, 12),
            sourceType = SubscriptionPaymentSourceType.AUTOMATIC,
            createdAt = Instant.fromEpochMilliseconds(2_000L),
        )
        val pOld = SubscriptionPayment(
            id = EntityId("p-3"),
            subscriptionId = EntityId("sub-1"),
            amount = Money(15_000L, Currency.TRY),
            paymentDate = LocalDate(2026, 8, 5),
            renewalDueDate = LocalDate(2026, 8, 5),
            sourceType = SubscriptionPaymentSourceType.MANUAL,
            createdAt = Instant.fromEpochMilliseconds(500L),
        )

        val sepSpending = SubscriptionAnalyticsCalculator.calculateActualMonthlySpending(listOf(p1, p2, pOld), "2026-09")
        assertEquals(40_000L, sepSpending[Currency.TRY])

        val augSpending = SubscriptionAnalyticsCalculator.calculateActualMonthlySpending(listOf(p1, p2, pOld), "2026-08")
        assertEquals(15_000L, augSpending[Currency.TRY])
    }

    @Test
    fun testMonthlyTrendBasisPointsAndZeroPreviousMonth() {
        val pAug = SubscriptionPayment(
            id = EntityId("p-aug"),
            subscriptionId = EntityId("sub-1"),
            amount = Money(10_000L, Currency.TRY),
            paymentDate = LocalDate(2026, 8, 1),
            renewalDueDate = LocalDate(2026, 8, 1),
            sourceType = SubscriptionPaymentSourceType.MANUAL,
            createdAt = Instant.fromEpochMilliseconds(1_000L),
        )
        val pSep = SubscriptionPayment(
            id = EntityId("p-sep"),
            subscriptionId = EntityId("sub-1"),
            amount = Money(12_000L, Currency.TRY),
            paymentDate = LocalDate(2026, 9, 1),
            renewalDueDate = LocalDate(2026, 9, 1),
            sourceType = SubscriptionPaymentSourceType.MANUAL,
            createdAt = Instant.fromEpochMilliseconds(2_000L),
        )

        // 100 TL'den 120 TL'ye: +20 TL -> +%20 = 2000 basis points
        val trend = SubscriptionAnalyticsCalculator.calculateMonthlyTrend(
            payments = listOf(pAug, pSep),
            currentYearMonth = "2026-09",
            previousYearMonth = "2026-08",
        )
        val tryTrend = trend[Currency.TRY]
        assertNotNull(tryTrend)
        assertEquals(2_000L, tryTrend.diffMinor)
        assertEquals(2000L, tryTrend.percentageBasisPoints)
        assertFalse(tryTrend.isPreviousMonthZero)

        // Önceki ay ödeme yoksa:
        val trendNoPrev = SubscriptionAnalyticsCalculator.calculateMonthlyTrend(
            payments = listOf(pSep),
            currentYearMonth = "2026-09",
            previousYearMonth = "2026-08",
        )
        val tryNoPrev = trendNoPrev[Currency.TRY]
        assertNotNull(tryNoPrev)
        assertNull(tryNoPrev.percentageBasisPoints)
        assertTrue(tryNoPrev.isPreviousMonthZero)
    }

    @Test
    fun testPriceHistoryBasisPoints() {
        val history = SubscriptionPriceHistory(
            id = EntityId("h-1"),
            subscriptionId = EntityId("sub-1"),
            oldAmount = Money(10_000L, Currency.TRY),
            newAmount = Money(12_500L, Currency.TRY),
            changedAt = Instant.fromEpochMilliseconds(1_000L),
        )
        assertTrue(history.isPriceIncreased)
        assertEquals(2_500L, history.increaseAmountMinor)
        // (2500 * 10000) / 10000 = 2500 bp (%25)
        assertEquals(2500L, history.increaseBasisPoints)
    }

    @Test
    fun testUpcomingAndOverdueFilters() {
        val today = LocalDate(2026, 9, 10)

        val overdueSub = createSubscription(id = "s-overdue", nextRenewalDate = LocalDate(2026, 9, 8))
        val todaySub = createSubscription(id = "s-today", nextRenewalDate = LocalDate(2026, 9, 10))
        val upcomingSub = createSubscription(id = "s-upcoming", nextRenewalDate = LocalDate(2026, 9, 17)) // +7 days
        val distantSub = createSubscription(id = "s-distant", nextRenewalDate = LocalDate(2026, 9, 18)) // +8 days (outside 7 day window)
        val pausedSub = createSubscription(id = "s-paused", nextRenewalDate = LocalDate(2026, 9, 12), lifecycleStatus = SubscriptionLifecycleStatus.PAUSED)

        val all = listOf(overdueSub, todaySub, upcomingSub, distantSub, pausedSub)

        val upcoming = SubscriptionAnalyticsCalculator.filterUpcoming(all, today, windowDays = 7)
        assertEquals(listOf("s-today", "s-upcoming"), upcoming.map { it.id.value })

        val overdue = SubscriptionAnalyticsCalculator.filterOverdue(all, today)
        assertEquals(listOf("s-overdue"), overdue.map { it.id.value })
    }

    @Test
    fun testGenerateInsights() {
        val today = LocalDate(2026, 9, 10)

        val sub1 = createSubscription(id = "s-1", name = "Netflix", amountMinor = 22_900L)
        val trialSub = createSubscription(
            id = "s-2",
            name = "Figma",
            lifecycleStatus = SubscriptionLifecycleStatus.TRIAL,
            trialEndDate = LocalDate(2026, 9, 15), // 5 days left
        )
        val pausedSub = createSubscription(
            id = "s-3",
            name = "Spotify",
            amountMinor = 10_900L,
            lifecycleStatus = SubscriptionLifecycleStatus.PAUSED,
        )

        val priceHistory = SubscriptionPriceHistory(
            id = EntityId("ph-1"),
            subscriptionId = EntityId("s-1"),
            oldAmount = Money(19_900L, Currency.TRY),
            newAmount = Money(22_900L, Currency.TRY),
            changedAt = Instant.fromEpochMilliseconds(5_000L),
        )

        val insights = SubscriptionAnalyticsCalculator.generateInsights(
            subscriptions = listOf(sub1, trialSub, pausedSub),
            priceHistories = listOf(priceHistory),
            today = today,
        )

        // En az TrialEndingSoon, PriceIncreases, PausedSavings ve TopCost üretilmeli
        assertTrue(insights.any { it is SubscriptionInsight.TrialEndingSoon })
        assertTrue(insights.any { it is SubscriptionInsight.PriceIncreases })
        assertTrue(insights.any { it is SubscriptionInsight.PausedSavings })
        assertTrue(insights.any { it is SubscriptionInsight.TopCost })
    }
}
