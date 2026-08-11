package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

data class TransactionCommand(
    val id: EntityId,
    val workspaceId: EntityId?,
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val transactionDate: LocalDate,
    val receiptPath: ReceiptPath?,
    val installment: InstallmentInfo?,
)

class AddTransactionUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        command: TransactionCommand,
        today: LocalDate,
        createdAt: Instant,
    ): RepositoryResult<EntityId> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        validateTransaction(command, today, categoryRepository)?.let { error ->
            return RepositoryResult.Failure(error)
        }

        return transactionRepository.create(
            Transaction(
                id = command.id,
                ownerId = session.userId,
                workspaceId = command.workspaceId,
                amount = command.amount,
                type = command.type,
                categoryId = command.categoryId,
                description = Transaction.normalizeDescription(command.description),
                paymentMethod = command.paymentMethod,
                transactionDate = command.transactionDate,
                receiptPath = command.receiptPath,
                installment = command.installment,
                createdAt = createdAt,
            ),
        )
    }
}

class UpdateTransactionUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        command: TransactionCommand,
        today: LocalDate,
    ): RepositoryResult<Unit> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val existing = transactionRepository.observeTransaction(command.id).first()
            ?: return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
        if (existing.ownerId != session.userId) {
            return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
        }

        validateTransaction(command, today, categoryRepository)?.let { error ->
            return RepositoryResult.Failure(error)
        }

        return transactionRepository.update(
            existing.copy(
                workspaceId = command.workspaceId,
                amount = command.amount,
                type = command.type,
                categoryId = command.categoryId,
                description = Transaction.normalizeDescription(command.description),
                paymentMethod = command.paymentMethod,
                transactionDate = command.transactionDate,
                receiptPath = command.receiptPath,
                installment = command.installment,
            ),
        )
    }
}

class DeleteTransactionUseCase(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val existing = transactionRepository.observeTransaction(id).first()
            ?: return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
        if (existing.ownerId != session.userId) {
            return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
        }
        return transactionRepository.softDelete(id)
    }
}

class ObserveTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>> =
        transactionRepository.observeTransactions(filter)
}

private suspend fun validateTransaction(
    command: TransactionCommand,
    today: LocalDate,
    categoryRepository: CategoryRepository,
): AppError.Validation? {
    if (command.amount.amountMinor <= 0) return AppError.Validation("transaction_amount_must_be_positive")
    if (!com.feniqo.mobile.domain.model.TransactionDatePolicy.isAllowed(command.transactionDate, today)) {
        return AppError.Validation("transaction_date_cannot_be_future")
    }
    val description = Transaction.normalizeDescription(command.description)
    if (description != null && description.length > Transaction.MAX_DESCRIPTION_LENGTH) {
        return AppError.Validation("transaction_description_too_long")
    }
    val category = categoryRepository.observeCategory(command.categoryId).first()
        ?: return AppError.Validation("transaction_category_not_found")
    if (category.type != command.type) return AppError.Validation("transaction_category_type_mismatch")
    return null
}
