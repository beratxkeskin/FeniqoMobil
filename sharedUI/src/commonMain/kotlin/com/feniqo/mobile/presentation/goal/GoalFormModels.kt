package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.validation.GoalDebtValidationError
import com.feniqo.mobile.domain.validation.GoalDebtValidationResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationRules
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Birikim hedefi formu alan bazlı doğrulama hataları.
 */
enum class GoalFormFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    TARGET_AMOUNT_REQUIRED,
    TARGET_AMOUNT_INVALID,
    TARGET_AMOUNT_NON_POSITIVE,
    INITIAL_AMOUNT_INVALID,
    INITIAL_AMOUNT_NEGATIVE,
    INITIAL_AMOUNT_NOT_ALLOWED_IN_EDIT,
    TARGET_DATE_REQUIRED,
    COLOR_INVALID,
    CURRENCY_LOCKED_IN_EDIT,
}

/**
 * Hedef formu girdi hatalarının konteyneri.
 */
data class GoalFormInputErrors(
    val nameError: GoalFormFieldError? = null,
    val targetAmountError: GoalFormFieldError? = null,
    val initialAmountError: GoalFormFieldError? = null,
    val targetDateError: GoalFormFieldError? = null,
    val colorError: GoalFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = nameError != null ||
                targetAmountError != null ||
                initialAmountError != null ||
                targetDateError != null ||
                colorError != null
}

/**
 * Hedef formu normalizasyon ve doğrulama sonucu.
 */
sealed interface GoalFormNormalizationResult {
    data class Valid(val draft: GoalFormDraft) : GoalFormNormalizationResult
    data class Invalid(val errors: GoalFormInputErrors) : GoalFormNormalizationResult
}

val GOAL_PRESET_COLORS = listOf(
    "#2E7D32", // Yeşil
    "#1976D2", // Mavi
    "#C2185B", // Pembe
    "#F57C00", // Turuncu
    "#7B1FA2", // Mor
    "#00796B", // Teal
    "#D32F2F", // Kırmızı
    "#5D4037", // Kahve
)

/**
 * Kullanıcı girdilerini tutan hedef form modeli.
 */
data class GoalFormInput(
    val goalId: EntityId? = null,
    val nameInput: String = "",
    val targetAmountInput: String = "",
    val currency: Currency = Currency.TRY,
    val initialAmountInput: String = "",
    val targetDate: LocalDate? = null,
    val colorHex: String = GOAL_PRESET_COLORS.first(),
    val iconKey: String? = "savings",
) {
    val isEditMode: Boolean get() = goalId != null
    val isCreateMode: Boolean get() = goalId == null

    fun toDraft(): GoalFormNormalizationResult {
        // 1. Name validation
        val nameValidation = GoalDebtValidationRules.validateGoalName(nameInput)
        val nameError = when (nameValidation) {
            is GoalDebtValidationResult.Invalid -> when (nameValidation.error) {
                GoalDebtValidationError.GOAL_NAME_BLANK -> GoalFormFieldError.NAME_REQUIRED
                GoalDebtValidationError.GOAL_NAME_TOO_LONG -> GoalFormFieldError.NAME_TOO_LONG
                else -> GoalFormFieldError.NAME_REQUIRED
            }
            is GoalDebtValidationResult.Valid -> null
        }

        // 2. Target Amount validation
        val targetAmountMinor: Long?
        var targetAmountError: GoalFormFieldError? = null
        if (targetAmountInput.isBlank()) {
            targetAmountMinor = null
            targetAmountError = GoalFormFieldError.TARGET_AMOUNT_REQUIRED
        } else {
            when (val parseResult = MoneyAmountParser.parseToMinorUnits(targetAmountInput, currency)) {
                is MoneyAmountParser.ParseResult.Success -> {
                    if (parseResult.amountMinor <= 0) {
                        targetAmountMinor = null
                        targetAmountError = GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE
                    } else {
                        targetAmountMinor = parseResult.amountMinor
                    }
                }
                is MoneyAmountParser.ParseResult.Invalid -> {
                    targetAmountMinor = null
                    targetAmountError = when (parseResult.error) {
                        MoneyAmountParser.MoneyParseError.NON_POSITIVE -> GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE
                        MoneyAmountParser.MoneyParseError.EMPTY -> GoalFormFieldError.TARGET_AMOUNT_REQUIRED
                        else -> GoalFormFieldError.TARGET_AMOUNT_INVALID
                    }
                }
            }
        }

        // 3. Initial Amount validation (yalnız create modunda izin verilir)
        var initialAmountMinor: Long? = null
        var initialAmountError: GoalFormFieldError? = null
        if (isEditMode) {
            if (initialAmountInput.isNotBlank()) {
                initialAmountError = GoalFormFieldError.INITIAL_AMOUNT_NOT_ALLOWED_IN_EDIT
            }
        } else {
            if (initialAmountInput.isNotBlank()) {
                when (val parseResult = MoneyAmountParser.parseToMinorUnits(initialAmountInput, currency)) {
                    is MoneyAmountParser.ParseResult.Success -> {
                        initialAmountMinor = parseResult.amountMinor
                    }
                    is MoneyAmountParser.ParseResult.Invalid -> {
                        if (parseResult.error == MoneyAmountParser.MoneyParseError.NON_POSITIVE) {
                            // 0 girildiyse başlangıç 0 minor unittir
                            initialAmountMinor = 0L
                        } else {
                            initialAmountError = GoalFormFieldError.INITIAL_AMOUNT_INVALID
                        }
                    }
                }
            }
        }


        // 4. Target Date validation
        val targetDateError = if (targetDate == null) GoalFormFieldError.TARGET_DATE_REQUIRED else null

        // 5. Color validation
        val colorError = if (ColorParser.parseHexColorOrNull(colorHex) == null) {
            GoalFormFieldError.COLOR_INVALID
        } else {
            null
        }

        val errors = GoalFormInputErrors(
            nameError = nameError,
            targetAmountError = targetAmountError,
            initialAmountError = initialAmountError,
            targetDateError = targetDateError,
            colorError = colorError,
        )

        return if (errors.hasErrors) {
            GoalFormNormalizationResult.Invalid(errors)
        } else {
            val validTargetAmount = Money(checkNotNull(targetAmountMinor), currency)
            val validInitialAmount = initialAmountMinor?.let { Money(it, currency) }
            GoalFormNormalizationResult.Valid(
                GoalFormDraft(
                    goalId = goalId,
                    name = (nameValidation as GoalDebtValidationResult.Valid).value,
                    targetAmount = validTargetAmount,
                    initialAmount = validInitialAmount,
                    targetDate = checkNotNull(targetDate),
                    colorHex = colorHex,
                    iconKey = iconKey?.trim()?.ifBlank { null },
                ),
            )
        }
    }
}

/**
 * Doğrulanmış hedef form taslağı.
 */
data class GoalFormDraft(
    val goalId: EntityId? = null,
    val name: String,
    val targetAmount: Money,
    val initialAmount: Money? = null,
    val targetDate: LocalDate,
    val colorHex: String,
    val iconKey: String? = null,
) {
    val isEditMode: Boolean get() = goalId != null
    val isCreateMode: Boolean get() = goalId == null

    fun toCreateCommand(): CreateGoalCommand {
        check(isCreateMode) {
            "Düzenleme taslağından (ID: ${goalId?.value}) create komutu üretilemez."
        }
        return CreateGoalCommand(
            name = name,
            targetAmount = targetAmount,
            initialAmount = initialAmount,
            targetDate = targetDate,
            color = CategoryColor(colorHex),
            icon = iconKey?.let { CategoryIcon(it) },
        )
    }

    fun toUpdateCommand(): UpdateGoalCommand {
        val targetId = checkNotNull(goalId) {
            "Yeni kayıt taslağından update komutu üretilemez; geçerli bir goalId gereklidir."
        }
        return UpdateGoalCommand(
            id = targetId,
            name = name,
            targetAmount = targetAmount,
            targetDate = targetDate,
            color = CategoryColor(colorHex),
            icon = iconKey?.let { CategoryIcon(it) },
        )
    }

    companion object {
        fun fromDomain(goal: Goal): GoalFormDraft {
            return GoalFormDraft(
                goalId = goal.id,
                name = goal.name,
                targetAmount = goal.targetAmount,
                initialAmount = null,
                targetDate = goal.targetDate,
                colorHex = goal.color.hex,
                iconKey = goal.icon?.key,
            )
        }
    }
}

/**
 * Düzenleme modu için SSOT veri yükleme durumları.
 */
sealed interface GoalEditLoadState {
    data object Idle : GoalEditLoadState
    data object Loading : GoalEditLoadState
    data class Ready(
        val draft: GoalFormDraft,
        val currentAmount: Money,
    ) : GoalEditLoadState
    data object NotFound : GoalEditLoadState
    data class Error(val message: FinanceUiMessage) : GoalEditLoadState
}

fun resolveEffectiveGoalEditLoadState(
    goalId: EntityId?,
    loadState: GoalEditLoadState,
): GoalEditLoadState {
    return if (goalId != null && loadState is GoalEditLoadState.Idle) {
        GoalEditLoadState.Loading
    } else {
        loadState
    }
}

/**
 * Hedef hareket geçmişi UI modeli.
 */
data class GoalContributionHistoryItemUiModel(
    val id: EntityId,
    val amount: Money,
    val formattedAmount: String,
    val direction: com.feniqo.mobile.domain.model.GoalContributionDirection,
    val occurredOn: LocalDate,
    val formattedDate: String,
    val note: String?,
)

fun com.feniqo.mobile.domain.model.GoalContribution.toHistoryItemUiModel(): GoalContributionHistoryItemUiModel {
    val prefix = if (direction == com.feniqo.mobile.domain.model.GoalContributionDirection.ADD) "+" else "-"
    return GoalContributionHistoryItemUiModel(
        id = id,
        amount = amount,
        formattedAmount = "$prefix${MoneyFormatter.format(amount)}",
        direction = direction,
        occurredOn = occurredOn,
        formattedDate = com.feniqo.mobile.presentation.util.DateFormatter.formatReadableDate(occurredOn),
        note = note,
    )
}

fun List<com.feniqo.mobile.domain.model.GoalContribution>.toSortedHistoryUiModels(): List<GoalContributionHistoryItemUiModel> {
    return this
        .sortedWith(
            compareByDescending<com.feniqo.mobile.domain.model.GoalContribution> { it.occurredOn }
                .thenByDescending { it.id.value }
        )
        .map { it.toHistoryItemUiModel() }
}

/**
 * Hedef formu UI durumu.
 */
data class GoalFormUiState(
    val input: GoalFormInput = GoalFormInput(),
    val errors: GoalFormInputErrors = GoalFormInputErrors(),
    val isSubmitting: Boolean = false,
    val pendingDeleteConfirmation: Boolean = false,
    val activeWorkspaceName: String? = null,
    val contributionsHistory: List<GoalContributionHistoryItemUiModel> = emptyList(),
)

/**
 * Tek seferlik hedef form eventleri.
 */
sealed interface GoalFormUiEvent {
    data class MutationSuccess(val message: FinanceUiMessage) : GoalFormUiEvent
    data class ShowMessage(val message: FinanceUiMessage) : GoalFormUiEvent
}

