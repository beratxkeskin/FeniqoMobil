package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateWorkspaceUseCase
import com.feniqo.mobile.domain.validation.WorkspaceValidationResult
import com.feniqo.mobile.domain.validation.WorkspaceValidationRules
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

sealed interface WorkspaceCreateEvent { data object NavigateBack : WorkspaceCreateEvent }

@HiltViewModel
class WorkspaceCreateViewModel @Inject constructor(
    private val createWorkspaceUseCase: CreateWorkspaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceCreateUiState())
    val uiState: StateFlow<WorkspaceCreateUiState> = _uiState.asStateFlow()
    private val _events = Channel<WorkspaceCreateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var submitJob: Job? = null

    fun onNameChanged(value: String) { if (_uiState.value.isFormEnabled) _uiState.update { it.copy(name = value, nameError = null) } }
    fun onDescriptionChanged(value: String) { if (_uiState.value.isFormEnabled) _uiState.update { it.copy(description = value) } }
    fun onTypeChanged(value: WorkspaceType) { if (_uiState.value.isFormEnabled) _uiState.update { it.copy(type = value) } }
    fun onCurrencyChanged(value: Currency) { if (_uiState.value.isFormEnabled) _uiState.update { it.copy(currency = value) } }
    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }

    fun submit() {
        val state = _uiState.value
        if (!state.isFormEnabled || submitJob?.isActive == true) return
        val validation = WorkspaceValidationRules.validateCreateWorkspace(state.name, state.type, state.currency, state.description)
        val command = (validation as? WorkspaceValidationResult.Valid)?.value
        if (command == null) { _uiState.update { it.copy(nameError = "Çalışma alanı adı zorunludur.") }; return }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        submitJob = viewModelScope.launch {
            try {
                when (val result = createWorkspaceUseCase(command)) {
                    is RepositoryResult.Success -> _events.send(WorkspaceCreateEvent.NavigateBack)
                    is RepositoryResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toFinanceUiMessage()) }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _uiState.update { it.copy(errorMessage = FinanceUiMessage.GENERIC_ERROR) }
            } finally { _uiState.update { it.copy(isSubmitting = false) }; submitJob = null }
        }
    }
}
