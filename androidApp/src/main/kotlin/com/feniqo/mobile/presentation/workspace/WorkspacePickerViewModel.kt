package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspacesUseCase
import com.feniqo.mobile.domain.usecase.SetActiveWorkspaceUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkspacePickerViewModel @Inject constructor(
    private val observeWorkspacesUseCase: ObserveWorkspacesUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val setActiveWorkspaceUseCase: SetActiveWorkspaceUseCase,
) : ViewModel() {

    private val _isSelecting = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<FinanceUiMessage?>(null)

    private var activeSelectionJob: Job? = null

    private data class ObservationData(
        val workspaces: List<Workspace>,
        val activeWorkspace: Workspace?,
    )

    private val observationFlow = combine(
        observeWorkspacesUseCase(),
        observeActiveWorkspaceUseCase(),
    ) { workspaces, activeWorkspace ->
        ObservationData(workspaces, activeWorkspace)
    }

    val uiState: StateFlow<WorkspacePickerUiState> = combine(
        observationFlow,
        _isSelecting,
        _errorMessage,
    ) { observationData, isSelecting, errorMessage ->
        val activeId = observationData.activeWorkspace?.id
        val isPersonal = activeId == null
        val items = observationData.workspaces.map { ws ->
            WorkspacePickerItemUiModel(
                id = ws.id,
                name = ws.name,
                isOwner = false, // V2 rol gösterimi sonraki fazlarda zenginleştirilebilir
                isActive = ws.id == activeId,
            )
        }
        WorkspacePickerUiState(
            isLoading = false,
            isPersonalModeActive = isPersonal,
            activeWorkspaceId = activeId,
            workspaces = items,
            isSelecting = isSelecting,
            errorMessage = errorMessage,
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(
            WorkspacePickerUiState(
                isLoading = false,
                isPersonalModeActive = true,
                activeWorkspaceId = null,
                workspaces = emptyList(),
                isSelecting = false,
                errorMessage = FinanceUiMessage.GENERIC_ERROR,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WorkspacePickerUiState(isLoading = true),
    )

    fun selectPersonalMode() {
        executeSelection(null)
    }

    fun selectWorkspace(workspaceId: EntityId) {
        executeSelection(workspaceId)
    }

    private fun executeSelection(targetWorkspaceId: EntityId?) {
        // Çift tıklama ve eşzamanlı seçim engeli (fail-closed guard)
        if (_isSelecting.value || activeSelectionJob?.isActive == true) {
            return
        }

        // Zaten seçili olan alanı tekrar seçiyorsa gereksiz IO yapma
        if (uiState.value.activeWorkspaceId == targetWorkspaceId) {
            return
        }

        _errorMessage.value = null
        _isSelecting.value = true

        activeSelectionJob = viewModelScope.launch {
            try {
                val result = setActiveWorkspaceUseCase(targetWorkspaceId)
                when (result) {
                    is RepositoryResult.Success -> {
                        _errorMessage.value = null
                    }
                    is RepositoryResult.Failure -> {
                        _errorMessage.value = result.error.toFinanceUiMessage()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = FinanceUiMessage.GENERIC_ERROR
            } finally {
                _isSelecting.value = false
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
