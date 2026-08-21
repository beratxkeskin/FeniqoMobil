package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.validation.AuthValidationError

/**
 * Giriş ekranının saf sunum durumudur.
 * Parola yalnızca geçici state içinde RAM'de tutulur; Room, SharedPreferences,
 * DataStore, SavedStateHandle veya log/analytics'e kalıcılaştırılmaz.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: AuthValidationError? = null,
    val passwordError: AuthValidationError? = null,
    val generalMessage: AuthUiMessage? = null,
    val isSubmitting: Boolean = false,
    val isPasswordVisible: Boolean = false,
) {
    companion object {
        val Initial = LoginUiState()
    }
}
