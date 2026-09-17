package com.feniqo.mobile.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ResendEmailConfirmationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmailVerificationUiState(
    val isResending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val resendSuccess: Boolean = false,
    val generalMessage: AuthUiMessage? = null,
)

@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val resendEmailConfirmationUseCase: ResendEmailConfirmationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    private var resendJob: Job? = null
    private var cooldownJob: Job? = null

    fun resend(email: String) {
        if (_uiState.value.isResending || _uiState.value.cooldownSeconds > 0 || resendJob?.isActive == true) {
            return
        }

        if (email.isBlank()) {
            _uiState.update { it.copy(generalMessage = AuthUiMessage.GENERIC_ERROR) }
            return
        }

        _uiState.update {
            it.copy(
                isResending = true,
                generalMessage = null,
                resendSuccess = false,
            )
        }

        val job = viewModelScope.launch {
            try {
                when (val result = resendEmailConfirmationUseCase(email = email)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isResending = false,
                                resendSuccess = true,
                                generalMessage = null,
                            )
                        }
                        startCooldown(60)
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isResending = false,
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
                        isResending = false,
                        generalMessage = AuthUiMessage.GENERIC_ERROR,
                    )
                }
            } finally {
                resendJob = null
            }
        }
        resendJob = job
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _uiState.update { it.copy(cooldownSeconds = i) }
                delay(1000)
            }
            _uiState.update { it.copy(cooldownSeconds = 0) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(generalMessage = null, resendSuccess = false) }
    }
}
