package com.feniqo.mobile.presentation.recurring

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.recurring_action_dismiss
import feniqomobil.sharedui.generated.resources.recurring_action_select
import feniqomobil.sharedui.generated.resources.recurring_banner_daily_single
import feniqomobil.sharedui.generated.resources.recurring_banner_days_plural
import feniqomobil.sharedui.generated.resources.recurring_banner_monthly_single
import feniqomobil.sharedui.generated.resources.recurring_banner_months_plural
import feniqomobil.sharedui.generated.resources.recurring_banner_paused
import feniqomobil.sharedui.generated.resources.recurring_banner_weekly_single
import feniqomobil.sharedui.generated.resources.recurring_banner_weeks_plural
import feniqomobil.sharedui.generated.resources.recurring_banner_yearly_single
import feniqomobil.sharedui.generated.resources.recurring_banner_years_plural
import feniqomobil.sharedui.generated.resources.recurring_card_ended
import feniqomobil.sharedui.generated.resources.recurring_card_next_date
import feniqomobil.sharedui.generated.resources.recurring_category_unknown
import feniqomobil.sharedui.generated.resources.recurring_error_amount_invalid
import feniqomobil.sharedui.generated.resources.recurring_error_amount_non_positive
import feniqomobil.sharedui.generated.resources.recurring_error_amount_required
import feniqomobil.sharedui.generated.resources.recurring_error_amount_too_large
import feniqomobil.sharedui.generated.resources.recurring_error_category_required
import feniqomobil.sharedui.generated.resources.recurring_error_category_type_mismatch
import feniqomobil.sharedui.generated.resources.recurring_error_description_too_long
import feniqomobil.sharedui.generated.resources.recurring_error_end_date_before_start_date
import feniqomobil.sharedui.generated.resources.recurring_error_interval_invalid
import feniqomobil.sharedui.generated.resources.recurring_error_interval_non_positive
import feniqomobil.sharedui.generated.resources.recurring_error_start_date_required
import feniqomobil.sharedui.generated.resources.recurring_frequency_daily
import feniqomobil.sharedui.generated.resources.recurring_frequency_monthly
import feniqomobil.sharedui.generated.resources.recurring_frequency_weekly
import feniqomobil.sharedui.generated.resources.recurring_frequency_yearly
import feniqomobil.sharedui.generated.resources.recurring_recurrence_interval_days_plural
import feniqomobil.sharedui.generated.resources.recurring_recurrence_interval_months_plural
import feniqomobil.sharedui.generated.resources.recurring_recurrence_interval_weeks_plural
import feniqomobil.sharedui.generated.resources.recurring_recurrence_interval_years_plural
import feniqomobil.sharedui.generated.resources.recurring_recurrence_summary_daily_single
import feniqomobil.sharedui.generated.resources.recurring_recurrence_summary_monthly_single
import feniqomobil.sharedui.generated.resources.recurring_recurrence_summary_weekly_single
import feniqomobil.sharedui.generated.resources.recurring_recurrence_summary_yearly_single
import feniqomobil.sharedui.generated.resources.recurring_route_back_action
import feniqomobil.sharedui.generated.resources.recurring_route_error_title
import feniqomobil.sharedui.generated.resources.recurring_route_loading
import feniqomobil.sharedui.generated.resources.recurring_route_not_found_desc
import feniqomobil.sharedui.generated.resources.recurring_route_not_found_title
import feniqomobil.sharedui.generated.resources.recurring_status_paused
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun RecurringTransactionDisplayModel.resolveDisplayTitle(): String {
    val trimmedDesc = description?.trim()
    if (!trimmedDesc.isNullOrBlank()) {
        return trimmedDesc
    }
    return if (!isCategoryMissing && !categoryName.isNullOrBlank()) {
        categoryName
    } else {
        stringResource(Res.string.recurring_category_unknown)
    }
}

@Composable
fun RecurringTransactionDisplayModel.resolveNextDateOrStatusText(): String =
    when {
        isPaused -> stringResource(Res.string.recurring_status_paused)
        nextOccurrenceDate != null ->
            stringResource(
                Res.string.recurring_card_next_date,
                nextOccurrenceDate.toLocalizedReadableDate(),
            )
        else -> stringResource(Res.string.recurring_card_ended)
    }

@Composable
fun formatLocalizedRecurringSignedAmount(
    amount: Money,
    type: TransactionType,
): String {
    val sign = if (type == TransactionType.EXPENSE) "−" else "+"
    return "$sign${amount.toLocalizedFormatted()}"
}

@Composable
fun formatLocalizedRecurrenceSummary(
    frequency: RecurrenceFrequency,
    interval: Int,
): String {
    val safeInterval = interval.coerceAtLeast(1)
    return if (safeInterval == 1) {
        when (frequency) {
            RecurrenceFrequency.DAILY -> stringResource(Res.string.recurring_recurrence_summary_daily_single)
            RecurrenceFrequency.WEEKLY -> stringResource(Res.string.recurring_recurrence_summary_weekly_single)
            RecurrenceFrequency.MONTHLY -> stringResource(Res.string.recurring_recurrence_summary_monthly_single)
            RecurrenceFrequency.YEARLY -> stringResource(Res.string.recurring_recurrence_summary_yearly_single)
        }
    } else {
        when (frequency) {
            RecurrenceFrequency.DAILY ->
                pluralStringResource(
                    Res.plurals.recurring_recurrence_interval_days_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.WEEKLY ->
                pluralStringResource(
                    Res.plurals.recurring_recurrence_interval_weeks_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.MONTHLY ->
                pluralStringResource(
                    Res.plurals.recurring_recurrence_interval_months_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.YEARLY ->
                pluralStringResource(
                    Res.plurals.recurring_recurrence_interval_years_plural,
                    safeInterval,
                    safeInterval,
                )
        }
    }
}

@Composable
fun formatLocalizedRecurringBannerText(
    frequency: RecurrenceFrequency,
    interval: Int,
    isPaused: Boolean,
): String {
    if (isPaused) {
        return stringResource(Res.string.recurring_banner_paused)
    }
    val safeInterval = interval.coerceAtLeast(1)
    return if (safeInterval == 1) {
        when (frequency) {
            RecurrenceFrequency.DAILY -> stringResource(Res.string.recurring_banner_daily_single)
            RecurrenceFrequency.WEEKLY -> stringResource(Res.string.recurring_banner_weekly_single)
            RecurrenceFrequency.MONTHLY -> stringResource(Res.string.recurring_banner_monthly_single)
            RecurrenceFrequency.YEARLY -> stringResource(Res.string.recurring_banner_yearly_single)
        }
    } else {
        when (frequency) {
            RecurrenceFrequency.DAILY ->
                pluralStringResource(
                    Res.plurals.recurring_banner_days_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.WEEKLY ->
                pluralStringResource(
                    Res.plurals.recurring_banner_weeks_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.MONTHLY ->
                pluralStringResource(
                    Res.plurals.recurring_banner_months_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.YEARLY ->
                pluralStringResource(
                    Res.plurals.recurring_banner_years_plural,
                    safeInterval,
                    safeInterval,
                )
        }
    }
}

@Composable
fun RecurrenceFrequency.toLocalizedFrequencyLabel(): String =
    when (this) {
        RecurrenceFrequency.DAILY -> stringResource(Res.string.recurring_frequency_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(Res.string.recurring_frequency_weekly)
        RecurrenceFrequency.MONTHLY -> stringResource(Res.string.recurring_frequency_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(Res.string.recurring_frequency_yearly)
    }

@Composable
fun RecurringTransactionFormFieldError.toLocalizedMessage(): String =
    when (this) {
        RecurringTransactionFormFieldError.AMOUNT_REQUIRED ->
            stringResource(Res.string.recurring_error_amount_required)
        RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE ->
            stringResource(Res.string.recurring_error_amount_non_positive)
        RecurringTransactionFormFieldError.AMOUNT_INVALID ->
            stringResource(Res.string.recurring_error_amount_invalid)
        RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE ->
            stringResource(Res.string.recurring_error_amount_too_large)
        RecurringTransactionFormFieldError.CATEGORY_REQUIRED ->
            stringResource(Res.string.recurring_error_category_required)
        RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH ->
            stringResource(Res.string.recurring_error_category_type_mismatch)
        RecurringTransactionFormFieldError.INTERVAL_INVALID ->
            stringResource(Res.string.recurring_error_interval_invalid)
        RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE ->
            stringResource(Res.string.recurring_error_interval_non_positive)
        RecurringTransactionFormFieldError.START_DATE_REQUIRED ->
            stringResource(Res.string.recurring_error_start_date_required)
        RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE ->
            stringResource(Res.string.recurring_error_end_date_before_start_date)
        RecurringTransactionFormFieldError.DESCRIPTION_TOO_LONG ->
            stringResource(
                Res.string.recurring_error_description_too_long,
                Transaction.MAX_DESCRIPTION_LENGTH,
            )
    }

@Composable
fun recurringRouteLoadingText(): String = stringResource(Res.string.recurring_route_loading)

@Composable
fun recurringRouteNotFoundTitleText(): String = stringResource(Res.string.recurring_route_not_found_title)

@Composable
fun recurringRouteNotFoundDescText(): String = stringResource(Res.string.recurring_route_not_found_desc)

@Composable
fun recurringRouteBackActionText(): String = stringResource(Res.string.recurring_route_back_action)

@Composable
fun recurringRouteErrorTitleText(): String = stringResource(Res.string.recurring_route_error_title)

@Composable
fun recurringActionSelectText(): String = stringResource(Res.string.recurring_action_select)

@Composable
fun recurringActionDismissText(): String = stringResource(Res.string.recurring_action_dismiss)
