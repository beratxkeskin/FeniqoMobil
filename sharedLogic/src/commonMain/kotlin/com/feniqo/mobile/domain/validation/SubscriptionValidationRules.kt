package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand

/**
 * Abonelik girdi ve komutlarına ait tipli doğrulama hata kodlarıdır.
 */
enum class SubscriptionValidationError {
    NAME_BLANK,
    NAME_TOO_LONG,
    AMOUNT_NON_POSITIVE,
    INTERVAL_NON_POSITIVE,
    END_DATE_BEFORE_START_DATE,
    NEXT_RENEWAL_BEFORE_START_DATE,
    NEXT_RENEWAL_AFTER_END_DATE,
}

/**
 * Abonelik doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface SubscriptionValidationResult<out T> {
    data class Valid<T>(val value: T) : SubscriptionValidationResult<T>
    data class Invalid(val error: SubscriptionValidationError) : SubscriptionValidationResult<Nothing>
}

/**
 * Platformdan bağımsız, saf abonelik kuralı doğrulama kuralları.
 */
object SubscriptionValidationRules {

    fun validateName(name: String): SubscriptionValidationResult<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_BLANK)
        }
        if (trimmed.length > Subscription.MAX_NAME_LENGTH) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.NAME_TOO_LONG)
        }
        return SubscriptionValidationResult.Valid(trimmed)
    }

    fun validateAmount(amount: Money): SubscriptionValidationResult<Money> {
        if (amount.amountMinor <= 0L) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE)
        }
        return SubscriptionValidationResult.Valid(amount)
    }

    fun validateAmountMinor(amountMinor: Long, currency: Currency): SubscriptionValidationResult<Money> {
        if (amountMinor <= 0L) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { SubscriptionValidationResult.Valid(it) }
            .getOrElse { SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE) }
    }

    fun validateAmountInput(amountInput: String, currency: Currency): SubscriptionValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0L) {
                            SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE)
                        } else {
                            SubscriptionValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { SubscriptionValidationResult.Invalid(SubscriptionValidationError.AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> SubscriptionValidationResult.Invalid(
                SubscriptionValidationError.AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateRenewalRule(rule: RecurrenceRule): SubscriptionValidationResult<RecurrenceRule> {
        if (rule.interval <= 0) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.INTERVAL_NON_POSITIVE)
        }
        if (rule.endDate != null && rule.endDate < rule.startDate) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.END_DATE_BEFORE_START_DATE)
        }
        return SubscriptionValidationResult.Valid(rule)
    }

    fun validateNextRenewalDate(
        nextRenewalDate: LocalDate,
        rule: RecurrenceRule,
    ): SubscriptionValidationResult<LocalDate> {
        if (nextRenewalDate < rule.startDate) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_BEFORE_START_DATE)
        }
        if (rule.endDate != null && nextRenewalDate > rule.endDate) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.NEXT_RENEWAL_AFTER_END_DATE)
        }
        return SubscriptionValidationResult.Valid(nextRenewalDate)
    }

    fun validateCreateCommand(
        command: CreateSubscriptionCommand,
    ): SubscriptionValidationResult<CreateSubscriptionCommand> {
        val nameRes = validateName(command.name)
        if (nameRes is SubscriptionValidationResult.Invalid) return nameRes

        val amountRes = validateAmount(command.amount)
        if (amountRes is SubscriptionValidationResult.Invalid) return amountRes

        val ruleRes = validateRenewalRule(command.renewalRule)
        if (ruleRes is SubscriptionValidationResult.Invalid) return ruleRes

        val dateRes = validateNextRenewalDate(command.nextRenewalDate, command.renewalRule)
        if (dateRes is SubscriptionValidationResult.Invalid) return dateRes

        val normalizedName = (nameRes as SubscriptionValidationResult.Valid).value
        val normalizedCommand = if (normalizedName != command.name) {
            command.copy(name = normalizedName)
        } else {
            command
        }

        return SubscriptionValidationResult.Valid(normalizedCommand)
    }

    fun validateUpdateCommand(
        command: UpdateSubscriptionCommand,
        existingNextRenewalDate: LocalDate,
    ): SubscriptionValidationResult<UpdateSubscriptionCommand> {
        val nameRes = validateName(command.name)
        if (nameRes is SubscriptionValidationResult.Invalid) return nameRes

        val amountRes = validateAmount(command.amount)
        if (amountRes is SubscriptionValidationResult.Invalid) return amountRes

        val ruleRes = validateRenewalRule(command.renewalRule)
        if (ruleRes is SubscriptionValidationResult.Invalid) return ruleRes

        val dateRes = validateNextRenewalDate(existingNextRenewalDate, command.renewalRule)
        if (dateRes is SubscriptionValidationResult.Invalid) return dateRes

        val normalizedName = (nameRes as SubscriptionValidationResult.Valid).value
        val normalizedCommand = if (normalizedName != command.name) {
            command.copy(name = normalizedName)
        } else {
            command
        }

        return SubscriptionValidationResult.Valid(normalizedCommand)
    }

    /**
     * Mevcut bir [Subscription] nesnesine [UpdateSubscriptionCommand] uygular.
     * Komut doğrulamadan geçirilir; geçersizse hata döndürülür ve entity üretilmez.
     * [ownerId], [workspaceId], [createdAt], [nextRenewalDate] ve [isActive] aynen korunur.
     */
    fun applySubscriptionUpdate(
        existing: Subscription,
        command: UpdateSubscriptionCommand,
    ): SubscriptionValidationResult<Subscription> {
        require(existing.id == command.id) {
            "Güncellenecek abonelik kimliği (${existing.id.value}) komut kimliğiyle (${command.id.value}) eşleşmelidir."
        }
        val validationResult = validateUpdateCommand(command, existing.nextRenewalDate)
        return when (validationResult) {
            is SubscriptionValidationResult.Invalid -> validationResult
            is SubscriptionValidationResult.Valid -> {
                val validCommand = validationResult.value
                SubscriptionValidationResult.Valid(
                    existing.copy(
                        name = validCommand.name,
                        amount = validCommand.amount,
                        categoryId = validCommand.categoryId,
                        renewalRule = validCommand.renewalRule,
                    )
                )
            }
        }
    }
}
