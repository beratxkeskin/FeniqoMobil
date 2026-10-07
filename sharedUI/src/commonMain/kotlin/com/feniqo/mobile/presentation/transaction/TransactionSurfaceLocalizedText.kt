@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.transaction

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.SyncStatus
import feniqomobil.sharedui.generated.resources.*
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class TransactionSurfaceUiMessage {
    DETAIL_INVALID_LINK,
    DETAIL_NOT_FOUND,
    DETAIL_PREPARATION_FAILED,
    DETAIL_LOAD_FAILED,
    SUCCESS_INVALID_PARAMETER,
    SUCCESS_NOT_FOUND,
    CONFLICT_LOAD_FAILED,
    CONFLICT_RESOLUTION_FAILED,
}

@Composable
fun TransactionSurfaceUiMessage.toLocalizedText(): String = stringResource(toStringResource())

@Composable
fun SyncStatus?.toLocalizedTransactionStatusText(): String = stringResource(toTransactionStatusStringResource())

@Composable
fun LocalDate.toLocalizedTransactionDate(): String =
    "$day ${stringResource(month.toTransactionMonthStringResource())} $year"

@Composable
fun transactionDetailLoadingText(): String = stringResource(Res.string.transaction_detail_loading)

@Composable
fun transactionDetailOpenFailedText(): String = stringResource(Res.string.transaction_detail_open_failed)

@Composable
fun transactionDetailUnavailableText(): String = stringResource(Res.string.transaction_detail_unavailable)

@Composable
fun transactionDetailReturnText(): String = stringResource(Res.string.transaction_detail_return)

private fun TransactionSurfaceUiMessage.toStringResource(): StringResource = when (this) {
    TransactionSurfaceUiMessage.DETAIL_INVALID_LINK -> Res.string.transaction_surface_detail_invalid_link
    TransactionSurfaceUiMessage.DETAIL_NOT_FOUND -> Res.string.transaction_surface_detail_not_found
    TransactionSurfaceUiMessage.DETAIL_PREPARATION_FAILED -> Res.string.transaction_surface_detail_preparation_failed
    TransactionSurfaceUiMessage.DETAIL_LOAD_FAILED -> Res.string.transaction_surface_detail_load_failed
    TransactionSurfaceUiMessage.SUCCESS_INVALID_PARAMETER -> Res.string.transaction_surface_success_invalid_parameter
    TransactionSurfaceUiMessage.SUCCESS_NOT_FOUND -> Res.string.transaction_surface_success_not_found
    TransactionSurfaceUiMessage.CONFLICT_LOAD_FAILED -> Res.string.transaction_surface_conflict_load_failed
    TransactionSurfaceUiMessage.CONFLICT_RESOLUTION_FAILED -> Res.string.transaction_surface_conflict_resolution_failed
}

internal fun SyncStatus?.toTransactionStatusStringResource(): StringResource = when (this) {
    SyncStatus.SYNCED -> Res.string.transaction_detail_sync_synced
    SyncStatus.PENDING_CREATE,
    SyncStatus.PENDING_UPDATE,
    SyncStatus.PENDING_DELETE,
    -> Res.string.transaction_detail_sync_pending
    SyncStatus.CONFLICT -> Res.string.transaction_detail_sync_conflict
    SyncStatus.FAILED -> Res.string.transaction_detail_sync_failed
    null -> Res.string.transaction_detail_sync_unknown
}

private fun Month.toTransactionMonthStringResource(): StringResource = when (this) {
    Month.JANUARY -> Res.string.transaction_form_month_january
    Month.FEBRUARY -> Res.string.transaction_form_month_february
    Month.MARCH -> Res.string.transaction_form_month_march
    Month.APRIL -> Res.string.transaction_form_month_april
    Month.MAY -> Res.string.transaction_form_month_may
    Month.JUNE -> Res.string.transaction_form_month_june
    Month.JULY -> Res.string.transaction_form_month_july
    Month.AUGUST -> Res.string.transaction_form_month_august
    Month.SEPTEMBER -> Res.string.transaction_form_month_september
    Month.OCTOBER -> Res.string.transaction_form_month_october
    Month.NOVEMBER -> Res.string.transaction_form_month_november
    Month.DECEMBER -> Res.string.transaction_form_month_december
}
