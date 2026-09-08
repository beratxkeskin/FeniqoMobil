package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.usecase.JoinWorkspaceUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WorkspaceJoinEvent {
    data object NavigateBack : WorkspaceJoinEvent
}

@HiltViewModel
class WorkspaceJoinViewModel @Inject constructor(
    private val joinWorkspaceUseCase: JoinWorkspaceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceJoinUiState())
    val uiState: StateFlow<WorkspaceJoinUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkspaceJoinEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var submitJob: Job? = null

    fun onInviteCodeChanged(value: String) {
        if (_uiState.value.isFormEnabled) {
            _uiState.update { it.copy(inviteCode = value, codeError = null) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.isFormEnabled || submitJob?.isActive == true) return

        val trimmedCode = state.inviteCode.trim()
        if (trimmedCode.isBlank()) {
            _uiState.update { it.copy(codeError = "Davet kodu boş olamaz.") }
            return
        }

        val inviteCode = try {
            WorkspaceInviteCode(trimmedCode)
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(codeError = e.message ?: "Geçersiz davet kodu.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        submitJob = viewModelScope.launch {
            try {
                when (val result = joinWorkspaceUseCase(inviteCode)) {
                    is RepositoryResult.Success -> {
                        _events.send(WorkspaceJoinEvent.NavigateBack)
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                submitJob = null
            }
        }
    }
}
