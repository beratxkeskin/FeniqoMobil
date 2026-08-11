package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecondWaveModelsTest {

    @Test
    fun recurrence_rule_rejects_an_end_before_its_start() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate(2026, 8, 5),
                endDate = LocalDate(2026, 8, 4),
            )
        }
    }

    @Test
    fun goal_requires_the_same_currency_for_target_and_progress() {
        assertFailsWith<IllegalArgumentException> {
            Goal(
                id = EntityId("goal-1"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                name = "Acil durum fonu",
                targetAmount = Money(100_000, Currency.TRY),
                currentAmount = Money(1_000, Currency.USD),
                targetDate = LocalDate(2027, 1, 1),
                color = CategoryColor("#0A7A55"),
                icon = CategoryIcon("shield"),
                createdAt = NOW,
            )
        }
    }

    @Test
    fun tracked_asset_requires_a_market_symbol() {
        assertFailsWith<IllegalArgumentException> {
            Asset(
                id = EntityId("asset-1"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                name = "Fon",
                type = AssetType.STOCKS,
                currentValue = Money(10_000, Currency.TRY),
                quantity = AssetQuantity(unscaledValue = 15, scale = 0),
                purchaseUnitPrice = null,
                trackingSymbol = null,
                autoTrack = true,
                createdAt = NOW,
            )
        }
    }

    @Test
    fun dashboard_can_represent_a_negative_monthly_balance() {
        val income = Money(10_000, Currency.TRY)
        val expense = Money(12_500, Currency.TRY)
        val balance = MoneyDelta.between(income, expense)

        val dashboard = DashboardSummary(
            month = YearMonth("2026-08"),
            income = income,
            expense = expense,
            balance = balance,
            savingsRate = RateBasisPoints(-2_500),
            topExpenseCategory = null,
            recentTransactionIds = emptyList(),
            moneyScore = null,
        )

        assertEquals(-2_500, dashboard.balance.amountMinor)
    }

    @Test
    fun money_score_must_equal_its_breakdown() {
        val score = MoneyScore(
            total = 75,
            savings = 25,
            budget = 20,
            debt = 15,
            goal = 15,
            level = MoneyScoreLevel.HEALTHY,
        )

        assertEquals(75, score.total)
        assertFailsWith<IllegalArgumentException> {
            score.copy(total = 76)
        }
    }

    @Test
    fun report_period_rejects_reverse_date_range() {
        assertFailsWith<IllegalArgumentException> {
            ReportPeriod(
                startDate = LocalDate(2026, 8, 5),
                endDate = LocalDate(2026, 8, 1),
            )
        }
    }

    @Test
    fun subscription_reuses_recurrence_rule_and_allows_a_missing_category() {
        val subscription = Subscription(
            id = EntityId("subscription-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Müzik",
            amount = Money(5999, Currency.TRY),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate(2026, 8, 5),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 9, 5),
            isActive = true,
            createdAt = NOW,
        )

        assertTrue(subscription.isActive)
        assertEquals(null, subscription.categoryId)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-05T00:00:00Z")
    }
}
