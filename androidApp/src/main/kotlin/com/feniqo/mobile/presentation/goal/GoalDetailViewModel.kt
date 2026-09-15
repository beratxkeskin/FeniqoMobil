package com.feniqo.mobile.presentation.goal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteGoalUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.navigation.GoalDetailRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeGoal: ObserveGoalUseCase,
    private val observeContributions: ObserveGoalContributionsUseCase,
    private val deleteGoal: DeleteGoalUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(GoalDetailUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<GoalDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val goalId = runCatching { savedStateHandle.toRoute<GoalDetailRoute>().goalId.trim() }
        .getOrNull()?.takeIf { it.isNotEmpty() }?.let { runCatching { EntityId(it) }.getOrNull() }

    init { observe() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observe() {
        val id = goalId ?: run { _state.value = GoalDetailUiState(isLoading = false, isNotFound = true); return }
        viewModelScope.launch {
            try {
                observeGoal(id).flatMapLatest { goal ->
                    if (goal == null) flowOf(null)
                    else observeContributions(id)
                        .catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }
                        .map { GoalDetailPresentationCalculator.calculate(goal, it, currentDateProvider.today()) }
                }.collect { detail ->
                    _state.value = if (detail == null) GoalDetailUiState(isLoading = false, isNotFound = true)
                    else _state.value.copy(isLoading = false, detail = detail, isNotFound = false, observationError = null)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(isLoading = false, observationError = FinanceUiMessage.GENERIC_ERROR) } }
        }
    }

    fun requestDelete() = _state.update { it.copy(showDeleteConfirmation = true) }
    fun dismissDelete() { if (!_state.value.isDeleting) _state.update { it.copy(showDeleteConfirmation = false) } }
    fun confirmDelete() {
        val id = goalId ?: return
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                when (val result = deleteGoal(id)) {
                    is RepositoryResult.Success -> _events.send(GoalDetailEvent.Deleted)
                    is RepositoryResult.Failure -> _events.send(GoalDetailEvent.Message(result.error.toFinanceUiMessage()))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _events.send(GoalDetailEvent.Message(FinanceUiMessage.GENERIC_ERROR)) }
            finally { _state.update { it.copy(isDeleting = false) } }
        }
    }
}
