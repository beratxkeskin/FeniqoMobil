package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.validation.AuthValidationError

/**
 * Kayıt ekranının saf sunum durumudur.
 * Parolalar yalnızca geçici state içinde RAM'de tutulur; Room, SharedPreferences,
 * DataStore, SavedStateHandle veya log/analytics'e kalıcılaştırılmaz.
 */
data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullNameError: AuthValidationError? = null,
    val emailError: AuthValidationError? = null,
    val passwordError: AuthValidationError? = null,
    val confirmPasswordError: AuthValidationError? = null,
    val generalMessage: AuthUiMessage? = null,
    val isSubmitting: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isEmailConfirmationPending: Boolean = false,
) {
    companion object {
        val Initial = RegisterUiState()
    }
}
