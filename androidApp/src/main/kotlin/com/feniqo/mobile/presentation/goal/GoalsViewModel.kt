package com.feniqo.mobile.presentation.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalsUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Birikim hedeflerinin gözlemini ve yeniden deneme (retry) niyetini yöneten ViewModel'dir.
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val observeGoalsUseCase: ObserveGoalsUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
) : ViewModel() {

    private val _retryTrigger = MutableStateFlow(0L)

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val goals: List<GoalDisplayModel>) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = _retryTrigger
        .flatMapLatest {
            observeGoalsUseCase()
                .map { goals ->
                    val items = GoalDisplayModelMapper.map(goals)
                    ObservationResult.Success(items) as ObservationResult
                }
                .onStart {
                    emit(ObservationResult.Loading)
                }
                .catch { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
                }
        }

    val uiState: StateFlow<GoalsUiState> = combine(
        observationResultFlow,
        observeActiveWorkspaceUseCase(),
    ) { observationResult, activeWorkspace ->
        val workspaceName = activeWorkspace?.name
        when (observationResult) {
            is ObservationResult.Loading -> GoalsUiState(
                isLoading = true,
                goals = emptyList(),
                activeWorkspaceName = workspaceName,
                observationError = null,
            )
            is ObservationResult.Failure -> GoalsUiState(
                isLoading = false,
                goals = emptyList(),
                activeWorkspaceName = workspaceName,
                observationError = observationResult.message,
            )
            is ObservationResult.Success -> GoalsUiState(
                isLoading = false,
                goals = observationResult.goals,
                activeWorkspaceName = workspaceName,
                observationError = null,
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = GoalsUiState(isLoading = true),
        )

    fun onIntent(intent: GoalsIntent) {
        when (intent) {
            is GoalsIntent.Retry -> retryObservation()
        }
    }

    private fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }
}
