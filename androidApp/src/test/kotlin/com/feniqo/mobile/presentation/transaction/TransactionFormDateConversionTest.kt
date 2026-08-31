package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFormDateConversionTest {

    @Test
    fun dateConversion_normalDate_roundTripsCorrectly() {
        val date = LocalDate(2026, 8, 24)
        val millis = date.toUtcEpochMillis()
        val converted = utcEpochMillisToLocalDate(millis)
        assertEquals(date, converted)
    }

    @Test
    fun dateConversion_epochStart_roundTripsCorrectly() {
        val date = LocalDate(1970, 1, 1)
        val millis = date.toUtcEpochMillis()
        assertEquals(0L, millis)
        val converted = utcEpochMillisToLocalDate(millis)
        assertEquals(date, converted)
    }

    @Test
    fun dateConversion_endOfMonths_roundTripsCorrectly() {
        val endOfJan = LocalDate(2026, 1, 31)
        assertEquals(endOfJan, utcEpochMillisToLocalDate(endOfJan.toUtcEpochMillis()))

        val endOfApr = LocalDate(2026, 4, 30)
        assertEquals(endOfApr, utcEpochMillisToLocalDate(endOfApr.toUtcEpochMillis()))

        val endOfDec = LocalDate(2026, 12, 31)
        assertEquals(endOfDec, utcEpochMillisToLocalDate(endOfDec.toUtcEpochMillis()))
    }

    @Test
    fun dateConversion_leapYearFeb29_roundTripsCorrectly() {
        val leap2024 = LocalDate(2024, 2, 29)
        assertEquals(leap2024, utcEpochMillisToLocalDate(leap2024.toUtcEpochMillis()))

        val leap2028 = LocalDate(2028, 2, 29)
        assertEquals(leap2028, utcEpochMillisToLocalDate(leap2028.toUtcEpochMillis()))
    }

    @Test
    fun initialDatePickerSelection_nullDate_returnsNull() {
        val selection = initialDatePickerSelection(null)
        assertEquals(null, selection)
    }

    @Test
    fun initialDatePickerSelection_validDate_returnsExpectedUtcMillis() {
        val date = LocalDate(2026, 8, 24)
        val expectedMillis = date.toUtcEpochMillis()
        val selection = initialDatePickerSelection(date)
        assertEquals(expectedMillis, selection)
    }
}
