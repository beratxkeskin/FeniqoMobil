package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.BudgetPerformanceSummary
import com.feniqo.mobile.domain.model.CalendarDaySpending
import com.feniqo.mobile.domain.model.CashFlowSummary
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryBudgetProgressItem
import com.feniqo.mobile.domain.model.CategoryComparisonItem
import com.feniqo.mobile.domain.model.CategoryDetailSpending
import com.feniqo.mobile.domain.model.CategorySpendingSummary
import com.feniqo.mobile.domain.model.ComparisonHighlightItem
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtDeadlineItem
import com.feniqo.mobile.domain.model.DebtReportSummary
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialInsightItem
import com.feniqo.mobile.domain.model.FinancialRhythmSummary
import com.feniqo.mobile.domain.model.ForecastProjectionPoint
import com.feniqo.mobile.domain.model.ForecastReportSummary
import com.feniqo.mobile.domain.model.ForecastSourceItem
import com.feniqo.mobile.domain.model.InsightType
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.MerchantSpendingSummary
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.MonthlyTrendPoint
import com.feniqo.mobile.domain.model.PeriodComparisonSummary
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.SpendingCalendarSummary
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionCategoryDistributionItem
import com.feniqo.mobile.domain.model.SubscriptionReportSummary
import com.feniqo.mobile.domain.model.SubscriptionUpcomingItem
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.WeeklySpendingPoint
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ObserveDetailedReportOverviewUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase = CalculateReportDateRangeUseCase(),
) {
    operator fun invoke(
        filter: ReportFilter,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<DetailedReportOverviewResult> {
        val currentRange = dateRangeCalculator(filter.periodPreset, filter.customDateRange, referenceDate)
        val currentPeriod = ReportPeriod(currentRange.startDate, currentRange.endDate)

        // 6 aylık trend başlangıç tarihi
        var startYear = referenceDate.year
        var startMonth = referenceDate.monthNumber - 5
        while (startMonth < 1) {
            startMonth += 12
            startYear -= 1
        }
        val trendStart = LocalDate(startYear, startMonth, 1)
        val trendEnd = LocalDate(
            referenceDate.year,
            referenceDate.monthNumber,
            CalculateReportDateRangeUseCase.monthLength(referenceDate.year, referenceDate.monthNumber),
        )
        val trendPeriod = ReportPeriod(trendStart, trendEnd)

        val transactionsFlow = transactionRepository.observeTransactions(
            TransactionFilter(period = trendPeriod, workspaceId = workspaceId)
        )
        val categoriesFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(transactionsFlow, categoriesFlow) { transactions, categories ->
            val currencyTransactions = transactions.filter { it.amount.currency == filter.currency }
            val currentPeriodTxs = currencyTransactions.filter {
                it.transactionDate >= currentPeriod.startDate && it.transactionDate <= currentPeriod.endDate
            }

            // 6 aylık trend
            val trendPoints = mutableListOf<MonthlyTrendPoint>()
            var curY = startYear
            var curM = startMonth
            for (i in 0..5) {
                val ym = YearMonth.from(curY, curM)
                val monthTxs = currencyTransactions.filter {
                    it.transactionDate.year == curY && it.transactionDate.monthNumber == curM
                }
                val mInc = monthTxs.filter { it.type == TransactionType.INCOME }
                    .fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
                val mExp = monthTxs.filter { it.type == TransactionType.EXPENSE }
                    .fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
                trendPoints.add(MonthlyTrendPoint(ym, mInc, mExp, MoneyDelta.between(mInc, mExp)))
                curM++
                if (curM > 12) {
                    curM = 1
                    curY++
                }
            }

            // En yüksek harcama yapılan kategori
            val currentExpenses = currentPeriodTxs.filter { it.type == TransactionType.EXPENSE }
            val topCategory = currentExpenses.groupBy { it.categoryId }
                .map { (catId, txs) ->
                    val sum = txs.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
                    val name = categories.find { it.id == catId }?.name ?: "Kategorisiz"
                    TopCategorySummary(catId, name, sum, txs.size)
                }
                .maxByOrNull { it.amount.amountMinor }

            // Finansal ritim
            val rhythm = calculateFinancialRhythm(currentExpenses, filter.currency)

            // Feniqo içgörü: Geçen ay ile kıyas
            val prevMonthYm = YearMonth.from(referenceDate).previousMonth()
            val prevMonthTxs = currencyTransactions.filter {
                it.type == TransactionType.EXPENSE &&
                    it.transactionDate.year == prevMonthYm.year &&
                    it.transactionDate.monthNumber == prevMonthYm.month
            }
            val currentExpTotal = currentExpenses.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
            val prevExpTotal = prevMonthTxs.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
            val insightText = if (prevExpTotal.amountMinor > 0) {
                val diffMinor = currentExpTotal.amountMinor - prevExpTotal.amountMinor
                val diffMoney = Money(kotlin.math.abs(diffMinor), filter.currency)
                val pct = ((kotlin.math.abs(diffMinor) * 100) / prevExpTotal.amountMinor).toInt()
                if (diffMinor <= 0) {
                    "Giderlerin geçen aya göre %$pct azaldı. Tebrikler! Geçen aya kıyasla ${diffMoney.amountMinor / 100} ${filter.currency.name} daha az harcama yaptınız."
                } else {
                    "Giderlerin geçen aya göre %$pct arttı. Geçen aya kıyasla ${diffMoney.amountMinor / 100} ${filter.currency.name} daha fazla harcama yapıldı."
                }
            } else {
                "Bu dönemde ${currentExpenses.size} harcama kaydı yapıldı."
            }

            DetailedReportOverviewResult(
                trendPoints = trendPoints,
                topCategory = topCategory,
                financialRhythm = rhythm,
                insightText = insightText,
            )
        }
    }

    private fun calculateFinancialRhythm(
        expenses: List<Transaction>,
        currency: Currency,
    ): FinancialRhythmSummary {
        val dayTotals = LongArray(7)
        for (tx in expenses) {
            val dayIdx = (tx.transactionDate.dayOfWeek.ordinal) % 7
            dayTotals[dayIdx] += tx.amount.amountMinor
        }
        val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
        var busiestDayIdx = 0
        var maxDayExpense = 0L
        for (i in 0..6) {
            if (dayTotals[i] > maxDayExpense) {
                maxDayExpense = dayTotals[i]
                busiestDayIdx = i
            }
        }

        val weekTotals = LongArray(5)
        for (tx in expenses) {
            val w = ((tx.transactionDate.dayOfMonth - 1) / 7).coerceIn(0, 4)
            weekTotals[w] += tx.amount.amountMinor
        }
        var lowestWeekIdx = 0
        var minWeekExpense = Long.MAX_VALUE
        for (w in 0..4) {
            if (weekTotals[w] in 1 until minWeekExpense) {
                minWeekExpense = weekTotals[w]
                lowestWeekIdx = w
            }
        }
        if (minWeekExpense == Long.MAX_VALUE) minWeekExpense = 0L

        return FinancialRhythmSummary(
            busiestDayName = dayNames[busiestDayIdx],
            busiestDayExpense = Money(maxDayExpense, currency),
            lowestExpenseWeekNumber = lowestWeekIdx + 1,
            lowestExpenseWeekExpense = Money(minWeekExpense, currency),
        )
    }
}

data class TopCategorySummary(
    val categoryId: EntityId?,
    val categoryName: String,
    val amount: Money,
    val transactionCount: Int,
)

data class DetailedReportOverviewResult(
    val trendPoints: List<MonthlyTrendPoint>,
    val topCategory: TopCategorySummary?,
    val financialRhythm: FinancialRhythmSummary,
    val insightText: String,
)

class ObserveCashFlowUseCase(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(
        monthCount: Int,
        currency: Currency,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<CashFlowSummary> {
        val safeMonthCount = monthCount.coerceIn(3, 12)
        var startYear = referenceDate.year
        var startMonth = referenceDate.monthNumber - (safeMonthCount - 1)
        while (startMonth < 1) {
            startMonth += 12
            startYear -= 1
        }
        val startDate = LocalDate(startYear, startMonth, 1)
        val endDate = LocalDate(
            referenceDate.year,
            referenceDate.monthNumber,
            CalculateReportDateRangeUseCase.monthLength(referenceDate.year, referenceDate.monthNumber),
        )

        return transactionRepository.observeTransactions(
            TransactionFilter(period = ReportPeriod(startDate, endDate), workspaceId = workspaceId)
        ).map { transactions ->
            val currencyTxs = transactions.filter { it.amount.currency == currency }
            val points = mutableListOf<MonthlyTrendPoint>()
            var curY = startYear
            var curM = startMonth
            for (i in 0 until safeMonthCount) {
                val ym = YearMonth.from(curY, curM)
                val mTxs = currencyTxs.filter { it.transactionDate.year == curY && it.transactionDate.monthNumber == curM }
                val inc = mTxs.filter { it.type == TransactionType.INCOME }
                    .fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                val exp = mTxs.filter { it.type == TransactionType.EXPENSE }
                    .fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                points.add(MonthlyTrendPoint(ym, inc, exp, MoneyDelta.between(inc, exp)))
                curM++
                if (curM > 12) {
                    curM = 1
                    curY++
                }
            }

            val totalInc = points.fold(0L) { acc, p -> acc + p.income.amountMinor }
            val totalExp = points.fold(0L) { acc, p -> acc + p.expense.amountMinor }
            val avgInc = Money(if (points.isNotEmpty()) totalInc / points.size else 0L, currency)
            val avgExp = Money(if (points.isNotEmpty()) totalExp / points.size else 0L, currency)

            val bestMonth = points.maxByOrNull { it.net.amountMinor }?.yearMonth
            val weakestMonth = points.minByOrNull { it.net.amountMinor }?.yearMonth

            CashFlowSummary(
                points = points,
                averageIncome = avgInc,
                averageExpense = avgExp,
                bestMonth = bestMonth,
                weakestMonth = weakestMonth,
            )
        }
    }
}

class ObserveCategoryBreakdownUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase = CalculateReportDateRangeUseCase(),
) {
    operator fun invoke(
        filter: ReportFilter,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<CategoryBreakdownResult> {
        val dateRange = dateRangeCalculator(filter.periodPreset, filter.customDateRange, referenceDate)
        val period = ReportPeriod(dateRange.startDate, dateRange.endDate)

        val txFlow = transactionRepository.observeTransactions(
            TransactionFilter(period = period, workspaceId = workspaceId)
        )
        val catFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(txFlow, catFlow) { txs, categories ->
            val currencyTxs = txs.filter { it.amount.currency == filter.currency }
            val expenseTxs = currencyTxs.filter { it.type == TransactionType.EXPENSE }
            val incomeTxs = currencyTxs.filter { it.type == TransactionType.INCOME }

            val totalExpense = expenseTxs.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
            val totalIncome = incomeTxs.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }

            val expenseList = mapToBreakdownItems(expenseTxs, categories, totalExpense.amountMinor, filter.currency)
            val incomeList = mapToBreakdownItems(incomeTxs, categories, totalIncome.amountMinor, filter.currency)

            CategoryBreakdownResult(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                expenseCategories = expenseList,
                incomeCategories = incomeList,
            )
        }
    }

    private fun mapToBreakdownItems(
        txs: List<Transaction>,
        categories: List<Category>,
        totalMinor: Long,
        currency: Currency,
    ): List<CategoryBreakdownItem> {
        return txs.groupBy { it.categoryId }
            .map { (catId, groupTxs) ->
                val amount = groupTxs.fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                val name = categories.find { it.id == catId }?.name ?: "Kategorisiz"
                val pctBasisPoints = if (totalMinor > 0) {
                    ((amount.amountMinor * 10_000) / totalMinor).toInt()
                } else 0
                CategoryBreakdownItem(
                    categoryId = catId,
                    categoryName = name,
                    amount = amount,
                    transactionCount = groupTxs.size,
                    sharePercentageBasisPoints = pctBasisPoints,
                )
            }
            .sortedByDescending { it.amount.amountMinor }
    }
}

data class CategoryBreakdownItem(
    val categoryId: EntityId?,
    val categoryName: String,
    val amount: Money,
    val transactionCount: Int,
    val sharePercentageBasisPoints: Int,
)

data class CategoryBreakdownResult(
    val totalExpense: Money,
    val totalIncome: Money,
    val expenseCategories: List<CategoryBreakdownItem>,
    val incomeCategories: List<CategoryBreakdownItem>,
)

class ObserveSpendingCalendarUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(
        month: YearMonth,
        currency: Currency,
        workspaceId: EntityId?,
    ): Flow<SpendingCalendarSummary> {
        val daysInMonth = CalculateReportDateRangeUseCase.monthLength(month.year, month.month)
        val startDate = LocalDate(month.year, month.month, 1)
        val endDate = LocalDate(month.year, month.month, daysInMonth)
        val period = ReportPeriod(startDate, endDate)

        val txFlow = transactionRepository.observeTransactions(
            TransactionFilter(period = period, workspaceId = workspaceId)
        )
        val catFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(txFlow, catFlow) { txs, categories ->
            val currencyTxs = txs.filter { it.amount.currency == currency && it.type == TransactionType.EXPENSE }
            val daysList = mutableListOf<CalendarDaySpending>()
            var maxDayExpense = 0L
            var highestDay: LocalDate? = null
            var totalExpenseMinor = 0L

            for (day in 1..daysInMonth) {
                val date = LocalDate(month.year, month.month, day)
                val dayTxs = currencyTxs.filter { it.transactionDate == date }
                val dayExpense = dayTxs.fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                totalExpenseMinor += dayExpense.amountMinor

                if (dayExpense.amountMinor > maxDayExpense) {
                    maxDayExpense = dayExpense.amountMinor
                    highestDay = date
                }

                val dominantCat = dayTxs.groupBy { it.categoryId }
                    .maxByOrNull { (_, list) -> list.sumOf { it.amount.amountMinor } }
                val dominantName = categories.find { it.id == dominantCat?.key }?.name
                val dominantAmount = Money(dominantCat?.value?.sumOf { it.amount.amountMinor } ?: 0L, currency)

                daysList.add(
                    CalendarDaySpending(
                        date = date,
                        expense = dayExpense,
                        transactionCount = dayTxs.size,
                        dominantCategoryName = dominantName,
                        dominantCategoryExpense = dominantAmount,
                        transactions = dayTxs,
                    )
                )
            }

            SpendingCalendarSummary(
                month = month,
                days = daysList,
                totalMonthExpense = Money(totalExpenseMinor, currency),
                highestExpenseDay = highestDay,
            )
        }
    }
}

class ObserveBudgetPerformanceReportUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(
        month: YearMonth,
        currency: Currency,
        workspaceId: EntityId?,
    ): Flow<BudgetPerformanceSummary> {
        val daysInMonth = CalculateReportDateRangeUseCase.monthLength(month.year, month.month)
        val startDate = LocalDate(month.year, month.month, 1)
        val endDate = LocalDate(month.year, month.month, daysInMonth)
        val period = ReportPeriod(startDate, endDate)

        val budgetFlow = budgetRepository.observeBudgets(month, workspaceId)
        val txFlow = transactionRepository.observeTransactions(
            TransactionFilter(period = period, type = TransactionType.EXPENSE, workspaceId = workspaceId)
        )
        val catFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(budgetFlow, txFlow, catFlow) { budgets, txs, categories ->
            val currencyBudgets = budgets.filter { it.limit.currency == currency }
            val currencyTxs = txs.filter { it.amount.currency == currency }

            var totalBudgetMinor = 0L
            var totalSpentMinor = 0L
            var onTrackCount = 0
            var exceededCount = 0
            val progressList = mutableListOf<CategoryBudgetProgressItem>()

            for (b in currencyBudgets) {
                totalBudgetMinor += b.limit.amountMinor
                val catTxs = currencyTxs.filter { it.categoryId == b.categoryId }
                val spent = catTxs.fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                totalSpentMinor += spent.amountMinor
                val isExceeded = spent.amountMinor > b.limit.amountMinor
                if (isExceeded) exceededCount++ else onTrackCount++
                val pctBasisPoints = if (b.limit.amountMinor > 0) {
                    ((spent.amountMinor * 10_000) / b.limit.amountMinor).toInt()
                } else 0

                val catName = categories.find { it.id == b.categoryId }?.name ?: "Bütçe"
                progressList.add(
                    CategoryBudgetProgressItem(
                        budgetId = b.id,
                        categoryId = b.categoryId,
                        categoryName = catName,
                        budgetAmount = b.limit,
                        spentAmount = spent,
                        usagePercentageBasisPoints = pctBasisPoints,
                        isExceeded = isExceeded,
                    )
                )
            }

            val totalBudgetMoney = Money(totalBudgetMinor, currency)
            val totalSpentMoney = Money(totalSpentMinor, currency)
            val remainingMinor = (totalBudgetMinor - totalSpentMinor).coerceAtLeast(0L)
            val overallUsage = if (totalBudgetMinor > 0) {
                ((totalSpentMinor * 10_000) / totalBudgetMinor).toInt()
            } else 0

            BudgetPerformanceSummary(
                totalBudget = totalBudgetMoney,
                totalSpent = totalSpentMoney,
                totalRemaining = Money(remainingMinor, currency),
                usagePercentageBasisPoints = overallUsage,
                budgetsOnTrackCount = onTrackCount,
                budgetsExceededCount = exceededCount,
                categoryProgressList = progressList,
            )
        }
    }
}

class ObserveSubscriptionSummaryReportUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(currency: Currency, workspaceId: EntityId? = null): Flow<SubscriptionReportSummary> {
        val subFlow = subscriptionRepository.observeSubscriptions()
        val catFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(subFlow, catFlow) { subscriptions, categories ->
            val active = subscriptions.filter { it.amount.currency == currency && it.isActive }
            var monthlyTotalMinor = 0L
            val upcoming = mutableListOf<SubscriptionUpcomingItem>()
            val categoryMap = mutableMapOf<String, Long>()

            for (sub in active) {
                val costMinor = sub.amount.amountMinor
                monthlyTotalMinor += costMinor
                upcoming.add(
                    SubscriptionUpcomingItem(
                        subscriptionId = sub.id,
                        name = sub.name,
                        amount = sub.amount,
                        renewalDate = sub.nextRenewalDate,
                    )
                )
                val catName = categories.find { it.id == sub.categoryId }?.name ?: "Diğer"
                categoryMap[catName] = (categoryMap[catName] ?: 0L) + costMinor
            }

            upcoming.sortBy { it.renewalDate }

            val distribution = categoryMap.map { (cat, minor) ->
                val pct = if (monthlyTotalMinor > 0) ((minor * 10_000) / monthlyTotalMinor).toInt() else 0
                SubscriptionCategoryDistributionItem(cat, Money(minor, currency), pct)
            }.sortedByDescending { it.amount.amountMinor }

            SubscriptionReportSummary(
                totalMonthlyEstimate = Money(monthlyTotalMinor, currency),
                activeSubscriptionCount = active.size,
                past6MonthsMiniTrend = listOf(
                    Money(monthlyTotalMinor, currency),
                    Money(monthlyTotalMinor, currency),
                    Money(monthlyTotalMinor, currency),
                ),
                upcomingPayments = upcoming,
                categoryDistribution = distribution,
            )
        }
    }
}

class ObserveDebtSummaryReportUseCase(
    private val debtRepository: DebtRepository,
) {
    operator fun invoke(currency: Currency, referenceDate: LocalDate): Flow<DebtReportSummary> {
        return debtRepository.observeDebts().map { debts ->
            val currencyDebts = debts.filter { it.amount.currency == currency && it.status == com.feniqo.mobile.domain.model.DebtStatus.OPEN }
            var totalDebtMinor = 0L
            var totalReceivableMinor = 0L
            var debtCount = 0
            var receivableCount = 0
            val deadlines = mutableListOf<DebtDeadlineItem>()
            var upcoming14Days = 0

            val limit14Days = LocalDate(
                referenceDate.year,
                referenceDate.monthNumber,
                (referenceDate.dayOfMonth + 14).coerceAtMost(28),
            )

            for (d in currencyDebts) {
                val remaining = d.amount
                if (d.type == com.feniqo.mobile.domain.model.DebtType.DEBT) {
                    totalDebtMinor += remaining.amountMinor
                    debtCount++
                    deadlines.add(
                        DebtDeadlineItem(
                            debtId = d.id,
                            title = d.title,
                            amount = remaining,
                            dueDate = d.dueDate,
                            isReceivable = false,
                            isOverdue = d.dueDate < referenceDate,
                        )
                    )
                } else {
                    totalReceivableMinor += remaining.amountMinor
                    receivableCount++
                    deadlines.add(
                        DebtDeadlineItem(
                            debtId = d.id,
                            title = d.title,
                            amount = remaining,
                            dueDate = d.dueDate,
                            isReceivable = true,
                            isOverdue = d.dueDate < referenceDate,
                        )
                    )
                }
                if (d.dueDate in referenceDate..limit14Days) {
                    upcoming14Days++
                }
            }

            val totalDebt = Money(totalDebtMinor, currency)
            val totalRec = Money(totalReceivableMinor, currency)
            val net = MoneyDelta.between(totalRec, totalDebt)

            deadlines.sortBy { it.dueDate }

            DebtReportSummary(
                totalDebtRemaining = totalDebt,
                debtCount = debtCount,
                totalReceivableRemaining = totalRec,
                receivableCount = receivableCount,
                netBalance = net,
                upcomingDeadlines = deadlines,
                upcomingCount14Days = upcoming14Days,
            )
        }
    }
}

class ObserveForecastReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
) {
    operator fun invoke(
        currency: Currency,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
        monthCount: Int = 6,
    ): Flow<ForecastReportSummary> {
        val nextMonth = YearMonth.from(referenceDate).nextMonth()
        val txFlow = transactionRepository.observeTransactions(TransactionFilter(workspaceId = workspaceId))
        val subFlow = subscriptionRepository.observeSubscriptions()
        val recurFlow = recurringTransactionRepository.observeRecurringTransactions()

        return combine(txFlow, subFlow, recurFlow) { txs, subs, recurs ->
            val currencyTxs = txs.filter { it.amount.currency == currency }
            val currencySubs = subs.filter { it.amount.currency == currency && it.isActive }
            val currencyRecurs = recurs.filter { it.amount.currency == currency && it.isActive }

            // Sabit ve planlı giderler (Abonelikler + Yinelenen giderler)
            val subExpensesMinor = currencySubs.sumOf { it.amount.amountMinor }
            val recurExpensesMinor = currencyRecurs.filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount.amountMinor }
            val fixedExpensesMinor = subExpensesMinor + recurExpensesMinor

            // Beklenen gelir (Yinelenen gelirler veya maaş ortalaması)
            val recurIncomeMinor = currencyRecurs.filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount.amountMinor }
            val salaryIncomeMinor = currencyTxs
                .filter { it.type == TransactionType.INCOME }
                .takeLast(3)
                .map { it.amount.amountMinor }
                .average()
                .toLong()
            val expectedIncomeMinor = if (recurIncomeMinor > 0) recurIncomeMinor else salaryIncomeMinor.coerceAtLeast(0L)

            // Değişken harcama ortalaması (son 3 ayın ortalama harcaması)
            val pastExpenses = currencyTxs.filter { it.type == TransactionType.EXPENSE }
            val variableAverageMinor = if (pastExpenses.isNotEmpty()) {
                pastExpenses.sumOf { it.amount.amountMinor } / 3
            } else 0L

            val expectedDifferenceMinor = expectedIncomeMinor - (fixedExpensesMinor + variableAverageMinor)
            val expectedIncomeMoney = Money(expectedIncomeMinor, currency)
            val fixedMoney = Money(fixedExpensesMinor, currency)
            val variableMoney = Money(variableAverageMinor, currency)
            val expectedDiffDelta = MoneyDelta(expectedDifferenceMinor, currency)

            // Projeksiyon çizgisi (Son 3 ay gerçekleşen, Gelecek 3 ay tahmin)
            val points = mutableListOf<ForecastProjectionPoint>()
            var ym = YearMonth.from(referenceDate).previousMonth().previousMonth()
            for (i in 0..2) {
                val mTxs = currencyTxs.filter { it.transactionDate.year == ym.year && it.transactionDate.monthNumber == ym.month }
                val inc = mTxs.filter { it.type == TransactionType.INCOME }.fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                val exp = mTxs.filter { it.type == TransactionType.EXPENSE }.fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                points.add(ForecastProjectionPoint(ym, inc, exp, isForecast = false))
                ym = ym.nextMonth()
            }
            for (i in 0..2) {
                points.add(ForecastProjectionPoint(ym, expectedIncomeMoney, fixedMoney + variableMoney, isForecast = true))
                ym = ym.nextMonth()
            }

            val totalAll = (expectedIncomeMinor + fixedExpensesMinor + variableAverageMinor).coerceAtLeast(1L)
            val sources = listOf(
                ForecastSourceItem("Maaş ve düzenli gelirler", expectedIncomeMoney, ((expectedIncomeMinor * 10_000) / totalAll).toInt()),
                ForecastSourceItem("Abonelik ve faturalar", Money(subExpensesMinor, currency), ((subExpensesMinor * 10_000) / totalAll).toInt()),
                ForecastSourceItem("Yinelenen işlemler", Money(recurExpensesMinor, currency), ((recurExpensesMinor * 10_000) / totalAll).toInt()),
                ForecastSourceItem("Değişken harcama ortalaması", variableMoney, ((variableAverageMinor * 10_000) / totalAll).toInt()),
            )

            ForecastReportSummary(
                forecastMonth = nextMonth,
                expectedDifference = expectedDiffDelta,
                expectedIncome = expectedIncomeMoney,
                fixedAndPlannedExpenses = fixedMoney,
                variableExpensesEstimate = variableMoney,
                projectionPoints = points,
                contributingSources = sources,
                assumptionsText = "Tahminler, son aylarınızdaki işlem geçmişiniz ve kayıtlı tekrar eden ödemeleriniz kullanılarak oluşturulur. Döviz kurları için tahmin yapılmaz, mevcut kur verileri kullanılır.",
            )
        }
    }
}

class ObserveFinancialInsightsUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(
        currentPeriod: ReportPeriod,
        currency: Currency,
        workspaceId: EntityId?,
    ): Flow<List<FinancialInsightItem>> {
        // Önceki dönem (aynı uzunlukta)
        val lengthDays = currentPeriod.endDate.dayOfMonth - currentPeriod.startDate.dayOfMonth + 1
        val prevStart = LocalDate(
            currentPeriod.startDate.year,
            if (currentPeriod.startDate.monthNumber > 1) currentPeriod.startDate.monthNumber - 1 else 12,
            1,
        )
        val prevEnd = LocalDate(
            prevStart.year,
            prevStart.monthNumber,
            lengthDays.coerceAtMost(CalculateReportDateRangeUseCase.monthLength(prevStart.year, prevStart.monthNumber)),
        )
        val prevPeriod = ReportPeriod(prevStart, prevEnd)

        val txFlow = transactionRepository.observeTransactions(
            TransactionFilter(
                period = ReportPeriod(prevPeriod.startDate, currentPeriod.endDate),
                workspaceId = workspaceId,
            )
        )
        val catFlow = categoryRepository.observeCategories(workspaceId = workspaceId)

        return combine(txFlow, catFlow) { txs, categories ->
            val currencyTxs = txs.filter { it.amount.currency == currency }
            val currentTxs = currencyTxs.filter { it.transactionDate >= currentPeriod.startDate && it.transactionDate <= currentPeriod.endDate }
            val prevTxs = currencyTxs.filter { it.transactionDate >= prevPeriod.startDate && it.transactionDate <= prevPeriod.endDate }

            val insights = mutableListOf<FinancialInsightItem>()

            // 1. Tasarruf oranı içgörüsü
            val curInc = currentTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.amountMinor }
            val curExp = currentTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.amountMinor }
            val prevInc = prevTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.amountMinor }
            val prevExp = prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.amountMinor }

            val curSavings = if (curInc > 0) ((curInc - curExp) * 100) / curInc else 0
            val prevSavings = if (prevInc > 0) ((prevInc - prevExp) * 100) / prevInc else 0
            val savingsDiff = curSavings - prevSavings

            if (savingsDiff >= 0) {
                insights.add(
                    FinancialInsightItem(
                        id = "savings_rate",
                        type = InsightType.SAVINGS_RATE_INCREASE,
                        title = "Tasarruf oranın yükseldi",
                        subtitle = "Geçen aya göre daha yüksek bir tasarruf oranı yakaladınız.",
                        currentPeriodAmount = Money(curInc - curExp, currency),
                        previousPeriodAmount = Money(prevInc - prevExp, currency),
                        delta = MoneyDelta((curInc - curExp) - (prevInc - prevExp), currency),
                        supportingTransactionCount = currentTxs.size,
                        weeklyBreakdown = emptyList(),
                        contributingTransactions = currentTxs.take(5),
                        explanationText = "Seçilen dönem ile önceki dönem aynı uzunluktadır. Gelirlerinizden kalan tasarruf miktarınız hesaplanmıştır.",
                    )
                )
            }

            // 2. Kategori harcama değişimleri (azalış ve artış)
            val curExpByCat = currentTxs.filter { it.type == TransactionType.EXPENSE }.groupBy { it.categoryId }
            val prevExpByCat = prevTxs.filter { it.type == TransactionType.EXPENSE }.groupBy { it.categoryId }

            for ((catId, curList) in curExpByCat) {
                val prevList = prevExpByCat[catId] ?: emptyList()
                val curTotal = curList.sumOf { it.amount.amountMinor }
                val prevTotal = prevList.sumOf { it.amount.amountMinor }
                val catName = categories.find { it.id == catId }?.name ?: "Kategori"

                if (prevTotal > 0 && curTotal < prevTotal) {
                    val diff = prevTotal - curTotal
                    val pct = ((diff * 100) / prevTotal).toInt()
                    insights.add(
                        FinancialInsightItem(
                            id = "cat_dec_${catId?.value}",
                            type = InsightType.CATEGORY_DECREASE,
                            title = "$catName harcamaları %$pct azaldı",
                            subtitle = "$catName kategorisindeki harcamalarınız geçen aya göre %$pct daha düşük.",
                            currentPeriodAmount = Money(curTotal, currency),
                            previousPeriodAmount = Money(prevTotal, currency),
                            delta = MoneyDelta(-diff, currency),
                            supportingTransactionCount = curList.size,
                            weeklyBreakdown = emptyList(),
                            contributingTransactions = curList,
                            explanationText = "Seçilen dönemdeki tamamlanmış $catName harcamaları dikkate alınır.",
                        )
                    )
                } else if (prevTotal > 0 && curTotal > prevTotal) {
                    val diff = curTotal - prevTotal
                    insights.add(
                        FinancialInsightItem(
                            id = "cat_inc_${catId?.value}",
                            type = InsightType.CATEGORY_INCREASE,
                            title = "$catName giderleri arttı",
                            subtitle = "Geçen aya göre $catName harcamalarınız ${diff / 100} ${currency.name} daha yüksek.",
                            currentPeriodAmount = Money(curTotal, currency),
                            previousPeriodAmount = Money(prevTotal, currency),
                            delta = MoneyDelta(diff, currency),
                            supportingTransactionCount = curList.size,
                            weeklyBreakdown = emptyList(),
                            contributingTransactions = curList,
                            explanationText = "Seçilen dönem ile önceki dönem karşılaştırılmıştır.",
                        )
                    )
                }
            }

            insights
        }
    }
}
