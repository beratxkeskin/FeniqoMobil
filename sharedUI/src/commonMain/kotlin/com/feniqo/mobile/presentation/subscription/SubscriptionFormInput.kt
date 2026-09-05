package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Abonelik formu alan bazlı doğrulama hataları.
 */
enum class SubscriptionFormFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    AMOUNT_TOO_LARGE,
    CATEGORY_NOT_FOUND,
    CATEGORY_TYPE_MISMATCH,
    INTERVAL_INVALID,
    INTERVAL_NON_POSITIVE,
    START_DATE_REQUIRED,
    START_DATE_AFTER_NEXT_RENEWAL,
    END_DATE_BEFORE_START_DATE,
    END_DATE_BEFORE_NEXT_RENEWAL,
}

/**
 * Form girdi hatalarının toplu konteyneri.
 */
data class SubscriptionFormInputErrors(
    val nameError: SubscriptionFormFieldError? = null,
    val amountError: SubscriptionFormFieldError? = null,
    val categoryError: SubscriptionFormFieldError? = null,
    val intervalError: SubscriptionFormFieldError? = null,
    val startDateError: SubscriptionFormFieldError? = null,
    val endDateError: SubscriptionFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = nameError != null ||
                amountError != null ||
                categoryError != null ||
                intervalError != null ||
                startDateError != null ||
                endDateError != null
}

/**
 * Normalizasyon ve doğrulama sonucu kapalı sözleşmesi.
 */
sealed interface SubscriptionFormNormalizationResult {
    data class Valid(val draft: SubscriptionFormDraft) : SubscriptionFormNormalizationResult
    data class Invalid(val errors: SubscriptionFormInputErrors) : SubscriptionFormNormalizationResult
}

/**
 * Kullanıcı metin girdilerini ve UI seçimlerini tutan saf abonelik form girdi modelidir.
 */
data class SubscriptionFormInput(
    val subscriptionId: EntityId? = null,
    val nameInput: String = "",
    val amountInput: String = "",
    val currency: Currency = Currency.TRY,
    val categoryId: EntityId? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalInput: String = "1",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val nextRenewalDate: LocalDate? = null,
) {
    val isEditMode: Boolean get() = subscriptionId != null
    val isCreateMode: Boolean get() = subscriptionId == null

    /**
     * Kullanıcı girdisini doğrular ve geçerliyse [SubscriptionFormDraft] üretir.
     */
    fun toDraft(
        categories: List<Category> = emptyList(),
    ): SubscriptionFormNormalizationResult {
        // 1. Name validation
        val trimmedName = nameInput.trim()
        val nameError = when {
            trimmedName.isBlank() -> SubscriptionFormFieldError.NAME_REQUIRED
            trimmedName.length > Subscription.MAX_NAME_LENGTH -> SubscriptionFormFieldError.NAME_TOO_LONG
            else -> null
        }

        // 2. Amount validation
        val amountMinor: Long?
        var amountError: SubscriptionFormFieldError? = null
        when (val parseResult = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                if (parseResult.amountMinor <= 0) {
                    amountMinor = null
                    amountError = SubscriptionFormFieldError.AMOUNT_NON_POSITIVE
                } else {
                    amountMinor = parseResult.amountMinor
                }
            }
            is MoneyAmountParser.ParseResult.Invalid -> {
                amountMinor = null
                amountError = when (parseResult.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> SubscriptionFormFieldError.AMOUNT_REQUIRED
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> SubscriptionFormFieldError.AMOUNT_NON_POSITIVE
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> SubscriptionFormFieldError.AMOUNT_TOO_LARGE
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT,
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS -> SubscriptionFormFieldError.AMOUNT_INVALID
                }
            }
        }

        // 3. Category validation (optional, but if provided, must not be income and must exist if categories provided)
        val categoryError = if (categoryId != null && categories.isNotEmpty()) {
            val matchingCategory = categories.firstOrNull { it.id == categoryId }
            when {
                matchingCategory == null -> SubscriptionFormFieldError.CATEGORY_NOT_FOUND
                matchingCategory.type != TransactionType.EXPENSE -> SubscriptionFormFieldError.CATEGORY_TYPE_MISMATCH
                else -> null
            }
        } else {
            null
        }

        // 4. Interval validation
        val interval: Int?
        val intervalError: SubscriptionFormFieldError?
        val parsedInterval = intervalInput.trim().toIntOrNull()
        if (parsedInterval == null) {
            interval = null
            intervalError = SubscriptionFormFieldError.INTERVAL_INVALID
        } else if (parsedInterval <= 0) {
            interval = null
            intervalError = SubscriptionFormFieldError.INTERVAL_NON_POSITIVE
        } else {
            interval = parsedInterval
            intervalError = null
        }

        // 5. Start Date validation (in edit mode, startDate cannot be after nextRenewalDate)
        val startDateError = when {
            startDate == null -> SubscriptionFormFieldError.START_DATE_REQUIRED
            isEditMode && nextRenewalDate != null && startDate > nextRenewalDate -> SubscriptionFormFieldError.START_DATE_AFTER_NEXT_RENEWAL
            else -> null
        }

        // 6. End Date validation (endDate cannot be before startDate, and in edit mode cannot be before nextRenewalDate)
        val endDateError = when {
            startDate != null && endDate != null && endDate < startDate -> SubscriptionFormFieldError.END_DATE_BEFORE_START_DATE
            isEditMode && nextRenewalDate != null && endDate != null && endDate < nextRenewalDate -> SubscriptionFormFieldError.END_DATE_BEFORE_NEXT_RENEWAL
            else -> null
        }


        val errors = SubscriptionFormInputErrors(
            nameError = nameError,
            amountError = amountError,
            categoryError = categoryError,
            intervalError = intervalError,
            startDateError = startDateError,
            endDateError = endDateError,
        )

        if (errors.hasErrors || amountMinor == null || interval == null || startDate == null) {
            return SubscriptionFormNormalizationResult.Invalid(errors)
        }

        val resolvedNextRenewalDate = nextRenewalDate ?: startDate

        val draft = SubscriptionFormDraft(
            subscriptionId = subscriptionId,
            name = trimmedName,
            amount = Money(amountMinor = amountMinor, currency = currency),
            categoryId = categoryId,
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = endDate,
            nextRenewalDate = resolvedNextRenewalDate,
        )

        return SubscriptionFormNormalizationResult.Valid(draft)
    }

    companion object {
        /**
         * Taslaktan form girdisi tohumlar.
         */
        fun fromDraft(draft: SubscriptionFormDraft): SubscriptionFormInput =
            SubscriptionFormInput(
                subscriptionId = draft.subscriptionId,
                nameInput = draft.name,
                amountInput = MoneyFormatter.formatMinorUnitsToInputText(draft.amount.amountMinor, draft.amount.currency),
                currency = draft.amount.currency,
                categoryId = draft.categoryId,
                frequency = draft.frequency,
                intervalInput = draft.interval.toString(),
                startDate = draft.startDate,
                endDate = draft.endDate,
                nextRenewalDate = draft.nextRenewalDate,
            )
    }
}

