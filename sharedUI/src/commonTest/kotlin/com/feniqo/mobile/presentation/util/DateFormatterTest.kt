package com.feniqo.mobile.presentation.util

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatterTest {

    @Test
    fun formatReadableDate_formatsTurkishDateCorrectly() {
        val date1 = LocalDate(2026, 8, 1)
        val date2 = LocalDate(2026, 12, 31)
        val date3 = LocalDate(2026, 1, 15)

        assertEquals("1 Ağustos 2026", DateFormatter.formatReadableDate(date1))
        assertEquals("31 Aralık 2026", DateFormatter.formatReadableDate(date2))
        assertEquals("15 Ocak 2026", DateFormatter.formatReadableDate(date3))
    }

    @Test
    fun formatTransactionGroupDate_handlesTodayYesterdayAndStandardDates() {
        val today = LocalDate(2026, 8, 15)
        val yesterday = LocalDate(2026, 8, 14)
        val earlier = LocalDate(2026, 8, 1)

        assertEquals("Bugün", DateFormatter.formatTransactionGroupDate(today, today))
        assertEquals("Dün", DateFormatter.formatTransactionGroupDate(yesterday, today))
        assertEquals("1 Ağustos 2026", DateFormatter.formatTransactionGroupDate(earlier, today))
    }

    @Test
    fun formatYearMonth_formatsTurkishYearMonthCorrectly() {
        assertEquals("Ağustos 2026", DateFormatter.formatYearMonth(YearMonth("2026-08")))
        assertEquals("Ocak 2026", DateFormatter.formatYearMonth(YearMonth("2026-01")))
    }
}
