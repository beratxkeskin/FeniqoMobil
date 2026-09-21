package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategorySpendingSummary
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialReport
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.MultiCurrencyReportSummary
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.ReportAvailability
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportSyncStatus
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CalculateReportDateRangeUseCase {
    operator fun invoke(
        preset: ReportPeriodPreset,
        customRange: ReportDateRange? = null,
        referenceDate: LocalDate,
    ): ReportDateRange {
        return when (preset) {
            ReportPeriodPreset.THIS_MONTH -> {
                val year = referenceDate.year
                val month = referenceDate.monthNumber
                val start = LocalDate(year, month, 1)
                val end = LocalDate(year, month, monthLength(year, month))
                ReportDateRange(start, end)
            }
            ReportPeriodPreset.LAST_MONTH -> {
                var year = referenceDate.year
                var month = referenceDate.monthNumber - 1
                if (month < 1) {
                    month = 12
                    year -= 1
                }
                val start = LocalDate(year, month, 1)
                val end = LocalDate(year, month, monthLength(year, month))
                ReportDateRange(start, end)
            }
            ReportPeriodPreset.LAST_3_MONTHS -> {
                var startYear = referenceDate.year
                var startMonth = referenceDate.monthNumber - 2
                while (startMonth < 1) {
                    startMonth += 12
                    startYear -= 1
                }
                val start = LocalDate(startYear, startMonth, 1)
                val end = LocalDate(referenceDate.year, referenceDate.monthNumber, monthLength(referenceDate.year, referenceDate.monthNumber))
                ReportDateRange(start, end)
            }
            ReportPeriodPreset.CUSTOM -> {
                customRange ?: invoke(ReportPeriodPreset.THIS_MONTH, null, referenceDate)
            }
        }
    }

    companion object {
        fun monthLength(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }

        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}

class ObserveFilteredReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase = CalculateReportDateRangeUseCase(),
) {
    operator fun invoke(
        filter: ReportFilter,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<FinancialReport> {
        val dateRange = dateRangeCalculator(filter.periodPreset, filter.customDateRange, referenceDate)
        val period = ReportPeriod(dateRange.startDate, dateRange.endDate)
        val queryFilter = TransactionFilter(
            period = period,
            type = when (filter.typeFilter) {
                ReportTypeFilter.ALL -> null
                ReportTypeFilter.INCOME -> TransactionType.INCOME
                ReportTypeFilter.EXPENSE -> TransactionType.EXPENSE
            },
            categoryId = filter.categoryId,
            workspaceId = workspaceId,
        )
        return transactionRepository.observeTransactions(queryFilter).map { transactions ->
            val currencyTransactions = transactions.filter { it.amount.currency == filter.currency }
            val incomeTransactions = currencyTransactions.filter { it.type == TransactionType.INCOME }
            val expenseTransactions = currencyTransactions.filter { it.type == TransactionType.EXPENSE }

            val income = incomeTransactions.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
            val expense = expenseTransactions.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount }
            val net = MoneyDelta.between(income, expense)
            val savingsRate = if (income.amountMinor == 0L) {
                0
            } else {
                rateBasisPoints(income.amountMinor - expense.amountMinor, income.amountMinor)
            }

            val categorySpending = expenseTransactions
                .groupBy { it.categoryId }
                .map { (catId, txs) ->
                    CategorySpendingSummary(
                        categoryId = catId,
                        amount = txs.fold(Money.zero(filter.currency)) { acc, t -> acc + t.amount },
                        transactionCount = txs.size,
                    )
                }
                .sortedByDescending { it.amount.amountMinor }

            FinancialReport(
                period = period,
                income = income,
                expense = expense,
                net = net,
                savingsRate = RateBasisPoints(savingsRate),
                spendingByCategory = categorySpending,
                transactionCount = currencyTransactions.size,
            )
        }
    }
}

class ObserveMultiCurrencyReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase = CalculateReportDateRangeUseCase(),
) {
    operator fun invoke(
        filter: ReportFilter,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<List<MultiCurrencyReportSummary>> {
        val dateRange = dateRangeCalculator(filter.periodPreset, filter.customDateRange, referenceDate)
        val queryFilter = TransactionFilter(
            period = ReportPeriod(dateRange.startDate, dateRange.endDate),
            type = when (filter.typeFilter) {
                ReportTypeFilter.ALL -> null
                ReportTypeFilter.INCOME -> TransactionType.INCOME
                ReportTypeFilter.EXPENSE -> TransactionType.EXPENSE
            },
            categoryId = filter.categoryId,
            workspaceId = workspaceId,
        )
        return transactionRepository.observeTransactions(queryFilter).map { transactions ->
            val byCurrency = transactions.groupBy { it.amount.currency }
            byCurrency.map { (currency, txList) ->
                val income = txList.filter { it.type == TransactionType.INCOME }
                    .fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                val expense = txList.filter { it.type == TransactionType.EXPENSE }
                    .fold(Money.zero(currency)) { acc, t -> acc + t.amount }
                MultiCurrencyReportSummary(
                    currency = currency,
                    income = income,
                    expense = expense,
                    net = MoneyDelta.between(income, expense),
                    transactionCount = txList.size,
                )
            }.sortedBy { it.currency.ordinal }
        }
    }
}

class ObserveReportAvailabilityUseCase(
    private val transactionRepository: TransactionRepository,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase = CalculateReportDateRangeUseCase(),
) {
    operator fun invoke(
        filter: ReportFilter,
        workspaceId: EntityId?,
        referenceDate: LocalDate,
    ): Flow<ReportAvailability> {
        val allTransactionsFlow = transactionRepository.observeTransactions(
            TransactionFilter(workspaceId = workspaceId)
        )
        val dateRange = dateRangeCalculator(filter.periodPreset, filter.customDateRange, referenceDate)
        val filteredTransactionsFlow = transactionRepository.observeTransactions(
            TransactionFilter(
                period = ReportPeriod(dateRange.startDate, dateRange.endDate),
                type = when (filter.typeFilter) {
                    ReportTypeFilter.ALL -> null
                    ReportTypeFilter.INCOME -> TransactionType.INCOME
                    ReportTypeFilter.EXPENSE -> TransactionType.EXPENSE
                },
                categoryId = filter.categoryId,
                workspaceId = workspaceId,
            )
        )
        return combine(allTransactionsFlow, filteredTransactionsFlow) { all, filtered ->
            val filteredCurrencyCount = filtered.count { it.amount.currency == filter.currency }
            ReportAvailability(
                totalReportableTransactionCount = all.size,
                filteredTransactionCount = filteredCurrencyCount,
            )
        }
    }
}

class ObserveReportSyncStatusUseCase(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(): Flow<ReportSyncStatus> {
        return syncRepository.observeOverview().map { overview ->
            ReportSyncStatus(
                isOffline = overview.phase == SyncPhase.OFFLINE,
                pendingOperationCount = overview.pendingOperationCount,
            )
        }
    }
}
