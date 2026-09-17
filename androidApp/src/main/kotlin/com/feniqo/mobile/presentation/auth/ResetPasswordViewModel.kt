package com.feniqo.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ClearRecoveryStateUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase
import com.feniqo.mobile.domain.usecase.ResetPasswordUseCase
import com.feniqo.mobile.domain.validation.AuthValidationError
import com.feniqo.mobile.domain.validation.AuthValidationResult
import com.feniqo.mobile.domain.validation.AuthValidationRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val passwordError: AuthValidationError? = null,
    val confirmPasswordError: AuthValidationError? = null,
    val isSubmitting: Boolean = false,
    val generalMessage: AuthUiMessage? = null,
    val isRecoveryAuthorized: Boolean = false,
    val isRecoveryVerifying: Boolean = false,
    val isRecoveryLinkInvalid: Boolean = false,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val observeRecoveryStateUseCase: ObserveRecoveryStateUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val clearRecoveryStateUseCase: ClearRecoveryStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            observeRecoveryStateUseCase().collect { recoveryState ->
                _uiState.update { current ->
                    when (recoveryState) {
                        is AuthRecoveryState.Verified -> current.copy(
                            isRecoveryAuthorized = true,
                            isRecoveryVerifying = false,
                            isRecoveryLinkInvalid = false,
                        )
                        AuthRecoveryState.Validating -> current.copy(
                            isRecoveryAuthorized = false,
                            isRecoveryVerifying = true,
                            isRecoveryLinkInvalid = false,
                        )
                        AuthRecoveryState.InvalidOrExpired -> current.copy(
                            isRecoveryAuthorized = false,
                            isRecoveryVerifying = false,
                            isRecoveryLinkInvalid = true,
                            generalMessage = AuthUiMessage.RECOVERY_LINK_INVALID,
                        )
                        AuthRecoveryState.Idle -> current.copy(
                            isRecoveryAuthorized = false,
                            isRecoveryVerifying = false,
                        )
                    }
                }
            }
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

    fun submit(onSuccess: () -> Unit) {
        if (_uiState.value.isSubmitting || submitJob?.isActive == true) return

        val currentState = _uiState.value

        // SDK üzerinden recovery doğrulaması yapılmamışsa şifre güncelleme yetkisi verme
        if (!currentState.isRecoveryAuthorized) {
            _uiState.update {
                it.copy(
                    generalMessage = if (currentState.isRecoveryLinkInvalid) {
                        AuthUiMessage.RECOVERY_LINK_INVALID
                    } else {
                        AuthUiMessage.RECOVERY_NOT_AUTHORIZED
                    }
                )
            }
            return
        }

        val passwordValidation = AuthValidationRules.validateNewPassword(currentState.password)
        val confirmValidation = AuthValidationRules.validateConfirmPassword(
            password = currentState.password,
            confirmPassword = currentState.confirmPassword,
        )

        val passwordError = (passwordValidation as? AuthValidationResult.Invalid)?.error
        val confirmError = (confirmValidation as? AuthValidationResult.Invalid)?.error

        if (passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                    generalMessage = null,
                )
            }
            return
        }

        val newPassword = currentState.password

        _uiState.update {
            it.copy(
                isSubmitting = true,
                generalMessage = null,
                passwordError = null,
                confirmPasswordError = null,
            )
        }

        val job = viewModelScope.launch {
            try {
                when (val result = resetPasswordUseCase(newPassword = newPassword)) {
                    is RepositoryResult.Success -> {
                        // Hassas parola alanlarını temizle ve recovery durumunu sıfırla
                        _uiState.update {
                            it.copy(
                                password = "",
                                confirmPassword = "",
                                isSubmitting = false,
                                isSuccess = true,
                            )
                        }
                        clearRecoveryStateUseCase()
                        onSuccess()
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
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
                        isSubmitting = false,
                        generalMessage = AuthUiMessage.GENERIC_ERROR,
                    )
                }
            } finally {
                submitJob = null
            }
        }
        submitJob = job
    }

    fun abandonRecovery() {
        // Akış terk edildiğinde parolayı RAM'den temizle ve recovery durumunu sıfırla
        _uiState.update {
            it.copy(
                password = "",
                confirmPassword = "",
                generalMessage = null,
            )
        }
        clearRecoveryStateUseCase()
    }
}
