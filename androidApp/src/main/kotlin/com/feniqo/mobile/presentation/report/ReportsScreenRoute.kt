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
        incomeFormatted = success.incomeFormatted,
        expenseFormatted = success.expenseFormatted,
        netFormatted = success.netFormatted,
        isNetPositive = success.isNetPositive,
        savingsRateFormatted = success.savingsRateFormatted,
        transactionCount = success.report.transactionCount,
        dailyAverageExpenseFormatted = MoneyFormatter.format(
            Money(dailyAvgMinor, success.report.income.currency)
        ),
        weeklyData = emptyList(),
        topCategories = success.categoryBreakdown,
        donutSlices = success.categoryBreakdown.map { it.name to it.shareRatio },
        financialRhythm = success.financialRhythm,
        insightText = success.feniqoInsightText,
        onPreviousMonth = {},
        onNextMonth = {},
        onNavigateToCategoryBreakdown = { onNavigateToCategoryDetail("", "") },
        onNavigateToPeriodComparison = {},
        onNavigateBack = onNavigateBack,
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
        periodLabel = "Bu Ay",
        totalExpenseFormatted = success.expenseFormatted,
        totalIncomeFormatted = success.incomeFormatted,
        expenseTransactionCount = success.report.transactionCount,
        incomeTransactionCount = 0,
        expenseCategories = success.categoryBreakdown,
        incomeCategories = emptyList(),
        onSelectCategory = { catId, catName ->
            onNavigateToCategoryDetail(catId?.value ?: "", catName)
        },
        onChangePeriod = {},
        onNavigateBack = onNavigateBack,
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

    CategoryDetailReportScreen(
        categoryName = categoryName,
        categoryDescription = "Kategori harcama özeti",
        periodLabel = "Bu Ay",
        totalSpendingFormatted = catItem?.amountFormatted ?: "₺0",
        transactionCount = catItem?.transactionCount ?: 0,
        periodShareFormatted = catItem?.sharePercentageFormatted ?: "%0",
        weeklyData = emptyList(),
        previousMonthComparisonText = "Geçen aya göre veri hesaplanıyor",
        merchantBreakdown = emptyList(),
        transactions = emptyList(),
        onNavigateToTransactions = {},
        onNavigateBack = onNavigateBack,
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
    var monthCount by remember { mutableIntStateOf(6) }
    val cashFlowSummary by viewModel.observeCashFlow(monthCount).collectAsStateWithLifecycle(initialValue = null)

    val summary = cashFlowSummary
    val currency = summary?.points?.firstOrNull()?.income?.currency ?: Currency.TRY
    val monthlyUiItems = summary?.points?.map { pt ->
        CashFlowMonthUiItem(
            monthLabel = "${pt.yearMonth.monthNumber}. Ay (${pt.yearMonth.year})",
            incomeFormatted = MoneyFormatter.format(pt.income),
            expenseFormatted = MoneyFormatter.format(pt.expense),
            netFormatted = MoneyFormatter.formatDelta(pt.net, includeSign = true),
            isNetPositive = pt.net.amountMinor >= 0,
            incomeMinor = pt.income.amountMinor,
            expenseMinor = pt.expense.amountMinor,
        )
    } ?: emptyList()

    val totalIncomeMinor = summary?.points?.fold(0L) { acc, p -> acc + p.income.amountMinor } ?: 0L
    val totalExpenseMinor = summary?.points?.fold(0L) { acc, p -> acc + p.expense.amountMinor } ?: 0L
    val netDiffMinor = totalIncomeMinor - totalExpenseMinor
    val netDiff = MoneyDelta(netDiffMinor, currency)

    CashFlowReportScreen(
        monthlyPoints = monthlyUiItems,
        totalIncomeFormatted = MoneyFormatter.format(Money(totalIncomeMinor, currency)),
        totalExpenseFormatted = MoneyFormatter.format(Money(totalExpenseMinor, currency)),
        netDifferenceFormatted = MoneyFormatter.formatDelta(netDiff, includeSign = true),
        isNetPositive = netDiffMinor >= 0L,
        averageIncomeFormatted = MoneyFormatter.format(summary?.averageIncome ?: Money(0L, currency)),
        averageExpenseFormatted = MoneyFormatter.format(summary?.averageExpense ?: Money(0L, currency)),
        strongestMonthLabel = summary?.bestMonth?.let { "${it.monthNumber}. Ay" } ?: "-",
        weakestMonthLabel = summary?.weakestMonth?.let { "${it.monthNumber}. Ay" } ?: "-",
        selectedMonthCount = monthCount,
        onSelectMonthRange = { monthCount = it },
        onBackClick = onNavigateBack,
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
    val success = uiState.contentState as? ReportsContentState.Success
    var showSheet by remember { mutableStateOf(false) }

    val comparisonItems = success?.categoryBreakdown?.map { cb ->
        CategoryComparisonUiItem(
            name = cb.name,
            currentFormatted = cb.amountFormatted,
            previousFormatted = "₺0",
            deltaFormatted = "+${cb.amountFormatted}",
            percentageFormatted = cb.sharePercentageFormatted,
            isIncreased = true,
        )
    } ?: emptyList()

    PeriodComparisonReportScreen(
        currentPeriodLabel = "Bu Ay",
        previousPeriodLabel = "Geçen Ay",
        currentNetFormatted = success?.netFormatted ?: "₺0",
        previousNetFormatted = "₺0",
        netDifferenceFormatted = success?.netFormatted ?: "₺0",
        isNetImproved = success?.isNetPositive ?: true,
        incomeDeltaFormatted = "+${success?.incomeFormatted ?: "₺0"}",
        expenseDeltaFormatted = "+${success?.expenseFormatted ?: "₺0"}",
        savingsRateDeltaFormatted = success?.savingsRateFormatted ?: "%0",
        categoryComparisons = comparisonItems,
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
            dayNumber = d.date.day,
            date = d.date,
            expenseFormatted = MoneyFormatter.format(d.expense),
            transactionCount = d.transactionCount,
            heatLevel = heat,
            isSelected = selectedDay?.date == d.date,
        )
    } ?: emptyList()

    SpendingCalendarReportScreen(
        monthLabel = "${ReportSummaryFormatter.monthName(currentMonth.monthNumber)} ${currentMonth.year}",
        days = daysUi,
        selectedDay = selectedDay,
        dayTransactions = emptyList(),
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
    val refDate = viewModel.getCurrentReferenceDate()
    val currentMonth = YearMonth.from(refDate.year, refDate.monthNumber)
    val budgetSummary by viewModel.observeBudgetPerformance(currentMonth).collectAsStateWithLifecycle(initialValue = null)

    val items = budgetSummary?.categoryProgressList?.map { b ->
        BudgetPerformanceUiItem(
            name = b.categoryName,
            budgetFormatted = MoneyFormatter.format(b.budgetAmount),
            spentFormatted = MoneyFormatter.format(b.spentAmount),
            usagePercentageFormatted = "%${b.usagePercentageBasisPoints / 100}",
            usageRatio = b.usagePercentageBasisPoints / 10000f,
            isExceeded = b.isExceeded,
        )
    } ?: emptyList()

    BudgetPerformanceReportScreen(
        periodLabel = "${ReportSummaryFormatter.monthName(currentMonth.monthNumber)} ${currentMonth.year}",
        totalBudgetFormatted = MoneyFormatter.format(budgetSummary?.totalBudget ?: Money(0, Currency.TRY)),
        totalSpentFormatted = MoneyFormatter.format(budgetSummary?.totalSpent ?: Money(0, Currency.TRY)),
        remainingFormatted = MoneyFormatter.format(budgetSummary?.totalRemaining ?: Money(0, Currency.TRY)),
        usagePercentageFormatted = "%${(budgetSummary?.usagePercentageBasisPoints ?: 0) / 100}",
        usageRatio = (budgetSummary?.usagePercentageBasisPoints ?: 0) / 10000f,
        isExceeded = (budgetSummary?.budgetsExceededCount ?: 0) > 0,
        budgetItems = items,
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
    val subSummary by viewModel.observeSubscriptionSummary().collectAsStateWithLifecycle(initialValue = null)

    val upcoming = subSummary?.upcomingPayments?.map { u ->
        SubscriptionReportUiItem(
            name = u.name,
            amountFormatted = MoneyFormatter.format(u.amount),
            renewalDateFormatted = ReportSummaryFormatter.formatDateDisplay(u.renewalDate),
        )
    } ?: emptyList()

    SubscriptionSummaryReportScreen(
        monthlyTotalFormatted = MoneyFormatter.format(subSummary?.totalMonthlyEstimate ?: Money(0, Currency.TRY)),
        activeSubscriptionCount = subSummary?.activeSubscriptionCount ?: 0,
        upcomingSubscriptions = upcoming,
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
    val debtSummary by viewModel.observeDebtSummary().collectAsStateWithLifecycle(initialValue = null)

    val deadlines = debtSummary?.upcomingDeadlines?.map { d ->
        DebtDeadlineUiItem(
            title = d.title,
            amountFormatted = MoneyFormatter.format(d.amount),
            dueDateFormatted = ReportSummaryFormatter.formatDateDisplay(d.dueDate),
            isReceivable = d.isReceivable,
            isOverdue = d.isOverdue,
        )
    } ?: emptyList()

    DebtSummaryReportScreen(
        totalDebtFormatted = MoneyFormatter.format(debtSummary?.totalDebtRemaining ?: Money(0, Currency.TRY)),
        totalReceivableFormatted = MoneyFormatter.format(debtSummary?.totalReceivableRemaining ?: Money(0, Currency.TRY)),
        netBalanceFormatted = MoneyFormatter.formatDelta(
            debtSummary?.netBalance ?: MoneyDelta(0, Currency.TRY),
            includeSign = true,
        ),
        isNetPositive = (debtSummary?.netBalance?.amountMinor ?: 0L) >= 0L,
        deadlines = deadlines,
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
    val refDate = viewModel.getCurrentReferenceDate()
    val nextMonth = YearMonth.from(refDate.year, refDate.monthNumber).nextMonth()
    val forecastSummary by viewModel.observeForecastReport(lookaheadMonths = 6).collectAsStateWithLifecycle(initialValue = null)

    var showDetail by remember { mutableStateOf(false) }

    val sources = forecastSummary?.contributingSources?.map { s ->
        ForecastSourceUiItem(
            title = s.title,
            amountFormatted = MoneyFormatter.format(s.amount),
            percentageFormatted = "%${s.percentageBasisPoints / 100}",
            ratio = s.percentageBasisPoints / 10000f,
        )
    } ?: listOf(
        ForecastSourceUiItem(
            title = "Sabit ve Düzenli Giderler",
            amountFormatted = MoneyFormatter.format(forecastSummary?.fixedAndPlannedExpenses ?: Money(0, Currency.TRY)),
            percentageFormatted = "%45",
            ratio = 0.45f,
        ),
        ForecastSourceUiItem(
            title = "Ortalama Değişken Harcama",
            amountFormatted = MoneyFormatter.format(forecastSummary?.variableExpensesEstimate ?: Money(0, Currency.TRY)),
            percentageFormatted = "%55",
            ratio = 0.55f,
        ),
    )

    if (showDetail) {
        ForecastDetailReportScreen(
            forecastMonthLabel = "${ReportSummaryFormatter.monthName(nextMonth.monthNumber)} ${nextMonth.year}",
            projectedNetFormatted = MoneyFormatter.formatDelta(
                forecastSummary?.expectedDifference ?: MoneyDelta(0, Currency.TRY),
                includeSign = true,
            ),
            isProjectedNetPositive = (forecastSummary?.expectedDifference?.amountMinor ?: 0L) >= 0L,
            projectionPoints = forecastSummary?.projectionPoints ?: emptyList(),
            dailyAverageFormatted = MoneyFormatter.format(Money((forecastSummary?.variableExpensesEstimate?.amountMinor ?: 0L) / 30, Currency.TRY)),
            activeRecurringCount = forecastSummary?.contributingSources?.size ?: 0,
            onBackClick = { showDetail = false },
            modifier = modifier,
        )
    } else {
        ForecastOverviewReportScreen(
            forecastMonthLabel = "${ReportSummaryFormatter.monthName(nextMonth.monthNumber)} ${nextMonth.year}",
            projectedNetFormatted = MoneyFormatter.formatDelta(
                forecastSummary?.expectedDifference ?: MoneyDelta(0, Currency.TRY),
                includeSign = true,
            ),
            isProjectedNetPositive = (forecastSummary?.expectedDifference?.amountMinor ?: 0L) >= 0L,
            projectedIncomeFormatted = MoneyFormatter.format(forecastSummary?.expectedIncome ?: Money(0, Currency.TRY)),
            fixedExpenseFormatted = MoneyFormatter.format(forecastSummary?.fixedAndPlannedExpenses ?: Money(0, Currency.TRY)),
            variableExpenseFormatted = MoneyFormatter.format(forecastSummary?.variableExpensesEstimate ?: Money(0, Currency.TRY)),
            sources = sources,
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
    val insightItems by viewModel.observeFinancialInsights().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedInsight by remember { mutableStateOf<FinancialInsightUiItem?>(null) }

    val uiItems = insightItems.map { item ->
        FinancialInsightUiItem(
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            amountFormatted = MoneyFormatter.format(item.currentPeriodAmount),
            deltaFormatted = MoneyFormatter.formatDelta(item.delta, includeSign = true),
            isPositive = item.delta.amountMinor >= 0,
            transactionCount = item.supportingTransactionCount,
            explanation = item.explanationText,
        )
    }

    if (selectedInsight != null) {
        InsightDetailReportScreen(
            insight = selectedInsight!!,
            transactions = emptyList(),
            onViewTransactionsClick = {},
            onBackClick = { selectedInsight = null },
            modifier = modifier,
        )
    } else {
        FinancialInsightsReportScreen(
            insights = uiItems,
            onInsightClick = { selectedInsight = it },
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
