package com.feniqo.mobile.presentation.subscription

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.subscription_badge_cancelled
import feniqomobil.sharedui.generated.resources.subscription_badge_expired
import feniqomobil.sharedui.generated.resources.subscription_badge_paused
import feniqomobil.sharedui.generated.resources.subscription_badge_trial
import feniqomobil.sharedui.generated.resources.subscription_common_cancel
import feniqomobil.sharedui.generated.resources.subscription_common_select
import feniqomobil.sharedui.generated.resources.subscription_error_amount_invalid
import feniqomobil.sharedui.generated.resources.subscription_error_amount_non_positive
import feniqomobil.sharedui.generated.resources.subscription_error_amount_required
import feniqomobil.sharedui.generated.resources.subscription_error_amount_too_large
import feniqomobil.sharedui.generated.resources.subscription_error_category_not_found
import feniqomobil.sharedui.generated.resources.subscription_error_category_type_mismatch
import feniqomobil.sharedui.generated.resources.subscription_error_end_date_before_next_renewal
import feniqomobil.sharedui.generated.resources.subscription_error_end_date_before_start_date
import feniqomobil.sharedui.generated.resources.subscription_error_interval_invalid
import feniqomobil.sharedui.generated.resources.subscription_error_interval_non_positive
import feniqomobil.sharedui.generated.resources.subscription_error_name_required
import feniqomobil.sharedui.generated.resources.subscription_error_name_too_long
import feniqomobil.sharedui.generated.resources.subscription_error_notes_too_long
import feniqomobil.sharedui.generated.resources.subscription_error_start_date_after_next_renewal
import feniqomobil.sharedui.generated.resources.subscription_error_start_date_required
import feniqomobil.sharedui.generated.resources.subscription_error_website_too_long
import feniqomobil.sharedui.generated.resources.subscription_filter_active
import feniqomobil.sharedui.generated.resources.subscription_filter_all
import feniqomobil.sharedui.generated.resources.subscription_filter_cancelled
import feniqomobil.sharedui.generated.resources.subscription_filter_overdue
import feniqomobil.sharedui.generated.resources.subscription_filter_paused
import feniqomobil.sharedui.generated.resources.subscription_filter_trial
import feniqomobil.sharedui.generated.resources.subscription_filter_upcoming
import feniqomobil.sharedui.generated.resources.subscription_frequency_daily
import feniqomobil.sharedui.generated.resources.subscription_frequency_monthly
import feniqomobil.sharedui.generated.resources.subscription_frequency_unit_daily
import feniqomobil.sharedui.generated.resources.subscription_frequency_unit_monthly
import feniqomobil.sharedui.generated.resources.subscription_frequency_unit_weekly
import feniqomobil.sharedui.generated.resources.subscription_frequency_unit_yearly
import feniqomobil.sharedui.generated.resources.subscription_frequency_weekly
import feniqomobil.sharedui.generated.resources.subscription_frequency_yearly
import feniqomobil.sharedui.generated.resources.subscription_insight_paused_savings_badge_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_paused_savings_desc_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_paused_savings_title
import feniqomobil.sharedui.generated.resources.subscription_insight_price_increase_badge_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_price_increase_desc_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_price_increase_title
import feniqomobil.sharedui.generated.resources.subscription_insight_top_cost_badge
import feniqomobil.sharedui.generated.resources.subscription_insight_top_cost_desc
import feniqomobil.sharedui.generated.resources.subscription_insight_top_cost_title
import feniqomobil.sharedui.generated.resources.subscription_insight_trial_badge_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_trial_desc_plural
import feniqomobil.sharedui.generated.resources.subscription_insight_trial_title
import feniqomobil.sharedui.generated.resources.subscription_permission_banner_action
import feniqomobil.sharedui.generated.resources.subscription_permission_banner_desc
import feniqomobil.sharedui.generated.resources.subscription_permission_banner_title
import feniqomobil.sharedui.generated.resources.subscription_recurrence_interval_days_plural
import feniqomobil.sharedui.generated.resources.subscription_recurrence_interval_months_plural
import feniqomobil.sharedui.generated.resources.subscription_recurrence_interval_weeks_plural
import feniqomobil.sharedui.generated.resources.subscription_recurrence_interval_years_plural
import feniqomobil.sharedui.generated.resources.subscription_recurrence_summary_daily_single
import feniqomobil.sharedui.generated.resources.subscription_recurrence_summary_monthly_single
import feniqomobil.sharedui.generated.resources.subscription_recurrence_summary_weekly_single
import feniqomobil.sharedui.generated.resources.subscription_recurrence_summary_yearly_single
import feniqomobil.sharedui.generated.resources.subscription_route_back_action
import feniqomobil.sharedui.generated.resources.subscription_route_error_title
import feniqomobil.sharedui.generated.resources.subscription_route_loading
import feniqomobil.sharedui.generated.resources.subscription_route_not_found_desc
import feniqomobil.sharedui.generated.resources.subscription_route_not_found_title
import feniqomobil.sharedui.generated.resources.subscription_status_due_today
import feniqomobil.sharedui.generated.resources.subscription_status_inactive
import feniqomobil.sharedui.generated.resources.subscription_status_overdue_days_plural
import feniqomobil.sharedui.generated.resources.subscription_status_upcoming_days_plural
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubscriptionFilter.toLocalizedFilterLabel(): String =
    when (this) {
        SubscriptionFilter.ALL -> stringResource(Res.string.subscription_filter_all)
        SubscriptionFilter.ACTIVE -> stringResource(Res.string.subscription_filter_active)
        SubscriptionFilter.UPCOMING -> stringResource(Res.string.subscription_filter_upcoming)
        SubscriptionFilter.OVERDUE -> stringResource(Res.string.subscription_filter_overdue)
        SubscriptionFilter.PAUSED -> stringResource(Res.string.subscription_filter_paused)
        SubscriptionFilter.CANCELLED -> stringResource(Res.string.subscription_filter_cancelled)
        SubscriptionFilter.TRIAL -> stringResource(Res.string.subscription_filter_trial)
    }

@Composable
fun RecurrenceFrequency.toLocalizedFrequencyLabel(): String =
    when (this) {
        RecurrenceFrequency.DAILY -> stringResource(Res.string.subscription_frequency_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(Res.string.subscription_frequency_weekly)
        RecurrenceFrequency.MONTHLY -> stringResource(Res.string.subscription_frequency_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(Res.string.subscription_frequency_yearly)
    }

@Composable
fun RecurrenceFrequency.toLocalizedUnitText(): String =
    when (this) {
        RecurrenceFrequency.DAILY -> stringResource(Res.string.subscription_frequency_unit_daily)
        RecurrenceFrequency.WEEKLY -> stringResource(Res.string.subscription_frequency_unit_weekly)
        RecurrenceFrequency.MONTHLY -> stringResource(Res.string.subscription_frequency_unit_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(Res.string.subscription_frequency_unit_yearly)
    }

@Composable
fun formatLocalizedRecurrenceSummary(
    frequency: RecurrenceFrequency,
    interval: Int,
): String {
    val safeInterval = interval.coerceAtLeast(1)
    return if (safeInterval == 1) {
        when (frequency) {
            RecurrenceFrequency.DAILY -> stringResource(Res.string.subscription_recurrence_summary_daily_single)
            RecurrenceFrequency.WEEKLY -> stringResource(Res.string.subscription_recurrence_summary_weekly_single)
            RecurrenceFrequency.MONTHLY -> stringResource(Res.string.subscription_recurrence_summary_monthly_single)
            RecurrenceFrequency.YEARLY -> stringResource(Res.string.subscription_recurrence_summary_yearly_single)
        }
    } else {
        when (frequency) {
            RecurrenceFrequency.DAILY ->
                pluralStringResource(
                    Res.plurals.subscription_recurrence_interval_days_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.WEEKLY ->
                pluralStringResource(
                    Res.plurals.subscription_recurrence_interval_weeks_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.MONTHLY ->
                pluralStringResource(
                    Res.plurals.subscription_recurrence_interval_months_plural,
                    safeInterval,
                    safeInterval,
                )
            RecurrenceFrequency.YEARLY ->
                pluralStringResource(
                    Res.plurals.subscription_recurrence_interval_years_plural,
                    safeInterval,
                    safeInterval,
                )
        }
    }
}

@Composable
fun toCycleSummaryText(
    frequency: RecurrenceFrequency,
    interval: Int,
): String = formatLocalizedRecurrenceSummary(frequency, interval)

@Composable
fun SubscriptionRenewalStatus.toLocalizedRenewalStatusLabel(): String =
    when (this) {
        SubscriptionRenewalStatus.Inactive -> stringResource(Res.string.subscription_status_inactive)
        is SubscriptionRenewalStatus.Overdue ->
            pluralStringResource(
                Res.plurals.subscription_status_overdue_days_plural,
                daysOverdue.toInt(),
                daysOverdue.toInt(),
            )
        SubscriptionRenewalStatus.DueToday -> stringResource(Res.string.subscription_status_due_today)
        is SubscriptionRenewalStatus.Upcoming ->
            pluralStringResource(
                Res.plurals.subscription_status_upcoming_days_plural,
                daysUntilRenewal.toInt(),
                daysUntilRenewal.toInt(),
            )
        is SubscriptionRenewalStatus.Scheduled ->
            pluralStringResource(
                Res.plurals.subscription_status_upcoming_days_plural,
                daysUntilRenewal.toInt(),
                daysUntilRenewal.toInt(),
            )
    }

@Composable
fun SubscriptionLifecycleStatus.toLocalizedLifecycleBadgeLabel(): String =
    when (this) {
        SubscriptionLifecycleStatus.ACTIVE -> stringResource(Res.string.subscription_filter_active)
        SubscriptionLifecycleStatus.PAUSED -> stringResource(Res.string.subscription_badge_paused)
        SubscriptionLifecycleStatus.CANCELLED -> stringResource(Res.string.subscription_badge_cancelled)
        SubscriptionLifecycleStatus.TRIAL -> stringResource(Res.string.subscription_badge_trial)
        SubscriptionLifecycleStatus.EXPIRED -> stringResource(Res.string.subscription_badge_expired)
    }

@Composable
fun SubscriptionLifecycleStatus.toLocalizedBadgeText(): String = toLocalizedLifecycleBadgeLabel()

@Composable
fun toLocalizedFrequencySummary(
    frequency: RecurrenceFrequency,
    interval: Int,
): String = formatLocalizedRecurrenceSummary(frequency, interval)

@Composable
fun SubscriptionFormFieldError.toLocalizedErrorMessage(): String =
    when (this) {
        SubscriptionFormFieldError.NAME_REQUIRED -> stringResource(Res.string.subscription_error_name_required)
        SubscriptionFormFieldError.NAME_TOO_LONG -> stringResource(Res.string.subscription_error_name_too_long)
        SubscriptionFormFieldError.AMOUNT_REQUIRED -> stringResource(Res.string.subscription_error_amount_required)
        SubscriptionFormFieldError.AMOUNT_INVALID -> stringResource(Res.string.subscription_error_amount_invalid)
        SubscriptionFormFieldError.AMOUNT_NON_POSITIVE -> stringResource(Res.string.subscription_error_amount_non_positive)
        SubscriptionFormFieldError.AMOUNT_TOO_LARGE -> stringResource(Res.string.subscription_error_amount_too_large)
        SubscriptionFormFieldError.CATEGORY_NOT_FOUND -> stringResource(Res.string.subscription_error_category_not_found)
        SubscriptionFormFieldError.CATEGORY_TYPE_MISMATCH -> stringResource(Res.string.subscription_error_category_type_mismatch)
        SubscriptionFormFieldError.INTERVAL_INVALID -> stringResource(Res.string.subscription_error_interval_invalid)
        SubscriptionFormFieldError.INTERVAL_NON_POSITIVE -> stringResource(Res.string.subscription_error_interval_non_positive)
        SubscriptionFormFieldError.START_DATE_REQUIRED -> stringResource(Res.string.subscription_error_start_date_required)
        SubscriptionFormFieldError.START_DATE_AFTER_NEXT_RENEWAL ->
            stringResource(Res.string.subscription_error_start_date_after_next_renewal)
        SubscriptionFormFieldError.END_DATE_BEFORE_START_DATE ->
            stringResource(Res.string.subscription_error_end_date_before_start_date)
        SubscriptionFormFieldError.END_DATE_BEFORE_NEXT_RENEWAL ->
            stringResource(Res.string.subscription_error_end_date_before_next_renewal)
        SubscriptionFormFieldError.WEBSITE_URL_TOO_LONG -> stringResource(Res.string.subscription_error_website_too_long)
        SubscriptionFormFieldError.NOTES_TOO_LONG -> stringResource(Res.string.subscription_error_notes_too_long)
    }

@Composable
fun SubscriptionFormFieldError.toLocalizedMessage(): String = toLocalizedErrorMessage()

@Composable
fun SubscriptionInsightPayload.resolveTitleAndDescription(): Pair<String, String> =
    when (this) {
        is SubscriptionInsightPayload.TrialEndingSoon -> {
            val title = stringResource(Res.string.subscription_insight_trial_title)
            val formattedDate = trialEndDate.toLocalizedReadableDate()
            val desc =
                pluralStringResource(
                    Res.plurals.subscription_insight_trial_desc_plural,
                    daysRemaining.toInt(),
                    subscriptionName,
                    daysRemaining.toInt(),
                    formattedDate,
                )
            Pair(title, desc)
        }
        is SubscriptionInsightPayload.PriceIncreases -> {
            val title = stringResource(Res.string.subscription_insight_price_increase_title)
            val desc =
                pluralStringResource(
                    Res.plurals.subscription_insight_price_increase_desc_plural,
                    count,
                    count,
                )
            Pair(title, desc)
        }
        is SubscriptionInsightPayload.PausedSavings -> {
            val title = stringResource(Res.string.subscription_insight_paused_savings_title)
            val formattedSavings = monthlyEstimatedSavings.toLocalizedFormatted()
            val desc =
                pluralStringResource(
                    Res.plurals.subscription_insight_paused_savings_desc_plural,
                    pausedCount,
                    pausedCount,
                    formattedSavings,
                )
            Pair(title, desc)
        }
        is SubscriptionInsightPayload.TopCost -> {
            val title = stringResource(Res.string.subscription_insight_top_cost_title)
            val formattedCost = monthlyEstimated.toLocalizedFormatted()
            val desc =
                stringResource(
                    Res.string.subscription_insight_top_cost_desc,
                    subscriptionName,
                    formattedCost,
                )
            Pair(title, desc)
        }
    }

@Composable
fun SubscriptionInsightPayload.resolveBadgeText(): String? =
    when (this) {
        is SubscriptionInsightPayload.TrialEndingSoon ->
            pluralStringResource(
                Res.plurals.subscription_insight_trial_badge_plural,
                daysRemaining.toInt(),
                daysRemaining.toInt(),
            )
        is SubscriptionInsightPayload.PriceIncreases ->
            pluralStringResource(
                Res.plurals.subscription_insight_price_increase_badge_plural,
                count,
                count,
            )
        is SubscriptionInsightPayload.PausedSavings ->
            pluralStringResource(
                Res.plurals.subscription_insight_paused_savings_badge_plural,
                pausedCount,
                pausedCount,
            )
        is SubscriptionInsightPayload.TopCost ->
            stringResource(Res.string.subscription_insight_top_cost_badge)
    }

@Composable
fun subscriptionRouteLoadingText(): String = stringResource(Res.string.subscription_route_loading)

@Composable
fun subscriptionRouteNotFoundTitleText(): String = stringResource(Res.string.subscription_route_not_found_title)

@Composable
fun subscriptionRouteNotFoundDescText(): String = stringResource(Res.string.subscription_route_not_found_desc)

@Composable
fun subscriptionRouteBackActionText(): String = stringResource(Res.string.subscription_route_back_action)

@Composable
fun subscriptionRouteErrorTitleText(): String = stringResource(Res.string.subscription_route_error_title)

@Composable
fun subscriptionCommonSelectText(): String = stringResource(Res.string.subscription_common_select)

@Composable
fun subscriptionCommonCancelText(): String = stringResource(Res.string.subscription_common_cancel)

@Composable
fun subscriptionPermissionBannerTitleText(): String = stringResource(Res.string.subscription_permission_banner_title)

@Composable
fun subscriptionPermissionBannerDescText(): String = stringResource(Res.string.subscription_permission_banner_desc)

@Composable
fun subscriptionPermissionBannerActionText(): String = stringResource(Res.string.subscription_permission_banner_action)
