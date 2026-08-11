package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType

enum class BudgetHealth {
    SAFE,
    WARNING,
    EXCEEDED,
}

data class BudgetProgress(
    val budget: Budget,
    val spent: Money,
    val remaining: MoneyDelta,
    val usageRate: RateBasisPoints,
    val health: BudgetHealth,
)

class CalculateBudgetProgressUseCase {
    operator fun invoke(budget: Budget, transactions: List<Transaction>): BudgetProgress {
        val matchingExpenses = transactions.filter { transaction ->
            transaction.type == TransactionType.EXPENSE &&
                transaction.categoryId == budget.categoryId &&
                transaction.transactionDate.toString().startsWith(budget.month.value)
        }
        require(matchingExpenses.all { it.amount.currency == budget.limit.currency }) {
            "Bütçe ve harcamalar aynı para biriminde olmalıdır."
        }
        val spent = matchingExpenses.fold(Money.zero(budget.limit.currency)) { total, transaction ->
            total + transaction.amount
        }
        val usageBasisPoints = if (budget.limit.amountMinor == 0L) {
            0
        } else {
            rateBasisPoints(spent.amountMinor, budget.limit.amountMinor).coerceAtLeast(0)
        }
        val health = when {
            usageBasisPoints >= BASIS_POINT_SCALE -> BudgetHealth.EXCEEDED
            usageBasisPoints >= WARNING_THRESHOLD -> BudgetHealth.WARNING
            else -> BudgetHealth.SAFE
        }
        return BudgetProgress(
            budget = budget,
            spent = spent,
            remaining = MoneyDelta.between(budget.limit, spent),
            usageRate = RateBasisPoints(usageBasisPoints),
            health = health,
        )
    }

    private companion object {
        const val WARNING_THRESHOLD = 8_000
    }
}
