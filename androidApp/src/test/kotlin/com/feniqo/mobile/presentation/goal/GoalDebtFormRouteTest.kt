package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.goal.GoalDebtFormRouteHelper.toUtcEpochMillis
import com.feniqo.mobile.presentation.goal.GoalDebtFormRouteHelper.utcEpochMillisToLocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalDebtFormRouteTest {

    @Test
    fun computeInitialDatePickerSelection_usesTargetDateWhenPresent() {
        val target = LocalDate(2027, 8, 15)
        val fallbackDate = LocalDate(2026, 1, 1)

        val selection = GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = target,
            currentDateProvider = { fallbackDate },
        )

        assertEquals(target.toUtcEpochMillis(), selection)
    }

    @Test
    fun computeInitialDatePickerSelection_usesCurrentDateProviderWhenTargetNull() {
        val fallbackDate = LocalDate(2026, 9, 3)

        val selection = GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = null,
            currentDateProvider = { fallbackDate },
        )

        assertEquals(fallbackDate.toUtcEpochMillis(), selection)
    }

    @Test
    fun resolveCurrentLocalDate_correctlyUsesSystemLocalTimeZoneAcrossMidnightBoundary() {
        // 2026-09-03 23:30:00 UTC -> UTC+3'te (İstanbul) 2026-09-04 02:30:00'dur
        val boundaryInstant = Instant.parse("2026-09-03T23:30:00Z")
        val utcTimeZone = TimeZone.UTC
        val istanbulTimeZone = TimeZone.of("Europe/Istanbul")

        val utcDate = GoalDebtFormRouteHelper.resolveCurrentLocalDate(boundaryInstant, utcTimeZone)
        val istanbulDate = GoalDebtFormRouteHelper.resolveCurrentLocalDate(boundaryInstant, istanbulTimeZone)

        assertEquals(LocalDate(2026, 9, 3), utcDate)
        assertEquals(LocalDate(2026, 9, 4), istanbulDate)

        // DatePicker seçimi null hedefte yerel günün (04 Eylül) UTC-midnight epoch değerini üretmeli
        val selectionIstanbul = GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = null,
            currentDateProvider = { istanbulDate },
        )

        assertEquals(LocalDate(2026, 9, 4).toUtcEpochMillis(), selectionIstanbul)
    }

    @Test
    fun utcEpochMillis_roundTripConversion_isExact() {
        val dates = listOf(
            LocalDate(2024, 2, 29), // Artık yıl
            LocalDate(2026, 1, 1),
            LocalDate(2026, 12, 31),
            LocalDate(2030, 6, 15),
        )

        dates.forEach { date ->
            val millis = date.toUtcEpochMillis()
            val converted = utcEpochMillisToLocalDate(millis)
            assertEquals(date, converted)
        }
    }
}
