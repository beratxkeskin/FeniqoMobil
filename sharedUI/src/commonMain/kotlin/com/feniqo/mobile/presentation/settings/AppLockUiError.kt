package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.security_privacy_auth_failed_error
import feniqomobil.sharedui.generated.resources.security_privacy_auth_none_enrolled_error
import feniqomobil.sharedui.generated.resources.security_privacy_auth_unavailable_error
import feniqomobil.sharedui.generated.resources.security_privacy_autolock_save_error
import feniqomobil.sharedui.generated.resources.security_privacy_lock_pref_save_error
import org.jetbrains.compose.resources.stringResource

sealed interface AppLockUiError {
    data object AutoLockTimeoutSaveFailed : AppLockUiError
    data object BiometricPreferenceSaveFailed : AppLockUiError
    data object AuthenticationFailed : AppLockUiError
    data object AuthenticationNoneEnrolled : AppLockUiError
    data object AuthenticationUnavailable : AppLockUiError
}

@Composable
fun AppLockUiError.toLocalizedMessage(): String = when (this) {
    AppLockUiError.AutoLockTimeoutSaveFailed ->
        stringResource(Res.string.security_privacy_autolock_save_error)
    AppLockUiError.BiometricPreferenceSaveFailed ->
        stringResource(Res.string.security_privacy_lock_pref_save_error)
    AppLockUiError.AuthenticationFailed ->
        stringResource(Res.string.security_privacy_auth_failed_error)
    AppLockUiError.AuthenticationNoneEnrolled ->
        stringResource(Res.string.security_privacy_auth_none_enrolled_error)
    AppLockUiError.AuthenticationUnavailable ->
        stringResource(Res.string.security_privacy_auth_unavailable_error)
}
