package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_error_current_password_invalid
import feniqomobil.sharedui.generated.resources.account_error_current_password_required
import feniqomobil.sharedui.generated.resources.account_error_email_invalid
import feniqomobil.sharedui.generated.resources.account_error_email_not_found
import feniqomobil.sharedui.generated.resources.account_error_email_required
import feniqomobil.sharedui.generated.resources.account_error_email_verification_failed
import feniqomobil.sharedui.generated.resources.account_error_network
import feniqomobil.sharedui.generated.resources.account_error_new_password_invalid
import feniqomobil.sharedui.generated.resources.account_error_new_password_required
import feniqomobil.sharedui.generated.resources.account_error_new_password_short
import feniqomobil.sharedui.generated.resources.account_error_password_mismatch
import feniqomobil.sharedui.generated.resources.account_error_password_unchanged
import feniqomobil.sharedui.generated.resources.account_error_password_update_failed
import feniqomobil.sharedui.generated.resources.account_error_rate_limited
import feniqomobil.sharedui.generated.resources.account_error_reauthentication_required
import feniqomobil.sharedui.generated.resources.account_error_session_expired
import org.jetbrains.compose.resources.stringResource

enum class AccountSecurityUiMessage {
    CURRENT_PASSWORD_REQUIRED,
    NEW_PASSWORD_REQUIRED,
    NEW_PASSWORD_TOO_SHORT,
    NEW_PASSWORD_INVALID,
    PASSWORDS_DO_NOT_MATCH,
    PASSWORD_UNCHANGED,
    CURRENT_PASSWORD_INVALID,
    SESSION_EXPIRED,
    REAUTHENTICATION_REQUIRED,
    NETWORK_UNAVAILABLE,
    PASSWORD_UPDATE_FAILED,
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    EMAIL_NOT_FOUND,
    RATE_LIMITED,
    EMAIL_VERIFICATION_FAILED,
}

@Composable
fun AccountSecurityUiMessage.toLocalizedText(): String =
    stringResource(
        when (this) {
            AccountSecurityUiMessage.CURRENT_PASSWORD_REQUIRED -> Res.string.account_error_current_password_required
            AccountSecurityUiMessage.NEW_PASSWORD_REQUIRED -> Res.string.account_error_new_password_required
            AccountSecurityUiMessage.NEW_PASSWORD_TOO_SHORT -> Res.string.account_error_new_password_short
            AccountSecurityUiMessage.NEW_PASSWORD_INVALID -> Res.string.account_error_new_password_invalid
            AccountSecurityUiMessage.PASSWORDS_DO_NOT_MATCH -> Res.string.account_error_password_mismatch
            AccountSecurityUiMessage.PASSWORD_UNCHANGED -> Res.string.account_error_password_unchanged
            AccountSecurityUiMessage.CURRENT_PASSWORD_INVALID -> Res.string.account_error_current_password_invalid
            AccountSecurityUiMessage.SESSION_EXPIRED -> Res.string.account_error_session_expired
            AccountSecurityUiMessage.REAUTHENTICATION_REQUIRED -> Res.string.account_error_reauthentication_required
            AccountSecurityUiMessage.NETWORK_UNAVAILABLE -> Res.string.account_error_network
            AccountSecurityUiMessage.PASSWORD_UPDATE_FAILED -> Res.string.account_error_password_update_failed
            AccountSecurityUiMessage.EMAIL_REQUIRED -> Res.string.account_error_email_required
            AccountSecurityUiMessage.EMAIL_INVALID -> Res.string.account_error_email_invalid
            AccountSecurityUiMessage.EMAIL_NOT_FOUND -> Res.string.account_error_email_not_found
            AccountSecurityUiMessage.RATE_LIMITED -> Res.string.account_error_rate_limited
            AccountSecurityUiMessage.EMAIL_VERIFICATION_FAILED -> Res.string.account_error_email_verification_failed
        },
    )
