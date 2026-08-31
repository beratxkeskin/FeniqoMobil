package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand

/**
 * Tekrarlayan işlem girdi ve komutlarına ait tipli doğrulama hata kodlarıdır.
 */
enum class RecurringTransactionValidationError {
    AMOUNT_NON_POSITIVE,
    CATEGORY_REQUIRED,
    DESCRIPTION_TOO_LONG,
    INTERVAL_NON_POSITIVE,
    END_DATE_BEFORE_START_DATE,
    START_DATE_AFTER_LAST_GENERATED,
    END_DATE_BEFORE_LAST_GENERATED,
}

/**
 * Tekrarlayan işlem doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface RecurringTransactionValidationResult<out T> {
    data class Valid<T>(val value: T) : RecurringTransactionValidationResult<T>
    data class Invalid(val error: RecurringTransactionValidationError) : RecurringTransactionValidationResult<Nothing>
}

/**
 * Platformdan bağımsız, saf tekrarlayan işlem kuralı doğrulama kuralları.
 */
object RecurringTransactionValidationRules {

    fun validateAmount(amount: Money): RecurringTransactionValidationResult<Money> {
        if (amount.amountMinor <= 0) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE)
        }
        return RecurringTransactionValidationResult.Valid(amount)
    }

    fun validateAmountMinor(amountMinor: Long, currency: Currency): RecurringTransactionValidationResult<Money> {
        if (amountMinor <= 0) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { RecurringTransactionValidationResult.Valid(it) }
            .getOrElse { RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE) }
    }

    fun validateAmountInput(amountInput: String, currency: Currency): RecurringTransactionValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0) {
                            RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE)
                        } else {
                            RecurringTransactionValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> RecurringTransactionValidationResult.Invalid(
                RecurringTransactionValidationError.AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateCategory(categoryId: EntityId?): RecurringTransactionValidationResult<EntityId> {
        if (categoryId == null || categoryId.value.isBlank()) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.CATEGORY_REQUIRED)
        }
        return RecurringTransactionValidationResult.Valid(categoryId)
    }

    fun validateDescription(description: String?): RecurringTransactionValidationResult<String?> {
        val normalized = Transaction.normalizeDescription(description)
        if (normalized != null && normalized.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.DESCRIPTION_TOO_LONG)
        }
        return RecurringTransactionValidationResult.Valid(normalized)
    }

    fun validateRule(rule: RecurrenceRule): RecurringTransactionValidationResult<RecurrenceRule> {
        if (rule.interval <= 0) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.INTERVAL_NON_POSITIVE)
        }
        if (rule.endDate != null && rule.endDate < rule.startDate) {
            return RecurringTransactionValidationResult.Invalid(RecurringTransactionValidationError.END_DATE_BEFORE_START_DATE)
        }
        return RecurringTransactionValidationResult.Valid(rule)
    }

    fun validateRuleUpdate(
        rule: RecurrenceRule,
        lastGeneratedDate: LocalDate?,
    ): RecurringTransactionValidationResult<RecurrenceRule> {
        val baseValidation = validateRule(rule)
        if (baseValidation is RecurringTransactionValidationResult.Invalid) {
            return baseValidation
        }

        if (lastGeneratedDate != null) {
            if (rule.startDate > lastGeneratedDate) {
                return RecurringTransactionValidationResult.Invalid(
                    RecurringTransactionValidationError.START_DATE_AFTER_LAST_GENERATED,
                )
            }
            if (rule.endDate != null && rule.endDate < lastGeneratedDate) {
                return RecurringTransactionValidationResult.Invalid(
                    RecurringTransactionValidationError.END_DATE_BEFORE_LAST_GENERATED,
                )
            }
        }

        return RecurringTransactionValidationResult.Valid(rule)
    }

    fun validateCreateCommand(
        command: CreateRecurringTransactionCommand,
    ): RecurringTransactionValidationResult<CreateRecurringTransactionCommand> {
        val amountRes = validateAmount(command.amount)
        if (amountRes is RecurringTransactionValidationResult.Invalid) return amountRes

        val catRes = validateCategory(command.categoryId)
        if (catRes is RecurringTransactionValidationResult.Invalid) return catRes

        val descRes = validateDescription(command.description)
        if (descRes is RecurringTransactionValidationResult.Invalid) return descRes

        val ruleRes = validateRule(command.rule)
        if (ruleRes is RecurringTransactionValidationResult.Invalid) return ruleRes

        val normalizedCommand = if (descRes is RecurringTransactionValidationResult.Valid && descRes.value != command.description) {
            command.copy(description = descRes.value)
        } else {
            command
        }

        return RecurringTransactionValidationResult.Valid(normalizedCommand)
    }

    fun validateUpdateCommand(
        command: UpdateRecurringTransactionCommand,
        existingLastGeneratedDate: LocalDate?,
    ): RecurringTransactionValidationResult<UpdateRecurringTransactionCommand> {
        val amountRes = validateAmount(command.amount)
        if (amountRes is RecurringTransactionValidationResult.Invalid) return amountRes

        val catRes = validateCategory(command.categoryId)
        if (catRes is RecurringTransactionValidationResult.Invalid) return catRes

        val descRes = validateDescription(command.description)
        if (descRes is RecurringTransactionValidationResult.Invalid) return descRes

        val ruleRes = validateRuleUpdate(command.rule, existingLastGeneratedDate)
        if (ruleRes is RecurringTransactionValidationResult.Invalid) return ruleRes

        val normalizedCommand = if (descRes is RecurringTransactionValidationResult.Valid && descRes.value != command.description) {
            command.copy(description = descRes.value)
        } else {
            command
        }

        return RecurringTransactionValidationResult.Valid(normalizedCommand)
    }

    /**
     * Mevcut bir [RecurringTransaction] nesnesine [UpdateRecurringTransactionCommand] uygular.
     * Komut doğrulamadan geçirilir; geçersizse hata döndürülür ve entity üretilmez.
     * [ownerId], [workspaceId], [createdAt], [lastGeneratedDate] ve [isActive] aynen korunur.
     */
    fun applyRecurringRuleUpdate(
        existing: RecurringTransaction,
        command: UpdateRecurringTransactionCommand,
    ): RecurringTransactionValidationResult<RecurringTransaction> {
        require(existing.id == command.id) {
            "Güncellenecek tekrarlayan işlem kimliği (${existing.id.value}) komut kimliğiyle (${command.id.value}) eşleşmelidir."
        }
        val validationResult = validateUpdateCommand(command, existing.lastGeneratedDate)
        return when (validationResult) {
            is RecurringTransactionValidationResult.Invalid -> validationResult
            is RecurringTransactionValidationResult.Valid -> {
                val validCommand = validationResult.value
                RecurringTransactionValidationResult.Valid(
                    existing.copy(
                        amount = validCommand.amount,
                        type = validCommand.type,
                        categoryId = validCommand.categoryId,
                        description = validCommand.description,
                        paymentMethod = validCommand.paymentMethod,
                        rule = validCommand.rule,
                    )
                )
            }
        }
    }
}

