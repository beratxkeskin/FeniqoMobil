package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
import com.feniqo.mobile.domain.validation.GoalDebtValidationError
import com.feniqo.mobile.domain.validation.GoalDebtValidationResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationRules
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.FinanceUiMessage

enum class DebtPaymentFormFieldError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    EXCEEDS_REMAINING_AMOUNT,
    DEBT_ALREADY_SETTLED,
    DATE_REQUIRED,
    CURRENCY_MISMATCH,
    PARENT_DEBT_MISSING,
}

data class DebtPaymentFormInputErrors(
    val amountError: DebtPaymentFormFieldError? = null,
    val dateError: DebtPaymentFormFieldError? = null,
    val generalError: DebtPaymentFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = amountError != null || dateError != null || generalError != null
}

data class DebtPaymentFormInput(
    val amountInput: String = "",
    val paidOn: LocalDate? = null,
) {
    fun toDraft(
        parentDebt: Debt?,
        existingPayments: List<DebtPayment>,
    ): DebtPaymentFormNormalizationResult {
        if (parentDebt == null) {
            return DebtPaymentFormNormalizationResult.Invalid(
                DebtPaymentFormInputErrors(generalError = DebtPaymentFormFieldError.PARENT_DEBT_MISSING),
            )
        }

        val balance = DebtBalanceCalculator.calculate(parentDebt, existingPayments)
        if (balance.status == DebtStatus.SETTLED || balance.remainingAmount.amountMinor <= 0L) {
            return DebtPaymentFormNormalizationResult.Invalid(
                DebtPaymentFormInputErrors(
                    amountError = DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED,
                    generalError = DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED,
                ),
            )
        }

        val currency = parentDebt.amount.currency

        // 1. Amount validation
        val amountMinor: Long?
        var amountError: DebtPaymentFormFieldError? = null
        if (amountInput.isBlank()) {
            amountMinor = null
            amountError = DebtPaymentFormFieldError.AMOUNT_REQUIRED
        } else {
            when (val parseResult = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
                is MoneyAmountParser.ParseResult.Success -> {
                    if (parseResult.amountMinor <= 0) {
                        amountMinor = null
                        amountError = DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE
                    } else if (parseResult.amountMinor > balance.remainingAmount.amountMinor) {
                        amountMinor = null
                        amountError = DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT
                    } else {
                        amountMinor = parseResult.amountMinor
                    }
                }
                is MoneyAmountParser.ParseResult.Invalid -> {
                    amountMinor = null
                    amountError = when (parseResult.error) {
                        MoneyAmountParser.MoneyParseError.NON_POSITIVE -> DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE
                        MoneyAmountParser.MoneyParseError.EMPTY -> DebtPaymentFormFieldError.AMOUNT_REQUIRED
                        else -> DebtPaymentFormFieldError.AMOUNT_INVALID
                    }
                }
            }
        }

        // 2. Date validation
        val dateError = if (paidOn == null) DebtPaymentFormFieldError.DATE_REQUIRED else null

        if (amountError != null || dateError != null || amountMinor == null || paidOn == null) {
            return DebtPaymentFormNormalizationResult.Invalid(
                DebtPaymentFormInputErrors(
                    amountError = amountError,
                    dateError = dateError,
                ),
            )
        }

        val candidateCommand = AddDebtPaymentCommand(
            debtId = parentDebt.id,
            amount = Money(amountMinor, currency),
            paidOn = paidOn,
        )

        val domainValidation = GoalDebtValidationRules.validateAddDebtPaymentCommand(
            command = candidateCommand,
            debt = parentDebt,
            existingPayments = existingPayments,
        )

        return when (domainValidation) {
            is GoalDebtValidationResult.Invalid -> {
                val mappedError = when (domainValidation.error) {
                    GoalDebtValidationError.DEBT_PAYMENT_EXCEEDS_REMAINING -> DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT
                    GoalDebtValidationError.DEBT_PAYMENT_CURRENCY_MISMATCH -> DebtPaymentFormFieldError.CURRENCY_MISMATCH
                    GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE -> DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE
                    else -> DebtPaymentFormFieldError.AMOUNT_INVALID

                }
                DebtPaymentFormNormalizationResult.Invalid(
                    DebtPaymentFormInputErrors(amountError = mappedError),
                )
            }
            is GoalDebtValidationResult.Valid -> {
                DebtPaymentFormNormalizationResult.Success(domainValidation.value)
            }
        }
    }
}

sealed interface DebtPaymentFormNormalizationResult {
    data class Success(val command: AddDebtPaymentCommand) : DebtPaymentFormNormalizationResult
    data class Invalid(val errors: DebtPaymentFormInputErrors) : DebtPaymentFormNormalizationResult
}

sealed interface DebtPaymentParentLoadState {
    data object Idle : DebtPaymentParentLoadState
    data object Loading : DebtPaymentParentLoadState
    data class Ready(
        val debt: Debt,
        val remainingAmount: Money,
        val isSettled: Boolean,
    ) : DebtPaymentParentLoadState
    data object NotFound : DebtPaymentParentLoadState
    data class Error(val message: FinanceUiMessage) : DebtPaymentParentLoadState
}

fun resolveEffectiveDebtPaymentParentLoadState(
    parentDebtId: EntityId?,
    loadState: DebtPaymentParentLoadState,
): DebtPaymentParentLoadState {
    if (parentDebtId == null) return DebtPaymentParentLoadState.NotFound
    return when (loadState) {
        DebtPaymentParentLoadState.Idle -> DebtPaymentParentLoadState.Loading
        else -> loadState
    }
}

data class DebtPaymentFormUiState(
    val input: DebtPaymentFormInput = DebtPaymentFormInput(),
    val errors: DebtPaymentFormInputErrors = DebtPaymentFormInputErrors(),
    val isSubmitting: Boolean = false,
)

sealed interface DebtPaymentFormUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : DebtPaymentFormUiEvent
    data class MutationSuccess(val message: FinanceUiMessage) : DebtPaymentFormUiEvent
}
