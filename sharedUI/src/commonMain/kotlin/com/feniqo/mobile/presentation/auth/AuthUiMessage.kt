package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.validation.AuthValidationError

/**
 * Kimlik doğrulama akışına ait tipli UI mesajlarıdır.
 * Ham teknik hatalar yerine güvenli ve çok dilli desteğe hazır durumları temsil eder.
 */
enum class AuthUiMessage {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    NETWORK_UNAVAILABLE,
    RATE_LIMITED,
    EMAIL_NOT_CONFIRMED,
    AUTH_PROVIDER_UNAVAILABLE,
    INVALID_INPUT,
    SESSION_EXPIRED,
    GENERIC_ERROR,
    EMAIL_CONFIRMATION_SENT,
}

/**
 * AppError domain hata nesnesini tipli AuthUiMessage'a dönüştürür.
 * String parsing veya ham mesaj arama yapmaz; kesin ve stabil domain hata kodlarını kullanır.
 * Tanımlanmamış veya bilinmeyen kodlar güvenli varsayılan olarak GENERIC_ERROR'a düşer.
 */
fun AppError.toAuthUiMessage(): AuthUiMessage = when (this) {
    is AppError.Authentication -> when (code) {
        "auth_invalid_credentials" -> AuthUiMessage.INVALID_CREDENTIALS
        "auth_session_expired" -> AuthUiMessage.SESSION_EXPIRED
        "auth_email_not_confirmed" -> AuthUiMessage.EMAIL_NOT_CONFIRMED
        "auth_provider_unavailable" -> AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE
        else -> AuthUiMessage.GENERIC_ERROR
    }
    is AppError.Conflict -> when (code) {
        "auth_email_already_registered" -> AuthUiMessage.EMAIL_ALREADY_REGISTERED
        else -> AuthUiMessage.GENERIC_ERROR
    }
    is AppError.Network -> when (code) {
        "network_unavailable" -> AuthUiMessage.NETWORK_UNAVAILABLE
        "auth_rate_limited" -> AuthUiMessage.RATE_LIMITED
        else -> AuthUiMessage.GENERIC_ERROR
    }
    is AppError.Validation -> when (code) {
        "auth_invalid_input" -> AuthUiMessage.INVALID_INPUT
        else -> AuthUiMessage.GENERIC_ERROR
    }
    is AppError.Storage -> AuthUiMessage.GENERIC_ERROR
    is AppError.Unknown -> AuthUiMessage.GENERIC_ERROR
}

/**
 * AuthUiMessage için güvenli Türkçe kullanıcı metnini çözer.
 */
fun AuthUiMessage.toDisplayText(): String = when (this) {
    AuthUiMessage.INVALID_CREDENTIALS -> "E-posta veya parola hatalı."
    AuthUiMessage.EMAIL_ALREADY_REGISTERED -> "Bu e-posta adresiyle kayıtlı bir hesap zaten var."
    AuthUiMessage.NETWORK_UNAVAILABLE -> "İnternet bağlantınızı kontrol edip tekrar deneyin."
    AuthUiMessage.RATE_LIMITED -> "Çok fazla deneme yaptınız. Lütfen bir süre bekleyip tekrar deneyin."
    AuthUiMessage.EMAIL_NOT_CONFIRMED -> "Giriş yapmadan önce e-posta adresinizi doğrulayın."
    AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE -> "Giriş ve kayıt işlemleri şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin."
    AuthUiMessage.INVALID_INPUT -> "Lütfen girdiğiniz bilgileri kontrol edin."
    AuthUiMessage.SESSION_EXPIRED -> "Oturum süreniz doldu, lütfen tekrar giriş yapın."
    AuthUiMessage.GENERIC_ERROR -> "Bir hata oluştu. Lütfen tekrar deneyin."
    AuthUiMessage.EMAIL_CONFIRMATION_SENT -> "Kaydınız oluşturuldu. Lütfen e-posta adresinize gönderilen doğrulama bağlantısını onaylayın."
}

/**
 * AuthValidationError için güvenli Türkçe alan hata metnini çözer.
 */
fun AuthValidationError.toDisplayText(): String = when (this) {
    AuthValidationError.EMAIL_REQUIRED -> "E-posta adresi gereklidir."
    AuthValidationError.EMAIL_INVALID -> "Geçerli bir e-posta adresi girin."
    AuthValidationError.PASSWORD_REQUIRED -> "Parola gereklidir."
    AuthValidationError.NEW_PASSWORD_TOO_SHORT -> "Parola en az 6 karakter olmalıdır."
    AuthValidationError.PASSWORDS_DO_NOT_MATCH -> "Parolalar birbiriyle eşleşmiyor."
    AuthValidationError.FULL_NAME_TOO_SHORT -> "Ad soyad en az 2 karakter olmalıdır."
}
