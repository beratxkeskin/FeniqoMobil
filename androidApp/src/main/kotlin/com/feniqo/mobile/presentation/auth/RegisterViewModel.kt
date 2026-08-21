package com.feniqo.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SignUpUseCase
import com.feniqo.mobile.domain.validation.AuthValidationResult
import com.feniqo.mobile.domain.validation.AuthValidationRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kayıt ekranının form doğrulama, yüklenme ve kayıt aksiyonlarını yönetir.
 * Parolalar yalnızca bellek üzerinde geçici tutulur; kalıcılaştırılmaz veya loglanmaz.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val signUpUseCase: SignUpUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState.Initial)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private var submitJob: Job? = null

    fun onFullNameChanged(fullName: String) {
        _uiState.update {
            it.copy(
                fullName = fullName,
                fullNameError = null,
                generalMessage = null,
            )
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                generalMessage = null,
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                confirmPasswordError = null,
                generalMessage = null,
            )
        }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = null,
                generalMessage = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || submitJob?.isActive == true) return

        val currentState = _uiState.value
        val fullNameValidation = AuthValidationRules.validateFullName(currentState.fullName)
        val emailValidation = AuthValidationRules.validateEmail(currentState.email)
        val passwordValidation = AuthValidationRules.validateNewPassword(currentState.password)
        val confirmPasswordValidation = AuthValidationRules.validateConfirmPassword(
            password = currentState.password,
            confirmPassword = currentState.confirmPassword,
        )

        val fullNameError = (fullNameValidation as? AuthValidationResult.Invalid)?.error
        val emailError = (emailValidation as? AuthValidationResult.Invalid)?.error
        val passwordError = (passwordValidation as? AuthValidationResult.Invalid)?.error
        val confirmPasswordError = (confirmPasswordValidation as? AuthValidationResult.Invalid)?.error

        if (fullNameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    generalMessage = null,
                )
            }
            return
        }

        val fullNameToSubmit = AuthValidationRules.normalizeFullName(currentState.fullName)
        val emailToSubmit = AuthValidationRules.normalizeEmail(currentState.email)
        val passwordToSubmit = currentState.password

        _uiState.update {
            it.copy(
                isSubmitting = true,
                generalMessage = null,
                fullNameError = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
            )
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val result = signUpUseCase(
                    email = emailToSubmit,
                    password = passwordToSubmit,
                    fullName = fullNameToSubmit,
                )) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                password = "",
                                confirmPassword = "",
                                isEmailConfirmationPending = true,
                                generalMessage = AuthUiMessage.EMAIL_CONFIRMATION_SENT,
                            )
                        }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                generalMessage = result.error.toAuthUiMessage(),
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        generalMessage = AuthUiMessage.GENERIC_ERROR,
                    )
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                submitJob = null
            }
        }
        submitJob = job
        job.start()
    }
}
