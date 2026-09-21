package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialReport
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter

/**
 * Rapor filtreleme sheet'i ve UI durumu için sunum modeli.
 */
data class ReportFilterUiState(
    val periodPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val typeFilter: ReportTypeFilter = ReportTypeFilter.ALL,
    val currency: Currency = Currency.TRY,
    val selectedCategoryId: EntityId? = null,
    val selectedCategoryName: String? = null,
) {
    fun toDomain(): ReportFilter {
        val customRange = if (periodPreset == ReportPeriodPreset.CUSTOM && customStartDate != null && customEndDate != null) {
            ReportDateRange(customStartDate, customEndDate)
        } else {
            null
        }
        return ReportFilter(
            periodPreset = periodPreset,
            customDateRange = customRange,
            typeFilter = typeFilter,
            currency = currency,
            categoryId = selectedCategoryId,
        )
    }

    companion object {
        val Default = ReportFilterUiState()

        fun fromDomain(filter: ReportFilter, categoryName: String? = null): ReportFilterUiState =
            ReportFilterUiState(
                periodPreset = filter.periodPreset,
                customStartDate = filter.customDateRange?.startDate,
                customEndDate = filter.customDateRange?.endDate,
                typeFilter = filter.typeFilter,
                currency = filter.currency,
                selectedCategoryId = filter.categoryId,
                selectedCategoryName = categoryName,
            )
    }
}
enum class ActiveFilterType {
    PERIOD,
    TYPE,
    CURRENCY,
    CATEGORY,
}

data class ActiveFilterChipUiModel(
    val id: String,
    val type: ActiveFilterType,
    val label: String,
)

data class MultiCurrencyReportUiModel(
    val currency: Currency,
    val title: String,
    val symbol: String,
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val isNetPositive: Boolean,
    val transactionCount: Int,
)

data class MonthlyTrendUiModel(
    val yearMonth: com.feniqo.mobile.domain.model.YearMonth,
    val monthLabel: String,
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val isNetPositive: Boolean,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class FinancialRhythmUiModel(
    val busiestDayName: String,
    val busiestDayExpenseFormatted: String,
    val lowestExpenseWeekLabel: String,
    val lowestExpenseWeekExpenseFormatted: String,
)

data class TopCategoryUiModel(
    val name: String,
    val amountFormatted: String,
    val transactionCount: Int,
)

data class CategoryBreakdownUiItem(
    val categoryId: EntityId?,
    val name: String,
    val amountFormatted: String,
    val transactionCount: Int,
    val sharePercentageFormatted: String,
    val shareRatio: Float,
)

data class CalendarDayUiModel(
    val dayNumber: Int,
    val date: LocalDate,
    val expenseFormatted: String,
    val transactionCount: Int,
    val heatLevel: Int, // 0..4
    val isSelected: Boolean,
)

data class BudgetPerformanceUiItem(
    val name: String,
    val budgetFormatted: String,
    val spentFormatted: String,
    val usagePercentageFormatted: String,
    val usageRatio: Float,
    val isExceeded: Boolean,
)

data class SubscriptionReportUiItem(
    val name: String,
    val amountFormatted: String,
    val renewalDateFormatted: String,
)

data class DebtDeadlineUiItem(
    val title: String,
    val amountFormatted: String,
    val dueDateFormatted: String,
    val isReceivable: Boolean,
    val isOverdue: Boolean,
)

data class ForecastSourceUiItem(
    val title: String,
    val amountFormatted: String,
    val percentageFormatted: String,
    val ratio: Float,
)

data class ReportTransactionUiItem(
    val id: EntityId,
    val title: String,
    val categoryName: String,
    val dateFormatted: String,
    val amountFormatted: String,
    val isExpense: Boolean,
)

data class CategoryComparisonUiItem(
    val name: String,
    val currentFormatted: String,
    val previousFormatted: String,
    val deltaFormatted: String,
    val percentageFormatted: String,
    val isIncreased: Boolean,
)

data class CashFlowMonthUiItem(
    val monthLabel: String,
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val isNetPositive: Boolean,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class FinancialInsightUiItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountFormatted: String,
    val deltaFormatted: String,
    val isPositive: Boolean,
    val transactionCount: Int,
    val explanation: String,
)

sealed interface ReportsContentState {
    data object Loading : ReportsContentState
    data object EmptyWorkspace : ReportsContentState
    data class EmptyFiltered(
        val activeFilters: List<ActiveFilterChipUiModel>,
    ) : ReportsContentState
    data class Success(
        val report: FinancialReport,
        val incomeFormatted: String,
        val expenseFormatted: String,
        val netFormatted: String,
        val isNetPositive: Boolean,
        val savingsRateFormatted: String,
        val multiCurrencySummaries: List<MultiCurrencyReportUiModel>,
        val activeFilters: List<ActiveFilterChipUiModel>,
        val monthlyTrend: List<MonthlyTrendUiModel> = emptyList(),
        val topCategory: TopCategoryUiModel? = null,
        val financialRhythm: FinancialRhythmUiModel? = null,
        val feniqoInsightText: String = "",
        val categoryBreakdown: List<CategoryBreakdownUiItem> = emptyList(),
    ) : ReportsContentState
    data class Error(
        val message: String,
    ) : ReportsContentState
}

sealed interface ReportConnectionState {
    data object Online : ReportConnectionState
    data class Offline(
        val pendingOperationCount: Int,
    ) : ReportConnectionState
}

data class ReportsScreenState(
    val contentState: ReportsContentState,
    val connectionState: ReportConnectionState,
    val filterState: ReportFilterUiState,
)
