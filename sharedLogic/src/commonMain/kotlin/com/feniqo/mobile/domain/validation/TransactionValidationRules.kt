package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionDatePolicy

sealed interface TransactionValidationResult<out T> {
    data class Valid<T>(val value: T) : TransactionValidationResult<T>
    data class Invalid(val error: TransactionValidationError) : TransactionValidationResult<Nothing>
}

enum class TransactionValidationError {
    AMOUNT_EMPTY,
    AMOUNT_INVALID_FORMAT,
    AMOUNT_NON_POSITIVE,
    AMOUNT_EXCESSIVE_DECIMAL_DIGITS,
    AMOUNT_MAX_EXCEEDED,
    CATEGORY_REQUIRED,
    DATE_FUTURE,
    DESCRIPTION_TOO_LONG,
    SPLIT_PARTICIPANTS_EMPTY,
    SPLIT_PARTICIPANTS_DUPLICATE,
    SPLIT_PAYER_NOT_IN_PARTICIPANTS,
    SPLIT_MEMBER_NOT_IN_WORKSPACE,
}

object TransactionValidationRules {

    fun validateAmount(input: String, currency: Currency): TransactionValidationResult<Long> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(input, currency)) {
            is MoneyAmountParser.ParseResult.Success -> TransactionValidationResult.Valid(result.amountMinor)
            is MoneyAmountParser.ParseResult.Invalid -> TransactionValidationResult.Invalid(
                when (result.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> TransactionValidationError.AMOUNT_EMPTY
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT -> TransactionValidationError.AMOUNT_INVALID_FORMAT
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> TransactionValidationError.AMOUNT_NON_POSITIVE
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS -> TransactionValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> TransactionValidationError.AMOUNT_MAX_EXCEEDED
                },
            )
        }
    }

    fun validateCategory(categoryId: EntityId?): TransactionValidationResult<EntityId> {
        if (categoryId == null || categoryId.value.isBlank()) {
            return TransactionValidationResult.Invalid(TransactionValidationError.CATEGORY_REQUIRED)
        }
        return TransactionValidationResult.Valid(categoryId)
    }

    fun validateDate(date: LocalDate, today: LocalDate): TransactionValidationResult<LocalDate> {
        if (!TransactionDatePolicy.isAllowed(date, today)) {
            return TransactionValidationResult.Invalid(TransactionValidationError.DATE_FUTURE)
        }
        return TransactionValidationResult.Valid(date)
    }

    fun validateDescription(description: String?): TransactionValidationResult<String?> {
        val normalized = Transaction.normalizeDescription(description)
        if (normalized != null && normalized.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return TransactionValidationResult.Invalid(TransactionValidationError.DESCRIPTION_TOO_LONG)
        }
        return TransactionValidationResult.Valid(normalized)
    }

    /**
     * Ortak gider split bilgisini doğrular veya kişisel/gelir işlemleri için güvenli şekilde normalize eder.
     * Kişisel veya INCOME işlemlerde: payer ve tek katılımcı ownerId yapılır.
     * Ortak EXPENSE işlemlerde: payer ve katılımcılar workspace aktif üyeleri olmalı, payer katılımcı listesinde bulunmalıdır.
     */
    fun normalizeAndValidateSplit(
        transaction: Transaction,
        activeMemberUserIds: Set<EntityId>? = null,
    ): TransactionValidationResult<Transaction> {
        if (transaction.workspaceId == null || transaction.type != com.feniqo.mobile.domain.model.TransactionType.EXPENSE) {
            val normalized = transaction.copy(
                paidByUserId = transaction.ownerId,
                participantUserIds = listOf(transaction.ownerId),
            )
            return TransactionValidationResult.Valid(normalized)
        }

        if (transaction.participantUserIds.isEmpty()) {
            return TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_PARTICIPANTS_EMPTY)
        }

        if (transaction.participantUserIds.distinct().size != transaction.participantUserIds.size) {
            return TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_PARTICIPANTS_DUPLICATE)
        }

        if (transaction.paidByUserId !in transaction.participantUserIds) {
            return TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_PAYER_NOT_IN_PARTICIPANTS)
        }

        if (activeMemberUserIds != null) {
            if (transaction.paidByUserId !in activeMemberUserIds ||
                transaction.participantUserIds.any { it !in activeMemberUserIds }
            ) {
                return TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_MEMBER_NOT_IN_WORKSPACE)
            }
        }

        return TransactionValidationResult.Valid(transaction)
    }
}
