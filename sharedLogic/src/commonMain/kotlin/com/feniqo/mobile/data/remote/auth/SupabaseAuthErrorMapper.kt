package com.feniqo.mobile.data.remote.auth

import com.feniqo.mobile.domain.model.AppError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException

/** Supabase/HTTP hatalarını UI metninden bağımsız, kararlı domain hata kodlarına dönüştürür. */
internal fun Throwable.toAuthAppError(): AppError = when (this) {
    is HttpRequestException -> AppError.Network(code = "network_unavailable")
    is AuthRestException -> errorCode?.toAppError()
        ?: AppError.Authentication(code = "auth_unknown_response")
    else -> AppError.Unknown(code = "auth_unknown")
}

internal fun AuthErrorCode.toAppError(): AppError = when (this) {
    AuthErrorCode.WeakPassword,
    AuthErrorCode.EmailAddressInvalid,
    AuthErrorCode.ValidationFailed,
    -> AppError.Validation(code = "auth_invalid_input")

    AuthErrorCode.EmailExists,
    AuthErrorCode.UserAlreadyExists,
    AuthErrorCode.Conflict,
    -> AppError.Conflict(code = "auth_email_already_registered")

    AuthErrorCode.InvalidCredentials,
    AuthErrorCode.UserNotFound,
    -> AppError.Authentication(code = "auth_invalid_credentials")

    AuthErrorCode.EmailNotConfirmed,
    AuthErrorCode.ProviderEmailNeedsVerification,
    -> AppError.Authentication(code = "auth_email_not_confirmed")

    AuthErrorCode.SessionNotFound,
    AuthErrorCode.SessionExpired,
    AuthErrorCode.RefreshTokenNotFound,
    AuthErrorCode.RefreshTokenAlreadyUsed,
    -> AppError.Authentication(code = "auth_session_expired")

    AuthErrorCode.OverRequestRateLimit,
    AuthErrorCode.OverEmailSendRateLimit,
    AuthErrorCode.RequestTimeout,
    -> AppError.Network(code = "auth_rate_limited")

    AuthErrorCode.SignupDisabled,
    AuthErrorCode.EmailProviderDisabled,
    AuthErrorCode.ProviderDisabled,
    -> AppError.Authentication(code = "auth_provider_unavailable")

    else -> AppError.Authentication(code = "auth_${value}")
}
