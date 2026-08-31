package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.DeleteBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

data class BudgetProgressItem(
    val progress: BudgetProgress,
    val category: Category?,
    val excludedDifferentCurrencyTransactionCount: Int,
) {
    init {
        require(excludedDifferentCurrencyTransactionCount >= 0) {
            "Dışlanan farklı para birimli işlem sayısı negatif olamaz: $excludedDifferentCurrencyTransactionCount"
        }
    }
}

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

class CreateBudgetUseCase(
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(command: CreateBudgetCommand): RepositoryResult<EntityId> {
        val category = categoryRepository.observeCategory(command.categoryId).first()
            ?: return RepositoryResult.Failure(AppError.Validation("budget_category_not_found"))

        if (category.type != TransactionType.EXPENSE) {
            return RepositoryResult.Failure(AppError.Validation("budget_category_must_be_expense"))
        }

        return budgetRepository.create(command)
    }
}

class UpdateBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(command: UpdateBudgetCommand): RepositoryResult<Unit> {
        return budgetRepository.update(command)
    }
}

class DeleteBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(command: DeleteBudgetCommand): RepositoryResult<Unit> {
        return budgetRepository.softDelete(command.id)
    }
}

/**
 * Verilen kimliğe sahip tekil bütçeyi Room SSOT üzerinden sürekli gözlemleyen use-case'dir.
 */
class ObserveBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    operator fun invoke(id: EntityId): Flow<Budget?> = budgetRepository.observeBudget(id)
}

class ObserveBudgetsWithProgressUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val calculator: CalculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(),
) {
    operator fun invoke(
        month: YearMonth,
        workspaceId: EntityId? = null,
    ): Flow<List<BudgetProgressItem>> {
        return combine(
            budgetRepository.observeBudgets(month, workspaceId),
            transactionRepository.observeTransactions(TransactionFilter(workspaceId = workspaceId)),
            categoryRepository.observeCategoriesForHistoryLookup(workspaceId),
        ) { budgets, transactions, categories ->
            val categoryMap = categories.associateBy { it.id }
            val monthPrefix = month.value

            budgets.map { budget ->
                val matchingExpenses = transactions.filter { tx ->
                    tx.type == TransactionType.EXPENSE &&
                        tx.categoryId == budget.categoryId &&
                        tx.transactionDate.toString().startsWith(monthPrefix)
                }

                val sameCurrency = matchingExpenses.filter { it.amount.currency == budget.limit.currency }
                val excludedCount = matchingExpenses.size - sameCurrency.size
                val progress = calculator(budget, sameCurrency)

                BudgetProgressItem(
                    progress = progress,
                    category = categoryMap[budget.categoryId],
                    excludedDifferentCurrencyTransactionCount = excludedCount,
                )
            }
        }
    }
}

class ObserveBudgetAlertsUseCase(
    private val observeBudgetsWithProgressUseCase: ObserveBudgetsWithProgressUseCase,
) {
    operator fun invoke(
        month: YearMonth,
        workspaceId: EntityId? = null,
    ): Flow<List<BudgetProgressItem>> {
        return observeBudgetsWithProgressUseCase(month, workspaceId).map { items ->
            items
                .filter { it.progress.health != BudgetHealth.SAFE }
                .sortedWith(
                    compareByDescending<BudgetProgressItem> { it.progress.health == BudgetHealth.EXCEEDED }
                        .thenByDescending { it.progress.usageRate.value }
                        .thenBy { it.progress.budget.id.value },
                )
        }
    }
}

class CopyBudgetsUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> =
        budgetRepository.copyBudgets(command)
}
