@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.goal

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.budget_month_january
        2 -> Res.string.budget_month_february
        3 -> Res.string.budget_month_march
        4 -> Res.string.budget_month_april
        5 -> Res.string.budget_month_may
        6 -> Res.string.budget_month_june
        7 -> Res.string.budget_month_july
        8 -> Res.string.budget_month_august
        9 -> Res.string.budget_month_september
        10 -> Res.string.budget_month_october
        11 -> Res.string.budget_month_november
        12 -> Res.string.budget_month_december
        else -> Res.string.budget_month_january
    }
    return stringResource(res)
}

suspend fun resolveLocalizedMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.budget_month_january
        2 -> Res.string.budget_month_february
        3 -> Res.string.budget_month_march
        4 -> Res.string.budget_month_april
        5 -> Res.string.budget_month_may
        6 -> Res.string.budget_month_june
        7 -> Res.string.budget_month_july
        8 -> Res.string.budget_month_august
        9 -> Res.string.budget_month_september
        10 -> Res.string.budget_month_october
        11 -> Res.string.budget_month_november
        12 -> Res.string.budget_month_december
        else -> Res.string.budget_month_january
    }
    return getString(res)
}

@Composable
fun localizedShortMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.budget_short_month_january
        2 -> Res.string.budget_short_month_february
        3 -> Res.string.budget_short_month_march
        4 -> Res.string.budget_short_month_april
        5 -> Res.string.budget_short_month_may
        6 -> Res.string.budget_short_month_june
        7 -> Res.string.budget_short_month_july
        8 -> Res.string.budget_short_month_august
        9 -> Res.string.budget_short_month_september
        10 -> Res.string.budget_short_month_october
        11 -> Res.string.budget_short_month_november
        12 -> Res.string.budget_short_month_december
        else -> Res.string.budget_short_month_january
    }
    return stringResource(res)
}

suspend fun resolveLocalizedShortMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.budget_short_month_january
        2 -> Res.string.budget_short_month_february
        3 -> Res.string.budget_short_month_march
        4 -> Res.string.budget_short_month_april
        5 -> Res.string.budget_short_month_may
        6 -> Res.string.budget_short_month_june
        7 -> Res.string.budget_short_month_july
        8 -> Res.string.budget_short_month_august
        9 -> Res.string.budget_short_month_september
        10 -> Res.string.budget_short_month_october
        11 -> Res.string.budget_short_month_november
        12 -> Res.string.budget_short_month_december
        else -> Res.string.budget_short_month_january
    }
    return getString(res)
}

@Composable
fun LocalDate.toLocalizedReadableDate(): String {
    val monthName = localizedMonthName(monthNumber)
    val languageCode = stringResource(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $monthName $year"
    } else {
        "$monthName $day, $year"
    }
}

suspend fun LocalDate.resolveLocalizedReadableDate(): String {
    val monthName = resolveLocalizedMonthName(monthNumber)
    val languageCode = getString(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $monthName $year"
    } else {
        "$monthName $day, $year"
    }
}

fun GoalStatusFilter.toLocalizedResource(): StringResource = when (this) {
    GoalStatusFilter.ALL -> Res.string.goal_filter_all
    GoalStatusFilter.ACTIVE -> Res.string.goal_filter_active
    GoalStatusFilter.ACHIEVED -> Res.string.goal_filter_achieved
}

@Composable
fun GoalStatusFilter.toLocalizedGoalFilterLabel(): String = stringResource(toLocalizedResource())

fun GoalStatusFilter.toLocalizedSummaryTitleResource(): StringResource = when (this) {
    GoalStatusFilter.ALL -> Res.string.goal_summary_title_all
    GoalStatusFilter.ACTIVE -> Res.string.goal_summary_title_active
    GoalStatusFilter.ACHIEVED -> Res.string.goal_summary_title_achieved
}

@Composable
fun GoalStatusFilter.toLocalizedSummaryTitle(): String = stringResource(toLocalizedSummaryTitleResource())

fun GoalStatusFilter.toLocalizedSummaryCountResource(): StringResource = when (this) {
    GoalStatusFilter.ALL -> Res.string.goal_summary_count_all
    GoalStatusFilter.ACTIVE -> Res.string.goal_summary_count_active
    GoalStatusFilter.ACHIEVED -> Res.string.goal_summary_count_achieved
}

@Composable
fun GoalStatusFilter.toLocalizedSummaryCountLabel(): String = stringResource(toLocalizedSummaryCountResource())

fun GoalStatus.toLocalizedResource(): StringResource = when (this) {
    GoalStatus.IN_PROGRESS -> Res.string.goal_card_status_active
    GoalStatus.ACHIEVED -> Res.string.goal_card_status_achieved
}

@Composable
fun GoalStatus.toLocalizedGoalStatusLabel(): String = stringResource(toLocalizedResource())

fun GoalDetailStatus.toLocalizedResource(): StringResource = when (this) {
    GoalDetailStatus.ACTIVE -> Res.string.goal_card_status_active
    GoalDetailStatus.ACHIEVED -> Res.string.goal_card_status_achieved
    GoalDetailStatus.PAST_DUE -> Res.string.goal_card_badge_past_due
}

@Composable
fun GoalDetailStatus.toLocalizedDetailStatusLabel(): String = stringResource(toLocalizedResource())

@Composable
fun GoalInsightPayload.resolveTitleAndDescription(): Pair<String, String> = when (this) {
    is GoalInsightPayload.ClosestGoal -> {
        val title = stringResource(Res.string.goal_insight_closest_title, goalName)
        val progressText = formatLocalizedRateBasisPoints(progressBasisPoints.value)
        val remainingText = remainingAmount.toLocalizedFormatted()
        val desc = stringResource(Res.string.goal_insight_closest_desc, progressText, remainingText)
        Pair(title, desc)
    }
    is GoalInsightPayload.CompletionCount -> {
        val title = pluralStringResource(Res.plurals.goal_count_plural, totalCount, totalCount, achievedCount)
        val desc = if (activeCount == 0) {
            stringResource(Res.string.goal_insight_completion_desc_empty)
        } else {
            pluralStringResource(Res.plurals.goal_active_count_plural, activeCount, activeCount)
        }
        Pair(title, desc)
    }
    is GoalInsightPayload.PastDateCount -> {
        val title = pluralStringResource(Res.plurals.goal_past_date_count_plural, pastDateCount, pastDateCount)
        val desc = stringResource(Res.string.goal_insight_past_date_desc)
        Pair(title, desc)
    }
}

@Composable
fun GoalDetailInsight.resolveLocalizedText(): String = when (this) {
    GoalDetailInsight.Achieved -> stringResource(Res.string.goal_detail_insight_achieved)
    GoalDetailInsight.ZeroContribution -> stringResource(Res.string.goal_detail_insight_zero)
    is GoalDetailInsight.PastDue -> stringResource(Res.string.goal_detail_insight_past_due, remaining.toLocalizedFormatted())
    is GoalDetailInsight.InProgress -> stringResource(
        Res.string.goal_detail_insight_in_progress,
        formatLocalizedRateBasisPoints(progress.value),
        remaining.toLocalizedFormatted(),
    )
}

fun GoalFormFieldError.toLocalizedResource(): StringResource = when (this) {
    GoalFormFieldError.NAME_REQUIRED -> Res.string.goal_error_name_required
    GoalFormFieldError.NAME_TOO_LONG -> Res.string.goal_error_name_too_long
    GoalFormFieldError.TARGET_AMOUNT_REQUIRED -> Res.string.goal_error_target_amount_required
    GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE -> Res.string.goal_error_target_amount_non_positive
    GoalFormFieldError.TARGET_AMOUNT_INVALID -> Res.string.goal_error_target_amount_invalid
    GoalFormFieldError.INITIAL_AMOUNT_NEGATIVE -> Res.string.goal_error_initial_amount_negative
    GoalFormFieldError.INITIAL_AMOUNT_INVALID -> Res.string.goal_error_initial_amount_invalid
    GoalFormFieldError.INITIAL_AMOUNT_NOT_ALLOWED_IN_EDIT -> Res.string.goal_error_initial_amount_not_allowed_in_edit
    GoalFormFieldError.TARGET_DATE_REQUIRED -> Res.string.goal_error_target_date_required
    GoalFormFieldError.COLOR_INVALID -> Res.string.goal_error_color_invalid
    GoalFormFieldError.CURRENCY_LOCKED_IN_EDIT -> Res.string.goal_error_currency_locked_in_edit
}

@Composable
fun GoalFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

fun GoalContributionFormFieldError.toLocalizedResource(): StringResource = when (this) {
    GoalContributionFormFieldError.AMOUNT_REQUIRED -> Res.string.goal_contrib_error_amount_required
    GoalContributionFormFieldError.AMOUNT_NON_POSITIVE -> Res.string.goal_contrib_error_amount_non_positive
    GoalContributionFormFieldError.AMOUNT_INVALID -> Res.string.goal_contrib_error_amount_invalid
    GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT -> Res.string.goal_contrib_error_exceeds_current
    GoalContributionFormFieldError.DATE_REQUIRED -> Res.string.goal_contrib_error_date_required
    GoalContributionFormFieldError.NOTE_TOO_LONG -> Res.string.goal_contrib_error_note_too_long
    GoalContributionFormFieldError.CURRENCY_MISMATCH -> Res.string.goal_contrib_error_currency_mismatch
    GoalContributionFormFieldError.PARENT_GOAL_MISSING -> Res.string.goal_contrib_error_parent_missing
}

@Composable
fun GoalContributionFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

@Composable
fun goalFormLoadingText(): String = stringResource(Res.string.goal_form_route_loading)

@Composable
fun goalFormNotFoundTitleText(): String = stringResource(Res.string.goal_form_route_not_found_title)

@Composable
fun goalFormNotFoundDescText(): String = stringResource(Res.string.goal_form_route_not_found_desc)

@Composable
fun goalContributionNotFoundDescText(): String = stringResource(Res.string.goal_contrib_route_not_found_desc)

@Composable
fun goalFormBackActionText(): String = stringResource(Res.string.goal_form_route_back)

@Composable
fun goalFormErrorTitleText(): String = stringResource(Res.string.goal_form_route_error_title)

@Composable
fun goalDialogOkText(): String = stringResource(Res.string.goal_dialog_ok)

@Composable
fun goalDialogCancelText(): String = stringResource(Res.string.goal_delete_dialog_cancel)
