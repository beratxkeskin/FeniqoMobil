package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceScheduleCalculatorTest {

    @Test
    fun nextOccurrenceAfter_nullLastGenerated_returnsStartDate() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val next = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, lastGeneratedDate = null)
        assertEquals(LocalDate(2026, 8, 1), next)
    }

    @Test
    fun nextOccurrenceAfter_nullLastGenerated_pastEndDate_returnsNull() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 1),
        )

        // startDate <= endDate is valid. Next after startDate is 2026-08-02 which is > endDate.
        val nextAfterStart = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 8, 1))
        assertNull(nextAfterStart)
    }

    @Test
    fun nextOccurrenceAfter_dailyWithInterval() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 3,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val next1 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 8, 1))
        assertEquals(LocalDate(2026, 8, 4), next1)

        val next2 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 8, 4))
        assertEquals(LocalDate(2026, 8, 7), next2)
    }

    @Test
    fun nextOccurrenceAfter_weeklyWithInterval() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val next1 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 8, 1))
        assertEquals(LocalDate(2026, 8, 15), next1)

        val next2 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 8, 15))
        assertEquals(LocalDate(2026, 8, 29), next2)
    }

    @Test
    fun nextOccurrenceAfter_monthly_anchor31stClampingAndRestoration() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 31),
            endDate = null,
        )

        // Jan 31 -> Feb 28 (2026 is non-leap)
        val feb = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 2, 28), feb)

        // Feb 28 -> Mar 31 (restored to anchor 31st)
        val mar = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 2, 28))
        assertEquals(LocalDate(2026, 3, 31), mar)

        // Mar 31 -> Apr 30 (30 days in April)
        val apr = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 3, 31))
        assertEquals(LocalDate(2026, 4, 30), apr)

        // Apr 30 -> May 31 (restored to anchor 31st)
        val may = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 4, 30))
        assertEquals(LocalDate(2026, 5, 31), may)
    }

    @Test
    fun nextOccurrenceAfter_monthly_leapYearFebruaryClamping() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2024, 1, 31),
            endDate = null,
        )

        // 2024 is a leap year -> Feb 29
        val feb = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2024, 1, 31))
        assertEquals(LocalDate(2024, 2, 29), feb)

        val mar = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2024, 2, 29))
        assertEquals(LocalDate(2024, 3, 31), mar)
    }

    @Test
    fun nextOccurrenceAfter_yearly_leapYearFeb29Anchor() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
            startDate = LocalDate(2024, 2, 29),
            endDate = null,
        )

        // 2024 (leap) -> 2025 (non-leap: Feb 28)
        val y2025 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2024, 2, 29))
        assertEquals(LocalDate(2025, 2, 28), y2025)

        // 2025 -> 2026 (non-leap: Feb 28)
        val y2026 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2025, 2, 28))
        assertEquals(LocalDate(2026, 2, 28), y2026)

        // 2026 -> 2027 (non-leap: Feb 28)
        val y2027 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 2, 28))
        assertEquals(LocalDate(2027, 2, 28), y2027)

        // 2027 -> 2028 (leap: restored to Feb 29)
        val y2028 = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2027, 2, 28))
        assertEquals(LocalDate(2028, 2, 29), y2028)
    }

    @Test
    fun nextOccurrenceAfter_endDateInclusiveBoundary() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 15),
            endDate = LocalDate(2026, 3, 15),
        )

        val feb = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 1, 15))
        assertEquals(LocalDate(2026, 2, 15), feb)

        val mar = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 2, 15))
        assertEquals(LocalDate(2026, 3, 15), mar)

        // Next after March 15 would be April 15, which exceeds endDate -> null
        val apr = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 3, 15))
        assertNull(apr)
    }

    @Test
    fun nextOccurrenceAfter_invalidLastGeneratedDate_throwsIllegalArgumentException() {
        val dailyRule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 3,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        // Before startDate
        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.nextOccurrenceAfter(dailyRule, LocalDate(2026, 7, 31))
        }

        // Off interval (1 -> 4 -> 7, so 2 is invalid)
        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.nextOccurrenceAfter(dailyRule, LocalDate(2026, 8, 2))
        }

        val monthlyRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 31),
            endDate = null,
        )

        // Off anchor day (expected Feb 28, passed Feb 15)
        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.nextOccurrenceAfter(monthlyRule, LocalDate(2026, 2, 15))
        }

        // Mathematically on schedule, but strictly after endDate (fail-closed)
        val dailyRuleWithEnd = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 2,
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 5),
        )
        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.nextOccurrenceAfter(dailyRuleWithEnd, LocalDate(2026, 8, 7))
        }

        val monthlyRuleWithEnd = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 31),
            endDate = LocalDate(2026, 3, 31),
        )
        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.nextOccurrenceAfter(monthlyRuleWithEnd, LocalDate(2026, 4, 30))
        }
    }

    @Test
    fun nextOccurrenceAfter_weeklyLargeInterval_preventsIntOverflow() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1000,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        val next = RecurrenceScheduleCalculator.nextOccurrenceAfter(rule, LocalDate(2026, 1, 1))
        assertEquals(LocalDate.fromEpochDays(LocalDate(2026, 1, 1).toEpochDays() + 7000L), next)
    }

    @Test
    fun occurrencesDueThrough_generatesExpectedListThroughDate() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 2,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = null,
            throughDate = LocalDate(2026, 8, 7),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 8, 1),
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 5),
                LocalDate(2026, 8, 7),
            ),
            due,
        )
    }

    @Test
    fun occurrencesDueThrough_withLastGeneratedDate_doesNotDuplicate() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = LocalDate(2026, 8, 3),
            throughDate = LocalDate(2026, 8, 5),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 8, 4),
                LocalDate(2026, 8, 5),
            ),
            due,
        )
    }

    @Test
    fun occurrencesDueThrough_throughDateBeforeStartDate_returnsEmptyList() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = null,
            throughDate = LocalDate(2026, 7, 31),
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun occurrencesDueThrough_stopsAtEndDate() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 31),
            endDate = LocalDate(2026, 3, 31),
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = null,
            throughDate = LocalDate(2026, 12, 31),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 1, 31),
                LocalDate(2026, 2, 28),
                LocalDate(2026, 3, 31),
            ),
            due,
        )
    }

    @Test
    fun occurrencesDueThrough_respectsMaxOccurrencesLimit() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = null,
            throughDate = LocalDate(2026, 8, 31),
            maxOccurrences = 5,
        )

        assertEquals(5, due.size)
        assertEquals(LocalDate(2026, 8, 1), due.first())
        assertEquals(LocalDate(2026, 8, 5), due.last())
    }

    @Test
    fun occurrencesDueThrough_invalidMaxOccurrences_throwsIllegalArgumentException() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        )

        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.occurrencesDueThrough(
                rule = rule,
                lastGeneratedDate = null,
                throughDate = LocalDate(2026, 8, 5),
                maxOccurrences = 0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RecurrenceScheduleCalculator.occurrencesDueThrough(
                rule = rule,
                lastGeneratedDate = null,
                throughDate = LocalDate(2026, 8, 5),
                maxOccurrences = -1,
            )
        }
    }

    @Test
    fun occurrencesDueThrough_outputIsStrictlyAscendingAndUnique() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        val due = RecurrenceScheduleCalculator.occurrencesDueThrough(
            rule = rule,
            lastGeneratedDate = null,
            throughDate = LocalDate(2026, 12, 31),
            maxOccurrences = 100,
        )

        assertTrue(due.isNotEmpty())
        assertEquals(due.distinct().size, due.size, "Tüm tarihler eşsiz olmalıdır.")
        for (i in 0 until due.size - 1) {
            assertTrue(due[i] < due[i + 1], "Tarihler kesin artan sırada olmalıdır: ${due[i]} < ${due[i + 1]}")
        }
    }
}
