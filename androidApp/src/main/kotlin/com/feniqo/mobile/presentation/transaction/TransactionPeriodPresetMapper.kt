package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportPeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * UI filtre ön ayarlarını domain ReportPeriod nesnesine dönüştüren saf mapper.
 * Platform bağımlılığı veya Clock içermez; deterministik çalışması için açık `today` alır.
 */
object TransactionPeriodPresetMapper {

    fun toReportPeriod(
        preset: TransactionPeriodPreset?,
        today: LocalDate,
    ): ReportPeriod? {
        if (preset == null) return null

        return when (preset) {
            TransactionPeriodPreset.TODAY -> {
                ReportPeriod(
                    startDate = today,
                    endDate = today,
                )
            }
            TransactionPeriodPreset.THIS_WEEK -> {
                val daysFromMonday = today.dayOfWeek.ordinal
                val startOfWeek = today.minus(daysFromMonday, DateTimeUnit.DAY)
                val daysToSunday = 6 - daysFromMonday
                val endOfWeek = today.plus(daysToSunday, DateTimeUnit.DAY)
                ReportPeriod(
                    startDate = startOfWeek,
                    endDate = endOfWeek,
                )
            }
            TransactionPeriodPreset.THIS_MONTH -> {
                val startOfMonth = LocalDate(today.year, today.month, 1)
                val startOfNextMonth = startOfMonth.plus(1, DateTimeUnit.MONTH)
                val endOfMonth = startOfNextMonth.minus(1, DateTimeUnit.DAY)
                ReportPeriod(
                    startDate = startOfMonth,
                    endDate = endOfMonth,
                )
            }
            TransactionPeriodPreset.LAST_30_DAYS -> {
                val startDate = today.minus(29, DateTimeUnit.DAY)
                ReportPeriod(
                    startDate = startDate,
                    endDate = today,
                )
            }
            TransactionPeriodPreset.THIS_YEAR -> {
                val startOfYear = LocalDate(today.year, 1, 1)
                val endOfYear = LocalDate(today.year, 12, 31)
                ReportPeriod(
                    startDate = startOfYear,
                    endDate = endOfYear,
                )
            }
        }
    }
}
