package com.feniqo.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SignInUseCase
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
 * Giriş ekranının form doğrulama, yüklenme ve giriş aksiyonlarını yönetir.
 * Parola yalnızca bellek üzerinde geçici tutulur; kalıcılaştırılmaz veya loglanmaz.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var submitJob: Job? = null

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
                generalMessage = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || submitJob?.isActive == true) return

        val currentState = _uiState.value
        val emailValidation = AuthValidationRules.validateEmail(currentState.email)
        val passwordValidation = AuthValidationRules.validateLoginPassword(currentState.password)

        val emailError = (emailValidation as? AuthValidationResult.Invalid)?.error
        val passwordError = (passwordValidation as? AuthValidationResult.Invalid)?.error

        if (emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    generalMessage = null,
                )
            }
            return
        }

        val emailToSubmit = AuthValidationRules.normalizeEmail(currentState.email)
        val passwordToSubmit = currentState.password

        _uiState.update {
            it.copy(
                isSubmitting = true,
                generalMessage = null,
                emailError = null,
                passwordError = null,
            )
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val result = signInUseCase(email = emailToSubmit, password = passwordToSubmit)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(password = "")
                        }
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(generalMessage = result.error.toAuthUiMessage())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(generalMessage = AuthUiMessage.GENERIC_ERROR)
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
