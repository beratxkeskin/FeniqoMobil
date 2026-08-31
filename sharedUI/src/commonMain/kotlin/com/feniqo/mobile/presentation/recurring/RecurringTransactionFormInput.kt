package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Tekrarlayan işlem formu alan bazlı doğrulama hataları.
 */
enum class RecurringTransactionFormFieldError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    AMOUNT_TOO_LARGE,
    CATEGORY_REQUIRED,
    CATEGORY_TYPE_MISMATCH,
    INTERVAL_INVALID,
    INTERVAL_NON_POSITIVE,
    START_DATE_REQUIRED,
    END_DATE_BEFORE_START_DATE,
    DESCRIPTION_TOO_LONG,
}

/**
 * Form girdi hatalarının toplu konteyneri.
 */
data class RecurringTransactionFormInputErrors(
    val amountError: RecurringTransactionFormFieldError? = null,
    val categoryError: RecurringTransactionFormFieldError? = null,
    val intervalError: RecurringTransactionFormFieldError? = null,
    val startDateError: RecurringTransactionFormFieldError? = null,
    val endDateError: RecurringTransactionFormFieldError? = null,
    val descriptionError: RecurringTransactionFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = amountError != null ||
                categoryError != null ||
                intervalError != null ||
                startDateError != null ||
                endDateError != null ||
                descriptionError != null
}

/**
 * Normalizasyon ve doğrulama sonucu kapalı sözleşmesi.
 */
sealed interface RecurringTransactionFormNormalizationResult {
    data class Valid(val draft: RecurringTransactionFormDraft) : RecurringTransactionFormNormalizationResult
    data class Invalid(val errors: RecurringTransactionFormInputErrors) : RecurringTransactionFormNormalizationResult
}

/**
 * Kullanıcı metin girdilerini ve UI seçimlerini tutan saf form girdi modelidir.
 */
data class RecurringTransactionFormInput(
    val recurringTransactionId: EntityId? = null,
    val amountInput: String = "",
    val currency: Currency = Currency.TRY,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: EntityId? = null,
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CREDIT_CARD,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalInput: String = "1",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    val isEditMode: Boolean get() = recurringTransactionId != null
    val isCreateMode: Boolean get() = recurringTransactionId == null

    /**
     * Kullanıcı girdisini doğrular ve geçerliyse [RecurringTransactionFormDraft] üretir.
     * Kategori tür uyumsuzluğunu denetlemek için kategori listesi verilebilir.
     */
    fun toDraft(categories: List<Category> = emptyList()): RecurringTransactionFormNormalizationResult {
        // 1. Amount validation
        val amountMinor: Long?
        var amountError: RecurringTransactionFormFieldError? = null
        when (val parseResult = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                if (parseResult.amountMinor <= 0) {
                    amountMinor = null
                    amountError = RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE
                } else {
                    amountMinor = parseResult.amountMinor
                }
            }
            is MoneyAmountParser.ParseResult.Invalid -> {
                amountMinor = null
                amountError = when (parseResult.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> RecurringTransactionFormFieldError.AMOUNT_REQUIRED
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT,
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS -> RecurringTransactionFormFieldError.AMOUNT_INVALID
                }
            }
        }

        // 2. Category validation
        var categoryError: RecurringTransactionFormFieldError? = null
        if (categoryId == null || categoryId.value.isBlank()) {
            categoryError = RecurringTransactionFormFieldError.CATEGORY_REQUIRED
        } else if (categories.isNotEmpty()) {
            val matchingCategory = categories.firstOrNull { it.id == categoryId }
            if (matchingCategory == null) {
                categoryError = RecurringTransactionFormFieldError.CATEGORY_REQUIRED
            } else if (matchingCategory.type != type) {
                categoryError = RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH
            }
        }

        // 3. Interval validation
        val parsedInterval = intervalInput.trim().toIntOrNull()
        val intervalError: RecurringTransactionFormFieldError? = when {
            parsedInterval == null -> RecurringTransactionFormFieldError.INTERVAL_INVALID
            parsedInterval <= 0 -> RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE
            else -> null
        }

        // 4. Start date validation
        val startDateError: RecurringTransactionFormFieldError? = if (startDate == null) {
            RecurringTransactionFormFieldError.START_DATE_REQUIRED
        } else {
            null
        }

        // 5. End date validation
        val endDateError: RecurringTransactionFormFieldError? = if (startDate != null && endDate != null && endDate < startDate) {
            RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE
        } else {
            null
        }

        // 6. Description validation
        val normalizedDescription = description.trim().ifBlank { null }
        val descriptionError: RecurringTransactionFormFieldError? = if (normalizedDescription != null && normalizedDescription.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            RecurringTransactionFormFieldError.DESCRIPTION_TOO_LONG
        } else {
            null
        }

        val errors = RecurringTransactionFormInputErrors(
            amountError = amountError,
            categoryError = categoryError,
            intervalError = intervalError,
            startDateError = startDateError,
            endDateError = endDateError,
            descriptionError = descriptionError,
        )

        if (errors.hasErrors || amountMinor == null || categoryId == null || parsedInterval == null || startDate == null) {
            return RecurringTransactionFormNormalizationResult.Invalid(errors)
        }

        return RecurringTransactionFormNormalizationResult.Valid(
            RecurringTransactionFormDraft(
                recurringTransactionId = recurringTransactionId,
                amount = Money(amountMinor, currency),
                type = type,
                categoryId = categoryId,
                description = normalizedDescription,
                paymentMethod = paymentMethod,
                frequency = frequency,
                interval = parsedInterval,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    companion object {
        /**
         * Tip güvenli [RecurringTransactionFormDraft] taslağından form girdi modelini tohumlar (seed eder).
         */
        fun fromDraft(draft: RecurringTransactionFormDraft): RecurringTransactionFormInput =
            RecurringTransactionFormInput(
                recurringTransactionId = draft.recurringTransactionId,
                amountInput = MoneyFormatter.formatMinorUnitsToInputText(draft.amount.amountMinor, draft.amount.currency),
                currency = draft.amount.currency,
                type = draft.type,
                categoryId = draft.categoryId,
                description = draft.description.orEmpty(),
                paymentMethod = draft.paymentMethod,
                frequency = draft.frequency,
                intervalInput = draft.interval.toString(),
                startDate = draft.startDate,
                endDate = draft.endDate,
            )
    }
}
