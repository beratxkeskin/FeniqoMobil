package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionDatePolicy
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.validation.InstallmentPlanCalculator
import com.feniqo.mobile.domain.validation.InstallmentPlanError
import com.feniqo.mobile.domain.validation.InstallmentPlanResult
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

enum class InstallmentDeleteScope {
    ONLY_THIS,
    THIS_AND_FOLLOWING,
    ALL_GROUP,
}

data class AddInstallmentGroupCommand(
    val workspaceId: EntityId?,
    val totalAmount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val anchorDate: LocalDate,
    val receiptPath: ReceiptPath?,
    val installmentCount: Int,
)

class AddInstallmentGroupUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val entityIdGenerator: EntityIdGenerator,
) {
    suspend operator fun invoke(
        command: AddInstallmentGroupCommand,
        today: LocalDate,
        createdAt: Instant,
    ): RepositoryResult<EntityId> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        if (command.type != TransactionType.EXPENSE) {
            return RepositoryResult.Failure(AppError.Validation("installment_type_must_be_expense"))
        }

        if (command.paymentMethod != PaymentMethod.CREDIT_CARD) {
            return RepositoryResult.Failure(AppError.Validation("installment_payment_must_be_credit_card"))
        }

        if (!TransactionDatePolicy.isAllowed(command.anchorDate, today)) {
            return RepositoryResult.Failure(AppError.Validation("transaction_date_cannot_be_future"))
        }

        val normalizedDescription = Transaction.normalizeDescription(command.description)
        if (normalizedDescription != null && normalizedDescription.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return RepositoryResult.Failure(AppError.Validation("transaction_description_too_long"))
        }

        val category = categoryRepository.observeCategory(command.categoryId).first()
        validateCategoryAccess(category, command.type, command.workspaceId, session.userId)?.let { error ->
            return RepositoryResult.Failure(error)
        }

        val planResult = InstallmentPlanCalculator.calculate(
            totalAmount = command.totalAmount,
            installmentCount = command.installmentCount,
            anchorDate = command.anchorDate,
        )

        val allocations = when (planResult) {
            is InstallmentPlanResult.Success -> planResult.allocations
            is InstallmentPlanResult.Invalid -> {
                val code = when (planResult.error) {
                    InstallmentPlanError.COUNT_OUT_OF_RANGE -> "installment_count_out_of_range"
                    InstallmentPlanError.COUNT_EXCEEDS_AMOUNT -> "installment_amount_too_small"
                    InstallmentPlanError.AMOUNT_MUST_BE_POSITIVE -> "transaction_amount_must_be_positive"
                }
                return RepositoryResult.Failure(AppError.Validation(code))
            }
        }

        val groupId = entityIdGenerator.nextId()
        val transactionIds = allocations.map { entityIdGenerator.nextId() }

        val allIds = listOf(groupId) + transactionIds
        if (allIds.map { it.value }.distinct().size != allIds.size || allIds.any { it.value.isBlank() }) {
            return RepositoryResult.Failure(AppError.Unknown("entity_id_generation_failed"))
        }

        val transactions = allocations.mapIndexed { index, allocation ->
            Transaction(
                id = transactionIds[index],
                ownerId = session.userId,
                workspaceId = command.workspaceId,
                amount = allocation.amount,
                type = command.type,
                categoryId = command.categoryId,
                description = normalizedDescription,
                paymentMethod = command.paymentMethod,
                transactionDate = allocation.transactionDate,
                receiptPath = command.receiptPath,
                installment = InstallmentInfo(
                    number = allocation.number,
                    total = allocation.total,
                    groupId = groupId,
                ),
                createdAt = createdAt,
            )
        }

        return transactionRepository.createInstallmentGroup(transactions)
    }
}
