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

        validateTransaction(command, today, session.userId, command.workspaceId, categoryRepository)?.let { error ->
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
                installment = null,
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

        if (command.workspaceId != existing.workspaceId) {
            return RepositoryResult.Failure(AppError.Validation("transaction_workspace_immutable"))
        }

        validateTransaction(command, today, session.userId, existing.workspaceId, categoryRepository)?.let { error ->
            return RepositoryResult.Failure(error)
        }

        return transactionRepository.update(
            existing.copy(
                amount = command.amount,
                type = command.type,
                categoryId = command.categoryId,
                description = Transaction.normalizeDescription(command.description),
                paymentMethod = command.paymentMethod,
                transactionDate = command.transactionDate,
                receiptPath = command.receiptPath,
            ),
        )
    }
}

class DeleteTransactionUseCase(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        id: EntityId,
        installmentScope: InstallmentDeleteScope? = null,
    ): RepositoryResult<Unit> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val existing = transactionRepository.observeTransaction(id).first()
            ?: return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
        if (existing.ownerId != session.userId) {
            return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
        }

        val installment = existing.installment
        if (installment == null) {
            if (installmentScope != null) {
                return RepositoryResult.Failure(AppError.Validation("installment_scope_not_applicable"))
            }
            return transactionRepository.softDelete(id)
        }

        if (installmentScope == null) {
            return RepositoryResult.Failure(AppError.Validation("installment_delete_scope_required"))
        }

        val groupTransactions = transactionRepository.observeInstallmentGroup(installment.groupId).first()
        if (groupTransactions.isEmpty() || groupTransactions.none { it.id == id }) {
            return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
        }

        for (item in groupTransactions) {
            val itemInst = item.installment
            if (itemInst == null || itemInst.groupId != installment.groupId || itemInst.total != installment.total) {
                return RepositoryResult.Failure(AppError.Validation("installment_group_mismatch"))
            }
        }

        val targetNumber = installment.number
        val targetIds: Set<EntityId> = when (installmentScope) {
            InstallmentDeleteScope.ONLY_THIS -> setOf(id)
            InstallmentDeleteScope.THIS_AND_FOLLOWING -> {
                groupTransactions
                    .filter { it.installment!!.number >= targetNumber }
                    .map { it.id }
                    .toSet()
            }
            InstallmentDeleteScope.ALL_GROUP -> {
                groupTransactions.map { it.id }.toSet()
            }
        }

        if (targetIds.isEmpty()) {
            return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
        }

        return transactionRepository.softDeleteInstallments(targetIds)
    }
}

class ObserveTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>> =
        transactionRepository.observeTransactions(filter)
}

class ObserveTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(id: EntityId): Flow<Transaction?> =
        transactionRepository.observeTransaction(id)
}

private suspend fun validateTransaction(
    command: TransactionCommand,
    today: LocalDate,
    userId: EntityId,
    targetWorkspaceId: EntityId?,
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
    return validateCategoryAccess(category, command.type, targetWorkspaceId, userId)
}

internal fun validateCategoryAccess(
    category: com.feniqo.mobile.domain.model.Category?,
    expectedType: TransactionType,
    targetWorkspaceId: EntityId?,
    userId: EntityId,
): AppError.Validation? {
    if (category == null) return AppError.Validation("transaction_category_not_found")
    if (category.type != expectedType) return AppError.Validation("transaction_category_type_mismatch")

    if (category.isDefault && category.ownerId == null) {
        return null
    }

    if (category.ownerId != userId) {
        return AppError.Validation("transaction_category_not_found")
    }

    // A null command scope means “current active scope”; observeCategory already
    // exposes only that Room-backed scope. Explicit route scopes must still match.
    if (targetWorkspaceId != null && category.workspaceId != targetWorkspaceId) {
        return AppError.Validation("category_workspace_mismatch")
    }

    return null
}
