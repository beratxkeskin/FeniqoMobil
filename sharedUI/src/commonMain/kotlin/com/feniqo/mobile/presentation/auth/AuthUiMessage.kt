package com.feniqo.mobile.presentation.auth

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.validation.AuthValidationError
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.auth_email_confirmation_sent
import feniqomobil.sharedui.generated.resources.auth_error_email_not_confirmed
import feniqomobil.sharedui.generated.resources.auth_error_email_registered
import feniqomobil.sharedui.generated.resources.auth_error_generic
import feniqomobil.sharedui.generated.resources.auth_error_invalid_credentials
import feniqomobil.sharedui.generated.resources.auth_error_invalid_input
import feniqomobil.sharedui.generated.resources.auth_error_network
import feniqomobil.sharedui.generated.resources.auth_error_provider_unavailable
import feniqomobil.sharedui.generated.resources.auth_error_rate_limited
import feniqomobil.sharedui.generated.resources.auth_error_recovery_link_invalid
import feniqomobil.sharedui.generated.resources.auth_error_recovery_not_authorized
import feniqomobil.sharedui.generated.resources.auth_error_session_expired
import feniqomobil.sharedui.generated.resources.auth_password_reset_sent
import feniqomobil.sharedui.generated.resources.auth_password_updated
import feniqomobil.sharedui.generated.resources.auth_validation_email_invalid
import feniqomobil.sharedui.generated.resources.auth_validation_email_required
import feniqomobil.sharedui.generated.resources.auth_validation_name_short
import feniqomobil.sharedui.generated.resources.auth_validation_password_mismatch
import feniqomobil.sharedui.generated.resources.auth_validation_password_required
import feniqomobil.sharedui.generated.resources.auth_validation_password_short
import org.jetbrains.compose.resources.stringResource

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
    PASSWORD_RESET_SENT,
    RECOVERY_LINK_INVALID,
    RECOVERY_NOT_AUTHORIZED,
    PASSWORD_UPDATED,
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
        "auth_recovery_link_invalid" -> AuthUiMessage.RECOVERY_LINK_INVALID
        "auth_recovery_not_authorized" -> AuthUiMessage.RECOVERY_NOT_AUTHORIZED
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
    AuthUiMessage.INVALID_CREDENTIALS -> "E-posta veya parola hatalı.\nBilgilerini kontrol edip tekrar dene."
    AuthUiMessage.EMAIL_ALREADY_REGISTERED -> "Bu e-posta adresiyle kayıtlı bir hesap zaten var."
    AuthUiMessage.NETWORK_UNAVAILABLE -> "Bağlantı kurulamadı.\nİnternet bağlantını kontrol edip yeniden dene."
    AuthUiMessage.RATE_LIMITED -> "Çok fazla deneme yaptınız. Lütfen bir süre bekleyip tekrar deneyin."
    AuthUiMessage.EMAIL_NOT_CONFIRMED -> "Giriş yapmadan önce e-posta adresinizi doğrulayın."
    AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE -> "Giriş ve kayıt işlemleri şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin."
    AuthUiMessage.INVALID_INPUT -> "Lütfen girdiğiniz bilgileri kontrol edin."
    AuthUiMessage.SESSION_EXPIRED -> "Oturum süreniz doldu, lütfen tekrar giriş yapın."
    AuthUiMessage.GENERIC_ERROR -> "Bir hata oluştu. Lütfen tekrar deneyin."
    AuthUiMessage.EMAIL_CONFIRMATION_SENT -> "Kaydınız oluşturuldu. Lütfen e-posta adresinize gönderilen doğrulama bağlantısını onaylayın."
    AuthUiMessage.PASSWORD_RESET_SENT -> "Bu adresle bir hesap varsa parola sıfırlama bağlantısı gönderilecek."
    AuthUiMessage.RECOVERY_LINK_INVALID -> "Bağlantı geçersiz veya süresi dolmuş.\nParolanı yenilemek için yeni bir bağlantı iste."
    AuthUiMessage.RECOVERY_NOT_AUTHORIZED -> "Kurtarma bağlantısı doğrulanmadı. Lütfen e-postandaki bağlantıyı tekrar açın."
    AuthUiMessage.PASSWORD_UPDATED -> "Parolan güncellendi.\nYeni parolanla hesabına giriş yapabilirsin."
}

/**
 * AuthValidationError için güvenli Türkçe alan hata metnini çözer.
 */
fun AuthValidationError.toDisplayText(): String = when (this) {
    AuthValidationError.EMAIL_REQUIRED -> "E-posta adresi gereklidir."
    AuthValidationError.EMAIL_INVALID -> "Geçerli bir e-posta adresi gir."
    AuthValidationError.PASSWORD_REQUIRED -> "Parola gereklidir."
    AuthValidationError.NEW_PASSWORD_TOO_SHORT -> "Parola en az 6 karakter olmalıdır."
    AuthValidationError.PASSWORDS_DO_NOT_MATCH -> "Parolalar eşleşmiyor."
    AuthValidationError.FULL_NAME_TOO_SHORT -> "Ad soyad en az 2 karakter olmalıdır."
}

@Composable
fun AuthUiMessage.toLocalizedText(): String =
    stringResource(
        when (this) {
            AuthUiMessage.INVALID_CREDENTIALS -> Res.string.auth_error_invalid_credentials
            AuthUiMessage.EMAIL_ALREADY_REGISTERED -> Res.string.auth_error_email_registered
            AuthUiMessage.NETWORK_UNAVAILABLE -> Res.string.auth_error_network
            AuthUiMessage.RATE_LIMITED -> Res.string.auth_error_rate_limited
            AuthUiMessage.EMAIL_NOT_CONFIRMED -> Res.string.auth_error_email_not_confirmed
            AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE -> Res.string.auth_error_provider_unavailable
            AuthUiMessage.INVALID_INPUT -> Res.string.auth_error_invalid_input
            AuthUiMessage.SESSION_EXPIRED -> Res.string.auth_error_session_expired
            AuthUiMessage.GENERIC_ERROR -> Res.string.auth_error_generic
            AuthUiMessage.EMAIL_CONFIRMATION_SENT -> Res.string.auth_email_confirmation_sent
            AuthUiMessage.PASSWORD_RESET_SENT -> Res.string.auth_password_reset_sent
            AuthUiMessage.RECOVERY_LINK_INVALID -> Res.string.auth_error_recovery_link_invalid
            AuthUiMessage.RECOVERY_NOT_AUTHORIZED -> Res.string.auth_error_recovery_not_authorized
            AuthUiMessage.PASSWORD_UPDATED -> Res.string.auth_password_updated
        },
    )

@Composable
fun AuthValidationError.toLocalizedText(): String =
    stringResource(
        when (this) {
            AuthValidationError.EMAIL_REQUIRED -> Res.string.auth_validation_email_required
            AuthValidationError.EMAIL_INVALID -> Res.string.auth_validation_email_invalid
            AuthValidationError.PASSWORD_REQUIRED -> Res.string.auth_validation_password_required
            AuthValidationError.NEW_PASSWORD_TOO_SHORT -> Res.string.auth_validation_password_short
            AuthValidationError.PASSWORDS_DO_NOT_MATCH -> Res.string.auth_validation_password_mismatch
            AuthValidationError.FULL_NAME_TOO_SHORT -> Res.string.auth_validation_name_short
        },
    )
