package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface RecurringTransactionRepository {
    fun observeRecurringTransactions(): Flow<List<RecurringTransaction>>

    fun observeRecurringTransaction(id: EntityId): Flow<RecurringTransaction?>

    suspend fun create(command: CreateRecurringTransactionCommand): RepositoryResult<EntityId>

    suspend fun update(command: UpdateRecurringTransactionCommand): RepositoryResult<Unit>

    suspend fun setActive(command: SetRecurringTransactionActiveCommand): RepositoryResult<Unit>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>

    suspend fun generateDueTransactions(
        throughDate: LocalDate,
        maxOccurrencesPerRule: Int = DEFAULT_MAX_PER_RULE,
        maxTotalOccurrences: Int = DEFAULT_MAX_TOTAL,
        createdAt: Instant,
    ): RepositoryResult<GenerateRecurringTransactionsResult>

    companion object {
        const val DEFAULT_MAX_PER_RULE = 50
        const val DEFAULT_MAX_TOTAL = 200
    }
}
