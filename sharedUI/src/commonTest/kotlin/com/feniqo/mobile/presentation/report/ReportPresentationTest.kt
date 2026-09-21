package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.presentation.util.MoneyFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportPresentationTest {

    @Test
    fun formatFilterSummary_withDefaultFilter_producesExpectedSummary() {
        val filter = ReportFilterUiState.Default
        val range = ReportDateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val summary = ReportSummaryFormatter.formatFilterSummary(filter, range)
        assertEquals("Bu ay • Tümü • TRY • Tüm kategoriler", summary)
    }

    @Test
    fun formatFilterSummary_withCustomFilters_producesCorrectText() {
        val filter = ReportFilterUiState(
            periodPreset = ReportPeriodPreset.CUSTOM,
            customStartDate = LocalDate(2026, 7, 1),
            customEndDate = LocalDate(2026, 9, 30),
            typeFilter = ReportTypeFilter.EXPENSE,
            currency = Currency.USD,
            selectedCategoryId = EntityId("cat-market"),
            selectedCategoryName = "Market",
        )
        val range = ReportDateRange(LocalDate(2026, 7, 1), LocalDate(2026, 9, 30))
        val summary = ReportSummaryFormatter.formatFilterSummary(filter, range)
        assertEquals("1 Tem – 30 Eyl 2026 • Gider • USD • Market", summary)
    }

    @Test
    fun generateActiveFilterChips_createsOnlyActiveChips() {
        val filter = ReportFilterUiState(
            periodPreset = ReportPeriodPreset.THIS_MONTH,
            typeFilter = ReportTypeFilter.EXPENSE,
            currency = Currency.TRY,
            selectedCategoryId = null,
        )
        val range = ReportDateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val chips = ReportSummaryFormatter.generateActiveFilterChips(filter, range)

        assertEquals(3, chips.size)
        assertEquals(ActiveFilterType.PERIOD, chips[0].type)
        assertEquals(ActiveFilterType.TYPE, chips[1].type)
        assertEquals("Gider", chips[1].label)
        assertEquals(ActiveFilterType.CURRENCY, chips[2].type)
        assertEquals("TRY", chips[2].label)
    }

    @Test
    fun reportFilterUiState_toDomain_mapsCorrectly() {
        val uiState = ReportFilterUiState(
            periodPreset = ReportPeriodPreset.CUSTOM,
            customStartDate = LocalDate(2026, 7, 1),
            customEndDate = LocalDate(2026, 9, 30),
            typeFilter = ReportTypeFilter.INCOME,
            currency = Currency.GBP,
            selectedCategoryId = EntityId("cat-1"),
            selectedCategoryName = "Maaş",
        )
        val domain = uiState.toDomain()
        assertEquals(ReportPeriodPreset.CUSTOM, domain.periodPreset)
        assertEquals(LocalDate(2026, 7, 1), domain.customDateRange?.startDate)
        assertEquals(LocalDate(2026, 9, 30), domain.customDateRange?.endDate)
        assertEquals(ReportTypeFilter.INCOME, domain.typeFilter)
        assertEquals(Currency.GBP, domain.currency)
        assertEquals(EntityId("cat-1"), domain.categoryId)
    }

    @Test
    fun multiCurrency_amountMasking_neverRevealsRawAmountWhenMasked() {
        val money = Money(4250000L, Currency.TRY)
        val masked = MoneyFormatter.formatMasked(money, mask = true)
        assertEquals(MoneyFormatter.MASKED_TEXT, masked)

        val accessible = MoneyFormatter.getAccessibleDescription("Gelir: 42.500 ₺", isMasked = true)
        assertEquals(MoneyFormatter.MASKED_ACCESSIBLE_DESCRIPTION, accessible)
    }
}
