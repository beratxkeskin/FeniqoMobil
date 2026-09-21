package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter

object ReportSummaryFormatter {

    fun formatPeriodPreset(preset: ReportPeriodPreset): String = when (preset) {
        ReportPeriodPreset.THIS_MONTH -> "Bu Ay"
        ReportPeriodPreset.LAST_MONTH -> "Geçen Ay"
        ReportPeriodPreset.LAST_3_MONTHS -> "Son 3 Ay"
        ReportPeriodPreset.CUSTOM -> "Özel"
    }

    fun formatFilterSummary(
        filter: ReportFilterUiState,
        dateRange: ReportDateRange,
    ): String {
        val periodText = when (filter.periodPreset) {
            ReportPeriodPreset.THIS_MONTH -> "Bu ay"
            ReportPeriodPreset.LAST_MONTH -> "Geçen ay"
            ReportPeriodPreset.LAST_3_MONTHS -> "Son 3 ay"
            ReportPeriodPreset.CUSTOM -> formatDateRangeShort(dateRange.startDate, dateRange.endDate)
        }

        val typeText = when (filter.typeFilter) {
            ReportTypeFilter.ALL -> "Tümü"
            ReportTypeFilter.INCOME -> "Gelir"
            ReportTypeFilter.EXPENSE -> "Gider"
        }

        val currencyText = filter.currency.code
        val categoryText = filter.selectedCategoryName ?: "Tüm kategoriler"

        return "$periodText • $typeText • $currencyText • $categoryText"
    }

    fun formatDateRangeShort(startDate: LocalDate, endDate: LocalDate): String {
        val startMonthName = monthAbbreviation(startDate.monthNumber)
        val endMonthName = monthAbbreviation(endDate.monthNumber)
        return if (startDate.year == endDate.year) {
            if (startDate.monthNumber == endDate.monthNumber) {
                "${startDate.dayOfMonth} – ${endDate.dayOfMonth} $endMonthName ${endDate.year}"
            } else {
                "${startDate.dayOfMonth} $startMonthName – ${endDate.dayOfMonth} $endMonthName ${endDate.year}"
            }
        } else {
            "${startDate.dayOfMonth} $startMonthName ${startDate.year} – ${endDate.dayOfMonth} $endMonthName ${endDate.year}"
        }
    }

    fun formatDateDisplay(date: LocalDate): String {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        return "$day.$month.${date.year}"
    }

    fun monthName(monthNumber: Int): String = when (monthNumber) {
        1 -> "Ocak"
        2 -> "Şubat"
        3 -> "Mart"
        4 -> "Nisan"
        5 -> "Mayıs"
        6 -> "Haziran"
        7 -> "Temmuz"
        8 -> "Ağustos"
        9 -> "Eylül"
        10 -> "Ekim"
        11 -> "Kasım"
        12 -> "Aralık"
        else -> ""
    }

    fun monthAbbreviation(monthNumber: Int): String = when (monthNumber) {
        1 -> "Oca"
        2 -> "Şub"
        3 -> "Mar"
        4 -> "Nis"
        5 -> "May"
        6 -> "Haz"
        7 -> "Tem"
        8 -> "Ağu"
        9 -> "Eyl"
        10 -> "Eki"
        11 -> "Kas"
        12 -> "Ara"
        else -> ""
    }

    fun getCurrencyDisplayName(currency: Currency): String = when (currency) {
        Currency.TRY -> "Türk Lirası (TRY)"
        Currency.USD -> "Amerikan Doları (USD)"
        Currency.EUR -> "Euro (EUR)"
        Currency.GBP -> "İngiliz Sterlini (GBP)"
    }

    fun getCurrencySymbol(currency: Currency): String = when (currency) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
        Currency.GBP -> "£"
    }

    fun generateActiveFilterChips(
        filter: ReportFilterUiState,
        dateRange: ReportDateRange,
    ): List<ActiveFilterChipUiModel> {
        val chips = mutableListOf<ActiveFilterChipUiModel>()

        // 1. Dönem çipi
        val periodLabel = when (filter.periodPreset) {
            ReportPeriodPreset.THIS_MONTH -> "Bu ay (${monthName(dateRange.startDate.monthNumber)} ${dateRange.startDate.year})"
            ReportPeriodPreset.LAST_MONTH -> "Geçen ay (${monthName(dateRange.startDate.monthNumber)} ${dateRange.startDate.year})"
            ReportPeriodPreset.LAST_3_MONTHS -> "Son 3 ay"
            ReportPeriodPreset.CUSTOM -> formatDateRangeShort(dateRange.startDate, dateRange.endDate)
        }
        chips.add(ActiveFilterChipUiModel("period", ActiveFilterType.PERIOD, periodLabel))

        // 2. Tür çipi (eğer Tümü değilse)
        when (filter.typeFilter) {
            ReportTypeFilter.INCOME -> chips.add(ActiveFilterChipUiModel("type", ActiveFilterType.TYPE, "Gelir"))
            ReportTypeFilter.EXPENSE -> chips.add(ActiveFilterChipUiModel("type", ActiveFilterType.TYPE, "Gider"))
            ReportTypeFilter.ALL -> Unit
        }

        // 3. Para birimi çipi
        chips.add(ActiveFilterChipUiModel("currency", ActiveFilterType.CURRENCY, filter.currency.code))

        // 4. Kategori çipi (seçiliyse)
        if (filter.selectedCategoryId != null && filter.selectedCategoryName != null) {
            chips.add(ActiveFilterChipUiModel("category", ActiveFilterType.CATEGORY, filter.selectedCategoryName))
        }

        return chips
    }
}
