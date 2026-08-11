package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyScore
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.Transaction

data class MoneyScoreInput(
    val income: Money,
    val expense: Money,
    val budgets: List<Budget>,
    val transactions: List<Transaction>,
    val debts: List<Debt>,
    val goals: List<Goal>,
    val today: LocalDate,
)

/** Web skorunun ağırlıklarını koruyan, platformdan bağımsız ve deterministik hesaplayıcı. */
class CalculateMoneyScoreUseCase(
    private val budgetProgressCalculator: CalculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(),
) {
    operator fun invoke(input: MoneyScoreInput): MoneyScore {
        require(input.income.currency == input.expense.currency) {
            "MoneyScore gelir ve gideri aynı para biriminde olmalıdır."
        }

        val savingsScore = calculateSavingsScore(input.income, input.expense)
        val budgetScore = calculateBudgetScore(input.budgets, input.transactions)
        val debtScore = calculateDebtScore(input.debts, input.today)
        val goalScore = calculateGoalScore(input.goals)
        val total = savingsScore + budgetScore + debtScore + goalScore
        val level = when {
            total < 50 -> MoneyScoreLevel.CRITICAL
            total < 80 -> MoneyScoreLevel.HEALTHY
            else -> MoneyScoreLevel.EXCELLENT
        }
        return MoneyScore(total, savingsScore, budgetScore, debtScore, goalScore, level)
    }

    private fun calculateSavingsScore(income: Money, expense: Money): Int {
        if (income.amountMinor == 0L) return if (expense.amountMinor == 0L) 15 else 0
        val savingsRate = rateBasisPoints(
            numerator = income.amountMinor - expense.amountMinor,
            denominator = income.amountMinor,
        )
        return when {
            savingsRate >= 2_500 -> 30
            savingsRate <= 0 -> 0
            else -> (savingsRate * 30 / 2_500).coerceIn(0, 30)
        }
    }

    private fun calculateBudgetScore(budgets: List<Budget>, transactions: List<Transaction>): Int {
        if (budgets.isEmpty()) return 15
        val totalScore = budgets.sumOf { budget ->
            val usage = budgetProgressCalculator(budget, transactions).usageRate.value
            when {
                usage <= 10_000 -> 30
                usage >= 20_000 -> 0
                else -> 30 - ((usage - 10_000) * 30 / 10_000)
            }
        }
        return (totalScore / budgets.size).coerceIn(0, 30)
    }

    private fun calculateDebtScore(debts: List<Debt>, today: LocalDate): Int {
        val activeDebts = debts.filter { it.type == DebtType.DEBT }
        if (activeDebts.isEmpty()) return 20
        val totalWeight = activeDebts.sumOf { debt ->
            when {
                debt.status == DebtStatus.SETTLED -> 10_000
                debt.dueDate < today -> 0
                else -> 7_500
            }
        }
        return (totalWeight / activeDebts.size * 20 / 10_000).coerceIn(0, 20)
    }

    private fun calculateGoalScore(goals: List<Goal>): Int {
        if (goals.isEmpty()) return 10
        val totalProgress = goals.sumOf { goal ->
            rateBasisPoints(goal.currentAmount.amountMinor, goal.targetAmount.amountMinor).coerceIn(0, 10_000)
        }
        val averageProgress = totalProgress / goals.size
        return (10 + averageProgress * 10 / 10_000).coerceIn(10, 20)
    }
}
