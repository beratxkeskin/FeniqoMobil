package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.validation.GoalDebtValidationError
import com.feniqo.mobile.domain.validation.GoalDebtValidationResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationRules
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Borç / Alacak formu alan bazlı doğrulama hataları.
 */
enum class DebtFormFieldError {
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    DUE_DATE_REQUIRED,
    DESCRIPTION_TOO_LONG,
    CURRENCY_LOCKED_IN_EDIT,
}

/**
 * Borç / Alacak formu girdi hatalarının konteyneri.
 */
data class DebtFormInputErrors(
    val titleError: DebtFormFieldError? = null,
    val amountError: DebtFormFieldError? = null,
    val dueDateError: DebtFormFieldError? = null,
    val descriptionError: DebtFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = titleError != null ||
                amountError != null ||
                dueDateError != null ||
                descriptionError != null
}

/**
 * Borç / Alacak formu normalizasyon ve doğrulama sonucu.
 */
sealed interface DebtFormNormalizationResult {
    data class Valid(val draft: DebtFormDraft) : DebtFormNormalizationResult
    data class Invalid(val errors: DebtFormInputErrors) : DebtFormNormalizationResult
}

/**
 * Kullanıcı girdilerini tutan borç / alacak form modeli.
 */
data class DebtFormInput(
    val debtId: EntityId? = null,
    val titleInput: String = "",
    val amountInput: String = "",
    val currency: Currency = Currency.TRY,
    val type: DebtType = DebtType.DEBT,
    val dueDate: LocalDate? = null,
    val descriptionInput: String = "",
) {
    val isEditMode: Boolean get() = debtId != null
    val isCreateMode: Boolean get() = debtId == null

    fun toDraft(): DebtFormNormalizationResult {
        // 1. Title validation
        val titleValidation = GoalDebtValidationRules.validateDebtTitle(titleInput)
        val titleError = when (titleValidation) {
            is GoalDebtValidationResult.Invalid -> when (titleValidation.error) {
                GoalDebtValidationError.DEBT_TITLE_BLANK -> DebtFormFieldError.TITLE_REQUIRED
                GoalDebtValidationError.DEBT_TITLE_TOO_LONG -> DebtFormFieldError.TITLE_TOO_LONG
                else -> DebtFormFieldError.TITLE_REQUIRED
            }
            is GoalDebtValidationResult.Valid -> null
        }

        // 2. Amount validation
        val amountMinor: Long?
        var amountError: DebtFormFieldError? = null
        if (amountInput.isBlank()) {
            amountMinor = null
            amountError = DebtFormFieldError.AMOUNT_REQUIRED
        } else {
            when (val parseResult = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
                is MoneyAmountParser.ParseResult.Success -> {
                    if (parseResult.amountMinor <= 0) {
                        amountMinor = null
                        amountError = DebtFormFieldError.AMOUNT_NON_POSITIVE
                    } else {
                        amountMinor = parseResult.amountMinor
                    }
                }
                is MoneyAmountParser.ParseResult.Invalid -> {
                    amountMinor = null
                    amountError = when (parseResult.error) {
                        MoneyAmountParser.MoneyParseError.NON_POSITIVE -> DebtFormFieldError.AMOUNT_NON_POSITIVE
                        MoneyAmountParser.MoneyParseError.EMPTY -> DebtFormFieldError.AMOUNT_REQUIRED
                        else -> DebtFormFieldError.AMOUNT_INVALID
                    }
                }

            }
        }


        // 3. Due Date validation
        val dueDateError = if (dueDate == null) DebtFormFieldError.DUE_DATE_REQUIRED else null

        // 4. Description validation
        val descriptionError = if (descriptionInput.trim().length > Debt.MAX_DESCRIPTION_LENGTH) {
            DebtFormFieldError.DESCRIPTION_TOO_LONG
        } else {
            null
        }

        val errors = DebtFormInputErrors(
            titleError = titleError,
            amountError = amountError,
            dueDateError = dueDateError,
            descriptionError = descriptionError,
        )

        return if (errors.hasErrors) {
            DebtFormNormalizationResult.Invalid(errors)
        } else {
            val validAmount = Money(checkNotNull(amountMinor), currency)
            val trimmedDesc = descriptionInput.trim().ifBlank { null }
            DebtFormNormalizationResult.Valid(
                DebtFormDraft(
                    debtId = debtId,
                    title = (titleValidation as GoalDebtValidationResult.Valid).value,
                    amount = validAmount,
                    type = type,
                    dueDate = checkNotNull(dueDate),
                    description = trimmedDesc,
                ),
            )
        }
    }
}

/**
 * Doğrulanmış borç / alacak form taslağı.
 */
data class DebtFormDraft(
    val debtId: EntityId? = null,
    val title: String,
    val amount: Money,
    val type: DebtType,
    val dueDate: LocalDate,
    val description: String? = null,
) {
    val isEditMode: Boolean get() = debtId != null
    val isCreateMode: Boolean get() = debtId == null

    fun toCreateCommand(): CreateDebtCommand {
        check(isCreateMode) {
            "Düzenleme taslağından (ID: ${debtId?.value}) create komutu üretilemez."
        }
        return CreateDebtCommand(
            title = title,
            amount = amount,
            type = type,
            dueDate = dueDate,
            description = description,
        )
    }

    fun toUpdateCommand(): UpdateDebtCommand {
        val targetId = checkNotNull(debtId) {
            "Yeni kayıt taslağından update komutu üretilemez; geçerli bir debtId gereklidir."
        }
        return UpdateDebtCommand(
            id = targetId,
            title = title,
            amount = amount,
            type = type,
            dueDate = dueDate,
            description = description,
        )
    }

    companion object {
        fun fromDomain(debt: Debt): DebtFormDraft {
            return DebtFormDraft(
                debtId = debt.id,
                title = debt.title,
                amount = debt.amount,
                type = debt.type,
                dueDate = debt.dueDate,
                description = debt.description,
            )
        }
    }
}

/**
 * Düzenleme modu için SSOT veri yükleme durumları.
 */
sealed interface DebtEditLoadState {
    data object Idle : DebtEditLoadState
    data object Loading : DebtEditLoadState
    data class Ready(
        val draft: DebtFormDraft,
        val status: DebtStatus,
    ) : DebtEditLoadState
    data object NotFound : DebtEditLoadState
    data class Error(val message: FinanceUiMessage) : DebtEditLoadState
}

fun resolveEffectiveDebtEditLoadState(
    debtId: EntityId?,
    loadState: DebtEditLoadState,
): DebtEditLoadState {
    return if (debtId != null && loadState is DebtEditLoadState.Idle) {
        DebtEditLoadState.Loading
    } else {
        loadState
    }
}

/**
 * Borç / Alacak ödeme geçmişi UI modeli.
 */
data class DebtPaymentHistoryItemUiModel(
    val id: EntityId,
    val amount: Money,
    val formattedAmount: String,
    val paidOn: LocalDate,
    val formattedDate: String,
)

fun com.feniqo.mobile.domain.model.DebtPayment.toHistoryItemUiModel(): DebtPaymentHistoryItemUiModel {
    return DebtPaymentHistoryItemUiModel(
        id = id,
        amount = amount,
        formattedAmount = MoneyFormatter.format(amount),
        paidOn = paidOn,
        formattedDate = com.feniqo.mobile.presentation.util.DateFormatter.formatReadableDate(paidOn),
    )
}

fun List<com.feniqo.mobile.domain.model.DebtPayment>.toSortedHistoryUiModels(): List<DebtPaymentHistoryItemUiModel> {
    return this
        .sortedWith(
            compareByDescending<com.feniqo.mobile.domain.model.DebtPayment> { it.paidOn }
                .thenByDescending { it.id.value }
        )
        .map { it.toHistoryItemUiModel() }
}

/**
 * Düzenleme ekranında gösterilen reaktif bakiye ve durum özeti presentation modeli.
 */
data class DebtBalanceSummaryUiModel(
    val formattedPrincipalAmount: String,
    val formattedTotalPaid: String,
    val formattedRemainingAmount: String,
    val isSettled: Boolean,
    val statusText: String,
    val type: DebtType,
)

/**
 * Borç / Alacak formu UI durumu.
 */
data class DebtFormUiState(
    val input: DebtFormInput = DebtFormInput(),
    val errors: DebtFormInputErrors = DebtFormInputErrors(),
    val isSubmitting: Boolean = false,
    val pendingDeleteConfirmation: Boolean = false,
    val paymentsHistory: List<DebtPaymentHistoryItemUiModel> = emptyList(),
    val balanceSummary: DebtBalanceSummaryUiModel? = null,
)

/**
 * Tek seferlik borç / alacak form eventleri.
 */
sealed interface DebtFormUiEvent {
    data class MutationSuccess(val message: FinanceUiMessage) : DebtFormUiEvent
    data class ShowMessage(val message: FinanceUiMessage) : DebtFormUiEvent
}

