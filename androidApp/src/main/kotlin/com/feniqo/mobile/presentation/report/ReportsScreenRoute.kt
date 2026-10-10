package com.feniqo.mobile.presentation.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * 01 Raporlar Ana Ekran Rotası.
 */
@Composable
fun ReportsScreenRoute(
    onNavigateToCustomDateRange: () -> Unit,
    onNavigateToMultiCurrency: () -> Unit,
    onNavigateToPeriodSummary: () -> Unit = {},
    onNavigateToAllReportsHub: () -> Unit = {},
    onNavigateToCategoryBreakdown: () -> Unit = {},
    onNavigateToCategoryDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToCashFlow: () -> Unit = {},
    onNavigateToPeriodComparison: () -> Unit = {},
    onNavigateToSpendingCalendar: () -> Unit = {},
    onNavigateToBudgetPerformance: () -> Unit = {},
    onNavigateToSubscriptionSummary: () -> Unit = {},
    onNavigateToDebtSummary: () -> Unit = {},
    onNavigateToForecast: () -> Unit = {},
    onNavigateToFinancialInsights: () -> Unit = {},
    onNavigateToAddTransaction: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsScreen(
        state = uiState,
        referenceDate = viewModel.getCurrentReferenceDate(),
        onApplyFilter = viewModel::applyFilter,
        onRemoveFilterChip = viewModel::removeFilterChip,
        onClearFilters = viewModel::clearFilters,
        onNavigateToCustomDateRange = onNavigateToCustomDateRange,
        onNavigateToMultiCurrency = onNavigateToMultiCurrency,
        onNavigateToSystemStatuses = {},
        onSelectCategory = onNavigateToCategoryBreakdown,
        onNavigateToPeriodSummary = onNavigateToPeriodSummary,
        onNavigateToAllReportsHub = onNavigateToAllReportsHub,
        onNavigateToCategoryBreakdown = onNavigateToCategoryBreakdown,
        onNavigateToCashFlow = onNavigateToCashFlow,
        onNavigateToPeriodComparison = onNavigateToPeriodComparison,
        onNavigateToSpendingCalendar = onNavigateToSpendingCalendar,
        onNavigateToBudgetPerformance = onNavigateToBudgetPerformance,
        onNavigateToSubscriptionSummary = onNavigateToSubscriptionSummary,
        onNavigateToDebtSummary = onNavigateToDebtSummary,
        onNavigateToForecast = onNavigateToForecast,
        onNavigateToFinancialInsights = onNavigateToFinancialInsights,
        onAddTransaction = onNavigateToAddTransaction,
        onNavigateToHome = onNavigateHome,
        onNavigateToSyncStatus = {},
        onRetry = viewModel::retry,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 02 & 03 Dönem Özeti Ekran Rotası.
 */
@Composable
fun PeriodSummaryReportRoute(
    onNavigateToCategoryDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState.contentState as? ReportsContentState.Success ?: return

    val refDate = viewModel.getCurrentReferenceDate()
    val currentMonthDays = 30
    val dailyAvgMinor = if (currentMonthDays > 0) success.report.expense.amountMinor / currentMonthDays else 0L

    PeriodSummaryScreen(
        currentMonth = YearMonth.from(refDate.year, refDate.monthNumber),
        income = success.income,
        expense = success.expense,
        net = success.net,
        isNetPositive = success.isNetPositive,
        savingsRateBasisPoints = success.savingsRateBasisPoints,
        transactionCount = success.report.transactionCount,
        dailyAverageExpense = Money(dailyAvgMinor, success.report.income.currency),
        weeklyPoints = emptyList(),
        topCategories = success.categoryBreakdown,
        financialRhythm = success.financialRhythm,
        insightPayload = success.insightPayload,
        onPreviousMonth = {},
        onNextMonth = {},
        onNavigateToCategoryBreakdown = { onNavigateToCategoryDetail("", "") },
        onNavigateToPeriodComparison = {},
        onNavigateBack = onNavigateBack,
        maskAmounts = success.maskAmounts,
        modifier = modifier,
    )
}

/**
 * 04 Tüm Raporlar Merkezi Rotası.
 */
@Composable
fun AllReportsHubRoute(
    onNavigateToCategoryBreakdown: () -> Unit,
    onNavigateToCashFlow: () -> Unit,
    onNavigateToPeriodComparison: () -> Unit,
    onNavigateToSpendingCalendar: () -> Unit,
    onNavigateToBudgetPerformance: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AllReportsHubScreen(
        onNavigateToCategoryBreakdown = onNavigateToCategoryBreakdown,
        onNavigateToCashFlow = onNavigateToCashFlow,
        onNavigateToPeriodComparison = onNavigateToPeriodComparison,
        onNavigateToSpendingCalendar = onNavigateToSpendingCalendar,
        onNavigateToBudgetPerformance = onNavigateToBudgetPerformance,
        onNavigateToSubscriptionSummary = onNavigateToSubscriptions,
        onNavigateToDebtSummary = onNavigateToDebts,
        onNavigateToForecast = onNavigateToForecast,
        onNavigateToFinancialInsights = onNavigateToInsights,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 05 & 08 Kategori Dağılımı Rotası.
 */
@Composable
fun CategoryBreakdownReportRoute(
    onNavigateToCategoryDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState.contentState as? ReportsContentState.Success ?: return

    CategoryBreakdownReportScreen(
        periodPreset = uiState.filterState.periodPreset,
        customDateRange = if (uiState.filterState.periodPreset == ReportPeriodPreset.CUSTOM &&
            uiState.filterState.customStartDate != null && uiState.filterState.customEndDate != null
        ) {
            ReportDateRange(uiState.filterState.customStartDate!!, uiState.filterState.customEndDate!!)
        } else null,
        totalExpense = success.expense,
        totalIncome = success.income,
        expenseTransactionCount = success.report.transactionCount,
        incomeTransactionCount = 0,
        expenseCategories = success.categoryBreakdown,
        incomeCategories = emptyList(),
        onSelectCategory = { catId, catName ->
            onNavigateToCategoryDetail(catId?.value ?: "", catName)
        },
        onChangePeriod = {},
        onNavigateBack = onNavigateBack,
        maskAmounts = success.maskAmounts,
        modifier = modifier,
    )
}

/**
 * 06 & 07 Kategori Detayı ve İşlemleri Rotası.
 */
@Composable
fun CategoryDetailReportRoute(
    categoryId: String,
    categoryName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState.contentState as? ReportsContentState.Success
    val catItem = success?.categoryBreakdown?.find { it.categoryId?.value == categoryId }

    val currency = success?.report?.income?.currency ?: Currency.TRY
    val isCategoryMissing = catItem?.isCategoryMissing ?: (categoryId.isBlank() || catItem == null)
    val displayName = if (isCategoryMissing) null else (catItem?.name ?: categoryName.ifBlank { null })

    CategoryDetailReportScreen(
        categoryName = displayName,
        isCategoryMissing = isCategoryMissing,
        periodPreset = uiState.filterState.periodPreset,
        customDateRange = if (uiState.filterState.periodPreset == ReportPeriodPreset.CUSTOM &&
            uiState.filterState.customStartDate != null && uiState.filterState.customEndDate != null
        ) {
            ReportDateRange(uiState.filterState.customStartDate!!, uiState.filterState.customEndDate!!)
        } else null,
        totalSpending = catItem?.amount ?: Money.zero(currency),
        transactionCount = catItem?.transactionCount ?: 0,
        periodShareBasisPoints = catItem?.shareBasisPoints ?: 0,
        weeklyTrend = emptyList(),
        comparisonState = CategoryDetailComparisonUiState.Calculating,
        merchantBreakdown = emptyList(),
        transactions = emptyList(),
        onNavigateToTransactions = {},
        onNavigateBack = onNavigateBack,
        maskAmounts = success?.maskAmounts ?: false,
        modifier = modifier,
    )
}

/**
 * 09 Nakit Akışı Ekran Rotası.
 */
@Composable
fun CashFlowReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts =
        (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false

    var monthCount by remember { mutableIntStateOf(6) }
    val cashFlowSummary by viewModel.observeCashFlow(monthCount).collectAsStateWithLifecycle(initialValue = null)

    val summary = cashFlowSummary
    val currency = summary?.points?.firstOrNull()?.income?.currency ?: Currency.TRY
    val monthlyUiItems = summary?.points?.map { pt ->
        CashFlowMonthUiItem(
            yearMonth = pt.yearMonth,
            income = pt.income,
            expense = pt.expense,
            net = pt.net,
            isNetPositive = pt.net.amountMinor >= 0L,
            incomeMinor = pt.income.amountMinor,
            expenseMinor = pt.expense.amountMinor,
            maskAmounts = maskAmounts,
        )
    } ?: emptyList()

    val totalIncomeMinor = summary?.points?.fold(0L) { acc, p -> acc + p.income.amountMinor } ?: 0L
    val totalExpenseMinor = summary?.points?.fold(0L) { acc, p -> acc + p.expense.amountMinor } ?: 0L
    val netDiffMinor = totalIncomeMinor - totalExpenseMinor
    val netDiff = MoneyDelta(netDiffMinor, currency)

    CashFlowReportScreen(
        monthlyPoints = monthlyUiItems,
        totalIncome = Money(totalIncomeMinor, currency),
        totalExpense = Money(totalExpenseMinor, currency),
        netDifference = netDiff,
        isNetPositive = netDiffMinor >= 0L,
        averageIncome = summary?.averageIncome ?: Money.zero(currency),
        averageExpense = summary?.averageExpense ?: Money.zero(currency),
        strongestMonth = summary?.bestMonth,
        weakestMonth = summary?.weakestMonth,
        selectedMonthCount = monthCount,
        onSelectMonthRange = { monthCount = it },
        onBackClick = onNavigateBack,
        maskAmounts = maskAmounts,
        modifier = modifier,
    )
}

/**
 * 10, 11 & 12 Dönem Karşılaştırma Rotası.
 */
@Composable
fun PeriodComparisonReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    var showSheet by remember { mutableStateOf(false) }

    val comparisonState = PeriodComparisonUiState.Unavailable

    PeriodComparisonReportScreen(
        comparisonState = comparisonState,
        onSelectPeriodClick = { showSheet = true },
        onCategoryClick = {},
        onBackClick = onNavigateBack,
        modifier = modifier,
    )

    if (showSheet) {
        SelectComparisonPeriodSheet(
            onDismissRequest = { showSheet = false },
            onApplyPreset = { showSheet = false },
        )
    }
}

/**
 * 13 Harcama Takvimi Rotası.
 */
@Composable
fun SpendingCalendarReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    val refDate = viewModel.getCurrentReferenceDate()
    var currentMonth by remember { mutableStateOf(YearMonth.from(refDate.year, refDate.monthNumber)) }
    val calendarSummary by viewModel.observeSpendingCalendar(currentMonth).collectAsStateWithLifecycle(initialValue = null)

    var selectedDay by remember { mutableStateOf<CalendarDayUiModel?>(null) }

    val daysUi = calendarSummary?.days?.map { d ->
        val heat = when {
            d.expense.amountMinor == 0L -> 0
            d.expense.amountMinor < 500_00 -> 1
            d.expense.amountMinor < 1500_00 -> 2
            d.expense.amountMinor < 3000_00 -> 3
            else -> 4
        }
        CalendarDayUiModel(
            dayNumber = d.date.dayOfMonth,
            date = d.date,
            expense = d.expense,
            transactionCount = d.transactionCount,
            heatLevel = heat,
            isSelected = selectedDay?.date == d.date,
            maskAmounts = maskAmounts,
        )
    } ?: emptyList()

    val dayTransactions = selectedDay?.let { sel ->
        val dayData = calendarSummary?.days?.find { it.date == sel.date }
        dayData?.transactions?.map { t ->
            ReportTransactionUiItem(
                id = t.id,
                description = t.description,
                type = t.type,
                categoryId = t.categoryId,
                categoryName = t.categoryName,
                isCategoryMissing = t.isCategoryMissing,
                date = t.date,
                amount = t.amount,
                maskAmounts = maskAmounts,
            )
        }
    } ?: emptyList()

    SpendingCalendarReportScreen(
        currentMonth = currentMonth,
        days = daysUi,
        selectedDay = selectedDay,
        dayTransactions = dayTransactions,
        maskAmounts = maskAmounts,
        onSelectDay = { selectedDay = it },
        onPreviousMonth = { currentMonth = currentMonth.previousMonth() },
        onNextMonth = { currentMonth = currentMonth.nextMonth() },
        onBackClick = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 14 Bütçe Performansı Rotası.
 */
@Composable
fun BudgetPerformanceReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    val refDate = viewModel.getCurrentReferenceDate()
    val currentMonth = YearMonth.from(refDate.year, refDate.monthNumber)
    val budgetSummary by viewModel.observeBudgetPerformance(currentMonth).collectAsStateWithLifecycle(initialValue = null)

    val items = budgetSummary?.categoryProgressList?.map { b ->
        BudgetPerformanceUiItem(
            budgetId = b.budgetId,
            categoryId = b.categoryId,
            name = b.categoryName,
            isCategoryMissing = b.isCategoryMissing,
            budget = b.budgetAmount,
            spent = b.spentAmount,
            usageBasisPoints = b.usagePercentageBasisPoints,
            isExceeded = b.isExceeded,
            maskAmounts = maskAmounts,
        )
    } ?: emptyList()

    val selectedCurrency = uiState.filterState.currency
    val totalBudget = budgetSummary?.totalBudget ?: Money.zero(selectedCurrency)
    val totalSpent = budgetSummary?.totalSpent ?: Money.zero(selectedCurrency)
    val totalRemaining = budgetSummary?.totalRemaining ?: Money.zero(selectedCurrency)
    val remainingDelta = MoneyDelta(totalRemaining.amountMinor, totalRemaining.currency)

    BudgetPerformanceReportScreen(
        currentMonth = currentMonth,
        totalBudget = totalBudget,
        totalSpent = totalSpent,
        remaining = remainingDelta,
        usageBasisPoints = budgetSummary?.usagePercentageBasisPoints ?: 0,
        isExceeded = (budgetSummary?.budgetsExceededCount ?: 0) > 0,
        budgetItems = items,
        maskAmounts = maskAmounts,
        onBackClick = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 15 Abonelik Özeti Rotası.
 */
@Composable
fun SubscriptionSummaryReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    val selectedCurrency = uiState.filterState.currency
    val subSummary by viewModel.observeSubscriptionSummary().collectAsStateWithLifecycle(initialValue = null)

    val upcoming = subSummary?.upcomingPayments?.map { u ->
        SubscriptionReportUiItem(
            id = u.subscriptionId,
            name = u.name,
            amount = u.amount,
            renewalDate = u.renewalDate,
            maskAmounts = maskAmounts,
        )
    } ?: emptyList()

    SubscriptionSummaryReportScreen(
        monthlyTotal = subSummary?.totalMonthlyEstimate ?: Money.zero(selectedCurrency),
        activeSubscriptionCount = subSummary?.activeSubscriptionCount ?: 0,
        upcomingSubscriptions = upcoming,
        maskAmounts = maskAmounts,
        onBackClick = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 16 Borç ve Alacak Özeti Rotası.
 */
@Composable
fun DebtSummaryReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    val selectedCurrency = (uiState.contentState as? ReportsContentState.Success)?.expense?.currency
        ?: uiState.filterState.currency

    val debtSummary by viewModel.observeDebtSummary().collectAsStateWithLifecycle(initialValue = null)

    val deadlines = debtSummary?.upcomingDeadlines?.map { d ->
        DebtDeadlineUiItem(
            title = d.title,
            amount = d.amount,
            dueDate = d.dueDate,
            isReceivable = d.isReceivable,
            isOverdue = d.isOverdue,
            maskAmounts = maskAmounts,
        )
    } ?: emptyList()

    val totalDebt = debtSummary?.totalDebtRemaining ?: Money(0L, selectedCurrency)
    val totalReceivable = debtSummary?.totalReceivableRemaining ?: Money(0L, selectedCurrency)
    val netBalance = debtSummary?.netBalance ?: MoneyDelta(0L, selectedCurrency)

    DebtSummaryReportScreen(
        totalDebt = totalDebt,
        totalReceivable = totalReceivable,
        netBalance = netBalance,
        isNetPositive = netBalance.amountMinor >= 0L,
        deadlines = deadlines,
        maskAmounts = maskAmounts,
        onBackClick = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 17 & 18 Gelecek Dönem Tahmini Rotası.
 */
@Composable
fun ForecastReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false
    val selectedCurrency = (uiState.contentState as? ReportsContentState.Success)?.expense?.currency
        ?: uiState.filterState.currency

    val refDate = viewModel.getCurrentReferenceDate()
    val nextMonth = YearMonth.from(refDate.year, refDate.monthNumber).nextMonth()
    val forecastSummary by viewModel.observeForecastReport(lookaheadMonths = 6).collectAsStateWithLifecycle(initialValue = null)

    var showDetail by remember { mutableStateOf(false) }

    val sources = forecastSummary?.contributingSources?.map { s ->
        ForecastSourceUiItem(
            type = s.type,
            amount = s.amount,
            percentageBasisPoints = s.percentageBasisPoints,
        )
    } ?: emptyList()

    val expectedDiff = forecastSummary?.expectedDifference ?: MoneyDelta(0L, selectedCurrency)
    val expectedIncome = forecastSummary?.expectedIncome ?: Money(0L, selectedCurrency)
    val fixedExpense = forecastSummary?.fixedAndPlannedExpenses ?: Money(0L, selectedCurrency)
    val variableExpense = forecastSummary?.variableExpensesEstimate ?: Money(0L, selectedCurrency)
    val dailyAvg = forecastSummary?.dailyVariableAverage ?: Money(0L, selectedCurrency)
    val activeRecurringCount = forecastSummary?.activeRecurringCount ?: 0
    val forecastMonth = forecastSummary?.forecastMonth ?: nextMonth

    if (showDetail) {
        ForecastDetailReportScreen(
            projectionPoints = forecastSummary?.projectionPoints ?: emptyList(),
            dailyAverage = dailyAvg,
            activeRecurringCount = activeRecurringCount,
            maskAmounts = maskAmounts,
            onBackClick = { showDetail = false },
            modifier = modifier,
        )
    } else {
        ForecastOverviewReportScreen(
            forecastMonth = forecastMonth,
            projectedNet = expectedDiff,
            isProjectedNetPositive = expectedDiff.amountMinor >= 0L,
            projectedIncome = expectedIncome,
            fixedExpense = fixedExpense,
            variableExpense = variableExpense,
            sources = sources,
            maskAmounts = maskAmounts,
            onViewDetailClick = { showDetail = true },
            onBackClick = onNavigateBack,
            modifier = modifier,
        )
    }
}

/**
 * 19 & 20 Finansal İçgörüler Rotası.
 */
@Composable
fun FinancialInsightsReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskAmounts = (uiState.contentState as? ReportsContentState.Success)?.maskAmounts ?: false

    val insightItems by viewModel.observeFinancialInsights().collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val categoriesById = remember(categories) { categories.associateBy { it.id } }

    var selectedInsightId by remember { mutableStateOf<String?>(null) }

    val uiItems = insightItems.map { item ->
        val contributingUiTransactions = item.contributingTransactions.map { tx ->
            val cat = categoriesById[tx.categoryId]
            val catName = cat?.name ?: if (tx.categoryId == item.categoryId) item.categoryName else null
            val isCatMissing = cat == null && (tx.categoryId != item.categoryId || item.isCategoryMissing)
            ReportTransactionUiItem(
                id = tx.id,
                description = tx.description,
                type = tx.type,
                categoryId = tx.categoryId,
                categoryName = catName,
                isCategoryMissing = isCatMissing,
                date = tx.transactionDate,
                amount = tx.amount,
                maskAmounts = maskAmounts,
            )
        }

        FinancialInsightUiItem(
            id = item.id,
            type = item.type,
            categoryId = item.categoryId,
            categoryName = item.categoryName,
            isCategoryMissing = item.isCategoryMissing,
            changePercentageBasisPoints = item.changePercentageBasisPoints,
            currentPeriodAmount = item.currentPeriodAmount,
            previousPeriodAmount = item.previousPeriodAmount,
            delta = item.delta,
            isPositive = item.delta.amountMinor >= 0L,
            transactionCount = item.supportingTransactionCount,
            contributingTransactions = contributingUiTransactions,
            maskAmounts = maskAmounts,
        )
    }

    val selectedInsight = uiItems.find { it.id == selectedInsightId }

    if (selectedInsight != null) {
        InsightDetailReportScreen(
            insight = selectedInsight,
            transactions = selectedInsight.contributingTransactions,
            maskAmounts = maskAmounts,
            onViewTransactionsClick = {},
            onBackClick = { selectedInsightId = null },
            modifier = modifier,
        )
    } else {
        FinancialInsightsReportScreen(
            insights = uiItems,
            maskAmounts = maskAmounts,
            onInsightClick = { selectedInsightId = it.id },
            onBackClick = onNavigateBack,
            modifier = modifier,
        )
    }
}

/**
 * 22 Özel Tarih Aralığı Ekran Rotası.
 */
@Composable
fun CustomDateRangeRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CustomDateRangeScreen(
        referenceDate = viewModel.getCurrentReferenceDate(),
        initialStartDate = uiState.filterState.customStartDate,
        initialEndDate = uiState.filterState.customEndDate,
        onApplyRange = { start: LocalDate, end: LocalDate ->
            viewModel.setCustomDateRange(start, end)
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 23 Çoklu Para Birimi Ekran Rotası.
 */
@Composable
fun MultiCurrencyReportRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summaries = when (val content = uiState.contentState) {
        is ReportsContentState.Success -> content.multiCurrencySummaries
        else -> emptyList()
    }

    MultiCurrencyReportsScreen(
        summaries = summaries,
        selectedCurrency = uiState.filterState.currency,
        onSelectCurrency = { currency: Currency ->
            viewModel.selectCurrency(currency)
            onNavigateBack()
        },
        onOpenFilterSheet = onNavigateBack,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * 26 Sistem Durumları (Katalog / Debug) Ekran Rotası.
 */
@Composable
fun ReportSystemStatusesRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingCount = when (val conn = uiState.connectionState) {
        is ReportConnectionState.Offline -> conn.pendingOperationCount
        else -> 0
    }

    ReportSystemStatusesScreen(
        pendingCount = pendingCount,
        onRetry = viewModel::retry,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
