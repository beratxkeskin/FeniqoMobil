package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Goal ve Debt form route'ları için saf tarih ve DatePicker seçim yardımcıları.
 */
object GoalDebtFormRouteHelper {

    fun resolveCurrentLocalDate(
        instant: Instant,
        timeZone: TimeZone,
    ): LocalDate {
        return instant.toLocalDateTime(timeZone).date
    }

    fun defaultCurrentDateProvider(): LocalDate {
        return resolveCurrentLocalDate(
            instant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    fun computeInitialDatePickerSelection(
        targetDate: LocalDate?,
        currentDateProvider: () -> LocalDate = ::defaultCurrentDateProvider,
    ): Long {
        val resolvedDate = targetDate ?: currentDateProvider()
        return resolvedDate.toUtcEpochMillis()
    }

    fun LocalDate.toUtcEpochMillis(): Long {
        return toEpochDays() * 86_400_000L
    }

    fun utcEpochMillisToLocalDate(utcEpochMillis: Long): LocalDate {
        val epochDays = (utcEpochMillis / 86_400_000L).toInt()
        return LocalDate.fromEpochDays(epochDays)
    }
}
