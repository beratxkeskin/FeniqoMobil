package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionDisplayModelMapperTest {

    private val today = LocalDate(2026, 9, 1)

    @Test
    fun map_ordersActiveFirstThenByNextRenewalDateAscendingAndId() {
        val category = sampleCategory("cat-1", "Abonelikler")

        // 4 subscriptions:
        // 1. active, next: 2026-09-15, id: sub-2
        // 2. active, next: 2026-09-01, id: sub-1
        // 3. inactive, next: 2026-09-05, id: sub-4
        // 4. inactive, next: 2026-09-01, id: sub-3
        val sub1 = sampleSubscription(
            id = "sub-1",
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
        )
        val sub2 = sampleSubscription(
            id = "sub-2",
            nextRenewalDate = LocalDate(2026, 9, 15),
            isActive = true,
        )
        val sub3 = sampleSubscription(
            id = "sub-3",
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = false,
        )
        val sub4 = sampleSubscription(
            id = "sub-4",
            nextRenewalDate = LocalDate(2026, 9, 5),
            isActive = false,
        )

        val result = SubscriptionDisplayModelMapper.map(
            subscriptions = listOf(sub4, sub2, sub3, sub1),
            categories = listOf(category),
            today = today,
        )

        assertEquals(4, result.size)
        // Active first: sub-1 (09-01), then sub-2 (09-15)
        assertEquals("sub-1", result[0].id.value)
        assertEquals(LocalDate(2026, 9, 1), result[0].nextRenewalDate)
        assertTrue(result[0].isActive)
        assertFalse(result[0].isPaused)

        assertEquals("sub-2", result[1].id.value)
        assertEquals(LocalDate(2026, 9, 15), result[1].nextRenewalDate)
        assertTrue(result[1].isActive)
        assertFalse(result[1].isPaused)

        // Inactive next: sub-3 (09-01), then sub-4 (09-05)
        assertEquals("sub-3", result[2].id.value)
        assertEquals(LocalDate(2026, 9, 1), result[2].nextRenewalDate)
        assertFalse(result[2].isActive)
        assertTrue(result[2].isPaused)

        assertEquals("sub-4", result[3].id.value)
        assertEquals(LocalDate(2026, 9, 5), result[3].nextRenewalDate)
        assertFalse(result[3].isActive)
        assertTrue(result[3].isPaused)
    }

    @Test
    fun map_formatsFrequencySummariesCorrectly() {
        val category = sampleCategory("cat-1", "Abonelik")

        val dailySingle = sampleSubscription(
            id = "sub-d1",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
        )
        val dailyMultiple = sampleSubscription(
            id = "sub-d3",
            frequency = RecurrenceFrequency.DAILY,
            interval = 3,
        )
        val weeklySingle = sampleSubscription(
            id = "sub-w1",
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
        )
        val weeklyMultiple = sampleSubscription(
            id = "sub-w2",
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
        )
        val monthlySingle = sampleSubscription(
            id = "sub-m1",
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
        )
        val monthlyMultiple = sampleSubscription(
            id = "sub-m6",
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 6,
        )
        val yearlySingle = sampleSubscription(
            id = "sub-y1",
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
        )
        val yearlyMultiple = sampleSubscription(
            id = "sub-y2",
            frequency = RecurrenceFrequency.YEARLY,
            interval = 2,
        )

        val mappedD1 = SubscriptionDisplayModelMapper.mapItem(dailySingle, category, today)
        val mappedD3 = SubscriptionDisplayModelMapper.mapItem(dailyMultiple, category, today)
        val mappedW1 = SubscriptionDisplayModelMapper.mapItem(weeklySingle, category, today)
        val mappedW2 = SubscriptionDisplayModelMapper.mapItem(weeklyMultiple, category, today)
        val mappedM1 = SubscriptionDisplayModelMapper.mapItem(monthlySingle, category, today)
        val mappedM6 = SubscriptionDisplayModelMapper.mapItem(monthlyMultiple, category, today)
        val mappedY1 = SubscriptionDisplayModelMapper.mapItem(yearlySingle, category, today)
        val mappedY2 = SubscriptionDisplayModelMapper.mapItem(yearlyMultiple, category, today)

        assertEquals("Her gün", mappedD1.formattedFrequency)
        assertEquals("Her 3 günde bir", mappedD3.formattedFrequency)
        assertEquals("Her hafta", mappedW1.formattedFrequency)
        assertEquals("Her 2 haftada bir", mappedW2.formattedFrequency)
        assertEquals("Her ay", mappedM1.formattedFrequency)
        assertEquals("Her 6 ayda bir", mappedM6.formattedFrequency)
        assertEquals("Her yıl", mappedY1.formattedFrequency)
        assertEquals("Her 2 yılda bir", mappedY2.formattedFrequency)
    }

    @Test
    fun map_mapsAllRenewalStatusesCorrectly() {
        val category = sampleCategory("cat-1", "Servis")

        val inactive = sampleSubscription(
            id = "sub-inactive",
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = false,
        )
        val overdue = sampleSubscription(
            id = "sub-overdue",
            nextRenewalDate = LocalDate(2026, 8, 30),
            isActive = true,
        )
        val dueToday = sampleSubscription(
            id = "sub-today",
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
        )
        val upcoming = sampleSubscription(
            id = "sub-upcoming",
            nextRenewalDate = LocalDate(2026, 9, 5),
            isActive = true,
        )
        val scheduled = sampleSubscription(
            id = "sub-scheduled",
            nextRenewalDate = LocalDate(2026, 9, 15),
            isActive = true,
        )

        val mappedInactive = SubscriptionDisplayModelMapper.mapItem(inactive, category, today)
        val mappedOverdue = SubscriptionDisplayModelMapper.mapItem(overdue, category, today)
        val mappedToday = SubscriptionDisplayModelMapper.mapItem(dueToday, category, today)
        val mappedUpcoming = SubscriptionDisplayModelMapper.mapItem(upcoming, category, today)
        val mappedScheduled = SubscriptionDisplayModelMapper.mapItem(scheduled, category, today)

        assertEquals(SubscriptionRenewalStatus.Inactive, mappedInactive.renewalStatus)
        assertEquals(SubscriptionRenewalStatus.Overdue(daysOverdue = 2), mappedOverdue.renewalStatus)
        assertEquals(SubscriptionRenewalStatus.DueToday, mappedToday.renewalStatus)
        assertEquals(SubscriptionRenewalStatus.Upcoming(daysUntilRenewal = 4), mappedUpcoming.renewalStatus)
        assertEquals(SubscriptionRenewalStatus.Scheduled(daysUntilRenewal = 14), mappedScheduled.renewalStatus)
    }

    @Test
    fun map_handlesCategoryNullAndMissingInListFallbackSafely() {
        val category = sampleCategory("cat-1", "Müzik")

        val withNullCategory = sampleSubscription(
            id = "sub-null-cat",
            categoryId = null,
        )
        val withMissingCategory = sampleSubscription(
            id = "sub-missing-cat",
            categoryId = "cat-unknown",
        )
        val withPresentCategory = sampleSubscription(
            id = "sub-present-cat",
            categoryId = "cat-1",
        )

        val mappedNull = SubscriptionDisplayModelMapper.mapItem(withNullCategory, null, today)
        val mappedMissing = SubscriptionDisplayModelMapper.mapItem(withMissingCategory, null, today)
        val mappedPresent = SubscriptionDisplayModelMapper.mapItem(withPresentCategory, category, today)

        // Null category case
        assertEquals("Kategorisiz", mappedNull.categoryName)
        assertTrue(mappedNull.isCategoryUnassigned)
        assertFalse(mappedNull.isCategoryMissing)
        assertNull(mappedNull.categoryColorHex)
        assertNull(mappedNull.categoryIconKey)

        // Missing category case
        assertEquals("Bilinmeyen Kategori", mappedMissing.categoryName)
        assertFalse(mappedMissing.isCategoryUnassigned)
        assertTrue(mappedMissing.isCategoryMissing)
        assertNull(mappedMissing.categoryColorHex)
        assertNull(mappedMissing.categoryIconKey)

        // Present category case
        assertEquals("Müzik", mappedPresent.categoryName)
        assertFalse(mappedPresent.isCategoryUnassigned)
        assertFalse(mappedPresent.isCategoryMissing)
        assertEquals("#10B981", mappedPresent.categoryColorHex)
        assertEquals("briefcase", mappedPresent.categoryIconKey)
    }

    @Test
    fun map_handlesEndDatePresentAndAbsent() {
        val category = sampleCategory("cat-1", "Yazılım")

        val withEndDate = sampleSubscription(
            id = "sub-end",
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31),
            nextRenewalDate = LocalDate(2026, 9, 1),
        )
        val withoutEndDate = sampleSubscription(
            id = "sub-no-end",
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
            nextRenewalDate = LocalDate(2026, 9, 1),
        )

        val mappedWithEnd = SubscriptionDisplayModelMapper.mapItem(withEndDate, category, today)
        val mappedWithoutEnd = SubscriptionDisplayModelMapper.mapItem(withoutEndDate, category, today)

        assertEquals(LocalDate(2026, 1, 1), mappedWithEnd.startDate)
        assertEquals("1 Ocak 2026", mappedWithEnd.formattedStartDate)
        assertEquals(LocalDate(2026, 12, 31), mappedWithEnd.endDate)
        assertEquals("31 Aralık 2026", mappedWithEnd.formattedEndDate)
        assertEquals("1 Eylül 2026", mappedWithEnd.formattedNextRenewalDate)

        assertEquals(LocalDate(2026, 1, 1), mappedWithoutEnd.startDate)
        assertEquals("1 Ocak 2026", mappedWithoutEnd.formattedStartDate)
        assertNull(mappedWithoutEnd.endDate)
        assertNull(mappedWithoutEnd.formattedEndDate)
    }

    @Test
    fun map_formatsNonTryCurrenciesCorrectly() {
        val category = sampleCategory("cat-1", "Bulut")

        val trySub = sampleSubscription(
            id = "sub-try",
            amountMinor = 5999L,
            currency = Currency.TRY,
        )
        val usdSub = sampleSubscription(
            id = "sub-usd",
            amountMinor = 1500L,
            currency = Currency.USD,
        )
        val eurSub = sampleSubscription(
            id = "sub-eur",
            amountMinor = 2550L,
            currency = Currency.EUR,
        )

        val mappedTry = SubscriptionDisplayModelMapper.mapItem(trySub, category, today)
        val mappedUsd = SubscriptionDisplayModelMapper.mapItem(usdSub, category, today)
        val mappedEur = SubscriptionDisplayModelMapper.mapItem(eurSub, category, today)

        assertEquals(Currency.TRY, mappedTry.currency)
        assertEquals("59,99 ₺", mappedTry.formattedAmount)

        assertEquals(Currency.USD, mappedUsd.currency)
        assertEquals(1500L, mappedUsd.amount.amountMinor)
        assertEquals("15,00 $", mappedUsd.formattedAmount)

        assertEquals(Currency.EUR, mappedEur.currency)
        assertEquals(2550L, mappedEur.amount.amountMinor)
        assertEquals("25,50 €", mappedEur.formattedAmount)
    }

    @Test
    fun map_invalidUpcomingWindowDays_rethrowsExceptionAndDoesNotMaskAsNull() {
        val category = sampleCategory("cat-1", "Abonelik")
        val sub = sampleSubscription(id = "sub-1")

        assertFailsWith<IllegalArgumentException> {
            SubscriptionDisplayModelMapper.mapItem(
                item = sub,
                category = category,
                today = today,
                upcomingWindowDays = -1,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SubscriptionDisplayModelMapper.map(
                subscriptions = listOf(sub),
                categories = listOf(category),
                today = today,
                upcomingWindowDays = -5,
            )
        }
    }

    private fun sampleCategory(id: String, name: String) = Category(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("briefcase"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun sampleSubscription(
        id: String,
        name: String = "Spotify",
        categoryId: String? = "cat-1",
        amountMinor: Long = 5999L,
        currency: Currency = Currency.TRY,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate(2026, 8, 1),
        endDate: LocalDate? = null,
        nextRenewalDate: LocalDate = LocalDate(2026, 9, 1),
        isActive: Boolean = true,
    ): Subscription = Subscription(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        amount = Money(amountMinor, currency),
        categoryId = categoryId?.let { EntityId(it) },
        renewalRule = RecurrenceRule(
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = endDate,
        ),
        nextRenewalDate = nextRenewalDate,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
