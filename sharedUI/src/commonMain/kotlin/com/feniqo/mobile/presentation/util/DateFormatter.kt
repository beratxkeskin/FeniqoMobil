package com.feniqo.mobile.presentation.util

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Month
import kotlinx.datetime.minus

/**
 * Saf presentation katmanı tarih biçimleyicisidir.
 * Platform API'si veya Clock bağımlılığı içermez; açık `today` parametresi ile deterministik çalışır.
 */
object DateFormatter {

    fun formatTransactionGroupDate(
        date: LocalDate,
        today: LocalDate,
    ): String {
        if (date == today) return "Bugün"
        if (date == today.minus(1, DateTimeUnit.DAY)) return "Dün"

        val monthName = getTurkishMonthName(date.month)
        return "${date.day} $monthName ${date.year}"
    }

    fun formatReadableDate(date: LocalDate): String {
        val monthName = getTurkishMonthName(date.month)
        return "${date.day} $monthName ${date.year}"
    }

    fun formatYearMonth(yearMonth: YearMonth): String {
        val parts = yearMonth.value.split("-")
        val year = parts.getOrNull(0) ?: return yearMonth.value
        val monthNum = parts.getOrNull(1)?.toIntOrNull() ?: return yearMonth.value
        val month = Month.entries.getOrNull(monthNum - 1) ?: return yearMonth.value
        return "${getTurkishMonthName(month)} $year"
    }

    fun formatShortMonth(month: Month): String = when (month) {
        Month.JANUARY -> "Oca"
        Month.FEBRUARY -> "Şub"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Nis"
        Month.MAY -> "May"
        Month.JUNE -> "Haz"
        Month.JULY -> "Tem"
        Month.AUGUST -> "Ağu"
        Month.SEPTEMBER -> "Eyl"
        Month.OCTOBER -> "Eki"
        Month.NOVEMBER -> "Kas"
        Month.DECEMBER -> "Ara"
    }

    fun formatDateRange(startDate: LocalDate, endDate: LocalDate): String {
        val startM = formatShortMonth(startDate.month)
        val endM = formatShortMonth(endDate.month)
        return if (startDate.month == endDate.month) {
            "${startDate.day} – ${endDate.day} $startM"
        } else {
            "${startDate.day} $startM – ${endDate.day} $endM"
        }
    }

    private fun getTurkishMonthName(month: Month): String = when (month) {
        Month.JANUARY -> "Ocak"
        Month.FEBRUARY -> "Şubat"
        Month.MARCH -> "Mart"
        Month.APRIL -> "Nisan"
        Month.MAY -> "Mayıs"
        Month.JUNE -> "Haziran"
        Month.JULY -> "Temmuz"
        Month.AUGUST -> "Ağustos"
        Month.SEPTEMBER -> "Eylül"
        Month.OCTOBER -> "Ekim"
        Month.NOVEMBER -> "Kasım"
        Month.DECEMBER -> "Aralık"
    }
}
