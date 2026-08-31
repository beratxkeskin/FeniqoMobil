package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.coroutines.flow.Flow

data class TransactionFilter(
    val period: ReportPeriod? = null,
    val type: TransactionType? = null,
    val categoryId: EntityId? = null,
    val paymentMethod: PaymentMethod? = null,
    val workspaceId: EntityId? = null,
    val query: String? = null,
)

interface TransactionRepository {
    fun observeTransactions(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>>

    fun observeTransaction(id: EntityId): Flow<Transaction?>

    fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>>

    suspend fun create(transaction: Transaction): RepositoryResult<EntityId>

    suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId>

    suspend fun update(transaction: Transaction): RepositoryResult<Unit>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>

    suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit>
}

interface CategoryRepository {
    fun observeCategories(
        type: TransactionType? = null,
        workspaceId: EntityId? = null,
    ): Flow<List<Category>>

    fun observeCategory(id: EntityId): Flow<Category?>

    fun observeCategoriesForHistoryLookup(
        workspaceId: EntityId? = null,
    ): Flow<List<Category>>

    suspend fun create(category: Category): RepositoryResult<EntityId>

    suspend fun update(category: Category): RepositoryResult<Unit>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>
}

interface BudgetRepository {
    fun observeBudgets(month: YearMonth, workspaceId: EntityId? = null): Flow<List<Budget>>

    fun observeBudget(id: EntityId): Flow<Budget?>

    suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId>

    suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>

    suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult>
}
