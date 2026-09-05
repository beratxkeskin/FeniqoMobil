package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.GoalDebtValidationError
import com.feniqo.mobile.domain.validation.GoalDebtValidationResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationRules
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.FinanceUiMessage

enum class GoalContributionFormFieldError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    EXCEEDS_CURRENT_AMOUNT,
    DATE_REQUIRED,
    NOTE_TOO_LONG,
    CURRENCY_MISMATCH,
    PARENT_GOAL_MISSING,
}

data class GoalContributionFormInputErrors(
    val amountError: GoalContributionFormFieldError? = null,
    val dateError: GoalContributionFormFieldError? = null,
    val noteError: GoalContributionFormFieldError? = null,
    val generalError: GoalContributionFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = amountError != null || dateError != null || noteError != null || generalError != null
}

data class GoalContributionFormInput(
    val amountInput: String = "",
    val direction: GoalContributionDirection = GoalContributionDirection.ADD,
    val occurredOn: LocalDate? = null,
    val noteInput: String = "",
) {
    fun toDraft(parentGoal: Goal?): GoalContributionFormNormalizationResult {
        if (parentGoal == null) {
            return GoalContributionFormNormalizationResult.Invalid(
                GoalContributionFormInputErrors(generalError = GoalContributionFormFieldError.PARENT_GOAL_MISSING),
            )
        }

        val currency = parentGoal.targetAmount.currency

        // 1. Amount validation
        val amountMinor: Long?
        var amountError: GoalContributionFormFieldError? = null
        if (amountInput.isBlank()) {
            amountMinor = null
            amountError = GoalContributionFormFieldError.AMOUNT_REQUIRED
        } else {
            when (val parseResult = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
                is MoneyAmountParser.ParseResult.Success -> {
                    if (parseResult.amountMinor <= 0) {
                        amountMinor = null
                        amountError = GoalContributionFormFieldError.AMOUNT_NON_POSITIVE
                    } else if (direction == GoalContributionDirection.REMOVE &&
                        parseResult.amountMinor > parentGoal.currentAmount.amountMinor
                    ) {
                        amountMinor = null
                        amountError = GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT
                    } else {
                        amountMinor = parseResult.amountMinor
                    }
                }
                is MoneyAmountParser.ParseResult.Invalid -> {
                    amountMinor = null
                    amountError = when (parseResult.error) {
                        MoneyAmountParser.MoneyParseError.NON_POSITIVE -> GoalContributionFormFieldError.AMOUNT_NON_POSITIVE
                        MoneyAmountParser.MoneyParseError.EMPTY -> GoalContributionFormFieldError.AMOUNT_REQUIRED
                        else -> GoalContributionFormFieldError.AMOUNT_INVALID
                    }
                }
            }
        }

        // 2. Date validation
        val dateError = if (occurredOn == null) GoalContributionFormFieldError.DATE_REQUIRED else null

        // 3. Note validation
        val rawNote = noteInput.trim().ifEmpty { null }
        val noteError = if (rawNote != null && rawNote.length > GoalContribution.MAX_NOTE_LENGTH) {
            GoalContributionFormFieldError.NOTE_TOO_LONG
        } else {
            null
        }

        if (amountError != null || dateError != null || noteError != null || amountMinor == null || occurredOn == null) {
            return GoalContributionFormNormalizationResult.Invalid(
                GoalContributionFormInputErrors(
                    amountError = amountError,
                    dateError = dateError,
                    noteError = noteError,
                ),
            )
        }

        val candidateCommand = AddGoalContributionCommand(
            goalId = parentGoal.id,
            amount = Money(amountMinor, currency),
            direction = direction,
            occurredOn = occurredOn,
            note = rawNote,
        )

        val domainValidation = GoalDebtValidationRules.validateAddGoalContributionCommand(candidateCommand, parentGoal)
        return when (domainValidation) {
            is GoalDebtValidationResult.Invalid -> {
                val mappedAmountError = when (domainValidation.error) {
                    GoalDebtValidationError.GOAL_CONTRIBUTION_EXCEEDS_CURRENT_AMOUNT -> GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT
                    GoalDebtValidationError.GOAL_CURRENCY_MISMATCH -> GoalContributionFormFieldError.CURRENCY_MISMATCH
                    GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE -> GoalContributionFormFieldError.AMOUNT_NON_POSITIVE
                    GoalDebtValidationError.GOAL_CONTRIBUTION_NOTE_TOO_LONG -> GoalContributionFormFieldError.NOTE_TOO_LONG
                    else -> GoalContributionFormFieldError.AMOUNT_INVALID

                }
                GoalContributionFormNormalizationResult.Invalid(
                    GoalContributionFormInputErrors(
                        amountError = if (mappedAmountError != GoalContributionFormFieldError.NOTE_TOO_LONG) mappedAmountError else null,
                        noteError = if (mappedAmountError == GoalContributionFormFieldError.NOTE_TOO_LONG) mappedAmountError else null,
                    ),
                )
            }
            is GoalDebtValidationResult.Valid -> {
                GoalContributionFormNormalizationResult.Success(domainValidation.value)
            }
        }
    }
}

sealed interface GoalContributionFormNormalizationResult {
    data class Success(val command: AddGoalContributionCommand) : GoalContributionFormNormalizationResult
    data class Invalid(val errors: GoalContributionFormInputErrors) : GoalContributionFormNormalizationResult
}

sealed interface GoalContributionParentLoadState {
    data object Idle : GoalContributionParentLoadState
    data object Loading : GoalContributionParentLoadState
    data class Ready(val goal: Goal) : GoalContributionParentLoadState
    data object NotFound : GoalContributionParentLoadState
    data class Error(val message: FinanceUiMessage) : GoalContributionParentLoadState
}

fun resolveEffectiveGoalContributionParentLoadState(
    parentGoalId: EntityId?,
    loadState: GoalContributionParentLoadState,
): GoalContributionParentLoadState {
    if (parentGoalId == null) return GoalContributionParentLoadState.NotFound
    return when (loadState) {
        GoalContributionParentLoadState.Idle -> GoalContributionParentLoadState.Loading
        else -> loadState
    }
}

data class GoalContributionFormUiState(
    val input: GoalContributionFormInput = GoalContributionFormInput(),
    val errors: GoalContributionFormInputErrors = GoalContributionFormInputErrors(),
    val isSubmitting: Boolean = false,
)

sealed interface GoalContributionFormUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : GoalContributionFormUiEvent
    data class MutationSuccess(val message: FinanceUiMessage) : GoalContributionFormUiEvent
}
