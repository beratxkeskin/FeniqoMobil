package com.feniqo.mobile.presentation.transaction

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.transaction_form_error_amount_invalid
import feniqomobil.sharedui.generated.resources.transaction_form_error_amount_non_positive
import feniqomobil.sharedui.generated.resources.transaction_form_error_amount_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_amount_too_large
import feniqomobil.sharedui.generated.resources.transaction_form_error_category_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_category_unavailable
import feniqomobil.sharedui.generated.resources.transaction_form_error_date_in_future
import feniqomobil.sharedui.generated.resources.transaction_form_error_date_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_description_too_long
import feniqomobil.sharedui.generated.resources.transaction_form_error_installment_amount_too_small
import feniqomobil.sharedui.generated.resources.transaction_form_error_installment_count_invalid
import feniqomobil.sharedui.generated.resources.transaction_form_error_note_too_long
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_custom_installment
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_custom_personal
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_custom_shares_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_member_inactive
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_non_payer_zero
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_participant_mismatch
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_participants_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_payer_not_participant
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_payer_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_share_invalid
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_share_negative
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_share_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_share_too_large
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_total_mismatch
import feniqomobil.sharedui.generated.resources.transaction_form_error_split_total_overflow
import feniqomobil.sharedui.generated.resources.transaction_form_error_title_required
import feniqomobil.sharedui.generated.resources.transaction_form_error_title_too_long
import feniqomobil.sharedui.generated.resources.transaction_form_split_inactive_participants
import feniqomobil.sharedui.generated.resources.transaction_form_split_inactive_payer
import feniqomobil.sharedui.generated.resources.transaction_form_split_inactive_payer_and_participants
import feniqomobil.sharedui.generated.resources.transactions_expense
import feniqomobil.sharedui.generated.resources.transactions_income
import feniqomobil.sharedui.generated.resources.transactions_payment_bank_transfer
import feniqomobil.sharedui.generated.resources.transactions_payment_cash
import feniqomobil.sharedui.generated.resources.transactions_payment_credit_card
import feniqomobil.sharedui.generated.resources.transactions_payment_debit_card
import feniqomobil.sharedui.generated.resources.transactions_payment_other
import feniqomobil.sharedui.generated.resources.transactions_period_all_time
import feniqomobil.sharedui.generated.resources.transactions_period_custom
import feniqomobil.sharedui.generated.resources.transactions_period_last_30_days
import feniqomobil.sharedui.generated.resources.transactions_period_this_month
import feniqomobil.sharedui.generated.resources.transactions_period_this_week
import feniqomobil.sharedui.generated.resources.transactions_period_this_year
import feniqomobil.sharedui.generated.resources.transactions_period_today
import feniqomobil.sharedui.generated.resources.transactions_sort_amount_asc
import feniqomobil.sharedui.generated.resources.transactions_sort_amount_desc
import feniqomobil.sharedui.generated.resources.transactions_sort_newest
import feniqomobil.sharedui.generated.resources.transactions_sort_oldest
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionType.toLocalizedText(): String =
    stringResource(
        when (this) {
            TransactionType.EXPENSE -> Res.string.transactions_expense
            TransactionType.INCOME -> Res.string.transactions_income
        },
    )

@Composable
fun PaymentMethod.toLocalizedText(): String =
    stringResource(
        when (this) {
            PaymentMethod.CASH -> Res.string.transactions_payment_cash
            PaymentMethod.CREDIT_CARD -> Res.string.transactions_payment_credit_card
            PaymentMethod.DEBIT_CARD -> Res.string.transactions_payment_debit_card
            PaymentMethod.BANK_TRANSFER -> Res.string.transactions_payment_bank_transfer
            PaymentMethod.OTHER -> Res.string.transactions_payment_other
        },
    )

@Composable
fun TransactionPeriodPreset.toLocalizedText(): String =
    stringResource(
        when (this) {
            TransactionPeriodPreset.TODAY -> Res.string.transactions_period_today
            TransactionPeriodPreset.THIS_WEEK -> Res.string.transactions_period_this_week
            TransactionPeriodPreset.THIS_MONTH -> Res.string.transactions_period_this_month
            TransactionPeriodPreset.LAST_30_DAYS -> Res.string.transactions_period_last_30_days
            TransactionPeriodPreset.THIS_YEAR -> Res.string.transactions_period_this_year
        },
    )

@Composable
fun TransactionSortOrder.toLocalizedText(): String =
    stringResource(
        when (this) {
            TransactionSortOrder.NEWEST -> Res.string.transactions_sort_newest
            TransactionSortOrder.OLDEST -> Res.string.transactions_sort_oldest
            TransactionSortOrder.AMOUNT_DESC -> Res.string.transactions_sort_amount_desc
            TransactionSortOrder.AMOUNT_ASC -> Res.string.transactions_sort_amount_asc
        },
    )

@Composable
fun TransactionFormFieldError.toLocalizedText(): String =
    stringResource(
        when (this) {
            TransactionFormFieldError.AMOUNT_REQUIRED -> Res.string.transaction_form_error_amount_required
            TransactionFormFieldError.AMOUNT_INVALID -> Res.string.transaction_form_error_amount_invalid
            TransactionFormFieldError.AMOUNT_NON_POSITIVE -> Res.string.transaction_form_error_amount_non_positive
            TransactionFormFieldError.AMOUNT_TOO_LARGE -> Res.string.transaction_form_error_amount_too_large
            TransactionFormFieldError.CATEGORY_REQUIRED -> Res.string.transaction_form_error_category_required
            TransactionFormFieldError.CATEGORY_UNAVAILABLE -> Res.string.transaction_form_error_category_unavailable
            TransactionFormFieldError.DATE_REQUIRED -> Res.string.transaction_form_error_date_required
            TransactionFormFieldError.DATE_IN_FUTURE -> Res.string.transaction_form_error_date_in_future
            TransactionFormFieldError.TITLE_REQUIRED -> Res.string.transaction_form_error_title_required
            TransactionFormFieldError.TITLE_TOO_LONG -> Res.string.transaction_form_error_title_too_long
            TransactionFormFieldError.NOTE_TOO_LONG -> Res.string.transaction_form_error_note_too_long
            TransactionFormFieldError.DESCRIPTION_TOO_LONG -> Res.string.transaction_form_error_description_too_long
            TransactionFormFieldError.INSTALLMENT_COUNT_INVALID -> Res.string.transaction_form_error_installment_count_invalid
            TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL -> Res.string.transaction_form_error_installment_amount_too_small
            TransactionFormFieldError.SPLIT_PAYER_REQUIRED -> Res.string.transaction_form_error_split_payer_required
            TransactionFormFieldError.SPLIT_PARTICIPANTS_REQUIRED -> Res.string.transaction_form_error_split_participants_required
            TransactionFormFieldError.SPLIT_PAYER_NOT_IN_PARTICIPANTS -> Res.string.transaction_form_error_split_payer_not_participant
            TransactionFormFieldError.SPLIT_CUSTOM_SHARES_REQUIRED -> Res.string.transaction_form_error_split_custom_shares_required
            TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED -> Res.string.transaction_form_error_split_share_required
            TransactionFormFieldError.SPLIT_CUSTOM_SHARE_INVALID -> Res.string.transaction_form_error_split_share_invalid
            TransactionFormFieldError.SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED ->
                Res.string.transaction_form_error_split_non_payer_zero
            TransactionFormFieldError.SPLIT_CUSTOM_SHARE_NEGATIVE -> Res.string.transaction_form_error_split_share_negative
            TransactionFormFieldError.SPLIT_CUSTOM_SHARE_TOO_LARGE -> Res.string.transaction_form_error_split_share_too_large
            TransactionFormFieldError.SPLIT_CUSTOM_TOTAL_MISMATCH -> Res.string.transaction_form_error_split_total_mismatch
            TransactionFormFieldError.SPLIT_CUSTOM_TOTAL_OVERFLOW -> Res.string.transaction_form_error_split_total_overflow
            TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE -> Res.string.transaction_form_error_split_member_inactive
            TransactionFormFieldError.SPLIT_PARTICIPANT_SET_MISMATCH -> Res.string.transaction_form_error_split_participant_mismatch
            TransactionFormFieldError.SPLIT_CUSTOM_NOT_ALLOWED_IN_PERSONAL -> Res.string.transaction_form_error_split_custom_personal
            TransactionFormFieldError.SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT ->
                Res.string.transaction_form_error_split_custom_installment
        },
    )

@Composable
fun CustomSplitInactiveMemberMessage.toLocalizedText(): String =
    stringResource(
        when (this) {
            CustomSplitInactiveMemberMessage.PAYER_AND_PARTICIPANTS ->
                Res.string.transaction_form_split_inactive_payer_and_participants
            CustomSplitInactiveMemberMessage.PAYER -> Res.string.transaction_form_split_inactive_payer
            CustomSplitInactiveMemberMessage.PARTICIPANTS -> Res.string.transaction_form_split_inactive_participants
        },
    )

@Composable
fun localizedTransactionPeriodText(
    preset: TransactionPeriodPreset?,
    hasCustomPeriod: Boolean,
    customDateRangeText: String,
): String = when {
    hasCustomPeriod ->
        customDateRangeText.ifBlank {
            stringResource(Res.string.transactions_period_custom)
        }
    preset != null -> preset.toLocalizedText()
    else -> stringResource(Res.string.transactions_period_all_time)
}
