package com.feniqo.mobile.presentation.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddGoalContributionUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
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

@HiltViewModel
class GoalContributionFormViewModel @Inject constructor(
    private val observeGoalUseCase: ObserveGoalUseCase,
    private val addGoalContributionUseCase: AddGoalContributionUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val _parentLoadState = MutableStateFlow<GoalContributionParentLoadState>(GoalContributionParentLoadState.Idle)
    val parentLoadState: StateFlow<GoalContributionParentLoadState> = _parentLoadState.asStateFlow()

    private val _uiState = MutableStateFlow(
        GoalContributionFormUiState(
            input = GoalContributionFormInput(occurredOn = currentDateProvider.today()),
        ),
    )
    val uiState: StateFlow<GoalContributionFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<GoalContributionFormUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var parentGoal: Goal? = null
    private var parentLoadJob: Job? = null
    private var parentLoadToken: Long = 0L

    fun loadParentGoal(goalId: EntityId) {
        parentLoadJob?.cancel()
        val currentToken = ++parentLoadToken

        _parentLoadState.value = GoalContributionParentLoadState.Loading

        parentLoadJob = viewModelScope.launch {
            try {
                observeGoalUseCase(goalId).collect { goal ->
                    if (parentLoadToken == currentToken) {
                        parentGoal = goal
                        if (goal != null) {
                            _parentLoadState.value = GoalContributionParentLoadState.Ready(goal)
                            // Tarih henüz belirlenmemişse varsayılan bugünü ayarla
                            if (_uiState.value.input.occurredOn == null) {
                                _uiState.update { current ->
                                    current.copy(
                                        input = current.input.copy(occurredOn = currentDateProvider.today()),
                                    )
                                }
                            }
                        } else {
                            _parentLoadState.value = GoalContributionParentLoadState.NotFound
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (parentLoadToken == currentToken) {
                    _parentLoadState.value = GoalContributionParentLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                }
            }
        }
    }

    fun setParentLoadInvalidId() {
        parentLoadJob?.cancel()
        parentLoadJob = null
        parentGoal = null
        parentLoadToken++
        _parentLoadState.value = GoalContributionParentLoadState.NotFound
    }

    fun updateInput(transform: (GoalContributionFormInput) -> GoalContributionFormInput) {
        _uiState.update { current ->
            val updated = transform(current.input)
            current.copy(
                input = updated,
                errors = GoalContributionFormInputErrors(),
            )
        }
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return

        val currentParent = parentGoal
        if (currentParent == null) {
            _uiState.update {
                it.copy(errors = GoalContributionFormInputErrors(generalError = GoalContributionFormFieldError.PARENT_GOAL_MISSING))
            }
            return
        }

        when (val draftResult = currentState.input.toDraft(currentParent)) {
            is GoalContributionFormNormalizationResult.Invalid -> {
                _uiState.update { it.copy(errors = draftResult.errors) }
            }
            is GoalContributionFormNormalizationResult.Success -> {
                _uiState.update { it.copy(isSubmitting = true, errors = GoalContributionFormInputErrors()) }
                viewModelScope.launch {
                    try {
                        val result = addGoalContributionUseCase(draftResult.command)
                        when (result) {
                            is RepositoryResult.Success -> {
                                _events.send(GoalContributionFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_CONTRIBUTION_ADDED))
                            }
                            is RepositoryResult.Failure -> {
                                _events.send(GoalContributionFormUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _events.send(GoalContributionFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    } finally {
                        _uiState.update { it.copy(isSubmitting = false) }
                    }
                }

            }
        }
    }
}
