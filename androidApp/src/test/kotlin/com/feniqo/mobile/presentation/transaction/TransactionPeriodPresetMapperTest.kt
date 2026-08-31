package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportPeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionPeriodPresetMapperTest {

    @Test
    fun toReportPeriod_nullPreset_returnsNull() {
        val today = LocalDate(2026, 8, 21)
        val result = TransactionPeriodPresetMapper.toReportPeriod(null, today)
        assertNull(result)
    }

    @Test
    fun toReportPeriod_todayPreset_returnsSameStartAndEndDate() {
        val today = LocalDate(2026, 8, 21)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.TODAY, today)
        assertEquals(ReportPeriod(startDate = today, endDate = today), result)
    }

    @Test
    fun toReportPeriod_thisWeek_midWeek_mapsToMondayAndSunday() {
        // 2026-08-19 is Wednesday
        val wednesday = LocalDate(2026, 8, 19)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_WEEK, wednesday)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 8, 17), // Monday
            endDate = LocalDate(2026, 8, 23),   // Sunday
        )
        assertEquals(expected, result)
    }

    @Test
    fun toReportPeriod_thisWeek_monday_mapsToSameMondayAndSunday() {
        // 2026-08-17 is Monday
        val monday = LocalDate(2026, 8, 17)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_WEEK, monday)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 8, 17), // Monday
            endDate = LocalDate(2026, 8, 23),   // Sunday
        )
        assertEquals(expected, result)
    }

    @Test
    fun toReportPeriod_thisWeek_sunday_mapsToPrecedingMondayAndSameSunday() {
        // 2026-08-23 is Sunday
        val sunday = LocalDate(2026, 8, 23)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_WEEK, sunday)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 8, 17), // Monday
            endDate = LocalDate(2026, 8, 23),   // Sunday
        )
        assertEquals(expected, result)
    }

    @Test
    fun toReportPeriod_thisMonth_normalMonthEnd_mapsToFirstAndLastDay() {
        val midAugust = LocalDate(2026, 8, 21)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_MONTH, midAugust)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 31),
        )
        assertEquals(expected, result)

        val midApril = LocalDate(2026, 4, 15)
        val aprilResult = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_MONTH, midApril)
        val aprilExpected = ReportPeriod(
            startDate = LocalDate(2026, 4, 1),
            endDate = LocalDate(2026, 4, 30),
        )
        assertEquals(aprilExpected, aprilResult)
    }

    @Test
    fun toReportPeriod_thisMonth_februaryLeapYear_mapsTo29th() {
        // 2024 is a leap year
        val febLeap = LocalDate(2024, 2, 10)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_MONTH, febLeap)
        val expected = ReportPeriod(
            startDate = LocalDate(2024, 2, 1),
            endDate = LocalDate(2024, 2, 29),
        )
        assertEquals(expected, result)
    }

    @Test
    fun toReportPeriod_thisMonth_februaryNonLeapYear_mapsTo28th() {
        // 2025 is not a leap year
        val febNonLeap = LocalDate(2025, 2, 10)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_MONTH, febNonLeap)
        val expected = ReportPeriod(
            startDate = LocalDate(2025, 2, 1),
            endDate = LocalDate(2025, 2, 28),
        )
        assertEquals(expected, result)
    }

    @Test
    fun toReportPeriod_last30Days_spansExactly30DaysInclusive() {
        val today = LocalDate(2026, 8, 30)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.LAST_30_DAYS, today)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 30),
        )
        assertEquals(expected, result)

        // Across month boundary with February in non-leap year (2025)
        val marchFirst = LocalDate(2025, 3, 1)
        val marchResult = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.LAST_30_DAYS, marchFirst)
        // 2025-03-01 minus 29 days is 2025-01-31 (1 in Jan, 28 in Feb, 1 in Mar = 30 days)
        val marchExpected = ReportPeriod(
            startDate = LocalDate(2025, 1, 31),
            endDate = LocalDate(2025, 3, 1),
        )
        assertEquals(marchExpected, marchResult)

        // Verify difference between start and end is exactly 29 days (making total days 30 inclusive)
        assertEquals(today.minus(29, DateTimeUnit.DAY), result!!.startDate)
        assertEquals(today, result.endDate)
    }

    @Test
    fun toReportPeriod_thisYear_mapsToStartAndEndOfYear() {
        val today = LocalDate(2026, 8, 21)
        val result = TransactionPeriodPresetMapper.toReportPeriod(TransactionPeriodPreset.THIS_YEAR, today)
        val expected = ReportPeriod(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31),
        )
        assertEquals(expected, result)
    }
}
