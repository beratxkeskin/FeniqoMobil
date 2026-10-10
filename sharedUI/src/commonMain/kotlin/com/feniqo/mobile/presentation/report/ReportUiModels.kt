package com.feniqo.mobile.presentation.report

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialReport
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.presentation.common.symbol
import kotlinx.datetime.DayOfWeek

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

sealed interface ActiveFilterChipUiModel {
    val id: String
    val type: ActiveFilterType

    data class Period(
        val preset: ReportPeriodPreset,
        val dateRange: ReportDateRange,
        override val id: String = "period",
    ) : ActiveFilterChipUiModel {
        override val type: ActiveFilterType get() = ActiveFilterType.PERIOD
    }

    data class Type(
        val typeFilter: ReportTypeFilter,
        override val id: String = "type",
    ) : ActiveFilterChipUiModel {
        override val type: ActiveFilterType get() = ActiveFilterType.TYPE
    }

    data class CurrencyChip(
        val currency: Currency,
        override val id: String = "currency",
    ) : ActiveFilterChipUiModel {
        override val type: ActiveFilterType get() = ActiveFilterType.CURRENCY
    }

    data class CategoryChip(
        val categoryId: EntityId?,
        val categoryName: String,
        override val id: String = "category",
    ) : ActiveFilterChipUiModel {
        override val type: ActiveFilterType get() = ActiveFilterType.CATEGORY
    }
}

data class MultiCurrencyReportUiModel(
    val currency: Currency,
    val income: Money,
    val expense: Money,
    val net: MoneyDelta,
    val isNetPositive: Boolean = net.amountMinor >= 0L,
    val transactionCount: Int,
    val maskAmounts: Boolean = false,
)

data class MonthlyTrendUiModel(
    val yearMonth: com.feniqo.mobile.domain.model.YearMonth,
    val income: Money,
    val expense: Money,
    val net: MoneyDelta,
    val isNetPositive: Boolean = net.amountMinor >= 0L,
    val incomeMinor: Long = income.amountMinor,
    val expenseMinor: Long = expense.amountMinor,
    val maskAmounts: Boolean = false,
)

data class FinancialRhythmUiModel(
    val busiestDay: DayOfWeek?,
    val busiestDayExpense: Money,
    val lowestExpenseWeekNumber: Int,
    val lowestExpenseWeekExpense: Money,
    val maskAmounts: Boolean = false,
)

data class TopCategoryUiModel(
    val name: String?,
    val amount: Money,
    val transactionCount: Int,
    val isCategoryMissing: Boolean = false,
    val maskAmounts: Boolean = false,
)

data class CategoryBreakdownUiItem(
    val categoryId: EntityId?,
    val name: String?,
    val isCategoryMissing: Boolean = false,
    val amount: Money,
    val transactionCount: Int,
    val shareBasisPoints: Int,
    val shareRatio: Float = shareBasisPoints / 10_000f,
    val maskAmounts: Boolean = false,
)

sealed interface ReportInsightPayload {
    data object None : ReportInsightPayload
    data class ExpenseChanged(val changeBasisPoints: Int, val difference: MoneyDelta) : ReportInsightPayload
    data class TransactionCountOnly(val count: Int) : ReportInsightPayload
}

sealed interface ReportUiError {
    data object Generic : ReportUiError
}

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
        val income: Money = report.income,
        val expense: Money = report.expense,
        val net: MoneyDelta = report.net,
        val isNetPositive: Boolean = report.net.amountMinor >= 0L,
        val savingsRateBasisPoints: Int = report.savingsRate.value,
        val multiCurrencySummaries: List<MultiCurrencyReportUiModel>,
        val activeFilters: List<ActiveFilterChipUiModel>,
        val monthlyTrend: List<MonthlyTrendUiModel> = emptyList(),
        val topCategory: TopCategoryUiModel? = null,
        val financialRhythm: FinancialRhythmUiModel? = null,
        val insightPayload: ReportInsightPayload = ReportInsightPayload.None,
        val categoryBreakdown: List<CategoryBreakdownUiItem> = emptyList(),
        val maskAmounts: Boolean = false,
    ) : ReportsContentState

    data class Error(
        val error: ReportUiError = ReportUiError.Generic,
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
