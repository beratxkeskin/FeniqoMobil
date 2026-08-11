package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategorySpendingSummary
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DashboardSummary
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.MoneyScore
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalculateDashboardSummaryUseCase {
    operator fun invoke(
        month: YearMonth,
        currency: Currency,
        transactions: List<Transaction>,
        moneyScore: MoneyScore? = null,
    ): DashboardSummary {
        val monthly = transactions.filter { it.transactionDate.toString().startsWith(month.value) }
        require(monthly.all { it.amount.currency == currency }) {
            "Dashboard işlemleri seçilen para biriminde olmalıdır."
        }
        val income = sum(monthly.filter { it.type == TransactionType.INCOME }, currency)
        val expenses = monthly.filter { it.type == TransactionType.EXPENSE }
        val expense = sum(expenses, currency)
        val savingsRate = if (income.amountMinor == 0L) {
            0
        } else {
            rateBasisPoints(
                numerator = income.amountMinor - expense.amountMinor,
                denominator = income.amountMinor,
            )
        }
        val topCategory = expenses
            .groupBy(Transaction::categoryId)
            .map { (categoryId, items) ->
                CategorySpendingSummary(categoryId, sum(items, currency), items.size)
            }
            .maxByOrNull { it.amount.amountMinor }

        return DashboardSummary(
            month = month,
            income = income,
            expense = expense,
            balance = MoneyDelta.between(income, expense),
            savingsRate = RateBasisPoints(savingsRate),
            topExpenseCategory = topCategory,
            recentTransactionIds = monthly.sortedByDescending(Transaction::createdAt).take(5).map(Transaction::id),
            moneyScore = moneyScore,
        )
    }

    private fun sum(transactions: List<Transaction>, currency: Currency): Money =
        transactions.fold(Money.zero(currency)) { total, transaction -> total + transaction.amount }
}

class ObserveDashboardSummaryUseCase(
    private val transactionRepository: TransactionRepository,
    private val calculator: CalculateDashboardSummaryUseCase = CalculateDashboardSummaryUseCase(),
) {
    operator fun invoke(
        month: YearMonth,
        currency: Currency,
        workspaceId: EntityId? = null,
    ): Flow<DashboardSummary> = transactionRepository
        .observeTransactions(TransactionFilter(workspaceId = workspaceId))
        .map { transactions -> calculator(month, currency, transactions) }
}
