package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.MoneyDelta
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
        assertTrue(chips[0] is ActiveFilterChipUiModel.Period)
        assertEquals(ActiveFilterType.PERIOD, chips[0].type)
        assertEquals(ReportPeriodPreset.THIS_MONTH, (chips[0] as ActiveFilterChipUiModel.Period).preset)

        assertTrue(chips[1] is ActiveFilterChipUiModel.Type)
        assertEquals(ActiveFilterType.TYPE, chips[1].type)
        assertEquals(ReportTypeFilter.EXPENSE, (chips[1] as ActiveFilterChipUiModel.Type).typeFilter)

        assertTrue(chips[2] is ActiveFilterChipUiModel.CurrencyChip)
        assertEquals(ActiveFilterType.CURRENCY, chips[2].type)
        assertEquals(Currency.TRY, (chips[2] as ActiveFilterChipUiModel.CurrencyChip).currency)
    }

    @Test
    fun formatFilterSummary_inEnglish_producesEnglishText() {
        val filter = ReportFilterUiState.Default
        val range = ReportDateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val summary = ReportSummaryFormatter.formatFilterSummary(filter, range, isEnglish = true)
        assertEquals("This month • All • TRY • All categories", summary)

        val customFilter =
            ReportFilterUiState(
                periodPreset = ReportPeriodPreset.CUSTOM,
                customStartDate = LocalDate(2026, 7, 1),
                customEndDate = LocalDate(2026, 9, 30),
                typeFilter = ReportTypeFilter.EXPENSE,
                currency = Currency.USD,
                selectedCategoryId = EntityId("cat-market"),
                selectedCategoryName = "Groceries",
            )
        val customRange = ReportDateRange(LocalDate(2026, 7, 1), LocalDate(2026, 9, 30))
        val customSummary = ReportSummaryFormatter.formatFilterSummary(customFilter, customRange, isEnglish = true)
        assertEquals("1 Jul – 30 Sep 2026 • Expense • USD • Groceries", customSummary)
    }

    @Test
    fun reportInsightPayload_typedContract_verifiesExactCalculationsWithoutFloatDouble() {
        // ExpenseChanged decrease: 1.250,50 TRY less
        val diffDecreased = MoneyDelta(-125050L, Currency.TRY)
        val payloadDecreased = ReportInsightPayload.ExpenseChanged(
            changeBasisPoints = 1250,
            difference = diffDecreased,
        )
        assertTrue(payloadDecreased.difference.amountMinor < 0L)
        assertEquals(-125050L, payloadDecreased.difference.amountMinor)
        assertEquals(1250, payloadDecreased.changeBasisPoints)
        assertEquals(12, payloadDecreased.changeBasisPoints / 100)

        // ExpenseChanged increase: 850,25 TRY more
        val diffIncreased = MoneyDelta(85025L, Currency.TRY)
        val payloadIncreased = ReportInsightPayload.ExpenseChanged(
            changeBasisPoints = 850,
            difference = diffIncreased,
        )
        assertTrue(payloadIncreased.difference.amountMinor > 0L)
        assertEquals(85025L, payloadIncreased.difference.amountMinor)
        assertEquals(850, payloadIncreased.changeBasisPoints)
        assertEquals(8, payloadIncreased.changeBasisPoints / 100)

        // TransactionCountOnly
        val payloadCount = ReportInsightPayload.TransactionCountOnly(count = 14)
        assertEquals(14, payloadCount.count)

        // None
        assertTrue(ReportInsightPayload.None is ReportInsightPayload)
    }

    @Test
    fun categoryBreakdownUiItem_pureLongArithmeticBasisPoints() {
        val totalMinor = 20_000_00L
        val amountMinor = 15_000_00L
        val shareBasisPoints = ((amountMinor * 10_000L) / totalMinor).toInt()
        assertEquals(7500, shareBasisPoints)

        val item = CategoryBreakdownUiItem(
            categoryId = EntityId("cat-1"),
            name = "Market",
            amount = Money(amountMinor, Currency.TRY),
            transactionCount = 5,
            shareBasisPoints = shareBasisPoints,
        )
        assertEquals(7500, item.shareBasisPoints)
        assertEquals(0.75f, item.shareRatio)
    }

    @Test
    fun financialRhythmUiModel_carriesDayOfWeekDirectly() {
        val rhythm = FinancialRhythmUiModel(
            busiestDay = kotlinx.datetime.DayOfWeek.MONDAY,
            busiestDayExpense = Money(8_000_00L, Currency.TRY),
            lowestExpenseWeekNumber = 2,
            lowestExpenseWeekExpense = Money(2_000_00L, Currency.TRY),
        )
        assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, rhythm.busiestDay)
        assertEquals(8_000_00L, rhythm.busiestDayExpense.amountMinor)
        assertEquals(2, rhythm.lowestExpenseWeekNumber)
    }

    @Test
    fun reportUiError_genericErrorContract() {
        val error: ReportUiError = ReportUiError.Generic
        assertTrue(error is ReportUiError.Generic)
    }

    @Test
    fun reportFilterUiState_toDomain_mapsCorrectly() {
        val uiState =
            ReportFilterUiState(
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

    @Test
    fun cashFlowMonthUiItem_typedContract_carriesYearMonthAndMoneyWithoutStrings() {
        val ym = com.feniqo.mobile.domain.model.YearMonth.from(2026, 9)
        val income = Money(60_000_00L, Currency.TRY)
        val expense = Money(25_000_00L, Currency.TRY)
        val net = MoneyDelta(35_000_00L, Currency.TRY)

        val item = CashFlowMonthUiItem(
            yearMonth = ym,
            income = income,
            expense = expense,
            net = net,
        )

        assertEquals(2026, item.yearMonth.year)
        assertEquals(9, item.yearMonth.monthNumber)
        assertEquals(60_000_00L, item.incomeMinor)
        assertEquals(25_000_00L, item.expenseMinor)
        assertTrue(item.isNetPositive)
        assertFalse(item.maskAmounts)
    }

    @Test
    fun weeklyDualBarUiPoint_typedContract_preservesMinorUnits() {
        val point = WeeklyDualBarUiPoint(
            weekNumber = 3,
            incomeMinor = 15_000_00L,
            expenseMinor = 10_000_00L,
        )
        assertEquals(3, point.weekNumber)
        assertEquals(15_000_00L, point.incomeMinor)
        assertEquals(10_000_00L, point.expenseMinor)
    }

    @Test
    fun categoryDetail_merchantAndComparison_typedModelsContract() {
        val merchant = MerchantBreakdownUiItem(
            merchantName = "Kahve Dünyası",
            amount = Money(450_00L, Currency.TRY),
            shareBasisPoints = 1500,
            transactionCount = 4,
        )
        assertEquals("Kahve Dünyası", merchant.merchantName)
        assertEquals(450_00L, merchant.amount.amountMinor)
        assertEquals(1500, merchant.shareBasisPoints)
        assertEquals(4, merchant.transactionCount)

        val available: CategoryDetailComparisonUiState = CategoryDetailComparisonUiState.Available(
            previousMonthExpense = Money(500_00L, Currency.TRY),
            changePercentageBasisPoints = -1000,
            isDecreased = true,
        )
        assertTrue(available is CategoryDetailComparisonUiState.Available)
        assertTrue(available.isDecreased)
        assertEquals(-1000, available.changePercentageBasisPoints)

        assertTrue(CategoryDetailComparisonUiState.Calculating is CategoryDetailComparisonUiState)
        assertTrue(CategoryDetailComparisonUiState.Unavailable is CategoryDetailComparisonUiState)
        assertEquals(9, AllReportsHubItem.entries.size)
    }
}
