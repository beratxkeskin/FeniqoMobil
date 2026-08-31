package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveRecurringTransactionsUseCase(
    private val repository: RecurringTransactionRepository,
) {
    operator fun invoke(): Flow<List<RecurringTransaction>> = repository.observeRecurringTransactions()
}

class ObserveRecurringTransactionUseCase(
    private val repository: RecurringTransactionRepository,
) {
    operator fun invoke(id: EntityId): Flow<RecurringTransaction?> = repository.observeRecurringTransaction(id)
}

class CreateRecurringTransactionUseCase(
    private val repository: RecurringTransactionRepository,
) {
    suspend operator fun invoke(command: CreateRecurringTransactionCommand) = repository.create(command)
}

class UpdateRecurringTransactionUseCase(
    private val repository: RecurringTransactionRepository,
) {
    suspend operator fun invoke(command: UpdateRecurringTransactionCommand) = repository.update(command)
}

class SetRecurringTransactionActiveUseCase(
    private val repository: RecurringTransactionRepository,
) {
    suspend operator fun invoke(command: SetRecurringTransactionActiveCommand) = repository.setActive(command)
}

class DeleteRecurringTransactionUseCase(
    private val repository: RecurringTransactionRepository,
) {
    suspend operator fun invoke(id: EntityId) = repository.softDelete(id)
}

