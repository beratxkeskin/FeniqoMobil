package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.validation.AuthValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUiMessageTest {

    @Test
    fun toAuthUiMessage_mapsKnownAuthenticationErrorsCorrectly() {
        assertEquals(
            AuthUiMessage.INVALID_CREDENTIALS,
            AppError.Authentication("auth_invalid_credentials").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.SESSION_EXPIRED,
            AppError.Authentication("auth_session_expired").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.EMAIL_NOT_CONFIRMED,
            AppError.Authentication("auth_email_not_confirmed").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE,
            AppError.Authentication("auth_provider_unavailable").toAuthUiMessage(),
        )
    }

    @Test
    fun toAuthUiMessage_mapsKnownConflictErrorsCorrectly() {
        assertEquals(
            AuthUiMessage.EMAIL_ALREADY_REGISTERED,
            AppError.Conflict("auth_email_already_registered").toAuthUiMessage(),
        )
    }

    @Test
    fun toAuthUiMessage_mapsKnownNetworkErrorsCorrectly() {
        assertEquals(
            AuthUiMessage.NETWORK_UNAVAILABLE,
            AppError.Network("network_unavailable").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.RATE_LIMITED,
            AppError.Network("auth_rate_limited").toAuthUiMessage(),
        )
    }

    @Test
    fun toAuthUiMessage_mapsKnownValidationErrorsCorrectly() {
        assertEquals(
            AuthUiMessage.INVALID_INPUT,
            AppError.Validation("auth_invalid_input").toAuthUiMessage(),
        )
    }

    @Test
    fun toAuthUiMessage_mapsUnknownOrUnrecognizedErrorsToGenericError() {
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Unknown("auth_unknown").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Storage("disk_full").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Authentication("unknown_custom_code").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Network("unknown_network_code").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Conflict("unknown_conflict_code").toAuthUiMessage(),
        )
        assertEquals(
            AuthUiMessage.GENERIC_ERROR,
            AppError.Validation("unknown_validation_code").toAuthUiMessage(),
        )
    }

    @Test
    fun authUiMessage_toDisplayText_returnsSafeTurkishStrings() {
        assertEquals("E-posta veya parola hatalı.", AuthUiMessage.INVALID_CREDENTIALS.toDisplayText())
        assertEquals("Bu e-posta adresiyle kayıtlı bir hesap zaten var.", AuthUiMessage.EMAIL_ALREADY_REGISTERED.toDisplayText())
        assertEquals("İnternet bağlantınızı kontrol edip tekrar deneyin.", AuthUiMessage.NETWORK_UNAVAILABLE.toDisplayText())
        assertEquals("Çok fazla deneme yaptınız. Lütfen bir süre bekleyip tekrar deneyin.", AuthUiMessage.RATE_LIMITED.toDisplayText())
        assertEquals("Giriş yapmadan önce e-posta adresinizi doğrulayın.", AuthUiMessage.EMAIL_NOT_CONFIRMED.toDisplayText())
        assertEquals("Giriş ve kayıt işlemleri şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin.", AuthUiMessage.AUTH_PROVIDER_UNAVAILABLE.toDisplayText())
        assertEquals("Lütfen girdiğiniz bilgileri kontrol edin.", AuthUiMessage.INVALID_INPUT.toDisplayText())
        assertEquals("Oturum süreniz doldu, lütfen tekrar giriş yapın.", AuthUiMessage.SESSION_EXPIRED.toDisplayText())
        assertEquals("Bir hata oluştu. Lütfen tekrar deneyin.", AuthUiMessage.GENERIC_ERROR.toDisplayText())
        assertEquals("Kaydınız oluşturuldu. Lütfen e-posta adresinize gönderilen doğrulama bağlantısını onaylayın.", AuthUiMessage.EMAIL_CONFIRMATION_SENT.toDisplayText())
    }

    @Test
    fun authValidationError_toDisplayText_returnsSafeTurkishStrings() {
        assertEquals("E-posta adresi gereklidir.", AuthValidationError.EMAIL_REQUIRED.toDisplayText())
        assertEquals("Geçerli bir e-posta adresi girin.", AuthValidationError.EMAIL_INVALID.toDisplayText())
        assertEquals("Parola gereklidir.", AuthValidationError.PASSWORD_REQUIRED.toDisplayText())
        assertEquals("Parola en az 6 karakter olmalıdır.", AuthValidationError.NEW_PASSWORD_TOO_SHORT.toDisplayText())
        assertEquals("Parolalar birbiriyle eşleşmiyor.", AuthValidationError.PASSWORDS_DO_NOT_MATCH.toDisplayText())
        assertEquals("Ad soyad en az 2 karakter olmalıdır.", AuthValidationError.FULL_NAME_TOO_SHORT.toDisplayText())
    }

    @Test
    fun loginUiState_defaultValues_areSafeAndEmpty() {
        val state = LoginUiState.Initial
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.generalMessage)
        assertFalse(state.isSubmitting)
        assertFalse(state.isPasswordVisible)
    }

    @Test
    fun registerUiState_defaultValues_areSafeAndEmpty() {
        val state = RegisterUiState.Initial
        assertEquals("", state.fullName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.fullNameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.generalMessage)
        assertFalse(state.isSubmitting)
        assertFalse(state.isPasswordVisible)
        assertFalse(state.isConfirmPasswordVisible)
        assertFalse(state.isEmailConfirmationPending)
    }

    @Test
    fun registerUiState_canExplicitlyRepresentEmailConfirmationPending() {
        val state = RegisterUiState(
            email = "user@feniqo.com",
            isEmailConfirmationPending = true,
            generalMessage = AuthUiMessage.EMAIL_CONFIRMATION_SENT,
        )
        assertTrue(state.isEmailConfirmationPending)
        assertEquals(AuthUiMessage.EMAIL_CONFIRMATION_SENT, state.generalMessage)
    }
}
