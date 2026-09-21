package com.feniqo.mobile.domain.model

enum class ReportPeriodPreset {
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    CUSTOM,
}

enum class ReportTypeFilter {
    ALL,
    INCOME,
    EXPENSE,
}

data class ReportDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(endDate >= startDate) { "Rapor bitiş tarihi başlangıç tarihinden önce olamaz." }
    }
}

data class ReportFilter(
    val periodPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    val customDateRange: ReportDateRange? = null,
    val typeFilter: ReportTypeFilter = ReportTypeFilter.ALL,
    val currency: Currency = Currency.TRY,
    val categoryId: EntityId? = null,
) {
    init {
        if (periodPreset == ReportPeriodPreset.CUSTOM) {
            requireNotNull(customDateRange) { "Özel tarih aralığı filtresinde tarih aralığı null olamaz." }
        }
    }
}

data class MultiCurrencyReportSummary(
    val currency: Currency,
    val income: Money,
    val expense: Money,
    val net: MoneyDelta,
    val transactionCount: Int,
) {
    init {
        require(income.currency == currency && expense.currency == currency && net.currency == currency) {
            "Para birimi alanları uyuşmalıdır."
        }
        require(transactionCount >= 0) { "İşlem sayısı negatif olamaz." }
    }
}

data class ReportAvailability(
    val totalReportableTransactionCount: Int,
    val filteredTransactionCount: Int,
) {
    init {
        require(totalReportableTransactionCount >= 0) { "Toplam işlem sayısı negatif olamaz." }
        require(filteredTransactionCount >= 0) { "Filtrelenmiş işlem sayısı negatif olamaz." }
        require(filteredTransactionCount <= totalReportableTransactionCount) {
            "Filtrelenmiş işlem sayısı toplam işlem sayısından fazla olamaz."
        }
    }

    val isWorkspaceEmpty: Boolean get() = totalReportableTransactionCount == 0
    val isFilteredEmpty: Boolean get() = totalReportableTransactionCount > 0 && filteredTransactionCount == 0
    val hasData: Boolean get() = filteredTransactionCount > 0
}

data class ReportSyncStatus(
    val isOffline: Boolean,
    val pendingOperationCount: Int,
) {
    init {
        require(pendingOperationCount >= 0) { "Bekleyen senkronizasyon sayısı negatif olamaz." }
    }
}

data class MonthlyTrendPoint(
    val yearMonth: YearMonth,
    val income: Money,
    val expense: Money,
    val net: MoneyDelta,
)

data class WeeklySpendingPoint(
    val weekNumber: Int,
    val income: Money,
    val expense: Money,
)

data class FinancialRhythmSummary(
    val busiestDayName: String,
    val busiestDayExpense: Money,
    val lowestExpenseWeekNumber: Int,
    val lowestExpenseWeekExpense: Money,
)

data class MerchantSpendingSummary(
    val merchantName: String,
    val amount: Money,
    val transactionCount: Int,
    val sharePercentageBasisPoints: Int,
)

data class CategoryDetailSpending(
    val categoryId: EntityId?,
    val categoryName: String,
    val totalExpense: Money,
    val transactionCount: Int,
    val periodShareBasisPoints: Int,
    val weeklyTrend: List<WeeklySpendingPoint>,
    val previousMonthExpense: Money?,
    val changePercentageBasisPoints: Int?,
    val merchantBreakdown: List<MerchantSpendingSummary>,
    val transactions: List<Transaction>,
)

data class CashFlowSummary(
    val points: List<MonthlyTrendPoint>,
    val averageIncome: Money,
    val averageExpense: Money,
    val bestMonth: YearMonth?,
    val weakestMonth: YearMonth?,
)

data class CategoryComparisonItem(
    val categoryId: EntityId?,
    val categoryName: String,
    val currentExpense: Money,
    val previousExpense: Money,
    val diff: MoneyDelta,
    val changePercentageBasisPoints: Int?,
)

data class ComparisonHighlightItem(
    val title: String,
    val description: String,
    val isPositive: Boolean,
)

data class PeriodComparisonSummary(
    val currentPeriod: ReportPeriod,
    val previousPeriod: ReportPeriod,
    val currentIncome: Money,
    val previousIncome: Money,
    val incomeDelta: MoneyDelta,
    val incomeChangePercentageBasisPoints: Int?,
    val currentExpense: Money,
    val previousExpense: Money,
    val expenseDelta: MoneyDelta,
    val expenseChangePercentageBasisPoints: Int?,
    val currentNet: MoneyDelta,
    val previousNet: MoneyDelta,
    val netImprovement: MoneyDelta,
    val currentSavingsRate: RateBasisPoints,
    val previousSavingsRate: RateBasisPoints,
    val categoryComparisons: List<CategoryComparisonItem>,
    val highlights: List<ComparisonHighlightItem>,
)

data class CalendarDaySpending(
    val date: LocalDate,
    val expense: Money,
    val transactionCount: Int,
    val dominantCategoryName: String?,
    val dominantCategoryExpense: Money,
    val transactions: List<Transaction>,
)

data class SpendingCalendarSummary(
    val month: YearMonth,
    val days: List<CalendarDaySpending>,
    val totalMonthExpense: Money,
    val highestExpenseDay: LocalDate?,
)

data class CategoryBudgetProgressItem(
    val budgetId: EntityId,
    val categoryId: EntityId,
    val categoryName: String,
    val budgetAmount: Money,
    val spentAmount: Money,
    val usagePercentageBasisPoints: Int,
    val isExceeded: Boolean,
)

data class BudgetPerformanceSummary(
    val totalBudget: Money,
    val totalSpent: Money,
    val totalRemaining: Money,
    val usagePercentageBasisPoints: Int,
    val budgetsOnTrackCount: Int,
    val budgetsExceededCount: Int,
    val categoryProgressList: List<CategoryBudgetProgressItem>,
)

data class SubscriptionUpcomingItem(
    val subscriptionId: EntityId,
    val name: String,
    val amount: Money,
    val renewalDate: LocalDate,
)

data class SubscriptionCategoryDistributionItem(
    val categoryName: String,
    val amount: Money,
    val percentageBasisPoints: Int,
)

data class SubscriptionReportSummary(
    val totalMonthlyEstimate: Money,
    val activeSubscriptionCount: Int,
    val past6MonthsMiniTrend: List<Money>,
    val upcomingPayments: List<SubscriptionUpcomingItem>,
    val categoryDistribution: List<SubscriptionCategoryDistributionItem>,
)

data class DebtDeadlineItem(
    val debtId: EntityId,
    val title: String,
    val amount: Money,
    val dueDate: LocalDate,
    val isReceivable: Boolean,
    val isOverdue: Boolean,
)

data class DebtReportSummary(
    val totalDebtRemaining: Money,
    val debtCount: Int,
    val totalReceivableRemaining: Money,
    val receivableCount: Int,
    val netBalance: MoneyDelta,
    val upcomingDeadlines: List<DebtDeadlineItem>,
    val upcomingCount14Days: Int,
)

data class ForecastProjectionPoint(
    val yearMonth: YearMonth,
    val income: Money,
    val expense: Money,
    val isForecast: Boolean,
)

data class ForecastSourceItem(
    val title: String,
    val amount: Money,
    val percentageBasisPoints: Int,
)

data class ForecastReportSummary(
    val forecastMonth: YearMonth,
    val expectedDifference: MoneyDelta,
    val expectedIncome: Money,
    val fixedAndPlannedExpenses: Money,
    val variableExpensesEstimate: Money,
    val projectionPoints: List<ForecastProjectionPoint>,
    val contributingSources: List<ForecastSourceItem>,
    val assumptionsText: String,
)

enum class InsightType {
    SAVINGS_RATE_INCREASE,
    CATEGORY_INCREASE,
    CATEGORY_DECREASE,
    SUBSCRIPTION_IMPACT,
}

data class FinancialInsightItem(
    val id: String,
    val type: InsightType,
    val title: String,
    val subtitle: String,
    val currentPeriodAmount: Money,
    val previousPeriodAmount: Money,
    val delta: MoneyDelta,
    val supportingTransactionCount: Int,
    val weeklyBreakdown: List<WeeklySpendingPoint>,
    val contributingTransactions: List<Transaction>,
    val explanationText: String,
)
