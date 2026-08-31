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
}
