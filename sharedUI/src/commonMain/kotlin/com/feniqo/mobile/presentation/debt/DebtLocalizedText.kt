package com.feniqo.mobile.presentation.debt

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedShortReadableDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.debt_date_picker_collection_title
import feniqomobil.sharedui.generated.resources.debt_date_picker_default_title
import feniqomobil.sharedui.generated.resources.debt_date_picker_payment_title
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_default_name
import feniqomobil.sharedui.generated.resources.debt_due_status_due_soon
import feniqomobil.sharedui.generated.resources.debt_due_status_due_today
import feniqomobil.sharedui.generated.resources.debt_due_status_on_time
import feniqomobil.sharedui.generated.resources.debt_due_status_overdue
import feniqomobil.sharedui.generated.resources.debt_due_status_settled
import feniqomobil.sharedui.generated.resources.debt_error_amount_invalid
import feniqomobil.sharedui.generated.resources.debt_error_amount_non_positive
import feniqomobil.sharedui.generated.resources.debt_error_amount_required
import feniqomobil.sharedui.generated.resources.debt_error_currency_locked_in_edit
import feniqomobil.sharedui.generated.resources.debt_error_description_too_long
import feniqomobil.sharedui.generated.resources.debt_error_due_date_required
import feniqomobil.sharedui.generated.resources.debt_error_title_required
import feniqomobil.sharedui.generated.resources.debt_error_title_too_long
import feniqomobil.sharedui.generated.resources.debt_form_route_not_found_desc
import feniqomobil.sharedui.generated.resources.debt_insight_all_settled_message
import feniqomobil.sharedui.generated.resources.debt_insight_all_settled_title
import feniqomobil.sharedui.generated.resources.debt_insight_overdue_message_plural
import feniqomobil.sharedui.generated.resources.debt_insight_overdue_title
import feniqomobil.sharedui.generated.resources.debt_insight_receivable_message
import feniqomobil.sharedui.generated.resources.debt_insight_receivable_title
import feniqomobil.sharedui.generated.resources.debt_insight_single_tracking_message
import feniqomobil.sharedui.generated.resources.debt_insight_single_tracking_title
import feniqomobil.sharedui.generated.resources.debt_insight_snowball_message_plural
import feniqomobil.sharedui.generated.resources.debt_insight_snowball_title
import feniqomobil.sharedui.generated.resources.debt_net_status_balanced
import feniqomobil.sharedui.generated.resources.debt_net_status_debt_exceeds
import feniqomobil.sharedui.generated.resources.debt_net_status_receivable_exceeds
import feniqomobil.sharedui.generated.resources.debt_payment_error_already_settled
import feniqomobil.sharedui.generated.resources.debt_payment_error_amount_invalid
import feniqomobil.sharedui.generated.resources.debt_payment_error_amount_non_positive
import feniqomobil.sharedui.generated.resources.debt_payment_error_amount_required
import feniqomobil.sharedui.generated.resources.debt_payment_error_currency_mismatch
import feniqomobil.sharedui.generated.resources.debt_payment_error_date_required
import feniqomobil.sharedui.generated.resources.debt_payment_error_parent_missing
import feniqomobil.sharedui.generated.resources.debt_payment_route_not_found_desc
import feniqomobil.sharedui.generated.resources.debt_route_back_action
import feniqomobil.sharedui.generated.resources.debt_route_error_title
import feniqomobil.sharedui.generated.resources.debt_route_loading
import feniqomobil.sharedui.generated.resources.debt_route_not_found_title
import feniqomobil.sharedui.generated.resources.debt_snowball_error_budget_invalid
import feniqomobil.sharedui.generated.resources.debt_snowball_error_budget_max_exceeded
import feniqomobil.sharedui.generated.resources.debt_snowball_error_budget_non_positive
import feniqomobil.sharedui.generated.resources.debt_snowball_error_budget_required
import feniqomobil.sharedui.generated.resources.debt_type_debt
import feniqomobil.sharedui.generated.resources.debt_type_receivable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

fun DebtType.toLocalizedResource(): StringResource =
    when (this) {
        DebtType.DEBT -> Res.string.debt_type_debt
        DebtType.RECEIVABLE -> Res.string.debt_type_receivable
    }

@Composable
fun DebtType.toLocalizedDebtTypeLabel(): String = stringResource(toLocalizedResource())

fun DebtDueStatus.toLocalizedResource(): StringResource =
    when (this) {
        is DebtDueStatus.Overdue -> Res.string.debt_due_status_overdue
        is DebtDueStatus.DueToday -> Res.string.debt_due_status_due_today
        is DebtDueStatus.DueSoon -> Res.string.debt_due_status_due_soon
        is DebtDueStatus.OnTime -> Res.string.debt_due_status_on_time
        is DebtDueStatus.Settled -> Res.string.debt_due_status_settled
    }

@Composable
fun DebtDueStatus.toLocalizedDueStatusLabel(): String = stringResource(toLocalizedResource())

fun DebtsNetStatus.toLocalizedResource(): StringResource =
    when (this) {
        DebtsNetStatus.DEBT_EXCEEDS -> Res.string.debt_net_status_debt_exceeds
        DebtsNetStatus.RECEIVABLE_EXCEEDS -> Res.string.debt_net_status_receivable_exceeds
        DebtsNetStatus.BALANCED -> Res.string.debt_net_status_balanced
    }

@Composable
fun DebtsNetStatus.toLocalizedText(): String = stringResource(toLocalizedResource())

@Composable
fun DebtInsightPayload.resolveTitleAndDescription(): Pair<String, String> =
    when (this) {
        is DebtInsightPayload.OverdueDebts -> {
            val title = stringResource(Res.string.debt_insight_overdue_title)
            val desc =
                pluralStringResource(
                    Res.plurals.debt_insight_overdue_message_plural,
                    count,
                    count,
                )
            Pair(title, desc)
        }
        is DebtInsightPayload.PendingReceivables -> {
            val title = stringResource(Res.string.debt_insight_receivable_title)
            val desc =
                stringResource(
                    Res.string.debt_insight_receivable_message,
                    totalReceivable.toLocalizedFormatted(),
                )
            Pair(title, desc)
        }
        is DebtInsightPayload.SnowballSuggestion -> {
            val title = stringResource(Res.string.debt_insight_snowball_title)
            val desc =
                pluralStringResource(
                    Res.plurals.debt_insight_snowball_message_plural,
                    debtCount,
                    debtCount,
                )
            Pair(title, desc)
        }
        is DebtInsightPayload.SingleDebtTracking -> {
            val title = stringResource(Res.string.debt_insight_single_tracking_title)
            val desc =
                stringResource(
                    Res.string.debt_insight_single_tracking_message,
                    titleText,
                    remainingAmount.toLocalizedFormatted(),
                    dueDate.toLocalizedShortReadableDate(),
                )
            Pair(title, desc)
        }
        is DebtInsightPayload.AllSettled -> {
            val title = stringResource(Res.string.debt_insight_all_settled_title)
            val desc = stringResource(Res.string.debt_insight_all_settled_message)
            Pair(title, desc)
        }
    }

fun DebtFormFieldError.toLocalizedResource(): StringResource =
    when (this) {
        DebtFormFieldError.TITLE_REQUIRED -> Res.string.debt_error_title_required
        DebtFormFieldError.TITLE_TOO_LONG -> Res.string.debt_error_title_too_long
        DebtFormFieldError.AMOUNT_REQUIRED -> Res.string.debt_error_amount_required
        DebtFormFieldError.AMOUNT_NON_POSITIVE -> Res.string.debt_error_amount_non_positive
        DebtFormFieldError.AMOUNT_INVALID -> Res.string.debt_error_amount_invalid
        DebtFormFieldError.DUE_DATE_REQUIRED -> Res.string.debt_error_due_date_required
        DebtFormFieldError.DESCRIPTION_TOO_LONG -> Res.string.debt_error_description_too_long
        DebtFormFieldError.CURRENCY_LOCKED_IN_EDIT -> Res.string.debt_error_currency_locked_in_edit
    }

@Composable
fun DebtFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

fun DebtPaymentFormFieldError.toLocalizedResource(): StringResource =
    when (this) {
        DebtPaymentFormFieldError.AMOUNT_REQUIRED -> Res.string.debt_payment_error_amount_required
        DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE -> Res.string.debt_payment_error_amount_non_positive
        DebtPaymentFormFieldError.AMOUNT_INVALID -> Res.string.debt_payment_error_amount_invalid
        DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT -> Res.string.debt_payment_error_amount_invalid
        DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED -> Res.string.debt_payment_error_already_settled
        DebtPaymentFormFieldError.DATE_REQUIRED -> Res.string.debt_payment_error_date_required
        DebtPaymentFormFieldError.CURRENCY_MISMATCH -> Res.string.debt_payment_error_currency_mismatch
        DebtPaymentFormFieldError.PARENT_DEBT_MISSING -> Res.string.debt_payment_error_parent_missing
    }

@Composable
fun DebtPaymentFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

fun DebtSnowballFormFieldError.toLocalizedResource(): StringResource =
    when (this) {
        DebtSnowballFormFieldError.BUDGET_REQUIRED -> Res.string.debt_snowball_error_budget_required
        DebtSnowballFormFieldError.BUDGET_NON_POSITIVE -> Res.string.debt_snowball_error_budget_non_positive
        DebtSnowballFormFieldError.BUDGET_MAX_EXCEEDED -> Res.string.debt_snowball_error_budget_max_exceeded
        DebtSnowballFormFieldError.BUDGET_INVALID -> Res.string.debt_snowball_error_budget_invalid
    }

@Composable
fun DebtSnowballFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

@Composable
fun debtRouteLoadingText(): String = stringResource(Res.string.debt_route_loading)

@Composable
fun debtRouteNotFoundTitleText(): String = stringResource(Res.string.debt_route_not_found_title)

@Composable
fun debtFormRouteNotFoundDescText(): String = stringResource(Res.string.debt_form_route_not_found_desc)

@Composable
fun debtPaymentRouteNotFoundDescText(): String = stringResource(Res.string.debt_payment_route_not_found_desc)

@Composable
fun debtRouteBackActionText(): String = stringResource(Res.string.debt_route_back_action)

@Composable
fun debtRouteErrorTitleText(): String = stringResource(Res.string.debt_route_error_title)

@Composable
fun debtDatePickerDefaultTitleText(): String = stringResource(Res.string.debt_date_picker_default_title)

@Composable
fun debtDatePickerPaymentTitleText(): String = stringResource(Res.string.debt_date_picker_payment_title)

@Composable
fun debtDatePickerCollectionTitleText(): String = stringResource(Res.string.debt_date_picker_collection_title)

@Composable
fun debtDeleteDialogDefaultNameText(): String = stringResource(Res.string.debt_delete_dialog_default_name)
