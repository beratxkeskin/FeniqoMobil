package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter

object ReportSummaryFormatter {

    fun formatPeriodPreset(preset: ReportPeriodPreset, isEnglish: Boolean = false): String = when (preset) {
        ReportPeriodPreset.THIS_MONTH -> if (isEnglish) "This Month" else "Bu Ay"
        ReportPeriodPreset.LAST_MONTH -> if (isEnglish) "Last Month" else "Geçen Ay"
        ReportPeriodPreset.LAST_3_MONTHS -> if (isEnglish) "Last 3 Months" else "Son 3 Ay"
        ReportPeriodPreset.CUSTOM -> if (isEnglish) "Custom" else "Özel"
    }

    fun formatFilterSummary(
        filter: ReportFilterUiState,
        dateRange: ReportDateRange,
        isEnglish: Boolean = false,
    ): String {
        val periodText = when (filter.periodPreset) {
            ReportPeriodPreset.THIS_MONTH -> if (isEnglish) "This month" else "Bu ay"
            ReportPeriodPreset.LAST_MONTH -> if (isEnglish) "Last month" else "Geçen ay"
            ReportPeriodPreset.LAST_3_MONTHS -> if (isEnglish) "Last 3 months" else "Son 3 ay"
            ReportPeriodPreset.CUSTOM -> formatDateRangeShort(dateRange.startDate, dateRange.endDate, isEnglish = isEnglish)
        }

        val typeText = when (filter.typeFilter) {
            ReportTypeFilter.ALL -> if (isEnglish) "All" else "Tümü"
            ReportTypeFilter.INCOME -> if (isEnglish) "Income" else "Gelir"
            ReportTypeFilter.EXPENSE -> if (isEnglish) "Expense" else "Gider"
        }

        val currencyText = filter.currency.code
        val categoryText = filter.selectedCategoryName ?: if (isEnglish) "All categories" else "Tüm kategoriler"

        return "$periodText • $typeText • $currencyText • $categoryText"
    }

    fun formatDateRangeShort(
        startDate: LocalDate,
        endDate: LocalDate,
        isEnglish: Boolean = false,
    ): String {
        val startMonthName = monthAbbreviation(startDate.monthNumber, isEnglish = isEnglish)
        val endMonthName = monthAbbreviation(endDate.monthNumber, isEnglish = isEnglish)
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

    fun monthName(monthNumber: Int, isEnglish: Boolean = false): String = if (isEnglish) {
        when (monthNumber) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> ""
        }
    } else {
        when (monthNumber) {
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
    }

    fun monthAbbreviation(monthNumber: Int, isEnglish: Boolean = false): String = if (isEnglish) {
        when (monthNumber) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> ""
        }
    } else {
        when (monthNumber) {
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
    }

    fun getCurrencyDisplayName(currency: Currency, isEnglish: Boolean = false): String = if (isEnglish) {
        when (currency) {
            Currency.TRY -> "Turkish Lira (TRY)"
            Currency.USD -> "US Dollar (USD)"
            Currency.EUR -> "Euro (EUR)"
            Currency.GBP -> "British Pound (GBP)"
        }
    } else {
        when (currency) {
            Currency.TRY -> "Türk Lirası (TRY)"
            Currency.USD -> "Amerikan Doları (USD)"
            Currency.EUR -> "Euro (EUR)"
            Currency.GBP -> "İngiliz Sterlini (GBP)"
        }
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
        chips.add(ActiveFilterChipUiModel.Period(filter.periodPreset, dateRange))
        when (filter.typeFilter) {
            ReportTypeFilter.INCOME -> chips.add(ActiveFilterChipUiModel.Type(ReportTypeFilter.INCOME))
            ReportTypeFilter.EXPENSE -> chips.add(ActiveFilterChipUiModel.Type(ReportTypeFilter.EXPENSE))
            ReportTypeFilter.ALL -> Unit
        }
        chips.add(ActiveFilterChipUiModel.CurrencyChip(filter.currency))
        if (filter.selectedCategoryId != null && filter.selectedCategoryName != null) {
            chips.add(ActiveFilterChipUiModel.CategoryChip(filter.selectedCategoryId, filter.selectedCategoryName))
        }
        return chips
    }
}
