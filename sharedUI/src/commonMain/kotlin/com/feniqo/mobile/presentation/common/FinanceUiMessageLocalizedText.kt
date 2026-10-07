@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun FinanceUiMessage.toLocalizedText(): String = stringResource(toStringResource())

suspend fun FinanceUiMessage.resolveLocalizedText(): String = getString(toStringResource())

internal fun FinanceUiMessage.toStringResource(): StringResource = when (this) {
    FinanceUiMessage.INVALID_AMOUNT -> Res.string.finance_message_invalid_amount
    FinanceUiMessage.AMOUNT_REQUIRED -> Res.string.finance_message_amount_required
    FinanceUiMessage.AMOUNT_TOO_SMALL -> Res.string.finance_message_amount_too_small
    FinanceUiMessage.DATE_IN_FUTURE -> Res.string.finance_message_date_in_future
    FinanceUiMessage.CATEGORY_NOT_FOUND -> Res.string.finance_message_category_not_found
    FinanceUiMessage.CATEGORY_TYPE_MISMATCH -> Res.string.finance_message_category_type_mismatch
    FinanceUiMessage.CATEGORY_WORKSPACE_MISMATCH -> Res.string.finance_message_category_workspace_mismatch
    FinanceUiMessage.WORKSPACE_IMMUTABLE -> Res.string.finance_message_workspace_immutable
    FinanceUiMessage.TRANSACTION_NOT_FOUND -> Res.string.finance_message_transaction_not_found
    FinanceUiMessage.INSTALLMENT_COUNT_INVALID -> Res.string.finance_message_installment_count_invalid
    FinanceUiMessage.INSTALLMENT_SCOPE_REQUIRED -> Res.string.finance_message_installment_scope_required
    FinanceUiMessage.INSTALLMENT_SCOPE_NOT_APPLICABLE -> Res.string.finance_message_installment_scope_not_applicable
    FinanceUiMessage.INSTALLMENT_DATA_INVALID -> Res.string.finance_message_installment_data_invalid
    FinanceUiMessage.CATEGORY_NAME_REQUIRED -> Res.string.finance_message_category_name_required
    FinanceUiMessage.CATEGORY_NAME_TOO_LONG -> Res.string.finance_message_category_name_too_long
    FinanceUiMessage.CATEGORY_DUPLICATE_NAME -> Res.string.finance_message_category_duplicate_name
    FinanceUiMessage.CATEGORY_COLOR_INVALID -> Res.string.finance_message_category_color_invalid
    FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE -> Res.string.finance_message_default_category_immutable
    FinanceUiMessage.SESSION_EXPIRED -> Res.string.finance_message_session_expired
    FinanceUiMessage.PERMISSION_DENIED -> Res.string.finance_message_permission_denied
    FinanceUiMessage.STORAGE_ERROR -> Res.string.finance_message_storage_error
    FinanceUiMessage.NETWORK_ERROR -> Res.string.finance_message_network_error
    FinanceUiMessage.CONFLICT -> Res.string.finance_message_conflict
    FinanceUiMessage.GENERIC_ERROR -> Res.string.finance_message_generic_error
    FinanceUiMessage.TRANSACTION_SAVED -> Res.string.finance_message_transaction_saved
    FinanceUiMessage.TRANSACTION_DELETED -> Res.string.finance_message_transaction_deleted
    FinanceUiMessage.CATEGORY_SAVED -> Res.string.finance_message_category_saved
    FinanceUiMessage.CATEGORY_DELETED -> Res.string.finance_message_category_deleted
    FinanceUiMessage.BUDGET_SAVED -> Res.string.finance_message_budget_saved
    FinanceUiMessage.BUDGET_DELETED -> Res.string.finance_message_budget_deleted
    FinanceUiMessage.BUDGETS_COPIED -> Res.string.finance_message_budgets_copied
    FinanceUiMessage.BUDGET_COPY_MONTHS_SAME -> Res.string.finance_message_budget_copy_months_same
    FinanceUiMessage.SUBSCRIPTION_SAVED -> Res.string.finance_message_subscription_saved
    FinanceUiMessage.SUBSCRIPTION_DELETED -> Res.string.finance_message_subscription_deleted
    FinanceUiMessage.SUBSCRIPTION_RENEWED -> Res.string.finance_message_subscription_renewed
    FinanceUiMessage.SUBSCRIPTION_COMPLETED -> Res.string.finance_message_subscription_completed
    FinanceUiMessage.GOAL_SAVED -> Res.string.finance_message_goal_saved
    FinanceUiMessage.GOAL_DELETED -> Res.string.finance_message_goal_deleted
    FinanceUiMessage.GOAL_CONTRIBUTION_ADDED -> Res.string.finance_message_goal_contribution_added
    FinanceUiMessage.ASSET_SAVED -> Res.string.finance_message_asset_saved
    FinanceUiMessage.ASSET_DELETED -> Res.string.finance_message_asset_deleted
    FinanceUiMessage.DEBT_SAVED -> Res.string.finance_message_debt_saved
    FinanceUiMessage.DEBT_DELETED -> Res.string.finance_message_debt_deleted
    FinanceUiMessage.DEBT_PAYMENT_ADDED -> Res.string.finance_message_debt_payment_added
    FinanceUiMessage.CONFLICT_RESOLUTION_STALE -> Res.string.finance_message_conflict_resolution_stale
    FinanceUiMessage.WORKSPACE_CONFLICT_REMOTE_TOMBSTONE -> Res.string.finance_message_workspace_conflict_remote_tombstone
    FinanceUiMessage.WORKSPACE_CONFLICT_OWNER_MISMATCH -> Res.string.finance_message_workspace_conflict_owner_mismatch
    FinanceUiMessage.CONFLICT_NOT_FOUND -> Res.string.finance_message_conflict_not_found
    FinanceUiMessage.CONFLICT_RESOLUTION_FAILED -> Res.string.finance_message_conflict_resolution_failed
    FinanceUiMessage.WORKSPACE_NOT_FOUND -> Res.string.finance_message_workspace_not_found
    FinanceUiMessage.WORKSPACE_INVITATION_NOT_FOUND -> Res.string.finance_message_workspace_invitation_not_found
    FinanceUiMessage.WORKSPACE_INVITATION_EXPIRED -> Res.string.finance_message_workspace_invitation_expired
    FinanceUiMessage.WORKSPACE_INVITATION_LIMIT_REACHED -> Res.string.finance_message_workspace_invitation_limit_reached
    FinanceUiMessage.WORKSPACE_CANNOT_LEAVE_AS_OWNER -> Res.string.finance_message_workspace_cannot_leave_as_owner
    FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER -> Res.string.finance_message_workspace_actor_not_member
    FinanceUiMessage.WORKSPACE_ACTOR_NOT_PERMITTED -> Res.string.finance_message_workspace_actor_not_permitted
    FinanceUiMessage.WORKSPACE_CANNOT_CHANGE_OWN_ROLE -> Res.string.finance_message_workspace_cannot_change_own_role
    FinanceUiMessage.WORKSPACE_OWNER_ROLE_CHANGE_REQUIRES_TRANSFER -> Res.string.finance_message_workspace_owner_role_change_requires_transfer
    FinanceUiMessage.WORKSPACE_TARGET_MEMBER_NOT_FOUND -> Res.string.finance_message_workspace_target_member_not_found
    FinanceUiMessage.WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE -> Res.string.finance_message_workspace_local_member_version_unavailable
    FinanceUiMessage.WORKSPACE_TRANSFER_ACTOR_NOT_OWNER -> Res.string.finance_message_workspace_transfer_actor_not_owner
    FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_NOT_MEMBER -> Res.string.finance_message_workspace_transfer_target_not_member
    FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER -> Res.string.finance_message_workspace_transfer_target_already_owner
    FinanceUiMessage.WORKSPACE_TRANSFER_VERSION_CONFLICT -> Res.string.finance_message_workspace_transfer_version_conflict
    FinanceUiMessage.WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER -> Res.string.finance_message_workspace_local_changes_prevent_transfer
    FinanceUiMessage.WORKSPACE_CANNOT_REMOVE_SELF_MEMBER -> Res.string.finance_message_workspace_cannot_remove_self_member
    FinanceUiMessage.WORKSPACE_CANNOT_REMOVE_WORKSPACE_OWNER -> Res.string.finance_message_workspace_cannot_remove_workspace_owner
}
