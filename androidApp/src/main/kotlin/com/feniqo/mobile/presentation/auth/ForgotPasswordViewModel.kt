package com.feniqo.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SendPasswordResetEmailUseCase
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

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: AuthValidationError? = null,
    val isSubmitting: Boolean = false,
    val generalMessage: AuthUiMessage? = null,
    val isSentSuccess: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordResetEmailUseCase: SendPasswordResetEmailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

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

    fun submit(onSuccess: (email: String) -> Unit) {
        if (_uiState.value.isSubmitting || submitJob?.isActive == true) return

        val currentState = _uiState.value
        val emailValidation = AuthValidationRules.validateEmail(currentState.email)
        val emailError = (emailValidation as? AuthValidationResult.Invalid)?.error

        if (emailError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    generalMessage = null,
                )
            }
            return
        }

        val emailToSubmit = AuthValidationRules.normalizeEmail(currentState.email)

        _uiState.update {
            it.copy(
                isSubmitting = true,
                generalMessage = null,
                emailError = null,
            )
        }

        val job = viewModelScope.launch {
            try {
                when (val result = sendPasswordResetEmailUseCase(email = emailToSubmit)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                isSentSuccess = true,
                                email = "",
                            )
                        }
                        onSuccess(emailToSubmit)
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

    fun clearState() {
        _uiState.value = ForgotPasswordUiState()
    }
}
