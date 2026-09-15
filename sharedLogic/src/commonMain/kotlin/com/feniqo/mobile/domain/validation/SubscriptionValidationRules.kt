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
    TRIAL_END_DATE_MISSING,
    TRIAL_END_DATE_BEFORE_START_DATE,
    CANCELLATION_DATE_MISSING,
    ACCESS_END_DATE_BEFORE_CANCELLATION_DATE,
    WEBSITE_URL_TOO_LONG,
    NOTES_TOO_LONG,
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

        val lifecycleRes = validateLifecycle(
            lifecycleStatus = command.lifecycleStatus,
            trialEndDate = command.trialEndDate,
            cancellationDate = null,
            accessEndDate = null,
            startDate = command.renewalRule.startDate,
        )
        if (lifecycleRes is SubscriptionValidationResult.Invalid) return lifecycleRes

        val websiteRes = validateWebsiteUrl(command.websiteUrl)
        if (websiteRes is SubscriptionValidationResult.Invalid) return websiteRes

        val notesRes = validateNotes(command.notes)
        if (notesRes is SubscriptionValidationResult.Invalid) return notesRes

        val normalizedName = (nameRes as SubscriptionValidationResult.Valid).value
        val normalizedCommand = command.copy(
            name = normalizedName,
            websiteUrl = (websiteRes as SubscriptionValidationResult.Valid).value,
            notes = (notesRes as SubscriptionValidationResult.Valid).value,
        )

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

        val lifecycleRes = validateLifecycle(
            lifecycleStatus = command.lifecycleStatus,
            trialEndDate = command.trialEndDate,
            cancellationDate = command.cancellationDate,
            accessEndDate = command.accessEndDate,
            startDate = command.renewalRule.startDate,
        )
        if (lifecycleRes is SubscriptionValidationResult.Invalid) return lifecycleRes

        val websiteRes = validateWebsiteUrl(command.websiteUrl)
        if (websiteRes is SubscriptionValidationResult.Invalid) return websiteRes

        val notesRes = validateNotes(command.notes)
        if (notesRes is SubscriptionValidationResult.Invalid) return notesRes

        val normalizedName = (nameRes as SubscriptionValidationResult.Valid).value
        val normalizedCommand = command.copy(
            name = normalizedName,
            websiteUrl = (websiteRes as SubscriptionValidationResult.Valid).value,
            notes = (notesRes as SubscriptionValidationResult.Valid).value,
        )

        return SubscriptionValidationResult.Valid(normalizedCommand)
    }

    fun validateWebsiteUrl(url: String?): SubscriptionValidationResult<String?> {
        if (url == null) return SubscriptionValidationResult.Valid(null)
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return SubscriptionValidationResult.Valid(null)
        if (trimmed.length > Subscription.MAX_WEBSITE_URL_LENGTH) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.WEBSITE_URL_TOO_LONG)
        }
        return SubscriptionValidationResult.Valid(trimmed)
    }

    fun validateNotes(notes: String?): SubscriptionValidationResult<String?> {
        if (notes == null) return SubscriptionValidationResult.Valid(null)
        val trimmed = notes.trim()
        if (trimmed.isEmpty()) return SubscriptionValidationResult.Valid(null)
        if (trimmed.length > Subscription.MAX_NOTES_LENGTH) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.NOTES_TOO_LONG)
        }
        return SubscriptionValidationResult.Valid(trimmed)
    }

    fun validateLifecycle(
        lifecycleStatus: com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus,
        trialEndDate: LocalDate?,
        cancellationDate: LocalDate?,
        accessEndDate: LocalDate?,
        startDate: LocalDate,
    ): SubscriptionValidationResult<Unit> {
        if (lifecycleStatus == com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus.TRIAL && trialEndDate == null) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.TRIAL_END_DATE_MISSING)
        }
        if (trialEndDate != null && trialEndDate < startDate) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.TRIAL_END_DATE_BEFORE_START_DATE)
        }
        if (lifecycleStatus == com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus.CANCELLED && cancellationDate == null) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.CANCELLATION_DATE_MISSING)
        }
        if (accessEndDate != null && cancellationDate != null && accessEndDate < cancellationDate) {
            return SubscriptionValidationResult.Invalid(SubscriptionValidationError.ACCESS_END_DATE_BEFORE_CANCELLATION_DATE)
        }
        return SubscriptionValidationResult.Valid(Unit)
    }

    /**
     * Mevcut bir [Subscription] nesnesine [UpdateSubscriptionCommand] uygular.
     * Komut doğrulamadan geçirilir; geçersizse hata döndürülür ve entity üretilmez.
     * [ownerId], [workspaceId], [createdAt] ve [nextRenewalDate] aynen korunur.
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
                val isEffectivelyActive = validCommand.lifecycleStatus == com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus.ACTIVE ||
                    validCommand.lifecycleStatus == com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus.TRIAL
                SubscriptionValidationResult.Valid(
                    existing.copy(
                        name = validCommand.name,
                        amount = validCommand.amount,
                        categoryId = validCommand.categoryId,
                        renewalRule = validCommand.renewalRule,
                        lifecycleStatus = validCommand.lifecycleStatus,
                        trialEndDate = validCommand.trialEndDate,
                        cancellationDate = validCommand.cancellationDate,
                        accessEndDate = validCommand.accessEndDate,
                        reminderEnabled = validCommand.reminderEnabled,
                        isActive = isEffectivelyActive,
                        websiteUrl = validCommand.websiteUrl,
                        notes = validCommand.notes,
                    )
                )
            }
        }
    }
}
