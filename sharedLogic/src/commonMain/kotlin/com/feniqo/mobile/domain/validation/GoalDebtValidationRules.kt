package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand

/**
 * Hedef ve Borç/Alacak girdi ve komutlarına ait tipli doğrulama hata kodlarıdır.
 */
enum class GoalDebtValidationError {
    GOAL_NAME_BLANK,
    GOAL_NAME_TOO_LONG,
    GOAL_TARGET_AMOUNT_NON_POSITIVE,
    GOAL_INITIAL_AMOUNT_NEGATIVE,
    GOAL_CURRENCY_MISMATCH,
    GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE,
    GOAL_CONTRIBUTION_NOTE_BLANK,
    GOAL_CONTRIBUTION_NOTE_TOO_LONG,
    GOAL_CONTRIBUTION_GOAL_ID_MISMATCH,
    GOAL_CONTRIBUTION_EXCEEDS_CURRENT_AMOUNT,
    DEBT_TITLE_BLANK,
    DEBT_TITLE_TOO_LONG,
    DEBT_AMOUNT_NON_POSITIVE,
    DEBT_CURRENCY_MISMATCH,
    DEBT_DESCRIPTION_BLANK,
    DEBT_DESCRIPTION_TOO_LONG,
    DEBT_UPDATE_AMOUNT_LESS_THAN_PAID,
    DEBT_PAYMENT_AMOUNT_NON_POSITIVE,
    DEBT_PAYMENT_CURRENCY_MISMATCH,
    DEBT_PAYMENT_DEBT_ID_MISMATCH,
    DEBT_PAYMENT_EXCEEDS_REMAINING,
}

/**
 * Hedef ve Borç/Alacak doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface GoalDebtValidationResult<out T> {
    data class Valid<T>(val value: T) : GoalDebtValidationResult<T>
    data class Invalid(val error: GoalDebtValidationError) : GoalDebtValidationResult<Nothing>
}

/**
 * Platformdan bağımsız, saf hedef ve borç/alacak doğrulama kuralları.
 */
object GoalDebtValidationRules {

    fun validateGoalName(name: String): GoalDebtValidationResult<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_NAME_BLANK)
        }
        if (trimmed.length > Goal.MAX_NAME_LENGTH) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_NAME_TOO_LONG)
        }
        return GoalDebtValidationResult.Valid(trimmed)
    }

    fun validateGoalTargetAmount(amount: Money): GoalDebtValidationResult<Money> {
        if (amount.amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE)
        }
        return GoalDebtValidationResult.Valid(amount)
    }

    fun validateGoalTargetAmountMinor(amountMinor: Long, currency: Currency): GoalDebtValidationResult<Money> {
        if (amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { GoalDebtValidationResult.Valid(it) }
            .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE) }
    }

    fun validateGoalTargetAmountInput(input: String, currency: Currency): GoalDebtValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(input, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0L) {
                            GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE)
                        } else {
                            GoalDebtValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> GoalDebtValidationResult.Invalid(
                GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateGoalInitialAmount(initialAmount: Money?, targetCurrency: Currency): GoalDebtValidationResult<Money> {
        if (initialAmount == null) {
            return GoalDebtValidationResult.Valid(Money(0L, targetCurrency))
        }
        if (initialAmount.currency != targetCurrency) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH)
        }
        if (initialAmount.amountMinor < 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_INITIAL_AMOUNT_NEGATIVE)
        }
        return GoalDebtValidationResult.Valid(initialAmount)
    }

    fun validateGoalContributionAmount(amount: Money): GoalDebtValidationResult<Money> {
        if (amount.amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE)
        }
        return GoalDebtValidationResult.Valid(amount)
    }

    fun validateGoalContributionAmountMinor(amountMinor: Long, currency: Currency): GoalDebtValidationResult<Money> {
        if (amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { GoalDebtValidationResult.Valid(it) }
            .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE) }
    }

    fun validateGoalContributionAmountInput(input: String, currency: Currency): GoalDebtValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(input, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0L) {
                            GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE)
                        } else {
                            GoalDebtValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> GoalDebtValidationResult.Invalid(
                GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateGoalContributionNote(note: String?): GoalDebtValidationResult<String?> {
        if (note == null) {
            return GoalDebtValidationResult.Valid(null)
        }
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_NOTE_BLANK)
        }
        if (trimmed.length > GoalContribution.MAX_NOTE_LENGTH) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_NOTE_TOO_LONG)
        }
        return GoalDebtValidationResult.Valid(trimmed)
    }

    fun validateDebtTitle(title: String): GoalDebtValidationResult<String> {
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_TITLE_BLANK)
        }
        if (trimmed.length > Debt.MAX_TITLE_LENGTH) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_TITLE_TOO_LONG)
        }
        return GoalDebtValidationResult.Valid(trimmed)
    }

    fun validateDebtAmount(amount: Money): GoalDebtValidationResult<Money> {
        if (amount.amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE)
        }
        return GoalDebtValidationResult.Valid(amount)
    }

    fun validateDebtAmountMinor(amountMinor: Long, currency: Currency): GoalDebtValidationResult<Money> {
        if (amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { GoalDebtValidationResult.Valid(it) }
            .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE) }
    }

    fun validateDebtAmountInput(input: String, currency: Currency): GoalDebtValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(input, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0L) {
                            GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE)
                        } else {
                            GoalDebtValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> GoalDebtValidationResult.Invalid(
                GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateDebtDescription(description: String?): GoalDebtValidationResult<String?> {
        if (description == null) {
            return GoalDebtValidationResult.Valid(null)
        }
        val trimmed = description.trim()
        if (trimmed.isEmpty()) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_DESCRIPTION_BLANK)
        }
        if (trimmed.length > Debt.MAX_DESCRIPTION_LENGTH) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_DESCRIPTION_TOO_LONG)
        }
        return GoalDebtValidationResult.Valid(trimmed)
    }

    fun validateDebtPaymentAmount(amount: Money): GoalDebtValidationResult<Money> {
        if (amount.amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE)
        }
        return GoalDebtValidationResult.Valid(amount)
    }

    fun validateDebtPaymentAmountMinor(amountMinor: Long, currency: Currency): GoalDebtValidationResult<Money> {
        if (amountMinor <= 0L) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE)
        }
        return runCatching { Money(amountMinor, currency) }
            .map { GoalDebtValidationResult.Valid(it) }
            .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE) }
    }

    fun validateDebtPaymentAmountInput(input: String, currency: Currency): GoalDebtValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(input, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map {
                        if (it.amountMinor <= 0L) {
                            GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE)
                        } else {
                            GoalDebtValidationResult.Valid(it)
                        }
                    }
                    .getOrElse { GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> GoalDebtValidationResult.Invalid(
                GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE,
            )
        }
    }

    fun validateCreateGoalCommand(command: CreateGoalCommand): GoalDebtValidationResult<CreateGoalCommand> {
        val nameRes = validateGoalName(command.name)
        if (nameRes is GoalDebtValidationResult.Invalid) return nameRes

        val targetAmountRes = validateGoalTargetAmount(command.targetAmount)
        if (targetAmountRes is GoalDebtValidationResult.Invalid) return targetAmountRes

        val initialAmountRes = validateGoalInitialAmount(command.initialAmount, command.targetAmount.currency)
        if (initialAmountRes is GoalDebtValidationResult.Invalid) return initialAmountRes

        val normalizedName = (nameRes as GoalDebtValidationResult.Valid).value
        val normalizedInitial = (initialAmountRes as GoalDebtValidationResult.Valid).value

        return GoalDebtValidationResult.Valid(
            command.copy(
                name = normalizedName,
                initialAmount = normalizedInitial,
            )
        )
    }

    fun validateUpdateGoalCommand(command: UpdateGoalCommand): GoalDebtValidationResult<UpdateGoalCommand> {
        val nameRes = validateGoalName(command.name)
        if (nameRes is GoalDebtValidationResult.Invalid) return nameRes

        val targetAmountRes = validateGoalTargetAmount(command.targetAmount)
        if (targetAmountRes is GoalDebtValidationResult.Invalid) return targetAmountRes

        val normalizedName = (nameRes as GoalDebtValidationResult.Valid).value
        return GoalDebtValidationResult.Valid(command.copy(name = normalizedName))
    }

    fun validateAddGoalContributionCommand(
        command: AddGoalContributionCommand,
        goal: Goal? = null,
    ): GoalDebtValidationResult<AddGoalContributionCommand> {
        val amountRes = validateGoalContributionAmount(command.amount)
        if (amountRes is GoalDebtValidationResult.Invalid) return amountRes

        val noteRes = validateGoalContributionNote(command.note)
        if (noteRes is GoalDebtValidationResult.Invalid) return noteRes

        if (goal != null) {
            if (command.goalId != goal.id) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_GOAL_ID_MISMATCH)
            }
            if (command.amount.currency != goal.targetAmount.currency) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH)
            }
            if (command.direction == GoalContributionDirection.REMOVE &&
                command.amount.amountMinor > goal.currentAmount.amountMinor
            ) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CONTRIBUTION_EXCEEDS_CURRENT_AMOUNT)
            }
        }

        val normalizedNote = (noteRes as GoalDebtValidationResult.Valid).value
        return GoalDebtValidationResult.Valid(command.copy(note = normalizedNote))
    }

    fun validateCreateDebtCommand(command: CreateDebtCommand): GoalDebtValidationResult<CreateDebtCommand> {
        val titleRes = validateDebtTitle(command.title)
        if (titleRes is GoalDebtValidationResult.Invalid) return titleRes

        val amountRes = validateDebtAmount(command.amount)
        if (amountRes is GoalDebtValidationResult.Invalid) return amountRes

        val descRes = validateDebtDescription(command.description)
        if (descRes is GoalDebtValidationResult.Invalid) return descRes

        val normalizedTitle = (titleRes as GoalDebtValidationResult.Valid).value
        val normalizedDesc = (descRes as GoalDebtValidationResult.Valid).value

        return GoalDebtValidationResult.Valid(
            command.copy(
                title = normalizedTitle,
                description = normalizedDesc,
            )
        )
    }

    fun validateUpdateDebtCommand(
        command: UpdateDebtCommand,
        existingPayments: List<DebtPayment>? = null,
    ): GoalDebtValidationResult<UpdateDebtCommand> {
        val titleRes = validateDebtTitle(command.title)
        if (titleRes is GoalDebtValidationResult.Invalid) return titleRes

        val amountRes = validateDebtAmount(command.amount)
        if (amountRes is GoalDebtValidationResult.Invalid) return amountRes

        val descRes = validateDebtDescription(command.description)
        if (descRes is GoalDebtValidationResult.Invalid) return descRes

        if (existingPayments != null) {
            val totalPaidMinor = existingPayments.sumOf { it.amount.amountMinor }
            if (command.amount.amountMinor < totalPaidMinor) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_UPDATE_AMOUNT_LESS_THAN_PAID)
            }
        }

        val normalizedTitle = (titleRes as GoalDebtValidationResult.Valid).value
        val normalizedDesc = (descRes as GoalDebtValidationResult.Valid).value

        return GoalDebtValidationResult.Valid(
            command.copy(
                title = normalizedTitle,
                description = normalizedDesc,
            )
        )
    }

    fun validateAddDebtPaymentCommand(
        command: AddDebtPaymentCommand,
        debt: Debt? = null,
        existingPayments: List<DebtPayment>? = null,
    ): GoalDebtValidationResult<AddDebtPaymentCommand> {
        val amountRes = validateDebtPaymentAmount(command.amount)
        if (amountRes is GoalDebtValidationResult.Invalid) return amountRes

        if (debt != null) {
            if (command.debtId != debt.id) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_DEBT_ID_MISMATCH)
            }
            if (command.amount.currency != debt.amount.currency) {
                return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_CURRENCY_MISMATCH)
            }
            if (existingPayments != null) {
                val balance = DebtBalanceCalculator.calculate(debt, existingPayments)
                if (command.amount.amountMinor > balance.remainingAmount.amountMinor) {
                    return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_EXCEEDS_REMAINING)
                }
            } else {
                if (command.amount.amountMinor > debt.amount.amountMinor) {
                    return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_PAYMENT_EXCEEDS_REMAINING)
                }
            }
        }

        return GoalDebtValidationResult.Valid(command)
    }

    /**
     * Mevcut bir [Goal] nesnesine [UpdateGoalCommand] uygular.
     * Hedef para biriminin değiştirilmesine izin verilmez.
     * [ownerId], [workspaceId], [currentAmount] ve [createdAt] aynen korunur.
     */
    fun applyGoalUpdate(
        existing: Goal,
        command: UpdateGoalCommand,
    ): GoalDebtValidationResult<Goal> {
        require(existing.id == command.id) {
            "Güncellenecek hedef kimliği (${existing.id.value}) komut kimliğiyle (${command.id.value}) eşleşmelidir."
        }
        if (command.targetAmount.currency != existing.currentAmount.currency ||
            command.targetAmount.currency != existing.targetAmount.currency
        ) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH)
        }
        val validationResult = validateUpdateGoalCommand(command)
        return when (validationResult) {
            is GoalDebtValidationResult.Invalid -> validationResult
            is GoalDebtValidationResult.Valid -> {
                val validCommand = validationResult.value
                GoalDebtValidationResult.Valid(
                    existing.copy(
                        name = validCommand.name,
                        targetAmount = validCommand.targetAmount,
                        targetDate = validCommand.targetDate,
                        color = validCommand.color,
                        icon = validCommand.icon,
                    )
                )
            }
        }
    }

    /**
     * Mevcut bir [Debt] nesnesine [UpdateDebtCommand] uygular.
     * Borç/alacak para biriminin değiştirilmesine izin verilmez.
     * [ownerId], [workspaceId], [status] ve [createdAt] aynen korunur.
     */
    fun applyDebtUpdate(
        existing: Debt,
        command: UpdateDebtCommand,
        existingPayments: List<DebtPayment>? = null,
    ): GoalDebtValidationResult<Debt> {
        require(existing.id == command.id) {
            "Güncellenecek borç kimliği (${existing.id.value}) komut kimliğiyle (${command.id.value}) eşleşmelidir."
        }
        if (command.amount.currency != existing.amount.currency) {
            return GoalDebtValidationResult.Invalid(GoalDebtValidationError.DEBT_CURRENCY_MISMATCH)
        }
        val validationResult = validateUpdateDebtCommand(command, existingPayments)
        return when (validationResult) {
            is GoalDebtValidationResult.Invalid -> validationResult
            is GoalDebtValidationResult.Valid -> {
                val validCommand = validationResult.value
                GoalDebtValidationResult.Valid(
                    existing.copy(
                        title = validCommand.title,
                        amount = validCommand.amount,
                        type = validCommand.type,
                        dueDate = validCommand.dueDate,
                        description = validCommand.description,
                    )
                )
            }
        }
    }
}

